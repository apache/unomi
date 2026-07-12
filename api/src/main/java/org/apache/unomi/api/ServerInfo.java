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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * High-level description of a running Unomi instance.
 * Exposes version, build, SCM branch, timestamp, and the list of event types
 * the server can handle. Clients call this to discover server capabilities.
 */
public class ServerInfo {

    private String serverIdentifier;
    private String serverVersion;
    private String serverBuildNumber;
    private Date serverBuildDate;
    private String serverTimestamp;
    private String serverScmBranch;

    private List<EventInfo> eventTypes;
    private Map<String,String> capabilities;

    private List<String> logoLines = new ArrayList<>();

    /**
     * Constructs a new ServerInfo instance.
     */
    public ServerInfo() {
    }

    /**
     * Returns the unique identifier of the server.
     * @return The server's identifier string.
     */
    public String getServerIdentifier() {
        return serverIdentifier;
    }

    /**
     * Sets the unique identifier for this server information.
     * @param serverIdentifier The unique identifier to set.
     */
    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }

    /**
     * Returns the version string of the server.
     * @return The server's version number.
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Sets the version string for this server information.
     * @param serverVersion The version string to set.
     */
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * Returns the build number of the server.
     * @return The server's build number as a string.
     */
    public String getServerBuildNumber() {
        return serverBuildNumber;
    }

    /**
     * Sets the build number for this server information.
     * @param serverBuildNumber The build number to set.
     */
    public void setServerBuildNumber(String serverBuildNumber) {
        this.serverBuildNumber = serverBuildNumber;
    }

    /**
     * Returns the date associated with the server's build.
     * @return The {@link java.util.Date} representing the build date.
     */
    public Date getServerBuildDate() {
        return serverBuildDate;
    }

    /**
     * Sets the build date of the server.
     * @param serverBuildDate the build date of the server
     */
    public void setServerBuildDate(Date serverBuildDate) {
        this.serverBuildDate = serverBuildDate;
    }

    /**
     * Retrieves the timestamp associated with the server information.
     * @return the server's timestamp string
     */
    public String getServerTimestamp() {
        return serverTimestamp;
    }

    /**
     * Sets the timestamp for the server information.
     * @param serverTimestamp the server's timestamp (e.g., YYYYMMDDHHmmss)
     */
    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    /**
     * Retrieves the source control management branch name of the server.
     * @return the SCM branch name
     */
    public String getServerScmBranch() {
        return serverScmBranch;
    }

    /**
     * Sets the source control management branch name for
     * the server information.
     * @param serverScmBranch the SCM branch name
     */
    public void setServerScmBranch(String serverScmBranch) {
        this.serverScmBranch = serverScmBranch;
    }

    /**
     * Retrieves the list of event types supported by this server instance.
     * @return a list of {@link EventInfo} objects representing
     * available event types
     */
    public List<EventInfo> getEventTypes() {
        return eventTypes;
    }

    /**
     * Sets the collection of event types associated with
     * the server information.
     * @param eventTypes the list of event types to set
     */
    public void setEventTypes(List<EventInfo> eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Retrieves a map containing various capabilities reported by the server.
     * @return a map where keys are capability names and values are descriptions
     */
    public Map<String, String> getCapabilities() {
        return capabilities;
    }

    /**
     * Sets the map containing various capabilities reported
     * by the Unomi server.
     * @param capabilities The map of capability names and descriptions.
     */
    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Retrieves the list of strings used to display the server's logo lines.
     * @return The {@link java.util.List<java.lang.String>}
     * containing the logo lines.
     */
    public List<String> getLogoLines() {
        return logoLines;
    }

    /**
     * Sets the list of strings that represent the server's logo lines
     * for display purposes.
     * @param logoLines The list of strings defining the logo lines.
     */
    public void setLogoLines(List<String> logoLines) {
        this.logoLines = logoLines;
    }
}
