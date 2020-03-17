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

package org.apache.unomi.graphql.fetchers;

import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BaseConnectionDataFetcher<T> extends BaseDataFetcher<T> {

    private String entityName;

    public BaseConnectionDataFetcher(String entityName) {
        this.entityName = entityName;
    }

    protected ConnectionParams parseConnectionParams(final DataFetchingEnvironment environment) {
        return ConnectionParams.create()
                .first(parseParam("first", 0, environment))
                .last(parseParam("last", DEFAULT_PAGE_SIZE, environment))
                .after(parseDateParam("after", environment))
                .before(parseDateParam("before", environment))
                .build();
    }

    protected Condition createPropertyCondition(final String propertyName, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, "equals", propertyValue, definitionsService);
    }

    protected Condition createPropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValue", propertyValue, definitionsService);
    }

    protected Condition createIntegerPropertyCondition(final String propertyName, final Object propertyValue, DefinitionsService definitionsService) {
        return createIntegerPropertyCondition(propertyName, "equals", propertyValue, definitionsService);
    }

    protected Condition createIntegerPropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValueInteger", propertyValue, definitionsService);
    }

    protected Condition createDatePropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValueDate", propertyValue, definitionsService);
    }

    protected Condition createPropertiesCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        return createPropertyCondition(propertyName, operator, "propertyValues", propertyValue, definitionsService);
    }

    protected Condition createPropertyCondition(final String propertyName, final String operator, final String propertyValueName, final Object propertyValue, DefinitionsService definitionsService) {
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType(entityName + "PropertyCondition"));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", operator);
        profileIdCondition.setParameter(propertyValueName, propertyValue);

        return profileIdCondition;
    }

    protected Condition createEventFilterInputCondition(CDPEventFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (after != null) {
            rootSubConditions.add(createDatePropertyCondition("timeStamp", "greaterThan", after, definitionsService));
        }

        if (before != null) {
            rootSubConditions.add(createDatePropertyCondition("timeStamp", "lessThanOrEqual", before, definitionsService));
        }

        if (filterInput != null) {
            if (filterInput.getId_equals() != null) {
                rootSubConditions.add(createPropertyCondition("_id", filterInput.getId_equals(), definitionsService));
            }

            if (filterInput.getCdp_clientID_equals() != null) {
                rootSubConditions.add(createPropertyCondition("clientId", filterInput.getCdp_clientID_equals(), definitionsService));
            }

            if (filterInput.getCdp_profileID_equals() != null) {
                rootSubConditions.add(createPropertyCondition("profileId", filterInput.getCdp_profileID_equals(), definitionsService));
            }

            if (filterInput.getCdp_sourceID_equals() != null) {
                rootSubConditions.add(createPropertyCondition("itemId", filterInput.getCdp_sourceID_equals(), definitionsService));
            }

            if (filterInput.getAnd() != null && filterInput.getAnd().size() > 0) {
                final Condition filterAndCondition = createBoolCondition("and", definitionsService);
                final List<Condition> filterAndSubConditions = filterInput.getAnd().stream()
                        .map(andInput -> createEventFilterInputCondition(andInput, null, null, definitionsService))
                        .collect(Collectors.toList());
                filterAndCondition.setParameter("subConditions", filterAndSubConditions);
                rootSubConditions.add(filterAndCondition);
            }

            if (filterInput.getOr() != null && filterInput.getOr().size() > 0) {
                final Condition filterOrCondition = createBoolCondition("or", definitionsService);
                final List<Condition> filterOrSubConditions = filterInput.getOr().stream()
                        .map(orInput -> createEventFilterInputCondition(orInput, null, null, definitionsService))
                        .collect(Collectors.toList());
                filterOrCondition.setParameter("subConditions", filterOrSubConditions);
                rootSubConditions.add(filterOrCondition);
            }
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }
}
