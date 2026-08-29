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

package org.apache.unomi.didvc.edge.m2m;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.apache.unomi.didvc.edge.customs.CustomsEdiAdapter.STATUS_ACCEPTED;
import static org.apache.unomi.didvc.edge.customs.CustomsEdiAdapter.STATUS_REJECTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logistics-flow M2M verification (FR-L2/L3): API-key authentication
 * (keys generated per test run — never literals), stateless single and
 * batch verification with claim-level responses, the sub-second p95
 * load check at customs peak volume, and the Single Window EDI
 * round-trip through the customs declarations endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
class M2mVerificationIntegrationTest {

    /** Customs peak volume for the p95 check: 200 declarations per burst. */
    private static final int LOAD_REQUESTS = 200;
    /** The sub-second p95 target (FR-L2), in nanoseconds. */
    private static final long P95_TARGET_NANOS = 1_000_000_000L;

    // API keys are generated per run and injected via properties — no
    // usable credential literals in source
    private static final String VALID_KEY = "m2m-" + UUID.randomUUID();
    private static final String OTHER_KEY = "m2m-" + UUID.randomUUID();

    @DynamicPropertySource
    static void m2mProperties(DynamicPropertyRegistry registry) {
        registry.add("didvc.edge.m2m-api-keys[0]", () -> VALID_KEY);
        registry.add("didvc.edge.m2m-api-keys[1]", () -> OTHER_KEY);
    }

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    private static final String TENANT = "customs-hk";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String cargoCredential;
    private String corporateCredential;
    private PlatformApi.IssuedCredential cargoRecord;

    @BeforeEach
    void setUp() {
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_cargo_v1");
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_corporate_v1");

        PlatformApi.IssueRequest cargo = new PlatformApi.IssueRequest();
        cargo.setTenantId("hkt");
        cargo.setSchemaId("hkt-cargo-v1");
        cargo.setSubjectId("didvc:pairwise:consignment-77");
        cargo.setKid(platformApi.getIssuerKid());
        cargo.setAlwaysDisclosedClaims(Map.of(
                "hsCodeClass", "8542",
                "customsStatus", "cleared"));
        cargo.setSelectivelyDisclosedClaims(Map.of(
                "aeoStatus", "AEO-F",
                "originAttestation", "HK-origin"));
        cargoRecord = platformApi.issueCredential("hkt", cargo);
        cargoCredential = cargoRecord.getCredential();

        PlatformApi.IssueRequest corporate = new PlatformApi.IssueRequest();
        corporate.setTenantId("hkt");
        corporate.setSchemaId("hkt-corporate-v1");
        corporate.setSubjectId("didvc:pairwise:trader-42");
        corporate.setKid(platformApi.getIssuerKid());
        corporate.setAlwaysDisclosedClaims(Map.of(
                "registrationNoHash", "sha256:1a2b3c",
                "jurisdiction", "HK"));
        corporate.setSelectivelyDisclosedClaims(Map.of(
                "licensedActivities", List.of("freight-forwarding"),
                "lei", "5493001KJTIIGC8Y1R12"));
        corporateCredential = platformApi.issueCredential("hkt", corporate).getCredential();
    }

    @Test
    void apiKeyAuthenticationEnforced() throws Exception {
        Map<String, Object> body = Map.of("credential", cargoCredential);
        // No key
        mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        // Unknown key
        mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", "m2m-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        // Valid key
        mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    void validCredentialVerifiesClaimLevel() throws Exception {
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", cargoCredential))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("valid").asBoolean());
        assertEquals("hkt_cargo_v1", response.get("vct").asText());
        assertTrue(response.has("expiresAt"));
        assertFalse(response.has("claims"));

        // includeClaims carries the disclosed values
        MvcResult withClaims = mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("credential", cargoCredential, "includeClaims", true))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode claims = objectMapper.readTree(withClaims.getResponse().getContentAsString());
        assertEquals("cleared", claims.get("claims").get("customsStatus").asText());
        assertEquals("AEO-F", claims.get("claims").get("aeoStatus").asText());
    }

