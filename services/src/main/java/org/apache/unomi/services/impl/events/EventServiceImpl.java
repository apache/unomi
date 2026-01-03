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
import org.apache.unomi.api.actions.ActionPostExecutor;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.apache.unomi.api.services.TypeResolutionService;
import org.apache.unomi.api.tenants.Tenant;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.aggregate.TermsAggregate;
import org.apache.unomi.services.common.security.IPValidationUtils;
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
    private static final int MAX_RECURSION_DEPTH = 20;

    /**
     * Simple data class to hold event information for recursion tracking.
     * Focuses on data relevant to rule condition matching: event type, scope, and key properties.
     */
    private static class EventInfo {
        final String eventType;
        final String scope;
        final String propertyKeys;

        EventInfo(Event event) {
            this.eventType = event.getEventType();
            this.scope = event.getScope();

            // Collect property keys that might be used in conditions (limit to first 5 to avoid noise)
            Map<String, Object> properties = event.getProperties();
            if (properties != null && !properties.isEmpty()) {
                List<String> keys = new ArrayList<>(properties.keySet());
                int maxKeys = Math.min(5, keys.size());
                this.propertyKeys = keys.subList(0, maxKeys).toString();
            } else {
                this.propertyKeys = null;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Event{type=").append(eventType);
            if (scope != null) {
                sb.append(", scope=").append(scope);
            }
            if (propertyKeys != null) {
                sb.append(", properties=").append(propertyKeys);
            }
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * ThreadLocal to track event stack for event processing.
     * This ensures the full event chain is tracked consistently even when send() is called directly
     * from actions or other services, preventing infinite recursion and providing detailed
     * diagnostics when recursion limits are reached.
     */
    private static final ThreadLocal<List<EventInfo>> EVENT_STACK = ThreadLocal.withInitial(ArrayList::new);

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

    /**
     * Helper method to get TypeResolutionService from DefinitionsService.
     * Returns null if DefinitionsService is not available or doesn't have TypeResolutionService.
     */
    private TypeResolutionService getTypeResolutionService() {
        return definitionsService != null ? definitionsService.getTypeResolutionService() : null;
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

        // Get current event stack from ThreadLocal
        List<EventInfo> eventStack = EVENT_STACK.get();

        // Check depth before processing (matches original: if (depth > MAX_RECURSION_DEPTH))
        // Original allowed depths 0-10 (11 calls), blocking at depth 11
        if (eventStack.size() > MAX_RECURSION_DEPTH) {
            EventInfo currentEventInfo = new EventInfo(event);
            tracer.trace("Max recursion depth reached for event: " + event.getEventType(), event.getItemId());

            // Build detailed error message with full event chain
            StringBuilder errorMsg = new StringBuilder("Max recursion depth reached (depth: ").append(eventStack.size() + 1)
                    .append(", max: ").append(MAX_RECURSION_DEPTH + 1)
                    .append("). Current event: ").append(currentEventInfo);

            if (!eventStack.isEmpty()) {
                errorMsg.append("\nEvent chain (oldest first):");
                for (int i = 0; i < eventStack.size(); i++) {
                    errorMsg.append("\n  [").append(i + 1).append("] ").append(eventStack.get(i));
                }
                errorMsg.append("\n  [").append(eventStack.size() + 1).append("] ").append(currentEventInfo).append(" <-- BLOCKED");
            }

            LOGGER.warn(errorMsg.toString());
            return NO_CHANGE;
        }

        // Add current event to stack
        EventInfo currentEventInfo = new EventInfo(event);
        eventStack.add(currentEventInfo);

        try {
            return sendInternal(event);
        } finally {
            // Remove current event from stack and cleanup ThreadLocal if empty
            eventStack.remove(eventStack.size() - 1);
            if (eventStack.isEmpty()) {
                EVENT_STACK.remove();
            }
        }
    }

    private int sendInternal(Event event) {
        RequestTracer tracer = tracerService.getCurrentTracer();

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
                    // Depth is automatically tracked via ThreadLocal, no need to pass parameter
                    changes |= send(profileUpdated);
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

    public Set<String> getEventTypeIds() {
        Map<String, Long> dynamicEventTypeIds = persistenceService.aggregateWithOptimizedQuery(null, new TermsAggregate("eventType"), Event.ITEM_TYPE);
        Set<String> eventTypeIds = new LinkedHashSet<String>(predefinedEventTypeIds);
        eventTypeIds.addAll(dynamicEventTypeIds.keySet());
        eventTypeIds.remove("_filtered");
        return eventTypeIds;
    }

    @Override
    public PartialList<Event> searchEvents(Condition condition, int offset, int size) {
        TypeResolutionService typeResolutionService = getTypeResolutionService();
        if (typeResolutionService != null) {
            typeResolutionService.resolveConditionType(condition, "event search");
        }
        // Note: Effective condition resolution happens in the query builder dispatcher or condition evaluator dispatcher
        // For in-memory persistence, the condition evaluator dispatcher will resolve the effective condition
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
        if (query.getCondition() != null) {
            definitionsService.getConditionValidationService().validate(query.getCondition());
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), Event.class, query.getOffset(), query.getLimit(), query.getScrollTimeValidity());
            }
        } else {
            // No condition - query without condition
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
