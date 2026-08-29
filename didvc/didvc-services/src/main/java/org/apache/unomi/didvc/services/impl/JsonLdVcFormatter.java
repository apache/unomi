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
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * JSON-LD verifiable-credential formatter ({@code ldp_vc}): builds a W3C
 * Verifiable Credentials Data Model 2.0 document (context, type,
 * issuer, validFrom/validUntil, credentialSubject, credentialSchema,
 * BitstringStatusList credentialStatus) and returns it as a compact JWS
 * ({@code typ: vc+ld+json}, {@code cty: application/ld+json}) signed by
 * the issuer key, so the credential travels as a single self-contained
 * string. Full Linked-Data-Proof (Data Integrity) signatures remain a
 * follow-up; the data model conforms to VC DM 2.0 today.
 */
@Component(service = CredentialFormatter.class, property = "didvc.format=ldp_vc", immediate = true)
public class JsonLdVcFormatter implements CredentialFormatter {

    public static final String FORMAT = "ldp_vc";

    /** The VC DM 2.0 JSON-LD context. */
    public static final String VC_DM_V2_CONTEXT = "https://www.w3.org/ns/credentials/v2";

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdVcFormatter.class);

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

        Map<String, Object> credential = buildCredential(schema, key, request);
        String credentialJson = toJson(credential);
        String jws = issuerKeyService.signTyped(request.getKid(), credentialJson, "vc+ld+json");
        LOGGER.info("Formatted {}-format credential for schema {} (type={}, subject={})",
                FORMAT, schema.getItemId(), schema.getVct(), request.getSubjectId());
        return jws;
    }

    private Map<String, Object> buildCredential(DidSchema schema, KeyDescriptor key,
                                                CredentialIssueRequest request) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + request.getValidityDays() * 24L * 3600 * 1000);

        Map<String, Object> credential = new LinkedHashMap<>();
        List<String> context = new ArrayList<>();
        context.add(VC_DM_V2_CONTEXT);
        credential.put("@context", context);
        credential.put("id", "urn:didvc:credential:" + UUID.randomUUID());
        List<String> type = new ArrayList<>();
        type.add("VerifiableCredential");
        type.add(schema.getVct());
        credential.put("type", type);
        credential.put("issuer", key.getIssuerDid());
        credential.put("validFrom", xmlDateTime(now));
        credential.put("validUntil", xmlDateTime(expiresAt));

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("id", request.getSubjectId());
        subject.putAll(request.allClaims());
        credential.put("credentialSubject", subject);

        Map<String, Object> credentialSchema = new LinkedHashMap<>();
        credentialSchema.put("id", "urn:didvc:schema:" + schema.getItemId());
        credentialSchema.put("type", "JsonSchema");
        credential.put("credentialSchema", credentialSchema);

        if (request.getStatusListIndex() >= 0 && request.getStatusListUri() != null) {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("id", request.getStatusListUri() + "#" + request.getStatusListIndex());
            status.put("type", "BitstringStatusListEntry");
            status.put("statusPurpose", "revocation");
            status.put("statusListIndex", request.getStatusListIndex());
            status.put("statusListCredential", request.getStatusListUri());
            credential.put("credentialStatus", status);
        }
        return credential;
    }

    private String xmlDateTime(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON-LD credential", e);
        }
    }
}
