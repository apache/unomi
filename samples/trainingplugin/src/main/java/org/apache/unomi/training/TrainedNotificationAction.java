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
package org.apache.unomi.training;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;

import java.util.Collections;

/**
 * Created by amidani on 11/04/2017.
 */
public class TrainedNotificationAction implements ActionExecutor {

    private static final String TRAINED_NB_PROPERTY = "trained";
    private static final String TARGET = "profiles";

    private ProfileService service;

    public void setProfileService(ProfileService service) {
        this.service = service;
    }

    @Override
    public int execute(Action action, Event event) {
        final Profile profile = event.getProfile();
        Integer trained = (Integer) profile.getProperty(TRAINED_NB_PROPERTY);

        if (trained == null) {
            // create trained flag property type
            PropertyType propertyType = new PropertyType(new Metadata(event.getScope(), TRAINED_NB_PROPERTY, TRAINED_NB_PROPERTY, "Am I trained"));
            propertyType.setValueTypeId("boolean");
            propertyType.setTags(Collections.singleton("training"));
            propertyType.setTarget(TARGET);
            service.setPropertyType(propertyType);
        }

        profile.setProperty(TRAINED_NB_PROPERTY, true);
        return EventService.PROFILE_UPDATED;
    }
}
