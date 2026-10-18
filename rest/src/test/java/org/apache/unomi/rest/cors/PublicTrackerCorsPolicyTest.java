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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicTrackerCorsPolicyTest {

    @Test
    void defaultSettingsAllowNoOrigin() {
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(null);
        assertTrue(policy.isEmpty());
        assertFalse(policy.allows("https://shop.example"));
        assertFalse(policy.allows(null));
    }

    @Test
    void listedOriginIsExact() {
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(" https://shop.example , https://shop.example:8443 ");
        assertTrue(policy.allows("https://shop.example"));
        assertTrue(policy.allows("https://shop.example:8443"));
        assertFalse(policy.allows("http://shop.example"));
        assertFalse(policy.allows("https://shop.example:444"));
        assertFalse(policy.allows("https://other.shop.example"));
        assertFalse(policy.allows("https://shop.example.evil"));
        assertFalse(policy.allows("null"));
        assertFalse(policy.allows("*"));
        assertFalse(policy.allows("https://shop.example/path"));
    }

    @Test
    void listedOriginsAreComparedAsBrowsersSendThem() {
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(
                "https://WWW.Shop.Example, https://cart.example:443, http://legacy.example:80, https://slash.example/, http://[::1]:8080");
        assertTrue(policy.allows("https://www.shop.example"));
        assertTrue(policy.allows("https://cart.example"));
        assertTrue(policy.allows("http://legacy.example"));
        assertTrue(policy.allows("https://slash.example"));
        assertTrue(policy.allows("http://[::1]:8080"));
        assertFalse(policy.allows("https://legacy.example"));
        // Browsers send the serialized form; a non-browser variant is not looked up.
        assertFalse(policy.allows("https://WWW.shop.example"));
        assertFalse(policy.allows("https://cart.example:443"));
    }

    @Test
    void invalidEntriesAreDropped() {
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(
                "https://shop.example/path, ftp://shop.example, *.example, null, https://*.example,, https://ok.example");
        assertTrue(policy.allows("https://ok.example"));
        assertFalse(policy.allows("https://shop.example"));
        assertFalse(policy.allows("null"));
    }

    @Test
    void visitorCookieIsUsedWithoutOriginOrFromUnomisOwnOrigin() {
        PublicTrackerCorsPolicy empty = PublicTrackerCorsPolicy.parse("");
        assertTrue(empty.allowsVisitorCookie(null, null, "cdp.example", null));
        // Browsers send Origin on same-origin POSTs.
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", "same-origin", "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", "none", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://www.cdp.example", "same-site", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://evil.example", "cross-site", "cdp.example", null));
    }

    @Test
    void withoutSecFetchSiteTheOriginHostIsComparedWithTheRequestHost() {
        PublicTrackerCorsPolicy empty = PublicTrackerCorsPolicy.parse("");
        // A TLS-terminating proxy changes scheme and port, so only the host is compared.
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, "internal-host", "cdp.example:443"));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, "internal-host", " CDP.example, proxy.internal"));
        assertTrue(empty.allowsVisitorCookie("http://[::1]:8181", null, "[::1]", null));
        assertFalse(empty.allowsVisitorCookie("https://www.cdp.example", null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://cdp.example.evil", null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://cdp", null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("null", null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://", null, "", null));
        assertFalse(empty.allowsVisitorCookie("https://evil.example", null, null, null));
    }

    @Test
    void listedOriginUsesTheVisitorCookieFromAnotherSite() {
        PublicTrackerCorsPolicy listed = PublicTrackerCorsPolicy.parse("https://shop.example");
        assertTrue(listed.allowsVisitorCookie("https://shop.example", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie("https://other.example", "cross-site", "cdp.example", null));
    }

    @Test
    void wildcardIsNeverAnAllowedOrigin() {
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse("*");
        assertTrue(policy.isEmpty());
        assertFalse(policy.allows("*"));
    }

    @Test
    void sameSiteNoneRequiresSecureAndOrigins() {
        PublicTrackerCorsPolicy empty = PublicTrackerCorsPolicy.parse("");
        assertEquals("Lax", empty.resolveSameSite(null, false));
        assertEquals("Lax", empty.resolveSameSite("None", true));
        assertEquals("Lax", empty.resolveSameSite("Lax", false));

        PublicTrackerCorsPolicy listed = PublicTrackerCorsPolicy.parse("https://shop.example");
        assertEquals("Lax", listed.resolveSameSite("None", false));
        assertEquals("None", listed.resolveSameSite("None", true));
        assertEquals("Strict", listed.resolveSameSite("Strict", false));
        assertEquals("None", listed.resolveSameSite(" none ", true));
        assertEquals("Lax", listed.resolveSameSite("bogus", true));
    }

    @Test
    void publicTrackerPaths() {
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("context.json"));
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("context.js"));
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("/eventcollector"));
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("client/myapp/status"));
        assertFalse(PublicTrackerCorsPolicy.isPublicTrackerPath("profiles/test"));
        assertFalse(PublicTrackerCorsPolicy.isPublicTrackerPath("context.jsonx"));
        assertFalse(PublicTrackerCorsPolicy.isPublicTrackerPath("clients"));
        assertFalse(PublicTrackerCorsPolicy.isPublicTrackerPath(null));
    }
}
