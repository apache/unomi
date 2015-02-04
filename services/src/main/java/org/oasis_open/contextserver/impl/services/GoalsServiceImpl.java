package org.oasis_open.contextserver.impl.services;

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.actions.Action;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.conditions.ConditionType;
import org.oasis_open.contextserver.api.goals.Goal;
import org.oasis_open.contextserver.api.goals.GoalReport;
import org.oasis_open.contextserver.api.rules.Rule;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.GoalsService;
import org.oasis_open.contextserver.api.services.RulesService;
import org.oasis_open.contextserver.persistence.spi.CustomObjectMapper;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.DateAggregate;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;
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

    private Map<Tag, Set<Goal>> goalByTag = new HashMap<>();

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
                if (goal.getMetadata().getScope() == null) {
                    goal.getMetadata().setScope("systemscope");
                }
                if (getGoal(goal.getMetadata().getScope(), goal.getMetadata().getId()) == null) {
                    for (String tagId : goal.getMetadata().getTags()) {
                        Tag tag = definitionsService.getTag(tagId);
                        if (tag != null) {
                            Set<Goal> goals = goalByTag.get(tag);
                            if (goals == null) {
                                goals = new LinkedHashSet<>();
                            }
                            goals.add(goal);
                            goalByTag.put(tag, goals);
                        } else {
                            // we found a tag that is not defined, we will define it automatically
                            logger.warn("Unknown tag " + tagId + " used in goal definition " + predefinedGoalURL);
                        }
                    }

                    setGoal(goal);
                }
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedGoalURL, e);
            }
        }
    }
