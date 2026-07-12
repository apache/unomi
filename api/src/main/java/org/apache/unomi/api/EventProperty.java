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

/**
 * Named property attached to an {@link Event}.
 * Events carry many properties; this type represents one key/value pair
 * stored with the event for segmentation, personalization, and reporting.
 */
public class EventProperty implements Serializable {

    private static final long serialVersionUID = -6727761503135013816L;

    private String id;

    private String valueType = "string";

    /**
     * Default constructor.
     */
    public EventProperty() {
        super();
    }

    /**
     * Creates an event property with the default string value type.
     *
     * @param id the property identifier
     */
    public EventProperty(String id) {
        this(id, null);
    }

    /**
     * Creates an event property with the given identifier and value type.
     *
     * @param id   the property identifier
     * @param type the value type for this property
     */
    public EventProperty(String id, String type) {
        this();
        this.id = id;
        if (type != null) {
            this.valueType = type;
        }
    }

    /**
     * Property identifier.
     *
     * @return the property id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the property identifier.
     *
     * @param id the property id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Value type for this property (for example {@code string} or {@code integer}).
     *
     * @return the value type id
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * Sets the value type.
     *
     * @param type the value type id
     */
    public void setValueType(String type) {
        this.valueType = type;
    }

}
