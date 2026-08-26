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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OID4VCI authorization-code grant with PKCE: authorize (code issued via
 * redirect), token exchange with code verifier, and on-demand credential
 * issuance bound to the authenticated subject.
 */
@SpringBootTest(properties = {
        "didvc.edge.internal-api-key=test-key"
})
@AutoConfigureMockMvc
class VciAuthorizationCodeFlowTest {

    @TestConfiguration
    static class FakePlatformConfiguration {
        @Bean
        @Primary
        InMemoryPlatformApi inMemoryPlatformApi() {
            return new InMemoryPlatformApi();
        }
    }

    private static final String TENANT = "hkt";
    private static final String REDIRECT_URI = "https://wallet.example.hkt/callback";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private InMemoryPlatformApi platformApi;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String s256Challenge(String verifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private String authorizeForCode(String codeVerifier) throws Exception {
        String codeChallenge = s256Challenge(codeVerifier);
        MvcResult result = mockMvc.perform(get("/" + TENANT + "/authorize")
                        .param("response_type", "code")
                        .param("client_id", "wallet-app")
                        .param("redirect_uri", REDIRECT_URI)
                        .param("state", "state-1")
                        .param("code_challenge", codeChallenge)
                        .param("code_challenge_method", "S256")
                        .param("subject_id", "didvc:pairwise:abc123")
                        .param("schema_id", "hkt-kyc-v1")
                        .param("kid", platformApi.getIssuerKid()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertNotNull(location);
        assertTrue(location.startsWith(REDIRECT_URI + "?"));
        String codePart = location.substring(location.indexOf("code=") + "code=".length());
        int amp = codePart.indexOf('&');
        return amp < 0 ? codePart : codePart.substring(0, amp);
    }

    private String exchangeCode(String code, String codeVerifier, String redirectUri) throws Exception {
        MvcResult result = mockMvc.perform(post("/" + TENANT + "/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", redirectUri)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
    }

    @Test
    void authorizationCodeFlowIssuesVerifiableCredential() throws Exception {
        String verifier = "a-sufficiently-long-code-verifier-0123456789abcdefghijklmnop";
        String code = authorizeForCode(verifier);
        String accessToken = exchangeCode(code, verifier, REDIRECT_URI);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claims", Map.of("kycLevel", "REMOTE_FULL", "givenName", "Yat"));
        body.put("selectiveClaims", List.of("givenName"));
        MvcResult credentialResult = mockMvc.perform(post("/" + TENANT + "/credential")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode credentialResponse = objectMapper.readTree(credentialResult.getResponse().getContentAsString());
        assertEquals("dc+sd-jwt", credentialResponse.get("format").asText());

        SdJwtPresentation presentation = new SdJwtParser()
                .parse(credentialResponse.get("credential").asText());
        assertTrue(presentation.verifySignature(platformApi.getIssuerKey().toPublicJWK()));
        assertEquals("hkt_kyc_v1", presentation.getClaims().get("vct"));
        assertEquals("Yat", presentation.getDisclosedClaims().get("givenName"));
    }

    @Test
    void wrongCodeVerifierRejected() throws Exception {
        String code = authorizeForCode("the-real-verifier-0123456789abcdefghijklmnopqrstuv");
        mockMvc.perform(post("/" + TENANT + "/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", "a-different-verifier-0123456789abcdefghijklmnop"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codeIsSingleUse() throws Exception {
        String verifier = "a-sufficiently-long-code-verifier-0123456789abcdefghijklmnop";
        String code = authorizeForCode(verifier);
        exchangeCode(code, verifier, REDIRECT_URI);
        mockMvc.perform(post("/" + TENANT + "/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirectUriMismatchRejected() throws Exception {
        String verifier = "a-sufficiently-long-code-verifier-0123456789abcdefghijklmnop";
        String code = authorizeForCode(verifier);
        mockMvc.perform(post("/" + TENANT + "/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "https://attacker.example.hkt/callback")
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metadataAdvertisesAuthorizationServer() throws Exception {
        MvcResult metadataResult = mockMvc.perform(get("/" + TENANT + "/.well-known/openid-credential-issuer"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode metadata = objectMapper.readTree(metadataResult.getResponse().getContentAsString());
        assertTrue(metadata.get("authorization_servers").get(0).asText().contains("/" + TENANT));
    }
}
