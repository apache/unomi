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

package org.apache.unomi.persistence.elasticsearch.conditions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.api.utils.ConditionBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.ConditionESQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.persistence.spi.conditions.ConditionContextHelper;
import org.apache.unomi.persistence.spi.conditions.PastEventConditionPersistenceQueryBuilder;
import org.apache.unomi.scripting.ScriptExecutor;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class PastEventConditionESQueryBuilder implements ConditionESQueryBuilder, PastEventConditionPersistenceQueryBuilder {

    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;
    private SegmentService segmentService;
    private ScriptExecutor scriptExecutor;

    private int maximumIdsQueryCount = 5000;
    private int aggregateQueryBucketSize = 5000;
    private boolean pastEventsDisablePartitions = false;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setScriptExecutor(ScriptExecutor scriptExecutor) {
        this.scriptExecutor = scriptExecutor;
    }

    public void setMaximumIdsQueryCount(int maximumIdsQueryCount) {
        this.maximumIdsQueryCount = maximumIdsQueryCount;
    }

    public void setAggregateQueryBucketSize(int aggregateQueryBucketSize) {
        this.aggregateQueryBucketSize = aggregateQueryBucketSize;
    }

    public void setPastEventsDisablePartitions(boolean pastEventsDisablePartitions) {
        this.pastEventsDisablePartitions = pastEventsDisablePartitions;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @Override
    public QueryBuilder buildQuery(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        boolean eventsOccurred = getStrategyFromOperator((String) condition.getParameter("operator"));
        int minimumEventCount = !eventsOccurred || condition.getParameter("minimumEventCount") == null ? 1 : (Integer) condition.getParameter("minimumEventCount");
        int maximumEventCount = !eventsOccurred || condition.getParameter("maximumEventCount") == null ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");
        String generatedPropertyKey = (String) condition.getParameter("generatedPropertyKey");

        if (generatedPropertyKey != null && generatedPropertyKey.equals(segmentService.getGeneratedPropertyKey((Condition) condition.getParameter("eventCondition"), condition))) {
            // A property is already set on profiles matching the past event condition, use it to check the numbers of occurrences
            return dispatcher.buildFilter(getProfileConditionForCounter(generatedPropertyKey, minimumEventCount, maximumEventCount, eventsOccurred), context);
        } else {
            // No property set - tries to build an idsQuery
            // TODO see for deprecation, this should not happen anymore each past event condition should have a generatedPropertyKey
            Condition eventCondition = getEventCondition(condition, context, null, definitionsService, scriptExecutor);
            Set<String> ids = getProfileIdsMatchingEventCount(eventCondition, minimumEventCount, maximumEventCount);
            ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
            return dispatcher.buildFilter(conditionBuilder.condition("idsCondition").parameter("ids", ids).parameter("match", eventsOccurred).build(), context);
        }
    }

    @Override
    public long count(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
        boolean eventsOccurred = getStrategyFromOperator((String) condition.getParameter("operator"));
        int minimumEventCount = !eventsOccurred || condition.getParameter("minimumEventCount") == null ? 1 : (Integer) condition.getParameter("minimumEventCount");
        int maximumEventCount = !eventsOccurred || condition.getParameter("maximumEventCount") == null ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");
        String generatedPropertyKey = (String) condition.getParameter("generatedPropertyKey");

        if (generatedPropertyKey != null && generatedPropertyKey.equals(segmentService.getGeneratedPropertyKey((Condition) condition.getParameter("eventCondition"), condition))) {
            // query profiles directly
            return persistenceService.queryCount(getProfileConditionForCounter(generatedPropertyKey, minimumEventCount, maximumEventCount, eventsOccurred), Profile.ITEM_TYPE);
        } else {
            // No count filter - simply get the full number of distinct profiles
            // TODO see for deprecation, this should not happen anymore each past event condition should have a generatedPropertyKey
            Condition eventCondition = getEventCondition(condition, context, null, definitionsService, scriptExecutor);
            if (eventsOccurred && minimumEventCount == 1 && maximumEventCount == Integer.MAX_VALUE) {
                return persistenceService.getSingleValuesMetrics(eventCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE).get("_card").longValue();
            }

            Set<String> profileIds = getProfileIdsMatchingEventCount(eventCondition, minimumEventCount, maximumEventCount);
            ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
            return eventsOccurred ? profileIds.size() : persistenceService.queryCount(conditionBuilder.condition("idsCondition").parameter("ids", profileIds).parameter("match", false).build(), Profile.ITEM_TYPE);
        }
    }

    public boolean getStrategyFromOperator(String operator) {
        if (operator != null && !operator.equals("eventsOccurred") && !operator.equals("eventsNotOccurred")) {
            throw new UnsupportedOperationException("Unsupported operator: " + operator + ", please use either 'eventsOccurred' or 'eventsNotOccurred'");
        }
        return operator == null || operator.equals("eventsOccurred");
    }

    private Condition getProfileConditionForCounter(String generatedPropertyKey, Integer minimumEventCount, Integer maximumEventCount, boolean eventsOccurred) {
        if (eventsOccurred) {
            return createEventOccurredCondition(generatedPropertyKey, minimumEventCount, maximumEventCount);
        } else {
            return createEventNotOccurredCondition(generatedPropertyKey);
        }
    }

    private Condition createEventOccurredCondition(String generatedPropertyKey, Integer minimumEventCount, Integer maximumEventCount) {
        ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
        ConditionBuilder.ConditionItem subConditionCount = conditionBuilder.profileProperty("systemProperties.pastEvents.count").between(minimumEventCount, maximumEventCount);
        ConditionBuilder.ConditionItem subConditionKey = conditionBuilder.profileProperty("systemProperties.pastEvents.key").equalTo(generatedPropertyKey);
        ConditionBuilder.ConditionItem booleanCondition = conditionBuilder.and(subConditionCount, subConditionKey);
        return conditionBuilder.nested(booleanCondition, "systemProperties.pastEvents").build();
    }

    private Condition createEventNotOccurredCondition(String generatedPropertyKey) {
        ConditionBuilder.ConditionItem counterMissing = createPastEventMustNotExistCondition(generatedPropertyKey);
        ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
        ConditionBuilder.ConditionItem counterZero = conditionBuilder.profileProperty("systemProperties.pastEvents.count").equalTo(0);
        ConditionBuilder.ConditionItem keyEquals = conditionBuilder.profileProperty("systemProperties.pastEvents.key").equalTo(generatedPropertyKey);
        ConditionBuilder.ConditionItem keyExistsAndCounterZero = conditionBuilder.and(counterZero, keyEquals);
        ConditionBuilder.ConditionItem nestedKeyExistsAndCounterZero = conditionBuilder.nested(keyExistsAndCounterZero, "systemProperties.pastEvents");
        return conditionBuilder.or(counterMissing, nestedKeyExistsAndCounterZero).build();
    }

    private ConditionBuilder.ConditionItem createPastEventMustNotExistCondition(String generatedPropertyKey) {
        ConditionBuilder conditionBuilder = definitionsService.getConditionBuilder();
        ConditionBuilder.ConditionItem keyEquals = conditionBuilder.profileProperty("systemProperties.pastEvents.key").equalTo(generatedPropertyKey);
        return conditionBuilder.not(keyEquals);
    }

    private Set<String> getProfileIdsMatchingEventCount(Condition eventCondition, int minimumEventCount, int maximumEventCount) {
        boolean noBoundaries = minimumEventCount == 1 && maximumEventCount == Integer.MAX_VALUE;
        if (pastEventsDisablePartitions) {
            Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(eventCondition, new TermsAggregate("profileId"), Event.ITEM_TYPE, maximumIdsQueryCount);
            eventCountByProfile.remove("_filtered");
            return noBoundaries ?
                    eventCountByProfile.keySet() :
                    eventCountByProfile.entrySet()
                            .stream()
                            .filter(eventCountPerProfile -> (eventCountPerProfile.getValue() >= minimumEventCount && eventCountPerProfile.getValue() <= maximumEventCount))
                            .map(Map.Entry::getKey)
                            .collect(Collectors.toSet());
        } else {
            Set<String> result = new HashSet<>();
            // Get full cardinality to partition the terms aggregation
            Map<String, Double> m = persistenceService.getSingleValuesMetrics(eventCondition, new String[]{"card"}, "profileId.keyword", Event.ITEM_TYPE);
            long card = m.get("_card").longValue();

            int numParts = (int) (card / aggregateQueryBucketSize) + 2;
            for (int i = 0; i < numParts; i++) {
                Map<String, Long> eventCountByProfile = persistenceService.aggregateWithOptimizedQuery(eventCondition, new TermsAggregate("profileId", i, numParts), Event.ITEM_TYPE);
                if (eventCountByProfile != null) {
                    eventCountByProfile.remove("_filtered");
                    if (noBoundaries) {
                        result.addAll(eventCountByProfile.keySet());
                    } else {
                        for (Map.Entry<String, Long> entry : eventCountByProfile.entrySet()) {
                            if (entry.getValue() < minimumEventCount) {
                                // No more interesting buckets in this partition
                                break;
                            } else if (entry.getValue() <= maximumEventCount) {
                                result.add(entry.getKey());
                            }
                        }
                    }
                }
            }

            return result;
        }
    }

    public Condition getEventCondition(Condition condition, Map<String, Object> context, String profileId,
                                                 DefinitionsService definitionsService, ScriptExecutor scriptExecutor) {
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

        l.add(ConditionContextHelper.getContextualCondition(eventCondition, context, scriptExecutor));

        if (profileId != null) {
            Condition profileCondition = new Condition();
            profileCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            profileCondition.setParameter("propertyName", "profileId");
            profileCondition.setParameter("comparisonOperator", "equals");
            profileCondition.setParameter("propertyValue", profileId);
            l.add(profileCondition);
        }

        Integer numberOfDays = (Integer) condition.getParameter("numberOfDays");
        String fromDate = (String) condition.getParameter("fromDate");
        String toDate = (String) condition.getParameter("toDate");

        if (numberOfDays != null) {
            l.add(getTimeStampCondition("greaterThan", "propertyValueDateExpr", "now-" + numberOfDays + "d", definitionsService));
        }
        if (fromDate != null)  {
            l.add(getTimeStampCondition("greaterThanOrEqualTo", "propertyValueDate", fromDate, definitionsService));
        }
        if (toDate != null)  {
            l.add(getTimeStampCondition("lessThanOrEqualTo", "propertyValueDate", toDate, definitionsService));
        }
        return andCondition;
    }

    private static Condition getTimeStampCondition(String operator, String propertyValueParameter, Object propertyValue, DefinitionsService definitionsService) {
        Condition endDateCondition = new Condition();
        endDateCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
        endDateCondition.setParameter("propertyName", "timeStamp");
        endDateCondition.setParameter("comparisonOperator", operator);
        endDateCondition.setParameter(propertyValueParameter, propertyValue);
        return endDateCondition;
    }
}
