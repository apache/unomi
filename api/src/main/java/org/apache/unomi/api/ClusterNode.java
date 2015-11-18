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
public class ClusterNode implements Serializable {

    private static final long serialVersionUID = 1281422346318230514L;

    private double cpuLoad;
    private double[] loadAverage;
    private String hostName;
    private String hostAddress;
    private int publicPort;
    private String secureHostAddress;
    private int securePort;
    private long uptime;
    private boolean master;
    private boolean data;

    /**
     * Instantiates a new Cluster node.
     */
    public ClusterNode() {
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
     * Retrieves the host name.
     *
     * @return the host name
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name.
     *
     * @param hostName the host name
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Retrieves the host address.
     *
     * @return the host address
     */
    public String getHostAddress() {
        return hostAddress;
    }

    /**
     * Sets the host address.
     *
     * @param hostAddress the host address
     */
    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    /**
     * Retrieves the public port.
     *
     * @return the public port
     */
    public int getPublicPort() {
        return publicPort;
    }

    /**
     * Sets the public port.
     *
     * @param publicPort the public port
     */
    public void setPublicPort(int publicPort) {
        this.publicPort = publicPort;
    }

    /**
     * Retrieves the secure host address which uses the HTTPS protocol for communications between clients and the context server.
     *
     * @return the secure host address
     */
    public String getSecureHostAddress() {
        return secureHostAddress;
    }

    /**
     * Sets the secure host address which uses the HTTPS protocol for communications between clients and the context server.
     *
     * @param secureHostAddress the secure host address
     */
    public void setSecureHostAddress(String secureHostAddress) {
        this.secureHostAddress = secureHostAddress;
    }

    /**
     * Retrieves the secure port.
     *
     * @return the secure port
     */
    public int getSecurePort() {
        return securePort;
    }

    /**
     * Sets the secure port.
     *
     * @param securePort the secure port
     */
    public void setSecurePort(int securePort) {
        this.securePort = securePort;
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
}
