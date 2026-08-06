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
package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: untrusted events must not update another profile or systemProperties.
 */
@RunWith(MockitoJUnitRunner.class)
public class UpdatePropertiesActionTest {

    @Mock private ProfileService profileService;
    @Mock private EventService eventService;
    @Mock private SecurityService securityService;

    private UpdatePropertiesAction actionExecutor;

    @Before
    public void setUp() {
        actionExecutor = new UpdatePropertiesAction();
        actionExecutor.setProfileService(profileService);
        actionExecutor.setEventService(eventService);
        actionExecutor.setSecurityService(securityService);

        when(securityService.isAdmin()).thenReturn(false);
        when(securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR)).thenReturn(false);
    }

    @Test
    public void untrustedCaller_cannotUpdateAnotherProfile() {
        Profile caller = new Profile("caller");
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("properties.email", "pwned");
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put(UpdatePropertiesAction.TARGET_ID_KEY, "victim");
        eventProps.put(UpdatePropertiesAction.TARGET_TYPE_KEY, UpdatePropertiesAction.TARGET_TYPE_PROFILE);
        eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, updateMap);

        Event event = new Event("updateProperties", null, caller, "systemscope", null, null, eventProps, new Date(), true);
        Action action = new Action();

        int changes = actionExecutor.execute(action, event);

        assertEquals(EventService.NO_CHANGE, changes);
        verify(profileService, never()).load(any(String.class));
        verify(profileService, never()).save(any(Profile.class));
    }

    @Test
    public void untrustedCaller_cannotWriteSystemProperties() {
        Profile caller = new Profile("caller");
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("systemProperties.mergeIdentifier", "stolen");
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, updateMap);

        Event event = new Event("updateProperties", null, caller, "systemscope", null, null, eventProps, new Date(), true);

        int changes = actionExecutor.execute(new Action(), event);

        assertEquals(EventService.NO_CHANGE, changes);
        assertEquals(null, caller.getSystemProperties().get("mergeIdentifier"));
    }

    @Test
    public void trustedAdmin_canUpdateAnotherProfile() {
        when(securityService.isAdmin()).thenReturn(true);

        Profile caller = new Profile("caller");
        Profile victim = new Profile("victim");
        when(profileService.load("victim")).thenReturn(victim);

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("properties.email", "admin-set");
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put(UpdatePropertiesAction.TARGET_ID_KEY, "victim");
        eventProps.put(UpdatePropertiesAction.TARGET_TYPE_KEY, UpdatePropertiesAction.TARGET_TYPE_PROFILE);
        eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, updateMap);

        Event event = new Event("updateProperties", null, caller, "systemscope", null, null, eventProps, new Date(), true);

        actionExecutor.execute(new Action(), event);

        verify(profileService).load("victim");
        verify(profileService).save(victim);
    }
}
