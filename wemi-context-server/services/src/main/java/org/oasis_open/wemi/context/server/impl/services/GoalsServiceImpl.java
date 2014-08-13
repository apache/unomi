package org.oasis_open.wemi.context.server.impl.services;

import org.oasis_open.wemi.context.server.api.Event;
import org.oasis_open.wemi.context.server.api.goals.Goal;
import org.oasis_open.wemi.context.server.api.services.DefinitionsService;
import org.oasis_open.wemi.context.server.api.services.GoalsService;
import org.oasis_open.wemi.context.server.persistence.spi.PersistenceService;
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

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    Map<String, Goal> goals = new LinkedHashMap<String, Goal>();

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPredefinedGoals(bundleContext);
        bundleContext.addBundleListener(this);
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
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
                Goal goal = ParserHelper.getObjectMapper().readValue(predefinedGoalURL, Goal.class);

                goal.getStartEvent().setConditionType(definitionsService.getConditionType(goal.getStartEvent().getConditionTypeId()));
                goal.getTargetEvent().setConditionType(definitionsService.getConditionType(goal.getTargetEvent().getConditionTypeId()));

                goals.put(goal.getMetadata().getId(), goal);
            } catch (IOException e) {
                logger.error("Error while loading segment definition " + predefinedGoalURL, e);
            }
        }
    }

    public Set<Goal> getGoals() {
        return new HashSet<Goal>(goals.values());
    }

    public float getGoalSuccessRate(String goalId) {
        Goal goal = goals.get(goalId);

        List<String> eventStart = persistenceService.aggregateQuery(Event.EVENT_ITEM_TYPE, goal.getStartEvent(), "sessionId");
        List<String> eventTarget = persistenceService.aggregateQuery(Event.EVENT_ITEM_TYPE, goal.getTargetEvent(), "sessionId");

        return (float)eventTarget.size() / (float)eventStart.size();
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                loadPredefinedGoals(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                // @todo remove bundle-defined resources (is it possible ?)
                break;
        }
    }

}
