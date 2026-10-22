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
import org.apache.unomi.api.services.TenantLifecycleListener;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * The public tracker origin lists and profile cookie settings, parsed and resolved when they are set
 * rather than on every request. The web configuration publishes the operator's settings through
 * {@link ConfigSharingService}; this component reads them at activation and again whenever one of them
 * changes. Each tenant may add its own website origins in its {@value #TENANT_PROPERTY_ALLOWED_ORIGINS}
 * property; those are kept in a {@link TenantOriginsSnapshot}. Requests only read the results.
 */
@Component(service = {PublicTrackerSettings.class, ConfigChangeListener.class, TenantLifecycleListener.class})
public class PublicTrackerSettings implements ConfigChangeListener, TenantLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicTrackerSettings.class);

    // Operator settings, published by WebConfig through ConfigSharingService.
    static final String ALLOWED_ORIGINS_PROPERTY = "publicCorsAllowedOrigins";
    static final String SAME_SITE_PROPERTY = "profileIdCookieSameSite";
    static final String MATCH_REFERER_PROPERTY = "publicCorsMatchReferer";
    /** Whether tenants may list their own origins (org.apache.unomi.public.cors.tenantOrigins). */
    static final String TENANT_ORIGINS_ENABLED_PROPERTY = "publicCorsTenantOrigins";
    static final String COOKIE_NAME_PROPERTY = "profileIdCookieName";
    static final String COOKIE_DOMAIN_PROPERTY = "profileIdCookieDomain";
    static final String COOKIE_MAX_AGE_PROPERTY = "profileIdCookieMaxAgeInSeconds";
    static final String COOKIE_HTTP_ONLY_PROPERTY = "profileIdCookieHttpOnly";

    /**
     * The tenant property, set through the tenant API, that lists the tenant's own website origins. Not to be
     * confused with {@link #ALLOWED_ORIGINS_PROPERTY}, the operator's list, which has the same name.
     */
    public static final String TENANT_PROPERTY_ALLOWED_ORIGINS = "publicCorsAllowedOrigins";

    static final String DEFAULT_COOKIE_NAME = "context-profile-id";
    static final int DEFAULT_COOKIE_MAX_AGE = 31536000;

    private static final List<String> WATCHED = List.of(
            ALLOWED_ORIGINS_PROPERTY, SAME_SITE_PROPERTY, MATCH_REFERER_PROPERTY, TENANT_ORIGINS_ENABLED_PROPERTY,
            COOKIE_NAME_PROPERTY, COOKIE_DOMAIN_PROPERTY, COOKIE_MAX_AGE_PROPERTY, COOKIE_HTTP_ONLY_PROPERTY);

    /** Everything a request needs, replaced as a whole so a request never sees half of a change. */
    private static final class Resolved {
        final PublicTrackerCorsPolicy policy;
        final boolean tenantOriginsEnabled;
        final String sameSiteWhenSecure;
        final String cookieName;
        /** {@code name=}, then the attributes after the value, for secure and plain HTTP requests. */
        final String cookiePrefix;
        final String cookieAttributesWhenSecure;
        final String cookieAttributesWhenInsecure;
        /** Set while a refusal of SameSite=None, only because a request was not over HTTPS, is unreported for this configuration. */
        final AtomicBoolean insecureRefusalToReport;
        /** Set until a refused request without Origin, typically a script tag, is reported for this configuration. */
        final AtomicBoolean refusedEmbedToReport;
        /** Problems found in this configuration, logged when it is applied. */
        final List<String> warnings;

        Resolved(PublicTrackerCorsPolicy policy, boolean tenantOriginsEnabled, String requestedSameSite, String cookieName,
                 String cookieDomain, int cookieMaxAge, boolean cookieHttpOnly, List<String> warnings) {
            this.policy = policy;
            this.tenantOriginsEnabled = tenantOriginsEnabled;
            this.warnings = List.copyOf(warnings);
            boolean originsListed = !policy.isEmpty() || tenantOriginsEnabled;
            this.sameSiteWhenSecure = policy.resolveSameSite(requestedSameSite, true, originsListed);
            String sameSiteWhenInsecure = policy.resolveSameSite(requestedSameSite, false, originsListed);
            this.cookieName = cookieName;
            this.cookiePrefix = cookieName + "=";
            String common = "; Path=/; Max-Age=" + cookieMaxAge
                    + (cookieDomain == null || cookieDomain.isBlank() ? "" : "; Domain=" + cookieDomain.trim());
            String httpOnly = cookieHttpOnly ? "; HttpOnly" : "";
            this.cookieAttributesWhenSecure = common + "; SameSite=" + sameSiteWhenSecure + "; Secure" + httpOnly;
            this.cookieAttributesWhenInsecure = common + "; SameSite=" + sameSiteWhenInsecure + httpOnly;
            this.insecureRefusalToReport = new AtomicBoolean("None".equals(sameSiteWhenSecure) && !"None".equals(sameSiteWhenInsecure));
            // Only worth reporting once websites can be listed.
            this.refusedEmbedToReport = new AtomicBoolean(originsListed);
        }
    }

    /** The operator's policy merged with each tenant's list, and every origin any tenant lists (for preflights). */
    private static final class Merged {
        final Resolved base;
        final TenantOriginsSnapshot.State origins;
        final Map<String, PublicTrackerCorsPolicy> byTenant;
        final Set<String> anyTenant;

        Merged(Resolved base, TenantOriginsSnapshot.State origins) {
            this.base = base;
            this.origins = origins;
            Map<String, PublicTrackerCorsPolicy> policies = new HashMap<>();
            Set<String> all = new HashSet<>();
            for (Map.Entry<String, Set<String>> entry : origins.lists.entrySet()) {
                policies.put(entry.getKey(), base.policy.withOrigins(entry.getValue()));
                all.addAll(entry.getValue());
            }
            this.byTenant = Collections.unmodifiableMap(policies);
            this.anyTenant = Collections.unmodifiableSet(all);
        }
    }

    private volatile Resolved resolved = resolve(Collections.emptyMap());
    /** {@code null} until activation, and in tests that do not set one: no tenant origins then. */
    private volatile TenantOriginsSnapshot tenantOrigins;
    private volatile Merged merged;
    /** Replaced in tests. */
    LongSupplier clock = System::nanoTime;

    @Reference
    private ConfigSharingService configSharingService;

    @Reference
    private TenantService tenantService;

    /** Used by OSGi Declarative Services; the settings are read at activation. */
    public PublicTrackerSettings() {
    }

    /** For tests; tenant origins off, cookie name, domain, max age and HttpOnly take their defaults. */
    public PublicTrackerSettings(String allowedOrigins, String sameSite) {
        this(allowedOrigins, sameSite, false);
    }

    /** For tests; tenant origins off, cookie name, domain, max age and HttpOnly take their defaults. */
    public PublicTrackerSettings(String allowedOrigins, String sameSite, boolean matchReferer) {
        Map<String, Object> raw = new HashMap<>();
        raw.put(ALLOWED_ORIGINS_PROPERTY, allowedOrigins);
        raw.put(SAME_SITE_PROPERTY, sameSite);
        raw.put(MATCH_REFERER_PROPERTY, matchReferer);
        raw.put(TENANT_ORIGINS_ENABLED_PROPERTY, "false");
        this.resolved = resolve(raw);
    }

    @Activate
    void activate() {
        reload();
        TenantOriginsSnapshot snapshot = new TenantOriginsSnapshot(tenantService::getAllTenants, clock);
        snapshot.load();
        this.tenantOrigins = snapshot;
    }

    /** For tests: the tenant origins to use. */
    void useTenantOrigins(TenantOriginsSnapshot snapshot) {
        this.tenantOrigins = snapshot;
    }

    @Override
    public void configChanged(ConfigChangeEvent event) {
        if (WATCHED.contains(event.getName())) {
            reload();
        }
    }

    // Synchronized so that two reloads (activation and a configuration event) cannot interleave.
    private synchronized void reload() {
        Resolved previous = resolved;
        Map<String, Object> raw = new HashMap<>();
        for (String property : WATCHED) {
            raw.put(property, configSharingService.getProperty(property));
        }
        Resolved next = resolve(raw);
        for (String warning : newWarnings(previous.warnings, next.warnings)) {
            LOGGER.warn(warning);
        }
        this.resolved = next;
    }

    /**
     * The warnings of a new configuration that the previous one did not already have. WebConfig publishes the
     * settings one by one, each causing a reload, so a problem would otherwise be logged once per setting.
     */
    static List<String> newWarnings(List<String> previous, List<String> next) {
        List<String> added = new ArrayList<>();
        for (String warning : next) {
            if (!previous.contains(warning)) {
                added.add(warning);
            }
        }
        return added;
    }

    /** The problems found in the current configuration, as they were logged. */
    List<String> configurationWarnings() {
        return resolved.warnings;
    }

    /** Resolves the raw settings, keyed by their {@code *_PROPERTY} names; a missing key takes the default. */
    private static Resolved resolve(Map<String, Object> raw) {
        List<String> warnings = new ArrayList<>();
        Object sameSite = raw.get(SAME_SITE_PROPERTY);
        Object cookieName = raw.get(COOKIE_NAME_PROPERTY);
        Object cookieDomain = raw.get(COOKIE_DOMAIN_PROPERTY);
        Object cookieHttpOnly = raw.get(COOKIE_HTTP_ONLY_PROPERTY);
        String requested = sameSite == null ? null : sameSite.toString().trim();
        String name = cookieName == null || cookieName.toString().isBlank() ? DEFAULT_COOKIE_NAME : cookieName.toString().trim();
        String origins = raw.get(ALLOWED_ORIGINS_PROPERTY) == null ? null : raw.get(ALLOWED_ORIGINS_PROPERTY).toString();
        // Off unless explicitly turned on: it trusts everything a listed page frames.
        String refererSetting = text(raw.get(MATCH_REFERER_PROPERTY));
        boolean refererMatch = "true".equalsIgnoreCase(refererSetting);
        if (!refererSetting.isEmpty() && !refererMatch && !"false".equalsIgnoreCase(refererSetting)) {
            warnings.add("Unrecognised value '" + refererSetting + "' for org.apache.unomi.public.cors.matchReferer; "
                    + "only true turns it on, so Referer matching is off.");
        }
        // On unless explicitly turned off: an empty tenant list adds nothing.
        String tenantSetting = text(raw.get(TENANT_ORIGINS_ENABLED_PROPERTY));
        boolean tenantOriginsEnabled = !"false".equalsIgnoreCase(tenantSetting);
        if (!tenantSetting.isEmpty() && tenantOriginsEnabled && !"true".equalsIgnoreCase(tenantSetting)) {
            warnings.add("Unrecognised value '" + tenantSetting + "' for org.apache.unomi.public.cors.tenantOrigins; "
                    + "only false turns it off, so tenants may list their own origins.");
        }
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(origins, refererMatch, warnings);
        if (origins != null && !origins.isBlank() && policy.isEmpty()) {
            warnings.add("None of the public CORS allowed origins '" + origins + "' is valid, so no other website may "
                    + "use the visitor cookie. Expected scheme://host[:port], comma-separated.");
        }
        if (refererMatch && policy.isEmpty() && !tenantOriginsEnabled) {
            warnings.add("org.apache.unomi.public.cors.matchReferer is on but no website origin is listed, so it has no effect.");
        }
        if ("None".equalsIgnoreCase(requested)
                && !"None".equals(policy.resolveSameSite(requested, true, !policy.isEmpty() || tenantOriginsEnabled))) {
            warnings.add("Refusing SameSite=None for the profile cookie because the public CORS allowed origins list "
                    + "has no valid entry and tenant lists are off; using SameSite=Lax.");
        }
        int maxAge = toInt(raw.get(COOKIE_MAX_AGE_PROPERTY), warnings);
        String httpOnlySetting = text(cookieHttpOnly);
        if (!httpOnlySetting.isEmpty() && !"true".equalsIgnoreCase(httpOnlySetting) && !"false".equalsIgnoreCase(httpOnlySetting)) {
            warnings.add("Unrecognised value '" + httpOnlySetting + "' for org.apache.unomi.profile.cookie.httpOnly; "
                    + "only false turns it off, so the cookie stays HttpOnly.");
        }
        return new Resolved(
                policy,
                tenantOriginsEnabled,
                requested,
                name,
                cookieDomain == null ? null : cookieDomain.toString(),
                maxAge,
                // HttpOnly unless explicitly turned off: page script must not read the bearer cookie,
                // so a typo keeps it on.
                !"false".equalsIgnoreCase(httpOnlySetting),
                warnings);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static int toInt(Object value, List<String> warnings) {
        if (value == null) {
            return DEFAULT_COOKIE_MAX_AGE;
        }
        try {
            int maxAge = value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString().trim());
            if (maxAge > 0) {
                return maxAge;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        // Zero or less would make browsers delete the cookie on every response.
        warnings.add("Invalid profile cookie max age '" + value + "'; using " + DEFAULT_COOKIE_MAX_AGE);
        return DEFAULT_COOKIE_MAX_AGE;
    }

    /** @return the operator's website origins that may use the visitor cookie, as currently configured */
    public PublicTrackerCorsPolicy policy() {
        return resolved.policy;
    }

    /**
     * The website origins that may use the visitor cookie for a tenant: the operator's, plus the tenant's own
     * when tenant lists are on.
     *
     * @param tenantId the tenant the request authenticated as, may be {@code null}
     * @return the policy to apply to that tenant's request
     */
    public PublicTrackerCorsPolicy policyForTenant(String tenantId) {
        Resolved base = resolved;
        TenantOriginsSnapshot snapshot = tenantOrigins;
        if (!base.tenantOriginsEnabled || tenantId == null || snapshot == null) {
            return base.policy;
        }
        Merged current = merged(base, snapshot.current());
        PublicTrackerCorsPolicy policy = current.byTenant.get(tenantId);
        if (policy == null && !current.origins.tenantIds.contains(tenantId)) {
            // A tenant created since the last load, possibly on another node.
            policy = merged(base, snapshot.afterMiss()).byTenant.get(tenantId);
        }
        return policy != null ? policy : base.policy;
    }

    /**
     * Whether a preflight from this origin may be answered with credentials. A preflight carries no API key,
     * so the tenant is not known yet: an origin the operator or any tenant lists is accepted, and the actual
     * request is then checked against its own tenant.
     *
     * @param origin the {@code Origin} header of the preflight, may be {@code null}
     * @return whether the preflight may allow credentials for that origin
     */
    public boolean allowsPreflight(String origin) {
        Resolved base = resolved;
        if (origin == null) {
            return false;
        }
        if (base.policy.allows(origin)) {
            return true;
        }
        TenantOriginsSnapshot snapshot = tenantOrigins;
        if (!base.tenantOriginsEnabled || snapshot == null) {
            return false;
        }
        if (merged(base, snapshot.current()).anyTenant.contains(origin)) {
            return true;
        }
        // Perhaps listed since the last load, possibly on another node.
        return merged(base, snapshot.afterMiss()).anyTenant.contains(origin);
    }

    /**
     * Applies a tenant created or updated through this node at once, from the saved object.
     *
     * @param tenant the tenant as saved
     */
    public void tenantSaved(Tenant tenant) {
        TenantOriginsSnapshot snapshot = tenantOrigins;
        if (snapshot != null) {
            snapshot.tenantSaved(tenant);
        }
    }

    /**
     * Drops a removed tenant's origins at once.
     *
     * @param tenantId the removed tenant
     */
    public void tenantRemoved(String tenantId) {
        TenantOriginsSnapshot snapshot = tenantOrigins;
        if (snapshot != null) {
            snapshot.tenantRemoved(tenantId);
        }
    }

    /** Called by the tenant service, just before it deletes the tenant. */
    @Override
    public void onTenantRemoved(String tenantId) {
        tenantRemoved(tenantId);
    }

    private Merged merged(Resolved base, TenantOriginsSnapshot.State origins) {
        Merged current = merged;
        if (current != null && current.base == base && current.origins == origins) {
            return current;
        }
        Merged next = new Merged(base, origins);
        merged = next;
        return next;
    }

    /** @return the name of the visitor cookie, as currently configured */
    public String cookieName() {
        return resolved.cookieName;
    }

    /**
     * The {@code Set-Cookie} value naming this profile; only the profile id is added per request.
     *
     * @param profileId the profile the cookie names
     * @param secure    whether the request reached Unomi over HTTPS
     * @return the {@code Set-Cookie} header value
     */
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
                    + "and have it send X-Forwarded-Proto: https. This is logged once per configuration change.");
        }
    }

    /**
     * Reports, once per configuration, a refused request that carried no {@code Origin}: if it came from a
     * listed website embedding {@code context.js} with a {@code <script>} tag, the operator needs to know why
     * that website keeps losing its visitors. Only once origins can be listed: before that, every website is
     * refused by design.
     *
     * @param hasReferer whether the refused request carried a {@code Referer}
     * @return whether the warning was logged by this call
     */
    public boolean reportRefusedRequestWithoutOrigin(boolean hasReferer) {
        Resolved current = resolved;
        boolean matchesReferer = current.policy.matchesReferer();
        if (matchesReferer && hasReferer) {
            // A Referer that is not listed: an unlisted website, nothing to fix.
            return false;
        }
        if (!current.refusedEmbedToReport.get() || !current.refusedEmbedToReport.compareAndSet(true, false)) {
            return false;
        }
        if (!matchesReferer) {
            LOGGER.warn("Refused the visitor cookie for a cross-site request without an Origin header, such as a "
                    + "<script src=\".../context.js\"> tag. If it came from a listed website, set "
                    + "org.apache.unomi.public.cors.matchReferer=true. This is logged once per configuration change.");
        } else {
            LOGGER.warn("Refused the visitor cookie for a cross-site request without Origin or Referer headers. If it "
                    + "came from a listed website, check that its Referrer-Policy sends the origin to other sites "
                    + "(the default strict-origin-when-cross-origin does). This is logged once per configuration change.");
        }
        return true;
    }
}
