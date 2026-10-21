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
package org.apache.unomi.utils;

import org.apache.unomi.api.Profile;
import org.apache.unomi.rest.cors.PublicTrackerSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCookieSameSiteTest {

    @Test
    void defaultCookieIsSameSiteLax() {
        String cookie = cookie(settings(null, null), false);
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.contains("SameSite=None"));
    }

    @Test
    void sameSiteNoneIsRefusedWithoutSecureOrOrigins() {
        String withoutSecure = cookie(settings("None", "https://shop.example"), false);
        assertTrue(withoutSecure.contains("SameSite=Lax"));

        String withoutOrigins = cookie(settings("None", ""), true);
        assertTrue(withoutOrigins.contains("SameSite=Lax"));
        assertTrue(withoutOrigins.contains("Secure"));
    }

    @Test
    void sameSiteNoneIsAcceptedWithSecureAndOrigins() {
        String cookie = cookie(settings("None", "https://shop.example"), true);
        assertTrue(cookie.contains("SameSite=None"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void sameSiteValueIsCaseInsensitiveAndUnknownValuesFallBackToLax() {
        assertTrue(cookie(settings(" none ", "https://shop.example"), true).contains("SameSite=None"));
        assertTrue(cookie(settings("strict", ""), false).contains("SameSite=Strict"));
        assertTrue(cookie(settings("bogus", ""), false).contains("SameSite=Lax"));
    }

    @Test
    void refusedSameSiteNoneStillWritesACookieOnEveryRequest() {
        // The warning is logged once; the fallback must apply every time.
        for (int i = 0; i < 3; i++) {
            assertTrue(cookie(settings("None", ""), true).contains("SameSite=Lax"));
        }
    }

    @Test
    void cookieAttributesArePrecomputedInTheShippedOrder() {
        assertEquals("context-profile-id=profile-1; Path=/; Max-Age=31536000; SameSite=None; Secure; HttpOnly",
                cookie(settings("None", "https://shop.example"), true));
        assertEquals("context-profile-id=profile-1; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly",
                cookie(settings("None", "https://shop.example"), false));
    }

    private static String cookie(PublicTrackerSettings settings, boolean secure) {
        return settings.profileCookie("profile-1", secure);
    }

    private static PublicTrackerSettings settings(String sameSite, String origins) {
        return new PublicTrackerSettings(origins, sameSite);
    }
}
