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

import graphql.ExecutionResult;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

public class ExecutionResultSubscriber extends io.reactivex.subscribers.DefaultSubscriber<ExecutionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionResultSubscriber.class);

    private final RemoteEndpoint remote;

    private final String id;

    public ExecutionResultSubscriber(String id, RemoteEndpoint remote) {
        this.id = id;
        this.remote = remote;
    }

    @Override
    public void onNext(ExecutionResult result) {
        LOGGER.debug("Subscriber sending data {}", result);

        final GraphQLMessage message = GraphQLMessage.create(id)
                .data(result.getData())
                .errors(result.getErrors())
                .field("dataPresent", result.isDataPresent())
                .build();

        sendMessage(message);
        request(1);
    }

    private void sendMessage(GraphQLMessage message) {
        try {
            this.remote.sendString(message.toString());
        } catch (IOException e) {
            LOGGER.warn("Subscriber failed to send data", e);
        }
    }

    @Override
    public void onError(Throwable t) {
        LOGGER.error("Subscriber exception", t);

        sendMessage(GraphQLMessage.create(id).errors(Collections.singletonList(t.getMessage())).build());
        cancel();
    }

    @Override
    public void onComplete() {
        LOGGER.info("Subscriber complete");

        sendMessage(GraphQLMessage.complete(id));
        cancel();
    }

    public void unsubscribe() {
        this.cancel();
    }
}
