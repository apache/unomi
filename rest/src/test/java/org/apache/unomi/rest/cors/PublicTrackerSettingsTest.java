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
package org.apache.unomi.rest.cors;

import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeEvent;
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeEvent.ConfigChangeEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTrackerSettingsTest {

    @Mock
    private ConfigSharingService configSharingService;

    private PublicTrackerSettings settings;

    @BeforeEach
    void setUp() throws Exception {
        settings = new PublicTrackerSettings();
        Field field = PublicTrackerSettings.class.getDeclaredField("configSharingService");
        field.setAccessible(true);
        field.set(settings, configSharingService);
    }

    @Test
    void readsTheSettingsAtActivation() {
        when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        when(configSharingService.getProperty(PublicTrackerSettings.SAME_SITE_PROPERTY)).thenReturn(" None ");

        settings.activate();

        assertTrue(settings.policy().allows("https://shop.example"));
        assertTrue(settings.profileCookie("p1", true).contains("; SameSite=None; Secure"));
    }

    @Test
    void defaultsWhenNothingIsConfigured() {
        settings.activate();

        assertTrue(settings.policy().isEmpty());
        assertEquals("context-profile-id=p1; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly", settings.profileCookie("p1", false));
    }

    @Test
    void reparsesOnlyWhenOneOfItsPropertiesChanges() {
        settings.activate();
        PublicTrackerCorsPolicy before = settings.policy();
        clearInvocations(configSharingService);

        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED, "publicPostRequestBytesLimit", 1, 2));
        assertSame(before, settings.policy());
        verifyNoInteractions(configSharingService);

        when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED,
                PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY, "", "https://shop.example"));
        assertTrue(settings.policy().allows("https://shop.example"));

        when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn(null);
        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.REMOVED,
                PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY, "https://shop.example", null));
        assertFalse(settings.policy().allows("https://shop.example"));
    }

    @Test
    void cookieAttributesFollowTheConfiguration() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_NAME_PROPERTY)).thenReturn("visitor");
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_DOMAIN_PROPERTY)).thenReturn("example.com");
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_MAX_AGE_PROPERTY)).thenReturn(60);
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_HTTP_ONLY_PROPERTY)).thenReturn(Boolean.FALSE);

        settings.activate();

        assertEquals("visitor", settings.cookieName());
        assertEquals("visitor=p1; Path=/; Max-Age=60; Domain=example.com; SameSite=Lax; Secure", settings.profileCookie("p1", true));
        assertEquals("visitor=p1; Path=/; Max-Age=60; Domain=example.com; SameSite=Lax", settings.profileCookie("p1", false));
    }

    @Test
    void cookieChangeIsPickedUp() {
        settings.activate();
        assertEquals("context-profile-id", settings.cookieName());

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_NAME_PROPERTY)).thenReturn("renamed");
        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED,
                PublicTrackerSettings.COOKIE_NAME_PROPERTY, "context-profile-id", "renamed"));

        assertEquals("renamed", settings.cookieName());
    }

    @Test
    void sameSiteIsResolvedForBothTransports() {
        PublicTrackerSettings none = new PublicTrackerSettings("https://shop.example", "none");
        assertTrue(none.profileCookie("p1", true).contains("SameSite=None; Secure"));
        assertTrue(none.profileCookie("p1", false).contains("SameSite=Lax"));
        assertTrue(new PublicTrackerSettings("", "None").profileCookie("p1", true).contains("SameSite=Lax; Secure"));
        assertTrue(new PublicTrackerSettings("", "Strict").profileCookie("p1", false).contains("SameSite=Strict"));
    }

    @Test
    void httpOnlyIsOnlyTurnedOffByAnExplicitFalse() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_HTTP_ONLY_PROPERTY)).thenReturn("yes");
        settings.activate();
        assertTrue(settings.profileCookie("p1", false).endsWith("; HttpOnly"));

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_HTTP_ONLY_PROPERTY)).thenReturn(" FALSE ");
        settings.activate();
        assertFalse(settings.profileCookie("p1", false).contains("HttpOnly"));
    }

    @Test
    void unusableMaxAgeFallsBackToTheDefault() {
        for (Object value : new Object[]{0, -1, "soon", " 120 "}) {
            lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_MAX_AGE_PROPERTY)).thenReturn(value);
            settings.activate();
            String expected = " 120 ".equals(value) ? "Max-Age=120;" : "Max-Age=31536000;";
            assertTrue(settings.profileCookie("p1", false).contains(expected), String.valueOf(value));
        }
    }

    @Test
    void everyWatchedPropertyTriggersAReload() {
        for (String property : new String[]{PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY, PublicTrackerSettings.SAME_SITE_PROPERTY,
                PublicTrackerSettings.MATCH_REFERER_PROPERTY,
                PublicTrackerSettings.COOKIE_NAME_PROPERTY, PublicTrackerSettings.COOKIE_DOMAIN_PROPERTY,
                PublicTrackerSettings.COOKIE_MAX_AGE_PROPERTY, PublicTrackerSettings.COOKIE_HTTP_ONLY_PROPERTY}) {
            clearInvocations(configSharingService);
            settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED, property, null, "x"));
            verify(configSharingService).getProperty(PublicTrackerSettings.COOKIE_NAME_PROPERTY);
        }
    }

    @Test
    void refererMatchingIsOnlyTurnedOnByAnExplicitTrue() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        settings.activate();
        assertFalse(settings.policy().matchesReferer());

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.MATCH_REFERER_PROPERTY)).thenReturn("yes");
        settings.activate();
        assertFalse(settings.policy().matchesReferer());

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.MATCH_REFERER_PROPERTY)).thenReturn(Boolean.TRUE);
        settings.activate();
        assertTrue(settings.policy().matchesReferer());
    }

    @Test
    void aListWithNoValidEntryAllowsNoOriginAndSaysSo() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("shop.example, *");
        settings.activate();
        assertFalse(settings.policy().allows("https://shop.example"));
        assertTrue(hasWarning("Ignoring public CORS allowed origin 'shop.example'"));
        assertTrue(hasWarning("Ignoring public CORS allowed origin '*'"));
        assertTrue(hasWarning("None of the public CORS allowed origins"));
    }

    @Test
    void misconfiguredSettingsAreReported() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.MATCH_REFERER_PROPERTY)).thenReturn("yes");
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_MAX_AGE_PROPERTY)).thenReturn(0);
        settings.activate();
        assertTrue(hasWarning("Unrecognised value 'yes' for org.apache.unomi.public.cors.matchReferer"));
        assertTrue(hasWarning("Invalid profile cookie max age '0'"));

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.MATCH_REFERER_PROPERTY)).thenReturn("true");
        settings.activate();
        assertTrue(hasWarning("matchReferer is on but no website origin is listed"));

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.MATCH_REFERER_PROPERTY)).thenReturn(Boolean.FALSE);
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_MAX_AGE_PROPERTY)).thenReturn(60);
        settings.activate();
        assertTrue(settings.configurationWarnings().isEmpty(), String.valueOf(settings.configurationWarnings()));
    }

    @Test
    void refusedRequestWithoutOriginIsReportedOncePerConfiguration() {
        // Nothing listed: every website is refused by design, nothing to report.
        assertFalse(new PublicTrackerSettings("", null).reportRefusedRequestWithoutOrigin(false));

        PublicTrackerSettings listed = new PublicTrackerSettings("https://shop.example", null);
        assertTrue(listed.reportRefusedRequestWithoutOrigin(true));
        assertFalse(listed.reportRefusedRequestWithoutOrigin(false));

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        settings.activate();
        assertTrue(settings.reportRefusedRequestWithoutOrigin(false));
        assertFalse(settings.reportRefusedRequestWithoutOrigin(false));
        // A configuration change re-arms it.
        settings.configChanged(new ConfigChangeEvent(ConfigChangeEventType.UPDATED,
                PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY, "", "https://shop.example"));
        assertTrue(settings.reportRefusedRequestWithoutOrigin(false));
    }

    @Test
    void anUnlistedRefererDoesNotUseUpTheReportWhenMatchingIsOn() {
        PublicTrackerSettings matching = new PublicTrackerSettings("https://shop.example", null, true);
        // An unlisted website: nothing to fix, and the report stays available.
        assertFalse(matching.reportRefusedRequestWithoutOrigin(true));
        // A listed website whose Referrer-Policy strips the Referer: reported.
        assertTrue(matching.reportRefusedRequestWithoutOrigin(false));
        assertFalse(matching.reportRefusedRequestWithoutOrigin(false));
    }

    @Test
    void onlyWarningsTheConfigurationDidNotAlreadyHaveAreLogged() {
        // WebConfig publishes one setting at a time; a problem must be logged once, not once per setting.
        List<String> before = List.of("bad origin", "bad max age");
        assertEquals(List.of(), PublicTrackerSettings.newWarnings(before, before));
        assertEquals(List.of("bad referer"),
                PublicTrackerSettings.newWarnings(before, List.of("bad origin", "bad referer")));
        assertEquals(before, PublicTrackerSettings.newWarnings(List.of(), before));
    }

    @Test
    void unrecognisedHttpOnlyValueKeepsItOnAndSaysSo() {
        // WebConfig publishes the raw text, so a typo reaches this point instead of becoming false.
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.COOKIE_HTTP_ONLY_PROPERTY)).thenReturn("flase");
        settings.activate();
        assertTrue(settings.profileCookie("p1", false).endsWith("; HttpOnly"));
        assertTrue(hasWarning("Unrecognised value 'flase' for org.apache.unomi.profile.cookie.httpOnly"));
    }

    @Test
    void refusedSameSiteNoneIsReported() {
        lenient().when(configSharingService.getProperty(PublicTrackerSettings.SAME_SITE_PROPERTY)).thenReturn("None");
        settings.activate();
        assertTrue(hasWarning("Refusing SameSite=None"));

        lenient().when(configSharingService.getProperty(PublicTrackerSettings.ALLOWED_ORIGINS_PROPERTY)).thenReturn("https://shop.example");
        settings.activate();
        assertFalse(hasWarning("Refusing SameSite=None"));
    }

    private boolean hasWarning(String start) {
        return settings.configurationWarnings().stream().anyMatch(warning -> warning.contains(start));
    }
}
