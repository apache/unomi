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

import java.util.Collections;
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
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType(entityName + "PropertyCondition"));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", operator);
        profileIdCondition.setParameter("propertyValue", propertyValue);

        return profileIdCondition;
    }

    protected Condition createDatePropertyCondition(final String propertyName, final String operator, final Object propertyValue, DefinitionsService definitionsService) {
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType(entityName + "PropertyCondition"));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", operator);
        profileIdCondition.setParameter("propertyValueDate", propertyValue);

        return profileIdCondition;
    }

    protected Condition createFilterInputCondition(CDPEventFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = Collections.emptyList();

        if (after != null) {
            final Condition afterCondition = createDatePropertyCondition("timeStamp", "greaterThan", after, definitionsService);
            rootSubConditions.add(afterCondition);
        }

        if (before != null) {
            final Condition afterCondition = createDatePropertyCondition("timeStamp", "lessThanOrEqual", before, definitionsService);
            rootSubConditions.add(afterCondition);
        }

        if (filterInput != null) {
            if (filterInput.id_equals != null) {
                final Condition idCondition = createPropertyCondition("id", filterInput.id_equals, definitionsService);
                rootSubConditions.add(idCondition);
            }

            if (filterInput.cdp_clientID_equals != null) {
                final Condition clientIdCondition = createPropertyCondition("clientId", filterInput.cdp_clientID_equals, definitionsService);
                rootSubConditions.add(clientIdCondition);
            }

            if (filterInput.cdp_profileID_equals != null) {
                final Condition profileIdCondition = createPropertyCondition("profileId", filterInput.cdp_profileID_equals, definitionsService);
                rootSubConditions.add(profileIdCondition);
            }

            if (filterInput.cdp_sourceID_equals != null) {
                final Condition sourceIdCondition = createPropertyCondition("sourceId", filterInput.cdp_sourceID_equals, definitionsService);
                rootSubConditions.add(sourceIdCondition);
            }

            if (filterInput.and != null && filterInput.and.size() > 0) {
                final Condition filterAndCondition = createBoolCondition("and", definitionsService);
                final List<Condition> filterAndSubConditions = filterInput.and.stream().map(andInput -> createFilterInputCondition(andInput, null, null, definitionsService)).collect(Collectors.toList());
                filterAndCondition.setParameter("subConditions", filterAndSubConditions);
            }

            if (filterInput.or != null && filterInput.or.size() > 0) {
                final Condition filterOrCondition = createBoolCondition("or", definitionsService);
                final List<Condition> filterOrSubConditions = filterInput.or.stream().map(orInput -> createFilterInputCondition(orInput, null, null, definitionsService)).collect(Collectors.toList());
                filterOrCondition.setParameter("subConditions", filterOrSubConditions);
            }
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }
}
