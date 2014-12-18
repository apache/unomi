package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.Metadata;
import org.oasis_open.contextserver.api.PluginType;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.goals.GoalReport;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.GoalsService;
import org.oasis_open.contextserver.api.services.RulesService;
import org.oasis_open.contextserver.persistence.spi.Aggregate;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;


public class GoalsServiceImpl implements GoalsService, SynchronousBundleListener {
    private static final Logger logger = LoggerFactory.getLogger(RulesServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private RulesService rulesService;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setRulesService(RulesService rulesService) {
        this.rulesService = rulesService;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedGoals(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null) {
                loadPredefinedGoals(bundle.getBundleContext());
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
        loadPredefinedGoals(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = definitionsService.getTypesByPlugin().get(bundleContext.getBundle().getBundleId());
        List<String> removedConditions = new ArrayList<String>();
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof ConditionType) {
                    removedConditions.add(((ConditionType) type).getId());
                }
            }
        }
        if (!removedConditions.isEmpty()) {
            for (Goal goal : persistenceService.getAllItems(Goal.class)) {
                List<String> conditions = ParserHelper.getConditionTypeIds(goal.getStartEvent());
                conditions.addAll(ParserHelper.getConditionTypeIds(goal.getTargetEvent()));

                if (!Collections.disjoint(conditions, removedConditions)) {
                    logger.info("Disable goal " + goal.getItemId());
                    goal.getMetadata().setEnabled(false);
                    setGoal(goal);
                }
            }
        }
    }

    private void loadPredefinedGoals(BundleContext bundleContext) {
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/wemi/goals", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }
        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedGoalURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined goals at " + predefinedGoalURL + ", loading... ");

            try {
                Goal goal = CustomObjectMapper.getObjectMapper().readValue(predefinedGoalURL, Goal.class);
                if (getGoal(goal.getMetadata().getScope(), goal.getMetadata().getId()) == null) {
                    setGoal(goal);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedGoalURL, e);
            }
        }
    }

    private void createRule(Goal goal, Condition event, String id) {
        Rule rule = new Rule(new Metadata(goal.getMetadata().getScope(), goal.getMetadata().getId() + "." + id + "Event", "Auto generated rule for goal " + goal.getMetadata().getName(), ""));
        rule.setCondition(event);
        rule.getMetadata().setHidden(true);
        Action action1 = new Action();
        action1.setActionType(definitionsService.getActionType("setPropertyAction"));
        action1.getParameterValues().put("setPropertyName", goal.getMetadata().getId() + "." + id + ".reached");
        action1.getParameterValues().put("setPropertyValue", "now");
        action1.getParameterValues().put("storeInSession", true);
        rule.setActions(Arrays.asList(action1));
        rulesService.setRule(rule);
    }

    public Set<Metadata> getGoalMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Goal definition : persistenceService.getAllItems(Goal.class, 0, 50, null).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Set<Metadata> getGoalMetadatas(String scope) {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Goal definition : persistenceService.query("scope", scope, null, Goal.class, 0, 50).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Goal getGoal(String scope, String goalId) {
        Goal goal = persistenceService.load(Metadata.getIdWithScope(scope,goalId), Goal.class);
        if (goal != null) {
            ParserHelper.resolveConditionType(definitionsService, goal.getStartEvent());
            ParserHelper.resolveConditionType(definitionsService, goal.getTargetEvent());
        }
        return goal;
    }

    @Override
    public void setGoal(Goal goal) {
        ParserHelper.resolveConditionType(definitionsService, goal.getStartEvent());
        ParserHelper.resolveConditionType(definitionsService, goal.getTargetEvent());

        if (goal.getMetadata().isEnabled()) {
            createRule(goal, goal.getStartEvent(), "start");
            createRule(goal, goal.getTargetEvent(), "target");
        }

        persistenceService.save(goal);
    }

    @Override
    public void createGoal(String scope, String goalId, String name, String description) {
        Metadata metadata = new Metadata(scope, goalId, name, description);
        Goal goal = new Goal(metadata);
        setGoal(goal);
    }

    @Override
    public void removeGoal(String scope, String goalId) {
        persistenceService.remove(goalId, Goal.class);
    }

    public GoalReport getGoalReport(String scope, String goalId) {
        return getGoalReport(scope, goalId, null, null);
    }

    public GoalReport getGoalReport(String scope, String goalId, String split) {
        return getGoalReport(scope, goalId, split, null);
    }

    public GoalReport getGoalReport(String scope, String goalId, String split, Condition filter) {
        Condition condition = new Condition(definitionsService.getConditionType("goalMatchCondition"));
        condition.getParameterValues().put("goalId", goalId);
        condition.getParameterValues().put("goalReached", false);

        if (filter != null) {
            Condition andCondition = new Condition(definitionsService.getConditionType("andCondition"));
            final ArrayList<Condition> list = new ArrayList<Condition>();
            andCondition.getParameterValues().put("subConditions", list);
            list.add(condition);
            list.add(filter);
            condition = andCondition;
        }

        Map<String, Long> all;
        Map<String, Long> match;

        if ("timeStamp".equals(split)) {
            all = persistenceService.aggregateQuery(condition, new Aggregate(Aggregate.Type.DATE, "timeStamp"), Session.ITEM_TYPE);
            condition.getParameterValues().put("goalReached", true);
            match = persistenceService.aggregateQuery(condition, new Aggregate(Aggregate.Type.DATE, "timeStamp"), Session.ITEM_TYPE);
        } else if (split != null) {
            all = persistenceService.aggregateQuery(condition, new Aggregate(Aggregate.Type.TERMS, split), Session.ITEM_TYPE);
            condition.getParameterValues().put("goalReached", true);
            match = persistenceService.aggregateQuery(condition, new Aggregate(Aggregate.Type.TERMS, split), Session.ITEM_TYPE);
        } else {
            all = new HashMap<String, Long>();
            all.put("_filtered", persistenceService.queryCount(condition, Session.ITEM_TYPE));
            condition.getParameterValues().put("goalReached", true);
            match = new HashMap<String, Long>();
            match.put("_filtered", persistenceService.queryCount(condition, Session.ITEM_TYPE));
        }

        GoalReport report = new GoalReport();

        GoalReport.Stat stat = new GoalReport.Stat();
        stat.setStartCount(all.remove("_filtered"));
        stat.setTargetCount(match.remove("_filtered"));
        stat.setConversionRate((float) stat.getTargetCount() / (float) stat.getStartCount());
        report.setGlobalStats(stat);

        report.setSplit(new LinkedHashMap<String, GoalReport.Stat>());
        for (Map.Entry<String, Long> entry : all.entrySet()) {
            GoalReport.Stat dateStat = new GoalReport.Stat();
            dateStat.setKey(entry.getKey());
            dateStat.setStartCount(entry.getValue());
            dateStat.setTargetCount(match.containsKey(entry.getKey()) ? match.get(entry.getKey()) : 0);
            dateStat.setConversionRate((float) dateStat.getTargetCount() / (float) dateStat.getStartCount());
            dateStat.setPercentage((float) dateStat.getTargetCount() / (float) stat.getTargetCount());
            report.getSplit().put(entry.getKey(), dateStat);
        }

        return report;
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
