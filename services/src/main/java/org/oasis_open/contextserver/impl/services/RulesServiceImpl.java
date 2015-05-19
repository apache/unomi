package org.oasis_open.contextserver.impl.services;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.ActionExecutor;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.query.Query;
import org.oasis_open.contextserver.api.services.*;
import org.oasis_open.contextserver.impl.actions.ActionExecutorDispatcher;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.actions.ActionType;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.*;
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

    private EventService eventService;

    private ActionExecutorDispatcher actionExecutorDispatcher;
    private List<Rule> allRules;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
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
        try {
            for (ServiceReference<ActionExecutor> reference : bundleContext.getServiceReferences(ActionExecutor.class, null)) {
                ActionExecutor service = bundleContext.getService(reference);
                actionExecutorDispatcher.addExecutor(reference.getProperty("actionExecutorId").toString(), reference.getBundle().getBundleId(), service);
            }
        } catch (Exception e) {
            logger.error("Cannot get services",e);
        }

        bundleContext.addBundleListener(this);

        initializeTimer();
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
        if (bundleContext.getBundle().getRegisteredServices() != null) {
            for (ServiceReference<?> reference : bundleContext.getBundle().getRegisteredServices()) {
                Object service = bundleContext.getService(reference);
                if (service instanceof ActionExecutor) {
                    actionExecutorDispatcher.addExecutor(reference.getProperty("actionExecutorId").toString(), bundleContext.getBundle().getBundleId(), (ActionExecutor) service);
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
        actionExecutorDispatcher.removeExecutors(bundleContext.getBundle().getBundleId());
    }

    private void loadPredefinedRules(BundleContext bundleContext) {
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/cxs/rules", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }

        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedSegmentURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined segment at " + predefinedSegmentURL + ", loading... ");

            try {
                Rule rule = CustomObjectMapper.getObjectMapper().readValue(predefinedSegmentURL, Rule.class);
                if (rule.getMetadata().getScope() == null) {
                    rule.getMetadata().setScope("systemscope");
                }
                if (getRule(rule.getMetadata().getId()) == null) {
                    setRule(rule);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedSegmentURL, e);
            }

        }
    }

    public Set<Rule> getMatchingRules(Event event) {
        Set<Rule> matchedRules = new LinkedHashSet<Rule>();

        Boolean hasEventAlreadyBeenRaisedForSession = null;
        Boolean hasEventAlreadyBeenRaisedForProfile = null;

        List<Rule> allItems = allRules;

        for (Rule rule : allItems) {
            String scope = rule.getMetadata().getScope();
            if (scope.equals(Metadata.SYSTEM_SCOPE) || scope.equals(event.getScope())) {
                Condition eventCondition = definitionsService.extractConditionByTag(rule.getCondition(), "eventCondition");

                if (eventCondition == null) {
                    continue;
                }

                if (!persistenceService.testMatch(eventCondition, event)) {
                    continue;
                }

                Set<Condition> sourceConditions = definitionsService.extractConditionsByType(rule.getCondition(), "sourceEventPropertyCondition");

                boolean unmatchedSource = false;
                for (Condition sourceCondition : sourceConditions) {
                    unmatchedSource = unmatchedSource || !persistenceService.testMatch(sourceCondition, event.getSource());
                }
                if (unmatchedSource) {
                    continue;
                }

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

                Condition profileCondition = definitionsService.extractConditionByTag(rule.getCondition(), "profileCondition");
                if (profileCondition != null && !persistenceService.testMatch(profileCondition, event.getProfile())) {
                    continue;
                }
                Condition sessionCondition = definitionsService.extractConditionByTag(rule.getCondition(), "sessionCondition");
                if (sessionCondition != null && !persistenceService.testMatch(sessionCondition, event.getSession())) {
                    continue;
                }
                matchedRules.add(rule);
            }
        }

        return matchedRules;
    }

    private List<Rule> getAllRules() {
        List<Rule> allItems = persistenceService.getAllItems(Rule.class, 0, -1, "priority").getList();
        for (Rule rule : allItems) {
            ParserHelper.resolveConditionType(definitionsService, rule.getCondition());
            ParserHelper.resolveActionTypes(definitionsService, rule.getActions());
        }
        return allItems;
    }


    public boolean canHandle(Event event) {
        return true;
    }

    public int onEvent(Event event) {
        Set<Rule> rules = getMatchingRules(event);

        int changes = EventService.NO_CHANGE;
        for (Rule rule : rules) {
            logger.debug("Fired rule " + rule.getMetadata().getId() + " for " + event.getEventType() + " - " + event.getItemId());
            for (Action action : rule.getActions()) {
                changes |= actionExecutorDispatcher.execute(action, event);
            }

            Event ruleFired = new Event("ruleFired", event.getSession(), event.getProfile(), event.getScope(), event, rule, event.getTimeStamp());
            ruleFired.getAttributes().putAll(event.getAttributes());
            ruleFired.setPersistent(false);
            changes |= eventService.send(ruleFired);
        }
        return changes;
    }

    public Set<Metadata> getRuleMetadatas() {
        Set<Metadata> metadatas = new HashSet<Metadata>();
        for (Rule rule : persistenceService.getAllItems(Rule.class, 0, 50, null).getList()) {
            metadatas.add(rule.getMetadata());
        }
        return metadatas;
    }

    public Set<Metadata> getRuleMetadatas(Query query) {
        definitionsService.resolveConditionType(query.getCondition());
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Rule definition : persistenceService.query(query.getCondition(), query.getSortby(), Rule.class, query.getOffset(), query.getLimit()).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Rule getRule(String ruleId) {
        Rule rule = persistenceService.load(ruleId, Rule.class);
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
                definitionsService.extractConditionByTag(condition, "eventCondition");
            }
        }
        persistenceService.save(rule);
    }

    public Set<Condition> getTrackedConditions(Item source){
        Set<Condition> trackedConditions = new HashSet<>();
        for (Rule r : allRules) {
            Condition trackedCondition = definitionsService.extractConditionByTag(r.getCondition(), "trackedCondition");
            if(trackedCondition != null){
                Set<Condition> sourceEventPropertyConditions = definitionsService.extractConditionsByType(r.getCondition(), "sourceEventPropertyCondition");
                boolean match = !(source == null && sourceEventPropertyConditions.size() > 0);
                for (Condition sourceEventPropertyCondition : sourceEventPropertyConditions){
                    ParserHelper.resolveConditionType(definitionsService, sourceEventPropertyCondition);
                    match = persistenceService.testMatch(sourceEventPropertyCondition, source);
                    if(!match){
                        break;
                    }
                }
                if(match){
                    trackedConditions.add(trackedCondition);
                }
            }
        }
        return trackedConditions;
    }

    public void removeRule(String ruleId) {
        persistenceService.remove(ruleId, Rule.class);
    }

    private void initializeTimer() {
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                allRules = getAllRules();
            }
        };
        timer.scheduleAtFixedRate(task, 0, 1000);
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
