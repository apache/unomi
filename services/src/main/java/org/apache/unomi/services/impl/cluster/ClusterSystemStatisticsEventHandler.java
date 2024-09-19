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
package org.apache.unomi.services.impl.cluster;

import org.apache.karaf.cellar.config.Constants;
import org.apache.karaf.cellar.core.CellarSupport;
import org.apache.karaf.cellar.core.Configurations;
import org.apache.karaf.cellar.core.Group;
import org.apache.karaf.cellar.core.control.BasicSwitch;
import org.apache.karaf.cellar.core.control.Switch;
import org.apache.karaf.cellar.core.control.SwitchStatus;
import org.apache.karaf.cellar.core.event.EventHandler;
import org.apache.karaf.cellar.core.event.EventType;
import org.osgi.service.cm.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Karaf Cellar event handler to process incoming events that contain system statistics updates from nodes.
 */
public class ClusterSystemStatisticsEventHandler extends CellarSupport implements EventHandler<ClusterSystemStatisticsEvent> {

    public static final String SWITCH_ID = "org.apache.unomi.cluster.system.statistics.handler";
    private static final Logger logger = LoggerFactory.getLogger(ClusterSystemStatisticsEventHandler.class.getName());
    private final Switch eventSwitch = new BasicSwitch(SWITCH_ID);
    private ClusterServiceImpl clusterServiceImpl;

    public void setClusterServiceImpl(ClusterServiceImpl clusterServiceImpl) {
        this.clusterServiceImpl = clusterServiceImpl;
    }

    public void init() {
        // nothing to do
    }

    public void destroy() {
        // nothing to do
    }

    @Override
    public void handle(ClusterSystemStatisticsEvent event) {
        // check if the handler is ON
        if (this.getSwitch().getStatus().equals(SwitchStatus.OFF)) {
            logger.debug("CELLAR SYSTEM STATS: {} switch is OFF, cluster event not handled", SWITCH_ID);
            return;
        }

        if (groupManager == null) {
            //in rare cases for example right after installation this happens!
            logger.error("CELLAR SYSTEM STATS: retrieved event {} while groupManager is not available yet!", event);
            return;
        }

        // check if the group is local
        if (!groupManager.isLocalGroup(event.getSourceGroup().getName())) {
            logger.info("CELLAR SYSTEM STATS: node is not part of the event cluster group {}",event.getSourceGroup().getName());
            return;
        }

        Group group = event.getSourceGroup();
        String groupName = group.getName();

        String pid = event.getId();

        if (isAllowed(event.getSourceGroup(), Constants.CATEGORY, pid, EventType.INBOUND)) {

            // check if it's not a "local" event
            if (event.getSourceNode() != null && event.getSourceNode().getId().equalsIgnoreCase(clusterManager.getNode().getId())) {
                logger.trace("CELLAR SYSTEM STATS: cluster event is local (coming from local synchronizer or listener)");
                return;
            }


            Map<String, Serializable> nodeSystemStatistics = clusterServiceImpl.getNodeSystemStatistics().get(event.getSourceNode().getId());
            if (nodeSystemStatistics == null) {
                nodeSystemStatistics = new ConcurrentHashMap<>();
            }
            nodeSystemStatistics.putAll(event.getStatistics());
            clusterServiceImpl.getNodeSystemStatistics().put(event.getSourceNode().getId(), nodeSystemStatistics);
        }

    }

    @Override
    public Class<ClusterSystemStatisticsEvent> getType() {
        return ClusterSystemStatisticsEvent.class;
    }

    /**
     * Get the cluster configuration event handler switch.
     *
     * @return the cluster configuration event handler switch.
     */
    @Override
    public Switch getSwitch() {
        // load the switch status from the config
        try {
            Configuration configuration = configurationAdmin.getConfiguration(Configurations.NODE, null);
            if (configuration != null) {
                String handlerStatus = (String) configuration.getProperties().get(Configurations.HANDLER + "." + this.getClass().getName());
                if (handlerStatus == null) {
                    // default value is on.
                    eventSwitch.turnOn();
                } else {
                    Boolean status = new Boolean(handlerStatus);
                    if (status) {
                        eventSwitch.turnOn();
                    } else {
                        eventSwitch.turnOff();
                    }
                }
            }
        } catch (Exception e) {
            // nothing to do
        }
        return eventSwitch;
    }


}
