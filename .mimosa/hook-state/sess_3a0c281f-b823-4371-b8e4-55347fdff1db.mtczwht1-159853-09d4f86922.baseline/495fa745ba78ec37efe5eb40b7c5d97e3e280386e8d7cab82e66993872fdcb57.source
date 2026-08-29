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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Capability and build information exposed by a running Unomi node.
 * Populated at startup and returned by health and cluster APIs so clients,
 * operators, and other nodes can read version, build metadata, supported
 * {@link EventInfo} types, optional capability flags, and banner logo lines.
 */
public class ServerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** @api.example unomi */
    private String serverIdentifier;
    /** @api.example 3.1.0-SNAPSHOT */
    private String serverVersion;
    /** @api.example 1 */
    private String serverBuildNumber;
    private Date serverBuildDate;
    private String serverTimestamp;
    private String serverScmBranch;

    private List<EventInfo> eventTypes;
    private Map<String,String> capabilities;

    private List<String> logoLines = new ArrayList<>();

    /**
     * Creates an empty server info record.
     */
    public ServerInfo() {
    }

    /**
     * Unique identifier for this server instance.
     *
     * @return server identifier
     * @api.example unomi
     */
    public String getServerIdentifier() {
        return serverIdentifier;
    }

    /**
     * Sets the server identifier.
     *
     * @param serverIdentifier server identifier
     */
    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }

    /**
     * Running Unomi version string.
     *
     * @return server version
     * @api.example 3.1.0-SNAPSHOT
     */
    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * Sets the server version.
     *
     * @param serverVersion server version
     */
    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    /**
     * Build number of this server distribution.
     *
     * @return build number
     */
    public String getServerBuildNumber() {
        return serverBuildNumber;
    }

    /**
     * Sets the build number.
     *
     * @param serverBuildNumber build number
     */
    public void setServerBuildNumber(String serverBuildNumber) {
        this.serverBuildNumber = serverBuildNumber;
    }

    /**
     * When this server build was produced.
     *
     * @return build date
     */
    public Date getServerBuildDate() {
        return serverBuildDate;
    }

    /**
     * Sets the build date.
     *
     * @param serverBuildDate build date
     */
    public void setServerBuildDate(Date serverBuildDate) {
        this.serverBuildDate = serverBuildDate;
    }

    /**
     * Server timestamp string (for example {@code YYYYMMDDHHmmss}).
     *
     * @return server timestamp
     */
    public String getServerTimestamp() {
        return serverTimestamp;
    }

    /**
     * Sets the server timestamp.
     *
     * @param serverTimestamp server timestamp
     */
    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    /**
     * Source control branch this build came from.
     *
     * @return SCM branch name
     */
    public String getServerScmBranch() {
        return serverScmBranch;
    }

    /**
     * Sets the SCM branch name.
     *
     * @param serverScmBranch SCM branch name
     */
    public void setServerScmBranch(String serverScmBranch) {
        this.serverScmBranch = serverScmBranch;
    }

    /**
     * Event types this server instance supports, with occurrence counts.
     *
     * @return supported event types
     */
    public List<EventInfo> getEventTypes() {
        return eventTypes;
    }

    /**
     * Sets the supported event types.
     *
     * @param eventTypes event types
     */
    public void setEventTypes(List<EventInfo> eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * Optional capability flags reported by the server.
     *
     * @return map of capability name to description
     */
    public Map<String, String> getCapabilities() {
        return capabilities;
    }

    /**
     * Sets the capability map.
     *
     * @param capabilities capability name to description
     */
    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * ASCII art lines shown in the server logo banner.
     *
     * @return logo lines
     */
    public List<String> getLogoLines() {
        return logoLines;
    }

    /**
     * Sets the logo banner lines.
     *
     * @param logoLines logo lines
     */
    public void setLogoLines(List<String> logoLines) {
        this.logoLines = logoLines;
    }
}
