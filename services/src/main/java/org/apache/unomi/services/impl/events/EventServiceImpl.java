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

package org.apache.unomi.services.impl.events;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventProperty;
import org.apache.unomi.api.PartialList;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class EventServiceImpl implements EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventServiceImpl.class.getName());
    private static final int MAX_RECURSION_DEPTH = 10;

    private List<EventListenerService> eventListeners = new ArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private BundleContext bundleContext;

    private Set<String> predefinedEventTypeIds = new LinkedHashSet<String>();

    private Set<String> restrictedEventTypeIds = new LinkedHashSet<String>();

    private Map<String, ThirdPartyServer> thirdPartyServers = new HashMap<>();

    public void init() {
        logger.info("Event service initialized.");
    }

    public void destroy() {
        logger.info("Event service shutdown.");
    }

    public void setThirdPartyConfiguration(Map<String,String> thirdPartyConfiguration) {
        this.thirdPartyServers = new HashMap<>();
        for (Map.Entry<String, String> entry : thirdPartyConfiguration.entrySet()) {
            String[] keys = StringUtils.split(entry.getKey(),'.');
            if (keys[0].equals("thirdparty")) {
                if (!thirdPartyServers.containsKey(keys[1])) {
                    thirdPartyServers.put(keys[1], new ThirdPartyServer(keys[1]));
                }
                ThirdPartyServer thirdPartyServer = thirdPartyServers.get(keys[1]);
                if (keys[2].equals("allowedEvents")) {
                    HashSet<String> allowedEvents = new HashSet<>(Arrays.asList(StringUtils.split(entry.getValue(), ',')));
                    restrictedEventTypeIds.addAll(allowedEvents);
                    thirdPartyServer.setAllowedEvents(allowedEvents);
                } else if (keys[2].equals("key")) {
                    thirdPartyServer.setKey(entry.getValue());
                } else if (keys[2].equals("ipAddresses")) {
                    Set<IPAddress> ipAddresses = new HashSet<>();
                    for (String ip : StringUtils.split(entry.getValue(), ',')) {
                        IPAddress ipAddress = new IPAddressString(ip.trim()).getAddress();
                        ipAddresses.add(ipAddress);
                    }
                    thirdPartyServer.setIpAddresses(ipAddresses);
                }
            }
        }
    }

    public void setPredefinedEventTypeIds(Set<String> predefinedEventTypeIds) {
        this.predefinedEventTypeIds = predefinedEventTypeIds;
    }

    public void setRestrictedEventTypeIds(Set<String> restrictedEventTypeIds) {
        this.restrictedEventTypeIds = restrictedEventTypeIds;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public boolean isEventAllowed(Event event, String thirdPartyId) {
        if (restrictedEventTypeIds.contains(event.getEventType())) {
            return thirdPartyServers.containsKey(thirdPartyId) && thirdPartyServers.get(thirdPartyId).getAllowedEvents().contains(event.getEventType());
        }
        return true;
    }

    public String authenticateThirdPartyServer(String key, String ip) {
        logger.debug("Authenticating third party server with key: " + key + " and IP: " + ip);
        if (key != null) {
            for (Map.Entry<String, ThirdPartyServer> entry : thirdPartyServers.entrySet()) {
                ThirdPartyServer server = entry.getValue();
                if (server.getKey().equals(key)) {
                    IPAddress ipAddress = new IPAddressString(ip).getAddress();
                    for (IPAddress serverIpAddress : server.getIpAddresses()) {
                        if (serverIpAddress.contains(ipAddress)) {
                            return server.getId();
                        }
                    }
                }
            }
            logger.debug("Could not authenticate any third party servers");
        }
        return null;
    }

    public int send(Event event) {
        return send(event, 0);
    }

    private int send(Event event, int depth) {
        if (depth > MAX_RECURSION_DEPTH) {
            logger.warn("Max recursion depth reached");
            return NO_CHANGE;
        }

        if (event.isPersistent()) {
            persistenceService.save(event);
        }

        int changes = NO_CHANGE;

        final Session session = event.getSession();
        if (event.isPersistent() && session != null) {
            session.setLastEventDate(event.getTimeStamp());
        }

        if (event.getProfile() != null) {
            for (EventListenerService eventListenerService : eventListeners) {
                if (eventListenerService.canHandle(event)) {
                    changes |= eventListenerService.onEvent(event);
                }
            }
            // At the end of the processing event execute the post executor actions
            for (ActionPostExecutor actionPostExecutor : event.getActionPostExecutors()) {
                changes |= actionPostExecutor.execute() ? changes : NO_CHANGE;
            }

            if ((changes & PROFILE_UPDATED) == PROFILE_UPDATED) {
                Event profileUpdated = new Event("profileUpdated", session, event.getProfile(), event.getScope(), event.getSource(), event.getProfile(), event.getTimeStamp());
                profileUpdated.setPersistent(false);
                profileUpdated.getAttributes().putAll(event.getAttributes());
                changes |= send(profileUpdated, depth + 1);
                if (session != null && session.getProfileId() != null) {
                    changes |= SESSION_UPDATED;
                    session.setProfile(event.getProfile());
                }
            }
        }
        return changes;
    }

    @Override
    public List<EventProperty> getEventProperties() {
        Map<String, Map<String, Object>> mappings = persistenceService.getPropertiesMapping(Event.ITEM_TYPE);
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
        Map<String, Long> dynamicEventTypeIds = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        Set<String> eventTypeIds = new LinkedHashSet<String>(predefinedEventTypeIds);
        eventTypeIds.addAll(dynamicEventTypeIds.keySet());
        return eventTypeIds;
    }

    @Override
    public PartialList<Event> searchEvents(Condition condition, int offset, int size) {
        ParserHelper.resolveConditionType(definitionsService, condition);
        return persistenceService.query(condition, "timeStamp", Event.class, offset, size);
    }

    @Override
    public PartialList<Event> searchEvents(String sessionId, String[] eventTypes, String query, int offset, int size, String sortBy) {
        List<Condition> conditions = new ArrayList<Condition>();

        Condition condition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
        condition.setParameter("propertyName", "sessionId");
        condition.setParameter("propertyValue", sessionId);
        condition.setParameter("comparisonOperator", "equals");
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", "or");
        List<Condition> subConditions = new ArrayList<Condition>();
        for (String eventType : eventTypes) {
            Condition subCondition = new Condition(definitionsService.getConditionType("eventPropertyCondition"));
            subCondition.setParameter("propertyName", "eventType");
            subCondition.setParameter("propertyValue", eventType);
            subCondition.setParameter("comparisonOperator", "equals");
            subConditions.add(subCondition);
        }
        condition.setParameter("subConditions", subConditions);
        conditions.add(condition);

        condition = new Condition(definitionsService.getConditionType("booleanCondition"));
        condition.setParameter("operator", "and");
        condition.setParameter("subConditions", conditions);

        if (StringUtils.isNotBlank(query)) {
            return persistenceService.queryFullText(query, condition, sortBy, Event.class, offset, size);
        } else {
            return persistenceService.query(condition, sortBy, Event.class, offset, size);
        }
    }

    @Override
    public PartialList<Event> search(Query query) {
        if (query.getCondition() != null && definitionsService.resolveConditionType(query.getCondition())) {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            }
        } else {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.getAllItems(Event.class, query.getOffset(), query.getLimit(), query.getSortby());
            }
        }
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
        condition.setParameter("propertyName", "target.itemType");
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
        if (serviceReference != null) {
            EventListenerService eventListenerService = bundleContext.getService(serviceReference);
            eventListeners.remove(eventListenerService);
        }
    }
}
