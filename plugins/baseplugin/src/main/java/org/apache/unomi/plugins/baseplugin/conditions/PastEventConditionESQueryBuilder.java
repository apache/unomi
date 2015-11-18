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
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilder;
import org.apache.unomi.persistence.elasticsearch.conditions.ConditionESQueryBuilderDispatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;

import java.util.*;

public class PastEventConditionESQueryBuilder implements ConditionESQueryBuilder {
    private DefinitionsService definitionsService;
    private PersistenceService persistenceService;

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public FilterBuilder buildFilter(Condition condition, Map<String, Object> context, ConditionESQueryBuilderDispatcher dispatcher) {
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

        l.add(eventCondition);

        Integer numberOfDays = (Integer) condition.getParameter("numberOfDays");
        if (numberOfDays != null) {
            Condition numberOfDaysCondition = new Condition();
            numberOfDaysCondition.setConditionType(definitionsService.getConditionType("sessionPropertyCondition"));
            numberOfDaysCondition.setParameter("propertyName", "timeStamp");
            numberOfDaysCondition.setParameter("comparisonOperator", "greaterThan");
            numberOfDaysCondition.setParameter("propertyValueDateExpr", "now-" + numberOfDays + "d");
            l.add(numberOfDaysCondition);
        }
        //todo : Check behaviour with important number of profiles
        Set<String> ids = new HashSet<String>();
        Integer minimumEventCount = condition.getParameter("minimumEventCount") == null ? 0 : (Integer) condition.getParameter("minimumEventCount");
        Integer maximumEventCount = condition.getParameter("maximumEventCount") == null  ? Integer.MAX_VALUE : (Integer) condition.getParameter("maximumEventCount");

        Map<String, Long> res = persistenceService.aggregateQuery(andCondition, new TermsAggregate("profileId"), Event.ITEM_TYPE);
        for (Map.Entry<String, Long> entry : res.entrySet()) {
            if (!entry.getKey().startsWith("_")) {
                if (entry.getValue() >= minimumEventCount && entry.getValue() <= maximumEventCount) {
                    ids.add(entry.getKey());
                }
            }
        }

        return FilterBuilders.idsFilter(Profile.ITEM_TYPE).addIds(ids.toArray(new String[ids.size()]));
    }
}
