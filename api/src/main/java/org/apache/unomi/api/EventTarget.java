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
 * Subject of an {@link Event} — what the visitor interacted with.
 * Stores the target item id and type plus optional properties (for example the
 * product or content element clicked). Together with {@link EventSource} it
 * gives rules enough context to personalize, segment, and report on behavior.
 */
public class EventTarget implements Serializable {
    private static final long serialVersionUID = 6370790894348364803L;
    private String id;
    private String type;
    private Map<String, Object> properties;

    /**
     * Default constructor.
     */
    public EventTarget() {
    }

    /**
     * Creates an event target with the given identifier and type.
     *
     * @param id the unique identifier of the event target
     * @param type the type associated with this event target
     */
    public EventTarget(String id, String type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Unique identifier of this event target.
     *
     * @return the event target id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this event target.
     *
     * @param id the event target id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Type label for this event target.
     *
     * @return the event target type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type label for this event target.
     *
     * @param type the event target type
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Arbitrary properties associated with this event target.
     *
     * @return the property map
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the arbitrary properties for this event target.
     *
     * @param properties the property map
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
