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
package org.apache.unomi.rest.service.impl;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.interceptor.security.RolePrefixSecurityContextImpl;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.utils.LogSanitizer;
import org.apache.unomi.api.security.TenantPrincipal;
import org.apache.unomi.api.security.UnomiRoles;
import org.apache.unomi.api.services.ConfigSharingService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.rest.authentication.RestAuthenticationConfig;
import org.apache.unomi.rest.authentication.V2ThirdPartyConfigService;
import org.apache.unomi.services.common.security.SecurityUtils;
import org.apache.unomi.rest.exception.InvalidRequestException;
import org.apache.unomi.rest.service.RestServiceUtils;
import org.apache.unomi.schema.api.SchemaService;
import org.apache.unomi.utils.EventsRequestContext;
import org.apache.unomi.utils.HttpUtils;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Default implementation of {@link org.apache.unomi.rest.service.RestServiceUtils}.
 */
@Component(service = RestServiceUtils.class)
public class RestServiceUtilsImpl implements RestServiceUtils {

    private static final String DEFAULT_CLIENT_ID = "defaultClientId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RestServiceUtilsImpl.class.getName());
    public static final String UNOMI_TENANT_ID_HEADER = "X-Unomi-Tenant-Id";

    @Reference
    private ConfigSharingService configSharingService;

    @Reference
    private PrivacyService privacyService;

    @Reference
    private EventService eventService;

    @Reference
    private ProfileService profileService;

    @Reference
    SchemaService schemaService;

    @Reference
    private TenantService tenantService;

    @Reference
    private RestAuthenticationConfig restAuthenticationConfig;

    @Reference
    private V2ThirdPartyConfigService v2ThirdPartyConfigService;

    @Reference
    private SecurityService securityService;

    @Override
    public String getProfileIdCookieValue(HttpServletRequest httpServletRequest) {
        String cookieProfileId = null;

        Cookie[] cookies = httpServletRequest.getCookies();

        if (cookies != null) {
            final Object profileIdCookieName = configSharingService.getProperty("profileIdCookieName");
            for (Cookie cookie : cookies) {
                if (profileIdCookieName.equals(cookie.getName())) {
                    String profileIdJSON = JsonNodeFactory.instance.objectNode().put("profileId", cookie.getValue()).toString();
                    if (!schemaService.isValid(profileIdJSON, "https://unomi.apache.org/schemas/json/rest/requestIds/1-0-0")) {
                        throw new InvalidRequestException("Invalid profile ID format in cookie", "Invalid received data");
                    }
                    cookieProfileId = cookie.getValue();
                }
            }
        }
        return cookieProfileId;
    }

