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

package org.apache.unomi.persistence.elasticsearch;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.apache.unomi.api.conditions.Condition;

import java.util.Map;

/**
 * SPI for building Elasticsearch {@link Query} objects from Unomi {@link org.apache.unomi.api.conditions.Condition}
 * instances. Implementations translate high-level conditions into ES queries and may optionally provide a
 * {@link #count(Condition, Map, ConditionESQueryBuilderDispatcher)} strategy when needed by callers.
 */
public interface ConditionESQueryBuilder {

    /**
     * Builds an Elasticsearch {@link Query} from the provided Unomi {@link Condition}.
     * Implementations may use the {@code context} (e.g., resolved parameters) and delegate to
     * the {@code dispatcher} for nested/child conditions.
     *
     * @param condition   the condition to translate
     * @param context     additional context for parameter resolution and sub-builders
     * @param dispatcher  dispatcher to build sub-conditions when composing queries
     * @return a concrete Elasticsearch {@link Query}
     */
    Query buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher);

    /**
     * Optionally returns a fast count for the provided condition using an implementation-specific
     * strategy. Default throws {@link UnsupportedOperationException}; override when a specialized
     * count path exists.
     *
     * @param condition   the condition to count
     * @param context     additional context for parameter resolution
     * @param dispatcher  dispatcher to count sub-conditions if needed
     * @return the number of matching documents
     */
    default long count(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        throw new UnsupportedOperationException();
    }
}
