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
        assertTrue(empty.allowsVisitorCookie(null, null, null, "cdp.example", null));
        // Browsers send Origin on same-origin POSTs.
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, "same-origin", "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, "none", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://www.cdp.example", null, "same-site", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://evil.example", null, "cross-site", "cdp.example", null));
        // <script> or <img> embedded by another site: no Origin, but the browser says cross-site.
        assertFalse(empty.allowsVisitorCookie(null, null, "cross-site", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie(null, null, "same-site", "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie(null, null, "same-origin", "cdp.example", null));
        // A server-side caller sends neither header.
        assertTrue(empty.allowsVisitorCookie(null, null, null, null, null));
    }

    @Test
    void withoutSecFetchSiteTheOriginHostIsComparedWithTheRequestHost() {
        PublicTrackerCorsPolicy empty = PublicTrackerCorsPolicy.parse("");
        // A TLS-terminating proxy changes scheme and port, so only the host is compared.
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, null, "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, null, "internal-host", "cdp.example:443"));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, null, "internal-host", " CDP.example, proxy.internal"));
        assertTrue(empty.allowsVisitorCookie("http://[::1]:8181", null, null, "[::1]", null));
        assertFalse(empty.allowsVisitorCookie("https://www.cdp.example", null, null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://cdp.example.evil", null, null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://cdp", null, null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("null", null, null, "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://", null, null, "", null));
        assertFalse(empty.allowsVisitorCookie("https://evil.example", null, null, null, null));
    }

    @Test
    void secFetchSiteWinsOverAMatchingHost() {
        PublicTrackerCorsPolicy empty = PublicTrackerCorsPolicy.parse("");
        // Same host but the browser says another site: the host fallback must not apply.
        assertFalse(empty.allowsVisitorCookie("https://cdp.example", null, "cross-site", "cdp.example", null));
        assertFalse(empty.allowsVisitorCookie("https://cdp.example", null, "same-site", "cdp.example", null));
        assertTrue(empty.allowsVisitorCookie("https://cdp.example", null, null, "cdp.example", null));
    }

    @Test
    void refererOfAListedPageCountsWhenThereIsNoOriginAndMatchingIsOn() {
        PublicTrackerCorsPolicy listed = PublicTrackerCorsPolicy.parse("https://shop.example, https://cart.example:8443, http://[::1]:8181", true);
        assertTrue(listed.allowsVisitorCookie(null, "https://shop.example/", "cross-site", "cdp.example", null));
        assertTrue(listed.allowsVisitorCookie(null, "https://shop.example", "cross-site", "cdp.example", null));
        assertTrue(listed.allowsVisitorCookie(null, "https://cart.example:8443/basket", "cross-site", "cdp.example", null));
        assertTrue(listed.allowsVisitorCookie(null, "http://[::1]:8181/p", "cross-site", "cdp.example", null));
        // The character after the listed origin must end it.
        assertFalse(listed.allowsVisitorCookie(null, "https://shop.example.evil/", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, "https://shop.example:8443/", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, "https://cart.example:84430/", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, "https://shop.example@evil.example/", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, "https://shop.example?x", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, "https://other.example/?https://shop.example/", "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie(null, null, "cross-site", "cdp.example", null));
        // A listed subdomain's <script> is same-site; the listed Referer still decides.
        assertTrue(listed.allowsVisitorCookie(null, "https://shop.example/", "same-site", "cdp.example", null));
        // Origin, when present, is what counts; Referer is not consulted.
        assertFalse(listed.allowsVisitorCookie("https://other.example", "https://shop.example/", "cross-site", "cdp.example", null));
    }

    @Test
    void refererIsIgnoredUnlessMatchingIsOn() {
        // A listed page's sandboxed frame reports that page as Referer, so the match is opt-in.
        PublicTrackerCorsPolicy listed = PublicTrackerCorsPolicy.parse("https://shop.example");
        assertFalse(listed.matchesReferer());
        assertFalse(listed.allowsVisitorCookie(null, "https://shop.example/", "cross-site", "cdp.example", null));
        // An unlisted Referer never refuses what the other rules allow.
        assertTrue(listed.allowsVisitorCookie(null, "https://evil.example/", "same-origin", "cdp.example", null));
        assertTrue(PublicTrackerCorsPolicy.parse("https://shop.example", true)
                .allowsVisitorCookie(null, "https://evil.example/", "none", "cdp.example", null));
    }

    @Test
    void listedOriginUsesTheVisitorCookieFromAnotherSite() {
        PublicTrackerCorsPolicy listed = PublicTrackerCorsPolicy.parse("https://shop.example");
        assertTrue(listed.allowsVisitorCookie("https://shop.example", null, "cross-site", "cdp.example", null));
        assertFalse(listed.allowsVisitorCookie("https://other.example", null, "cross-site", "cdp.example", null));
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
