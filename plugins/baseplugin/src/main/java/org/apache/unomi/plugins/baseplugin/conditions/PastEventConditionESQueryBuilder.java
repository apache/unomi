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

package org.apache.unomi.plugins.baseplugin.conditions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;

import java.util.*;

public class PastEventConditionESQueryBuilder implements ConditionESQueryBuilder {

    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;
    private SegmentService segmentService;

    private int maximumIdsQueryCount = 5000;
    private int aggregateQueryBucketSize = 5000;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setMaximumIdsQueryCount(int maximumIdsQueryCount) {
        this.maximumIdsQueryCount = maximumIdsQueryCount;
    }

    public void setAggregateQueryBucketSize(int aggregateQueryBucketSize) {
        this.aggregateQueryBucketSize = aggregateQueryBucketSize;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Integer minimumEventCount = condition.getParameter("minimumEventCount") == null ? 1 : (Integer) condition.getParameter("minimumEventCount");
        Integer maximumEventCount = condition.getParameter("maximumEventCount") == null ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");

        if (condition.getParameter("generatedPropertyKey") != null && condition.getParameter("generatedPropertyKey").equals(segmentService.getGeneratedPropertyKey((Condition) condition.getParameter("eventCondition"), condition))) {
            // A property is already set on profiles matching the past event condition, use it
            if (minimumEventCount != 1 || maximumEventCount != Integer.MAX_VALUE) {
                // Check the number of occurences
                RangeQueryBuilder builder = QueryBuilders.rangeQuery("systemProperties.pastEvents." + condition.getParameter("generatedPropertyKey"));
                if (minimumEventCount != 1) {
                    builder.gte(minimumEventCount);
                }
                if (maximumEventCount != Integer.MAX_VALUE) {
                    builder.lte(minimumEventCount);
                }
                return builder;
            } else {
                // Simply get profiles who have the property set
                return QueryBuilders.existsQuery("systemProperties.pastEvents." + condition.getParameter("generatedPropertyKey"));
            }
        } else {
            // No property set - tries to build an idsQuery
            // Build past event condition
            Condition eventCondition = getEventCondition(condition, context);

            Set<String> ids = new HashSet<>();

            // Get full cardinality to partition the terms aggreggation
            Map<String, Double> m = persistenceService.getSingleValuesMetrics(eventCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE);
            long card = m.get("_card").longValue();

            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(eventCondition, new TermsAggregate("profileId", i, numParts), Event.ITEM_TYPE);
                if (eventCountByProfile != null) {
                    eventCountByProfile.remove("_filtered");
                    for (Map.Entry<String, Long> entry : eventCountByProfile.entrySet()) {
                        if (entry.getValue() < minimumEventCount) {
                            // No more interesting buckets in this partition
                            break;
                        } else if (entry.getValue() <= maximumEventCount) {
                            ids.add(entry.getKey());

                            if (ids.size() > maximumIdsQueryCount) {
                                // Avoid building too big ids query - throw exception instead
                                throw new UnsupportedOperationException("Too many profiles");
                            }
                        }
                    }
                }
            }

            return QueryBuilders.idsQuery().addIds(ids.toArray(new String[0]));
        }
    }

    public long count(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        Condition eventCondition = getEventCondition(condition, context);

        Integer minimumEventCount = condition.getParameter("minimumEventCount") == null ? 1 : (Integer) condition.getParameter("minimumEventCount");
        Integer maximumEventCount = condition.getParameter("maximumEventCount") == null ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");

        // Get full cardinality to partition the terms aggreggation
        Map<String, Double> m = persistenceService.getSingleValuesMetrics(eventCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE);
        long card = m.get("_card").longValue();

        if (minimumEventCount != 1 || maximumEventCount != Integer.MAX_VALUE) {
            // Event count specified, must check occurences count for each profile
            int result = 0;
            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(eventCondition, new TermsAggregate("profileId", i, numParts), Event.ITEM_TYPE);
                int j = 0;
                if (eventCountByProfile != null) {
                    eventCountByProfile.remove("_filtered");
                    for (Map.Entry<String, Long> entry : eventCountByProfile.entrySet()) {
                        if (entry.getValue() < minimumEventCount) {
                            // No more interesting buckets in this partition
                            break;
                        } else if (entry.getValue() <= maximumEventCount && minimumEventCount == 1) {
                            // Take all remaining elements
                            result += eventCountByProfile.size() - j;
                            break;
                        } else if (entry.getValue() <= maximumEventCount) {
                            result++;
                        }
                        j++;
                    }
                }
            }
            return result;
        } else {
            // Simply get the full number of distinct profiles
            return card;
        }
    }

    private Condition getEventCondition(Condition condition, Map<String, Object> context) {
        Condition eventCondition;
        try {
            eventCondition = (Condition) condition.getParameter("eventCondition");
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Empty eventCondition");
        }
        if (eventCondition == null) {
            throw new IllegalArgumentException("No eventCondition");
        }
        List<Condition> l = new ArrayList<Condition>();
        Condition andCondition = new Condition();
        andCondition.setConditionType(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", l);

        l.add(ConditionContextHelper.getContextualCondition(eventCondition, context));

        Integer numberOfDays = (Integer) condition.getParameter("numberOfDays");
        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValueDateExpr", "now-" + numberOfDays + "d");
            l.add(numberOfDaysCondition);
        }
        return andCondition;
    }

}
