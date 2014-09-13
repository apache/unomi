package org.oasis_open.wemi.context.server.api;

import java.io.Serializable;

/**
 * Represents the information about a cluster node
 */
public class ClusterNode implements Serializable {

    private double cpuLoad;
    private String hostName;
    private int publicPort;

}
