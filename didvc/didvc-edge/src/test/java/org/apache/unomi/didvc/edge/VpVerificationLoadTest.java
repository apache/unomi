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

package org.apache.unomi.didvc.edge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.apache.unomi.didvc.sdjwt.KeyBindingJwtBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-P5/L2 performance acceptance for the OID4VP verification path: N
 * concurrent authorize → key-bound presentation → direct_post cycles
 * must hold a sub-second p95 per cycle (the same target the M2M
 * endpoint carries; the presentation cycle includes the signed-request
 * object and full SD-JWT + KB-JWT validation).
 */
@SpringBootTest
@AutoConfigureMockMvc
class VpVerificationLoadTest {

    /** Verification cycles per burst (bank onboarding peak shape). */
    private static final int CYCLES = 60;
    private static final long P95_TARGET_NANOS = 1_000_000_000L;

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    private static final String TENANT = "bank-load";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_kyc_v1");
    }

    private String presentationFor(OctetKeyPair holderKey, String credential, String nonce)
            throws Exception {
        // The issued credential already ends with the RFC 9901 trailing
        // '~'; the key-binding JWT is appended directly to it
        String kbJwt = new KeyBindingJwtBuilder().build(holderKey, nonce,
                "https://bank-load.example.hkt", credential, new Date());
        return credential + kbJwt;
    }

    @Test
    void authorizationAndPresentationCycleHoldsSubSecondP95() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Future<Long>> cycles = new ArrayList<>();
            for (int i = 0; i < CYCLES; i++) {
                cycles.add(executor.submit(() -> oneCycle()));
            }
            List<Long> sorted = new ArrayList<>();
            for (Future<Long> cycle : cycles) {
                sorted.add(cycle.get(120, java.util.concurrent.TimeUnit.SECONDS));
            }
            java.util.Collections.sort(sorted);
            long p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
            assertTrue(p95 < P95_TARGET_NANOS,
                    "verification-cycle p95 " + (p95 / 1_000_000) + " ms exceeds the sub-second target");
        } finally {
            executor.shutdownNow();
        }
    }

    /** One full authorize → present cycle; returns its duration in ns. */
    private Long oneCycle() throws Exception {
        OctetKeyPair holderKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        PlatformApi.IssueRequest request = new PlatformApi.IssueRequest();
        request.setTenantId("hkt");
        request.setSchemaId("hkt-kyc-v1");
        request.setSubjectId("didvc:pairwise:load-" + UUID.randomUUID());
        request.setKid(platformApi.getIssuerKid());
        request.setHolderPublicJwkJson(holderKey.toPublicJWK().toJSONString());
        request.setAlwaysDisclosedClaims(Map.of("kycLevel", "REMOTE_FULL"));
        // at least one selective disclosure so the credential carries ~
        // delimiters (the KB-JWT input form below relies on them)
        request.setSelectivelyDisclosedClaims(Map.of("givenName", "Load"));
        String credential = platformApi.issueCredential("hkt", request).getCredential();

        long start = System.nanoTime();
        String nonce = "load-" + UUID.randomUUID();

        // 1. authorize (claims-map form)
        Map<String, Object> authorize = Map.of(
                "client_id", "https://bank-load.example.hkt",
                "response_uri", "http://localhost:8080/" + TENANT + "/vp/direct_post",
                "nonce", nonce,
                "claims", Map.of("hkt_kyc_v1", List.of("kycLevel")));
        String authorizeBody = mockMvc.perform(post("/" + TENANT + "/vp/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authorize)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String requestUri = objectMapper.readTree(authorizeBody).get("request_uri").asText();
        String requestId = requestUri.substring(requestUri.lastIndexOf('/') + 1);

        // 2. direct_post with the key-bound presentation
        String vp = presentationFor(holderKey, credential, nonce);
        String result = mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", nonce, "vp_token", vp))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(objectMapper.readTree(result).get("valid").asBoolean());
        return System.nanoTime() - start;
    }
}
