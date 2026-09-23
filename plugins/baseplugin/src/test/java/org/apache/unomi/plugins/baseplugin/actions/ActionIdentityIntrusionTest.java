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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Intrusion scenarios against the two identity-bearing actions, run the way a public-key caller
 * would drive them: every classic injection shape for a property path, every reserved field of the
 * profile bean, and hostile merge values. Each scenario asserts on the resulting profile state, not
 * on the return code alone, so a regression in either action (or in PropertyHelper, which resolves
 * the names through commons-beanutils) shows up as a concrete unwanted write.
 */
@RunWith(MockitoJUnitRunner.class)
public class ActionIdentityIntrusionTest {

    @Mock private ProfileService profileService;
    @Mock private PersistenceService persistenceService;
    @Mock private EventService eventService;
    @Mock private DefinitionsService definitionsService;
    @Mock private PrivacyService privacyService;
    @Mock private SecurityService securityService;

    private UpdatePropertiesAction updateAction;
    private MergeProfilesOnPropertyAction mergeAction;

    /** Property paths a public caller must never be able to write through, whatever the value. */
    private static final List<String> HOSTILE_PATHS = Arrays.asList(
            // identity and bookkeeping fields of the bean
            "itemId", "itemType", "tenantId", "version", "mergedWith", "scope", "systemMetadata",
            // trust-bearing areas reserved to trusted callers
            "systemProperties", "systemProperties.mergeIdentifier", "systemProperties.mergedWith",
            "segments", "scores.vip", "consents.newsletter",
            // beanutils mapped / indexed / chained syntax
            "systemProperties(mergeIdentifier)", "systemProperties[0]", "systemProperties(a).b",
            "systemProperties(a)(b)", "properties(a.b)", "properties[0]",
            // path shape tricks: empty segments, leading/trailing dots, control characters
            "properties..x", "properties.", ".systemProperties.mergeIdentifier",
            "properties.a\tb", "properties.a\nb", "properties\u0000.x",
            // the classic commons-beanutils gadget
            "class.classLoader.URLs", "class",
            // encodings and lookalikes that never resolve, listed so a future decoder cannot
            // quietly turn them into the real thing
            "systemProperties%2EmergeIdentifier", "systemProperties\\.mergeIdentifier",
            "SystemProperties.mergeIdentifier", "\uFF53ystemProperties.mergeIdentifier",
            "systemProperties\u200B.mergeIdentifier");

    /** Values covering the shapes JSON can deliver: scalars, collections, and nested maps. */
    private static final List<Object> HOSTILE_VALUES = Arrays.asList(
            "victim-profile-id",
            Collections.singletonMap("mergeIdentifier", "victim@example.com"),
            new ArrayList<>(Arrays.asList("vip", "admin")),
            Long.valueOf(99),
            Boolean.TRUE);

    @Before
    public void setUp() {
        updateAction = new UpdatePropertiesAction();
        updateAction.setProfileService(profileService);
        updateAction.setEventService(eventService);
        updateAction.setSecurityService(securityService);

        mergeAction = new MergeProfilesOnPropertyAction();
        mergeAction.setProfileService(profileService);
        mergeAction.setPersistenceService(persistenceService);
        mergeAction.setEventService(eventService);
        mergeAction.setDefinitionsService(definitionsService);
        mergeAction.setPrivacyService(privacyService);
        mergeAction.bindSecurityService(securityService);
        mergeAction.setMaxProfilesInOneMerge("50");

        when(securityService.hasSystemAccess()).thenReturn(false);
    }

    @Test
    public void publicCaller_hostilePathsLeaveEveryReservedFieldUntouched_add_update_addToSet() {
        for (String strategyKey : new String[] {
                UpdatePropertiesAction.PROPS_TO_ADD, UpdatePropertiesAction.PROPS_TO_UPDATE, UpdatePropertiesAction.PROPS_TO_ADD_TO_SET}) {
            for (String path : HOSTILE_PATHS) {
                for (Object value : HOSTILE_VALUES) {
                    Profile caller = freshCaller();
                    Map<String, Object> props = new HashMap<>();
                    props.put(path, value);
                    Map<String, Object> eventProps = new HashMap<>();
                    eventProps.put(strategyKey, props);

                    int changes = updateAction.execute(new Action(), event(caller, eventProps));

                    String scenario = strategyKey + " " + printable(path) + " = " + value;
                    assertEquals(scenario, EventService.NO_CHANGE, changes);
                    assertUntouched(scenario, caller);
                }
            }
        }
    }

    @Test
    public void publicCaller_hostilePathsCannotDeleteReservedFields() {
        for (String path : HOSTILE_PATHS) {
            Profile caller = freshCaller();
            Map<String, Object> eventProps = new HashMap<>();
            eventProps.put(UpdatePropertiesAction.PROPS_TO_DELETE, Collections.singletonList(path));

            int changes = updateAction.execute(new Action(), event(caller, eventProps));

            assertEquals("delete " + printable(path), EventService.NO_CHANGE, changes);
            assertUntouched("delete " + printable(path), caller);
        }
    }

