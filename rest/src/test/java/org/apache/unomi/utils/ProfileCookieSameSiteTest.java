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
import org.apache.unomi.rest.cors.PublicTrackerCorsSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileCookieSameSiteTest {

    @Test
    void defaultCookieIsSameSiteLax() {
        String cookie = HttpUtils.getProfileCookieString(profile(), settings(null, null), false);
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.contains("SameSite=None"));
    }

    @Test
    void sameSiteNoneIsRefusedWithoutSecureOrOrigins() {
        String withoutSecure = HttpUtils.getProfileCookieString(profile(), settings("None", "https://shop.example"), false);
        assertTrue(withoutSecure.contains("SameSite=Lax"));

        String withoutOrigins = HttpUtils.getProfileCookieString(profile(), settings("None", ""), true);
        assertTrue(withoutOrigins.contains("SameSite=Lax"));
        assertTrue(withoutOrigins.contains("Secure"));
    }

    @Test
    void sameSiteNoneIsAcceptedWithSecureAndOrigins() {
        String cookie = HttpUtils.getProfileCookieString(profile(), settings("None", "https://shop.example"), true);
        assertTrue(cookie.contains("SameSite=None"));
        assertTrue(cookie.contains("Secure"));
    }

    @Test
    void sameSiteValueIsCaseInsensitiveAndUnknownValuesFallBackToLax() {
        assertTrue(HttpUtils.getProfileCookieString(profile(), settings(" none ", "https://shop.example"), true).contains("SameSite=None"));
        assertTrue(HttpUtils.getProfileCookieString(profile(), settings("strict", ""), false).contains("SameSite=Strict"));
        assertTrue(HttpUtils.getProfileCookieString(profile(), settings("bogus", ""), false).contains("SameSite=Lax"));
    }

    @Test
    void refusedSameSiteNoneStillWritesACookieOnEveryRequest() {
        // The warning is logged once; the fallback must apply every time.
        for (int i = 0; i < 3; i++) {
            assertTrue(HttpUtils.getProfileCookieString(profile(), settings("None", ""), true).contains("SameSite=Lax"));
        }
    }

    @Test
    void cookieAttributesArePrecomputedInTheShippedOrder() {
        assertEquals("context-profile-id=profile-1; Path=/; Max-Age=31536000; SameSite=None; Secure; HttpOnly",
                HttpUtils.getProfileCookieString(profile(), settings("None", "https://shop.example"), true));
        assertEquals("context-profile-id=profile-1; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly",
                HttpUtils.getProfileCookieString(profile(), settings("None", "https://shop.example"), false));
    }

    private static Profile profile() {
        Profile profile = new Profile();
        profile.setItemId("profile-1");
        return profile;
    }

    private static PublicTrackerCorsSettings settings(String sameSite, String origins) {
        return new PublicTrackerCorsSettings(origins, sameSite);
    }
}
