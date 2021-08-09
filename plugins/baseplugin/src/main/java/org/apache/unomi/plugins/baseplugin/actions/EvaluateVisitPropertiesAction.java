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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * This action is used to calculate the firstVisit, lastVisit and previousVisit date properties on the profile
 * Depending on the event timestamp it will adjust one or multiples of this properties accordingly to the logical chronology.
 */
public class EvaluateVisitPropertiesAction implements ActionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(EvaluateVisitPropertiesAction.class.getName());

    public int execute(Action action, Event event) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date currentEventTimeStamp = event.getTimeStamp();
        Date currentProfileFirstVisit = extractDateFromProperty(event.getProfile(), "firstVisit", dateFormat);
        Date currentProfilePreviousVisit = extractDateFromProperty(event.getProfile(), "previousVisit", dateFormat);
        Date currentProfileLastVisit = extractDateFromProperty(event.getProfile(), "lastVisit", dateFormat);

        int result = EventService.NO_CHANGE;

        if (currentProfileFirstVisit == null || currentProfileFirstVisit.after(currentEventTimeStamp)) {
            // event < firstVisit < previousVisit < lastVisit. we need to update firstVisit
            result = PropertyHelper.setProperty(event.getProfile(), "properties.firstVisit", dateFormat.format(currentEventTimeStamp), "alwaysSet") ?
                    EventService.PROFILE_UPDATED : result;
        }

        if (currentProfileLastVisit == null || currentProfileLastVisit.before(currentEventTimeStamp)) {
            // firstVisit < previousVisit < lastVisit < event. we need to update lastVisit and previousVisit
            if (PropertyHelper.setProperty(event.getProfile(), "properties.lastVisit", dateFormat.format(currentEventTimeStamp), "alwaysSet")) {
                result = EventService.PROFILE_UPDATED;

                if (currentProfileLastVisit != null) {
                    PropertyHelper.setProperty(event.getProfile(), "properties.previousVisit", dateFormat.format(currentProfileLastVisit), "alwaysSet");
                }
            }
        } else if (currentProfilePreviousVisit != null && currentProfilePreviousVisit.before(currentEventTimeStamp)) {
            // firstVisit < previousVisit < event < lastVisit. we need to update previousVisit
            result = PropertyHelper.setProperty(event.getProfile(), "properties.previousVisit", dateFormat.format(currentEventTimeStamp), "alwaysSet") ?
                    EventService.PROFILE_UPDATED : result;
        }

        return result;
    }

    private Date extractDateFromProperty(Profile profile, String propertyName, DateFormat dateFormat) {
        Object property = profile.getProperties().get(propertyName);
        Date date = null;
        try {
            if (property != null) {
                if (property instanceof String) {
                    date = dateFormat.parse((String) property);
                } else if (property instanceof Date) {
                    date = (Date) property;
                } else {
                    date = dateFormat.parse(property.toString());
                }
            }
        } catch (ParseException e) {
            logger.error("Error parsing {} date property. See debug log level for more information", propertyName);
            if (logger.isDebugEnabled()) {
                logger.debug("Error parsing date: {}, on profile: {}", property, profile.getItemId(), e);
            }
        }

        return date;
    }
}