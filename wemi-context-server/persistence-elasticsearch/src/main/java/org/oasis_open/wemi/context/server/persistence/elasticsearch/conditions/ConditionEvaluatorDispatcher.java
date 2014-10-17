package org.oasis_open.wemi.context.server.persistence.elasticsearch.conditions;

import org.oasis_open.wemi.context.server.api.Item;
import org.oasis_open.wemi.context.server.api.conditions.Condition;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Collection;

/**
 * Entry point for condition evaluation. Will dispatch to all evaluators.
 */
public class ConditionEvaluatorDispatcher {

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean eval(Condition condition, Item item) {
        Collection<ServiceReference<ConditionEvaluator>> matchConditionEvaluators = null;
        if (condition.getConditionType().getConditionEvaluator() == null) {
            throw new UnsupportedOperationException("No evaluator defined for : "+condition.getConditionTypeId());
        }
        try {
            matchConditionEvaluators = bundleContext.getServiceReferences(ConditionEvaluator.class, condition.getConditionType().getConditionEvaluator());
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }
        // despite multiple references possible, we will only execute the first one
        for (ServiceReference<ConditionEvaluator> evaluatorServiceReference : matchConditionEvaluators) {
            ConditionEvaluator evaluator = bundleContext.getService(evaluatorServiceReference);
            return evaluator.eval(condition, item, this);
        }
        // if no matching
        return true;

    }
}
