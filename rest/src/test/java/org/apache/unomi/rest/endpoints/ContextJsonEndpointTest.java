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

import org.apache.unomi.api.ContextRequest;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.PersonalizationService;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ContextJsonEndpoint}. Private sanitization helpers are exercised via reflection
 * so filter/personalization hardening can be validated without a running container (UNOMI-945).
 */
class ContextJsonEndpointTest {

    private final ContextJsonEndpoint endpoint = new ContextJsonEndpoint();

    @Test
    void contextOptionsEndpoints_returnNoContentWithCorsHeader() {
        Response jsOptions = endpoint.contextJSAsOptions();
        assertEquals(204, jsOptions.getStatus());
        assertEquals("*", jsOptions.getHeaderString("Access-Control-Allow-Origin"));

        Response jsonOptions = endpoint.contextJSONAsOptions();
        assertEquals(204, jsonOptions.getStatus());
        assertEquals("*", jsonOptions.getHeaderString("Access-Control-Allow-Origin"));
    }

    @Test
    void destroy_logsShutdownWithoutThrowing() {
        endpoint.destroy();
    }

    @Test
    void sanitizeValue_keepsSafeStringsAndScalars() throws Exception {
        assertEquals("hello", invokeSanitizeValue("hello"));
        assertEquals(42, invokeSanitizeValue(42));
        assertEquals(true, invokeSanitizeValue(true));
    }

    @Test
    void sanitizeValue_filtersScriptAndParameterReferences() throws Exception {
        assertNull(invokeSanitizeValue("script::Runtime.getRuntime().exec(\"touch /tmp/evil\")"));
        assertNull(invokeSanitizeValue("parameter::eventTypeId"));
    }

    @Test
    void sanitizeValue_filtersScriptAndParameterReferencesFromLists() throws Exception {
        Object sanitized = invokeSanitizeValue(Arrays.asList(
                "safe-value",
                "script::Runtime.getRuntime().exec(\"touch /tmp/evil\")",
                "parameter::eventTypeId",
                "another-safe-value"));

        assertEquals(List.of("safe-value", "another-safe-value"), sanitized);
    }

    @Test
    void sanitizeValue_returnsEmptyListWhenAllEntriesAreFiltered() throws Exception {
        Object sanitized = invokeSanitizeValue(List.of("script::evil", "parameter::key"));
        assertEquals(Collections.emptyList(), sanitized);
    }

    @Test
    void sanitizeValue_filtersNestedScriptReferencesInMapsAndLists() throws Exception {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("allowed", List.of("ok", "script::evil"));
        nested.put("blocked", "parameter::key");

        @SuppressWarnings("unchecked")
        Map<Object, Object> sanitizedMap = (Map<Object, Object>) invokeSanitizeValue(nested);
        assertEquals(List.of("ok"), sanitizedMap.get("allowed"));
        assertTrue(!sanitizedMap.containsKey("blocked"));
    }

    @Test
    void sanitizeCondition_returnsNullWhenParameterContainsScriptReference() throws Exception {
        Condition condition = conditionWithParameters(Map.of(
                "propertyName", "firstName",
                "propertyValue", "script::evil"));

        assertNull(invokeSanitizeCondition(condition));
    }

    @Test
    void sanitizeCondition_returnsConditionWhenParametersAreSafe() throws Exception {
        Condition condition = conditionWithParameters(Map.of(
                "propertyName", "firstName",
                "propertyValue", "Jane"));

        assertNotNull(invokeSanitizeCondition(condition));
    }

    @Test
    void sanitizePersonalizedContentObjects_dropsContentWithUnsafeFilterCondition() throws Exception {
        PersonalizationService.Filter unsafeFilter = filterWithCondition(conditionWithParameters(Map.of(
                "propertyName", "firstName",
                "propertyValue", "script::evil")));
        PersonalizationService.Filter safeFilter = filterWithCondition(conditionWithParameters(Map.of(
                "propertyName", "firstName",
                "propertyValue", "Jane")));

        PersonalizationService.PersonalizedContent unsafeContent = personalizedContent("unsafe", unsafeFilter);
        PersonalizationService.PersonalizedContent safeContent = personalizedContent("safe", safeFilter);

        @SuppressWarnings("unchecked")
        List<PersonalizationService.PersonalizedContent> sanitized = (List<PersonalizationService.PersonalizedContent>)
                invokeSanitizePersonalizedContentObjects(List.of(unsafeContent, safeContent));

        assertEquals(1, sanitized.size());
        assertEquals("safe", sanitized.get(0).getId());
    }

