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
package org.apache.unomi.rest.service.impl;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.apache.unomi.rest.authentication.V2ThirdPartyConfigService;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.utils.EventsRequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for public profileId/sessionId bearer binding on context/eventcollector paths.
 */
@ExtendWith(MockitoExtension.class)
class RestServiceUtilsImplProfileBindingTest {

    private static final String COOKIE_NAME = "context-profile-id";

    @Mock private ConfigSharingService configSharingService;
    @Mock private PrivacyService privacyService;
    @Mock private EventService eventService;
    @Mock private ProfileService profileService;
    @Mock private SchemaService schemaService;
    @Mock private RestAuthenticationConfig restAuthenticationConfig;
    @Mock private V2ThirdPartyConfigService v2ThirdPartyConfigService;
    @Mock private SecurityService securityService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private RestServiceUtilsImpl restServiceUtils;

    @BeforeEach
    void setUp() throws Exception {
        restServiceUtils = new RestServiceUtilsImpl();
        setField(restServiceUtils, "configSharingService", configSharingService);
        setField(restServiceUtils, "privacyService", privacyService);
        setField(restServiceUtils, "eventService", eventService);
        setField(restServiceUtils, "profileService", profileService);
        setField(restServiceUtils, "schemaService", schemaService);
        setField(restServiceUtils, "restAuthenticationConfig", restAuthenticationConfig);
        setField(restServiceUtils, "v2ThirdPartyConfigService", v2ThirdPartyConfigService);
        setField(restServiceUtils, "securityService", securityService);

        lenient().when(configSharingService.getProperty("profileIdCookieName")).thenReturn(COOKIE_NAME);
        lenient().when(schemaService.isValid(anyString(), anyString())).thenReturn(true);
        lenient().when(securityService.isAdmin()).thenReturn(false);
        lenient().when(securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR)).thenReturn(false);
        lenient().when(privacyService.isRequireAnonymousBrowsing(org.mockito.ArgumentMatchers.any(Profile.class))).thenReturn(false);
    }

    @Test
    void initEventsRequest_ignoresMismatchedBodyProfileIdForPublicCaller() {
        Profile cookieProfile = new Profile("cookie-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "attacker-supplied-profile", null,
                false, false, request, response, new Date());

        assertEquals("cookie-profile", ctx.getProfile().getItemId());
        verify(profileService).load("cookie-profile");
        verify(profileService, never()).load("attacker-supplied-profile");
    }

    @Test
    void initEventsRequest_ignoresBodyProfileIdWithoutCookieForPublicCaller() {
        when(request.getCookies()).thenReturn(null);

        try {
            restServiceUtils.initEventsRequest(
                    "systemscope", null, "victim-profile-id", null,
                    false, false, request, response, new Date());
            throw new AssertionError("Expected BadRequestException when public caller has only a body profileId");
        } catch (javax.ws.rs.BadRequestException expected) {
            // Body profileId is ignored; with no cookie/session the request cannot bind a profile
        }

        verify(profileService, never()).load("victim-profile-id");
    }

    @Test
    void initEventsRequest_allowsBodyProfileIdWhenItMatchesCookie() {
        Profile profile = new Profile("same-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "same-profile")});
        when(profileService.load("same-profile")).thenReturn(profile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "same-profile", null,
                false, false, request, response, new Date());

        assertEquals("same-profile", ctx.getProfile().getItemId());
    }

    @Test
    void initEventsRequest_refusesSessionProfileSwitchWithoutMatchingCookie() {
        Profile cookieProfile = new Profile("cookie-profile");
        Profile sessionOwner = new Profile("session-owner");
        Session session = new Session("sess-1", sessionOwner, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("sess-1")).thenReturn(session);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess-1", "cookie-profile", null,
                false, false, request, response, new Date());

        assertEquals("cookie-profile", ctx.getProfile().getItemId());
        // Foreign session must be detached (not rebound to the cookie profile)
        assertEquals(null, ctx.getSession());
        assertEquals("session-owner", session.getProfileId());
        verify(profileService, never()).load("session-owner");
    }

    @Test
    void initEventsRequest_trustedAdminMayUseBodyProfileIdOverride() {
        Profile bodyProfile = new Profile("admin-chosen");
        when(securityService.isAdmin()).thenReturn(true);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("admin-chosen")).thenReturn(bodyProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "admin-chosen", null,
                false, false, request, response, new Date());

        assertEquals("admin-chosen", ctx.getProfile().getItemId());
    }

    @Test
    void initEventsRequest_trustedBodyOverride_notUndoneByMatchingCookieSession() {
        when(securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR)).thenReturn(true);

        Profile cookieProfile = new Profile("cookie-profile");
        Profile bodyProfile = new Profile("admin-chosen");
        Session session = new Session("sess-1", cookieProfile, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("admin-chosen")).thenReturn(bodyProfile);
        when(profileService.loadSession("sess-1")).thenReturn(session);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess-1", "admin-chosen", null,
                false, false, request, response, new Date());

        assertEquals("admin-chosen", ctx.getProfile().getItemId());
    }

    @Test
    void initEventsRequest_trustedCaller_maySwitchToSessionProfile() {
        when(securityService.hasRole(UnomiRoles.TENANT_ADMINISTRATOR)).thenReturn(true);

        Profile cookieProfile = new Profile("cookie-profile");
        Profile sessionOwner = new Profile("session-owner");
        Session session = new Session("sess-1", sessionOwner, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("sess-1")).thenReturn(session);
        when(profileService.load("session-owner")).thenReturn(sessionOwner);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess-1", "cookie-profile", null,
                false, false, request, response, new Date());

        assertEquals("session-owner", ctx.getProfile().getItemId());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
