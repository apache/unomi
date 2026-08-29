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

package org.apache.unomi.api.services;

import org.apache.unomi.api.Item;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.AggregateQuery;

import java.util.Map;

/**
 * Runs stored queries and aggregations against the persistence layer.
 * Complements segment search with lower-level query and aggregate access.
 */
public interface QueryService {

    /**
     * Counts items of the given type grouped by a property's distinct values.
     *
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @param property property to aggregate on
     * @return map of property value to item count
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Long> getAggregate(String itemType, String property);

    /**
     * Counts items of the given type grouped by property values, optionally filtered by an aggregate query.
     * Also returns the global document count when {@code query} is {@code null}.
     *
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @param property property to aggregate on
     * @param query optional aggregate query, or {@code null} for simple aggregation
     * @return map of property value to item count
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     * @deprecated As of 1.3.0-incubating, please use {@link #getAggregateWithOptimizedQuery(String, String, AggregateQuery)} instead
     */
    @Deprecated
    Map<String, Long> getAggregate(String itemType, String property, AggregateQuery query);

    /**
     * Counts items of the given type grouped by property values using an optimized aggregate query.
     * Does not return a global document count.
     *
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @param property property to aggregate on
     * @param query aggregate query defining the aggregation
     * @return map of property value to item count
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Long> getAggregateWithOptimizedQuery(String itemType, String property, AggregateQuery query);

    /**
     * Counts items of the given type that match a condition.
     *
     * @param condition filter condition
     * @param itemType item type name from the item class {@code ITEM_TYPE} field
     * @return matching item count
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long getQueryCount(String itemType, Condition condition);

    /**
     * Computes numeric metrics (sum, avg, min, max) for a field on items matching a condition.
     *
     * @param condition filter condition
     * @param slashConcatenatedMetrics metrics to compute, separated by {@code /} ({@code sum}, {@code avg}, {@code min}, {@code max})
     * @param property field name to aggregate
     * @param type item type name from the item class {@code ITEM_TYPE} field
     * @return map of metric name to computed value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Double> getMetric(String type, String property, String slashConcatenatedMetrics, Condition condition);

}
