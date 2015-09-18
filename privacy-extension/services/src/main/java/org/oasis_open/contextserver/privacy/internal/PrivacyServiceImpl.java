package org.oasis_open.contextserver.privacy.internal;

import org.oasis_open.contextserver.api.Event;
import org.oasis_open.contextserver.api.Profile;
import org.oasis_open.contextserver.api.Session;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;
import org.oasis_open.contextserver.api.EventInfo;
import org.oasis_open.contextserver.api.services.PrivacyService;
import org.oasis_open.contextserver.api.ServerInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by loom on 10.09.15.
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
        serverInfo.setServerIdentifier("Jahia Unomi");
        serverInfo.setServerVersion("1.0.0.SNAPSHOT");

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
    public Boolean setAnonymous(String profileId, boolean anonymous) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return false;
        }
        profile.setProperty("anonymous", anonymous);
        profileService.save(profile);
        return true;
    }

    public Boolean isAnonymous(String profileId) {
        Profile profile = profileService.load(profileId);
        if (profile == null) {
            return null;
        }
        Boolean anonymous = (Boolean) profile.getProperty("anonymous");
        return anonymous;
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
}
