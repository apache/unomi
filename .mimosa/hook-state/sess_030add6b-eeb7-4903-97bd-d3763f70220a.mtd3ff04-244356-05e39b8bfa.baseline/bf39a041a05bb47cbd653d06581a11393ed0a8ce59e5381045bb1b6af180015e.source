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
 * Runtime snapshot of one Apache Unomi node in a cluster.
 * Stores health and topology flags such as load averages, addresses, uptime,
 * and whether the node is a master (coordination only) or data node (stores
 * context data). Cluster services publish these objects so operators can
 * inspect cluster state.
 */
public class ClusterNode extends Item {

    private static final long serialVersionUID = 1281422346318230514L;

    /** Item type identifier for cluster nodes. */
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
     * Creates an empty cluster node with item type {@link #ITEM_TYPE}.
     */
    public ClusterNode() {
        super();
        setItemType(ITEM_TYPE);
    }

    /**
     * Current CPU load on this node.
     *
     * @return CPU load
     */
    public double getCpuLoad() {
        return cpuLoad;
    }

    /**
     * Sets the CPU load.
     *
     * @param cpuLoad CPU load
     */
    public void setCpuLoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    /**
     * Public host address clients use to reach this node.
     *
     * @return public host address
     */
    public String getPublicHostAddress() {
        return publicHostAddress;
    }

    /**
     * Sets the public host address.
     *
     * @param publicHostAddress public host address
     */
    public void setPublicHostAddress(String publicHostAddress) {
        this.publicHostAddress = publicHostAddress;
    }

    /**
     * Internal HTTP/HTTPS address used for client-to-server communication.
     *
     * @return internal host address
     */
    public String getInternalHostAddress() {
        return internalHostAddress;
    }

    /**
     * Sets the internal HTTP/HTTPS host address.
     *
     * @param internalHostAddress internal host address
     */
    public void setInternalHostAddress(String internalHostAddress) {
        this.internalHostAddress = internalHostAddress;
    }

    /**
     * Load averages for the last 1, 5, and 15 minutes.
     *
     * @return three-element array: index 0 = 1 min, 1 = 5 min, 2 = 15 min
     */
    public double[] getLoadAverage() {
        return loadAverage;
    }

    /**
     * Sets load averages for the last 1, 5, and 15 minutes.
     *
     * @param loadAverage three-element array: index 0 = 1 min, 1 = 5 min, 2 = 15 min
     */
    public void setLoadAverage(double[] loadAverage) {
        this.loadAverage = loadAverage;
    }

    /**
     * Node uptime in milliseconds.
     *
     * @return uptime
     */
    public long getUptime() {
        return uptime;
    }

    /**
     * Sets the node uptime.
     *
     * @param uptime uptime in milliseconds
     */
    public void setUptime(long uptime) {
        this.uptime = uptime;
    }

    /**
     * Whether this node is a master (coordination only, no local context data).
     *
     * @return {@code true} if this is a master node
     */
    public boolean isMaster() {
        return master;
    }

    /**
     * Sets whether this node is a master (coordination only, no local context data).
     *
     * @param master {@code true} for a master node
     */
    public void setMaster(boolean master) {
        this.master = master;
    }

    /**
     * Whether this node stores context data locally.
     *
     * @return {@code true} if this is a data node
     */
    public boolean isData() {
        return data;
    }

    /**
     * Sets whether this node stores context data locally.
     *
     * @param data {@code true} for a data node
     */
    public void setData(boolean data) {
        this.data = data;
    }

    /**
     * When this node started (milliseconds since epoch).
     *
     * @return start time
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Sets the node start time.
     *
     * @param startTime start time in milliseconds
     */
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    /**
     * When this node last sent a heartbeat (milliseconds since epoch).
     *
     * @return last heartbeat time
     */
    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Sets the last heartbeat time.
     *
     * @param lastHeartbeat last heartbeat time in milliseconds
     */
    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    /**
     * Build and capability details for this node.
     *
     * @return server information
     */
    public ServerInfo getServerInfo() {
        return serverInfo;
    }

    /**
     * Sets the server information for this node.
     *
     * @param serverInfo server information
     */
    public void setServerInfo(ServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }
}
