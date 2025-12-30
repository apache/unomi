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

package org.apache.unomi.persistence.spi.conditions.dispatcher;

import org.slf4j.Logger;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Abstract base class for condition query builder dispatchers (ES/OS). Centralizes logic that is
 * backend-agnostic: legacy ID mapping with logging, and queryBuilder key resolution.
 * The legacy-to-new queryBuilder identifiers are centralized here in
 * {@link #LEGACY_TO_NEW_QUERY_BUILDER_IDS} and are used by the
 * {@link #findQueryBuilderKey(String, String, java.util.function.Predicate)} method.
 * This abstract class intentionally avoids any dependency on backend-specific query types.
 */
public abstract class ConditionQueryBuilderDispatcher {

    /**
     * Backend-agnostic legacy-to-new mapping of queryBuilder identifiers.
     */
    public static final Map<String, String> LEGACY_TO_NEW_QUERY_BUILDER_IDS = Map.ofEntries(
            Map.entry("idsConditionESQueryBuilder", "idsConditionQueryBuilder"),
            Map.entry("geoLocationByPointSessionConditionESQueryBuilder", "geoLocationByPointSessionConditionQueryBuilder"),
            Map.entry("pastEventConditionESQueryBuilder", "pastEventConditionQueryBuilder"),
            Map.entry("booleanConditionESQueryBuilder", "booleanConditionQueryBuilder"),
            Map.entry("notConditionESQueryBuilder", "notConditionQueryBuilder"),
            Map.entry("matchAllConditionESQueryBuilder", "matchAllConditionQueryBuilder"),
            Map.entry("propertyConditionESQueryBuilder", "propertyConditionQueryBuilder"),
            Map.entry("sourceEventPropertyConditionESQueryBuilder", "sourceEventPropertyConditionQueryBuilder"),
            Map.entry("nestedConditionESQueryBuilder", "nestedConditionQueryBuilder")
    );

    /**
     * Returns the logger instance for the concrete dispatcher implementation.
     *
     * @return the logger instance
     */
    protected abstract Logger getLogger();

    /**
     * Resolves a legacy queryBuilder identifier to its new canonical identifier and logs a deprecation warning.
     * Returns {@code null} if the provided identifier is not legacy-mapped.
     */
    private String resolveLegacyQueryBuilderId(String queryBuilderId, String conditionTypeId) {
        if (!LEGACY_TO_NEW_QUERY_BUILDER_IDS.containsKey(queryBuilderId)) {
            return null;
        }
        String mappedId = LEGACY_TO_NEW_QUERY_BUILDER_IDS.get(queryBuilderId);
        getLogger().warn("DEPRECATED: Using legacy queryBuilderId '{}' for condition type '{}'. Please update your condition definition to use the new queryBuilderId '{}'. Legacy mappings are deprecated and may be removed in future versions.",
                queryBuilderId, conditionTypeId, mappedId);
        return mappedId;
    }

    /**
     * Resolves the final queryBuilder key to use, trying the provided key first, then applying legacy mapping.
     * The {@code hasBuilder} predicate is used to test the presence of a builder for a given key.
     */
    public String findQueryBuilderKey(String queryBuilderKey, String conditionTypeId,
                                      Predicate<String> hasBuilder) {
        if (hasBuilder.test(queryBuilderKey)) {
            return queryBuilderKey;
        }
        String legacyMappedId = resolveLegacyQueryBuilderId(queryBuilderKey, conditionTypeId);
        if (legacyMappedId != null && hasBuilder.test(legacyMappedId)) {
            return legacyMappedId;
        }
        return null;
    }
}

