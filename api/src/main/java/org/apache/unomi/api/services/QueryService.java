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
 * A service to perform queries.
 */
public interface QueryService {

    /**
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and aggregated by possible values of the specified
     * property.
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param property the property we're aggregating on, i.e. for each possible value of this property, we are counting how many items of the specified type have that value
     * @return a Map associating a specific value of the property to the cardinality of items with that value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Long> getAggregate(String itemType, String property);

    /**
     * TODO: rework, this method is confusing since it either behaves like {@link #getAggregate(String, String)} if query is null but completely differently if it isn't
     *
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and aggregated by possible values of the specified
     * property or, if the specified query is not {@code null}, perform that aggregate query.
     * Also return the global count of document matching the {@code ITEM_TYPE}
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param property the property we're aggregating on, i.e. for each possible value of this property, we are counting how many items of the specified type have that value
     * @param query    the {@link AggregateQuery} specifying the aggregation that should be perfomed
     * @return a Map associating a specific value of the property to the cardinality of items with that value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     * @deprecated As of 1.3.0-incubating, please use {@link #getAggregateWithOptimizedQuery(String, String, AggregateQuery)} instead
     */
    Map<String, Long> getAggregate(String itemType, String property, AggregateQuery query);

    /**
     * Retrieves the number of items with the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and aggregated by possible values of the specified
     * property or, if the specified query is not {@code null}, perform that aggregate query.
     * This aggregate won't return the global count and should therefore be much faster than {@link #getAggregate(String, String, AggregateQuery)}
     *
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param property the property we're aggregating on, i.e. for each possible value of this property, we are counting how many items of the specified type have that value
     * @param query    the {@link AggregateQuery} specifying the aggregation that should be perfomed
     * @return a Map associating a specific value of the property to the cardinality of items with that value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Long> getAggregateWithOptimizedQuery(String itemType, String property, AggregateQuery query);

    /**
     * Retrieves the number of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the specified {@link Condition}.
     *
     * @param condition the condition the items must satisfy
     * @param itemType  the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return the number of items of the specified type
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    long getQueryCount(String itemType, Condition condition);

    /**
     * Retrieves the specified metrics for the specified field of items of the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and matching the
     * specified {@link Condition}.
     *
     * @param condition                the condition the items must satisfy
     * @param slashConcatenatedMetrics a String specifying which metrics should be computed, separated by a slash ({@code /}) (possible values: {@code sum} for the sum of the
     *                                 values, {@code avg} for the average of the values, {@code min} for the minimum value and {@code max} for the maximum value)
     * @param property                 the name of the field for which the metrics should be computed
     * @param type                     the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return a Map associating computed metric name as key to its associated value
     * @see Item Item for a discussion of {@code ITEM_TYPE}
     */
    Map<String, Double> getMetric(String type, String property, String slashConcatenatedMetrics, Condition condition);

}
