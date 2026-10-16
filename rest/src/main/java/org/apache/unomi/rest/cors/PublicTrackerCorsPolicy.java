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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Operator allow-list of website origins that may send the visitor cookie to public tracker endpoints.
 * Comparison is exact (scheme, host and port). Wildcards and the {@code null} origin are never allowed.
 * An empty list is the secure default: no cross-site cookies.
 */
public final class PublicTrackerCorsPolicy {

    public static final String ALLOWED_ORIGINS_PROPERTY = "publicCorsAllowedOrigins";
    public static final String SAME_SITE_PROPERTY = "profileIdCookieSameSite";

    private static final Pattern ORIGIN = Pattern.compile("^https?://[A-Za-z0-9.-]+(:[0-9]{1,5})?$");

    private final Set<String> allowedOrigins;

    public PublicTrackerCorsPolicy(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public static PublicTrackerCorsPolicy parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return new PublicTrackerCorsPolicy(Collections.emptySet());
        }
        Set<String> origins = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String origin = part.trim();
            if (origin.isEmpty() || !isExactOrigin(origin)) {
                continue;
            }
            origins.add(origin);
        }
        return new PublicTrackerCorsPolicy(Collections.unmodifiableSet(origins));
    }

    /**
     * An origin is exact when it is {@code http} or {@code https}, a host, and an optional port,
     * with no path, query, user info, or wildcard.
     */
    public static boolean isExactOrigin(String origin) {
        return origin != null && ORIGIN.matcher(origin).matches();
    }

    public boolean isEmpty() {
        return allowedOrigins.isEmpty();
    }

    public boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    /**
     * {@code SameSite=None} is only valid together with {@code Secure} and a non-empty origin list.
     * Anything else falls back to {@code Lax}.
     */
    public String resolveSameSite(String requested, boolean secure) {
        if (requested == null || requested.isBlank()) {
            return "Lax";
        }
        String value = requested.trim();
        if ("None".equalsIgnoreCase(value)) {
            if (secure && !allowedOrigins.isEmpty()) {
                return "None";
            }
            return "Lax";
        }
        if ("Strict".equalsIgnoreCase(value)) {
            return "Strict";
        }
        return "Lax";
    }

    public static boolean isPublicTrackerPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.equals("context.json")
                || normalized.startsWith("context.json/")
                || normalized.equals("eventcollector")
                || normalized.startsWith("eventcollector/")
                || normalized.equals("client")
                || normalized.startsWith("client/");
    }
}
