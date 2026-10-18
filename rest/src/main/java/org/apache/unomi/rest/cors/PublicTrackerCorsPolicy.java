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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operator allow-list of website origins that may send the visitor cookie to public tracker endpoints.
 * Origins are compared on scheme, host and port, after lowercasing and dropping a default port, the way
 * browsers serialize the {@code Origin} header. Wildcards and the {@code null} origin are never allowed.
 * An empty list is the secure default: no cross-origin cookies.
 */
public final class PublicTrackerCorsPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicTrackerCorsPolicy.class);

    public static final String ALLOWED_ORIGINS_PROPERTY = "publicCorsAllowedOrigins";
    public static final String SAME_SITE_PROPERTY = "profileIdCookieSameSite";

    private static final Pattern ORIGIN = Pattern.compile(
            "^(https?)://([a-z0-9.-]+|\\[[0-9a-f:.]+\\])(?::([0-9]{1,5}))?$");

    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";

    private final Set<String> allowedOrigins;

    private PublicTrackerCorsPolicy(Set<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Parses the comma-separated list, logging each entry it drops. Call it when the setting changes,
     * not per request (see {@link PublicTrackerSettings}).
     */
    public static PublicTrackerCorsPolicy parse(String configured) {
        if (configured == null || configured.isBlank()) {
            return new PublicTrackerCorsPolicy(Collections.emptySet());
        }
        Set<String> origins = new LinkedHashSet<>();
        for (String part : configured.split(",")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            // An origin has no path, but a trailing slash is an easy mistake to make in the list.
            String origin = normalize(entry.endsWith("/") ? entry.substring(0, entry.length() - 1) : entry);
            if (origin == null) {
                LOGGER.warn("Ignoring public CORS allowed origin '{}': expected scheme://host[:port] with http or https, "
                        + "no path and no wildcard", entry);
                continue;
            }
            origins.add(origin);
        }
        return new PublicTrackerCorsPolicy(Collections.unmodifiableSet(origins));
    }

    /**
     * Returns the origin as a browser serializes it (lowercase, no default port), or {@code null} when it is
     * not an {@code http} or {@code https} origin with a host and an optional port only.
     */
    static String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        Matcher matcher = ORIGIN.matcher(origin.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        String scheme = matcher.group(1);
        String host = matcher.group(2);
        String port = matcher.group(3);
        boolean defaultPort = port != null
                && (("http".equals(scheme) && "80".equals(port)) || ("https".equals(scheme) && "443".equals(port)));
        return scheme + "://" + host + (port == null || defaultPort ? "" : ":" + port);
    }

    boolean isEmpty() {
        return allowedOrigins.isEmpty();
    }

    /**
     * Exact lookup: the list is stored the way browsers serialize {@code Origin} (lowercase, no default
     * port), so a browser's header matches as is. Anything else is not a browser origin and is refused.
     */
    public boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    /**
     * Whether a request may be bound to, or given, the visitor cookie. That is the case for a listed origin,
     * for Unomi's own origin, and for a request that shows no sign of coming from another site's page.
     * Browsers send {@code Origin} on every POST, same-origin ones included, so its presence alone is not
     * a reason to refuse. Runs on every tracker request, so it only compares strings and allocates nothing.
     *
     * @param origin        the {@code Origin} header, may be {@code null}
     * @param secFetchSite  the {@code Sec-Fetch-Site} header, may be {@code null} for older browsers
     * @param host          the host the request was addressed to, may be {@code null}
     * @param forwardedHost the {@code X-Forwarded-Host} header, may be {@code null}
     */
    public boolean allowsVisitorCookie(String origin, String secFetchSite, String host, String forwardedHost) {
        if (origin != null && allowedOrigins.contains(origin)) {
            return true;
        }
        if (secFetchSite != null) {
            // Set by the browser, lowercase, and not writable by page script. It is also sent where Origin
            // is not, such as a <script> or <img> embedded by another site, so it is checked first.
            return "same-origin".equals(secFetchSite) || "none".equals(secFetchSite);
        }
        if (origin == null) {
            // No browser marker at all: a server-side caller or an older browser's same-site GET.
            return true;
        }
        // Older browsers: the same host counts as the page being served by Unomi itself. The scheme and
        // port are not compared, since a TLS-terminating proxy changes both on the way in.
        int hostStart;
        if (origin.startsWith(HTTPS)) {
            hostStart = HTTPS.length();
        } else if (origin.startsWith(HTTP)) {
            hostStart = HTTP.length();
        } else {
            return false;
        }
        int hostEnd = hostEnd(origin, hostStart);
        return sameHost(origin, hostStart, hostEnd, host) || sameHost(origin, hostStart, hostEnd, forwardedHost);
    }

    /** End of the host in {@code value} starting at {@code start}: before the port, a comma or the end. */
    private static int hostEnd(String value, int start) {
        int end = value.length();
        int search = start;
        if (start < end && value.charAt(start) == '[') {
            int bracket = value.indexOf(']', start);
            if (bracket < 0) {
                return end;
            }
            search = bracket;
        }
        for (int i = search; i < end; i++) {
            char c = value.charAt(i);
            if (c == ':' || c == ',' || c == '/') {
                return i;
            }
        }
        return end;
    }

    private static boolean sameHost(String origin, int originStart, int originEnd, String hostHeader) {
        if (hostHeader == null) {
            return false;
        }
        int start = 0;
        while (start < hostHeader.length() && hostHeader.charAt(start) == ' ') {
            start++;
        }
        int length = originEnd - originStart;
        return length > 0
                && hostEnd(hostHeader, start) - start == length
                && hostHeader.regionMatches(true, start, origin, originStart, length);
    }

    /**
     * {@code SameSite=None} is only valid together with {@code Secure} and a non-empty origin list.
     * Anything else falls back to {@code Lax}.
     */
    String resolveSameSite(String requested, boolean secure) {
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

    /** The public tracker endpoints, relative to the REST base address. Runs on every response. */
    public static boolean isPublicTrackerPath(String path) {
        if (path == null) {
            return false;
        }
        int start = path.startsWith("/") ? 1 : 0;
        return isSegment(path, start, "context.js")
                || isSegment(path, start, "context.json")
                || isSegment(path, start, "eventcollector")
                || isSegment(path, start, "client");
    }

    private static boolean isSegment(String path, int start, String segment) {
        int end = start + segment.length();
        return path.startsWith(segment, start) && (path.length() == end || path.charAt(end) == '/');
    }
}
