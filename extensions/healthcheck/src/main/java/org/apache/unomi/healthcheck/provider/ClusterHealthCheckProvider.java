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

package org.apache.unomi.healthcheck.provider;

import org.apache.unomi.api.ClusterNode;
import org.apache.unomi.api.services.ClusterService;
import org.apache.unomi.healthcheck.HealthCheckResponse;
import org.apache.unomi.healthcheck.HealthCheckProvider;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * A Health Check that checks the status of the Unomi cluster service.
 */
@Component(service = HealthCheckProvider.class, immediate = true)
public class ClusterHealthCheckProvider implements HealthCheckProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterHealthCheckProvider.class.getName());
    public static final String NAME = "cluster";

    @Reference(service = ClusterService.class, cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC, bind = "bind", unbind = "unbind")
    private volatile ClusterService service;

    public ClusterHealthCheckProvider() {
        LOGGER.info("Building cluster health provider service...");
    }

    public void bind(ClusterService service) {
        this.service = service;
    }

    public void unbind(ClusterService service) {
        this.service = null;
    }

    @Override public String name() {
        return NAME;
    }

    @Override public HealthCheckResponse execute() {
        LOGGER.debug("Health check cluster");
        HealthCheckResponse.Builder builder = new HealthCheckResponse.Builder();
        builder.name(NAME).down();
        try {
            if (service != null) {
                builder.up();
                List<ClusterNode> nodes = service.getClusterNodes();
                builder.withData("cluster.size", nodes.size());
                if (nodes.isEmpty()) {
                    builder.down();
                }
                int idx = 1;
                for (ClusterNode node : nodes) {
                    if (nodes.size() == 1 || node.isMaster()) {
                        builder.live();
                    }
                    builder.withData("cluster.node." + idx + ".uptime", node.getUptime());
                    builder.withData("cluster.node." + idx + ".cpuload", Double.toString(node.getCpuLoad()));
                    builder.withData("cluster.node." + idx + ".loadAverage", Arrays.stream(node.getLoadAverage()).mapToObj(
                            Double::toString).collect(java.util.stream.Collectors.joining(",")));
                    builder.withData("cluster.node." + idx + ".public", node.getPublicHostAddress());
                    builder.withData("cluster.node." + idx + ".internal", node.getInternalHostAddress());
                    if (node.isData() || node.isMaster()) {
                        builder.withData("cluster.node." + idx + ".role",
                                (node.isMaster() ? "master" : "") + (node.isData() ? "data" : ""));
                    }
                    idx++;
                }
            }
        } catch (Exception e) {
            builder.error().withData("error", e.getMessage());
            LOGGER.error("Error checking cluster health", e);
        }
        return builder.build();
    }
}
