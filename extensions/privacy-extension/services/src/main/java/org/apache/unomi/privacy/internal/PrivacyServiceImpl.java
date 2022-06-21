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
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.PrivacyService;
import org.apache.unomi.api.services.ProfileService;
import org.apache.unomi.lifecycle.BundleWatcher;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Privacy service implementation
 */
public class PrivacyServiceImpl implements PrivacyService {

    private static final Logger logger = LoggerFactory.getLogger(PrivacyServiceImpl.class);

    private PersistenceService persistenceService;
    private ProfileService profileService;
    private EventService eventService;
    private BundleWatcher bundleWatcher;

    public PrivacyServiceImpl() {
        logger.info("Initializing privacy service...");
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }


    public void setBundleWatcher(BundleWatcher bundleWatcher) {
        this.bundleWatcher = bundleWatcher;
    }

    @Override
    public ServerInfo getServerInfo() {
        List<ServerInfo> serverInfos = bundleWatcher.getServerInfos();
        ServerInfo serverInfo = serverInfos.get(0); // Unomi is always be the first entry

        addUnomiInfo(serverInfo);
        return serverInfo;
    }

    private void addUnomiInfo(ServerInfo serverInfo) {
        // let's retrieve all the event types the server has seen.
        Map<String, Long> eventTypeCounts = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        List<EventInfo> eventTypes = new ArrayList<EventInfo>();
        for (Map.Entry<String, Long> eventTypeEntry : eventTypeCounts.entrySet()) {
            EventInfo eventInfo = new EventInfo();
            eventInfo.setName(eventTypeEntry.getKey());
            eventInfo.setOccurences(eventTypeEntry.getValue());
            eventTypes.add(eventInfo);
        }
        serverInfo.setEventTypes(eventTypes);

        serverInfo.setCapabilities(new HashMap<>());
    }

    public List<ServerInfo> getServerInfos() {
        List<ServerInfo> serverInfos = bundleWatcher.getServerInfos();
        addUnomiInfo(serverInfos.get(0));
        return serverInfos;
    }

    @Override
    public Boolean deleteProfile(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        eventService.send(new Event("profileDeleted", null, profile, null, null, profile,null, new Date(), false));
        // we simply overwrite the existing profile with an empty one.
        Profile emptyProfile = new Profile(profileId);
        profileService.save(emptyProfile);
        return true;
    }

    @Override
    public Boolean anonymizeProfile(String profileId, String scope) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }

        // first we send out the anonymize profile event to make sure other systems can still use external identifiers to lookup the profile and anonymize it.
        eventService.send(new Event("anonymizeProfile", null, profile, scope, null, profile, null, new Date(), false));

        boolean res = profile.getProperties().keySet().removeAll(getDeniedProperties(profile.getItemId()));

        eventService.send(new Event("profileUpdated", null, profile, scope, null, profile, null, new Date(), false));

        profileService.save(profile);

        return res;
    }

    @Override
    public Boolean anonymizeBrowsingData(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }

        List<Session> sessions = profileService.getProfileSessions(profileId, null, 0, -1, null).getList();
        if (sessions.isEmpty()) {
            return false;
        }
        for (Session session : sessions) {
            Profile newProfile = getAnonymousProfile(session.getProfile());
            session.setProfile(newProfile);
            persistenceService.save(session);
            List<Event> events = eventService.searchEvents(session.getItemId(), new String[0], null, 0, -1, null).getList();
            for (Event event : events) {
                persistenceService.update(event, event.getTimeStamp(), Event.class, "profileId", newProfile.getItemId());
            }
        }

        return true;
    }

    @Override
    public Boolean deleteProfileData(String profileId,boolean purgeData) {
        if (purgeData) {
            eventService.removeProfileEvents(profileId);
            profileService.removeProfileSessions(profileId);
        } else {
            anonymizeBrowsingData(profileId);
        }
        profileService.delete(profileId, false);
        return true;
    }

    @Override
    public Boolean setRequireAnonymousBrowsing(String profileId, boolean anonymous, String scope) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        profile.getSystemProperties().put("requireAnonymousProfile", anonymous);
        if (anonymous) {
            profile.getSystemProperties().remove("goals");
            profile.getSystemProperties().remove("pastEvents");
        }
        Event profileUpdated = new Event("profileUpdated", null, profile, scope, null, profile, new Date());
        profileUpdated.setPersistent(false);
        eventService.send(profileUpdated);

        profileService.save(profile);
        return true;
    }

    public Boolean isRequireAnonymousBrowsing(String profileId) {
        Profile profile = profileService.load(profileId);
        return isRequireAnonymousBrowsing(profile);
    }

    @Override
    public Boolean isRequireAnonymousBrowsing(Profile profile) {
        if (profile == null) {
            return false;
        }
        Boolean anonymous = (Boolean) profile.getSystemProperties().get("requireAnonymousProfile");
        return anonymous != null && anonymous;
    }

    public Profile getAnonymousProfile(Profile profile) {
        Profile anonymousProfile = new Profile();
        anonymousProfile.getSystemProperties().put("isAnonymousProfile", true);
        anonymousProfile.getProperties().putAll(profile.getProperties());
        anonymousProfile.getProperties().keySet().removeAll(getDeniedProperties(profile.getItemId()));
//        profileService.save(anonymousProfile);
        return anonymousProfile;
    }

    @Override
    public List<String> getFilteredEventTypes(String profileId) {
        Profile profile = profileService.load(profileId);
        return getFilteredEventTypes(profile);
    }

    @Override
    public List<String> getFilteredEventTypes(Profile profile) {
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
        List deniedProperties = new ArrayList<String>();
        Set<PropertyType> personalIdsProps = profileService.getPropertyTypeBySystemTag(ProfileService.PERSONAL_IDENTIFIER_TAG_NAME);
        personalIdsProps.forEach(propType -> deniedProperties.add(propType.getMetadata().getId()));
        return deniedProperties;
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
