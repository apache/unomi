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
package org.apache.unomi.rest.endpoints;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.ValueType;
import org.apache.unomi.api.goals.Goal;
import org.apache.unomi.api.goals.GoalReport;
import org.apache.unomi.api.query.AggregateQuery;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.GoalsService;
import org.apache.unomi.rest.models.RESTValueType;
import org.apache.unomi.rest.service.impl.LocalizationHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissingResourceEndpointsTest {

    @Mock
    private DefinitionsService definitionsService;

    @Mock
    private LocalizationHelper localizationHelper;

    @Mock
    private GoalsService goalsService;

    @Mock
    private EventService eventService;

    @Test
    void getValueType_missingValueType_throwsNotFound() {
        when(definitionsService.getValueType("missing-type")).thenReturn(null);

        DefinitionsServiceEndPoint endpoint = new DefinitionsServiceEndPoint();
        endpoint.setDefinitionsService(definitionsService);
        endpoint.setLocalizationHelper(localizationHelper);

        NotFoundException exception = assertThrows(NotFoundException.class,
            () -> endpoint.getValueType("missing-type", "en"));

        assertTrue(exception.getMessage().contains("missing-type"));
        verify(localizationHelper, never()).generateValueType(any(ValueType.class), anyString());
    }

    @Test
    void getValueType_existingValueType_returnsLocalizedValueType() {
        ValueType valueType = new ValueType();
        valueType.setId("string");
        RESTValueType restValueType = new RESTValueType();
        restValueType.setId("string");

        when(definitionsService.getValueType("string")).thenReturn(valueType);
        when(localizationHelper.generateValueType(valueType, "en")).thenReturn(restValueType);

        DefinitionsServiceEndPoint endpoint = new DefinitionsServiceEndPoint();
        endpoint.setDefinitionsService(definitionsService);
        endpoint.setLocalizationHelper(localizationHelper);

        RESTValueType result = endpoint.getValueType("string", "en");

        assertEquals("string", result.getId());
    }

    @Test
    void getGoalReport_missingGoal_throwsNotFound() {
        when(goalsService.getGoal("missing-goal")).thenReturn(null);

        GoalsServiceEndPoint endpoint = new GoalsServiceEndPoint();
        endpoint.setGoalsService(goalsService);

        assertThrows(NotFoundException.class, () -> endpoint.getGoalReport("missing-goal"));
    }

    @Test
    void getGoalReportPost_missingGoal_throwsNotFound() {
        when(goalsService.getGoal("missing-goal")).thenReturn(null);

        GoalsServiceEndPoint endpoint = new GoalsServiceEndPoint();
        endpoint.setGoalsService(goalsService);

        assertThrows(NotFoundException.class,
            () -> endpoint.getGoalReport("missing-goal", new AggregateQuery()));
    }

    @Test
    void getGoalReport_existingGoal_delegatesToService() {
        Goal goal = new Goal();
        goal.setItemId("goal-1");
        GoalReport report = new GoalReport();

        when(goalsService.getGoal("goal-1")).thenReturn(goal);
        when(goalsService.getGoalReport("goal-1")).thenReturn(report);

        GoalsServiceEndPoint endpoint = new GoalsServiceEndPoint();
        endpoint.setGoalsService(goalsService);

        assertEquals(report, endpoint.getGoalReport("goal-1"));
    }

    @Test
    void getEvent_missingEvent_returnsNotFound() {
        when(eventService.getEvent("missing-event")).thenReturn(null);

        EventServiceEndpoint endpoint = new EventServiceEndpoint();
        endpoint.setEventService(eventService);

        Response response = endpoint.getEvents("missing-event");

        assertEquals(404, response.getStatus());
    }

    @Test
    void getEvent_existingEvent_returnsOkWithBody() {
        Event event = new Event();
        event.setItemId("event-1");

        when(eventService.getEvent("event-1")).thenReturn(event);

        EventServiceEndpoint endpoint = new EventServiceEndpoint();
        endpoint.setEventService(eventService);

        Response response = endpoint.getEvents("event-1");

        assertEquals(200, response.getStatus());
        assertEquals(event, response.getEntity());
    }
}