    @Test
    public void publicCaller_mixedBatchAppliesOnlyTheOrdinaryProperty() {
        Profile caller = freshCaller();
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("properties.firstName", "Alice");
        props.put("systemProperties.mergeIdentifier", "victim@example.com");
        props.put("itemId", "victim-profile-id");
        Map<String, Object> eventProps = new HashMap<>();
        eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, props);

        int changes = updateAction.execute(new Action(), event(caller, eventProps));

        assertEquals(EventService.PROFILE_UPDATED, changes);
        assertEquals("Alice", caller.getProperty("firstName"));
        assertUntouched("mixed batch", caller);
    }

    @Test
    public void publicCaller_cannotReachAnotherProfileThroughTargetIdVariants() {
        for (String targetId : Arrays.asList("victim-profile-id", " victim-profile-id", "victim-profile-id\n", "../victim")) {
            for (String targetType : Arrays.asList("profile", "persona", "", "PROFILE", null)) {
                Profile caller = freshCaller();
                Map<String, Object> props = new HashMap<>();
                props.put("properties.firstName", "Mallory");
                Map<String, Object> eventProps = new HashMap<>();
                eventProps.put(UpdatePropertiesAction.TARGET_ID_KEY, targetId);
                eventProps.put(UpdatePropertiesAction.TARGET_TYPE_KEY, targetType);
                eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, props);

                int changes = updateAction.execute(new Action(), event(caller, eventProps));

                assertEquals(EventService.NO_CHANGE, changes);
                assertNull("caller's own profile must not be written either", caller.getProperty("firstName"));
            }
        }
        verify(profileService, never()).load(anyString());
        verify(profileService, never()).loadPersona(anyString());
        verify(profileService, never()).save(any(Profile.class));
    }

    @Test
    public void trustedCaller_reservedIdentityFieldsAreNeverWritable() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        for (String path : Arrays.asList("itemId", "itemType", "tenantId", "version", "mergedWith", "scope", "class.classLoader.URLs",
                "systemProperties(mergeIdentifier)", "properties(a.b)", "properties..x")) {
            Profile caller = freshCaller();
            Map<String, Object> props = new HashMap<>();
            props.put(path, "victim-profile-id");
            Map<String, Object> eventProps = new HashMap<>();
            eventProps.put(UpdatePropertiesAction.PROPS_TO_UPDATE, props);

            int changes = updateAction.execute(new Action(), event(caller, eventProps));

            assertEquals("trusted " + printable(path), EventService.NO_CHANGE, changes);
            assertUntouched("trusted " + printable(path), caller);
        }
    }

    @Test
    public void publicCaller_hostileMergeValuesNeverRecordOrMergeAnIdentity() {
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(new ConditionType());
        Profile other = new Profile("victim-profile-id");
        other.getSystemProperties().put("mergeIdentifier", "victim@example.com");
        when(persistenceService.query(any(), anyString(), eq(Profile.class), anyInt(), anyInt()))
                .thenReturn(new PartialList<>(new ArrayList<>(Collections.singletonList(other)), 0, 1, 1, PartialList.Relation.EQUAL));

        for (String value : Arrays.asList("victim@example.com", "*", "victim@example.com OR 1=1", "victim@example.com\n",
                "{\"query\":{\"match_all\":{}}}", "victim-profile-id")) {
            Profile caller = freshCaller();
            Action action = new Action();
            Map<String, Object> params = new HashMap<>();
            params.put("mergeProfilePropertyName", "mergeIdentifier");
            params.put("mergeProfilePropertyValue", value);
            action.setParameterValues(params);
            Event event = new Event("login", null, caller, "systemscope", null, null, null, new Date(), true);

            int changes = mergeAction.execute(action, event);

            assertEquals(printable(value), EventService.NO_CHANGE, changes);
            assertEquals(printable(value), "attacker", event.getProfile().getItemId());
            assertUntouched(printable(value), caller);
        }
        verify(profileService, never()).mergeProfiles(any(), any());
    }

    private static Profile freshCaller() {
        Profile caller = new Profile("attacker");
        caller.getSystemProperties().put("existing", "keep");
        return caller;
    }

    private static Event event(Profile caller, Map<String, Object> eventProps) {
        return new Event("updateProperties", null, caller, "systemscope", null, null, eventProps, new Date(), true);
    }

    /** Everything a public caller must not be able to change on its own profile. */
    private static void assertUntouched(String scenario, Profile caller) {
        assertEquals(scenario, "attacker", caller.getItemId());
        assertEquals(scenario, Profile.ITEM_TYPE, caller.getItemType());
        assertNull(scenario, caller.getMergedWith());
        assertNull(scenario, caller.getTenantId());
        assertEquals(scenario, Collections.singletonMap("existing", "keep"), caller.getSystemProperties());
        assertTrue(scenario, caller.getSegments().isEmpty());
        assertTrue(scenario, caller.getScores() == null || caller.getScores().isEmpty());
        assertTrue(scenario, caller.getConsents() == null || caller.getConsents().isEmpty());
        assertNull(scenario + " (map key 'class' would mean the gadget path was reached)", caller.getProperty("class"));
    }

    private static String printable(String s) {
        return s.replace("\t", "\\t").replace("\n", "\\n").replace("\u0000", "\\0");
    }
}
