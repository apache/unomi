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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.services.impl.events.EventServiceImpl;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class EventServiceImplTest {
    @Test
    public void testThirdPartyAuthenticationAndRestrictedEvents() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "127.0.0.1,::1",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        String authenticateServerName = eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "127.0.0.1");
        assertEquals("provider1", authenticateServerName);

        // test allowed events
        assertTrue(eventService.isEventAllowed(new Event("test1", null, new Profile(), null, null, null, null), authenticateServerName));
        assertTrue(eventService.isEventAllowed(new Event("test2", null, new Profile(), null, null, null, null), authenticateServerName));
        assertTrue(eventService.isEventAllowed(new Event("test4", null, new Profile(), null, null, null, null), authenticateServerName));

        // test restricted events
        assertFalse(eventService.isEventAllowed(new Event("test3", null, new Profile(), null, null, null, null), authenticateServerName));
    }

    @Test
    public void testNotAuthenticatedRestrictedEvents() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "127.0.0.1,::1",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        String authenticateServerName = eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.15");
        assertNull("Server should not be authenticate, ip is not matching a declared thirdparty server", authenticateServerName);

        // test allowed events
        assertTrue(eventService.isEventAllowed(new Event("test4", null, new Profile(), null, null, null, null), authenticateServerName));

        // test restricted events
        assertFalse(eventService.isEventAllowed(new Event("test1", null, new Profile(), null, null, null, null), authenticateServerName));
        assertFalse(eventService.isEventAllowed(new Event("test2", null, new Profile(), null, null, null, null), authenticateServerName));
        assertFalse(eventService.isEventAllowed(new Event("test3", null, new Profile(), null, null, null, null), authenticateServerName));
    }

    @Test
    public void testThirdPartyAuthentication_ip_range() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "192.168.1.1-100",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.1"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.2"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.3"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.98"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.99"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.100"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.101"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.2.2"));
    }

    @Test
    public void testThirdPartyAuthentication_ip_subnet() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "1.2.0.0/16",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.0.0"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.1.1"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.2.2"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.50.125"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.50.125"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.100"));
    }

    @Test
    public void testThirdPartyAuthentication_ip_wildcards() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "1.2.*.*",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.0.0"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.1.1"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.2.2"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.50.125"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.50.125"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.100"));
    }

    @Test
    public void testThirdPartyAuthentication_ip_combined() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "1.*.2-3.4",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.3.4"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.50.2.4"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.50.4"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.3.5"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.100"));
    }

    @Test
    public void testThirdPartyAuthentication_ip_multiple() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "1.*.2-3.4,192.168.1.1-100,::1",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.3.4"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.50.2.4"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.1"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.2"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.50.4"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.3.5"));
        assertNull(eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.101"));
    }

    @Test
    public void testThirdPartyAuthentication_ip_matchAll() {
        EventServiceImpl eventService = mockEventServiceForThirdPartyTests(
                "670c26d1cc413346c3b2fd9ce65dab41",
                "*.*.*.*",
                "test1,test2",
                Arrays.asList("test1", "test2", "test3")
        );

        // test authentication
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.0.0"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.1.1"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.2.2"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.2.50.125"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "1.3.50.125"));
        assertEquals("provider1", eventService.authenticateThirdPartyServer("670c26d1cc413346c3b2fd9ce65dab41", "192.168.1.100"));
    }

    private EventServiceImpl mockEventServiceForThirdPartyTests(String key, String ipAddresses, String allowedEvents, List<String> restrictedEventTypeIds) {
        // conf
        Map<String, String> thirdPartyConfiguration = new HashMap<>();
        thirdPartyConfiguration.put("thirdparty.provider1.key", key);
        thirdPartyConfiguration.put("thirdparty.provider1.ipAddresses", ipAddresses);
        thirdPartyConfiguration.put("thirdparty.provider1.allowedEvents", allowedEvents);

        // mock service
        EventServiceImpl eventService = new EventServiceImpl();
        eventService.setThirdPartyConfiguration(thirdPartyConfiguration);
        eventService.setRestrictedEventTypeIds(new HashSet<>(restrictedEventTypeIds));

        return eventService;
    }
}
