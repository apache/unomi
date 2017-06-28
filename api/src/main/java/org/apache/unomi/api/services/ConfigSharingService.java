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
 * A service to share configuration properties with other bundles. It also support listeners that will be called whenever
 * a property is added/updated/removed. Simply register a service with the @link ConfigSharingServiceConfigChangeListener interface and it will
 * be automatically picked up.
 */
public interface ConfigSharingService {

    Object getProperty(String name);
    Object setProperty(String name, Object value);
    boolean hasProperty(String name);
    Object removeProperty(String name);
    Set<String> getPropertyNames();

    class ConfigChangeEvent {
        public enum ConfigChangeEventType { ADDED, UPDATED, REMOVED };
        private ConfigChangeEventType eventType;
        private String name;
        private Object oldValue;
        private Object newValue;

        public ConfigChangeEvent(ConfigChangeEventType eventType, String name, Object oldValue, Object newValue) {
            this.eventType = eventType;
            this.name = name;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public ConfigChangeEventType getEventType() {
            return eventType;
        }

        public String getName() {
            return name;
        }

        public Object getOldValue() {
            return oldValue;
        }

        public Object getNewValue() {
            return newValue;
        }
    }

    interface ConfigChangeListener {
        void configChanged(ConfigChangeEvent configChangeEvent);
    }

}
