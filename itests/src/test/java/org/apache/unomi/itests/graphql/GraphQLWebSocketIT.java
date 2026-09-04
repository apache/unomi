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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
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

            CloseStatus status = socket.waitClose().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(1000, (int) status.getStatus());

        } finally {
            client.stop();
            LOGGER.info("Web socket client stopped.");
        }
    }

    /**
     * A handshake carrying no credential is upgraded rather than refused, because a browser cannot set
     * request headers on a WebSocket handshake. The socket that results can do nothing at all until it
     * authenticates through connection_init, which the following tests pin down.
     */
    @Test
    public void testWebSocketUpgrade_withoutAuth_upgradesButCannotOperate() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            Future<Session> onConnected = client.connect(socket, graphqlWebSocketUri(), new ClientUpgradeRequest());
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();
            // Subscribe for the server's refusal message before triggering it: the client harness
            // blocks in onWebSocketText until a listener exists, which would otherwise stall the close.
            Future<String> refusal = socket.waitMessage();
            remote.sendString(resourceAsString("graphql/socket/out/start.json"));
            refusal.get(10, TimeUnit.SECONDS);
            CloseStatus status = socket.waitClose().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(1008, (int) status.getStatus());
        } finally {
            client.stop();
        }
    }

    /** connection_init carrying a valid credential is how a browser client authenticates. */
    @Test
    public void testWebSocketConnectionInit_withValidCredentials_authenticatesSocket() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            Future<Session> onConnected = client.connect(socket, graphqlWebSocketUri(), new ClientUpgradeRequest());
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();
            remote.sendString(resourceAsString("graphql/socket/out/init-with-credentials.json")
                    .replace("__AUTHORIZATION__", basicAuthHeader(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD)));
            String initResp = socket.waitMessage().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(resourceAsString("graphql/socket/in/ack.json"), initResp);
            remote.sendString(resourceAsString("graphql/socket/out/term.json"));
            CloseStatus status = socket.waitClose().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(1000, (int) status.getStatus());
        } finally {
            client.stop();
        }
    }

    /** A wrong credential in connection_init must not authenticate the socket. */
    @Test
    public void testWebSocketConnectionInit_withBadCredentials_isRefused() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            Future<Session> onConnected = client.connect(socket, graphqlWebSocketUri(), new ClientUpgradeRequest());
            RemoteEndpoint remote = onConnected.get(10, TimeUnit.SECONDS).getRemote();
            Future<String> refusal = socket.waitMessage();
            remote.sendString(resourceAsString("graphql/socket/out/init-bad-credentials.json"));
            refusal.get(10, TimeUnit.SECONDS);
            CloseStatus status = socket.waitClose().get(10, TimeUnit.SECONDS);
            Assert.assertEquals(1008, (int) status.getStatus());
        } finally {
            client.stop();
        }
    }

    /**
     * The unauthenticated-socket deadline is a scheduled close, not an idle timeout: keeping the
     * connection busy with ping frames (which reset an idle timeout) must not extend it.
     */
    @Test
    public void testWebSocketUpgrade_withoutAuth_isClosedAtDeadlineDespitePings() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            Session session = client.connect(socket, graphqlWebSocketUri(), new ClientUpgradeRequest()).get(10, TimeUnit.SECONDS);
            Future<CloseStatus> close = socket.waitClose();
            long start = System.currentTimeMillis();
            while (!close.isDone() && System.currentTimeMillis() - start < 25_000L) {
                try {
                    session.getRemote().sendPing(ByteBuffer.allocate(0));
                } catch (Exception e) {
                    break; // the server closed the socket under us, which is the expected outcome
                }
                Thread.sleep(1_000L);
            }
            CloseStatus status = close.get(10, TimeUnit.SECONDS);
            long elapsed = System.currentTimeMillis() - start;
            Assert.assertTrue("Unauthenticated socket should be closed at its deadline, took " + elapsed + " ms", elapsed < 25_000L);
            Assert.assertEquals(1008, (int) status.getStatus());
        } finally {
            client.stop();
        }
    }

    /** The HTTP authentication scheme token is case-insensitive. */
    @Test
    public void testWebSocketUpgrade_withLowercaseBasicScheme_succeeds() throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            request.setHeader("Authorization", "basic " + Base64.getEncoder().encodeToString(
                    (BASIC_AUTH_USER_NAME + ":" + BASIC_AUTH_PASSWORD).getBytes(StandardCharsets.UTF_8)));
            RemoteEndpoint remote = client.connect(socket, graphqlWebSocketUri(), request).get(10, TimeUnit.SECONDS).getRemote();
            remote.sendString(resourceAsString("graphql/socket/out/init.json"));
            Assert.assertEquals(resourceAsString("graphql/socket/in/ack.json"), socket.waitMessage().get(10, TimeUnit.SECONDS));
            remote.sendString(resourceAsString("graphql/socket/out/term.json"));
            Assert.assertEquals(1000, (int) socket.waitClose().get(10, TimeUnit.SECONDS).getStatus());
        } finally {
            client.stop();
        }
    }

    @Test
    public void testWebSocketUpgrade_withWrongJaasPassword_returns401() throws Exception {
        assertWebSocketUpgradeRejected(basicAuthHeader(BASIC_AUTH_USER_NAME, "definitely-not-the-password"), null, 401);
    }

    @Test
    public void testWebSocketUpgrade_withMalformedBasic_returns401() throws Exception {
        assertWebSocketUpgradeRejected("Basic !!!", null, 401);
    }

    /** A WebSocket handshake bypasses CORS, so a foreign origin is refused before anything else. */
    @Test
    public void testWebSocketUpgrade_fromForeignOrigin_returns403() throws Exception {
        assertWebSocketUpgradeRejected(basicAuthHeader(BASIC_AUTH_USER_NAME, BASIC_AUTH_PASSWORD), "http://attacker.example", 403);
    }

    private URI graphqlWebSocketUri() throws Exception {
        return new URI("ws://localhost:" + getHttpPort() + "/graphql");
    }

    /**
     * Jetty's websocket-client types are kept out of method signatures on purpose: JUnit resolves
     * signature types when it scans the class, before {@code @Before} has waited for the container,
     * and that bundle is not necessarily wired yet at that point.
     */
    private void assertWebSocketUpgradeRejected(String authorization, String origin, int expectedStatus) throws Exception {
        WebSocketClient client = new WebSocketClient();
        Socket socket = new Socket();
        try {
            client.start();
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            if (authorization != null) {
                request.setHeader("Authorization", authorization);
            }
            if (origin != null) {
                request.setHeader("Origin", origin);
            }
            Future<Session> onConnected = client.connect(socket, graphqlWebSocketUri(), request);
            try {
                onConnected.get(10, TimeUnit.SECONDS);
                Assert.fail("GraphQL WebSocket upgrade should have been rejected with " + expectedStatus);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                Assert.assertTrue("Expected UpgradeException, got: " + cause, cause instanceof UpgradeException);
                Assert.assertEquals(expectedStatus, ((UpgradeException) cause).getResponseStatusCode());
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
