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
package org.apache.unomi.api.services;

import java.util.Set;

/**
 * OSGi service for sharing runtime configuration properties across Unomi bundles.
 * <p>
 * Bundles that own configuration (for example {@code web-servlets} for cookie settings or
 * {@code router-core} for import paths) publish values with {@link #setProperty(String, Object)}.
 * Other bundles read them with {@link #getProperty(String)} without taking a direct dependency on
 * the publishing module. Initial properties may also be seeded at startup (for example
 * {@code internalServerAddress} from cluster configuration).
 * <p>
 * Property changes notify registered {@link ConfigChangeListener} OSGi services. Register a
 * component implementing {@link ConfigChangeListener} to react to {@link ConfigChangeEvent}s
 * when properties are added, updated, or removed.
 */
public interface ConfigSharingService {

    /**
     * Retrieves the value of the named property.
     *
     * @param name the property name
     * @return the property value, or {@code null} if not set
     */
    Object getProperty(String name);

    /**
     * Sets the value of the named property and notifies registered listeners.
     *
     * @param name the property name
     * @param value the new value
     * @return the previous value, or {@code null} if the property was not previously set
     */
    Object setProperty(String name, Object value);

    /**
     * Determines whether the named property is currently set.
     *
     * @param name the property name
     * @return {@code true} if the property exists, {@code false} otherwise
     */
    boolean hasProperty(String name);

    /**
     * Removes the named property and notifies registered listeners.
     *
     * @param name the property name
     * @return the previous value, or {@code null} if the property was not set
     */
    Object removeProperty(String name);

    /**
     * Retrieves the names of all currently known properties.
     *
     * @return the property names
     */
    Set<String> getPropertyNames();

    /**
     * Event describing a configuration property change.
     */
    class ConfigChangeEvent {
        /**
         * Types of configuration change events.
         */
        public enum ConfigChangeEventType {
            /** Property was added. */
            ADDED,
            /** Property was updated. */
            UPDATED,
            /** Property was removed. */
            REMOVED
        };
        private ConfigChangeEventType eventType;
        private String name;
        private Object oldValue;
        private Object newValue;

        /**
         * Instantiates a configuration change event.
         *
         * @param eventType the type of change
         * @param name the property name
         * @param oldValue the previous value, or {@code null} for {@link ConfigChangeEventType#ADDED} events
         * @param newValue the new value, or {@code null} for {@link ConfigChangeEventType#REMOVED} events
         */
        public ConfigChangeEvent(ConfigChangeEventType eventType, String name, Object oldValue, Object newValue) {
            this.eventType = eventType;
            this.name = name;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        /**
         * Retrieves the type of configuration change.
         *
         * @return the event type
         */
        public ConfigChangeEventType getEventType() {
            return eventType;
        }

        /**
         * Retrieves the name of the changed property.
         *
         * @return the property name
         */
        public String getName() {
            return name;
        }

        /**
         * Retrieves the previous value of the property.
         *
         * @return the old value, or {@code null} for {@link ConfigChangeEventType#ADDED} events
         */
        public Object getOldValue() {
            return oldValue;
        }

        /**
         * Retrieves the new value of the property.
         *
         * @return the new value, or {@code null} for {@link ConfigChangeEventType#REMOVED} events
         */
        public Object getNewValue() {
            return newValue;
        }
    }

    /**
     * Listener for configuration property changes.
     * <p>
     * Implementations are registered as OSGi services and invoked when properties are added,
     * updated, or removed through {@link ConfigSharingService}.
     */
    interface ConfigChangeListener {
        /**
         * Called when a configuration property changes.
         *
         * @param configChangeEvent the change event
         */
        void configChanged(ConfigChangeEvent configChangeEvent);
    }

}
