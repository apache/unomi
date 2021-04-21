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

package org.apache.unomi.plugins.tracardi.actions.numeric;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.unomi.plugins.tracardi.utils.Params.*;


abstract public class IncDecProfilePropertyAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(IncDecProfilePropertyAction.class.getName());

    abstract protected int operation(int value, int changeByValue);

    @Override
    public int execute(Action action, Event event) {
        logger.debug("Started");

        try {
            String propertyName = getParamAsString(action, "propertyName", true);
            int changeByValue = getParamAsInteger(action, "by", 1);

            Profile profile = event.getProfile();

            Object profileProperty = profile.getProperty(propertyName);

            if (profileProperty != null) {
                logger.debug("Updates value for profile " + profile.getItemId());
                try {

                    int value = getInteger(profileProperty);

                    value = operation(value, changeByValue);
                    profile.setProperty(propertyName, value);
                    logger.info("Profile property `" + propertyName + "` changed to " + value);

                    return EventService.PROFILE_UPDATED;

                } catch (NumberFormatException e) {
                    logger.error("Can not increment non-numeric value.");
                }
            } else {
                logger.debug("Sets default value for profile " + profile.getItemId());
                profile.setProperty(propertyName, 0);
                return EventService.PROFILE_UPDATED;
            }

        } catch (IllegalArgumentException | NoSuchFieldException e) {
            logger.error(e.toString());
        }

        return EventService.NO_CHANGE;
    }
}