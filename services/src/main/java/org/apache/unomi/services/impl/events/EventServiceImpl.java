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

import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.services.common.security.IPValidationUtils;
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.api.utils.ParserHelper;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventServiceImpl implements EventService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);
    private static final int MAX_RECURSION_DEPTH = 10;

    private List<EventListenerService> eventListeners = new CopyOnWriteArrayList<EventListenerService>();

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private TenantService tenantService;

    private BundleContext bundleContext;

    private Set<String> predefinedEventTypeIds = new LinkedHashSet<String>();

    private Set<String> restrictedEventTypeIds = new LinkedHashSet<String>();

    private TracerService tracerService;

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

    public void setTenantService(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setTracerService(TracerService tracerService) {
        this.tracerService = tracerService;
    }

    @Override
    public boolean isEventAllowedForTenant(Event event, String tenantId, String sourceIP) {
        if (event == null || tenantId == null) {
            return false;
        }

        // Get tenant
        Tenant tenant = tenantService.getTenant(tenantId);
        if (tenant == null) {
            return false;
        }

        // Check tenant-specific restrictions first
        Set<String> tenantRestrictions = tenant.getRestrictedEventTypes();
        if (tenantRestrictions != null && !tenantRestrictions.isEmpty()) {
            // If tenant has defined restrictions, check if this event type is restricted
            if (tenantRestrictions.contains(event.getEventType())) {
                // Event is restricted by tenant, proceed to IP check
                return checkIPAuthorization(tenant, sourceIP);
            }
        }

        // If tenant has no restrictions or event not in tenant restrictions,
        // check global restrictions
        if (restrictedEventTypeIds.contains(event.getEventType())) {
            // Event is restricted globally, proceed to IP check
            return checkIPAuthorization(tenant, sourceIP);
        }

        // Event is not restricted by either tenant or global settings
        return true;
    }

    private boolean checkIPAuthorization(Tenant tenant, String sourceIP) {
        Set<String> authorizedIPs = tenant.getAuthorizedIPs();
        return IPValidationUtils.isIpAuthorized(sourceIP, authorizedIPs);
    }

    public int send(Event event) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        tracer.trace("Sending event: " + event.getEventType(), event.getItemId());
        return send(event, 0);
    }

    private int send(Event event, int depth) {
        RequestTracer tracer = tracerService.getCurrentTracer();
        if (depth > MAX_RECURSION_DEPTH) {
            tracer.trace("Max recursion depth reached for event: " + event.getEventType(), event.getItemId());
            LOGGER.warn("Max recursion depth reached");
            return NO_CHANGE;
        }

        boolean saveSucceeded = true;
        if (event.isPersistent()) {
            try {
                tracer.trace("Saving persistent event: " + event.getEventType(), event.getItemId());
                saveSucceeded = persistenceService.save(event, null, true);
            } catch (Throwable t) {
                tracer.trace("Failed to save event: " + event.getEventType() + ", error: " + t.getMessage(), event.getItemId());
                LOGGER.error("Failed to save event: ", t);
                return NO_CHANGE;
            }
        }

        int changes;

        if (saveSucceeded) {
            changes = NO_CHANGE;
            final Session session = event.getSession();
            if (event.isPersistent() && session != null) {
                session.setLastEventDate(event.getTimeStamp());
                tracer.trace("Updated session last event date", session.getItemId());
            }

            if (event.getProfile() != null) {
                tracer.trace("Processing event for profile: " + event.getProfile().getItemId(), event.getItemId());
                for (EventListenerService eventListenerService : eventListeners) {
                    if (eventListenerService.canHandle(event)) {
                        tracer.trace("Event listener service handling event: " + eventListenerService.getClass().getSimpleName(), event.getItemId());
                        changes |= eventListenerService.onEvent(event);
                    }
                }
                // At the end of the processing event execute the post executor actions
                for (ActionPostExecutor actionPostExecutor : event.getActionPostExecutors()) {
                    tracer.trace("Executing post action executor", event.getItemId());
                    changes |= actionPostExecutor.execute() ? changes : NO_CHANGE;
                }

                if ((changes & PROFILE_UPDATED) == PROFILE_UPDATED) {
                    tracer.trace("Profile updated, sending profileUpdated event", event.getItemId());
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
        } else {
            changes = ERROR;
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

    private List<PropertyType> getEventPropertyTypes() {
        Map<String, Map<String, Object>> mappings = persistenceService.getPropertiesMapping(Event.ITEM_TYPE);
        return new ArrayList<>(getEventPropertyTypes(mappings));
    }

    @SuppressWarnings("unchecked")
    private Set<PropertyType> getEventPropertyTypes(Map<String, Map<String, Object>> mappings) {
        Set<PropertyType> properties = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : mappings.entrySet()) {
            Set<PropertyType> childProperties = null;
            Metadata propertyMetadata = new Metadata(null, e.getKey(), e.getKey(), null);
            Set<String> systemTags = new HashSet<>();
            propertyMetadata.setSystemTags(systemTags);
            PropertyType propertyType = new PropertyType(propertyMetadata);
            propertyType.setTarget("event");
            ValueType valueType = null;
            if (e.getValue().get("properties") != null) {
                childProperties = getEventPropertyTypes((Map<String, Map<String, Object>>) e.getValue().get("properties"));
                valueType = definitionsService.getValueType("set");
                if (childProperties != null && childProperties.size() > 0) {
                    propertyType.setChildPropertyTypes(childProperties);
                }
            } else {
                valueType = mappingTypeToValueType( (String) e.getValue().get("type"));
            }
            propertyType.setValueTypeId(valueType.getId());
            propertyType.setValueType(valueType);
            properties.add(propertyType);
        }
        return properties;
    }

    private ValueType mappingTypeToValueType(String mappingType) {
        if ("text".equals(mappingType)) {
            return definitionsService.getValueType("string");
        } else if ("date".equals(mappingType)) {
            return definitionsService.getValueType("date");
        } else if ("long".equals(mappingType)) {
            return definitionsService.getValueType("integer");
        } else if ("boolean".equals(mappingType)) {
            return definitionsService.getValueType("boolean");
        } else if ("set".equals(mappingType)) {
            return definitionsService.getValueType("set");
        } else if ("object".equals(mappingType)) {
            return definitionsService.getValueType("set");
        } else {
            return definitionsService.getValueType("unknown");
        }
    }

    public Set<String> getEventTypeIds() {
        Map<String, Long> dynamicEventTypeIds = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        Set<String> eventTypeIds = new LinkedHashSet<String>(predefinedEventTypeIds);
        eventTypeIds.addAll(dynamicEventTypeIds.keySet());
        eventTypeIds.remove("_filtered");
        return eventTypeIds;
    }

    @Override
    public PartialList<Event> searchEvents(Condition condition, int offset, int size) {
        ParserHelper.resolveConditionType(definitionsService, condition, "event search");
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
        if (query.getScrollIdentifier() != null) {
            return persistenceService.continueScrollQuery(Event.class, query.getScrollIdentifier(), query.getScrollTimeValidity());
        }
        if (query.getCondition() != null && definitionsService.resolveConditionType(query.getCondition())) {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit(), query.getScrollTimeValidity());
            }
        } else {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.getAllItems(Event.class, query.getOffset(), query.getLimit(), query.getSortby());
            }
        }
    }

    @Override
    public Event getEvent(String id) {
        return persistenceService.load(id, Event.class);
    }

    public boolean hasEventAlreadyBeenRaised(Event event) {
        Event pastEvent = this.persistenceService.load(event.getItemId(), Event.class);
        if (pastEvent != null && pastEvent.getVersion() >= 1) {
            if ((pastEvent.getSessionId() != null && pastEvent.getSessionId().equals(event.getSessionId())) ||
                    (pastEvent.getProfileId() != null && pastEvent.getProfileId().equals(event.getProfileId())))  {
                return true;
            }
        }
        return false;
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

    public void addEventListenerService(EventListenerService eventListenerService) {
        eventListeners.add(eventListenerService);
    }

    public void removeEventListenerService(EventListenerService eventListenerService) {
        eventListeners.remove(eventListenerService);
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

    public void removeProfileEvents(String profileId){
        Condition profileCondition = new Condition();
        profileCondition.setConditionType(definitionsService.getConditionType("eventPropertyCondition"));
        profileCondition.setParameter("propertyName", "profileId");
        profileCondition.setParameter("comparisonOperator", "equals");
        profileCondition.setParameter("propertyValue", profileId);

        persistenceService.removeByQuery(profileCondition,Event.class);
    }

    @Override
    public void deleteEvent(String eventIdentifier) {
        persistenceService.remove(eventIdentifier, Event.class);
    }
}
