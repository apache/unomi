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

package org.oasis_open.contextserver.api.services;

import org.oasis_open.contextserver.api.Event;

/**
 * A service that gets notified (via {@link #onEvent(Event)}) whenever an event it can handle as decided by {@link #canHandle(Event)} occurs in the context server.
 */
public interface EventListenerService {

    /**
     * Whether or not this listener can handle the specified event.
     *
     * @param event the event to be handled
     * @return {@code true} if this listener can handle the specified event, {@code false} otherwise
     */
    boolean canHandle(Event event);

    /**
     * Handles the specified event.
     *
     * @param event the event to be handled
     * @return the result of the event handling as combination of {@link EventService} flags, to be checked using bitwise AND (&amp;) operator
     * @see EventService#NO_CHANGE
     * @see EventService#PROFILE_UPDATED
     * @see EventService#SESSION_UPDATED
     */
    int onEvent(Event event);

}
