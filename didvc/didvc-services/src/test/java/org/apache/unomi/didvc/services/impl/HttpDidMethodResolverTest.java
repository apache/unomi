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

package org.apache.unomi.didvc.services.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.unomi.didvc.api.DidDocumentData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The HTTP DID-method driver against a stub Universal Resolver endpoint:
 * resolution success, 404 semantics and the required
 * {@code /1.0/identifiers/{did}} request shape.
 */
class HttpDidMethodResolverTest {

    private HttpServer server;
    private int port;
    private final Map<String, String> requestPaths = new java.util.concurrent.ConcurrentHashMap<>();

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestPaths.put(exchange.getRequestURI().getPath(), exchange.getRequestURI().getPath());
        String path = exchange.getRequestURI().getPath();
        byte[] body;
        int status;
        if (path.endsWith("/did:iamsmart:known.example.hkt:profile:abc")) {
            DidDocumentData document = new DidDocumentData();
            document.setContext(Arrays.asList("https://www.w3.org/ns/did/v1"));
            document.setId("did:iamsmart:known.example.hkt:profile:abc");
            DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
            method.setId(document.getId() + "#key-1");
            method.setType("JsonWebKey2020");
            method.setController(document.getId());
            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "OKP");
            jwk.put("crv", "Ed25519");
            jwk.put("x", "stub-key-material");
            method.setPublicKeyJwk(jwk);
            document.addVerificationMethod(method);
            body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(document);
            status = 200;
        } else if (path.endsWith("/did:iamsmart:missing.example.hkt")) {
            body = "not found".getBytes(StandardCharsets.UTF_8);
            status = 404;
        } else {
            body = "unexpected".getBytes(StandardCharsets.UTF_8);
            status = 500;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    void resolvesThroughUniversalResolverHttpApi() {
        HttpDidMethodResolver resolver = new HttpDidMethodResolver("iamsmart", "http://127.0.0.1:" + port);
        DidDocumentData document = resolver.resolve("did:iamsmart:known.example.hkt:profile:abc");
        assertNotNull(document);
        assertEquals("did:iamsmart:known.example.hkt:profile:abc", document.getId());
        assertEquals(1, document.getVerificationMethod().size());
        assertNotNull(requestPaths.get("/1.0/identifiers/did:iamsmart:known.example.hkt:profile:abc"));
    }

    @Test
    void missingDidReturnsNull() {
        HttpDidMethodResolver resolver = new HttpDidMethodResolver("iamsmart", "http://127.0.0.1:" + port);
        assertNull(resolver.resolve("did:iamsmart:missing.example.hkt"));
    }
}
