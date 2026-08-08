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
import org.eclipse.jetty.websocket.api.UpgradeException;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * End-to-end GraphQL WebSocket auth regression.
 * <p>
 * Maps to the reported unauthenticated-subscription scenarios:
 * <ul>
 *   <li>Upgrade with no credentials → HTTP 401 (not 101)</li>
 *   <li>Upgrade with public API key only → HTTP 401 (subscriptions are never public)</li>
 *   <li>Upgrade with wrong Basic password → HTTP 401</li>
 *   <li>Upgrade with malformed Basic → HTTP 401</li>
 *   <li>Upgrade with valid JAAS / private key → 101, then connection_init / start work</li>
 * </ul>
 * Unauthenticated clients must never reach {@code connection_ack} or {@code GQL_START}.
 */
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
            request.setHeader("Authorization", basicAuthHeader(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD));

            Future<Session> onConnected = client.connect(socket, echoUri, request);
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();

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

            socket.waitClose().get(10, TimeUnit.SECONDS);

        } finally {
            client.stop();
            LOGGER.info("Web socket client stopped.");
        }
    }

    @Test
    public void testWebSocketUpgrade_withoutAuth_returns401() throws Exception {
        assertWebSocketUpgradeRejected(new ClientUpgradeRequest());
    }

    @Test
    public void testWebSocketUpgrade_withPublicApiKeyOnly_returns401() throws Exception {
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setHeader("X-Unomi-Api-Key", testPublicKeyValue);
        assertWebSocketUpgradeRejected(request);
    }

    @Test
    public void testWebSocketUpgrade_withWrongJaasPassword_returns401() throws Exception {
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setHeader("Authorization", basicAuthHeader(BASIC_AUTH_USER_NAME, "definitely-not-the-password"));
        assertWebSocketUpgradeRejected(request);
    }

    @Test
    public void testWebSocketUpgrade_withMalformedBasic_returns401() throws Exception {
        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setHeader("Authorization", "Basic !!!");
        assertWebSocketUpgradeRejected(request);
    }

    @Test
    public void testWebSocketUpgrade_withPrivateKey_succeeds() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            URI echoUri = new URI("ws://localhost:" + getHttpPort() + "/graphql");
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", basicAuthHeader(TEST_TENANT_ID, testPrivateKeyValue));

            Future<Session> onConnected = client.connect(socket, echoUri, request);
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();

            remote.sendString(resourceAsString("graphql/socket/out/init.json"));
            String initResp = socket.waitMessage().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(resourceAsString("graphql/socket/in/ack.json"), initResp);

            remote.sendString(resourceAsString("graphql/socket/out/term.json"));
            socket.waitClose().get(10, TimeUnit.SECONDS);
        } finally {
            client.stop();
        }
    }

    /**
     * Exercises GQL_START with the subject/context captured at upgrade time. A successful
     * subscription setup leaves the socket open (no error close); stop + terminate then clean up.
     */
    @Test
    public void testWebSocketSubscriptionStart_withPrivateKey_acceptsStart() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            URI echoUri = new URI("ws://localhost:" + getHttpPort() + "/graphql");
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", basicAuthHeader(TEST_TENANT_ID, testPrivateKeyValue));

            Future<Session> onConnected = client.connect(socket, echoUri, request);
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();
            Future<CloseStatus> closeFuture = socket.waitClose();

            remote.sendString(resourceAsString("graphql/socket/out/init.json"));
            Assert.assertEquals(resourceAsString("graphql/socket/in/ack.json"),
                    socket.waitMessage().get(10, TimeUnit.SECONDS));

            remote.sendString(resourceAsString("graphql/socket/out/start.json"));
            // Successful subscribe() registers a publisher and does not emit until events arrive.
            // Give the server a moment; an auth/context failure would close the socket with an error.
            Thread.sleep(500);
            Assert.assertFalse("Subscription start should not close the socket", closeFuture.isDone());

            remote.sendString(resourceAsString("graphql/socket/out/stop.json"));
            remote.sendString(resourceAsString("graphql/socket/out/term.json"));
            closeFuture.get(10, TimeUnit.SECONDS);
        } finally {
            client.stop();
        }
    }

    private void assertWebSocketUpgradeRejected(ClientUpgradeRequest request) throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            URI echoUri = new URI("ws://localhost:" + getHttpPort() + "/graphql");
            Future<Session> onConnected = client.connect(socket, echoUri, request);
            try {
                onConnected.get(10, TimeUnit.SECONDS);
                Assert.fail("Unauthenticated GraphQL WebSocket upgrade should be rejected");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                Assert.assertTrue("Expected UpgradeException, got: " + cause, cause instanceof UpgradeException);
                Assert.assertEquals(401, ((UpgradeException) cause).getResponseStatusCode());
            }
        } finally {
            client.stop();
        }
    }

    private static String basicAuthHeader(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
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
