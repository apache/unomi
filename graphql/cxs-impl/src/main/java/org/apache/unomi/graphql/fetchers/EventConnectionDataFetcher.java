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

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.types.input.CDPEventFilterInput;
import org.apache.unomi.graphql.types.input.CDPProfileIDInput;
import org.apache.unomi.graphql.types.output.CDPEvent;
import org.apache.unomi.graphql.types.output.CDPEventConnection;
import org.apache.unomi.graphql.types.output.CDPEventEdge;
import org.apache.unomi.graphql.types.output.CDPPageInfo;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;

public class EventConnectionDataFetcher implements DataFetcher<CDPEventConnection> {

    public static final String TYPE_ALL = "All";
    public static final String TYPE_LATEST = "Latest";

    private String type;
    private ObjectMapper objectMapper;

    public EventConnectionDataFetcher() {
        this(TYPE_ALL);
    }

    public EventConnectionDataFetcher(String type) {
        this.type = type;
        objectMapper = new ObjectMapper();
    }

    @Override
    public CDPEventConnection get(DataFetchingEnvironment environment) throws Exception {
        final ServiceManager serviceManager = environment.getContext();

        switch (type) {
            case TYPE_LATEST:
                return getLatest(environment, serviceManager);
            default:
                return getAll(environment, serviceManager);
        }
    }

    private CDPEventConnection getLatest(DataFetchingEnvironment environment, ServiceManager serviceManager) {
        final CDPProfileIDInput profileIDInput =
                objectMapper.convertValue(environment.getArgument("profileID"), CDPProfileIDInput.class);
        final Integer count = environment.getArgument("count");

        final Condition condition = createLatestEventsCondition(profileIDInput, serviceManager.getDefinitionsService());
        final PartialList<Event> events = serviceManager.getEventService().searchEvents(condition, 0, count);

        return createEventConnection(events);
    }

    private CDPEventConnection getAll(DataFetchingEnvironment environment, ServiceManager serviceManager) {
        final Integer first = environment.getArgument("first");
        final Integer last = environment.getArgument("last");
        Date after = null;
        try {
            after = DateFormat.getInstance().parse(environment.getArgument("after"));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Date before = null;
        try {
            before = DateFormat.getInstance().parse(environment.getArgument("before"));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        final CDPEventFilterInput filterInput = objectMapper.convertValue(environment.getArgument("filter"), CDPEventFilterInput.class);

        final Condition condition = createAllEventsCondition(filterInput, after, before, serviceManager.getDefinitionsService());
        final PartialList<Event> events = serviceManager.getEventService().searchEvents(condition, first, last - first);

        return createEventConnection(events);
    }

    private Condition createLatestEventsCondition(CDPProfileIDInput profileIDInput, DefinitionsService definitionsService) {
        return createEventPropertyCondition("profileId", profileIDInput.getId(), definitionsService);
    }

    private Condition createBoolCondition(final String operator, DefinitionsService definitionsService) {
        final Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", operator);
        return andCondition;
    }

    private Condition createEventPropertyCondition(final String propertyName, final String propertyValue, DefinitionsService definitionsService) {
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", "equals");
        profileIdCondition.setParameter("propertyValue", propertyValue);

        return profileIdCondition;
    }

    private Condition createEventPropertyDateCondition(final String propertyName, final String operator, final Date propertyValue, DefinitionsService definitionsService) {
        final Condition profileIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));

        profileIdCondition.setParameter("propertyName", propertyName);
        profileIdCondition.setParameter("comparisonOperator", operator);
        profileIdCondition.setParameter("propertyValueDate", propertyValue);

        return profileIdCondition;
    }

    private Condition createAllEventsCondition(CDPEventFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
        return createFilterInputCondition(filterInput, after, before, definitionsService);
    }

    private Condition createFilterInputCondition(CDPEventFilterInput filterInput, Date after, Date before, DefinitionsService definitionsService) {
        final Condition rootCondition = createBoolCondition("and", definitionsService);
        final List<Condition> rootSubConditions = newArrayList();

        if (after != null) {
            final Condition afterCondition = createEventPropertyDateCondition("timeStamp", "greaterThan", after, definitionsService);
            rootSubConditions.add(afterCondition);
        }

        if (before != null) {
            final Condition afterCondition = createEventPropertyDateCondition("timeStamp", "lessThanOrEqual", before, definitionsService);
            rootSubConditions.add(afterCondition);
        }

        if (filterInput.id_equals != null) {
            final Condition idCondition = createEventPropertyCondition("id", filterInput.id_equals, definitionsService);
            rootSubConditions.add(idCondition);
        }

        if (filterInput.cdp_clientID_equals != null) {
            final Condition clientIdCondition = createEventPropertyCondition("clientId", filterInput.cdp_clientID_equals, definitionsService);
            rootSubConditions.add(clientIdCondition);
        }

        if (filterInput.cdp_profileID_equals != null) {
            final Condition profileIdCondition = createEventPropertyCondition("profileId", filterInput.cdp_profileID_equals, definitionsService);
            rootSubConditions.add(profileIdCondition);
        }

        if (filterInput.cdp_sourceID_equals != null) {
            final Condition sourceIdCondition = createEventPropertyCondition("sourceId", filterInput.cdp_sourceID_equals, definitionsService);
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

        rootCondition.setParameter("subConditions", rootSubConditions);
        return rootCondition;
    }

    private CDPEventConnection createEventConnection(PartialList<Event> events) {
        final List<CDPEventEdge> eventEdges = events.getList().stream().map(event -> new CDPEventEdge(new CDPEvent(event), event.getItemId())).collect(Collectors.toList());
        final CDPPageInfo cdpPageInfo = new CDPPageInfo(events.getOffset() > 0, events.getTotalSize() > events.getList().size());

        return new CDPEventConnection(eventEdges, cdpPageInfo);
    }
}