    @Override
    public EventsRequestContext initEventsRequest(String scope, String sessionId, String profileId, String personaId,
                                                  boolean invalidateProfile, boolean invalidateSession,
                                                  HttpServletRequest request, HttpServletResponse response, Date timestamp) {

        // Build context
        EventsRequestContext eventsRequestContext = new EventsRequestContext(timestamp, null, null, request, response);

        // Handle persona
        if (personaId != null) {
            PersonaWithSessions personaWithSessions = profileService.loadPersonaWithSessions(personaId);
            if (personaWithSessions == null) {
                LOGGER.error("Couldn't find persona, please check your personaId parameter");
            } else {
                eventsRequestContext.setProfile(personaWithSessions.getPersona());
                eventsRequestContext.setSession(personaWithSessions.getLastSession());
            }
        }

        final String requestedBodyProfileId = profileId;
        // Read unconditionally, where before the cookie was only consulted when no body profileId was
        // supplied. Every check below needs to know the cookie bearer even when the body names someone
        // else - that mismatch is the thing being guarded against. Note the widened side effect:
        // getProfileIdCookieValue rejects a schema-invalid cookie with a 400, so a request carrying
        // both an explicit profileId and a malformed cookie now fails where it used to be served.
        final String cookieProfileIdAtRequest = getProfileIdCookieValue(request);
        // Resolved once: the caller's identity cannot change during a single request, and the
        // checks below must all agree on it.
        final boolean trustedCaller = isTrustedProfileCaller();
        // When a public caller presents a foreign sessionId, we must not overwrite that session.
        String effectiveSessionId = sessionId;

        if (!trustedCaller) {
            // Public callers: the cookie is the only profile bearer, so a body profileId never selects
            // the profile — including when no cookie is present, where there is nothing to match against.
            // Logged at WARN like the two other refusals below, and for the same reason: this is an
            // identity claim being rejected. An integration that used to bind a profile this way
            // stops working at this line, and DEBUG would leave an operator with nothing to find.
            if (requestedBodyProfileId != null && !requestedBodyProfileId.equals(cookieProfileIdAtRequest)) {
                LOGGER.warn("Ignoring body profileId {} from public caller (cookie profileId is {})",
                        LogSanitizer.forLogging(requestedBodyProfileId), LogSanitizer.forLogging(cookieProfileIdAtRequest));
            }
            profileId = cookieProfileIdAtRequest;
        } else if (profileId == null) {
            profileId = cookieProfileIdAtRequest;
        }
        // else trusted caller keeps explicit body profileId (may differ from cookie)

        // Trusted callers may intentionally bind to a body profileId that differs from the cookie.
        //
        // A missing cookie counts as "differs". Requiring a cookie here meant a trusted integration
        // that sent an explicit profileId with no cookie - the normal shape for a server-side caller,
        // which has no browser and therefore no cookie jar - had its profile silently replaced by the
        // session owner further down, contradicting the documented ability to bind a profile
        // intentionally.
        final boolean trustedExplicitProfileOverride = trustedCaller
                && requestedBodyProfileId != null
                && !requestedBodyProfileId.equals(cookieProfileIdAtRequest);

        // invalidateSession re-creates the session bound to the supplied id, so it has to observe the
        // same ownership rule as the binding path below rather than being a second, unchecked route
        // to it: a public caller may only invalidate a session its own cookie already owns.
        if (invalidateSession && !trustedCaller && StringUtils.isNotBlank(effectiveSessionId)) {
            Session existingSession = profileService.loadSession(effectiveSessionId);
            // An unknown session id is fine - there is nothing to take over, and the request goes on to
            // create one. What must not pass is an existing session the cookie bearer cannot be shown to
            // own, which includes a session with no owner recorded at all: an anonymous session has a
            // null profileId, so an "owner differs" test would wave it through and re-create it under
            // the caller's profile.
            if (existingSession != null
                    && !isOwnedByCookieBearer(existingSession.getProfileId(), cookieProfileIdAtRequest)) {
                LOGGER.warn("Refusing to invalidate session {} owned by profile {} for a public caller "
                                + "whose cookie bearer is {}",
                        LogSanitizer.forLogging(effectiveSessionId), LogSanitizer.forLogging(existingSession.getProfileId()),
                        LogSanitizer.forLogging(cookieProfileIdAtRequest));
                eventsRequestContext.setSessionRefused(true);
                effectiveSessionId = null;
            }
        }

        if (profileId == null && effectiveSessionId == null && personaId == null) {
            LOGGER.warn("Couldn't find profileId, sessionId or personaId in incoming request! Stopped processing request. See debug level for more information");
            if (LOGGER.isDebugEnabled()) LOGGER.debug("Request dump: {}", HttpUtils.dumpRequestInfo(request));
            throw new BadRequestException("Couldn't find profileId, sessionId or personaId in incoming request!");
        }

        boolean profileCreated = false;
        if (eventsRequestContext.getProfile() == null) {
            if (profileId == null || invalidateProfile) {
                // no profileId cookie was found or the profile has to be invalidated, we generate a new one and create the profile in the profile service
                eventsRequestContext.setProfile(createNewProfile(null, timestamp));
                profileCreated = true;
            } else {
                eventsRequestContext.setProfile(profileService.load(profileId));
                if (eventsRequestContext.getProfile() == null) {
                    // this can happen if we have an old cookie but have reset the server,
                    // or if we merged the profiles and somehow this cookie didn't get updated.
                    eventsRequestContext.setProfile(createNewProfile(profileId, timestamp));
                    profileCreated = true;
                }
            }

            // Try to recover existing session
            Profile sessionProfile;
            if (StringUtils.isNotBlank(effectiveSessionId) && !invalidateSession) {

                eventsRequestContext.setSession(profileService.loadSession(effectiveSessionId));
                if (eventsRequestContext.getSession() != null) {

                    sessionProfile = eventsRequestContext.getSession().getProfile();
                    boolean anonymousSessionProfile = sessionProfile.isAnonymousProfile();
                    if (!eventsRequestContext.getProfile().isAnonymousProfile() &&
                            !anonymousSessionProfile &&
                            !eventsRequestContext.getProfile().getItemId().equals(sessionProfile.getItemId())) {
                        // Session profile differs from the request profile. Only switch when the
                        // cookie bearer already matches the session owner, or the caller is trusted —
                        // unless a trusted caller explicitly overrode the profile via the body.
                        boolean cookieOwnsSession = isOwnedByCookieBearer(sessionProfile.getItemId(), cookieProfileIdAtRequest);
                        if (trustedExplicitProfileOverride) {
                            LOGGER.debug("Keeping trusted body profileId {} despite session/cookie mismatch",
                                    LogSanitizer.forLogging(eventsRequestContext.getProfile().getItemId()));
                        } else if (cookieOwnsSession || trustedCaller) {
                            Profile sessionProfileWithId = profileService.load(sessionProfile.getItemId());
                            if (sessionProfileWithId != null) {
                                eventsRequestContext.setProfile(sessionProfileWithId);
                            } else {
                                LOGGER.warn("Couldn't find profile ID {} referenced from session with ID {}, so we re-create it",
                                        LogSanitizer.forLogging(sessionProfile.getItemId()), LogSanitizer.forLogging(effectiveSessionId));
                                eventsRequestContext.setProfile(createNewProfile(sessionProfile.getItemId(), timestamp));
                            }
                        } else {
                            LOGGER.warn("Refusing to switch profile from {} to session profile {} without matching cookie bearer; "
                                            + "detaching session {} for this request",
                                    LogSanitizer.forLogging(eventsRequestContext.getProfile().getItemId()),
                                    LogSanitizer.forLogging(sessionProfile.getItemId()), LogSanitizer.forLogging(effectiveSessionId));
                            // Detach so we neither adopt the foreign profile nor rebind the foreign
                            // session. No session exists for the rest of the request; the response
                            // must not echo the refused id back (see EventsRequestContext#isSessionRefused).
                            eventsRequestContext.setSession(null);
                            eventsRequestContext.setSessionRefused(true);
                            effectiveSessionId = null;
                        }
                    }

                    // Handle anonymous situation (only when we still hold a session)
                    if (eventsRequestContext.getSession() != null) {
                        Boolean requireAnonymousBrowsing = privacyService.isRequireAnonymousBrowsing(eventsRequestContext.getProfile());
                        if (requireAnonymousBrowsing && anonymousSessionProfile) {
                            // User wants to browse anonymously, anonymous profile is already set.
                        } else if (requireAnonymousBrowsing && !anonymousSessionProfile) {
                            // User wants to browse anonymously, update the sessionProfile to anonymous profile
                            sessionProfile = privacyService.getAnonymousProfile(eventsRequestContext.getProfile());
                            eventsRequestContext.getSession().setProfile(sessionProfile);
                            eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                        } else if (!requireAnonymousBrowsing && anonymousSessionProfile) {
                            // User does not want to browse anonymously anymore, update the sessionProfile to real profile.
                            //
                            // Only a trusted caller may do this. An anonymous session records no owner at
                            // all - PrivacyService#getAnonymousProfile returns a profile with no itemId, so
                            // the session's profileId is null - which leaves the ownership rule enforced
                            // everywhere else in this method with nothing to check the cookie against. The
                            // rebinding is a write, so honouring it for a public caller would hand the
                            // session to whoever presents its id, which is exactly what that rule exists to
                            // prevent. A public caller therefore leaves the session anonymous; the visitor
                            // picks up a named session again once the client rotates its session id.
                            //
                            // Refusing also avoids retroactively re-attributing every event already recorded
                            // in that session to a real profile, which is the outcome anonymous browsing was
                            // asked for in the first place.
                            if (trustedCaller) {
                                sessionProfile = eventsRequestContext.getProfile();
                                eventsRequestContext.getSession().setProfile(sessionProfile);
                                eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                            } else {
                                // INFO rather than WARN, unlike the refusals above: those fire on a claim
                                // that is demonstrably wrong, while this one cannot tell a takeover attempt
                                // from the visitor who legitimately just turned anonymity off - that is the
                                // whole difficulty. It also repeats on every request until the client picks
                                // a new session id, so a WARN here would train operators to ignore the ones
                                // that do mean something.
                                LOGGER.info("Not rebinding anonymous session {} to profile {} for a public caller: "
                                                + "an anonymous session has no recorded owner to match the cookie bearer against",
                                        LogSanitizer.forLogging(effectiveSessionId),
                                        LogSanitizer.forLogging(eventsRequestContext.getProfile().getItemId()));
                            }
                        } else if (!requireAnonymousBrowsing && !anonymousSessionProfile) {
                            // User does not want to browse anonymously, use the real profile. Check that session contains the current profile.
                            sessionProfile = eventsRequestContext.getProfile();
                            if (sessionProfile != null) {
                                if (!eventsRequestContext.getSession().getProfileId().equals(sessionProfile.getItemId())) {
                                    eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                                }
                                eventsRequestContext.getSession().setProfile(sessionProfile);
                            } else {
                                LOGGER.warn("Null profile in event request context");
                            }
                        }
                    }
                }
            }

            // Try to create new session
            if (eventsRequestContext.getSession() == null || invalidateSession) {
                sessionProfile = privacyService.isRequireAnonymousBrowsing(eventsRequestContext.getProfile()) ?
                        privacyService.getAnonymousProfile(eventsRequestContext.getProfile()) : eventsRequestContext.getProfile();

                if (StringUtils.isNotBlank(effectiveSessionId)) {
                    // Only save session and send event if a session id was provided, otherwise keep transient session

                    Session session = new Session(effectiveSessionId, sessionProfile, timestamp, scope);
                    eventsRequestContext.setSession(session);
                    eventsRequestContext.setNewSession(true);
                    eventsRequestContext.addChanges(EventService.SESSION_UPDATED);
                    Event event = new Event("sessionCreated", eventsRequestContext.getSession(), eventsRequestContext.getProfile(),
                            scope, null, eventsRequestContext.getSession(), null, timestamp, false);
                    if (sessionProfile.isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        event.setProfileId(null);
                    }
                    event.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                    event.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                    if (LOGGER.isDebugEnabled()) LOGGER.debug("Received event {} for profile={} session={} target={} timestamp={}", event.getEventType(),
                            eventsRequestContext.getProfile().getItemId(), eventsRequestContext.getSession().getItemId(), event.getTarget(), timestamp);
                    eventsRequestContext.addChanges(eventService.send(event));
                }
            }

            // Handle new profile creation
            if (profileCreated) {
                eventsRequestContext.addChanges(EventService.PROFILE_UPDATED);

                Event profileUpdated = new Event("profileUpdated", eventsRequestContext.getSession(), eventsRequestContext.getProfile(),
                        scope, null, eventsRequestContext.getProfile(), timestamp);
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, request);
                profileUpdated.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, response);
                profileUpdated.getAttributes().put(Event.CLIENT_ID_ATTRIBUTE, DEFAULT_CLIENT_ID);

                if (LOGGER.isDebugEnabled()) LOGGER.debug("Received event {} for profile={} {} target={} timestamp={}", profileUpdated.getEventType(),
                        eventsRequestContext.getProfile().getItemId(),
                        " session=" + (eventsRequestContext.getSession() != null ? eventsRequestContext.getSession().getItemId() : null),
                        profileUpdated.getTarget(), timestamp);
                eventsRequestContext.addChanges(eventService.send(profileUpdated));
            }
        }

        return eventsRequestContext;
    }

    @Override
    public EventsRequestContext performEventsRequest(List<Event> events, EventsRequestContext eventsRequestContext, SecurityContext securityContext) {
        List<String> filteredEventTypes = privacyService.getFilteredEventTypes(eventsRequestContext.getProfile());

        String tenantId = resolveTenantId(eventsRequestContext.getRequest());
        if (tenantId == null) {
            throw new WebApplicationException("Unable to resolve a tenant", Response.Status.UNAUTHORIZED);
        }

        // execute provided events if any
        if (events != null && !(eventsRequestContext.getProfile() instanceof Persona)) {
            // set Total items on context
            eventsRequestContext.setTotalItems(events.size());

            for (Event event : events) {
                eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() + 1);

                if (event.getEventType() != null) {
                    Event eventToSend = new Event(event.getEventType(), eventsRequestContext.getSession(), eventsRequestContext.getProfile(), event.getScope(),
                            event.getSource(), event.getTarget(), event.getProperties(), eventsRequestContext.getTimestamp(), event.isPersistent());
                    eventToSend.setFlattenedProperties(event.getFlattenedProperties());
                    // Check if V2 compatibility mode is enabled and handle V2-style event authorization
                    if (restAuthenticationConfig.isV2CompatibilityModeEnabled()) {
                        if (!isEventAllowedInV2CompatibilityMode(event, eventsRequestContext.getRequest())) {
                            LOGGER.debug("Event {} not authorized in V2 compatibility mode from IP {}", event.getEventType(), eventsRequestContext.getRequest().getRemoteAddr());
                            //Don't count the event that failed
                            eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() - 1);
                            continue;
                        }
                    } else {
                        // Normal V3 event authorization
                        if (!eventService.isEventAllowedForTenant(event, tenantId, eventsRequestContext.getRequest().getRemoteAddr())) {
                            LOGGER.debug("Tenant is not authorized to send event {} from IP {}", event.getEventType(), eventsRequestContext.getRequest().getRemoteAddr());
                            //Don't count the event that failed
                            eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() - 1);
                            continue;
                        }
                    }
                    if (securityContext.isUserInRole(UnomiRoles.TENANT_ADMINISTRATOR) && event.getItemId() != null) {
                        eventToSend = new Event(event.getItemId(), event.getEventType(), eventsRequestContext.getSession(), eventsRequestContext.getProfile(), event.getScope(),
                                event.getSource(), event.getTarget(), event.getProperties(), eventsRequestContext.getTimestamp(), event.isPersistent());
                        eventToSend.setFlattenedProperties(event.getFlattenedProperties());
                    }
                    if (filteredEventTypes != null && filteredEventTypes.contains(event.getEventType())) {
                        LOGGER.debug("Profile is filtering event type {}", event.getEventType());
                        eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() - 1);
                        continue;
                    }
                    if (eventsRequestContext.getProfile().isAnonymousProfile()) {
                        // Do not keep track of profile in event
                        eventToSend.setProfileId(null);
                    }

                    eventToSend.getAttributes().put(Event.HTTP_REQUEST_ATTRIBUTE, eventsRequestContext.getRequest());
                    eventToSend.getAttributes().put(Event.HTTP_RESPONSE_ATTRIBUTE, eventsRequestContext.getResponse());
                    LOGGER.debug("Received event {} for profile={} session={} target={} timestamp={}", event.getEventType(),
                            eventsRequestContext.getProfile().getItemId(),
                            eventsRequestContext.getSession() != null ? eventsRequestContext.getSession().getItemId() : null,
                            event.getTarget(), eventsRequestContext.getTimestamp());
                    eventsRequestContext.addChanges(eventService.send(eventToSend));
                    // If the event execution changes the profile we need to update it so the next event use the right profile
                    if ((eventsRequestContext.getChanges() & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        eventsRequestContext.setProfile(eventToSend.getProfile());
                    }
                    if (eventsRequestContext.isNewSession()) {
                        eventsRequestContext.getSession().getOriginEventIds().add(eventToSend.getItemId());
                        eventsRequestContext.getSession().getOriginEventTypes().add(eventToSend.getEventType());
                    }
                    if ((eventsRequestContext.getChanges() & EventService.ERROR) == EventService.ERROR) {
                        //Don't count the event that failed
                        eventsRequestContext.setProcessedItems(eventsRequestContext.getProcessedItems() - 1);
                        LOGGER.error("Error processing events. Total number of processed events: {}/{}", eventsRequestContext.getProcessedItems(), eventsRequestContext.getTotalItems());
                        break;
                    }
                }
            }

        }

        return eventsRequestContext;
    }

    private static String resolveTenantId(HttpServletRequest request) {
        RolePrefixSecurityContextImpl rolePrefixSecurityContextImpl = (RolePrefixSecurityContextImpl) JAXRSUtils.getCurrentMessage().get(org.apache.cxf.security.SecurityContext.class);
        Subject subject = rolePrefixSecurityContextImpl.getSubject();
        Optional<Principal> optTenantPrincipal = subject.getPrincipals().stream().filter(principal -> principal instanceof TenantPrincipal).findFirst();
        if (optTenantPrincipal.isPresent()) {
            TenantPrincipal tenantPrincipal = (TenantPrincipal) optTenantPrincipal.get();
            return tenantPrincipal.getTenantId();
        }
        String tenantId = request.getHeader(UNOMI_TENANT_ID_HEADER);
        if (tenantId == null) {
            return null;
        }
        tenantId = tenantId.trim();
        tenantId = tenantId.substring(0, Math.min(tenantId.length(), 100)); // basic protection against long string injection.
        return tenantId;
    }

    @Override
    public void finalizeEventsRequest(EventsRequestContext eventsRequestContext, boolean crashOnError) {
        // in case of changes on profile, persist the profile
        if ((eventsRequestContext.getChanges() & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
            profileService.save(eventsRequestContext.getProfile());
        }

        // in case of changes on session, persist the session
        if ((eventsRequestContext.getChanges() & EventService.SESSION_UPDATED) == EventService.SESSION_UPDATED && eventsRequestContext.getSession() != null) {
            profileService.saveSession(eventsRequestContext.getSession());
        }

        // In case of error, return an error message
        if ((eventsRequestContext.getChanges() & EventService.ERROR) == EventService.ERROR) {
            if (crashOnError) {
                String errorMessage = "Error processing events. Total number of processed events: " + eventsRequestContext.getProcessedItems() + "/"
                        + eventsRequestContext.getTotalItems();
                throw new BadRequestException(errorMessage);
            } else {
                eventsRequestContext.getResponse().setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }

        // Set profile cookie
        if (!(eventsRequestContext.getProfile() instanceof Persona)) {
            eventsRequestContext.getResponse().setHeader("Set-Cookie",
                    HttpUtils.getProfileCookieString(eventsRequestContext.getProfile(), configSharingService, eventsRequestContext.getRequest().isSecure()));
        }
    }

    private Profile createNewProfile(String existingProfileId, Date timestamp) {
        Profile profile;
        String profileId = existingProfileId;
        if (profileId == null) {
            profileId = UUID.randomUUID().toString();
        }
        profile = new Profile(profileId);
        profile.setProperty("firstVisit", timestamp);
        return profile;
    }

    /**
     * System or tenant administrators may override profile/session binding; public callers may not.
     * <p>
     * A tenant private key authenticates as {@link UnomiRoles#TENANT_ADMINISTRATOR}, so integrations
     * using one are trusted here; a tenant <em>public</em> API key is not.
     * <p>
     * {@code securityService} is a mandatory static {@code @Reference}, so this component is never
     * active without it. Deliberately not null-guarded: defaulting a missing identity service to
     * "untrusted" would silently strip every trusted integration of its binding rights with nothing
     * in the log to explain it, which is far harder to diagnose than the NPE that says so outright.
     */
    private boolean isTrustedProfileCaller() {
        return securityService.hasSystemAccess();
    }

    /**
     * Whether the profile named by the caller's cookie is the recorded owner of a session.
     * <p>
     * The one place the ownership rule is written down, so the two call sites that need it cannot
     * drift apart. Ownership has to be positively established, so an absent cookie and an unowned
     * session both answer {@code false}. A session with a {@code null} owner is not "owned by
     * nobody, so anyone may have it" - it is a session whose owner cannot be checked, which for this
     * purpose is the same answer.
     *
     * @param ownerProfileId           the profile id recorded as owning the session, may be {@code null}
     * @param cookieProfileIdAtRequest the profile id carried by the caller's cookie, may be {@code null}
     * @return {@code true} only when the cookie bearer demonstrably owns the session
     */
    private boolean isOwnedByCookieBearer(String ownerProfileId, String cookieProfileIdAtRequest) {
        return cookieProfileIdAtRequest != null && cookieProfileIdAtRequest.equals(ownerProfileId);
    }

    /**
     * Check if an event is allowed in V2 compatibility mode.
     * In V2, protected events required IP + X-Unomi-Peer (third-party key) authentication.
     *
     * @param event the event to check
     * @param request the HTTP request
     * @return true if the event is allowed, false otherwise
     */
    private boolean isEventAllowedInV2CompatibilityMode(Event event, HttpServletRequest request) {
        // Check if this is a protected event type using the V2 third-party configuration
        if (!v2ThirdPartyConfigService.isProtectedEventType(event.getEventType())) {
            // Non-protected events are always allowed in V2 compatibility mode
            return true;
        }

        // For protected events, check IP + third-party key (V2-style)
        String sourceIP = request.getRemoteAddr();
        String thirdPartyKey = request.getHeader("X-Unomi-Peer");

        if (StringUtils.isBlank(thirdPartyKey)) {
            LOGGER.debug("V2 compatibility mode: Protected event {} rejected - missing X-Unomi-Peer header", event.getEventType());
            return false;
        }

        // Validate the third-party provider using the V2 configuration
        if (!v2ThirdPartyConfigService.validateProviderByKey(thirdPartyKey, event.getEventType(), sourceIP)) {
            LOGGER.debug("V2 compatibility mode: Protected event {} rejected - invalid third-party provider key: {} from IP: {}",
                        event.getEventType(), SecurityUtils.maskSecret(thirdPartyKey), sourceIP);
            return false;
        }

        LOGGER.debug("V2 compatibility mode: Protected event {} allowed for provider key: {} from IP: {}",
                    event.getEventType(), SecurityUtils.maskSecret(thirdPartyKey), sourceIP);
        return true;
    }

}
