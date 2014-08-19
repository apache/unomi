package org.oasis_open.wemi.context.server.impl.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.EventListenerService;
import org.oasis_open.wemi.context.server.api.services.RulesService;
import org.oasis_open.wemi.context.server.impl.actions.ActionExecutorDispatcher;
import org.oasis_open.wemi.context.server.persistence.spi.MapperHelper;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.util.*;

public class RulesServiceImpl implements RulesService, EventListenerService, BundleListener {

    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

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
                Rule rule = MapperHelper.getObjectMapper().readValue(predefinedSegmentURL, Rule.class);
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

        if (matchingQueries.size() > 0) {
            for (String matchingQuery : matchingQueries) {
                Rule rule = getRule(matchingQuery);
                if (rule != null) {
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
        for (Rule rule: rules) {
            for (Action action : rule.getActions()) {
                changed |= actionExecutorDispatcher.execute(action, event);
            }
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
        for (Rule rule : persistenceService.getAllItems(Rule.class)) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public Rule getRule(String ruleId) {
        Rule rule = persistenceService.load(ruleId, Rule.class);
        if (rule != null) {
            ParserHelper.resolveConditionType(definitionsService, rule.getCondition());
            for (Action action : rule.getActions()) {
                ParserHelper.resolveActionType(definitionsService, action);
            }
        }
        return rule;
    }

    public void setRule(String ruleId, Rule rule) {
        ParserHelper.resolveConditionType(definitionsService, rule.getCondition());
        for (Action action : rule.getActions()) {
            ParserHelper.resolveActionType(definitionsService, action);
        }
        persistenceService.saveQuery(ruleId, rule.getCondition());
        persistenceService.save(rule);
    }

    public void createRule(String ruleId, String name, String description) {
        Metadata metadata = new Metadata(ruleId, name, description);
        Rule rule = new Rule(metadata);
        Condition rootCondition = new Condition();
        rootCondition.setConditionType(definitionsService.getConditionType("andCondition"));
        rootCondition.getParameterValues().put("subConditions", new ArrayList<Condition>());
        rule.setCondition(rootCondition);

        setRule(ruleId, rule);

    }

    public void removeRule(String ruleId) {
        persistenceService.removeQuery(ruleId);
        persistenceService.remove(ruleId, Rule.class);
    }

    public void createAutoGeneratedRules(Condition condition) {
        List<Rule> rules = new ArrayList<Rule>();
        getAutoGeneratedRules(condition, rules);
        for (Rule rule : rules) {
            setRule(rule.getMetadata().getId(), rule);
        }
    }

    private void getAutoGeneratedRules(Condition condition, List<Rule> rules) {
        if (condition.getConditionType().getTagIDs().contains("event")) {
            try {
                String key = MapperHelper.getObjectMapper().writeValueAsString(MapperHelper.getObjectMapper().writeValueAsString(condition));
                key = "eventTriggered" + getMD5(key);
                condition.getParameterValues().put("generatedPropertyKey", key);
                if (getRule(key) == null) {
                    Rule r = new Rule(new Metadata(key, "Auto generated rule",""));
                    r.setCondition(condition);
                    final Action action = new Action();
                    action.setActionType(definitionsService.getActionType("setPropertyAction"));
                    action.getParameterValues().put("propertyName", key);
                    action.getParameterValues().put("propertyValue", "now");
                    r.setActions(Arrays.asList(action));
                    rules.add(r);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

//        }
//        if (condition.getConditionTypeId().equals("userEventCondition")) {
//            try {
//                final List<Condition> subConditions = (List<Condition>) condition.getParameterValues().get("subConditions");
//                String key = MapperHelper.getObjectMapper().writeValueAsString(MapperHelper.getObjectMapper().writeValueAsString(subConditions.get(0)));
//                key = "eventTriggered" + getMD5(key);
//                condition.getParameterValues().put("generatedPropertyKey", key);
//                if (getRule(key) == null) {
//                    Rule r = new Rule(new Metadata(key, "Auto generated rule",""));
//                    r.setCondition(subConditions.get(0));
//
//                    final Action action = new Action();
//                    action.setActionType(definitionsService.getActionType("setPropertyAction"));
//                    action.getParameterValues().put("propertyName", key);
//                    action.getParameterValues().put("propertyValue", "now");
//                    r.setActions(Arrays.asList(action));
//                    rules.add(r);
//                }
//            } catch (JsonProcessingException e) {
//                e.printStackTrace();
//            }
        } else {
            for (Object parameterValue : condition.getParameterValues().values()) {
                if (parameterValue instanceof Condition) {
                    getAutoGeneratedRules((Condition) parameterValue, rules);
                } else if (parameterValue instanceof Collection) {
                    for (Object subCondition : (Collection) parameterValue) {
                        if (subCondition instanceof Condition) {
                            getAutoGeneratedRules((Condition) subCondition, rules);
                        }
                    }
                }
            }
        }
    }

    private String getMD5(String md5) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] array = md.digest(md5.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < array.length; ++i) {
                sb.append(Integer.toHexString((array[i] & 0xFF) | 0x100).substring(1,3));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
        }
        return null;
    }



}
