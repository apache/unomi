package org.oasis_open.contextserver.impl.services;

import org.apache.commons.lang3.StringUtils;
import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.EventTarget;
import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.services.*;
import org.oasis_open.contextserver.impl.actions.ActionExecutorDispatcher;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class RulesServiceImpl implements RulesService, EventListenerService, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());
    public static final String RULE_QUERY_PREFIX = "rule_";

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private ProfileService profileService;

    private EventService eventService;

    private ActionExecutorDispatcher actionExecutorDispatcher;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setActionExecutorDispatcher(ActionExecutorDispatcher actionExecutorDispatcher) {
        this.actionExecutorDispatcher = actionExecutorDispatcher;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedRules(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedRules(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedRules(bundleContext);

        List<PluginType> types = definitionsService.getTypesByPlugin().get(bundleContext.getBundle().getBundleId());
        List<String> addedConditions = new ArrayList<String>();
        List<String> addedActions = new ArrayList<String>();
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ConditionType) {
                    addedConditions.add(((ConditionType) type).getId());
                } else if (type instanceof ActionType) {
                    addedActions.add(((ActionType) type).getId());
                }
            }
        }
        if (!addedConditions.isEmpty() || !addedActions.isEmpty()) {
            for (Rule rule : persistenceService.query("missingPlugins", "true", null, Rule.class)) {
                boolean succeed = ParserHelper.resolveConditionType(definitionsService, rule.getCondition()) &&
                        ParserHelper.resolveActionTypes(definitionsService, rule.getActions());
                if (succeed) {
                    logger.info("Enable rule " + rule.getItemId());
                    rule.getMetadata().setMissingPlugins(false);
                    setRule(rule);
                }
            }
        }
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = definitionsService.getTypesByPlugin().get(bundleContext.getBundle().getBundleId());
        List<String> removedConditions = new ArrayList<String>();
        List<String> removedActions = new ArrayList<String>();
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ConditionType) {
                    removedConditions.add(((ConditionType) type).getId());
                } else if (type instanceof ActionType) {
                    removedActions.add(((ActionType) type).getId());
                }
            }
        }
        if (!removedConditions.isEmpty() || !removedActions.isEmpty()) {
            for (Rule rule : persistenceService.getAllItems(Rule.class)) {
                List<String> conditions = ParserHelper.getConditionTypeIds(rule.getCondition());
                List<String> actions = new ArrayList<String>();
                for (Action action : rule.getActions()) {
                    actions.add(action.getActionTypeId());
                }
                if (!Collections.disjoint(conditions, removedConditions) || !Collections.disjoint(actions, removedActions)) {
                    logger.info("Disable rule " + rule.getItemId());
                    rule.getMetadata().setMissingPlugins(true);
                    setRule(rule);
                }
            }
        }
    }

    private void loadPredefinedRules(BundleContext bundleContext) {
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/wemi/rules", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }

        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                Rule rule = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Rule.class);
                if (getRule(rule.getMetadata().getScope(), rule.getMetadata().getId()) == null) {
                    setRule(rule);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }

        }
    }

    public Set<Rule> getMatchingRules(Event event) {
        Set<Rule> matchedRules = new LinkedHashSet<Rule>();

        List<String> matchingQueries = persistenceService.getMatchingSavedQueries(event);

        logger.info("Found matching : " + matchingQueries);

        Boolean hasEventAlreadyBeenRaisedForSession = null;
        Boolean hasEventAlreadyBeenRaisedForProfile = null;

        if (matchingQueries.size() > 0) {
            for (String matchingQuery : matchingQueries) {
                if (matchingQuery.startsWith(RULE_QUERY_PREFIX)) {
                    matchingQuery = matchingQuery.substring(RULE_QUERY_PREFIX.length());
                    String scope = StringUtils.substringBefore(matchingQuery, "_");
                    if (scope.equals(Metadata.SYSTEM_SCOPE) || scope.equals(event.getScope())) {
                        matchingQuery = StringUtils.substringAfter(matchingQuery, "_");
                        Rule rule = getRule(scope, matchingQuery);
                        if (rule != null) {
                            if (rule.isRaiseEventOnlyOnceForProfile()) {
                                hasEventAlreadyBeenRaisedForProfile = hasEventAlreadyBeenRaisedForProfile != null ? hasEventAlreadyBeenRaisedForProfile : eventService.hasEventAlreadyBeenRaised(event, false);
                                if (hasEventAlreadyBeenRaisedForProfile) {
                                    continue;
                                }
                            } else if (rule.isRaiseEventOnlyOnceForSession()) {
                                hasEventAlreadyBeenRaisedForSession = hasEventAlreadyBeenRaisedForSession != null ? hasEventAlreadyBeenRaisedForSession : eventService.hasEventAlreadyBeenRaised(event, true);
                                if (hasEventAlreadyBeenRaisedForSession) {
                                    continue;
                                }
                            }

                            Condition profileCondition = extractConditionByTag(rule.getCondition(), "profileCondition");
                            if (profileCondition != null && !persistenceService.testMatch(profileCondition, event.getProfile())) {
                                continue;
                            }
                            Condition sessionCondition = extractConditionByTag(rule.getCondition(), "sessionCondition");
                            if (sessionCondition != null && !persistenceService.testMatch(sessionCondition, event.getSession())) {
                                continue;
                            }

                            matchedRules.add(rule);
                        }
                    }
                }
            }
        }

        return matchedRules;
    }


    public boolean canHandle(Event event) {
        return true;
    }

    public boolean onEvent(Event event) {
        Set<Rule> rules = getMatchingRules(event);

        boolean changed = false;
        for (Rule rule : rules) {
            for (Action action : rule.getActions()) {
                changed |= actionExecutorDispatcher.execute(action, event);
            }

            Event ruleFired = new Event("ruleFired", event.getSession(), event.getProfile(), event.getScope(), event.getSource(), new EventTarget(rule.getItemId(), Rule.ITEM_TYPE), event.getTimeStamp());
            ruleFired.getAttributes().putAll(event.getAttributes());
            ruleFired.setPersistent(false);
            eventService.send(ruleFired);
        }
        return changed;
    }

    public Set<Metadata> getRuleMetadatas() {
        Set<Metadata> metadatas = new HashSet<Metadata>();
        for (Rule rule : persistenceService.getAllItems(Rule.class, 0, 50, null).getList()) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public Set<Metadata> getRuleMetadatas(String scope) {
        Set<Metadata> metadatas = new HashSet<Metadata>();
        for (Rule rule : persistenceService.query("scope", scope, null, Rule.class, 0, 50).getList()) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public Rule getRule(String scope, String ruleId) {
        Rule rule = persistenceService.load(Metadata.getIdWithScope(scope, ruleId), Rule.class);
        if (rule != null) {
            if (rule.getCondition() != null) {
                ParserHelper.resolveConditionType(definitionsService, rule.getCondition());
            }
            if (rule.getActions() != null) {
                ParserHelper.resolveActionTypes(definitionsService, rule.getActions());
            }
        }
        return rule;
    }

    public void setRule(Rule rule) {
        Condition condition = rule.getCondition();
        if (condition != null) {
            if (rule.getMetadata().isEnabled() && !rule.getMetadata().isMissingPlugins()) {
                ParserHelper.resolveConditionType(definitionsService, condition);
                Condition eventCondition = extractConditionByTag(condition, "eventCondition");
                if (eventCondition != null) {
                    persistenceService.saveQuery(RULE_QUERY_PREFIX + rule.getMetadata().getIdWithScope(), eventCondition);
                }
            } else {
                persistenceService.removeQuery(RULE_QUERY_PREFIX + rule.getMetadata().getIdWithScope());
            }
        }
        persistenceService.save(rule);
    }

    private Condition extractConditionByTag(Condition rootCondition, String tagId) {
        if (rootCondition.getParameterValues().containsKey("subConditions")) {
            List<Condition> subConditions = (List<Condition>) rootCondition.getParameterValues().get("subConditions");
            List<Condition> matchingConditions = new ArrayList<Condition>();
            for (Condition condition : subConditions) {
                Condition c = extractConditionByTag(condition, tagId);
                if (c != null) {
                    matchingConditions.add(c);
                }
            }
            if (matchingConditions.size() == 0) {
                return null;
            } else if (matchingConditions.equals(subConditions)) {
                return rootCondition;
            } else if (rootCondition.getConditionTypeId().equals("andCondition")) {
                if (matchingConditions.size() == 1) {
                    return matchingConditions.get(0);
                } else if (rootCondition.getConditionTypeId().equals("andCondition")) {
                    Condition res = new Condition();
                    res.setConditionType(definitionsService.getConditionType("andCondition"));
                    res.getParameterValues().put("subConditions", matchingConditions);
                    return res;
                }
            }
            throw new IllegalArgumentException();
        } else if (rootCondition.getConditionType() != null && rootCondition.getConditionType().getTagIDs().contains(tagId)) {
            return rootCondition;
        } else {
            return null;
        }
    }


    public void createRule(String scope, String ruleId, String name, String description) {
        Metadata metadata = new Metadata(scope, ruleId, name, description);
        Rule rule = new Rule(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        rule.setCondition(rootCondition);
        rule.setActions(new ArrayList<Action>());
        setRule(rule);
    }

    public void removeRule(String scope, String ruleId) {
        String idWithScope = Metadata.getIdWithScope(scope, ruleId);
        persistenceService.removeQuery(RULE_QUERY_PREFIX + idWithScope);
        persistenceService.remove(idWithScope, Rule.class);
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }
}
