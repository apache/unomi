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
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
import org.apache.unomi.didvc.metering.InMemoryMeteringSink;
import org.apache.unomi.didvc.sdjwt.KeyBindingJwtBuilder;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OID4VP verification end to end: authorization request, signed request
 * object, presentation with key binding, and the full rejection matrix
 * (nonce, replay, revoked, untrusted, tampered key binding) plus audit and
 * metering evidence.
 */
@SpringBootTest(properties = {
        "didvc.edge.internal-api-key=test-key"
})
@AutoConfigureMockMvc
class VpVerificationIntegrationTest {

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    private static final String TENANT = "bank-a";
    private static final String NONCE = "nonce-verify-123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;
    @Autowired
    private InMemoryMeteringSink meteringSink;
    @Autowired
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OctetKeyPair holderKey;
    private String issuedCredential;
    private PlatformApi.IssuedCredential issuedRecord;

    @BeforeEach
    void setUp() throws Exception {
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_kyc_v1");
        meteringSink.clear();
        holderKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();

        PlatformApi.IssueRequest request = new PlatformApi.IssueRequest();
        request.setTenantId("hkt");
        request.setSchemaId("hkt-kyc-v1");
        request.setSubjectId("didvc:pairwise:abc123");
        request.setKid(platformApi.getIssuerKid());
        request.setHolderPublicJwkJson(holderKey.toPublicJWK().toJSONString());
        request.setAlwaysDisclosedClaims(Map.of("kycLevel", "REMOTE_FULL"));
        request.setSelectivelyDisclosedClaims(Map.of("givenName", "Yat", "nationality", "HK"));
        issuedRecord = platformApi.issueCredential("hkt", request);
        issuedCredential = issuedRecord.getCredential();
    }

    private String authorize() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", "https://bank-a.example.hkt");
        body.put("nonce", NONCE);
        body.put("claims", Map.of("hkt_kyc_v1", List.of("kycLevel", "givenName")));
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/vp/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        String requestUri = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("request_uri").asText();
        String requestId = requestUri.substring(requestUri.lastIndexOf('/') + 1);

