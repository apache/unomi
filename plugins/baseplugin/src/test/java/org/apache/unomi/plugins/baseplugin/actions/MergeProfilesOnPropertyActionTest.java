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
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: public/untrusted events must not rebind a session onto another profile via merge.
 */
@RunWith(MockitoJUnitRunner.class)
public class MergeProfilesOnPropertyActionTest {

    @Mock private ProfileService profileService;
    @Mock private PersistenceService persistenceService;
    @Mock private EventService eventService;
    @Mock private DefinitionsService definitionsService;
    @Mock private PrivacyService privacyService;
    @Mock private SecurityService securityService;

    private MergeProfilesOnPropertyAction actionExecutor;

    @Before
    public void setUp() {
        actionExecutor = new MergeProfilesOnPropertyAction();
        actionExecutor.setProfileService(profileService);
        actionExecutor.setPersistenceService(persistenceService);
        actionExecutor.setEventService(eventService);
        actionExecutor.setDefinitionsService(definitionsService);
        actionExecutor.setPrivacyService(privacyService);
        actionExecutor.bindSecurityService(securityService);
        actionExecutor.setMaxProfilesInOneMerge("50");

        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(new ConditionType());
        when(securityService.hasSystemAccess()).thenReturn(false);
    }

    @Test
    public void untrustedCaller_cannotMergeIntoExistingOtherProfile() {
        Profile publicCaller = new Profile("public-caller");
        Profile other = new Profile("other");
        other.getSystemProperties().put("mergeIdentifier", "other@example.com");

        when(persistenceService.query(any(), anyString(), eq(Profile.class), anyInt(), anyInt()))
                .thenReturn(new PartialList<>(new ArrayList<>(Collections.singletonList(other)), 0, 1, 1, PartialList.Relation.EQUAL));

        Event event = new Event("login", null, publicCaller, "systemscope", null, null, null, new Date(), true);
        Action action = mergeAction("other@example.com");

        int changes = actionExecutor.execute(action, event);

        // May write mergeIdentifier onto the untrusted caller profile, but must not rebind to other
        assertNotEquals(EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED, changes);
        assertEquals("public-caller", event.getProfile().getItemId());
        verify(profileService, never()).mergeProfiles(any(), any());
    }

    @Test
    public void trustedTenantAdmin_canMergeIntoExistingProfile() {
        when(securityService.hasSystemAccess()).thenReturn(true);

        Profile caller = new Profile("caller");
        Profile other = new Profile("other");
        other.setProperty("firstVisit", new Date(0));
        caller.setProperty("firstVisit", new Date());

        when(persistenceService.query(any(), anyString(), eq(Profile.class), anyInt(), anyInt()))
                .thenReturn(new PartialList<>(new ArrayList<>(Collections.singletonList(other)), 0, 1, 1, PartialList.Relation.EQUAL));
        when(profileService.mergeProfiles(eq(other), any())).thenReturn(other);
        when(privacyService.isRequireAnonymousBrowsing(any(Profile.class))).thenReturn(false);
        when(privacyService.isRequireAnonymousBrowsing("other")).thenReturn(false);

        Event event = new Event("login", null, caller, "systemscope", null, null, null, new Date(), true);
        Action action = mergeAction("other@example.com");

        int changes = actionExecutor.execute(action, event);

        assertEquals(EventService.PROFILE_UPDATED + EventService.SESSION_UPDATED, changes);
        assertEquals("other", event.getProfile().getItemId());
        verify(profileService).mergeProfiles(eq(other), any());
    }

    private static Action mergeAction(String mergeValue) {
        Action action = new Action();
        Map<String, Object> params = new HashMap<>();
        params.put("mergeProfilePropertyName", "mergeIdentifier");
        params.put("mergeProfilePropertyValue", mergeValue);
        action.setParameterValues(params);
        return action;
    }
}
