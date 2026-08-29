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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * HTTP {@link WalletProtocolClient}: the wallet backend as an OID4VCI
 * wallet client and OID4VP presenter against remote issuers and
 * verifiers.
 */
@Component
public class HttpWalletProtocolClient implements WalletProtocolClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpWalletProtocolClient(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode fetchIssuerMetadata(String credentialIssuer) {
        String url = credentialIssuer + "/.well-known/openid-credential-issuer";
        try {
            return restClient.get().uri(url).retrieve().body(JsonNode.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "issuer metadata fetch failed: " + e.getMessage());
        }
    }

    @Override
    public JsonNode tokenRequest(String tokenEndpoint, String grantType, String grantParam, String grantValue) {
        try {
            String body = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=" + grantType + "&" + grantParam + "=" + grantValue)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "token exchange failed: " + e.getMessage());
        }
    }

    @Override
    public JsonNode credentialRequest(String credentialEndpoint, String accessToken, Map<String, Object> body) {
        try {
            String response = restClient.post()
                    .uri(credentialEndpoint)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "credential request failed: " + e.getMessage());
        }
    }

    @Override
    public String fetchRequestObject(String requestUri) {
        try {
            return restClient.get().uri(requestUri).retrieve().body(String.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "request object fetch failed: " + e.getMessage());
        }
    }

    @Override
    public JsonNode postPresentation(String responseUri, Map<String, Object> submission) {
        try {
            String response = restClient.post()
                    .uri(responseUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(submission))
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "presentation submission failed: " + e.getMessage());
        }
    }
}
