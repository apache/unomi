package org.oasis_open.contextserver.persistence.elasticsearch.conditions;

/*
 * #%L
 * context-server-persistence-elasticsearch-core
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

import org.oasis_open.contextserver.api.Item;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Entry point for condition evaluation. Will dispatch to all evaluators.
 */
public class ConditionEvaluatorDispatcher {
    private static final Logger logger = LoggerFactory.getLogger(ConditionEvaluatorDispatcher.class.getName());

    private BundleContext bundleContext;

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean eval(Condition condition, Item item) {
        return eval(condition, item, new HashMap<String, Object>());
    }

    public boolean eval(Condition condition, Item item, Map<String, Object> context) {
        if (condition.getConditionType().getParentCondition() != null) {
            context.putAll(condition.getParameterValues());
            return eval(condition.getConditionType().getParentCondition(), item, context);
        }
        Collection<ServiceReference<ConditionEvaluator>> matchConditionEvaluators = null;
        if (condition.getConditionType().getConditionEvaluator() == null) {
            throw new UnsupportedOperationException("No evaluator defined for : " + condition.getConditionTypeId());
        }
        try {
            matchConditionEvaluators = bundleContext.getServiceReferences(ConditionEvaluator.class, condition.getConditionType().getConditionEvaluator());
        } catch (InvalidSyntaxException e) {
            logger.error("Invalid filter",e);
        }
        // despite multiple references possible, we will only execute the first one
        for (ServiceReference<ConditionEvaluator> evaluatorServiceReference : matchConditionEvaluators) {
            ConditionEvaluator evaluator = bundleContext.getService(evaluatorServiceReference);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(condition, context);
            if (contextualCondition != null) {
                return evaluator.eval(contextualCondition, item, context, this);
            }
        }
        // if no matching
        return true;

    }
}
