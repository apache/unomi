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
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateProfilePropertiesAction implements ActionExecutor {

    public static final String PROPS_TO_ADD = "propertiesToAdd";
    public static final String PROPS_TO_UPDATE = "propertiesToUpdate";
    public static final String PROPS_TO_DELETE = "propertiesToDelete";
    public static final String PROFILE_TARGET_ID_KEY = "targetProfileId";
    Logger logger = LoggerFactory.getLogger(UpdateProfilePropertiesAction.class.getName());

    private ProfileService profileService;
    private EventService eventService;

    public int execute(Action action, Event event) {

        Profile target = event.getProfile();

        String targetProfileId = (String) event.getProperty(PROFILE_TARGET_ID_KEY);
        if (StringUtils.isNotBlank(targetProfileId) && event.getProfile() != null && !targetProfileId.equals(event.getProfile().getItemId())) {
            target = profileService.load(targetProfileId);
            if (target == null) {
                logger.warn("No profile found with Id : {}. Update skipped.", targetProfileId);
                return EventService.NO_CHANGE;
            }
        }

        boolean isProfileUpdated = false;

        Map<String, Object> propsToAdd = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_ADD);
        if (propsToAdd != null) {
            for (String prop : propsToAdd.keySet()) {
                PropertyType propType = null;
                if (prop.startsWith("properties.")) {
                    propType = profileService.getPropertyType(prop.substring("properties.".length()));
                }
                if (propType != null) {
                    isProfileUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsToAdd.get(prop), propType.getValueTypeId()), "setIfMissing");
                } else {
                    isProfileUpdated |= PropertyHelper.setProperty(target, prop, propsToAdd.get(prop), "setIfMissing");
                }
            }
        }

        Map<String, Object> propsToUpdate = (HashMap<String, Object>) event.getProperties().get(PROPS_TO_UPDATE);
        if (propsToUpdate != null) {
            for (String prop : propsToUpdate.keySet()) {
                PropertyType propType = null;
                if (prop.startsWith("properties.")) {
                    propType = profileService.getPropertyType(prop.substring("properties.".length()));
                }
                if (propType != null) {
                    isProfileUpdated |= PropertyHelper.setProperty(target, prop, PropertyHelper.getValueByTypeId(propsToUpdate.get(prop), propType.getValueTypeId()), "alwaysSet");
                } else {
                    isProfileUpdated |= PropertyHelper.setProperty(target, prop, propsToUpdate.get(prop), "alwaysSet");
                }
            }
        }

        List<String> propsToDelete = (List<String>) event.getProperties().get(PROPS_TO_DELETE);
        if (propsToDelete != null) {
            for (String prop : propsToDelete) {
                isProfileUpdated |= PropertyHelper.setProperty(target, prop, null, "remove");
            }
        }

        if (StringUtils.isNotBlank(targetProfileId) && isProfileUpdated &&
                event.getProfile() != null && !targetProfileId.equals(event.getProfile().getItemId())) {
            profileService.save(target);
            Event profileUpdated = new Event("profileUpdated", null, target, null, null, target, new Date());
            profileUpdated.setPersistent(false);
            int changes = eventService.send(profileUpdated);
            if ((changes & EventService.PROFILE_UPDATED) == EventService.PROFILE_UPDATED) {
                profileService.save(target);
            }
            return EventService.NO_CHANGE;

        }

        return isProfileUpdated ? EventService.PROFILE_UPDATED : EventService.NO_CHANGE;

    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

}
