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
package org.apache.unomi.itests.graphql;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.subscribers.DefaultSubscriber;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class GraphQLWebSocketIT extends BaseGraphQLIT {

    private final static Logger LOGGER = LoggerFactory.getLogger(GraphQLWebSocketIT.class);

    @Test
    public void testWebSocketConnectionSegment() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            LOGGER.info("Starting web socket client...");
            client.start();

            URI echoUri = new URI("ws://localhost:" + getHttpPort() + "/graphql");
            ClientUpgradeRequest request = new ClientUpgradeRequest();

            Future<Session> onConnected = client.connect(socket, echoUri, request);
            RemoteEndpoint remote = onConnected.get().getRemote();

            LOGGER.info("Connected, initializing... ");

            String initMsg = resourceAsString("graphql/socket/out/init.json");
            remote.sendString(initMsg);

            LOGGER.info("Initialized, acknowledging...  ");

            String ackMsg = resourceAsString("graphql/socket/in/ack.json");
            String initResp = socket.waitMessage().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(ackMsg, initResp);

            LOGGER.info("Sending terminate message...");

            String termMsg = resourceAsString("graphql/socket/out/term.json");
            remote.sendString(termMsg);

            LOGGER.info("Waiting for socket to close...");

            CloseStatus status = socket.waitClose().get(10, TimeUnit.SECONDS);
            // Assert.assertEquals(1000, (int) status.getStatus()); TODO skip for now

        } finally {
            client.stop();
            LOGGER.info("Web socket client stopped.");
        }
    }

    private class Socket extends WebSocketAdapter {

        private Flowable<String> publisher;

        private CompletableFuture<ObservableEmitter<String>> emitterFuture;

        private CompletableFuture<CloseStatus> closeStatus = new CompletableFuture<>();

        private List<Future<String>> messageListeners = new ArrayList<>();

        public Socket() {
            // web socket message may come faster than observable callback is executed
            emitterFuture = new CompletableFuture<>();

            publisher = Observable
                    .create((ObservableEmitter<String> emitter) -> this.emitterFuture.complete(emitter))
                    .toFlowable(BackpressureStrategy.BUFFER);
        }

        @Override
        public void onWebSocketConnect(Session sess) {
            super.onWebSocketConnect(sess);
        }

        @Override
        public void onWebSocketText(String message) {
            try {
                this.emitterFuture.get(10, TimeUnit.SECONDS).onNext(message);
            } catch (Exception e) {
                throw new RuntimeException("Could not get emitter", e);
            }
        }

        public Future<String> waitMessage() {
            CompletableFuture<String> future = new CompletableFuture<>();
            publisher.subscribe(new DefaultSubscriber<String>() {

                @Override
                public void onNext(String s) {
                    future.complete(s);
                    cancel();
                }

                @Override
                public void onError(Throwable throwable) {
                    future.completeExceptionally(throwable);
                    cancel();
                }

                @Override
                public void onComplete() {
                    future.cancel(false);
                    cancel();
                    messageListeners.remove(future);
                }
            });
            messageListeners.add(future);
            return future;
        }

        public Future<CloseStatus> waitClose() {
            return closeStatus;
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            LOGGER.info("Web socket close, code: " + statusCode + ", reason: " + reason);
            super.onWebSocketClose(statusCode, reason);
            closeStatus.complete(new CloseStatus(statusCode, reason));
            cancelListeners();
        }

        private void cancelListeners() {
            this.messageListeners.forEach(future -> future.cancel(false));
        }
    }

    private class CloseStatus {
        final Integer status;
        final String reason;

        public CloseStatus(Integer status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public Integer getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }
}
