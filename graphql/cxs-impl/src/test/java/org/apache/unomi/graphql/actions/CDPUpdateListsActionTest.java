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
package org.apache.unomi.graphql.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.services.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UNOMI-1030: joining/leaving a list must send a new lists value, not the profile's list mutated
 * in place (otherwise {@code updateProperties} sees no change and the profile is never saved).
 */
@ExtendWith(MockitoExtension.class)
class CDPUpdateListsActionTest {

    @Mock
    private EventService eventService;
    @Mock
    private Action action;

    private CDPUpdateListsAction updateListsAction;

    @BeforeEach
    void setUp() {
        updateListsAction = new CDPUpdateListsAction();
        updateListsAction.setEventService(eventService);
    }

    @Test
    void joinSecondListSendsANewListNotTheMutatedOriginal() {
        Profile profile = profileWithLists("listA");
        List<String> originalLists = (List<String>) profile.getSystemProperties().get("lists");

        Event joinEvent = new Event("cdp_listUpdateEvent", null, profile, null, null, null, new Date());
        joinEvent.setProperty("joinLists", Collections.singletonList("listB"));

        when(eventService.send(org.mockito.ArgumentMatchers.any(Event.class)))
                .thenReturn(EventService.PROFILE_UPDATED);

        int result = updateListsAction.execute(action, joinEvent);

        assertEquals(EventService.PROFILE_UPDATED, result);
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventService).send(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> update = (Map<String, Object>) captor.getValue().getProperty("update");
        @SuppressWarnings("unchecked")
        List<String> sentLists = (List<String>) update.get("systemProperties.lists");

        assertEquals(List.of("listA"), originalLists, "Profile list must not be mutated in place");
        assertNotSame(originalLists, sentLists);
        assertEquals(List.of("listA", "listB"), sentLists);
    }

    @Test
    void leaveListSendsANewListWithoutTheRemovedId() {
        Profile profile = profileWithLists("listA", "listB");
        List<String> originalLists = (List<String>) profile.getSystemProperties().get("lists");

        Event leaveEvent = new Event("cdp_listUpdateEvent", null, profile, null, null, null, new Date());
        leaveEvent.setProperty("leaveLists", Collections.singletonList("listB"));

        when(eventService.send(org.mockito.ArgumentMatchers.any(Event.class)))
                .thenReturn(EventService.PROFILE_UPDATED);

        updateListsAction.execute(action, leaveEvent);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventService).send(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> update = (Map<String, Object>) captor.getValue().getProperty("update");
        @SuppressWarnings("unchecked")
        List<String> sentLists = (List<String>) update.get("systemProperties.lists");

        assertEquals(List.of("listA", "listB"), originalLists);
        assertNotSame(originalLists, sentLists);
        assertEquals(List.of("listA"), sentLists);
    }

    @Test
    void joinFirstListWorksWhenProfileHasNoListsYet() {
        Profile profile = new Profile("p1");
        Event joinEvent = new Event("cdp_listUpdateEvent", null, profile, null, null, null, new Date());
        joinEvent.setProperty("joinLists", Collections.singletonList("listA"));

        when(eventService.send(org.mockito.ArgumentMatchers.any(Event.class)))
                .thenReturn(EventService.PROFILE_UPDATED);

        updateListsAction.execute(action, joinEvent);

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventService).send(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> update = (Map<String, Object>) captor.getValue().getProperty("update");
        @SuppressWarnings("unchecked")
        List<String> sentLists = (List<String>) update.get("systemProperties.lists");

        assertEquals(List.of("listA"), sentLists);
        assertTrue(profile.getSystemProperties() == null
                || profile.getSystemProperties().get("lists") == null
                || ((List<?>) profile.getSystemProperties().get("lists")).isEmpty());
    }

    private static Profile profileWithLists(String... listIds) {
        Profile profile = new Profile("p1");
        Map<String, Object> systemProperties = new HashMap<>();
        List<String> lists = new ArrayList<>();
        Collections.addAll(lists, listIds);
        systemProperties.put("lists", lists);
        profile.setSystemProperties(systemProperties);
        return profile;
    }
}
