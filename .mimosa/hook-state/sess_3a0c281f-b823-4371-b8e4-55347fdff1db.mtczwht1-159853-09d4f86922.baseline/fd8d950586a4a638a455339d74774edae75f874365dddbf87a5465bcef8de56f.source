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
package org.apache.unomi.graphql.schema;

import org.apache.unomi.api.Event;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.EventListenerService;
import org.apache.unomi.api.services.EventService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for property type change events and invalidates the corresponding tenant GraphQL schemas.
 */
@Component(service = EventListenerService.class)
public class TenantSchemaInvalidator implements EventListenerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantSchemaInvalidator.class);
    
    // Define event types for property changes
    private static final String PROPERTY_TYPE_EVENT_TYPE = "propertyType";
    private static final String PROPERTY_TYPES_EVENT_TYPE = "propertyTypes";

    private GraphQLSchemaUpdater schemaUpdater;

    @Reference
    public void setSchemaUpdater(GraphQLSchemaUpdater schemaUpdater) {
        this.schemaUpdater = schemaUpdater;
    }

    @Override
    public boolean canHandle(Event event) {
        return PROPERTY_TYPE_EVENT_TYPE.equals(event.getEventType()) ||
               PROPERTY_TYPES_EVENT_TYPE.equals(event.getEventType());
    }

    @Override
    public int onEvent(Event event) {
        LOGGER.debug("Property type event received: {}", event.getEventType());
        
        // Extract tenant ID from the event
        String tenantId = event.getScope();
        
        if (tenantId == null) {
            // If no tenant ID in scope, try to get it from the property type
            if (event.getProperties().containsKey("propertyType")) {
                PropertyType propertyType = (PropertyType) event.getProperties().get("propertyType");
                if (propertyType != null && propertyType.getTenantId() != null) {
                    tenantId = propertyType.getTenantId();
                }
            }
        }
        
        if (tenantId != null) {
            // Invalidate the tenant schema
            LOGGER.info("Invalidating GraphQL schema for tenant {} due to property type change", tenantId);
            schemaUpdater.invalidateTenantSchema(tenantId);
        } else {
            // If we can't determine the tenant, invalidate all schemas
            LOGGER.info("Invalidating all GraphQL schemas due to property type change");
            schemaUpdater.updateSchema();
        }
        
        // Return NO_CHANGE as we don't modify profiles or sessions
        return EventService.NO_CHANGE;
    }
} 