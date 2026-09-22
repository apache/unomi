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

package org.apache.unomi.plugins.baseplugin.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.Persona;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.utils.LogSanitizer;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.unomi.tracing.api.TracerService;
import org.apache.unomi.tracing.api.RequestTracer;

import java.util.*;
import java.util.regex.Pattern;

public class UpdatePropertiesAction implements ActionExecutor {

    public static final String PROPS_TO_ADD = "add";
    public static final String PROPS_TO_UPDATE = "update";
    public static final String PROPS_TO_DELETE = "delete";
    public static final String PROPS_TO_ADD_TO_SET  = "addToSet";

    public static final String TARGET_ID_KEY = "targetId";
    public static final String TARGET_TYPE_KEY = "targetType";

    public static final String TARGET_TYPE_PROFILE = "profile";

    /**
     * The only profile areas an event may write, by caller trust. Everything else on the bean
     * ({@code itemId}, {@code itemType}, {@code tenantId}, {@code version}, {@code mergedWith},
     * {@code scope}, {@code systemMetadata}, ...) is identity or bookkeeping and is never writable
     * through an event: an allowlist needs no knowledge of what a future field might be called.
     */
    private static final Set<String> PUBLIC_WRITABLE_AREAS = Set.of("properties");
    private static final Set<String> TRUSTED_WRITABLE_AREAS = Set.of("properties", "systemProperties", "segments", "scores", "consents");
    /**
     * A plain dotted path: non-empty segments, no control characters, and none of the
     * commons-beanutils mapped/indexed syntax ({@code a(b)}, {@code a[0]}) that
     * {@link PropertyHelper#setProperty} would otherwise interpret. Everything this action
     * legitimately writes is expressible as {@code area.key.subkey}.
     */
    private static final Pattern PROPERTY_PATH = Pattern.compile("[^.()\\[\\]\\p{Cntrl}]+(?:\\.[^.()\\[\\]\\p{Cntrl}]+)*");

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdatePropertiesAction.class.getName());

    private ProfileService profileService;
    private EventService eventService;
    private TracerService tracerService;
    private SecurityService securityService;

    public int execute(Action action, Event event) {
        RequestTracer tracer = null;
        if (tracerService != null && tracerService.isTracingEnabled()) {
            tracer = tracerService.getCurrentTracer();
            tracer.startOperation("update-properties", 
                "Updating properties", action);
        }

        try {
            Profile target = event.getProfile();
            String targetId = (String) event.getProperty(TARGET_ID_KEY);
            String targetType = (String) event.getProperty(TARGET_TYPE_KEY);
            // Resolved once for the whole action: the caller's roles cannot change while it runs.
            final boolean trustedCaller = isTrustedIdentityCaller();

            if (tracer != null) {
                Map<String, Object> traceData = new HashMap<>();
                traceData.put("targetId", targetId);
                traceData.put("targetType", targetType);
                traceData.put("hasTarget", target != null);
                tracer.trace("Processing properties update", traceData);
            }

            if (StringUtils.isNotBlank(targetId) && event.getProfile() != null && !targetId.equals(event.getProfile().getItemId())) {
                if (!trustedCaller) {
                    LOGGER.warn("Refusing cross-profile property update for untrusted caller (targetId={})",
                            LogSanitizer.forLogging(targetId));
                    if (tracer != null) {
                        tracer.endOperation(false, "Untrusted caller cannot update another profile");
                    }
                    return EventService.NO_CHANGE;
                }
                target = TARGET_TYPE_PROFILE.equals(targetType) ? profileService.load(targetId) : profileService.loadPersona(targetId);
                if (target == null) {
                    if (tracer != null) {
                        tracer.endOperation(false, "No profile found with Id: " + targetId);
                    }
                    LOGGER.warn("No profile found with Id : {}. Update skipped.", LogSanitizer.forLogging(targetId));
                    return EventService.NO_CHANGE;
                }
            }

            boolean isProfileOrPersonaUpdated = false;

            Map<String, Object> propsToAdd = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD);

            if (propsToAdd != null) {
                isProfileOrPersonaUpdated |= processProperties(target, propsToAdd, "setIfMissing", trustedCaller);
            }

            Map<String, Object> propsToUpdate = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_UPDATE);
            if (propsToUpdate != null) {
                isProfileOrPersonaUpdated |= processProperties(target, propsToUpdate, "alwaysSet", trustedCaller);
            }

            Map<String, Object> propsToAddToSet = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD_TO_SET);
            if (propsToAddToSet != null) {
                isProfileOrPersonaUpdated |= processProperties(target, propsToAddToSet, "addValues", trustedCaller);
            }

            List<String> propsToDelete = (List<String>) event.getProperties().get(PROPS_TO_DELETE);
            if (propsToDelete != null) {
                for (String prop : propsToDelete) {
                    if (!isWritable(prop, trustedCaller)) {
                        LOGGER.warn("Refusing property delete for {} caller: {}", trustedCaller ? "trusted" : "untrusted",
                                LogSanitizer.forLogging(prop));
                        continue;
                    }
                    isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, null, "remove");
                }
            }

            if (StringUtils.isNotBlank(targetId) && isProfileOrPersonaUpdated &&
                    event.getProfile() != null && !targetId.equals(event.getProfile().getItemId())) {
                if (TARGET_TYPE_PROFILE.equals(targetType)) {
                    profileService.save(target);
                    Event profileUpdated = new Event("profileUpdated", null, target, null, null, target, new Date());
                    profileUpdated.setPersistent(false);
                    int changes = eventService.send(profileUpdated);
                    if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                        profileService.save(target);
                    }
                } else {
                    profileService.savePersona((Persona) target);
                }

                return EventService.NO_CHANGE;

            }

            if (tracer != null) {
                tracer.endOperation(isProfileOrPersonaUpdated, 
                    isProfileOrPersonaUpdated ? "Properties updated successfully" : "No changes needed");
            }
            return isProfileOrPersonaUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;
        } catch (Exception e) {
            if (tracer != null) {
                tracer.endOperation(false, "Error updating properties: " + e.getMessage());
            }
            throw e;
        }
    }

    private boolean processProperties(Profile target, Map<String, Object> propsMap, String strategy, boolean trustedCaller) {
        boolean isProfileOrPersonaUpdated = false;
        for (String prop : propsMap.keySet()) {
            if (!isWritable(prop, trustedCaller)) {
                LOGGER.warn("Refusing property write for {} caller: {}", trustedCaller ? "trusted" : "untrusted",
                        LogSanitizer.forLogging(prop));
                continue;
            }
            PropertyType propType = null;
            if (prop.startsWith("properties.") || prop.startsWith("systemProperties.")) {
                propType = profileService.getPropertyType(prop.substring(prop.indexOf('.') + 1));
            } else {
                propType = profileService.getPropertyType(prop);
                //ideally each property must have a matching propertyType
                if(prop.equals("segments")) {
                    propsMap.put(prop, new HashSet<String>((ArrayList<String>)propsMap.get(prop)));
                }
            }
            if (propType != null) {
                isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsMap.get(prop), propType.getValueTypeId()), strategy);
            } else {
                isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, propsMap.get(prop), strategy);
            }
        }
        return isProfileOrPersonaUpdated;
    }


    /**
     * Whether an event-supplied property name may be written by this caller.
     * <p>
     * One rule covers every injection shape seen against this action: the name must be a plain
     * dotted path (so beanutils mapped/indexed syntax, empty segments and control characters are
     * out), and its first segment must be one of the areas allowed for the caller's trust level
     * (so identity fields such as {@code itemId} or {@code mergedWith}, and for public callers
     * {@code systemProperties}, {@code segments}, {@code scores} and {@code consents}, are out).
     *
     * @param propertyName the event-supplied property name
     * @param trustedCaller whether the caller holds system access
     * @return true when the write is allowed
     */
    static boolean isWritable(String propertyName, boolean trustedCaller) {
        if (propertyName == null || !PROPERTY_PATH.matcher(propertyName).matches()) {
            return false;
        }
        int dot = propertyName.indexOf('.');
        String area = dot < 0 ? propertyName : propertyName.substring(0, dot);
        return (trustedCaller ? TRUSTED_WRITABLE_AREAS : PUBLIC_WRITABLE_AREAS).contains(area);
    }

    private boolean isTrustedIdentityCaller() {
        return IdentityTrust.isTrustedIdentityCaller(securityService);
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

}
