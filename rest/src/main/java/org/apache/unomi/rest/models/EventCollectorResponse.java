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

package org.apache.unomi.rest.models;

import org.apache.unomi.tracing.api.TraceNode;
import java.io.Serializable;

/**
 * Response model for the event collector endpoint.
 * This class provides information about the result of event processing, including
 * which entities were updated and tracing information.
 */
public class EventCollectorResponse implements Serializable {
    /**
     * A bitwise combination of EventService flags indicating what was updated during event processing.
     * The value is composed of the following flags:
     * <ul>
     *   <li>0 = NO_CHANGE - No changes occurred</li>
     *   <li>1 = ERROR - An error occurred during processing</li>
     *   <li>2 = SESSION_UPDATED - The associated session was updated</li>
     *   <li>4 = PROFILE_UPDATED - The associated profile was updated</li>
     * </ul>
     * Multiple flags can be combined, for example:
     * <ul>
     *   <li>6 = SESSION_UPDATED (2) + PROFILE_UPDATED (4) - Both session and profile were updated</li>
     * </ul>
     */
    private int updated;

    /**
     * Contains tracing information about the request processing.
     * This can be used for debugging and monitoring purposes.
     */
    private TraceNode requestTracing;

    public EventCollectorResponse() {
    }

    /**
     * Creates a new EventCollectorResponse with the specified update flags.
     *
     * @param updated The bitwise combination of EventService flags indicating what was updated
     */
    public EventCollectorResponse(int updated) {
        this.updated = updated;
    }

    /**
     * Sets the update flags indicating what was modified during event processing.
     *
     * @param updated The bitwise combination of EventService flags
     */
    public void setUpdated(int updated) {
        this.updated = updated;
    }

    /**
     * Gets the update flags indicating what was modified during event processing.
     *
     * @return The bitwise combination of EventService flags
     */
    public int getUpdated() {
        return updated;
    }

    /**
     * Gets the tracing information for the request.
     *
     * @return The TraceNode containing request tracing information
     */
    public TraceNode getRequestTracing() {
        return requestTracing;
    }

    /**
     * Sets the tracing information for the request.
     *
     * @param requestTracing The TraceNode containing request tracing information
     */
    public void setRequestTracing(TraceNode requestTracing) {
        this.requestTracing = requestTracing;
    }
}
