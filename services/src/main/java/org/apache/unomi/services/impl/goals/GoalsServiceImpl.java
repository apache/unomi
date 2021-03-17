/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.unomi.services.impl.goals;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.campaigns.Campaign;
import org.apache.unomi.api.campaigns.CampaignDetail;
import org.apache.unomi.api.campaigns.events.CampaignEvent;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.goals.GoalReport;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.GoalsService;
import org.apache.unomi.api.services.RulesService;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.*;
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;


public class GoalsServiceImpl implements GoalsService, SynchronousBundleListener {
    private static final Logger logger = LoggerFactory.getLogger(GoalsServiceImpl.class.getName());

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
        loadPredefinedCampaigns(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                loadPredefinedGoals(bundle.getBundleContext());
                loadPredefinedCampaigns(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        logger.info("Goal service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Goal service shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedGoals(bundleContext);
        loadPredefinedCampaigns(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
    }

    private void loadPredefinedGoals(BundleContext bundleContext) {
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/cxs/goals", "*.json", true);
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

                setGoal(goal);
                logger.info("Predefined goal with id {} registered", goal.getMetadata().getId());
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedGoalURL, e);
            }
        }
    }

    private void createRule(Goal goal, Condition event, String id, boolean testStart) {
        Rule rule = new Rule(new Metadata(goal.getMetadata().getScope(), goal.getMetadata().getId() + id + "Event", "Auto generated rule for goal " + goal.getMetadata().getName(), ""));
        Condition res = new Condition();
        List<Condition> subConditions = new ArrayList<Condition>();
        res.setConditionType(definitionsService.getConditionType("booleanCondition"));
        res.setParameter("operator", "and");
        res.setParameter("subConditions", subConditions);

        subConditions.add(event);

        Condition notExist = new Condition();
        notExist.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        notExist.setParameter("propertyName", "systemProperties.goals." + goal.getMetadata().getId() + id + "Reached");
        notExist.setParameter("comparisonOperator", "missing");
        subConditions.add(notExist);

        if (testStart) {
            Condition startExists = new Condition();
            startExists.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            startExists.setParameter("propertyName", "systemProperties.goals." + goal.getMetadata().getId() + "StartReached");
            startExists.setParameter("comparisonOperator", "exists");
            subConditions.add(startExists);
        }

        if (goal.getCampaignId() != null) {
            Condition engagedInCampaign = new Condition();
            engagedInCampaign.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            engagedInCampaign.setParameter("propertyName", "systemProperties.campaigns." + goal.getCampaignId() + "Engaged");
            engagedInCampaign.setParameter("comparisonOperator", "exists");
            subConditions.add(engagedInCampaign);
        }

        rule.setCondition(res);
        rule.getMetadata().setHidden(true);
        Action action1 = new Action();
        action1.setActionType(definitionsService.getActionType("setPropertyAction"));
        String name = "systemProperties.goals." + goal.getMetadata().getId() + id + "Reached";
        action1.setParameter("setPropertyName", name);
        action1.setParameter("setPropertyValue", "now");
        action1.setParameter("storeInSession", true);
        Action action2 = new Action();
        action2.setActionType(definitionsService.getActionType("setPropertyAction"));
        action2.setParameter("setPropertyName", name);
        action2.setParameter("setPropertyValue", "script::profile.properties.?"+name+" != null ? (profile.properties."+name+") : 'now'");
        action2.setParameter("storeInSession", false);
        rule.setActions(Arrays.asList(action1, action2));

        if (id.equals("Target")) {
            Action action3 = new Action();
            action3.setActionType(definitionsService.getActionType("sendEventAction"));
            action3.setParameter("eventType", "goal");
            action3.setParameter("eventTarget", goal);
            action3.setParameter("eventProperties", new HashMap<String, Object>());
            rule.setActions(Arrays.asList(action1,action2,action3));
        }

        rulesService.setRule(rule);
    }

