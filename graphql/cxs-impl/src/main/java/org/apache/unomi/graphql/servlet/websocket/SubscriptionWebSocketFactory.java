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

package org.apache.unomi.graphql.servlet.websocket;

import graphql.GraphQL;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.osgi.service.http.HttpContext.REMOTE_USER;

public class SubscriptionWebSocketFactory extends WebSocketServerFactory {

    private final GraphQL graphQL;

    private final ServiceManager serviceManager;

    private final GraphQLServletSecurityValidator validator;

    /**
     * Closes sockets that do not authenticate within their deadline. One daemon thread for all sockets.
     * This object is only ever Jetty's creator, never a started lifecycle, so the servlet shuts the
     * scheduler down explicitly from {@code destroy()}.
     */
    private final ScheduledExecutorService authenticationDeadlineScheduler;

    public SubscriptionWebSocketFactory(GraphQL graphQL, ServiceManager serviceManager,
                                        GraphQLServletSecurityValidator validator) {
        this.graphQL = graphQL;
        this.serviceManager = serviceManager;
        this.validator = validator;
        this.authenticationDeadlineScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "graphql-ws-authentication-deadline");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        // The validator records the remote user on the request when the upgrade carried a valid
        // credential. Its absence is not an error: a browser cannot send credentials on the handshake,
        // so the socket is created unauthenticated and must authenticate through connection_init
        // before it can do anything.
        boolean authenticatedOnUpgrade = req.getHttpServletRequest().getAttribute(REMOTE_USER) != null;
        return new SubscriptionWebSocket(graphQL, serviceManager, authenticatedOnUpgrade, validator,
                authenticationDeadlineScheduler);
    }

    /** Stops the deadline scheduler; called when the owning servlet is destroyed. */
    public void shutdown() {
        authenticationDeadlineScheduler.shutdownNow();
    }
}
