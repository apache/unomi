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
import org.apache.unomi.api.ExecutionContext;
import org.apache.unomi.api.security.SecurityService;
import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.graphql.services.ServiceManager;
import org.apache.unomi.graphql.servlet.auth.GraphQLServletSecurityValidator;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;

import javax.security.auth.Subject;

public class SubscriptionWebSocketFactory extends WebSocketServerFactory {

    private final GraphQL graphQL;

    private final ServiceManager serviceManager;

    private final SecurityService securityService;

    private final ExecutionContextManager executionContextManager;

    private final GraphQLServletSecurityValidator validator;

    public SubscriptionWebSocketFactory(GraphQL graphQL, ServiceManager serviceManager,
                                        SecurityService securityService,
                                        ExecutionContextManager executionContextManager,
                                        GraphQLServletSecurityValidator validator) {
        this.graphQL = graphQL;
        this.serviceManager = serviceManager;
        this.securityService = securityService;
        this.executionContextManager = executionContextManager;
        this.validator = validator;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        // A subject here means the upgrade authenticated (header route). Its absence is not an error:
        // a browser cannot send credentials on the handshake, so the socket is created unauthenticated
        // and must authenticate through connection_init before it can do anything.
        Subject subject = securityService.getCurrentSubject();
        ExecutionContext executionContext = subject != null ? executionContextManager.getCurrentContext() : null;
        return new SubscriptionWebSocket(graphQL, serviceManager, subject, executionContext,
                securityService, executionContextManager, validator);
    }
}
