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

import org.apache.unomi.api.Event;
import org.apache.unomi.api.EventType;
import org.apache.unomi.api.GeoPoint;
import org.apache.unomi.api.PluginType;
import org.apache.unomi.api.PropertyType;
import org.apache.unomi.api.services.EventTypeRegistry;
import org.apache.unomi.persistence.spi.CustomObjectMapper;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventTypeRegistryImpl implements EventTypeRegistry, SynchronousBundleListener {

    private static final Logger logger = LoggerFactory.getLogger(EventTypeRegistryImpl.class.getName());

    private Map<Long, List<PluginType>> pluginTypes = new HashMap<>();

    private Map<String, EventType> eventTypes = new LinkedHashMap<>();

    private BundleContext bundleContext;

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }

    public void init() {
        processBundleStartup(bundleContext);

        // process already started bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }

        bundleContext.addBundleListener(this);
        logger.info("Event registry initialized.");
    }

    public void destroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Event registry shutdown.");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public EventType get(String typeName) {
        return eventTypes.get(typeName);
    }

    public void register(EventType eventType) {
        eventTypes.put(eventType.getType(), eventType);
    }

    @Override
    public boolean isValid(Event event) {
        if (event == null) {
            return false;
        }
        final EventType eventType = this.get(event.getEventType());
        if (eventType == null) {
            return false;
        }

        return areAllPropertiesValid(event.getProperties(), eventType.getPropertyTypes());
    }

    /**
     * Checks that all properties from map are defined in the property type set.
     * Does not require all defined properties to be present in map.
     *
     * @param props map of properties to validate
     * @param types set of a predefined event type properties
     * @return boolean result of validation
     */
    private boolean areAllPropertiesValid(Map<String, Object> props, Set<PropertyType> types) {
        if (props == null || props.isEmpty() || types == null || types.isEmpty()) {
            return true;
        }
        return props.entrySet().stream().allMatch(entry -> {
            return types.stream().anyMatch(type -> {
                if (!type.getItemId().equals(entry.getKey())) {
                    return false;
                }
                final Set<PropertyType> childTypes = type.getChildPropertyTypes();
                if (childTypes.size() > 0 && entry.getValue() != null) {
                    try {
                        final Map<String, Object> childProps = (Map<String, Object>) entry.getValue();
                        return areAllPropertiesValid(childProps, childTypes);
                    } catch (ClassCastException e) {
                        logger.error("Event property '{}' value is invalid: {}", entry.getKey(), e.getCause());
                        return false;
                    }
                } else {
                    return testValueType(entry.getValue(), type.getValueTypeId());
                }
            });
        });
    }

    private boolean testValueType(final Object value, final String valueTypeId) {
        switch (valueTypeId) {
            case "integer":
                return value instanceof Integer;
            case "long":
                return value instanceof Long;
            case "float":
                return value instanceof Double;
            case "set":
            case "json":
                return value instanceof Map;
            case "geopoint":
                return value instanceof GeoPoint;
            case "date":
                return value instanceof Date;
            case "boolean":
                return value instanceof Boolean;
            case "id":
            case "string":
                return value instanceof String;
            default:
                // return true if type is unknown cuz it may be custom
                return true;
        }
    }

    public Collection<EventType> getAll() {
        return this.eventTypes.values();
    }

    private void loadPredefinedEventTypes(BundleContext bundleContext) {
        Enumeration<URL> predefinedEventTypes = bundleContext.getBundle().findEntries("META-INF/cxs/events", "*.json", true);
        if (predefinedEventTypes == null) {
            return;
        }
        ArrayList<PluginType> pluginTypeArrayList = (ArrayList<PluginType>) pluginTypes.get(bundleContext.getBundle().getBundleId());

        while (predefinedEventTypes.hasMoreElements()) {
            URL predefinedEventTypeURL = predefinedEventTypes.nextElement();
            logger.debug("Found predefined event type at " + predefinedEventTypeURL + ", loading... ");

            try {
                EventType eventType = CustomObjectMapper.getObjectMapper().readValue(predefinedEventTypeURL, EventType.class);
                eventType.setPluginId(bundleContext.getBundle().getBundleId());
                register(eventType);
                pluginTypeArrayList.add(eventType);
            } catch (Exception e) {
                logger.error("Error while loading event type definition " + predefinedEventTypeURL, e);
            }
        }

    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        pluginTypes.put(bundleContext.getBundle().getBundleId(), new ArrayList<PluginType>());
        loadPredefinedEventTypes(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        List<PluginType> types = pluginTypes.remove(bundleContext.getBundle().getBundleId());
        if (types != null) {
            for (PluginType type : types) {
                if (type instanceof EventType) {
                    EventType eventType = (EventType) type;
                    eventTypes.remove(eventType.getType());
                }
            }
        }
    }
}
