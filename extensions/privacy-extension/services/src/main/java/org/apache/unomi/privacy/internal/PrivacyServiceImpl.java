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

package org.apache.unomi.privacy.internal;

import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;

import java.util.*;

/**
 * Privacy service implementation
 */
public class PrivacyServiceImpl implements PrivacyService {

    private PersistenceService persistenceService;
    private DefinitionsService definitionsService;
    private ProfileService profileService;
    private EventService eventService;

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public ServerInfo getServerInfo() {
        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setServerIdentifier("Apache Unomi");
        serverInfo.setServerVersion("2.0.0.incubating-SNAPSHOT");

        // let's retrieve all the event types the server has seen.
        Map<String,Long> eventTypeCounts = persistenceService.aggregateQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        List<EventInfo> eventTypes = new ArrayList<EventInfo>();
        for (Map.Entry<String,Long> eventTypeEntry : eventTypeCounts.entrySet()) {
            EventInfo eventInfo = new EventInfo();
            eventInfo.setName(eventTypeEntry.getKey());
            eventInfo.setOccurences(eventTypeEntry.getValue());
            eventTypes.add(eventInfo);
        }
        serverInfo.setEventTypes(eventTypes);

        serverInfo.setCapabilities(new HashMap<String,String>());
        return serverInfo;
    }

    @Override
    public Boolean deleteProfile(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        // we simply overwrite the existing profile with an empty one.
        Profile emptyProfile = new Profile(profileId);
        profileService.save(emptyProfile);
        return true;
    }

    @Override
    public String anonymizeBrowsingData(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return profileId;
        }

        List<Session> sessions = profileService.getProfileSessions(profileId, null, 0, -1, null).getList();
        for (Session session : sessions) {
            Profile newProfile = getAnonymousProfile();
            session.setProfile(newProfile);
            persistenceService.save(session);
            List<Event> events = eventService.searchEvents(session.getItemId(), new String[0], null, 0, -1, null).getList();
            for (Event event : events) {
                persistenceService.update(event.getItemId(), event.getTimeStamp(), Event.class, "profileId", newProfile.getItemId());
            }
        }

        return profileId;
    }

    @Override
    public Boolean deleteProfileData(String profileId) {
        Condition eventPropertyCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        eventPropertyCondition.setParameter("propertyName", "profileId");
        eventPropertyCondition.setParameter("propertyValue", profileId);
        eventPropertyCondition.setParameter("comparisonOperator", "equals");
        persistenceService.removeByQuery(eventPropertyCondition, Event.class);

        Condition sessionPropertyCondition = new Condition(definitionsService.getConditionType("sessionPropertyCondition"));
        sessionPropertyCondition.setParameter("propertyName", "profileId");
        sessionPropertyCondition.setParameter("propertyValue", profileId);
        sessionPropertyCondition.setParameter("comparisonOperator", "equals");
        persistenceService.removeByQuery(sessionPropertyCondition, Session.class);

        profileService.delete(profileId, false);
        return true;
    }

    @Override
    public Boolean setRequireAnonymousBrowsing(String profileId, boolean anonymous) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        profile.getSystemProperties().put("requireAnonymousProfile", anonymous);
        profileService.save(profile);
        return true;
    }

    public Boolean isRequireAnonymousBrowsing(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        Boolean anonymous = (Boolean) profile.getSystemProperties().get("requireAnonymousProfile");
        return anonymous != null && anonymous;
    }

    public Profile getAnonymousProfile() {
        String id = UUID.randomUUID().toString();
        Profile anonymousProfile = new Profile(id);
        anonymousProfile.getSystemProperties().put("isAnonymousProfile", true);
        profileService.save(anonymousProfile);
        return anonymousProfile;
    }

    @Override
    public List<String> getFilteredEventTypes(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return new ArrayList<String>();
        }
        return (List<String>) profile.getProperty("filteredEventTypes");
    }

    @Override
    public Boolean setFilteredEventTypes(String profileId, List<String> eventTypes) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return null;
        }
        profile.setProperty("filteredEventTypes", eventTypes);
        profileService.save(profile);
        return true;
    }

    @Override
    public List<String> getDeniedProperties(String profileId) {
        return null;
    }

    @Override
    public Boolean setDeniedProperties(String profileId, List<String> propertyNames) {
        return null;
    }

    @Override
    public List<String> getDeniedPropertyDistribution(String profileId) {
        return null;
    }

    @Override
    public Boolean setDeniedPropertyDistribution(String profileId, List<String> propertyNames) {
        return null;
    }

    @Override
    public Boolean removeProperty(String profileId, String propertyName) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return null;
        }
        if (!profile.getProperties().containsKey(propertyName)) {
            return false;
        }
        Object propertyValue = profile.getProperties().remove(propertyName);
        profileService.save(profile);
        return true;
    }
}
