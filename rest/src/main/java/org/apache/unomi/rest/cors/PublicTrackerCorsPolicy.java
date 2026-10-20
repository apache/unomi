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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Operator allow-list of website origins that may send the visitor cookie to public tracker endpoints.
 * Origins are compared on scheme, host and port, after lowercasing and dropping a default port, the way
 * browsers serialize the {@code Origin} header. Wildcards and the {@code null} origin are never allowed.
 * An empty list is the secure default: no cross-origin cookies. When enabled, a request that carries no
 * {@code Origin}, such as a {@code <script>} tag, is matched by the listed page in its {@code Referer}.
 */
public final class PublicTrackerCorsPolicy {

    private static final Pattern ORIGIN = Pattern.compile(
            "^(https?)://([a-z0-9.-]+|\\[[0-9a-f:.]+\\])(?::([0-9]{1,5}))?$");

    private static final String HTTPS = "https://";
    private static final String HTTP = "http://";

    private final Set<String> allowedOrigins;
    private final boolean matchReferer;

    private PublicTrackerCorsPolicy(Set<String> allowedOrigins, boolean matchReferer) {
        this.allowedOrigins = allowedOrigins;
        this.matchReferer = matchReferer;
    }

    /** For tests: Referer matching off, dropped entries not reported. */
    static PublicTrackerCorsPolicy parse(String configured) {
        return parse(configured, false, new ArrayList<>());
    }

    /**
     * Parses the comma-separated list. Call it when the setting changes, not per request
     * (see {@link PublicTrackerSettings}).
     *
     * @param matchReferer whether a request without {@code Origin} may be matched by its {@code Referer}
     * @param warnings     receives a message for each entry dropped
     */
    static PublicTrackerCorsPolicy parse(String configured, boolean matchReferer, List<String> warnings) {
        if (configured == null || configured.isBlank()) {
            return new PublicTrackerCorsPolicy(Collections.emptySet(), matchReferer);
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
                warnings.add("Ignoring public CORS allowed origin '" + entry + "': expected scheme://host[:port] "
                        + "with http or https, no path and no wildcard");
                continue;
            }
            origins.add(origin);
        }
        return new PublicTrackerCorsPolicy(Collections.unmodifiableSet(origins), matchReferer);
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
    boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    /**
     * Whether a request may be bound to, or given, the visitor cookie. That is the case for a listed origin,
     * for Unomi's own origin, and for a request that shows no sign of coming from another site's page.
     * Current browsers send {@code Origin} on every POST, same-origin ones included, so its presence alone
     * is not a reason to refuse. Runs on every tracker request: no parsing or regex, only string comparisons
     * (and a scan of the short list for the {@code Referer} match).
     *
     * @param origin        the {@code Origin} header, may be {@code null}
     * @param referer       the {@code Referer} header, may be {@code null}; matched against the list only when
     *                      that is enabled and the browser sends no {@code Origin}, as for {@code <script src>}
     * @param secFetchSite  the {@code Sec-Fetch-Site} header, may be {@code null} for older browsers
     * @param host          the host the request was addressed to, may be {@code null}
     * @param forwardedHost the {@code X-Forwarded-Host} header, may be {@code null}
     */
    public boolean allowsVisitorCookie(String origin, String referer, String secFetchSite, String host, String forwardedHost) {
        if (origin != null ? allowedOrigins.contains(origin) : matchReferer && isFromListedPage(referer)) {
            return true;
        }
        if (secFetchSite != null) {
            // Set by the browser, lowercase, and not writable by page script. It is also sent where Origin
            // is not, such as a <script> or <img> embedded by another site, so it is checked first.
            return "same-origin".equals(secFetchSite) || "none".equals(secFetchSite);
        }
        if (origin == null) {
            // No browser marker at all: a server-side caller, or a GET from an older browser that sends no
            // Sec-Fetch-Site, which cannot be told apart from it.
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

    boolean matchesReferer() {
        return matchReferer;
    }

    /**
     * Whether a {@code Referer} URL belongs to a listed origin. Browsers set it, lowercase the scheme and
     * host, drop default ports, and let a page shorten it but never name another origin. A missing one
     * (for example {@code Referrer-Policy: no-referrer}) is simply not a match. A document without an origin
     * of its own, such as a sandboxed {@code srcdoc} frame, reports the page that created it, which is why
     * this match is opt-in: it trusts everything a listed page frames.
     */
    private boolean isFromListedPage(String referer) {
        if (referer == null) {
            return false;
        }
        for (String allowed : allowedOrigins) {
            int end = allowed.length();
            if (referer.startsWith(allowed) && (referer.length() == end || referer.charAt(end) == '/')) {
                return true;
            }
        }
        return false;
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
    static boolean isPublicTrackerPath(String path) {
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
