package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Metadata;
import org.oasis_open.wemi.context.server.api.Session;
import org.oasis_open.wemi.context.server.api.actions.Action;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.oasis_open.wemi.context.server.api.goals.Goal;
import org.oasis_open.wemi.context.server.api.goals.GoalReport;
import org.oasis_open.wemi.context.server.api.rules.Rule;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.GoalsService;
import org.oasis_open.wemi.context.server.api.services.RulesService;
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
import java.util.*;


public class GoalsServiceImpl implements GoalsService, BundleListener {
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

    private void loadPredefinedGoals(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/wemi/goals", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }
        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedGoalURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined goals at " + predefinedGoalURL + ", loading... ");

            try {
                Goal goal = MapperHelper.getObjectMapper().readValue(predefinedGoalURL, Goal.class);
                if (getGoal(goal.getMetadata().getId()) == null) {
                    saveGoal(goal);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedGoalURL, e);
            }
        }
    }

    private void saveGoal(Goal goal) {
        ParserHelper.resolveConditionType(definitionsService, goal.getStartEvent());
        ParserHelper.resolveConditionType(definitionsService, goal.getTargetEvent());

        createRule(goal, goal.getStartEvent(), "start");
        createRule(goal, goal.getTargetEvent(), "target");

        persistenceService.save(goal);
    }

    private void createRule(Goal goal, Condition event, String id) {
        Rule r1 = new Rule(new Metadata(goal.getItemId()+ "." + id + "Event", "Auto generated rule for goal "+goal.getMetadata().getName(), ""));
        r1.setCondition(event);
        Action action1 = new Action();
        action1.setActionType(definitionsService.getActionType("setPropertyAction"));
        action1.getParameterValues().put("setPropertyName", goal.getMetadata().getId()+ "." + id + ".reached");
        action1.getParameterValues().put("setPropertyValue", "now");
        action1.getParameterValues().put("storeInSession", true);
        r1.setActions(Arrays.asList(action1));
        rulesService.setRule(r1.getItemId(), r1);
    }

    public Set<Metadata> getGoalMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Goal definition : persistenceService.getAllItems(Goal.class)) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Goal getGoal(String goalId) {
        Goal goal = persistenceService.load(goalId, Goal.class);
        if (goal != null) {
            ParserHelper.resolveConditionType(definitionsService, goal.getStartEvent());
            ParserHelper.resolveConditionType(definitionsService, goal.getTargetEvent());
        }
        return goal;
    }

    @Override
    public void setGoal(String goalId, Goal goal) {
        persistenceService.save(goal);

    }

    @Override
    public void createGoal(String goalId, String name, String description) {
        Metadata metadata = new Metadata(goalId, name, description);
        Goal goal = new Goal(metadata);
        setGoal(goalId, goal);
    }

    @Override
    public void removeGoal(String goalId) {
        persistenceService.remove(goalId, Goal.class);
    }

    public GoalReport getGoalReport(String goalId) {
        return getGoalReport(goalId, null, null);
    }

    public GoalReport getGoalReport(String goalId, String split) {
        return getGoalReport(goalId, split, null);
    }

    public GoalReport getGoalReport(String goalId, String split, Condition filter) {
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

        if ("sessionCreationDate".equals(split)) {
            all = persistenceService.aggregateQuery(condition, "date", "sessionCreationDate", Session.class);
            condition.getParameterValues().put("goalReached", true);
            match = persistenceService.aggregateQuery(condition, "date", "sessionCreationDate", Session.class);
        } else if (split != null) {
            all = persistenceService.aggregateQuery(condition, "terms", split , Session.class);
            condition.getParameterValues().put("goalReached", true);
            match = persistenceService.aggregateQuery(condition, "terms", split, Session.class);
        } else {
            all = new HashMap<String, Long>();
            all.put("_filtered",persistenceService.queryCount(condition, Session.class));
            condition.getParameterValues().put("goalReached", true);
            match = new HashMap<String, Long>();
            match.put("_filtered",persistenceService.queryCount(condition, Session.class));
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
            dateStat.setPercentage( (float) dateStat.getTargetCount() / (float) stat.getTargetCount());
            report.getSplit().put(entry.getKey(), dateStat);
        }

        return report;
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                if (event.getBundle().getBundleContext() != null) {
                    loadPredefinedGoals(event.getBundle().getBundleContext());
                }
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

}
