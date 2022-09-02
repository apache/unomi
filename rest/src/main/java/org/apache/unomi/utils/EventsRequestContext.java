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

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public boolean isNewSession() {
        return newSession;
    }

    public void setNewSession(boolean newSession) {
        this.newSession = newSession;
    }

    public int getChanges() {
        return changes;
    }

    public void addChanges(int changes) {
        this.changes |= changes;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public int getProcessedItems() {
        return processedItems;
    }

    public void setProcessedItems(int processedItems) {
        this.processedItems = processedItems;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public void setRequest(HttpServletRequest request) {
        this.request = request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }
}
