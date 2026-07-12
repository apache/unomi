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

/**
 * Summary of one event type known to the server.
 * Pairs an event type name with an occurrence count, for example when
 * {@link ServerInfo} lists which events the running instance supports.
 */
public class EventInfo {

    private String name;
    private Long occurences;

    /**
     * Constructs a new {@code EventInfo} object.
     */
    public EventInfo() {
    }

    /**
     * Returns the name of the event.
     * @return The event name as a {@link String}.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the event.
     * @param name The name to set for the event.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the number of occurrences for this event type.
     * @return The count of occurrences as a {@link Long}.
     */
    public Long getOccurences() {
        return occurences;
    }

    /**
     * Sets the number of occurrences for this event type.
     * @param occurences The count of occurrences to set.
     */
    public void setOccurences(Long occurences) {
        this.occurences = occurences;
    }
}
