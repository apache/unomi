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

package org.apache.unomi.api;

import java.io.Serializable;
import java.util.Map;

/**
 * TODO: REMOVE
 * Describes what an event acted upon.
 * Holds the target item id and type plus optional properties, for example
 * the product or content element a user clicked. Used together with
 * {@link Event} to give rules and analytics full context.
 */
public class EventTarget implements Serializable {
    private static final long serialVersionUID = 6370790894348364803L;
    private String id;
    private String type;
    private Map<String, Object> properties;

    /**
     * Constructs a default {@code EventTarget}.
     */
    public EventTarget() {
    }

    /**
     * Constructs an {@code EventTarget} with the specified identifier and type.
     * @param id The unique identifier of the event target.
     * @param type The type associated with this event target.
     */
    public EventTarget(String id, String type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Returns the unique identifier of this event target.
     * @return The {@code String} ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this event target.
     * @param id The new {@code String} ID.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the type associated with this event target.
     * @return The {@code String} type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type associated with this event target.
     * @param type The new {@code String} type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns a map containing arbitrary properties associated
     * with this event target.
     * @return The {@link java.util.Map} of properties.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the arbitrary properties associated with this event target.
     * @param properties The {@link java.util.Map} of properties to set.
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EventTarget{");
        sb.append("id='").append(id).append('\'');
        sb.append(", type='").append(type).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
