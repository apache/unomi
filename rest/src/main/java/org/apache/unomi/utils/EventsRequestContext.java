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
package org.apache.unomi.utils;

import org.apache.unomi.api.Profile;
import org.apache.unomi.api.Session;
import org.apache.unomi.api.services.EventService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;

/**
 * This is a bean that maintain the current situation during a request that contains events to be processed.
 * It's in charge to hold an up to date Session + Profile for the current request, but also the status of the events executions:
 * - changes
 * - number of events processed
 */
public class EventsRequestContext {

    private Date timestamp;
    private Profile profile;
    private Session session;

    private boolean newSession = false;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private int changes;
    private int totalItems;
    private int processedItems;

    private EventsRequestContext() {
    }

    /**
     * Creates a request context for event processing.
     *
     * @param timestamp the request timestamp
     * @param profile the current profile
     * @param session the current session
     * @param request the HTTP request
     * @param response the HTTP response
     */
    public EventsRequestContext(Date timestamp, Profile profile, Session session, HttpServletRequest request, HttpServletResponse response) {
        this.timestamp = timestamp;
        this.profile = profile;
        this.session = session;
        this.request = request;
        this.response = response;
        this.changes = EventService.NO_CHANGE;
        this.totalItems = 0;
        this.processedItems = 0;
    }

    /**
     * Returns the request timestamp.
     *
     * @return the request timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the request timestamp.
     *
     * @param timestamp the request timestamp
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the current profile.
     *
     * @return the current profile
     */
    public Profile getProfile() {
        return profile;
    }

    /**
     * Sets the current profile.
     *
     * @param profile the current profile
     */
    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    /**
     * Returns the current session.
     *
     * @return the current session
     */
    public Session getSession() {
        return session;
    }

    /**
     * Sets the current session.
     *
     * @param session the current session
     */
    public void setSession(Session session) {
        this.session = session;
    }

    /**
     * Returns whether a new session was created for this request.
     *
     * @return {@code true} when a new session was created
     */
    public boolean isNewSession() {
        return newSession;
    }

    /**
     * Sets whether a new session was created for this request.
     *
     * @param newSession {@code true} when a new session was created
     */
    public void setNewSession(boolean newSession) {
        this.newSession = newSession;
    }

    /**
     * Returns the accumulated event-processing change flags.
     *
     * @return the bitwise change flags
     */
    public int getChanges() {
        return changes;
    }

    /**
     * Adds event-processing change flags.
     *
     * @param changes the flags to add
     */
    public void addChanges(int changes) {
        this.changes |= changes;
    }

    /**
     * Returns the total number of events in the request.
     *
     * @return the total event count
     */
    public int getTotalItems() {
        return totalItems;
    }

    /**
     * Sets the total number of events in the request.
     *
     * @param totalItems the total event count
     */
    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    /**
     * Returns the number of events processed so far.
     *
     * @return the processed event count
     */
    public int getProcessedItems() {
        return processedItems;
    }

    /**
     * Sets the number of events processed so far.
     *
     * @param processedItems the processed event count
     */
    public void setProcessedItems(int processedItems) {
        this.processedItems = processedItems;
    }

    /**
     * Returns the HTTP request.
     *
     * @return the HTTP request
     */
    public HttpServletRequest getRequest() {
        return request;
    }

    /**
     * Sets the HTTP request.
     *
     * @param request the HTTP request
     */
    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    /**
     * Returns the HTTP response.
     *
     * @return the HTTP response
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * Sets the HTTP response.
     *
     * @param response the HTTP response
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }
}
