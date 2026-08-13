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

import org.apache.unomi.api.Persona;
import org.apache.unomi.api.PersonaSession;
import org.apache.unomi.api.PersonaWithSessions;
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
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        lenient().when(securityService.hasSystemAccess()).thenReturn(false);
        lenient().when(privacyService.isRequireAnonymousBrowsing(org.mockito.ArgumentMatchers.any(Profile.class))).thenReturn(false);
    }

    @Test
    void initEventsRequest_ignoresMismatchedBodyProfileIdForPublicCaller() {
        Profile cookieProfile = new Profile("cookie-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "public-supplied-profile", null,
                false, false, request, response, new Date());

        assertEquals("cookie-profile", ctx.getProfile().getItemId());
        verify(profileService).load("cookie-profile");
        verify(profileService, never()).load("public-supplied-profile");
    }

    @Test
    void initEventsRequest_ignoresBodyProfileIdWithoutCookieForPublicCaller() {
        when(request.getCookies()).thenReturn(null);

        try {
            restServiceUtils.initEventsRequest(
                    "systemscope", null, "other-profile-id", null,
                    false, false, request, response, new Date());
            throw new AssertionError("Expected BadRequestException when public caller has only a body profileId");
        } catch (javax.ws.rs.BadRequestException expected) {
            // Body profileId is ignored; with no cookie/session the request cannot bind a profile
        }

        verify(profileService, never()).load("other-profile-id");
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
        when(securityService.hasSystemAccess()).thenReturn(true);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("admin-chosen")).thenReturn(bodyProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "admin-chosen", null,
                false, false, request, response, new Date());

        assertEquals("admin-chosen", ctx.getProfile().getItemId());
    }

    @Test
    void initEventsRequest_trustedBodyOverride_notUndoneByMatchingCookieSession() {
        when(securityService.hasSystemAccess()).thenReturn(true);

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
        when(securityService.hasSystemAccess()).thenReturn(true);

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

    /**
     * {@code invalidateSession} re-creates the session bound to the supplied id, so it must not
     * be usable to sidestep the ownership rule that the binding path applies: a public caller may
     * only invalidate a session its own cookie already owns.
     */
    @Test
    void initEventsRequest_publicCallerCannotInvalidateAForeignSession() {
        Profile cookieProfile = new Profile("cookie-profile");
        Profile sessionOwner = new Profile("session-owner");
        Session foreignSession = new Session("foreign-sess", sessionOwner, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("foreign-sess")).thenReturn(foreignSession);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "foreign-sess", null, null,
                false, true, request, response, new Date());

        assertEquals("cookie-profile", ctx.getProfile().getItemId());
        assertTrue(ctx.isSessionRefused(), "the refusal must be visible to the endpoint building the response");
        assertNull(ctx.getSession(), "no session may be created for a refused id");
        // The refused id must not be written back over the real owner's session.
        verify(profileService, never()).saveSession(org.mockito.ArgumentMatchers.any(Session.class));
    }

    /** The same call is legitimate for a trusted caller, which may rebind sessions deliberately. */
    @Test
    void initEventsRequest_trustedCallerMayInvalidateAForeignSession() {
        when(securityService.hasSystemAccess()).thenReturn(true);

        Profile cookieProfile = new Profile("cookie-profile");
        Profile sessionOwner = new Profile("session-owner");
        Session foreignSession = new Session("foreign-sess", sessionOwner, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        lenient().when(profileService.loadSession("foreign-sess")).thenReturn(foreignSession);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "foreign-sess", null, null,
                false, true, request, response, new Date());

        assertFalse(ctx.isSessionRefused());
        assertNotNull(ctx.getSession(), "a trusted caller still gets a session for the supplied id");
        assertEquals("foreign-sess", ctx.getSession().getItemId());
    }

    /** A public caller invalidating a session it already owns is normal and must keep working. */
    @Test
    void initEventsRequest_publicCallerMayInvalidateItsOwnSession() {
        Profile cookieProfile = new Profile("cookie-profile");
        Session ownSession = new Session("own-sess", cookieProfile, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        lenient().when(profileService.loadSession("own-sess")).thenReturn(ownSession);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "own-sess", null, null,
                false, true, request, response, new Date());

        assertFalse(ctx.isSessionRefused());
        assertNotNull(ctx.getSession());
        assertEquals("own-sess", ctx.getSession().getItemId());
    }

    // ---------------------------------------------------------------------------------------
    // Anonymous browsing. All four branches of the anonymity handling in initEventsRequest are
    // pinned here BEFORE any change to the session-ownership check, because the ownership check
    // currently skips anonymous profiles entirely: tightening it without this safety net would
    // silently detach the session of every legitimately anonymous visitor on every request.
    // ---------------------------------------------------------------------------------------

    /**
     * Branch 1: the visitor wants anonymity and the session already carries an anonymous profile,
     * so nothing changes. This is the steady state of an anonymous visitor and must stay a no-op.
     */
    @Test
    void anonymousBrowsing_alreadyAnonymousSession_isLeftUntouched() {
        Profile cookieProfile = new Profile("cookie-profile");
        Profile anonymousProfile = anonymous();
        Session session = new Session("anon-sess", anonymousProfile, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("anon-sess")).thenReturn(session);
        when(privacyService.isRequireAnonymousBrowsing(cookieProfile)).thenReturn(true);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "anon-sess", null, null,
                false, false, request, response, new Date());

        assertFalse(ctx.isSessionRefused(), "an anonymous visitor's own session must not be refused");
        assertNotNull(ctx.getSession());
        assertTrue(ctx.getSession().getProfile().isAnonymousProfile(),
                "the session must keep its anonymous profile");
        assertEquals("cookie-profile", ctx.getProfile().getItemId(),
                "the request profile stays the real cookie profile");
    }

    /**
     * Branch 2: the visitor has just asked for anonymity while their session still carries the real
     * profile, so the session is switched to an anonymous profile. This is how anonymity is entered.
     */
    @Test
    void anonymousBrowsing_entering_replacesSessionProfileWithAnonymous() {
        Profile cookieProfile = new Profile("cookie-profile");
        Session session = new Session("sess", cookieProfile, new Date(), "systemscope");
        Profile anonymousProfile = anonymous();

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("sess")).thenReturn(session);
        when(privacyService.isRequireAnonymousBrowsing(cookieProfile)).thenReturn(true);
        when(privacyService.getAnonymousProfile(cookieProfile)).thenReturn(anonymousProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess", null, null,
                false, false, request, response, new Date());

        assertFalse(ctx.isSessionRefused());
        assertTrue(ctx.getSession().getProfile().isAnonymousProfile(),
                "entering anonymity must swap the session profile for an anonymous one");
        assertTrue((ctx.getChanges() & EventService.SESSION_UPDATED) != 0,
                "the session change must be flagged so it is persisted");
    }

    /**
     * Branch 3: the visitor has turned anonymity off, so their anonymous session is bound back to
     * their real profile. This is the branch an ownership check would most easily break, and it is
     * also the branch an untrusted caller reaches with a reused anonymous session id — so it must keep
     * working for the legitimate case while the fix is designed.
     */
    @Test
    void anonymousBrowsing_leaving_rebindsSessionToTheRealProfile() {
        Profile cookieProfile = new Profile("cookie-profile");
        Session session = new Session("anon-sess", anonymous(), new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("anon-sess")).thenReturn(session);
        when(privacyService.isRequireAnonymousBrowsing(cookieProfile)).thenReturn(false);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "anon-sess", null, null,
                false, false, request, response, new Date());

        assertFalse(ctx.isSessionRefused());
        assertNotNull(ctx.getSession());
        assertEquals("cookie-profile", ctx.getSession().getProfile().getItemId(),
                "leaving anonymity must bind the session back to the visitor's real profile");
    }

    /** Branch 4: the ordinary non-anonymous case — the session is bound to the caller's profile. */
    @Test
    void anonymousBrowsing_notAnonymousAtAll_bindsSessionToCallerProfile() {
        Profile cookieProfile = new Profile("cookie-profile");
        Session session = new Session("sess", cookieProfile, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadSession("sess")).thenReturn(session);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess", null, null,
                false, false, request, response, new Date());

        assertFalse(ctx.isSessionRefused());
        assertEquals("cookie-profile", ctx.getSession().getProfile().getItemId());
    }

    // ---------------------------------------------------------------------------------------
    // Personas. A personaId short-circuits binding entirely: the profile and session both come
    // from the persona, and the cookie/body binding logic below it never runs. Nothing covered
    // this before, so a change to the binding code could have silently broken persona preview.
    // ---------------------------------------------------------------------------------------

    /** A persona overrides the cookie profile outright, and brings its own session with it. */
    @Test
    void persona_overridesCookieProfileAndSuppliesItsOwnSession() {
        Persona persona = new Persona("persona-1");
        PersonaSession personaSession = new PersonaSession("persona-sess", persona, new Date());
        PersonaWithSessions personaWithSessions =
                new PersonaWithSessions(persona, Collections.singletonList(personaSession));

        lenient().when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.loadPersonaWithSessions("persona-1")).thenReturn(personaWithSessions);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, null, "persona-1",
                false, false, request, response, new Date());

        assertEquals("persona-1", ctx.getProfile().getItemId(), "the persona must win over the cookie");
        assertNotNull(ctx.getSession(), "the persona's own session must be used");
        assertEquals("persona-sess", ctx.getSession().getItemId());
        verify(profileService, never()).load("cookie-profile");
    }

    /** A persona also wins over an explicitly supplied body profileId. */
    @Test
    void persona_winsOverBodyProfileId() {
        Persona persona = new Persona("persona-1");
        PersonaWithSessions personaWithSessions =
                new PersonaWithSessions(persona, Collections.singletonList(
                        new PersonaSession("persona-sess", persona, new Date())));

        when(profileService.loadPersonaWithSessions("persona-1")).thenReturn(personaWithSessions);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, "some-other-profile", "persona-1",
                false, false, request, response, new Date());

        assertEquals("persona-1", ctx.getProfile().getItemId());
        verify(profileService, never()).load("some-other-profile");
    }

    /**
     * An unknown persona must not blow up the request: the persona is simply not applied and the
     * normal cookie binding takes over, so a stale persona id degrades to ordinary tracking.
     */
    @Test
    void persona_unknownId_fallsBackToNormalCookieBinding() {
        Profile cookieProfile = new Profile("cookie-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        when(profileService.load("cookie-profile")).thenReturn(cookieProfile);
        when(profileService.loadPersonaWithSessions("missing-persona")).thenReturn(null);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, null, "missing-persona",
                false, false, request, response, new Date());

        assertEquals("cookie-profile", ctx.getProfile().getItemId());
    }

    // ---------------------------------------------------------------------------------------
    // invalidateProfile. Untested at IT level, and it sits inside the same block the security
    // changes rewrote, so pin it: it must still hand the visitor a brand new profile rather than
    // reusing the cookie one.
    // ---------------------------------------------------------------------------------------

    /** invalidateProfile discards the cookie profile and issues a fresh one. */
    @Test
    void invalidateProfile_issuesANewProfileInsteadOfTheCookieOne() {
        Profile cookieProfile = new Profile("cookie-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        lenient().when(profileService.load("cookie-profile")).thenReturn(cookieProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, null, null,
                true, false, request, response, new Date());

        assertNotNull(ctx.getProfile());
        assertFalse("cookie-profile".equals(ctx.getProfile().getItemId()),
                "invalidateProfile must not reuse the cookie profile");
    }

    /** invalidateProfile is honoured for a trusted caller too, not silently swallowed by the trust path. */
    @Test
    void invalidateProfile_alsoAppliesForTrustedCallers() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        Profile cookieProfile = new Profile("cookie-profile");
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(COOKIE_NAME, "cookie-profile")});
        lenient().when(profileService.load("cookie-profile")).thenReturn(cookieProfile);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", null, null, null,
                true, false, request, response, new Date());

        assertNotNull(ctx.getProfile());
        assertFalse("cookie-profile".equals(ctx.getProfile().getItemId()));
    }

    /**
     * A trusted server-side integration has no browser and therefore no profile cookie, so an
     * explicit body profileId is the only way it can name the profile it means. Before the fix the
     * override only counted when a cookie was also present, so this request had its profile silently
     * replaced by the session's owner - the opposite of the documented behaviour for trusted callers.
     */
    @Test
    void trustedCaller_explicitBodyProfileId_survivesWithoutACookie() {
        when(securityService.hasSystemAccess()).thenReturn(true);
        Profile intended = new Profile("intended-profile");
        Profile sessionOwner = new Profile("session-owner");
        Session session = new Session("sess-1", sessionOwner, new Date(), "systemscope");

        when(request.getCookies()).thenReturn(null);
        when(profileService.load("intended-profile")).thenReturn(intended);
        when(profileService.loadSession("sess-1")).thenReturn(session);

        EventsRequestContext ctx = restServiceUtils.initEventsRequest(
                "systemscope", "sess-1", "intended-profile", null,
                false, false, request, response, new Date());

        assertEquals("intended-profile", ctx.getProfile().getItemId(),
                "a trusted caller's explicit profileId must not be overridden by the session owner");
        verify(profileService, never()).load("session-owner");
    }

    /** A profile carrying the anonymous marker, as {@code PrivacyService#getAnonymousProfile} builds it. */
    private static Profile anonymous() {
        Profile anonymousProfile = new Profile();
        anonymousProfile.getSystemProperties().put("isAnonymousProfile", true);
        return anonymousProfile;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
