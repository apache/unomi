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

package org.apache.unomi.didvc.services.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.CredentialFormatter;
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.sdjwt.SdJwtBuilder;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SD-JWT VC credential formatter ({@code vc+sd-jwt}): builds the SD-JWT
 * claims and disclosures and signs through the issuer key service, with
 * selective disclosure for the request's selectively-disclosable claims.
 */
@Component(service = CredentialFormatter.class, property = "didvc.format=vc+sd-jwt", immediate = true)
public class SdJwtVcFormatter implements CredentialFormatter {

    public static final String FORMAT = "vc+sd-jwt";

    private static final Logger LOGGER = LoggerFactory.getLogger(SdJwtVcFormatter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Reference
    private IssuerKeyService issuerKeyService;
    @Reference
    private CredentialSchemaService schemaService;

    public void setIssuerKeyService(IssuerKeyService issuerKeyService) {
        this.issuerKeyService = issuerKeyService;
    }

    public void setSchemaService(CredentialSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public String format(CredentialIssueRequest request) {
        DidSchema schema = schemaService.getSchema(request.getSchemaId());
        if (schema == null) {
            throw new IllegalArgumentException("Unknown schema: " + request.getSchemaId());
        }
        KeyDescriptor key = issuerKeyService.getKey(request.getKid());
        if (key == null) {
            throw new IllegalArgumentException("Unknown signing key: " + request.getKid());
        }

        SdJwtBuilder.CredentialPayload payload = new SdJwtBuilder.CredentialPayload();
        payload.setVct(schema.getVct());
        payload.setIss(key.getIssuerDid());
        payload.setSub(request.getSubjectId());
        payload.setIssuedAt(new Date());
        payload.setExpiresAt(new Date(System.currentTimeMillis()
                + request.getValidityDays() * 24L * 3600 * 1000));
        if (request.getStatusListIndex() >= 0 && request.getStatusListUri() != null) {
            Map<String, Object> statusList = new LinkedHashMap<>();
            statusList.put("idx", request.getStatusListIndex());
            statusList.put("uri", request.getStatusListUri());
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("status_list", statusList);
            payload.setStatus(status);
        }
        if (request.getHolderPublicJwkJson() != null) {
            Map<String, Object> cnf = new LinkedHashMap<>();
            cnf.put("jwk", parseJwk(request.getHolderPublicJwkJson()));
            payload.setCnf(cnf);
        }
        payload.getAlwaysDisclosed().putAll(request.getAlwaysDisclosedClaims());
        payload.getSelectivelyDisclosed().putAll(request.getSelectivelyDisclosedClaims());

        SdJwtBuilder.BuildResult result = SdJwtBuilder.buildClaims(payload);
        String payloadJson = toJson(result.getClaims());
        String jws = issuerKeyService.signTyped(request.getKid(), payloadJson, "vc+sd-jwt");
        StringBuilder sb = new StringBuilder(jws);
        for (String disclosure : result.getDisclosures()) {
            sb.append('~').append(disclosure);
        }
        LOGGER.info("Formatted {}-format credential for schema {} (vct={}, {} selective claims)",
                FORMAT, schema.getItemId(), schema.getVct(), result.getDisclosures().size());
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJwk(String jwkJson) {
        try {
            return objectMapper.readValue(jwkJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Holder public JWK is unreadable", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize credential payload", e);
        }
    }
}
