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
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.security.auth.Subject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionWebSocketFactoryTest {

    @Mock
    private GraphQL graphQL;
    @Mock
    private ServiceManager serviceManager;
    @Mock
    private SecurityService securityService;
    @Mock
    private ExecutionContextManager executionContextManager;
    @Mock
    private ServletUpgradeRequest upgradeRequest;
    @Mock
    private ServletUpgradeResponse upgradeResponse;

    private SubscriptionWebSocketFactory factory;

    @BeforeEach
    void setUp() {
        factory = new SubscriptionWebSocketFactory(graphQL, serviceManager, securityService, executionContextManager);
    }

    @Test
    void createWebSocket_withoutSubject_returnsNullAndSets401() {
        when(securityService.getCurrentSubject()).thenReturn(null);

        Object socket = factory.createWebSocket(upgradeRequest, upgradeResponse);

        assertNull(socket);
        verify(upgradeResponse).setStatusCode(401);
    }

    @Test
    void createWebSocket_withSubject_returnsSubscriptionWebSocket() {
        Subject subject = new Subject();
        ExecutionContext context = new ExecutionContext("tenant-a", null, null);
        when(securityService.getCurrentSubject()).thenReturn(subject);
        when(executionContextManager.getCurrentContext()).thenReturn(context);

        Object socket = factory.createWebSocket(upgradeRequest, upgradeResponse);

        assertNotNull(socket);
        assertTrue(socket instanceof SubscriptionWebSocket);
        verify(upgradeResponse, never()).setStatusCode(401);
    }
}
