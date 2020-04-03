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

package org.apache.unomi.graphql.condition;

import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPListsUpdateEventFilterInput;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class EventConditionFactory extends ConditionFactory {

    public EventConditionFactory() {
        super("eventPropertyCondition");
    }

    public Condition createEventFilterInputCondition(CDPEventFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
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

            if (filterInput.getCdp_listsUpdateEvent() != null) {
                rootSubConditions.add(createListUpdateEventCondition(filterInput.getCdp_listsUpdateEvent(), definitionsService));
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

    private Condition createListUpdateEventCondition(CDPListsUpdateEventFilterInput cdp_listsUpdateEvent, DefinitionsService definitionsService) {

        final List<Condition> rootSubConditions = new ArrayList<>();

        if (cdp_listsUpdateEvent.getJoinLists_contains() != null && !cdp_listsUpdateEvent.getJoinLists_contains().isEmpty()) {
            rootSubConditions.add(createPropertiesCondition("joinLists", "contains", cdp_listsUpdateEvent.getJoinLists_contains(), definitionsService));
        }

        if (cdp_listsUpdateEvent.getJoinLists_contains() != null && !cdp_listsUpdateEvent.getJoinLists_contains().isEmpty()) {
            rootSubConditions.add(createPropertiesCondition("leaveLists", "contains", cdp_listsUpdateEvent.getLeaveLists_contains(), definitionsService));
        }

        final Condition rootCondition = createBoolCondition("and", definitionsService);
        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }

}
