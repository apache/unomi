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
 * Origin of an {@link Event} in the visitor journey.
 * Captures scope, item id, path, and type for the page or asset that produced
 * the event. Client trackers populate this object when posting to the events
 * collector so rules and analytics can attribute actions to a source context.
 */
public class EventSource {
    private String scope;
    private String id;
    private String path;
    private String type;

    /**
     * Creates an empty event source.
     */
    public EventSource() {
    }

    /**
     * Scope of the page or asset that originated the event.
     *
     * @return scope name
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the source scope.
     *
     * @param scope scope name
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Identifier of the originating page or asset within the scope.
     *
     * @return source item id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the source item id.
     *
     * @param id source item id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * URL or logical path of the page where the event was triggered.
     *
     * @return source path
     */
    public String getPath() {
        return path;
    }

    /**
     * Sets the source path.
     *
     * @param path source path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Type label for the source object (for example page, form, or product).
     *
     * @return source type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the source type label.
     *
     * @param type source type
     */
    public void setType(String type) {
        this.type = type;
    }
}
