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

package org.apache.unomi.services.services;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * Representation of a third party server, containing key, ip address, and allowed events
 */
public class ThirdPartyServer {
    private String id;

    private String key;

    private Set<InetAddress> ipAddresses;

    private Set<String> allowedEvents = new HashSet<>();

    public ThirdPartyServer(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public Set<InetAddress> getIpAddresses() {
        return ipAddresses;
    }

    public Set<String> getAllowedEvents() {
        return allowedEvents;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setIpAddresses(Set<InetAddress> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    public void setAllowedEvents(Set<String> allowedEvents) {
        this.allowedEvents = allowedEvents;
    }
}
