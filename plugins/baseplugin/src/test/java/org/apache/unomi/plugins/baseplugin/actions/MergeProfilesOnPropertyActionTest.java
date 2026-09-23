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
import static org.junit.Assert.assertNull;
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

        assertEquals(EventService.NO_CHANGE, changes);
        assertEquals("public-caller", event.getProfile().getItemId());
        assertNull("the untrusted caller must not record the identity claim either",
                publicCaller.getSystemProperties().get("mergeIdentifier"));
        verify(profileService, never()).mergeProfiles(any(), any());
    }

    /**
     * The identifier write is the poisoning primitive, independently of whether a candidate exists
     * today: a value planted here is what a later trusted login for the same identifier merges on,
     * which would pull the planter's profile into the real owner's merge and alias it onto the
     * master. So the empty-candidates path must refuse too, not only the merge path.
     */
    @Test
    public void untrustedCaller_cannotRecordMergeIdentifierEvenWithoutCandidates() {
        Profile publicCaller = new Profile("public-caller");

        when(persistenceService.query(any(), anyString(), eq(Profile.class), anyInt(), anyInt()))
                .thenReturn(new PartialList<>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL));

        Event event = new Event("login", null, publicCaller, "systemscope", null, null, null, new Date(), true);

        int changes = actionExecutor.execute(mergeAction("victim@example.com"), event);

        assertEquals(EventService.NO_CHANGE, changes);
        assertNull(publicCaller.getSystemProperties().get("mergeIdentifier"));
    }

    @Test
    public void trustedTenantAdmin_recordsMergeIdentifierWhenNoCandidateExists() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        Profile caller = new Profile("caller");

        when(persistenceService.query(any(), anyString(), eq(Profile.class), anyInt(), anyInt()))
                .thenReturn(new PartialList<>(new ArrayList<>(), 0, 0, 0, PartialList.Relation.EQUAL));

        Event event = new Event("login", null, caller, "systemscope", null, null, null, new Date(), true);

        int changes = actionExecutor.execute(mergeAction("me@example.com"), event);

        assertEquals(EventService.PROFILE_UPDATED, changes);
        assertEquals("me@example.com", caller.getSystemProperties().get("mergeIdentifier"));
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
