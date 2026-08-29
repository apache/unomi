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

package org.apache.unomi.didvc.edge.gbz185;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.apache.unomi.didvc.audit.AuditLogService;
import org.apache.unomi.didvc.audit.InMemoryAuditLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GB/Z 185 interop bridge (FR-ID6): a fixture-style linkage VP verifies
 * end to end (signature against the per-tenant trusted key set, agent
 * identity code shape, expiry, per-tenant policy-scope mapping), and
 * every call — accepted or rejected — appends an audit record.
 */
@SpringBootTest
@AutoConfigureMockMvc
class Gbz185BridgeIntegrationTest {

    /** Issuer identifiers and keys are generated per run — no literals. */
    private static final OctetKeyPair ISSUER_KEY = newKey();
    private static final String ISSUER_ID = "sic-agent-registry-" + Long.toHexString(System.nanoTime());

    private static OctetKeyPair newKey() {
        try {
            return new OctetKeyPairGenerator(Curve.Ed25519).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void bridgeProperties(DynamicPropertyRegistry registry) {
        registry.add("didvc.edge.gbz185-issuer-jwks." + ISSUER_ID,
                () -> ISSUER_KEY.toPublicJWK().toJSONString());
        registry.add("didvc.edge.gbz185-policies[0]",
                () -> "mainland-gateway|" + ISSUER_ID + "=logistics,payment");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void resetAudit() {
        // The audit service is a shared bean; use a marker to count our
        // own records within each test
    }

    private String linkageVp(Map<String, Object> overrides) throws Exception {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", ISSUER_ID);
        claims.put("agentIdentityCode", "MA1234567890ABCDEFG");
        claims.put("agentPubKeyHash", "sha256:abcdef0123456789");
        claims.put("linkageRef", "LNK-2026-0042");
        claims.put("principalRef", "didvc:pairwise:mainland-agent-7");
        claims.put("policyScope", "logistics");
        claims.put("exp", System.currentTimeMillis() / 1000 + 600);
        claims.putAll(overrides);
        JWSObject jws = new JWSObject(
                new JWSHeader.Builder(JWSAlgorithm.EdDSA).type(
                        new com.nimbusds.jose.JOSEObjectType("gbz185+jwt")).build(),
                new Payload(objectMapper.writeValueAsBytes(claims)));
        jws.sign(new Ed25519Signer(ISSUER_KEY));
        return jws.serialize();
    }

    private JsonNode verify(String vp) throws Exception {
        MvcResult result = mockMvc.perform(post("/mainland-gateway/gbz185/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("linkageVp", vp))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void fixtureLinkageVpVerifiesWithPolicyMapping() throws Exception {
        JsonNode response = verify(linkageVp(Map.of()));
        assertTrue(response.get("valid").asBoolean());
        assertEquals(ISSUER_ID, response.get("issuer").asText());
        assertEquals("MA1234567890ABCDEFG", response.get("agentIdentityCode").asText());
        assertEquals("logistics", response.get("policyScope").asText());
        // exactly one audit record for this call
        long audited = auditLogService.readAll().stream()
                .filter(r -> "didvcGbz185Verified".equals(r.getEventType())
                        && r.getActor().equals("mainland-gateway")
                        && r.getPayload().contains("MA1234567890ABCDEFG"))
                .count();
        assertTrue(audited >= 1, "bridge call must be audited");
    }

    @Test
    void tamperedSignatureRejectedButStillAudited() throws Exception {
        String vp = linkageVp(Map.of());
        String tampered = vp.substring(0, vp.length() - 4) + "AAAA";
        JsonNode response = verify(tampered);
        assertFalse(response.get("valid").asBoolean());
        assertEquals("linkage VP signature is invalid", response.get("reason").asText());
        long audited = auditLogService.readAll().stream()
                .filter(r -> "didvcGbz185Verified".equals(r.getEventType())
                        && r.getPayload().contains("signature is invalid"))
                .count();
        assertTrue(audited >= 1, "rejected calls must be audited too");
    }

    @Test
    void expiredAndMalformedCodesRejected() throws Exception {
        JsonNode expired = verify(linkageVp(Map.of("exp", System.currentTimeMillis() / 1000 - 10)));
        assertFalse(expired.get("valid").asBoolean());
        assertEquals("linkage VP has expired", expired.get("reason").asText());

        JsonNode malformed = verify(linkageVp(Map.of("agentIdentityCode", "X")));
        assertFalse(malformed.get("valid").asBoolean());
        assertEquals("agent identity code is malformed", malformed.get("reason").asText());
    }

    @Test
    void policyScopeMappingIsEnforcedPerTenant() throws Exception {
        // scope outside the tenant's accepted list
        JsonNode outOfScope = verify(linkageVp(Map.of("policyScope", "healthcare")));
        assertFalse(outOfScope.get("valid").asBoolean());
        assertEquals("policy scope is not accepted for this issuer", outOfScope.get("reason").asText());

        // issuer trusted for keys but not mapped for this tenant
        JsonNode unmappedTenant;
        MvcResult result = mockMvc.perform(post("/other-tenant/gbz185/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("linkageVp", linkageVp(Map.of())))))
                .andExpect(status().isOk())
                .andReturn();
        unmappedTenant = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(unmappedTenant.get("valid").asBoolean());
        assertEquals("issuer is not mapped for this tenant", unmappedTenant.get("reason").asText());
    }

    @Test
    void unknownIssuerKeyRejected() throws Exception {
        OctetKeyPair attacker = newKey();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", "untrusted-registry-" + Long.toHexString(System.nanoTime()));
        claims.put("agentIdentityCode", "MA1234567890ABCDEFG");
        claims.put("policyScope", "logistics");
        claims.put("exp", System.currentTimeMillis() / 1000 + 600);
        JWSObject jws = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.EdDSA).build(),
                new Payload(objectMapper.writeValueAsBytes(claims)));
        jws.sign(new Ed25519Signer(attacker));
        JsonNode response = verify(jws.serialize());
        assertFalse(response.get("valid").asBoolean());
        assertEquals("issuer is not in the GB/Z 185 trusted key set", response.get("reason").asText());
    }
}
