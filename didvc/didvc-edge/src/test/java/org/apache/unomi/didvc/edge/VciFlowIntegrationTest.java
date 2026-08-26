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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OID4VCI issuance flow end to end: offer creation, pre-authorized-code
 * token exchange, credential delivery, and verification of the delivered
 * SD-JWT against the issuer key.
 */
@SpringBootTest(properties = {
        "didvc.edge.internal-api-key=test-key"
})
@AutoConfigureMockMvc
class VciFlowIntegrationTest {

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void offerTokenCredentialFlowDeliversVerifiableSdJwt() throws Exception {
        // Issuer metadata is well-formed
        MvcResult metadataResult = mockMvc.perform(get("/hkt/.well-known/openid-credential-issuer"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode metadata = objectMapper.readTree(metadataResult.getResponse().getContentAsString());
        assertTrue(metadata.get("credential_endpoint").asText().contains("/hkt/credential"));
        assertEquals("vc+sd-jwt",
                metadata.get("credential_configurations_supported").get("hkt_kyc_v1").get("format").asText());

        // Internal offer creation (admin API key)
        Map<String, Object> offerBody = new LinkedHashMap<>();
        offerBody.put("schemaId", "hkt-kyc-v1");
        offerBody.put("subjectId", "didvc:pairwise:abc123");
        offerBody.put("kid", platformApi.getIssuerKid());
        offerBody.put("alwaysDisclosedClaims", Map.of("kycLevel", "REMOTE_FULL"));
        offerBody.put("selectivelyDisclosedClaims", Map.of("givenName", "Yat", "nationality", "HK"));
        MvcResult offerResult = mockMvc.perform(post("/hkt/internal/offers")
                        .header("X-Api-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offerBody)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode offer = objectMapper.readTree(offerResult.getResponse().getContentAsString());
        String preAuthCode = offer.get("grants")
                .get("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                .get("pre-authorized_code").asText();
        assertNotNull(preAuthCode);

        // Internal offer endpoint requires the admin key
        mockMvc.perform(post("/hkt/internal/offers")
                        .header("X-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offerBody)))
                .andExpect(status().isUnauthorized());

        // Pre-authorized code token exchange
        MvcResult tokenResult = mockMvc.perform(post("/hkt/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        .param("pre-authorized_code", preAuthCode))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tokenResponse = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        String accessToken = tokenResponse.get("access_token").asText();
        assertNotNull(accessToken);

        // Credential delivery
        MvcResult credentialResult = mockMvc.perform(post("/hkt/credential")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode credentialResponse = objectMapper.readTree(credentialResult.getResponse().getContentAsString());
        assertEquals("vc+sd-jwt", credentialResponse.get("format").asText());
        String credential = credentialResponse.get("credential").asText();

        // The delivered credential verifies against the issuer key and
        // carries the expected vct and disclosed claims
        SdJwtPresentation presentation = new SdJwtParser().parse(credential);
        assertTrue(presentation.verifySignature(platformApi.getIssuerKey().toPublicJWK()));
        assertEquals("hkt_kyc_v1", presentation.getClaims().get("vct"));
        assertEquals("REMOTE_FULL", presentation.getClaims().get("kycLevel"));
        assertEquals(2, presentation.getDisclosedClaims().size());
        assertEquals("Yat", presentation.getDisclosedClaims().get("givenName"));
    }

    @Test
    void unknownCodeRejectedAtTokenEndpoint() throws Exception {
        mockMvc.perform(post("/hkt/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        .param("pre-authorized_code", "unknown-code"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void holderBindingIncludedWhenHolderKeyProvided() throws Exception {
        OctetKeyPair holderKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        Map<String, Object> offerBody = new LinkedHashMap<>();
        offerBody.put("schemaId", "hkt-kyc-v1");
        offerBody.put("subjectId", "didvc:pairwise:def456");
        offerBody.put("kid", platformApi.getIssuerKid());
        offerBody.put("holderPublicJwkJson", holderKey.toPublicJWK().toJSONString());
        offerBody.put("alwaysDisclosedClaims", Map.of("kycLevel", "REMOTE_FULL"));

        MvcResult offerResult = mockMvc.perform(post("/hkt/internal/offers")
                        .header("X-Api-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(offerBody)))
                .andExpect(status().isOk())
                .andReturn();
        String preAuthCode = objectMapper.readTree(offerResult.getResponse().getContentAsString())
                .get("grants").get("urn:ietf:params:oauth:grant-type:pre-authorized_code")
                .get("pre-authorized_code").asText();
        MvcResult tokenResult = mockMvc.perform(post("/hkt/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "urn:ietf:params:oauth:grant-type:pre-authorized_code")
                        .param("pre-authorized_code", preAuthCode))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString())
                .get("access_token").asText();
        MvcResult credentialResult = mockMvc.perform(post("/hkt/credential")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String credential = objectMapper.readTree(credentialResult.getResponse().getContentAsString())
                .get("credential").asText();

        SdJwtPresentation presentation = new SdJwtParser().parse(credential);
        assertEquals(holderKey.computeThumbprint().toString(),
                com.nimbusds.jose.jwk.JWK.parse(
                        (Map<String, Object>) ((Map<String, Object>) presentation.getClaims().get("cnf")).get("jwk"))
                        .computeThumbprint().toString());
    }
}
