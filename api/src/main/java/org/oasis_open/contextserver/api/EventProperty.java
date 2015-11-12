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
package org.oasis_open.contextserver.api;

import java.io.Serializable;

/**
 * An event property.
 *
 * @author Sergiy Shyrkov
 */
public class EventProperty implements Serializable {

    private static final long serialVersionUID = -6727761503135013816L;

    private String id;

    private String valueType = "string";

    /**
     * Initializes an instance of this class.
     */
    public EventProperty() {
        super();
    }

    /**
     * Initializes an instance of an event property with the string value type.
     *
     * @param id the event property id
     */
    public EventProperty(String id) {
        this(id, null);
    }

    /**
     * Initializes an instance of this class.
     *
     * @param id   the event property id
     * @param type the type of the value for this property
     */
    public EventProperty(String id, String type) {
        this();
        this.id = id;
        if (type != null) {
            this.valueType = type;
        }
    }

    /**
     * Retrieves the identifier for this EventProperty.
     *
     * @return the identifier for this EventProperty
     */
    public String getId() {
        return id;
    }

    /**
     * Retrieves the type.
     *
     * @return the value type
     */
    public String getValueType() {
        return valueType;
    }

    /**
     * Sets the identifier.
     *
     * @param id the id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Sets the value type.
     *
     * @param type the type
     */
    public void setValueType(String type) {
        this.valueType = type;
    }

}
