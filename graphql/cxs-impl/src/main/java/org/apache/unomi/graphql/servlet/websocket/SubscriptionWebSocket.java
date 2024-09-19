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

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import org.apache.unomi.graphql.services.ServiceManager;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SubscriptionWebSocket extends WebSocketAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionWebSocket.class);

    private GraphQL graphQL;

    private ServiceManager serviceManager;

    private Map<String, ExecutionResultSubscriber> subscriptions = new HashMap<String, ExecutionResultSubscriber>();

    public SubscriptionWebSocket(GraphQL graphQL, ServiceManager serviceManager) {
        this.graphQL = graphQL;
        this.serviceManager = serviceManager;
    }

    @Override
    public void onWebSocketConnect(Session sess) {
        logger.info("Opening web socket");
        super.onWebSocketConnect(sess);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        logger.info("Closing web socket");
        super.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void onWebSocketText(String textMessage) {
        logger.info("Got web socket messages {}", textMessage);
        final GraphQLMessage message = GraphQLMessage.fromJson(textMessage);
        if (message == null) {
            return;
        }

        switch (message.getType()) {
            case GraphQLMessage.TYPE_CONNECTION_INIT:
                sendMessage(GraphQLMessage.connectionAck(message.getId()));
                break;
            case GraphQLMessage.GQL_START:
                subscribe(message);
                break;
            case GraphQLMessage.GQL_STOP:
                unsubscribe(message);
                break;
            case GraphQLMessage.TYPE_CONNECTION_TERMINATE:
                closeConnection(message, "Client terminated connection");
                break;
        }
    }

    private void closeConnection(GraphQLMessage message, String reason) {
        unsubscribe(message);
        getSession().close(0, reason);
    }

    private void sendMessage(GraphQLMessage message) {
        try {
            getRemote().sendString(message.toString());
        } catch (IOException e) {
            logger.error("Web socket error when sending a message", e);
        }
    }

    private void unsubscribe(GraphQLMessage message) {
        final ExecutionResultSubscriber sub = subscriptions.get(message.getId());
        if (sub != null) {
            sub.unsubscribe();
            subscriptions.remove(message.getId());
        }
    }

    private void subscribe(GraphQLMessage message) {
        final Map<String, Object> payload = message.getPayload();

        ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                .query((String) payload.get("query"))
                .variables((Map<String, Object>) payload.get("variables"))
                .operationName((String) payload.get("operationName"))
                .context(serviceManager)
                .build();

        ExecutionResult executionResult = this.graphQL.execute(executionInput);
        if (executionResult.getErrors() != null && !executionResult.getErrors().isEmpty()) {
            sendMessage(GraphQLMessage.create(message.getId())
                    .errors(executionResult.getErrors())
                    .build());
            closeConnection(message, "Error executing graphQL query");
            return;
        } else if (!(executionResult.getData() instanceof Publisher)) {
            final String error = "Fetched value should be instance of Publisher, was: " + executionResult.getClass().getName();
            sendMessage(GraphQLMessage.create(message.getId())
                    .errors(Collections.singletonList(error))
                    .build());
            closeConnection(message, error);
            return;
        }

        Publisher<ExecutionResult> publisher = executionResult.getData();
        ExecutionResultSubscriber subscriber = new ExecutionResultSubscriber(message.getId(), getRemote());
        publisher.subscribe(subscriber);

        subscriptions.put(message.getId(), subscriber);
    }
}
