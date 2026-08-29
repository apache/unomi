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

package org.apache.unomi.didvc.edge.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import org.apache.unomi.didvc.edge.EdgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.text.ParseException;
import java.util.Map;

/**
 * {@link PlatformApi} over the Unomi REST API, tenant-scoped and
 * authenticated with the platform API key.
 */
@Component
public class HttpPlatformApi implements PlatformApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPlatformApi.class);

    private final RestClient restClient;
    private final EdgeProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpPlatformApi(EdgeProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getPlatformBaseUrl())
                .defaultHeader("X-Api-Key", properties.getPlatformApiKey())
                .build();
    }

    @Override
    public IssuedCredential issueCredential(String tenantId, IssueRequest request) {
        request.setTenantId(tenantId);
        return restClient.post()
                .uri("/didvc/credentials")
                .body(request)
                .retrieve()
                .body(IssuedCredential.class);
    }

    @Override
    public IssuedCredential getCredential(String tenantId, String recordId) {
        return restClient.get()
                .uri("/didvc/credentials/{recordId}", recordId)
                .retrieve()
                .body(IssuedCredential.class);
    }

    @Override
    public IssuedCredential rebindCredential(String tenantId, String recordId, String holderPublicJwkJson) {
        return restClient.post()
                .uri("/didvc/credentials/{recordId}/rebind", recordId)
                .body(Map.of("holderPublicJwkJson", holderPublicJwkJson))
                .retrieve()
                .body(IssuedCredential.class);
    }

    @Override
    public boolean isStatusRevoked(String tenantId, String statusListId, int index) {
        JsonNode response = restClient.get()
                .uri("/didvc/statuslists/{id}/revoked?index={index}", statusListId, index)
                .retrieve()
                .body(JsonNode.class);
        return response != null && response.path("revoked").asBoolean(false);
    }

    @Override
    public boolean isTrusted(String tenantId, String issuerDid, String vct) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/didvc/trust-check")
                        .queryParam("verifierTenantId", tenantId)
                        .queryParam("issuerDid", issuerDid)
                        .queryParam("vct", vct)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        return response != null && response.path("trusted").asBoolean(false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public JWK resolveIssuerKey(String issuerDid, String kid) {
        JsonNode didDocument = restClient.get()
                .uri("/didvc/dids/{did}", issuerDid)
                .retrieve()
                .body(JsonNode.class);
        if (didDocument == null) {
            return null;
        }
        for (JsonNode method : didDocument.path("verificationMethod")) {
            String methodId = method.path("id").asText("");
            if (methodId.endsWith("#" + kid) && method.has("publicKeyJwk")) {
                try {
                    Map<String, Object> jwk = objectMapper.convertValue(method.get("publicKeyJwk"), Map.class);
                    return JWK.parse(jwk);
                } catch (ParseException e) {
                    LOGGER.warn("Unreadable public JWK for {}#{}", issuerDid, kid, e);
                    return null;
                }
            }
        }
        return null;
    }
}
