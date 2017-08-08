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
package org.apache.unomi.samples.tweet_button_plugin.actions;

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionExecutor;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.ProfileService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Increments the number of times the user associated with the profile tweeted.
 */
public class IncrementTweetNumberAction implements ActionExecutor {
    private static final String TWEET_NB_PROPERTY = "tweetNb";
    private static final String TWEETED_FROM_PROPERTY = "tweetedFrom";
    private static final String TARGET = "profiles";

    private ProfileService service;

    public int execute(Action action, Event event) {
        final Profile profile = event.getProfile();
        Integer tweetNb = (Integer) profile.getProperty(TWEET_NB_PROPERTY);
        List<String> tweetedFrom = (List<String>) profile.getProperty(TWEETED_FROM_PROPERTY);

        if (tweetNb == null || tweetedFrom == null) {
            // create tweet number property type
            PropertyType propertyType = new PropertyType(new Metadata(event.getScope(), TWEET_NB_PROPERTY, TWEET_NB_PROPERTY, "Number of times a user tweeted"));
            propertyType.setValueTypeId("integer");
            propertyType.setTags(Collections.singleton("social"));
            propertyType.setTarget(TARGET);
            service.setPropertyType(propertyType);

            // create tweeted from property type
            propertyType = new PropertyType(new Metadata(event.getScope(), TWEETED_FROM_PROPERTY, TWEETED_FROM_PROPERTY, "The list of pages a user tweeted from"));
            propertyType.setValueTypeId("string");
            propertyType.setTags(Collections.singleton("social"));
            propertyType.setTarget(TARGET);
            propertyType.setMultivalued(true);
            service.setPropertyType(propertyType);

            tweetNb = 0;
            tweetedFrom = new ArrayList<>();
        }

        profile.setProperty(TWEET_NB_PROPERTY, tweetNb + 1);
        final String sourceURL = extractSourceURL(event);
        if (sourceURL != null) {
            tweetedFrom.add(sourceURL);
        }
        profile.setProperty(TWEETED_FROM_PROPERTY, tweetedFrom);

        return EventService.PROFILE_UPDATED;
    }

    public void setProfileService(ProfileService service) {
        this.service = service;
    }

    private String extractSourceURL(Event event) {
        final Item sourceAsItem = event.getSource();
        if (sourceAsItem instanceof CustomItem) {
            CustomItem source = (CustomItem) sourceAsItem;
            final String url = (String) source.getProperties().get("url");
            if (url != null) {
                return url;
            }
        }

        return null;
    }
}
