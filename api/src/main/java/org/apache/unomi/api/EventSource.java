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
 * TODO: REMOVE
 * Describes where an event originated in the digital experience.
 * Identifies the scope, item id, path, and type of the source page or item
 * that triggered the event. Clients attach this information when they send
 * events to Unomi.
 */
public class EventSource {
    private String scope;
    private String id;
    private String path;
    private String type;

    /**
     * Constructs a new EventSource instance.
     */
    public EventSource() {
    }

    /**
     * Returns the scope associated with this event source.
     * @return The scope string.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope for this event source.
     * @param scope the new scope to set.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Returns the unique identifier of this event source.
     * @return The ID string.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier for this event source.
     * @param id the new ID to set.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the path associated with this event source.
     * @return The path string.
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the path for this event source.
     * @param path the new path to set.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Returns the type identifier of this event source.
     * @return The type string.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of this event source.
     * This value can be used to classify the data structure or content
     * represented by the event source, such as "string", "array", or "boolean".
     * @param type The string representation of the desired type.
     */
    public void setType(String type) {
        this.type = type;
    }
}
