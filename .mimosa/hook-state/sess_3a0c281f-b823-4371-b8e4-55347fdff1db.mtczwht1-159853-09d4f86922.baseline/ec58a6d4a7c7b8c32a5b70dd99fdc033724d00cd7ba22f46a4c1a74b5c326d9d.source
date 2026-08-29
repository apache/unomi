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
 * One event type entry in {@link ServerInfo#getEventTypes()}.
 * Pairs the event type name with how many matching events exist on the server,
 * giving operators and clients a quick view of which event types are active.
 */
public class EventInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private Long occurences;

    /**
     * Creates an empty event info record.
     */
    public EventInfo() {
    }

    /**
     * Event type name.
     *
     * @return event type name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the event type name.
     *
     * @param name event type name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Number of occurrences for this event type.
     *
     * @return occurrence count
     */
    public Long getOccurences() {
        return occurences;
    }

    /**
     * Sets the occurrence count for this event type.
     *
     * @param occurences occurrence count
     */
    public void setOccurences(Long occurences) {
        this.occurences = occurences;
    }
}
