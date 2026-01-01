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

package org.apache.unomi.persistence.opensearch;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.TypeResolutionService;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.dispatcher.ConditionQueryBuilderDispatcher;
import org.apache.unomi.scripting.ScriptExecutor;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dispatcher responsible for routing condition query building to the appropriate
 * OpenSearch-specific {@link ConditionOSQueryBuilder} implementation.
 * <p>
 * Responsibilities:
 * - Maintain a registry of available query builders by their IDs
 * - Resolve legacy queryBuilder IDs to the canonical IDs using centralized mapping in
 *   {@link org.apache.unomi.persistence.spi.conditions.dispatcher.ConditionQueryBuilderDispatcher}
 *   (with deprecation warnings)
 * - Build query fragments (filters) and full queries from {@link org.apache.unomi.api.conditions.Condition}
 * <p>
 * Notes:
 * - Legacy mappings are centralized in SPI support; there is no runtime customization
 * - New IDs are always preferred; legacy IDs trigger a warning and are mapped transparently
 */
public class ConditionOSQueryBuilderDispatcher extends ConditionQueryBuilderDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionOSQueryBuilderDispatcher.class.getName());

    private Map<String, ConditionOSQueryBuilder> queryBuilders = new ConcurrentHashMap<>();
    private ScriptExecutor scriptExecutor;
    private DefinitionsService definitionsService;

    public ConditionOSQueryBuilderDispatcher() {
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void bindDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
        LOGGER.debug("DefinitionsService bound to ConditionOSQueryBuilderDispatcher");
    }

    public void unbindDefinitionsService(DefinitionsService definitionsService) {
        if (this.definitionsService == definitionsService) {
            this.definitionsService = null;
            LOGGER.debug("DefinitionsService unbound from ConditionOSQueryBuilderDispatcher");
        }
    }

    /**
     * Registers a query builder implementation under the provided ID.
     *
     * @param name       the queryBuilder ID (canonical, non-legacy)
     * @param evaluator  the query builder implementation
     */
    public void addQueryBuilder(String name, ConditionOSQueryBuilder evaluator) {
        queryBuilders.put(name, evaluator);
    }

    public void removeQueryBuilder(String name) {
        queryBuilders.remove(name);
    }

    public String getQuery(Condition condition) {
        return "{\"query\": " + getQueryBuilder(condition).toString() + "}";
    }

    public Query getQueryBuilder(Condition condition) {
        return Query.of(q->q.bool(b->b.must(Query.of(q2 -> q2.matchAll(t -> t))).filter(buildFilter(condition))));
    }

    public Query buildFilter(Condition condition) {
        return buildFilter(condition, new HashMap<>());
    }

    public Query buildFilter(Condition condition, Map<String, Object> context) {
        if (condition == null) {
            throw new IllegalArgumentException("Condition is null, impossible to build filter");
        }

        // Resolve condition type if needed - try to resolve first if definitionsService is available
        if (condition.getConditionType() == null) {
            if (definitionsService != null) {
                TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
                if (typeResolutionService != null) {
                    typeResolutionService.resolveConditionType(condition, "query builder");
                }
            } else {
                LOGGER.debug("DefinitionsService not available, cannot resolve condition type for condition typeID={}", condition.getConditionTypeId());
            }
            // If still null after attempting resolution (or definitionsService was null), return match-all
            if (condition.getConditionType() == null) {
                LOGGER.debug("Condition type is null for condition typeID={}, returning match-all query", condition.getConditionTypeId());
                return Query.of(q -> q.matchAll(t -> t));
            }
        }

        // Resolve effective condition from parent chain if needed
        Condition effectiveCondition = condition;
        if (definitionsService != null) {
            effectiveCondition = ParserHelper.resolveEffectiveCondition(
                condition, definitionsService, context, "query builder");
        }

        // Check if effective condition has a type - if not, return match-all query
        if (effectiveCondition.getConditionType() == null) {
            LOGGER.debug("Effective condition type is null for condition typeID={}, returning match-all query", 
                effectiveCondition.getConditionTypeId());
            return Query.of(q -> q.matchAll(t -> t));
        }

        String queryBuilderKey = effectiveCondition.getConditionType().getQueryBuilder();
        if (queryBuilderKey == null) {
            LOGGER.warn("No query builder defined for condition type: {}, returning match-all query", effectiveCondition.getConditionTypeId());
            return Query.of(q -> q.matchAll(t -> t));
        }

        // Find the appropriate query builder key (new or legacy)
        String finalQueryBuilderKey = findQueryBuilderKey(
                queryBuilderKey,
                effectiveCondition.getConditionTypeId(),
                queryBuilders::containsKey);

        if (finalQueryBuilderKey != null) {
            ConditionOSQueryBuilder queryBuilder = queryBuilders.get(finalQueryBuilderKey);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(effectiveCondition, context, scriptExecutor);
            if (contextualCondition != null) {
                return queryBuilder.buildQuery(contextualCondition, context, this);
            }
        } else {
            // if no matching
            LOGGER.warn("No matching query builder. See debug log level for more information");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("No matching query builder for condition {} and context {}", condition, context);
            }
        }

        return Query.of(q -> q.matchAll(t->t));
    }

    public long count(Condition condition) {
        return count(condition, new HashMap<>());
    }

    public long count(Condition condition, Map<String, Object> context) {
        if (condition == null) {
            throw new IllegalArgumentException("Condition is null, impossible to build filter");
        }

        // Resolve condition type if needed - try to resolve first if definitionsService is available
        if (condition.getConditionType() == null) {
            if (definitionsService != null) {
                TypeResolutionService typeResolutionService = definitionsService.getTypeResolutionService();
                if (typeResolutionService != null) {
                    typeResolutionService.resolveConditionType(condition, "query builder");
                }
            } else {
                LOGGER.debug("DefinitionsService not available, cannot resolve condition type for condition typeID={}", condition.getConditionTypeId());
            }
            // If still null after attempting resolution (or definitionsService was null), throw exception
            // (count operations require a valid condition type)
            if (condition.getConditionType() == null) {
                LOGGER.warn("Condition type is null for condition typeID={}, cannot perform count operation", condition.getConditionTypeId());
                throw new IllegalArgumentException("Condition doesn't have type, impossible to build filter for count");
            }
        }

        // Resolve effective condition from parent chain if needed
        Condition effectiveCondition = condition;
        if (definitionsService != null) {
            effectiveCondition = ParserHelper.resolveEffectiveCondition(
                condition, definitionsService, context, "query builder");
        }

        // Check if effective condition has a type - if not, throw exception for count
        if (effectiveCondition.getConditionType() == null) {
            LOGGER.warn("Effective condition type is null for condition typeID={}, cannot perform count operation", 
                effectiveCondition.getConditionTypeId());
            throw new IllegalArgumentException("Effective condition type not resolved for : " + effectiveCondition.getConditionTypeId());
        }

        String queryBuilderKey = effectiveCondition.getConditionType().getQueryBuilder();
        if (queryBuilderKey == null) {
            LOGGER.warn("No query builder defined for condition type: {}, cannot perform count operation", effectiveCondition.getConditionTypeId());
            throw new UnsupportedOperationException("No query builder defined for : " + effectiveCondition.getConditionTypeId());
        }

        // Find the appropriate query builder key (new or legacy)
        String finalQueryBuilderKey = findQueryBuilderKey(
                queryBuilderKey,
                effectiveCondition.getConditionTypeId(),
                queryBuilders::containsKey);

        if (finalQueryBuilderKey != null) {
            ConditionOSQueryBuilder queryBuilder = queryBuilders.get(finalQueryBuilderKey);
            Condition contextualCondition = ConditionContextHelper.getContextualCondition(effectiveCondition, context, scriptExecutor);
            if (contextualCondition != null) {
                return queryBuilder.count(contextualCondition, context, this);
            }
        }

        // if no matching
        LOGGER.warn("No matching query builder. See debug log level for more information");
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("No matching query builder for condition {} and context {}", condition, context);
        }
        throw new UnsupportedOperationException();
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }

}
