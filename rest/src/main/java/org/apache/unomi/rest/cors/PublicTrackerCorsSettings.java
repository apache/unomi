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
import org.apache.unomi.api.services.ConfigSharingService.ConfigChangeListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The public tracker origin list and profile cookie settings, parsed and resolved when they are set
 * rather than on every request. The web configuration publishes them through {@link ConfigSharingService};
 * this component reads them at activation and again whenever one of them changes. Requests only read
 * the results.
 */
@Component(service = {PublicTrackerCorsSettings.class, ConfigChangeListener.class})
public class PublicTrackerCorsSettings implements ConfigChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicTrackerCorsSettings.class);

    public static final String COOKIE_NAME_PROPERTY = "profileIdCookieName";
    public static final String COOKIE_DOMAIN_PROPERTY = "profileIdCookieDomain";
    public static final String COOKIE_MAX_AGE_PROPERTY = "profileIdCookieMaxAgeInSeconds";
    public static final String COOKIE_HTTP_ONLY_PROPERTY = "profileIdCookieHttpOnly";

    static final String DEFAULT_COOKIE_NAME = "context-profile-id";
    static final int DEFAULT_COOKIE_MAX_AGE = 31536000;

    private static final Set<String> WATCHED = Set.of(
            PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY, PublicTrackerCorsPolicy.SAME_SITE_PROPERTY,
            COOKIE_NAME_PROPERTY, COOKIE_DOMAIN_PROPERTY, COOKIE_MAX_AGE_PROPERTY, COOKIE_HTTP_ONLY_PROPERTY);

    /** Everything a request needs, replaced as a whole so a request never sees half of a change. */
    private static final class Resolved {
        final PublicTrackerCorsPolicy policy;
        final String requestedSameSite;
        final String sameSiteWhenSecure;
        final String sameSiteWhenInsecure;
        final String cookieName;
        /** {@code name=}, then the attributes after the value, for secure and plain HTTP requests. */
        final String cookiePrefix;
        final String cookieAttributesWhenSecure;
        final String cookieAttributesWhenInsecure;
        /** Set while a refusal of SameSite=None, only because a request was not over HTTPS, is unreported. */
        final AtomicBoolean insecureRefusalToReport;

        Resolved(PublicTrackerCorsPolicy policy, String requestedSameSite, String cookieName, String cookieDomain,
                 int cookieMaxAge, boolean cookieHttpOnly) {
            this.policy = policy;
            this.requestedSameSite = requestedSameSite;
            this.sameSiteWhenSecure = policy.resolveSameSite(requestedSameSite, true);
            this.sameSiteWhenInsecure = policy.resolveSameSite(requestedSameSite, false);
            this.cookieName = cookieName;
            this.cookiePrefix = cookieName + "=";
            String common = "; Path=/; Max-Age=" + cookieMaxAge
                    + (cookieDomain == null || cookieDomain.isBlank() ? "" : "; Domain=" + cookieDomain.trim());
            String httpOnly = cookieHttpOnly ? "; HttpOnly" : "";
            this.cookieAttributesWhenSecure = common + "; SameSite=" + sameSiteWhenSecure + "; Secure" + httpOnly;
            this.cookieAttributesWhenInsecure = common + "; SameSite=" + sameSiteWhenInsecure + httpOnly;
            this.insecureRefusalToReport = new AtomicBoolean("None".equals(sameSiteWhenSecure) && !"None".equals(sameSiteWhenInsecure));
        }
    }

    private volatile Resolved resolved = resolve(null, null, null, null, null, null);

    @Reference
    private ConfigSharingService configSharingService;

    public PublicTrackerCorsSettings() {
    }

    /** For tests and callers outside OSGi; cookie name, domain, max age and HttpOnly take their defaults. */
    public PublicTrackerCorsSettings(String allowedOrigins, String sameSite) {
        this.resolved = resolve(allowedOrigins, sameSite, null, null, null, null);
    }

    @Activate
    void activate() {
        reload();
    }

    @Override
    public void configChanged(ConfigChangeEvent event) {
        if (WATCHED.contains(event.getName())) {
            reload();
        }
    }

    private void reload() {
        this.resolved = resolve(
                configSharingService.getProperty(PublicTrackerCorsPolicy.ALLOWED_ORIGINS_PROPERTY),
                configSharingService.getProperty(PublicTrackerCorsPolicy.SAME_SITE_PROPERTY),
                configSharingService.getProperty(COOKIE_NAME_PROPERTY),
                configSharingService.getProperty(COOKIE_DOMAIN_PROPERTY),
                configSharingService.getProperty(COOKIE_MAX_AGE_PROPERTY),
                configSharingService.getProperty(COOKIE_HTTP_ONLY_PROPERTY));
    }

    private static Resolved resolve(Object allowedOrigins, Object sameSite, Object cookieName, Object cookieDomain,
                                    Object cookieMaxAge, Object cookieHttpOnly) {
        String requested = sameSite == null || sameSite.toString().isBlank() ? "Lax" : sameSite.toString().trim();
        String name = cookieName == null || cookieName.toString().isBlank() ? DEFAULT_COOKIE_NAME : cookieName.toString().trim();
        Resolved next = new Resolved(
                PublicTrackerCorsPolicy.parse(allowedOrigins == null ? null : allowedOrigins.toString()),
                requested,
                name,
                cookieDomain == null ? null : cookieDomain.toString(),
                toInt(cookieMaxAge),
                // HttpOnly unless explicitly turned off: page script must not read the bearer cookie.
                cookieHttpOnly == null || Boolean.parseBoolean(cookieHttpOnly.toString().trim()));
        if ("None".equalsIgnoreCase(requested) && !"None".equals(next.sameSiteWhenSecure)) {
            LOGGER.warn("Refusing SameSite=None for the profile cookie because the public CORS allowed origins list "
                    + "is empty; using SameSite=Lax.");
        }
        return next;
    }

    private static int toInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid profile cookie max age '{}'; using {}", value, DEFAULT_COOKIE_MAX_AGE);
            }
        }
        return DEFAULT_COOKIE_MAX_AGE;
    }

    public PublicTrackerCorsPolicy policy() {
        return resolved.policy;
    }

    public String cookieName() {
        return resolved.cookieName;
    }

    /** The configured {@code SameSite} value, before it is checked. */
    public String requestedSameSite() {
        return resolved.requestedSameSite;
    }

    /**
     * The {@code SameSite} value for a profile cookie, resolved in advance for secure and insecure requests.
     * {@code None} needs both {@code Secure} and a non-empty origin list; anything else is {@code Lax}
     * unless {@code Strict} was asked for.
     */
    public String cookieSameSite(boolean secure) {
        Resolved current = resolved;
        if (!secure) {
            reportInsecureRefusal(current);
        }
        return secure ? current.sameSiteWhenSecure : current.sameSiteWhenInsecure;
    }

    /** The {@code Set-Cookie} value naming this profile; only the profile id is added per request. */
    public String profileCookie(String profileId, boolean secure) {
        Resolved current = resolved;
        if (!secure) {
            reportInsecureRefusal(current);
        }
        return current.cookiePrefix + profileId
                + (secure ? current.cookieAttributesWhenSecure : current.cookieAttributesWhenInsecure);
    }

    private static void reportInsecureRefusal(Resolved current) {
        if (current.insecureRefusalToReport.get() && current.insecureRefusalToReport.compareAndSet(true, false)) {
            LOGGER.warn("Refusing SameSite=None for the profile cookie because the request did not arrive over HTTPS; "
                    + "using SameSite=Lax. Behind a TLS-terminating proxy, list it in org.apache.unomi.ip.trustedProxies "
                    + "and have it send X-Forwarded-Proto: https. This is logged once.");
        }
    }
}
