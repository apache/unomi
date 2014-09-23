package org.oasis_open.wemi.context.server.impl.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.*;
import org.oasis_open.wemi.context.server.impl.actions.ActionExecutorDispatcher;
import org.oasis_open.wemi.context.server.persistence.spi.CustomObjectMapper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class RulesServiceImpl implements RulesService, EventListenerService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private UserService userService;

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

    public void setUserService(UserService userService) {
        this.userService = userService;
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

    private void loadPredefinedRules(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/wemi/rules", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }

        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                Rule rule = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Rule.class);
                if (getRule(rule.getMetadata().getId()) == null) {
                    setRule(rule.getMetadata().getId(), rule);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }

        }
    }

    public Set<Rule> getMatchingRules(Event event) {
        Set<Rule> matchedRules = new LinkedHashSet<Rule>();

        List<String> matchingQueries = persistenceService.getMatchingSavedQueries(event);

        Boolean hasEventAlreadyBeenRaisedForSession = null;
        Boolean hasEventAlreadyBeenRaisedForUser = null;

        if (matchingQueries.size() > 0) {
            for (String matchingQuery : matchingQueries) {
                Rule rule = getRule(matchingQuery);
                if (rule != null) {
                    if (rule.isRaiseEventOnlyOnceForUser()) {
                        hasEventAlreadyBeenRaisedForUser = hasEventAlreadyBeenRaisedForUser != null ? hasEventAlreadyBeenRaisedForUser : eventService.hasEventAlreadyBeenRaised(event, false);
                        if (hasEventAlreadyBeenRaisedForUser) {
                            continue;
                        }
                    } else if (rule.isRaiseEventOnlyOnceForSession()) {
                        hasEventAlreadyBeenRaisedForSession = hasEventAlreadyBeenRaisedForSession != null ? hasEventAlreadyBeenRaisedForSession : eventService.hasEventAlreadyBeenRaised(event, true);
                        if (hasEventAlreadyBeenRaisedForSession) {
                            continue;
                        }
                    }

                    ObjectMapper mapper = CustomObjectMapper.getObjectMapper();
                    try {
                        Condition userCondition = extractConditionByTag(rule.getCondition(), "userCondition");
                        if (userCondition != null && !userService.matchCondition(mapper.writeValueAsString(userCondition), event.getUser(), event.getSession())) {
                            continue;
                        }
                        Condition sessionCondition = extractConditionByTag(rule.getCondition(), "sessionCondition");
                        if (sessionCondition != null && !userService.matchCondition(mapper.writeValueAsString(sessionCondition), event.getUser(), event.getSession())) {
                            continue;
                        }
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }

                    matchedRules.add(rule);
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

            Event ruleFired = new Event("ruleFired", event.getSession(), event.getUser(), event.getTimeStamp());
            ruleFired.getAttributes().putAll(event.getAttributes());
            ruleFired.setProperty("ruleName", rule.getItemId());
            eventService.save(ruleFired);
        }
        return changed;
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle().getBundleContext() != null) {
                    loadPredefinedRules(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

    public Set<Metadata> getRuleMetadatas() {
        Set<Metadata> metadatas = new HashSet<Metadata>();
        for (Rule rule : persistenceService.getAllItems(Rule.class).getList()) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public Rule getRule(String ruleId) {
        Rule rule = persistenceService.load(ruleId, Rule.class);
        if (rule != null) {
            if (rule.getCondition() != null) {
                ParserHelper.resolveConditionType(definitionsService, rule.getCondition());
            }
            if (rule.getActions() != null) {
                for (Action action : rule.getActions()) {
                    ParserHelper.resolveActionType(definitionsService, action);
                }
            }
        }
        return rule;
    }

    public void setRule(String ruleId, Rule rule) {
        Condition condition = rule.getCondition();
        if (condition != null) {
            ParserHelper.resolveConditionType(definitionsService, condition);
            Condition eventCondition = extractConditionByTag(condition, "eventCondition");
            if (eventCondition != null) {
                persistenceService.saveQuery(ruleId, eventCondition);
            }
        }
        if (rule.getActions() != null) {
            for (Action action : rule.getActions()) {
                ParserHelper.resolveActionType(definitionsService, action);
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


    public void createRule(String ruleId, String name, String description) {
        Metadata metadata = new Metadata(ruleId, name, description);
        Rule rule = new Rule(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        rule.setCondition(rootCondition);
        rule.setActions(new ArrayList<Action>());
        setRule(ruleId, rule);

    }

    public void removeRule(String ruleId) {
        persistenceService.removeQuery(ruleId);
        persistenceService.remove(ruleId, Rule.class);
    }


}
