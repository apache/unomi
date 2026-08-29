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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Test variant of {@link WalletProtocolClient} that drives the in-process
 * edge through MockMvc — the wallet backend, issuer and verifier all run
 * in the same test application.
 */
class MockMvcWalletProtocolClient implements WalletProtocolClient {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    MockMvcWalletProtocolClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Override
    public JsonNode fetchIssuerMetadata(String credentialIssuer) {
        return getJson(credentialIssuer + "/.well-known/openid-credential-issuer");
    }

    @Override
    public JsonNode tokenRequest(String tokenEndpoint, String grantType, String grantParam, String grantValue) {
        MvcResult result = perform(post(pathOf(tokenEndpoint))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=" + grantType + "&" + grantParam + "=" + grantValue));
        return toJson(result);
    }

    @Override
    public JsonNode credentialRequest(String credentialEndpoint, String accessToken, Map<String, Object> body) {
        MvcResult result = perform(post(pathOf(credentialEndpoint))
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(serialize(body)));
        return toJson(result);
    }

    @Override
    public String fetchRequestObject(String requestUri) {
        MvcResult result = perform(get(pathOf(requestUri)));
        return bodyOf(result);
    }

    @Override
    public JsonNode postPresentation(String responseUri, Map<String, Object> submission) {
        MvcResult result = perform(post(pathOf(responseUri))
                .contentType(MediaType.APPLICATION_JSON)
                .content(serialize(submission)));
        return toJson(result);
    }

    private JsonNode getJson(String url) {
        MvcResult result = perform(get(pathOf(url)));
        return toJson(result);
    }

    private MvcResult perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        try {
            MvcResult result = mockMvc.perform(request).andReturn();
            if (result.getResponse().getStatus() >= 400) {
                throw new IllegalStateException("wallet protocol call failed with HTTP "
                        + result.getResponse().getStatus() + ": " + bodyOf(result));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("wallet protocol call failed", e);
        }
    }

    private JsonNode toJson(MvcResult result) {
        try {
            return objectMapper.readTree(bodyOf(result));
        } catch (Exception e) {
            throw new IllegalStateException("unreadable response", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("unserializable request", e);
        }
    }

    private static String bodyOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String pathOf(String url) {
        return URI.create(url).getPath();
    }
}
