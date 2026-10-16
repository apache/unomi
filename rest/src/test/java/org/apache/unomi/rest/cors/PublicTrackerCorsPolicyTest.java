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
        assertFalse(policy.allows("https://shop.example:443"));
        assertFalse(policy.allows("https://other.shop.example"));
        assertFalse(policy.allows("https://shop.example.evil"));
        assertFalse(policy.allows("null"));
        assertFalse(policy.allows("*"));
        assertFalse(policy.allows("https://shop.example/path"));
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
    }

    @Test
    void publicTrackerPaths() {
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("context.json"));
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("/eventcollector"));
        assertTrue(PublicTrackerCorsPolicy.isPublicTrackerPath("client/myapp/status"));
        assertFalse(PublicTrackerCorsPolicy.isPublicTrackerPath("profiles/test"));
    }
}
