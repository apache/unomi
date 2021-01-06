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
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UpdatePropertiesAction implements ActionExecutor {

    public static final String PROPS_TO_ADD = "add";
    public static final String PROPS_TO_UPDATE = "update";
    public static final String PROPS_TO_DELETE = "delete";
    public static final String PROPS_TO_ADD_TO_SET  = "addToSet";

    public static final String TARGET_ID_KEY = "targetId";
    public static final String TARGET_TYPE_KEY = "targetType";

    public static final String TARGET_TYPE_PROFILE = "profile";

    Logger logger = LoggerFactory.getLogger(UpdatePropertiesAction.class.getName());

    private ProfileService profileService;
    private EventService eventService;

    public int execute(Action action, Event event) {

        Profile target = event.getProfile();

        String targetId = (String) event.getProperty(TARGET_ID_KEY);
        String targetType = (String) event.getProperty(TARGET_TYPE_KEY);

        if (StringUtils.isNotBlank(targetId) && event.getProfile() != null && !targetId.equals(event.getProfile().getItemId())) {
            target = TARGET_TYPE_PROFILE.equals(targetType) ? profileService.load(targetId) : profileService.loadPersona(targetId);
            if (target == null) {
                logger.warn("No profile found with Id : {}. Update skipped.", targetId);
                return EventService.NO_CHANGE;
            }
        }

        boolean isProfileOrPersonaUpdated = false;

        Map<String, Object> propsToAdd = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD);

        if (propsToAdd != null) {
            isProfileOrPersonaUpdated |= processProperties(target, propsToAdd, "setIfMissing");
        }

        Map<String, Object> propsToUpdate = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_UPDATE);
        if (propsToUpdate != null) {
            isProfileOrPersonaUpdated |= processProperties(target, propsToUpdate, "alwaysSet");
        }

        Map<String, Object> propsToAddToSet = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD_TO_SET);
        if (propsToAddToSet != null) {
            isProfileOrPersonaUpdated |= processProperties(target, propsToAddToSet, "addValues");
        }

        List<String> propsToDelete = (List<String>) event.getProperties().get(PROPS_TO_DELETE);
        if (propsToDelete != null) {
            for (String prop : propsToDelete) {
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

        return isProfileOrPersonaUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;

    }

    private boolean processProperties(Profile target, Map<String, Object> propsMap, String strategy) {
        boolean isProfileOrPersonaUpdated = false;
        for (String prop : propsMap.keySet()) {
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
                isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsMap.get(prop), propType.getValueTypeId()), "alwaysSet");
            } else {
                isProfileOrPersonaUpdated |= PropertyHelper.setProperty(target, prop, propsMap.get(prop), strategy);
            }
        }
        return isProfileOrPersonaUpdated;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}
