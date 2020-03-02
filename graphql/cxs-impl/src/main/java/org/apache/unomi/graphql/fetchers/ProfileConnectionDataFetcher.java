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

import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.graphql.types.input.CDPInterestFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileEventsFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfilePropertiesFilterInput;
import org.apache.unomi.graphql.types.output.CDPPageInfo;
import org.apache.unomi.graphql.types.output.CDPProfile;
import org.apache.unomi.graphql.types.output.CDPProfileConnection;
import org.apache.unomi.graphql.types.output.CDPProfileEdge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public abstract class ProfileConnectionDataFetcher extends BaseConnectionDataFetcher<CDPProfileConnection> {

    public ProfileConnectionDataFetcher() {
        super("profile");
    }

    protected CDPProfileConnection createProfileConnection(PartialList<Profile> profiles) {
        final List<CDPProfileEdge> eventEdges = profiles.getList().stream()
                .map(profile -> new CDPProfileEdge(new CDPProfile(profile), profile.getItemId()))
                .collect(Collectors.toList());
        final CDPPageInfo cdpPageInfo = new CDPPageInfo(profiles.getOffset() > 0, profiles.getTotalSize() > profiles.getList().size());

        return new CDPProfileConnection(eventEdges, cdpPageInfo);
    }

    /*
    * Condition operations can be seen here
    * org/apache/unomi/plugins/baseplugin/conditions/PropertyConditionESQueryBuilder.java:69
    * */
    protected Condition createProfileFilterInputCondition(CDPProfileFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (after != null) {
            rootSubConditions.add(createDatePropertyCondition("timeStamp", "greaterThan", after, definitionsService));
        }

        if (before != null) {
            rootSubConditions.add(createDatePropertyCondition("timeStamp", "lessThanOrEqual", before, definitionsService));
        }

        if (filterInput != null) {
            if (filterInput.getProfileIDs_contains() != null && filterInput.getProfileIDs_contains().size() > 0) {
                rootSubConditions.add(createPropertiesCondition("_id", "hasSomeOf", filterInput.getProfileIDs_contains(), definitionsService));
            }

            if (filterInput.getConsents_contains() != null && filterInput.getConsents_contains().size() > 0) {
                rootSubConditions.add(createPropertiesCondition("consents", "hasSomeOf", filterInput.getConsents_contains(), definitionsService));
            }

            if (filterInput.getSegments_contains() != null && filterInput.getSegments_contains().size() > 0) {
                rootSubConditions.add(createPropertiesCondition("segments", "hasSomeOf", filterInput.getSegments_contains(), definitionsService));
            }

            if (filterInput.getLists_contains() != null && filterInput.getLists_contains().size() > 0) {
                rootSubConditions.add(createPropertiesCondition("lists", "hasSomeOf", filterInput.getLists_contains(), definitionsService));
            }

            if (filterInput.getProperties() != null) {
                rootSubConditions.add(createProfilePropertiesFilterInputCondition(filterInput.getProperties(), definitionsService));
            }

            if (filterInput.getEvents() != null) {
                rootSubConditions.add(createProfileEventsFilterInputCondition(filterInput.getEvents(), definitionsService));
            }

            if (filterInput.getInterests() != null) {
                rootSubConditions.add(createInterestFilterInputCondition(filterInput.getInterests(), definitionsService));
            }
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }

    private Condition createInterestFilterInputCondition(CDPInterestFilterInput filterInput, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getTopic_equals() != null) {
            rootSubConditions.add(createPropertyCondition("topic", filterInput.getTopic_equals(), definitionsService));
        }

        if (filterInput.getScore_equals() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("score", filterInput.getScore_equals(), definitionsService));
        }

        if (filterInput.getScore_gt() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("score", "greaterThan", filterInput.getScore_gt(), definitionsService));
        }

        if (filterInput.getScore_gte() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("score", "greaterThanOrEqualTo", filterInput.getScore_gte(), definitionsService));
        }

        if (filterInput.getScore_lt() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("score", "lessThan", filterInput.getScore_lt(), definitionsService));
        }

        if (filterInput.getScore_lte() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("score", "lessThanOrEqualTo", filterInput.getScore_lte(), definitionsService));
        }

        if (filterInput.getAnd() != null && filterInput.getAnd().size() > 0) {
            final Condition filterAndCondition = createBoolCondition("and", definitionsService);
            final List<Condition> filterAndSubConditions = filterInput.getAnd().stream()
                    .map(andInput -> createInterestFilterInputCondition(andInput, definitionsService))
                    .collect(Collectors.toList());
            filterAndCondition.setParameter("subConditions", filterAndSubConditions);
            rootSubConditions.add(filterAndCondition);
        }

        if (filterInput.getOr() != null && filterInput.getOr().size() > 0) {
            final Condition filterOrCondition = createBoolCondition("or", definitionsService);
            final List<Condition> filterOrSubConditions = filterInput.getOr().stream()
                    .map(orInput -> createInterestFilterInputCondition(orInput, definitionsService))
                    .collect(Collectors.toList());
            filterOrCondition.setParameter("subConditions", filterOrSubConditions);
            rootSubConditions.add(filterOrCondition);
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }

    private Condition createProfileEventsFilterInputCondition(CDPProfileEventsFilterInput filterInput, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getMaximalCount() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("count", "lessThanOrEqualTo", filterInput.getMaximalCount(), definitionsService));
        }

        if (filterInput.getMinimalCount() != null) {
            rootSubConditions.add(createIntegerPropertyCondition("count", "greaterThanOrEqualTo", filterInput.getMinimalCount(), definitionsService));
        }

        if (filterInput.getNot() != null) {
            rootSubConditions.add(createProfileEventsFilterInputCondition(filterInput.getNot(), definitionsService));
        }

        if (filterInput.getEventFilter() != null) {
            rootSubConditions.add(createEventFilterInputCondition(filterInput.getEventFilter(), null, null, definitionsService));
        }

        if (filterInput.getAnd() != null && filterInput.getAnd().size() > 0) {
            final Condition filterAndCondition = createBoolCondition("and", definitionsService);
            final List<Condition> filterAndSubConditions = filterInput.getAnd().stream()
                    .map(andInput -> createProfileEventsFilterInputCondition(andInput, definitionsService))
                    .collect(Collectors.toList());
            filterAndCondition.setParameter("subConditions", filterAndSubConditions);
            rootSubConditions.add(filterAndCondition);
        }

        if (filterInput.getOr() != null && filterInput.getOr().size() > 0) {
            final Condition filterOrCondition = createBoolCondition("or", definitionsService);
            final List<Condition> filterOrSubConditions = filterInput.getOr().stream()
                    .map(orInput -> createProfileEventsFilterInputCondition(orInput, definitionsService))
                    .collect(Collectors.toList());
            filterOrCondition.setParameter("subConditions", filterOrSubConditions);
            rootSubConditions.add(filterOrCondition);
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }

    private Condition createProfilePropertiesFilterInputCondition(CDPProfilePropertiesFilterInput filterInput, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = new ArrayList<>();

        if (filterInput.getAnd() != null && filterInput.getAnd().size() > 0) {
            final Condition filterAndCondition = createBoolCondition("and", definitionsService);
            final List<Condition> filterAndSubConditions = filterInput.getAnd().stream()
                    .map(andInput -> createProfilePropertiesFilterInputCondition(andInput, definitionsService))
                    .collect(Collectors.toList());
            filterAndCondition.setParameter("subConditions", filterAndSubConditions);
            rootSubConditions.add(filterAndCondition);
        }

        if (filterInput.getOr() != null && filterInput.getOr().size() > 0) {
            final Condition filterOrCondition = createBoolCondition("or", definitionsService);
            final List<Condition> filterOrSubConditions = filterInput.getOr().stream()
                    .map(orInput -> createProfilePropertiesFilterInputCondition(orInput, definitionsService))
                    .collect(Collectors.toList());
            filterOrCondition.setParameter("subConditions", filterOrSubConditions);
            rootSubConditions.add(filterOrCondition);
        }

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }
}
