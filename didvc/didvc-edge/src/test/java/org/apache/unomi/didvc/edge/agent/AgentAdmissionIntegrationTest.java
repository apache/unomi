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

package org.apache.unomi.didvc.edge.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.edge.platform.PlatformApi;
import org.apache.unomi.didvc.edge.support.InMemoryPlatformApi;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agent-plane gateway admission (FR-ID6): the gateway admits a bound
 * agent from its {@code hkt_agent_binding_v1} credential, rejects
 * unbound agents, and — kill-switch semantics — rejects a bound agent
 * at the next verified call once the binding credential is revoked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentAdmissionIntegrationTest {

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    private static final String TENANT = "hkt-agent-gateway";
    private static final String AGENT_KEY_HASH = "sha256:" + Long.toHexString(System.nanoTime());

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String bindingCredential;
    private PlatformApi.IssuedCredential bindingRecord;

    @BeforeEach
    void setUp() {
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_agent_binding_v1");
        PlatformApi.IssueRequest request = new PlatformApi.IssueRequest();
        request.setTenantId("hkt");
        request.setSchemaId("hkt-agent-binding-v1");
        request.setSubjectId("didvc:pairwise:principal-agent-1");
        request.setKid(platformApi.getIssuerKid());
        request.setAlwaysDisclosedClaims(Map.of(
                "agentPubKeyHash", AGENT_KEY_HASH,
                "principalBindingLevel", "verified HK principal"));
        request.setSelectivelyDisclosedClaims(Map.of("policyScope", "payments"));
        bindingRecord = platformApi.issueCredential("hkt", request);
        bindingCredential = bindingRecord.getCredential();
    }

    @Test
    void admitsBoundAgentAndRejectsUnbound() throws Exception {
        // Unbound: no admission registered
        MvcResult unbound = mockMvc.perform(get("/" + TENANT + "/agents/admission/" + AGENT_KEY_HASH))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode unboundResponse = objectMapper.readTree(unbound.getResponse().getContentAsString());
        assertFalse(unboundResponse.get("admitted").asBoolean());
        assertEquals("agent is not bound", unboundResponse.get("reason").asText());

        // Admit from the binding credential
        MvcResult admitted = mockMvc.perform(post("/" + TENANT + "/agents/admit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", bindingCredential))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode admission = objectMapper.readTree(admitted.getResponse().getContentAsString());
        assertTrue(admission.get("admitted").asBoolean());
        assertEquals(AGENT_KEY_HASH, admission.get("agentPubKeyHash").asText());

        // The gateway's verified call admits the bound agent
        MvcResult check = mockMvc.perform(get("/" + TENANT + "/agents/admission/" + AGENT_KEY_HASH))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(check.getResponse().getContentAsString()).get("admitted").asBoolean());
    }

    @Test
    void revokedBindingRejectedAtNextVerifiedCall() throws Exception {
        mockMvc.perform(post("/" + TENANT + "/agents/admit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", bindingCredential))))
                .andExpect(status().isOk());

        // Revoke the binding: the NEXT admission check must reject
        platformApi.markRevoked(bindingRecord.getItemId());
        MvcResult check = mockMvc.perform(get("/" + TENANT + "/agents/admission/" + AGENT_KEY_HASH))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(check.getResponse().getContentAsString());
        assertFalse(response.get("admitted").asBoolean());
        assertEquals("credential is revoked", response.get("reason").asText());

        // And stays rejected (admission dropped)
        MvcResult again = mockMvc.perform(get("/" + TENANT + "/agents/admission/" + AGENT_KEY_HASH))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode second = objectMapper.readTree(again.getResponse().getContentAsString());
        assertFalse(second.get("admitted").asBoolean());
        assertEquals("agent is not bound", second.get("reason").asText());
    }

    @Test
    void nonBindingCredentialsAreRefusedAtAdmission() throws Exception {
        platformApi.trust(TENANT, InMemoryPlatformApi.ISSUER_DID, "hkt_kyc_v1");
        PlatformApi.IssueRequest request = new PlatformApi.IssueRequest();
        request.setTenantId("hkt");
        request.setSchemaId("hkt-kyc-v1");
        request.setSubjectId("didvc:pairwise:not-an-agent");
        request.setKid(platformApi.getIssuerKid());
        request.setAlwaysDisclosedClaims(Map.of("kycLevel", "REMOTE_FULL"));
        String kycCredential = platformApi.issueCredential("hkt", request).getCredential();

        MvcResult result = mockMvc.perform(post("/" + TENANT + "/agents/admit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("credential", kycCredential))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(response.get("admitted").asBoolean());
        assertTrue(response.get("reason").asText().contains("not an agent binding"));
    }
}
