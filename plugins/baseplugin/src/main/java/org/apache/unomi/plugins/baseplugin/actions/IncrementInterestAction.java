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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncrementInterestAction implements ActionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(IncrementInterestAction.class.getName());

    private static final String EVENT_INTERESTS_PROPERTY = "interests";

    private static final String ACTION_INTERESTS_PROPERTY = "eventInterestProperty";

    private TopicService topicService;

    private EventService eventService;

    private Double interestsMinValue;

    private Double interestsMaxValue;

    private Double interestsDividerValue;

    @Override
    @SuppressWarnings("unchecked")
    public int execute(final Action action, final Event event) {
        Map<String, Double> interestsAsMap = (Map<String, Double>) action.getParameterValues().get( ACTION_INTERESTS_PROPERTY );

        if ( interestsAsMap == null ) {
            interestsAsMap = (Map<String, Double>) event.getProperty( EVENT_INTERESTS_PROPERTY );

            if (interestsAsMap == null) {
                return EventService.NO_CHANGE;
            }
        }

        final Profile profile = event.getProfile();

        final Map<String, Double> profileInterestsMap = new HashMap<>();

        if (profile.getProperty( EVENT_INTERESTS_PROPERTY ) != null) {
            profileInterestsMap.putAll((Map<String, Double>) profile.getProperty( EVENT_INTERESTS_PROPERTY ));
        }

        interestsAsMap.forEach((topicId, incrementScoreBy) -> {
            if (topicService.load(topicId) != null) {
                if (!profileInterestsMap.containsKey(topicId)) {
                    profileInterestsMap.put(topicId, incrementScoreBy);
                } else {
                    profileInterestsMap.merge(topicId, incrementScoreBy, Double::sum);
                }

                double value = Math.max(Math.min(profileInterestsMap.get(topicId), interestsMaxValue), interestsMinValue);

                value = Math.min(value, interestsDividerValue) / interestsDividerValue;

                profileInterestsMap.put(topicId, value);
            } else {
                LOG.warn("The interest with key \"{}\" was not recalculated for profile with itemId \"{}\" ", topicId, profile.getItemId());
            }
        });

        final Map<String, Object> propertyToUpdate = new HashMap<>();
        propertyToUpdate.put("properties.interests", profileInterestsMap);

        final Event updatePropertiesEvent = new Event("updateProperties", null, profile, null, null, profile, new Date());
        updatePropertiesEvent.setProperty("update", propertyToUpdate);

        return eventService.send(updatePropertiesEvent);
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setTopicService(TopicService topicService) {
        this.topicService = topicService;
    }

    public void setInterestsMinValue(Double interestsMinValue) {
        this.interestsMinValue = interestsMinValue;
    }

    public void setInterestsMaxValue(Double interestsMaxValue) {
        this.interestsMaxValue = interestsMaxValue;
    }

    public void setInterestsDividerValue(Double interestsDividerValue) {
        this.interestsDividerValue = interestsDividerValue;
    }

}
