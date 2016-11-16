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

package org.apache.unomi.elasticsearch.plugin.security;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.network.NetworkService;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.*;
import org.elasticsearch.transport.TransportConnectionListener;
import org.elasticsearch.transport.TransportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ElasticSearch plugin that simply rejects connection from non-authorized IP ranges
 */
public class SecurityPluginService extends AbstractLifecycleComponent<SecurityPluginService> {

    private static final Logger logger = LoggerFactory.getLogger(SecurityPluginService.class.getName());

    RestController restController;
    TransportService transportService;
    RestFilter restFilter;
    TransportConnectionListener transportConnectionListener;
    String publishHost;
    List<IPRangeMatcher> ipRangeMatchers = new ArrayList<IPRangeMatcher>();

    @Inject
    public SecurityPluginService(Settings settings,
                                 RestController restController,
                                 TransportService transportService,
                                 NetworkService networkService) {
        super(settings);
        this.restController = restController;
        this.transportService = transportService;
        this.publishHost = settings.get("publish_host", settings.get("transport.publish_host", settings.get("transport.host")));
        InetAddress publishHostAddress = null;
        try {
            publishHostAddress = networkService.resolvePublishHostAddresses(new String[] { publishHost });
        } catch (IOException e) {
            logger.error("Error trying to resolve publish host address " + publishHost);
        }

        initIPRangeMatcher(settings, publishHostAddress);
    }

    protected void initIPRangeMatcher(Settings settings, InetAddress publishHostAddress) {
        String hostAddressRange = null;
        if (publishHostAddress != null) {
            String hostAddress = publishHostAddress.getHostAddress();
            if (publishHostAddress instanceof Inet4Address) {
                int lastDotPos = hostAddress.lastIndexOf(".");
                if (lastDotPos > -1) {
                    hostAddressRange = hostAddress.substring(0, lastDotPos) + ".0-" + hostAddress.substring(0, lastDotPos) + ".255";
                }
            } else if (publishHostAddress instanceof Inet6Address) {
                int lastColonPos = hostAddress.lastIndexOf(":");
                if (lastColonPos > -1) {
                    hostAddressRange = hostAddress.substring(0, lastColonPos) + ":0-" + hostAddress.substring(0, lastColonPos) + ":ffff";
                }
            }
        }
        String defaultIpRanges = "localhost,127.0.0.1,127.0.1.1,::1";
        if (hostAddressRange != null) {
            defaultIpRanges += "," + hostAddressRange;
        }
        String[] ipRanges = settings.get("security.ipranges", defaultIpRanges).split(",");
        for (String ipRange : ipRanges) {
            try {
                IPRangeMatcher iprangeMatcher = new IPRangeMatcher(ipRange.trim());
                ipRangeMatchers.add(iprangeMatcher);
            } catch (UnknownHostException e) {
                logger.error("Error in specified IP range " + ipRange, e);
            }
        }
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        restFilter = new RestFilter() {
            @Override
            public void process(RestRequest request, RestChannel channel, RestFilterChain filterChain) throws Exception {
                logger.info("Processing REST request=" + request + " channel=" + channel);
                if (request.getRemoteAddress() instanceof InetSocketAddress) {
                    InetSocketAddress inetSocketAddress = (InetSocketAddress) request.getRemoteAddress();
                    if (!isIPAllowed(inetSocketAddress.getHostName())) {
                        logger.warn("Rejecting request from unauthorized IP " + request.getRemoteAddress());
                        return;
                    }
                } else {
                    logger.warn("Unexpected SocketAddress that is not an InetSocketAddress (but an instance of  " + request.getRemoteAddress().getClass().getName() + "), IP range filtering is DISABLED !");
                }
                filterChain.continueProcessing(request, channel);
            }
        };
        restController.registerFilter(restFilter);
        transportConnectionListener = new TransportConnectionListener() {
            public void onNodeConnected(DiscoveryNode node) {
                logger.info("Node connected " + node);
                if (!isIPAllowed(node.getHostAddress())) {
                    logger.warn("Rejecting connection from unauthorized IP " + node.getHostAddress());
                    transportService.disconnectFromNode(node);
                }
            }

            public void onNodeDisconnected(DiscoveryNode node) {
            }
        };
        transportService.addConnectionListener(transportConnectionListener);
    }

    @Override
    protected void doStop() throws ElasticsearchException {
        transportService.removeConnectionListener(transportConnectionListener);
    }

    @Override
    protected void doClose() throws ElasticsearchException {

    }

    public boolean isIPAllowed(String ipAddress) {
        for (IPRangeMatcher ipRangeMatcher : ipRangeMatchers) {
            try {
                if (ipRangeMatcher.isInRange(ipAddress)) {
                    return true;
                }
            } catch (UnknownHostException e) {
                logger.error("Error checking IP range for " + ipAddress + " connection will NOT be allowed", e);
            }
        }
        return false;
    }
}
