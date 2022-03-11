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
 * Basic information about a Unomi server
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

    public ServerInfo() {
    }

    public String getServerIdentifier() {
        return serverIdentifier;
    }

    public void setServerIdentifier(String serverIdentifier) {
        this.serverIdentifier = serverIdentifier;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public String getServerBuildNumber() {
        return serverBuildNumber;
    }

    public void setServerBuildNumber(String serverBuildNumber) {
        this.serverBuildNumber = serverBuildNumber;
    }

    public Date getServerBuildDate() {
        return serverBuildDate;
    }

    public void setServerBuildDate(Date serverBuildDate) {
        this.serverBuildDate = serverBuildDate;
    }

    public String getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }

    public String getServerScmBranch() {
        return serverScmBranch;
    }

    public void setServerScmBranch(String serverScmBranch) {
        this.serverScmBranch = serverScmBranch;
    }

    public List<EventInfo> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<EventInfo> eventTypes) {
        this.eventTypes = eventTypes;
    }

    public Map<String, String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, String> capabilities) {
        this.capabilities = capabilities;
    }

    public List<String> getLogoLines() {
        return logoLines;
    }

    public void setLogoLines(List<String> logoLines) {
        this.logoLines = logoLines;
    }
}