    public Set<Metadata> getGoalMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Goal definition : persistenceService.getAllItems(Goal.class, 0, 50, null).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Set<Metadata> getGoalMetadatas(Query query) {
        definitionsService.resolveConditionType(query.getCondition());
        Set<Metadata> descriptions = new LinkedHashSet<>();

        List<Goal> goals = persistenceService.query(query.getCondition(), query.getSortby(), Goal.class, query.getOffset(), query.getLimit()).getList();
        for (Goal definition : goals) {
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
    public void removeGoal(String goalId) {
        persistenceService.remove(goalId, Goal.class);
        rulesService.removeRule(goalId + "StartEvent");
        rulesService.removeRule(goalId + "TargetEvent");
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
        } else {
            rulesService.removeRule(goal.getMetadata().getId() + "StartEvent");
            rulesService.removeRule(goal.getMetadata().getId() + "TargetEvent");
        }

        persistenceService.save(goal);
    }

    public Set<Metadata> getCampaignGoalMetadatas(String campaignId) {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Goal definition : persistenceService.query("campaignId", campaignId, null, Goal.class,0,50).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    private void loadPredefinedCampaigns(BundleContext bundleContext) {
        Enumeration<URL> predefinedRuleEntries = bundleContext.getBundle().findEntries("META-INF/cxs/campaigns", "*.json", true);
        if (predefinedRuleEntries == null) {
            return;
        }

        while (predefinedRuleEntries.hasMoreElements()) {
            URL predefinedCampaignURL = predefinedRuleEntries.nextElement();
            logger.debug("Found predefined campaigns at " + predefinedCampaignURL + ", loading... ");

            try {
                Campaign campaign = CustomObjectMapper.getObjectMapper().readValue(predefinedCampaignURL, Campaign.class);
                setCampaign(campaign);
                logger.info("Predefined campaign with id {} registered", campaign.getMetadata().getId());
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedCampaignURL, e);
            }
        }
    }

    private void createRule(Campaign campaign, Condition event) {
        Rule rule = new Rule(new Metadata(campaign.getMetadata().getScope(), campaign.getMetadata().getId() + "EntryEvent", "Auto generated rule for campaign " + campaign.getMetadata().getName(), ""));
        Condition res = new Condition();
        List<Condition> subConditions = new ArrayList<Condition>();
        res.setConditionType(definitionsService.getConditionType("booleanCondition"));
        res.setParameter("operator", "and");
        res.setParameter("subConditions", subConditions);

        if (campaign.getStartDate() != null) {
            Condition startCondition = new Condition();
            startCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            startCondition.setParameter("propertyName", "timeStamp");
            startCondition.setParameter("comparisonOperator", "greaterThan");
            startCondition.setParameter("propertyValueDate", campaign.getStartDate());
            subConditions.add(startCondition);
        }

        if (campaign.getEndDate() != null) {
            Condition endCondition = new Condition();
            endCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            endCondition.setParameter("propertyName", "timeStamp");
            endCondition.setParameter("comparisonOperator", "lessThan");
            endCondition.setParameter("propertyValueDate", campaign.getEndDate());
            subConditions.add(endCondition);
        }

        rule.setPriority(-5);

        subConditions.add(event);

        rule.setCondition(res);
        rule.getMetadata().setHidden(true);
        Action action1 = new Action();
        action1.setActionType(definitionsService.getActionType("setPropertyAction"));
        String name = "systemProperties.campaigns." + campaign.getMetadata().getId() + "Engaged";
        action1.setParameter("setPropertyName", name);
        action1.setParameter("setPropertyValue", "now");
        action1.setParameter("storeInSession", true);
        Action action2 = new Action();
        action2.setActionType(definitionsService.getActionType("setPropertyAction"));
        action2.setParameter("setPropertyName", name);
        action2.setParameter("setPropertyValue", "script::profile.properties.?"+name+" != null ? (profile.properties."+name+") : 'now'");
        action2.setParameter("storeInSession", false);
        rule.setActions(Arrays.asList(action1,action2));
        rulesService.setRule(rule);
    }


    public Set<Metadata> getCampaignMetadatas() {
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Campaign definition : persistenceService.getAllItems(Campaign.class, 0, 50, null).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public Set<Metadata> getCampaignMetadatas(Query query) {
        definitionsService.resolveConditionType(query.getCondition());
        Set<Metadata> descriptions = new HashSet<Metadata>();
        for (Campaign definition : persistenceService.query(query.getCondition(), query.getSortby(), Campaign.class, query.getOffset(), query.getLimit()).getList()) {
            descriptions.add(definition.getMetadata());
        }
        return descriptions;
    }

    public PartialList<CampaignDetail> getCampaignDetails(Query query) {
        definitionsService.resolveConditionType(query.getCondition());
        PartialList<Campaign> campaigns = persistenceService.query(query.getCondition(), query.getSortby(), Campaign.class, query.getOffset(), query.getLimit());
        List<CampaignDetail> details = new LinkedList<>();
        for (Campaign definition : campaigns.getList()) {
            final CampaignDetail campaignDetail = getCampaignDetail(definition);
            if (campaignDetail != null) {
                details.add(campaignDetail);
            }
        }
        return new PartialList<>(details, campaigns.getOffset(), campaigns.getPageSize(), campaigns.getTotalSize(), campaigns.getTotalSizeRelation());
    }

    public CampaignDetail getCampaignDetail(String id) {
        return getCampaignDetail(getCampaign(id));
    }

    private CampaignDetail getCampaignDetail(Campaign campaign) {
        if (campaign == null) {
            return null;
        }

        CampaignDetail campaignDetail = new CampaignDetail(campaign);

        // engaged profile
        Condition profileEngagedCondition = new Condition(definitionsService.getConditionType("profilePropertyCondition"));
        profileEngagedCondition.setParameter("propertyName", "systemProperties.campaigns." + campaign.getMetadata().getId() + "Engaged");
        profileEngagedCondition.setParameter("comparisonOperator", "exists");
        campaignDetail.setEngagedProfiles(persistenceService.queryCount(profileEngagedCondition, Profile.ITEM_TYPE));

        // number of goals
        Condition campaignGoalsCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        campaignGoalsCondition.setParameter("propertyName", "campaignId");
        campaignGoalsCondition.setParameter("comparisonOperator", "equals");
        campaignGoalsCondition.setParameter("propertyValue", campaign.getMetadata().getId());
        campaignDetail.setNumberOfGoals(persistenceService.queryCount(campaignGoalsCondition, Goal.ITEM_TYPE));

        // sessions
        Condition sessionEngagedCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        sessionEngagedCondition.setParameter("propertyName", "systemProperties.campaigns." + campaign.getMetadata().getId() + "Engaged");
        sessionEngagedCondition.setParameter("comparisonOperator", "exists");
        campaignDetail.setCampaignSessionViews(persistenceService.queryCount(sessionEngagedCondition, Session.ITEM_TYPE));

        // sessions
        Condition sessionConvertedCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        sessionConvertedCondition.setParameter("propertyName", "systemProperties.goals." + campaign.getPrimaryGoal() + "TargetReached");
        sessionConvertedCondition.setParameter("comparisonOperator", "exists");
        campaignDetail.setCampaignSessionSuccess(persistenceService.queryCount(sessionConvertedCondition, Session.ITEM_TYPE));

        // conversion
        campaignDetail.setConversionRate((double) campaignDetail.getCampaignSessionSuccess() / (campaignDetail.getCampaignSessionViews() > 0  ? (double) campaignDetail.getCampaignSessionViews() : 1));
        return campaignDetail;
    }

    public Campaign getCampaign(String id) {
        Campaign campaign = persistenceService.load(id, Campaign.class);
        if (campaign != null) {
            ParserHelper.resolveConditionType(definitionsService, campaign.getEntryCondition());
        }
        return campaign;
    }

    public void removeCampaign(String id) {
        for(Metadata m : getCampaignGoalMetadatas(id)) {
            removeGoal(m.getId());
        }
        rulesService.removeRule(id + "EntryEvent");
        persistenceService.remove(id, Campaign.class);
    }

    public void setCampaign(Campaign campaign) {
        ParserHelper.resolveConditionType(definitionsService, campaign.getEntryCondition());

        if(rulesService.getRule(campaign.getMetadata().getId() + "EntryEvent") != null) {
            rulesService.removeRule(campaign.getMetadata().getId() + "EntryEvent");
        }

        if (campaign.getMetadata().isEnabled()) {
            if (campaign.getEntryCondition() != null) {
                createRule(campaign, campaign.getEntryCondition());
            }
        }

        persistenceService.save(campaign);
    }

    public GoalReport getGoalReport(String goalId) {
        return getGoalReport(goalId, null);
    }

    public GoalReport getGoalReport(String goalId, AggregateQuery query) {
        Condition condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        final ArrayList<Condition> list = new ArrayList<Condition>();
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", list);

        Goal g = getGoal(goalId);

        Condition goalTargetCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        goalTargetCondition.setParameter("propertyName",  "systemProperties.goals." + goalId+ "TargetReached");
        goalTargetCondition.setParameter("comparisonOperator", "exists");

        Condition goalStartCondition;
        if (g.getStartEvent() == null && g.getCampaignId() != null) {
            goalStartCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
            goalStartCondition.setParameter("propertyName", "systemProperties.campaigns." + g.getCampaignId() + "Engaged");
            goalStartCondition.setParameter("comparisonOperator", "exists");
        } else if (g.getStartEvent() == null) {
            goalStartCondition = new Condition(definitionsService.getConditionType("matchAllCondition"));
        } else {
            goalStartCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
            goalStartCondition.setParameter("propertyName", "systemProperties.goals." + goalId + "StartReached");
            goalStartCondition.setParameter("comparisonOperator", "exists");
        }

        if (query != null && query.getCondition() != null) {
            ParserHelper.resolveConditionType(definitionsService, query.getCondition());
            list.add(query.getCondition());
        }

        Map<String, Long> all;
        Map<String, Long> match;

        // resolve aggregate
        BaseAggregate aggregate = null;
        String property = query.getAggregate().getProperty();
        if(query != null && query.getAggregate() != null && property != null) {
            if (query.getAggregate().getType() != null){
                // try to guess the aggregate type
                if(query.getAggregate().getType().equals("date")) {
                    String interval = (String) query.getAggregate().getParameters().get("interval");
                    String format = (String) query.getAggregate().getParameters().get("format");
                    aggregate = new DateAggregate(property, interval, format);
                } else if (query.getAggregate().getType().equals("dateRange") && query.getAggregate().getDateRanges() != null && query.getAggregate().getDateRanges().size() > 0) {
                    String format = (String) query.getAggregate().getParameters().get("format");
                    aggregate = new DateRangeAggregate(property, format, query.getAggregate().getDateRanges());
                } else if (query.getAggregate().getType().equals("numericRange") && query.getAggregate().getNumericRanges() != null && query.getAggregate().getNumericRanges().size() > 0) {
                    aggregate = new NumericRangeAggregate(property, query.getAggregate().getNumericRanges());
                } else if (query.getAggregate().getType().equals("ipRange") && query.getAggregate().ipRanges() != null && query.getAggregate().ipRanges().size() > 0) {
                    aggregate = new IpRangeAggregate(property, query.getAggregate().ipRanges());
                }
            }

            if(aggregate == null){
                aggregate = new TermsAggregate(property);
            }
        }

        if (aggregate != null) {
            list.add(goalStartCondition);
            all = persistenceService.aggregateWithOptimizedQuery(condition, aggregate, Session.ITEM_TYPE);

            list.remove(goalStartCondition);
            list.add(goalTargetCondition);
            match = persistenceService.aggregateWithOptimizedQuery(condition, aggregate, Session.ITEM_TYPE);
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
        Long allFiltered = all.remove("_filtered");
        Long matchFiltered = match.remove("_filtered");
        stat.setStartCount(allFiltered != null ? allFiltered : 0);
        stat.setTargetCount(matchFiltered != null ? matchFiltered : 0);
        stat.setConversionRate(stat.getStartCount() > 0 ? (float) stat.getTargetCount() / (float) stat.getStartCount() : 0);
        report.setGlobalStats(stat);
        all.remove("_all");
        report.setSplit(new LinkedList<GoalReport.Stat>());
        for (Map.Entry<String, Long> entry : all.entrySet()) {
            GoalReport.Stat dateStat = new GoalReport.Stat();
            dateStat.setKey(entry.getKey());
            dateStat.setStartCount(entry.getValue());
            dateStat.setTargetCount(match.containsKey(entry.getKey()) ? match.get(entry.getKey()) : 0);
            dateStat.setConversionRate(dateStat.getStartCount() > 0 ? (float) dateStat.getTargetCount() / (float) dateStat.getStartCount() : 0);
            dateStat.setPercentage(stat.getTargetCount() > 0 ? (float) dateStat.getTargetCount() / (float) stat.getTargetCount() : 0);
            report.getSplit().add(dateStat);
        }

        return report;
    }

    // Campaign Event management methods
    @Override
    public PartialList<CampaignEvent> getEvents(Query query) {
        if(query.isForceRefresh()){
            persistenceService.refresh();
        }
        definitionsService.resolveConditionType(query.getCondition());
        return persistenceService.query(query.getCondition(), query.getSortby(), CampaignEvent.class, query.getOffset(), query.getLimit());
    }

    @Override
    public void setCampaignEvent(CampaignEvent event) {
        persistenceService.save(event);
    }

    @Override
    public void removeCampaignEvent(String campaignEventId) {
        persistenceService.remove(campaignEventId, CampaignEvent.class);
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