    @Test
    void sanitizePersonalizations_dropsRequestWhenAllContentsAreUnsafe() throws Exception {
        PersonalizationService.Filter unsafeFilter = filterWithCondition(conditionWithParameters(Map.of(
                "propertyName", "firstName",
                "propertyValue", "script::evil")));
        PersonalizationService.PersonalizedContent unsafeContent = personalizedContent("variant", unsafeFilter);

        PersonalizationService.PersonalizationRequest request = new PersonalizationService.PersonalizationRequest();
        request.setId("perso-1");
        request.setContents(List.of(unsafeContent));

        @SuppressWarnings("unchecked")
        List<PersonalizationService.PersonalizationRequest> sanitized = (List<PersonalizationService.PersonalizationRequest>)
                invokeSanitizePersonalizations(List.of(request));

        assertTrue(sanitized.isEmpty());
    }

    @Test
    void processOverrides_appliesOverridesOnlyForPersonaProfiles() throws Exception {
        Persona persona = new Persona();
        persona.setItemId("persona-1");
        Session session = new Session();
        session.setItemId("session-1");
        session.setProperties(new HashMap<>(Map.of("campaign", "summer")));

        ContextRequest contextRequest = new ContextRequest();
        Profile profileOverrides = new Profile();
        profileOverrides.setProperty("firstName", "Test");
        contextRequest.setProfileOverrides(profileOverrides);
        contextRequest.setSessionPropertiesOverrides(Map.of("campaign", "winter"));

        invokeProcessOverrides(contextRequest, persona, session);
        assertEquals("Test", persona.getProperty("firstName"));
        assertEquals("winter", session.getProperty("campaign"));

        Profile regularProfile = new Profile();
        regularProfile.setItemId("profile-1");
        regularProfile.setProperty("firstName", "Original");
        Session regularSession = new Session();
        regularSession.setProperties(new HashMap<>(Map.of("campaign", "summer")));

        invokeProcessOverrides(contextRequest, regularProfile, regularSession);
        assertEquals("Original", regularProfile.getProperty("firstName"));
        assertEquals("summer", regularSession.getProperty("campaign"));
    }

    private static Condition conditionWithParameters(Map<String, Object> parameters) {
        Condition condition = new Condition();
        condition.setConditionTypeId("profilePropertyCondition");
        condition.setParameterValues(new HashMap<>(parameters));
        return condition;
    }

    private static PersonalizationService.Filter filterWithCondition(Condition condition) {
        PersonalizationService.Filter filter = new PersonalizationService.Filter();
        filter.setCondition(condition);
        return filter;
    }

    private static PersonalizationService.PersonalizedContent personalizedContent(String id,
            PersonalizationService.Filter... filters) {
        PersonalizationService.PersonalizedContent content = new PersonalizationService.PersonalizedContent();
        content.setId(id);
        content.setFilters(Arrays.asList(filters));
        return content;
    }

    private Object invokeSanitizeValue(Object value) throws Exception {
        return invokePrivate("sanitizeValue", new Class<?>[] { Object.class }, value);
    }

    private Object invokeSanitizeCondition(Condition condition) throws Exception {
        return invokePrivate("sanitizeCondition", new Class<?>[] { Condition.class }, condition);
    }

    private Object invokeSanitizePersonalizedContentObjects(List<PersonalizationService.PersonalizedContent> contents)
            throws Exception {
        return invokePrivate("sanitizePersonalizedContentObjects", new Class<?>[] { List.class }, contents);
    }

    private Object invokeSanitizePersonalizations(List<PersonalizationService.PersonalizationRequest> personalizations)
            throws Exception {
        return invokePrivate("sanitizePersonalizations", new Class<?>[] { List.class }, personalizations);
    }

    private void invokeProcessOverrides(ContextRequest contextRequest, Profile profile, Session session)
            throws Exception {
        invokePrivate("processOverrides",
                new Class<?>[] { ContextRequest.class, Profile.class, Session.class },
                contextRequest, profile, session);
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ContextJsonEndpoint.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(endpoint, args);
    }
}
