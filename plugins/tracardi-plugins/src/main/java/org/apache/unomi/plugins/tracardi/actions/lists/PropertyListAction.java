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

package org.apache.unomi.plugins.tracardi.actions.lists;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.plugins.tracardi.utils.ListHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.apache.unomi.plugins.tracardi.utils.Params.getParamAsInteger;
import static org.apache.unomi.plugins.tracardi.utils.Params.getParamAsString;

abstract public class PropertyListAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AddToPropertyListAction.class.getName());

    protected abstract boolean operation(Profile profile, List list, String propertyName, Object propertyToAdd);

    public int execute(Action action, Event event) {

        try {
            Profile profile = event.getProfile();

            String propertyName = getParamAsString(action, "propertyName", true);
            String propertyStringToAdd = getParamAsString(action, "propertyValue", false);
            Integer propertyIntegerToAdd = getParamAsInteger(action, "propertyValueInteger", false);
            Object propertyList = profile.getProperty(propertyName);

            // No ta list
            if (propertyList != null && !(propertyList instanceof List)) {
                logger.error("Property `" + propertyName + "` is not a list. Can not append value to non-list property.");
                return EventService.NO_CHANGE;
            }

            if (propertyList == null) {
                logger.error("Property `" + propertyName + "` does not exist. New empty list created.");
                propertyList = new ArrayList<>();
            }

            List list = (List) propertyList;

            // Check if value to add exists
            Object propertyToAdd;
            if (propertyStringToAdd != null) {
                propertyToAdd = propertyStringToAdd;
            } else if (propertyIntegerToAdd != null) {
                propertyToAdd = propertyIntegerToAdd;
            } else {
                throw new IllegalArgumentException("Missing value to add.");
            }

            // Check type of list elements
            Class<?> listObjectType = ListHelper.getListType(list);
            if (listObjectType != null && propertyToAdd.getClass() != listObjectType) {
                throw new IllegalArgumentException("Type of propertyValue must match type of list elements. List contains " +
                        "`" + propertyToAdd.getClass().toString() + "` types while propertyValue is type `" + listObjectType.toString() + "`");

            }

            // Add
            if (operation(profile, list, propertyName, propertyToAdd)) {
                return EventService.PROFILE_UPDATED;
            }


        } catch (IllegalArgumentException | NoSuchFieldException e) {
            logger.error(e.toString());
        }

        return EventService.NO_CHANGE;
    }

}
