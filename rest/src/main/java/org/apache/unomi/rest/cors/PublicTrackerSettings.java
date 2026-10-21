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

import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.security.SecurityContext;
import org.apache.unomi.api.security.TenantPrincipal;
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

import javax.security.auth.Subject;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * The public tracker origin lists and profile cookie settings, parsed and resolved when they are set
 * rather than on every request. The web configuration publishes the operator's settings through
 * {@link ConfigSharingService}; this component reads them at activation and again whenever one of them
 * changes. Each tenant may add its own website origins in its {@value #TENANT_ORIGINS_KEY} property; those
 * are kept in an in-memory snapshot, reloaded in the background of requests every
 * {@value #TENANT_REFRESH_SECONDS} seconds, and at once when a tenant is changed through this node or an
 * unknown tenant or origin shows up. Requests only read the results.
 */
@Component(service = {PublicTrackerSettings.class, ConfigChangeListener.class, TenantLifecycleListener.class})
public class PublicTrackerSettings implements ConfigChangeListener, TenantLifecycleListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicTrackerSettings.class);

    // Published by WebConfig through ConfigSharingService.
    static final String ALLOWED_ORIGINS_PROPERTY = "publicCorsAllowedOrigins";
    static final String SAME_SITE_PROPERTY = "profileIdCookieSameSite";
    static final String MATCH_REFERER_PROPERTY = "publicCorsMatchReferer";
    static final String TENANT_LISTS_PROPERTY = "publicCorsTenantOrigins";
    static final String COOKIE_NAME_PROPERTY = "profileIdCookieName";
    static final String COOKIE_DOMAIN_PROPERTY = "profileIdCookieDomain";
    static final String COOKIE_MAX_AGE_PROPERTY = "profileIdCookieMaxAgeInSeconds";
    static final String COOKIE_HTTP_ONLY_PROPERTY = "profileIdCookieHttpOnly";

    /** The tenant property, set through the tenant API, that lists the tenant's own website origins. */
    public static final String TENANT_ORIGINS_KEY = "publicCorsAllowedOrigins";

    static final int TENANT_REFRESH_SECONDS = 30;
    /** At most one reload for an unknown tenant or origin in this time, so requests cannot force reloads. */
    static final int TENANT_RELOAD_MIN_SECONDS = 5;

    static final String DEFAULT_COOKIE_NAME = "context-profile-id";
    static final int DEFAULT_COOKIE_MAX_AGE = 31536000;

    private static final Set<String> WATCHED = Set.of(
            ALLOWED_ORIGINS_PROPERTY, SAME_SITE_PROPERTY, MATCH_REFERER_PROPERTY, TENANT_LISTS_PROPERTY,
            COOKIE_NAME_PROPERTY, COOKIE_DOMAIN_PROPERTY, COOKIE_MAX_AGE_PROPERTY, COOKIE_HTTP_ONLY_PROPERTY);

    /** Everything a request needs, replaced as a whole so a request never sees half of a change. */
    private static final class Resolved {
        final PublicTrackerCorsPolicy policy;
        /** Whether tenants may add their own origins. */
        final boolean tenantLists;
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

        Resolved(PublicTrackerCorsPolicy policy, boolean tenantLists, String requestedSameSite, String cookieName,
                 String cookieDomain, int cookieMaxAge, boolean cookieHttpOnly, List<String> warnings) {
            this.policy = policy;
            this.tenantLists = tenantLists;
            this.warnings = List.copyOf(warnings);
            boolean originsListed = !policy.isEmpty() || tenantLists;
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

    /** The origins each tenant lists, as last loaded. Only tenants with a list have an entry in {@code lists}. */
    private static final class TenantOrigins {
        static final TenantOrigins NONE = new TenantOrigins(Collections.emptySet(), Collections.emptyMap(), Long.MIN_VALUE);

        final Set<String> tenantIds;
        final Map<String, Set<String>> lists;
        final long loadedAt;

        TenantOrigins(Set<String> tenantIds, Map<String, Set<String>> lists, long loadedAt) {
            this.tenantIds = tenantIds;
            this.lists = lists;
            this.loadedAt = loadedAt;
        }
    }

    /** The operator's policy merged with each tenant's list, and every origin any tenant lists (for preflights). */
    private static final class Merged {
        final Resolved base;
        final TenantOrigins origins;
        final Map<String, PublicTrackerCorsPolicy> byTenant;
        final Set<String> anyTenant;

        Merged(Resolved base, TenantOrigins origins) {
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

    private volatile Resolved resolved = resolve(null, null, null, null, null, null, null, null);
    private volatile TenantOrigins tenantOrigins = TenantOrigins.NONE;
    private volatile Merged merged;
    private volatile long lastTenantLoad = Long.MIN_VALUE;
    private final ReentrantLock tenantLoadLock = new ReentrantLock();
    /** Replaced in tests. */
    LongSupplier clock = System::nanoTime;

    @Reference
    private ConfigSharingService configSharingService;

    @Reference
    private TenantService tenantService;

    /** Used by OSGi Declarative Services; the settings are read at activation. */
    public PublicTrackerSettings() {
    }

    /** For tests; cookie name, domain, max age and HttpOnly take their defaults. */
    public PublicTrackerSettings(String allowedOrigins, String sameSite) {
        this(allowedOrigins, sameSite, false);
    }

    /** For tests; cookie name, domain, max age and HttpOnly take their defaults. */
    public PublicTrackerSettings(String allowedOrigins, String sameSite, boolean matchReferer) {
        this.resolved = resolve(allowedOrigins, sameSite, matchReferer, "false", null, null, null, null);
    }

    @Activate
    void activate() {
        reload();
        loadTenantOrigins();
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
        Resolved next = resolve(
                configSharingService.getProperty(ALLOWED_ORIGINS_PROPERTY),
                configSharingService.getProperty(SAME_SITE_PROPERTY),
                configSharingService.getProperty(MATCH_REFERER_PROPERTY),
                configSharingService.getProperty(TENANT_LISTS_PROPERTY),
                configSharingService.getProperty(COOKIE_NAME_PROPERTY),
                configSharingService.getProperty(COOKIE_DOMAIN_PROPERTY),
                configSharingService.getProperty(COOKIE_MAX_AGE_PROPERTY),
                configSharingService.getProperty(COOKIE_HTTP_ONLY_PROPERTY));
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

    private static Resolved resolve(Object allowedOrigins, Object sameSite, Object matchReferer, Object tenantListsSetting,
                                    Object cookieName, Object cookieDomain, Object cookieMaxAge, Object cookieHttpOnly) {
        List<String> warnings = new ArrayList<>();
        String requested = sameSite == null ? null : sameSite.toString().trim();
        String name = cookieName == null || cookieName.toString().isBlank() ? DEFAULT_COOKIE_NAME : cookieName.toString().trim();
        String origins = allowedOrigins == null ? null : allowedOrigins.toString();
        // Off unless explicitly turned on: it trusts everything a listed page frames.
        String refererSetting = matchReferer == null ? "" : matchReferer.toString().trim();
        boolean refererMatch = "true".equalsIgnoreCase(refererSetting);
        if (!refererSetting.isEmpty() && !refererMatch && !"false".equalsIgnoreCase(refererSetting)) {
            warnings.add("Unrecognised value '" + refererSetting + "' for org.apache.unomi.public.cors.matchReferer; "
                    + "only true turns it on, so Referer matching is off.");
        }
        // On unless explicitly turned off: an empty tenant list adds nothing.
        String tenantSetting = tenantListsSetting == null ? "" : tenantListsSetting.toString().trim();
        boolean tenantLists = !"false".equalsIgnoreCase(tenantSetting);
        if (!tenantSetting.isEmpty() && tenantLists && !"true".equalsIgnoreCase(tenantSetting)) {
            warnings.add("Unrecognised value '" + tenantSetting + "' for org.apache.unomi.public.cors.tenantOrigins; "
                    + "only false turns it off, so tenants may list their own origins.");
        }
        PublicTrackerCorsPolicy policy = PublicTrackerCorsPolicy.parse(origins, refererMatch, warnings);
        if (origins != null && !origins.isBlank() && policy.isEmpty()) {
            warnings.add("None of the public CORS allowed origins '" + origins + "' is valid, so no other website may "
                    + "use the visitor cookie. Expected scheme://host[:port], comma-separated.");
        }
        if (refererMatch && policy.isEmpty() && !tenantLists) {
            warnings.add("org.apache.unomi.public.cors.matchReferer is on but no website origin is listed, so it has no effect.");
        }
        if ("None".equalsIgnoreCase(requested) && !"None".equals(policy.resolveSameSite(requested, true, !policy.isEmpty() || tenantLists))) {
            warnings.add("Refusing SameSite=None for the profile cookie because the public CORS allowed origins list "
                    + "has no valid entry and tenant lists are off; using SameSite=Lax.");
        }
        int maxAge = toInt(cookieMaxAge, warnings);
        String httpOnlySetting = cookieHttpOnly == null ? "" : cookieHttpOnly.toString().trim();
        if (!httpOnlySetting.isEmpty() && !"true".equalsIgnoreCase(httpOnlySetting) && !"false".equalsIgnoreCase(httpOnlySetting)) {
            warnings.add("Unrecognised value '" + httpOnlySetting + "' for org.apache.unomi.profile.cookie.httpOnly; "
                    + "only false turns it off, so the cookie stays HttpOnly.");
        }
        return new Resolved(
                policy,
                tenantLists,
                requested,
                name,
                cookieDomain == null ? null : cookieDomain.toString(),
                maxAge,
                // HttpOnly unless explicitly turned off: page script must not read the bearer cookie,
                // so a typo keeps it on.
                cookieHttpOnly == null || !"false".equalsIgnoreCase(cookieHttpOnly.toString().trim()),
                warnings);
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
        if (!base.tenantLists || tenantId == null) {
            return base.policy;
        }
        refreshTenantOriginsIfStale();
        Merged current = merged(base);
        PublicTrackerCorsPolicy policy = current.byTenant.get(tenantId);
        if (policy == null && !current.origins.tenantIds.contains(tenantId) && reloadTenantOriginsSoon()) {
            // A tenant created since the last load, possibly on another node.
            policy = merged(resolved).byTenant.get(tenantId);
        }
        return policy != null ? policy : base.policy;
    }

    /**
     * Whether a preflight from this origin may be answered with credentials. A preflight carries no API key,
     * so the tenant is not known yet: an origin the operator or any tenant lists is accepted, and the actual
     * request is then checked against its own tenant.
     */
    public boolean allowsPreflight(String origin) {
        Resolved base = resolved;
        if (origin == null) {
            return false;
        }
        if (base.policy.allows(origin)) {
            return true;
        }
        if (!base.tenantLists) {
            return false;
        }
        refreshTenantOriginsIfStale();
        if (merged(base).anyTenant.contains(origin)) {
            return true;
        }
        // Perhaps a tenant that listed it since the last load, possibly on another node.
        return reloadTenantOriginsSoon() && merged(resolved).anyTenant.contains(origin);
    }

    /** Reloads the tenant origins now, after a tenant was created, changed or deleted through this node. */
    public void tenantsChanged() {
        loadTenantOrigins();
    }

    @Override
    public void onTenantRemoved(String tenantId) {
        loadTenantOrigins();
    }

    /**
     * The tenant the current REST request authenticated as, read from the CXF security context, which stays
     * in place until the response has been written. {@code null} outside a request or for an anonymous one.
     */
    public static String requestTenantId() {
        return tenantIdOf(JAXRSUtils.getCurrentMessage());
    }

    /**
     * The authentication filter stores the security context on the request message. In a response filter the
     * current message is the response, so the request message is read from the exchange.
     */
    static String tenantIdOf(Message message) {
        if (message == null) {
            return null;
        }
        Object context = message.get(SecurityContext.class);
        if (context == null && message.getExchange() != null && message.getExchange().getInMessage() != null) {
            context = message.getExchange().getInMessage().get(SecurityContext.class);
        }
        if (!(context instanceof RolePrefixSecurityContextImpl)) {
            return null;
        }
        Subject subject = ((RolePrefixSecurityContextImpl) context).getSubject();
        if (subject == null) {
            return null;
        }
        for (Principal principal : subject.getPrincipals()) {
            if (principal instanceof TenantPrincipal) {
                return ((TenantPrincipal) principal).getTenantId();
            }
        }
        return null;
    }

    private Merged merged(Resolved base) {
        Merged current = merged;
        TenantOrigins origins = tenantOrigins;
        if (current != null && current.base == base && current.origins == origins) {
            return current;
        }
        Merged next = new Merged(base, origins);
        merged = next;
        return next;
    }

    private void refreshTenantOriginsIfStale() {
        if (clock.getAsLong() - tenantOrigins.loadedAt < TimeUnit.SECONDS.toNanos(TENANT_REFRESH_SECONDS)) {
            return;
        }
        // One request reloads; the others keep using the previous snapshot meanwhile.
        if (tenantLoadLock.tryLock()) {
            try {
                if (clock.getAsLong() - tenantOrigins.loadedAt >= TimeUnit.SECONDS.toNanos(TENANT_REFRESH_SECONDS)) {
                    doLoadTenantOrigins();
                }
            } finally {
                tenantLoadLock.unlock();
            }
        }
    }

    /** Reloads unless a load happened in the last few seconds; returns whether it reloaded. */
    private boolean reloadTenantOriginsSoon() {
        tenantLoadLock.lock();
        try {
            if (clock.getAsLong() - lastTenantLoad < TimeUnit.SECONDS.toNanos(TENANT_RELOAD_MIN_SECONDS)) {
                return false;
            }
            doLoadTenantOrigins();
            return true;
        } finally {
            tenantLoadLock.unlock();
        }
    }

    private void loadTenantOrigins() {
        tenantLoadLock.lock();
        try {
            doLoadTenantOrigins();
        } finally {
            tenantLoadLock.unlock();
        }
    }

    private void doLoadTenantOrigins() {
        long now = clock.getAsLong();
        lastTenantLoad = now;
        if (tenantService == null) {
            return;
        }
        try {
            Set<String> ids = new HashSet<>();
            Map<String, Set<String>> lists = new HashMap<>();
            for (Tenant tenant : tenantService.getAllTenants()) {
                ids.add(tenant.getItemId());
                Object value = tenant.getProperties() == null ? null : tenant.getProperties().get(TENANT_ORIGINS_KEY);
                Set<String> origins = PublicTrackerCorsPolicy.parseTenantOrigins(value);
                if (!origins.isEmpty()) {
                    lists.put(tenant.getItemId(), origins);
                }
            }
            tenantOrigins = new TenantOrigins(Collections.unmodifiableSet(ids), Collections.unmodifiableMap(lists), now);
        } catch (RuntimeException e) {
            // Keep the previous snapshot; the next refresh tries again.
            LOGGER.warn("Could not load the tenants' public CORS allowed origins; keeping the previous ones: {}", e.getMessage());
            LOGGER.debug("Tenant origins load failure", e);
        }
    }

    /** @return the name of the visitor cookie, as currently configured */
    public String cookieName() {
        return resolved.cookieName;
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
                    + "and have it send X-Forwarded-Proto: https. This is logged once per configuration change.");
        }
    }

    /**
     * Reports, once per configuration, a refused request that carried no {@code Origin}: if it came from a
     * listed website embedding {@code context.js} with a {@code <script>} tag, the operator needs to know why
     * that website keeps losing its visitors. Only once origins are listed: before that, every website is
     * refused by design.
     *
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
