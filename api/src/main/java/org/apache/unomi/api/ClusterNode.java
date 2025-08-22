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

/**
 * Information about a cluster node.
 */
public class ClusterNode extends Item {

    private static final long serialVersionUID = 1281422346318230514L;

    public static final String ITEM_TYPE = "clusterNode";

    private double cpuLoad;
    private double[] loadAverage;
    private String publicHostAddress;
    private String internalHostAddress;
    private long uptime;
    private boolean master;
    private boolean data;
    private long startTime;
    private long lastHeartbeat;

    // Server information
    private ServerInfo serverInfo;

    /**
     * Instantiates a new Cluster node.
     */
    public ClusterNode() {
        super();
        setItemType(ITEM_TYPE);
    }

    /**
     * Retrieves the cpu load.
     *
     * @return the cpu load
     */
    public double getCpuLoad() {
        return cpuLoad;
    }

    /**
     * Sets the cpu load.
     *
     * @param cpuLoad the cpu load
     */
    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    /**
     * Retrieves the public host address.
     *
     * @return the public host address
     */
    public String getPublicHostAddress() {
        return publicHostAddress;
    }

    /**
     * Sets the public host address.
     *
     * @param publicHostAddress the public host address
     */
    public void setPublicHostAddress(String publicHostAddress) {
        this.publicHostAddress = publicHostAddress;
    }

    /**
     * Retrieves the internal host address which uses the HTTP/HTTPS protocol for communications between clients and the context server.
     *
     * @return the internal host address
     */
    public String getInternalHostAddress() {
        return internalHostAddress;
    }

    /**
     * Sets the internal host address which uses the HTTP/HTTPS protocol for communications between clients and the context server.
     *
     * @param internalHostAddress the internal host address
     */
    public void setInternalHostAddress(String internalHostAddress) {
        this.internalHostAddress = internalHostAddress;
    }

    /**
     * Retrieves the load average for the last minute, five minutes and fifteen minutes.
     *
     * @return an array of {@code double} containing, in order and starting from index {@code 0}, the load average for the last minute, last five minutes and last fifteen minutes
     */
    public double[] getLoadAverage() {
        return loadAverage;
    }

    /**
     * Sets the load average for the last minute, five minutes and fifteen minutes.
     *
     * @param loadAverage an array of {@code double} containing, in order and starting from index {@code 0}, the load average for the last minute, last five minutes and last fifteen minutes
     */
    public void setLoadAverage(double[] loadAverage) {
        this.loadAverage = loadAverage;
    }

    /**
     * Retrieves the uptime.
     *
     * @return the uptime
     */
    public long getUptime() {
        return uptime;
    }

    /**
     * Sets the uptime.
     *
     * @param uptime the uptime
     */
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    /**
     * Determines whether this ClusterNode is a master node, i.e. this node doesn't store any data but is only focused on cluster management operations.
     *
     * @return {@code true} if this node is a master node, {@code false} otherwise
     */
    public boolean isMaster() {
        return master;
    }

    /**
     * Specifies whether this ClusterNode is a master node, i.e. this node doesn't store any data but is only focused on cluster management operations..
     *
     * @param master {@code true} if this node is a master node, {@code false} otherwise
     */
    public void setMaster(boolean master) {
        this.master = master;
    }

    /**
     * Determines whether this ClusterNode locally stores data.
     *
     * @return {@code true} if this node locally stores data, {@code false} otherwise
     */
    public boolean isData() {
        return data;
    }

    /**
     * Specifies whether this ClusterNode locally stores data.
     *
     * @param data {@code true} if this node locally stores data, {@code false} otherwise
     */
    public void setData(boolean data) {
        this.data = data;
    }

    /**
     * Retrieves the node start time in milliseconds.
     *
     * @return the start time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Sets the node start time in milliseconds.
     *
     * @param startTime the start time
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * Retrieves the last heartbeat time in milliseconds.
     *
     * @return the last heartbeat time
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Sets the last heartbeat time in milliseconds.
     *
     * @param lastHeartbeat the last heartbeat time
     */
    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * Gets the server information.
     *
     * @return the server information
     */
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * Sets the server information.
     *
     * @param serverInfo the server information
     */
    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }
}
