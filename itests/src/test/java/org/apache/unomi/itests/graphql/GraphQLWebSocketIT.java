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

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class GraphQLWebSocketIT extends BaseGraphQLIT {

    private static final String SUBSCRIPTION_ENDPOINT = "ws://localhost:" + HTTP_PORT + "/graphql";

    @Test
    public void testWebSocketConnectionSegment() throws Exception {
        WebSocketClient client = new WebSocketClient();
        try {
            client.start();

            URI echoUri = new URI(SUBSCRIPTION_ENDPOINT);
            ClientUpgradeRequest request = new ClientUpgradeRequest();

            Socket socket = new Socket();
            Future<Session> onConnected = client.connect(socket, echoUri, request);
            RemoteEndpoint remote = onConnected.get().getRemote();

            String initMsg = resourceAsString("graphql/socket/out/init.json");
            remote.sendString(initMsg);

            String ackMsg = resourceAsString("graphql/socket/in/ack.json");
            String initResp = socket.waitMessage().get();
            Assert.assertEquals(ackMsg, initResp);

            String termMsg = resourceAsString("graphql/socket/out/term.json");
            remote.sendString(termMsg);


            CloseStatus status = socket.waitClose().get();
            // Assert.assertEquals(1000, (int) status.getStatus()); TODO skip for now

        } finally {
            client.stop();
        }
    }

    private class Socket extends WebSocketAdapter {

        private Flowable<String> publisher;

        private ObservableEmitter<String> emitter;

        private CompletableFuture<CloseStatus> closeStatus = new CompletableFuture<>();

        public Socket() {
            publisher = Observable
                    .create((ObservableEmitter<String> emitter) -> this.emitter = emitter)
                    .toFlowable(BackpressureStrategy.BUFFER);
        }

        @Override
        public void onWebSocketConnect(Session sess) {
            super.onWebSocketConnect(sess);
        }

        @Override
        public void onWebSocketText(String message) {
            this.emitter.onNext(message);
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
                }
            });
            return future;
        }

        public Future<CloseStatus> waitClose() {
            return closeStatus;
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            super.onWebSocketClose(statusCode, reason);
            closeStatus.complete(new CloseStatus(statusCode, reason));
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