    @Test
    void revokedAndUntrustedCredentialsFailWithReasons() throws Exception {
        // Revoked
        platformApi.markRevoked(cargoRecord.getItemId());
        MvcResult revoked = mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", cargoCredential))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(revoked.getResponse().getContentAsString());
        assertFalse(response.get("valid").asBoolean());
        assertEquals("credential is revoked", response.get("reason").asText());

        // Untrusted issuer (fresh credential, trust removed)
        platformApi.untrustAll();
        MvcResult untrusted = mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                        .header("X-Api-Key", OTHER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", corporateCredential))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode untrustedResponse = objectMapper.readTree(untrusted.getResponse().getContentAsString());
        assertFalse(untrustedResponse.get("valid").asBoolean());
        assertEquals("issuer is not trusted by this verifier", untrustedResponse.get("reason").asText());
    }

    @Test
    void batchVerificationReturnsPerRecordOutcomes() throws Exception {
        Map<String, Object> batch = Map.of("records", List.of(
                Map.of("id", "LI-001", "credential", cargoCredential),
                Map.of("id", "LI-002", "credential", corporateCredential)));
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/m2m/verify-batch")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, response.get("count").asInt());
        assertTrue(response.get("results").get(0).get("valid").asBoolean());
        assertEquals("hkt_cargo_v1", response.get("results").get(0).get("vct").asText());
        assertTrue(response.get("results").get(1).get("valid").asBoolean());
    }

    @Test
    void loadTestHoldsSubSecondP95AtCustomsPeakVolume() throws Exception {
        // Customs peak volume burst through the stateless endpoint; the
        // p95 of per-request latency must stay under one second (FR-L2)
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Long>> latencies = new ArrayList<>();
            for (int i = 0; i < LOAD_REQUESTS; i++) {
                latencies.add(executor.submit(() -> {
                    long start = System.nanoTime();
                    mockMvc.perform(post("/" + TENANT + "/m2m/verify")
                                    .header("X-Api-Key", VALID_KEY)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(
                                            Map.of("credential", cargoCredential))))
                            .andExpect(status().isOk());
                    return System.nanoTime() - start;
                }));
            }
            List<Long> sorted = new ArrayList<>();
            for (Future<Long> future : latencies) {
                sorted.add(future.get(60, TimeUnit.SECONDS));
            }
            java.util.Collections.sort(sorted);
            long p95 = sorted.get((int) Math.ceil(0.95 * sorted.size()) - 1);
            assertTrue(p95 < P95_TARGET_NANOS,
                    "p95 latency " + (p95 / 1_000_000) + " ms exceeds the sub-second target");
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- Single Window EDI round-trip (T-6.3) ----

    private String loadFixture(String name, Map<String, String> placeholders) throws Exception {
        String raw = Files.readString(Path.of("src/test/resources/customs/" + name));
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            raw = raw.replace(placeholder.getKey(), placeholder.getValue());
        }
        return raw;
    }

    @Test
    void declarationFixtureRoundTripsToSingleWindowResponse() throws Exception {
        String body = loadFixture("declaration-sample.json", Map.of(
                "${cargoCredential}", cargoCredential,
                "${corporateCredential}", corporateCredential));
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/customs/declarations")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("VERIFICATION", response.get("messageType").asText());
        assertEquals("SWD-2026-004217", response.get("inReplyTo").asText());
        assertEquals(2, response.get("statusLines").size());
        assertEquals("SWD-2026-004217:LI-001", response.get("statusLines").get(0).get("itemId").asText());
        assertEquals(STATUS_ACCEPTED,
                response.get("statusLines").get(0).get("status").asText());
        assertEquals(STATUS_ACCEPTED,
                response.get("statusLines").get(1).get("status").asText());
    }

    @Test
    void revokedLineItemIsRejectedInTheEdiResponse() throws Exception {
        String body = loadFixture("declaration-revoked-sample.json", Map.of(
                "${cargoCredential}", cargoCredential,
                "${revokedCargoCredential}", cargoCredential));
        // Mark the credential revoked after building the fixture: both
        // line items carry it now, but the fixture shape stays intact
        platformApi.markRevoked(cargoRecord.getItemId());
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/customs/declarations")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(2, response.get("statusLines").size());
        for (JsonNode line : response.get("statusLines")) {
            assertEquals(STATUS_REJECTED, line.get("status").asText());
            assertTrue(line.has("statusReason"));
        }
    }

    @Test
    void malformedDeclarationsAreRejected() throws Exception {
        mockMvc.perform(post("/" + TENANT + "/customs/declarations")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"NOT-A-DECLARATION\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/" + TENANT + "/customs/declarations")
                        .header("X-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"DECLARATION\",\"declarationNumber\":\"X\",\"lineItems\":[]}"))
                .andExpect(status().isBadRequest());
        // Unauthenticated
        mockMvc.perform(post("/" + TENANT + "/customs/declarations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageType\":\"DECLARATION\"}"))
                .andExpect(status().isUnauthorized());
    }

}