//
//    private void loadPredefinedCampaigns(BundleContext bundleContext) {
//        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/wemi/campaigns", "*.json", true);
//        if (predefinedRuleEntries == null) {
//            return;
//        }
//        while (predefinedRuleEntries.hasMoreElements()) {
//            URL predefinedCampaignURL = predefinedRuleEntries.nextElement();
//            logger.debug("Found predefined campaigns at " + predefinedCampaignURL + ", loading... ");
//
//            try {
//                Campaign campaign = CustomObjectMapper.getObjectMapper().readValue(predefinedCampaignURL, Campaign.class);
//                if (getCampaign(campaign.getMetadata().getScope(), campaign.getMetadata().getId()) == null) {
//                    setCampaign(campaign);
//                }
//            } catch (IOException e) {
//                logger.error("Error while loading segment definition " + predefinedCampaignURL, e);
//            }
//        }
//    }

    private void createRule(Goal goal, Condition event, String id, boolean testStart) {
        Rule rule = new Rule(new Metadata(goal.getMetadata().getScope(), goal.getMetadata().getId() + id + "Event", "Auto generated rule for goal " + goal.getMetadata().getName(), ""));
        Condition res = new Condition();
        List<Condition> subConditions = new ArrayList<Condition>();
        res.setConditionType(definitionsService.getConditionType("booleanCondition"));
        res.getParameterValues().put("operator", "and");
        res.getParameterValues().put("subConditions", subConditions);

        subConditions.add(event);

        Condition notExist = new Condition();
        notExist.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        notExist.getParameterValues().put("propertyName", "properties." + goal.getMetadata().getId() + id + "Reached");
        notExist.getParameterValues().put("comparisonOperator", "missing");
        subConditions.add(notExist);

        if (testStart) {
            Condition startExists = new Condition();
            startExists.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            startExists.getParameterValues().put("propertyName", "properties." + goal.getMetadata().getId() + "StartReached");
            startExists.getParameterValues().put("comparisonOperator", "exists");
            subConditions.add(startExists);
        }

        rule.setCondition(res);
        rule.getMetadata().setHidden(true);
        Action action1 = new Action();
        action1.setActionType(definitionsService.getActionType("setPropertyAction"));
        String name = goal.getMetadata().getId() + id + "Reached";
        action1.getParameterValues().put("setPropertyName", name);
        action1.getParameterValues().put("setPropertyValue", "now");
        action1.getParameterValues().put("storeInSession", true);
        Action action2 = new Action();
        action2.setActionType(definitionsService.getActionType("setPropertyAction"));
        action2.getParameterValues().put("setPropertyName", name);
        action2.getParameterValues().put("setPropertyValue", "script::profile.properties.?"+name+" != null ? (profile.properties."+name+") : 'now'");
        action2.getParameterValues().put("storeInSession", false);
        rule.setActions(Arrays.asList(action1,action2));
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
    public void removeGoal(String scope, String goalId) {
        String idWithScope = Metadata.getIdWithScope(scope, goalId);
        persistenceService.remove(idWithScope, Goal.class);
    }

    @Override
    public void setGoal(Goal goal) {
        ParserHelper.resolveConditionType(definitionsService, goal.getStartEvent());
        ParserHelper.resolveConditionType(definitionsService, goal.getTargetEvent());

        if (goal.getMetadata().isEnabled()) {
            if (goal.getStartEvent() != null) {
                createRule(goal, goal.getStartEvent(), "Start", false);
            }
            if (goal.getTargetEvent() != null) {
                createRule(goal, goal.getTargetEvent(), "Target", goal.getStartEvent() != null);
            }
        }

        persistenceService.save(goal);
    }

    public GoalReport getGoalReport(String scope, String goalId) {
        return getGoalReport(scope, goalId, null, null);
    }

    public GoalReport getGoalReport(String scope, String goalId, String split) {
        return getGoalReport(scope, goalId, split, null);
    }

    public GoalReport getGoalReport(String scope, String goalId, String split, Condition filter) {
        Condition condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        final ArrayList<Condition> list = new ArrayList<Condition>();
        condition.getParameterValues().put("operator", "and");
        condition.getParameterValues().put("subConditions", list);

        Goal g = getGoal(scope, goalId);

        Condition goalTargetCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        goalTargetCondition.getParameterValues().put("propertyName", goalId+ "TargetReached");
        goalTargetCondition.getParameterValues().put("comparisonOperator", "exists");

        Condition goalStartCondition;
        if (g.getStartEvent() == null) {
            goalStartCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        } else {
            goalStartCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
            goalStartCondition.getParameterValues().put("propertyName", goalId + "StartReached");
            goalStartCondition.getParameterValues().put("comparisonOperator", "exists");
        }

        if (filter != null) {
            list.add(filter);
        }

        Map<String, Long> all;
        Map<String, Long> match;

        if ("timeStamp".equals(split)) {
            list.add(goalStartCondition);
            all = persistenceService.aggregateQuery(condition, new DateAggregate("timeStamp"), Session.ITEM_TYPE);

            list.remove(goalStartCondition);
            list.add(goalTargetCondition);
            match = persistenceService.aggregateQuery(condition, new DateAggregate("timeStamp"), Session.ITEM_TYPE);
        } else if (split != null) {
            list.add(goalStartCondition);
            all = persistenceService.aggregateQuery(condition, new TermsAggregate(split), Session.ITEM_TYPE);

            list.remove(goalStartCondition);
            list.add(goalTargetCondition);
            match = persistenceService.aggregateQuery(condition, new TermsAggregate(split), Session.ITEM_TYPE);
        } else {
            list.add(goalStartCondition);
            all = new HashMap<String, Long>();
            all.put("_filtered", persistenceService.queryCount(condition, Session.ITEM_TYPE));

            list.remove(goalStartCondition);
            list.add(goalTargetCondition);
            match = new HashMap<String, Long>();
            match.put("_filtered", persistenceService.queryCount(condition, Session.ITEM_TYPE));
        }

        GoalReport report = new GoalReport();

        GoalReport.Stat stat = new GoalReport.Stat();
        stat.setStartCount(all.remove("_filtered"));
        stat.setTargetCount(match.remove("_filtered"));
        stat.setConversionRate(stat.getStartCount() > 0 ? (float) stat.getTargetCount() / (float) stat.getStartCount() : 0);
        report.setGlobalStats(stat);
        all.remove("_all");
        report.setSplit(new LinkedHashMap<String, GoalReport.Stat>());
        for (Map.Entry<String, Long> entry : all.entrySet()) {
            GoalReport.Stat dateStat = new GoalReport.Stat();
            dateStat.setKey(entry.getKey());
            dateStat.setStartCount(entry.getValue());
            dateStat.setTargetCount(match.containsKey(entry.getKey()) ? match.get(entry.getKey()) : 0);
            dateStat.setConversionRate(dateStat.getStartCount() > 0 ? (float) dateStat.getTargetCount() / (float) dateStat.getStartCount() : 0);
            dateStat.setPercentage(stat.getTargetCount() > 0 ? (float) dateStat.getTargetCount() / (float) stat.getTargetCount() : 0);
            report.getSplit().put(entry.getKey(), dateStat);
        }

        return report;
    }

    public Set<Goal> getGoalByTag(Tag tag, boolean recursive) {
        Set<Goal> goals = new LinkedHashSet<>();
        Set<Goal> directGoals = goalByTag.get(tag);
        if (directGoals != null) {
            goals.addAll(directGoals);
        }
        if (recursive) {
            for (Tag subTag : tag.getSubTags()) {
                Set<Goal> childGoals = getGoalByTag(subTag, true);
                goals.addAll(childGoals);
            }
        }
        return goals;
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
