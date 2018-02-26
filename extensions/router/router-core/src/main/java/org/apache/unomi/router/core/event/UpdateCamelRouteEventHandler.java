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
package org.apache.unomi.router.core.event;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.cellar.config.Constants;
import org.apache.karaf.cellar.core.CellarSupport;
import org.apache.karaf.cellar.core.control.Switch;
import org.apache.karaf.cellar.core.event.EventHandler;
import org.apache.karaf.cellar.core.event.EventType;
import org.apache.unomi.router.core.context.RouterCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dgaillard
 */
public class UpdateCamelRouteEventHandler extends CellarSupport implements EventHandler<UpdateCamelRouteEvent> {
    private static final Logger logger = LoggerFactory.getLogger(UpdateCamelRouteEventHandler.class.getName());

    private RouterCamelContext routerCamelContext;

    @Override
    public void handle(UpdateCamelRouteEvent event) {
        logger.debug("Handle event");
        if (isAllowed(event.getSourceGroup(), Constants.CATEGORY, event.getId(), EventType.INBOUND)) {
            logger.debug("Event is allowed");
            // check if it's not a "local" event
            if (event.getSourceNode() != null && event.getSourceNode().getId().equalsIgnoreCase(clusterManager.getNode().getId())) {
                logger.debug("Cluster event is local (coming from local synchronizer or listener)");
                return;
            }

            try {
                logger.debug("Event id is {}", event.getId());
                if (event.getId().equals("org.apache.unomi.router.event.remove") && StringUtils.isNotBlank(event.getRouteId())) {
                    routerCamelContext.killExistingRoute(event.getRouteId(), false);
                } else if ((event.getId().equals("org.apache.unomi.router.event.import") || event.getId().equals("org.apache.unomi.router.event.export")) && event.getConfiguration() != null) {
                    routerCamelContext.updateProfileReaderRoute(event.getConfiguration(), false);
                }
            } catch (Exception e) {
                logger.error("Error when executing event", e);
            }
        }
    }

    @Override
    public Class<UpdateCamelRouteEvent> getType() {
        return UpdateCamelRouteEvent.class;
    }

    @Override
    public Switch getSwitch() {
        return null;
    }

    public void setRouterCamelContext(RouterCamelContext routerCamelContext) {
        this.routerCamelContext = routerCamelContext;
    }
}
