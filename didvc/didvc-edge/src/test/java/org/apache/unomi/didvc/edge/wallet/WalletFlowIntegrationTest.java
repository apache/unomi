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

package org.apache.unomi.didvc.edge.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 People-flow wallet acceptance: the full offer → hold → present
 * loop through the wallet backend API. A professional-qualification
 * credential ({@code hkt_profcred_v1}) is offered by the issuer, redeemed
 * and held by the wallet, and presented against a verifier authorization
 * request — the verification result comes back valid with the disclosed
 * qualification claims (the T-4.3 reference-verifier acceptance too).
 */
@SpringBootTest(properties = {
        "didvc.edge.internal-api-key=test-key"
})
@AutoConfigureMockMvc
class WalletFlowIntegrationTest {

    @TestConfiguration
    static class WalletTestConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }

        @Bean
        @Primary
        WalletProtocolClient walletProtocolClient(MockMvc mockMvc) {
            return new MockMvcWalletProtocolClient(mockMvc);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void offerHoldPresentFlowForProfessionalCredential() throws Exception {
        // The professional-body issuer offers a qualification credential
        Map<String, Object> offerBody = new LinkedHashMap<>();
        offerBody.put("schemaId", "hkt-profcred-v1");
        offerBody.put("vct", "hkt_profcred_v1");
        offerBody.put("subjectId", "didvc:pairwise:engineer-1");
        offerBody.put("kid", platformApi.getIssuerKid());
        offerBody.put("alwaysDisclosedClaims", Map.of(
                "qualificationCode", "CIV-STRUCT-3",
                "issuingBody", "HKIE",
                "validUntilYear", 2030));
        MvcResult offerResult = mockMvc.perform(post("/hkt/internal/offers")
                        .header("X-Api-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offerBody)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode offer = objectMapper.readTree(offerResult.getResponse().getContentAsString());

        // The relying verifier trusts this issuer for this credential type
        platformApi.trust("hkt", InMemoryPlatformApi.ISSUER_DID, "hkt_profcred_v1");

        // Hold: the wallet redeems the offer through the wallet API
        MvcResult redeemResult = mockMvc.perform(post("/wallet/wallet-1/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("offer", offer))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode held = objectMapper.readTree(redeemResult.getResponse().getContentAsString());
        String credentialId = held.get("credentialId").asText();
        assertNotNull(credentialId);
        assertEquals("dc+sd-jwt", held.get("format").asText());
        assertEquals("hkt_profcred_v1", held.get("vct").asText());
        assertEquals(InMemoryPlatformApi.ISSUER_DID, held.get("issuerDid").asText());

        // Storage listing shows the held credential
        mockMvc.perform(get("/wallet/wallet-1/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credentialId").value(credentialId))
                .andExpect(jsonPath("$[0].vct").value("hkt_profcred_v1"))
                .andExpect(jsonPath("$[0].credential").doesNotExist());

        // The holder key is published for proof verification
        mockMvc.perform(get("/wallet/wallet-1/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("OKP"));

        // Present: the verifier starts an authorization request (DCQL for
        // the qualification claim), the wallet builds the key-bound
        // presentation and submits it
        Map<String, Object> dcql = Map.of("credentials", java.util.List.of(Map.of(
                "id", "credential-1",
                "format", "dc+sd-jwt",
                "meta", Map.of("vct_values", java.util.List.of("hkt_profcred_v1")),
                "claims", java.util.List.of(Map.of("path", java.util.List.of("qualificationCode"))))));
        Map<String, Object> authorize = new LinkedHashMap<>();
        authorize.put("client_id", "https://verifier.example.hkt");
        authorize.put("response_uri", "http://localhost:8080/hkt/vp/direct_post");
        authorize.put("nonce", "nonce-wallet-1");
        authorize.put("dcql_query", dcql);
        MvcResult authorizeResult = mockMvc.perform(post("/hkt/vp/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authorize)))
                .andExpect(status().isOk())
                .andReturn();
        String requestUri = objectMapper.readTree(authorizeResult.getResponse().getContentAsString())
                .get("request_uri").asText();

        MvcResult presentResult = mockMvc.perform(post("/wallet/wallet-1/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestUri", requestUri))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode result = objectMapper.readTree(presentResult.getResponse().getContentAsString());
        assertTrue(result.get("valid").asBoolean());
        assertEquals("hkt_profcred_v1", result.get("vct").asText());
        assertEquals("CIV-STRUCT-3", result.get("claims").get("qualificationCode").asText());

        // The credential can be removed from the wallet
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/wallet/wallet-1/credentials/" + credentialId))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/wallet/wallet-1/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void presentWithoutHeldCredentialIsRejected() throws Exception {
        Map<String, Object> dcql = Map.of("credentials", java.util.List.of(Map.of(
                "id", "credential-1",
                "format", "dc+sd-jwt",
                "meta", Map.of("vct_values", java.util.List.of("hkt_kyc_v1")))));
        Map<String, Object> authorize = new LinkedHashMap<>();
        authorize.put("client_id", "https://verifier.example.hkt");
        authorize.put("response_uri", "http://localhost:8080/hkt/vp/direct_post");
        authorize.put("nonce", "nonce-empty-wallet");
        authorize.put("dcql_query", dcql);
        MvcResult authorizeResult = mockMvc.perform(post("/hkt/vp/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authorize)))
                .andExpect(status().isOk())
                .andReturn();
        String requestUri = objectMapper.readTree(authorizeResult.getResponse().getContentAsString())
                .get("request_uri").asText();

        mockMvc.perform(post("/wallet/empty-wallet/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("requestUri", requestUri))))
                .andExpect(status().isNotFound());
    }

    @Test
    void offerRedemptionRequiresOffer() throws Exception {
        mockMvc.perform(post("/wallet/wallet-1/offers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storageListingIsPerWallet() throws Exception {
        mockMvc.perform(get("/wallet/other-wallet/credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
