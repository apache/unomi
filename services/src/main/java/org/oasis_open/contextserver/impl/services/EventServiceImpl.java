package org.oasis_open.contextserver.impl.services;

/*
 * #%L
 * context-server-services
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.oasis_open.contextserver.api.*;
import org.oasis_open.contextserver.api.conditions.Condition;
import org.oasis_open.contextserver.api.services.DefinitionsService;
import org.oasis_open.contextserver.api.services.EventListenerService;
import org.oasis_open.contextserver.api.services.EventService;
import org.oasis_open.contextserver.api.services.ProfileService;
import org.oasis_open.contextserver.persistence.spi.PersistenceService;
import org.oasis_open.contextserver.persistence.spi.aggregate.TermsAggregate;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

public class EventServiceImpl implements EventService {

    private List<EventListenerService> eventListeners = new ArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private ProfileService profileService;

    private DefinitionsService definitionsService;

    private BundleContext bundleContext;

    private Set<String> predefinedEventTypeIds = new LinkedHashSet<String>();

    public void setPredefinedEventTypeIds(Set<String> predefinedEventTypeIds) {
        this.predefinedEventTypeIds = predefinedEventTypeIds;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setProfileService(ProfileService profileService) {
        this.profileService = profileService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean send(Event event) {
        if (event.isPersistent()) {
            persistenceService.save(event);
        }

        boolean changed = false;

        Profile profile = event.getProfile();
        final Session session = event.getSession();
        if (session != null) {
            session.setLastEventDate(event.getTimeStamp());
        }

        if (profile != null) {
            Map<String, Object> previousProperties = new LinkedHashMap<String, Object>(profile.getProperties());
            Set<String> previousSegments = new HashSet<String>(profile.getSegments());

            for (EventListenerService eventListenerService : eventListeners) {
                if (eventListenerService.canHandle(event)) {
                    changed |= eventListenerService.onEvent(event);
                }
            }

            if (session != null && session.getProfile() != null && !session.getProfile().getItemId().equals(profile.getItemId())) {
                // this can happen when profiles are merged for example.
                profile = session.getProfile();
            }

            if (changed && (!profile.getProperties().equals(previousProperties) || !profile.getSegments().equals(previousSegments))) {
                Event profileUpdated = new Event("profileUpdated", session, profile, event.getScope(), event.getSource(), profile, event.getTimeStamp());
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().putAll(event.getAttributes());
                send(profileUpdated);
                profileService.save(profile);
                if (session != null) {
                    session.setProfile(profile);
                }
            }

            if (session != null) {
                profileService.saveSession(session);
            }
        }
        return changed;
    }

    @Override
    public List<EventProperty> getEventProperties() {
        Map<String, Map<String, Object>> mappings = persistenceService.getMapping(Event.ITEM_TYPE);
        List<EventProperty> props = new ArrayList<>(mappings.size());
        getEventProperties(mappings, props, "");
        return props;
    }

    @SuppressWarnings("unchecked")
    private void getEventProperties(Map<String, Map<String, Object>> mappings, List<EventProperty> props, String prefix) {
        for (Map.Entry<String, Map<String, Object>> e : mappings.entrySet()) {
            if (e.getValue().get("properties") != null) {
                getEventProperties((Map<String, Map<String, Object>>) e.getValue().get("properties"), props, prefix + e.getKey() + ".");
            } else {
                props.add(new EventProperty(prefix + e.getKey(), (String) e.getValue().get("type")));
            }
        }
    }

    public Set<String> getEventTypeIds() {
        Map<String, Long> dynamicEventTypeIds = persistenceService.aggregateQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        Set<String> eventTypeIds = new LinkedHashSet<String>(predefinedEventTypeIds);
        eventTypeIds.addAll(dynamicEventTypeIds.keySet());
        return eventTypeIds;
    }

    @Override
    public PartialList<Event> searchEvents(Condition condition, int offset, int size) {
        return persistenceService.query(condition, "timeStamp", Event.class, offset, size);
    }

    public boolean hasEventAlreadyBeenRaised(Event event, boolean session) {
        List<Condition> conditions = new ArrayList<Condition>();

        Condition profileIdCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        if (session) {
            profileIdCondition.setParameter("propertyName", "sessionId");
            profileIdCondition.setParameter("propertyValue", event.getSessionId());
        } else {
            profileIdCondition.setParameter("propertyName", "profileId");
            profileIdCondition.setParameter("propertyValue", event.getProfileId());
        }
        profileIdCondition.setParameter("comparisonOperator", "equals");
        conditions.add(profileIdCondition);

        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "eventType");
        condition.setParameter("propertyValue", event.getEventType());
        condition.setParameter("comparisonOperator", "equals");
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "target.itemId");
        condition.setParameter("propertyValue", event.getTarget().getItemId());
        condition.setParameter("comparisonOperator", "equals");
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "target.type");
        condition.setParameter("propertyValue", event.getTarget().getItemType());
        condition.setParameter("comparisonOperator", "equals");
        conditions.add(condition);

        Condition andCondition = new Condition(definitionsService.getConditionType("booleanCondition"));
        andCondition.setParameter("operator", "and");
        andCondition.setParameter("subConditions", conditions);
        long size = persistenceService.queryCount(andCondition, Event.ITEM_TYPE);
        return size > 0;
    }


    public void bind(ServiceReference<EventListenerService> serviceReference) {
        EventListenerService eventListenerService = bundleContext.getService(serviceReference);
        eventListeners.add(eventListenerService);
    }

    public void unbind(ServiceReference<EventListenerService> serviceReference) {
        if(serviceReference!=null) {
            EventListenerService eventListenerService = bundleContext.getService(serviceReference);
            eventListeners.remove(eventListenerService);
        }
    }

}