        // The request object must be a signed JWT carrying the nonce
        MvcResult requestObject = mockMvc.perform(get("/" + TENANT + "/vp/request/" + requestId))
                .andExpect(status().isOk())
                .andReturn();
        com.nimbusds.jwt.SignedJWT signedJwt = com.nimbusds.jwt.SignedJWT.parse(
                requestObject.getResponse().getContentAsString());
        assertEquals(NONCE, signedJwt.getJWTClaimsSet().getStringClaim("nonce"));
        return requestId;
    }

    private String buildVp(String nonce, List<String> disclosures) throws Exception {
        String[] parts = issuedCredential.split("~");
        String kbJwt = new KeyBindingJwtBuilder().build(holderKey, nonce,
                "http://localhost:8080", disclosures, new Date());
        return parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + kbJwt;
    }

    private String submit(String requestId, String nonce, String vpToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state", requestId);
        body.put("nonce", nonce);
        body.put("vp_token", vpToken);
        return mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void validPresentationIsAcceptedAndMetered() throws Exception {
        String requestId = authorize();
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));

        MvcResult result = mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId,
                                "nonce", NONCE,
                                "vp_token", vp))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("valid").asBoolean());
        assertEquals("hkt_kyc_v1", response.get("vct").asText());
        assertEquals("Yat", response.get("claims").get("givenName").asText());
        assertEquals("REMOTE_FULL", response.get("alwaysDisclosed").get("kycLevel").asText());

        // Audit + metering evidence
        assertTrue(auditLogService.verifyChain());
        assertFalse(auditLogService.readAll().isEmpty());
        assertEquals(1, meteringSink.getRecords().size());
        assertEquals(TENANT, meteringSink.getRecords().get(0).getVerifierTenantId());
    }

    @Test
    void wrongNonceRejected() throws Exception {
        String requestId = authorize();
        String[] parts = issuedCredential.split("~");
        String vp = buildVp("other-nonce", Arrays.asList(parts[1], parts[2]));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replayRejected() throws Exception {
        String requestId = authorize();
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        String body = objectMapper.writeValueAsString(Map.of(
                "state", requestId, "nonce", NONCE, "vp_token", vp));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        // The nonce/state pair was consumed — a second submission is rejected
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        // Only the first verification is billable
        assertEquals(1, meteringSink.getRecords().size());
    }

    @Test
    void revokedCredentialRejected() throws Exception {
        String requestId = authorize();
        platformApi.markRevoked(issuedRecord.getItemId());
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void untrustedIssuerRejected() throws Exception {
        platformApi.untrustAll();
        String requestId = authorize();
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tamperedKeyBindingRejected() throws Exception {
        String requestId = authorize();
        String[] parts = issuedCredential.split("~");
        OctetKeyPair attacker = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        String kbJwt = new KeyBindingJwtBuilder().build(attacker, NONCE,
                "http://localhost:8080", Arrays.asList(parts[1], parts[2]), new Date());
        String vp = parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + kbJwt;
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }

    // ---- DCQL ----

    private String authorizeDcql(Map<String, Object> dcqlQuery) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", "https://bank-a.example.hkt");
        body.put("nonce", NONCE);
        body.put("dcql_query", dcqlQuery);
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/vp/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        String requestUri = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("request_uri").asText();
        return requestUri.substring(requestUri.lastIndexOf('/') + 1);
    }

    private Map<String, Object> kycDcqlQuery(List<Object> givenNameValues) {
        Map<String, Object> claim = new LinkedHashMap<>();
        claim.put("path", List.of("givenName"));
        if (givenNameValues != null) {
            claim.put("values", givenNameValues);
        }
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", "kyc_credential");
        credential.put("format", "vc+sd-jwt");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("vct_values", List.of("hkt_kyc_v1"));
        credential.put("meta", meta);
        credential.put("claims", List.of(claim));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("credentials", List.of(credential));
        return query;
    }

    @Test
    void dcqlQueryIsSignedAndEnforced() throws Exception {
        String requestId = authorizeDcql(kycDcqlQuery(List.of("Yat")));

        // The signed request object carries the dcql_query
        MvcResult requestObject = mockMvc.perform(get("/" + TENANT + "/vp/request/" + requestId))
                .andExpect(status().isOk())
                .andReturn();
        com.nimbusds.jwt.SignedJWT signedJwt = com.nimbusds.jwt.SignedJWT.parse(
                requestObject.getResponse().getContentAsString());
        JsonNode dcqlInRequest = objectMapper.readTree(objectMapper.writeValueAsString(
                signedJwt.getJWTClaimsSet().getJSONObjectClaim("dcql_query")));
        assertEquals("hkt_kyc_v1", dcqlInRequest.get("credentials").get(0)
                .get("meta").get("vct_values").get(0).asText());

        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("valid").asBoolean());
        assertEquals("Yat", response.get("claims").get("givenName").asText());
    }

    @Test
    void dcqlExpectedValueMismatchRejected() throws Exception {
        String requestId = authorizeDcql(kycDcqlQuery(List.of("Someone Else")));
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dcqlUnrequestedVctRejected() throws Exception {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", "other");
        credential.put("format", "vc+sd-jwt");
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("vct_values", List.of("hkt_profcred_v1"));
        credential.put("meta", meta);
        credential.put("claims", List.of(Map.of("path", List.of("kycLevel"))));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("credentials", List.of(credential));

        String requestId = authorizeDcql(query);
        String[] parts = issuedCredential.split("~");
        String vp = buildVp(NONCE, Arrays.asList(parts[1], parts[2]));
        mockMvc.perform(post("/" + TENANT + "/vp/direct_post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "state", requestId, "nonce", NONCE, "vp_token", vp))))
                .andExpect(status().isBadRequest());
    }
}
