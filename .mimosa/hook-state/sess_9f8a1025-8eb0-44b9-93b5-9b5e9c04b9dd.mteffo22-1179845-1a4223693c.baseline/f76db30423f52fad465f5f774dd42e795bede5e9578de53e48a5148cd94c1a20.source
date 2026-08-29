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

import com.nimbusds.jose.jwk.JWK;
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code ldp_vc} formatter: VC DM 2.0 document structure, the W3C
 * UniversityDegree example shape as a test vector, and the
 * sign → parse → verify round trip.
 */
class JsonLdVcFormatterTest {

    private static final String TENANT = "professional-body-a";

    private PersistenceService persistenceService;
    private IssuerKeyService keyService;
    private CredentialSchemaService schemaService;
    private JsonLdVcFormatter formatter;
    private KeyDescriptor issuerKey;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
        schemaService = new CredentialSchemaServiceImpl();
        ((CredentialSchemaServiceImpl) schemaService).setPersistenceService(persistenceService);
        formatter = new JsonLdVcFormatter();
        formatter.setIssuerKeyService(keyService);
        formatter.setSchemaService(schemaService);

        DidSchema schema = new DidSchema("hkt-profcred-v1");
        schema.setVct("hkt_profcred_v1");
        schema.setTenantId(TENANT);
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("qualification", "awardingBody", "scope")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("qualification", "awardingBody")));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("qualification", "string");
        claimTypes.put("awardingBody", "string");
        claimTypes.put("scope", "string");
        schema.setClaimTypes(claimTypes);
        schemaService.saveSchema(schema);

        issuerKey = keyService.generateKey(TENANT, "did:web:issuers.example.hkt:professional-body-a", "EdDSA");
    }

    private CredentialIssueRequest request() {
        CredentialIssueRequest request = new CredentialIssueRequest();
        request.setTenantId(TENANT);
        request.setSchemaId("hkt-profcred-v1");
        request.setSubjectId("profile-hk-engineer-1");
        request.setKid(issuerKey.getKid());
        request.setValidityDays(365);
        request.setStatusListIndex(42);
        request.setStatusListUri("urn:didvc:status:revocation:test-list");
        request.getAlwaysDisclosedClaims().put("qualification", "Chartered Structural Engineer");
        request.getAlwaysDisclosedClaims().put("awardingBody", "HK Institution of Engineers");
        request.getAlwaysDisclosedClaims().put("scope", "Structural engineering");
        return request;
    }

    @Test
    void buildsVcDm20DocumentMatchingUniversityDegreeVectorShape() throws Exception {
        String credential = formatter.format(request());
        JsonLdVcParser.ParsedCredential parsed = new JsonLdVcParser().parse(credential);
        Map<String, Object> claims = parsed.getClaims();

        // VC DM 2.0 (https://www.w3.org/TR/vc-data-model-2.0/#example-1)
        // shape: @context, id, type array with VerifiableCredential first,
        // string issuer, validFrom/validUntil, credentialSubject with id,
        // credentialSchema and credentialStatus — every field the W3C
        // UniversityDegree example carries.
        assertEquals(List.of(JsonLdVcFormatter.VC_DM_V2_CONTEXT), claims.get("@context"));
        assertNotNull(claims.get("id"));
        List<?> type = (List<?>) claims.get("type");
        assertEquals("VerifiableCredential", type.get(0));
        assertEquals("hkt_profcred_v1", type.get(1));
        assertEquals("did:web:issuers.example.hkt:professional-body-a", claims.get("issuer"));
        assertNotNull(claims.get("validFrom"));
        assertNotNull(claims.get("validUntil"));
        Map<?, ?> subject = (Map<?, ?>) claims.get("credentialSubject");
        assertEquals("profile-hk-engineer-1", subject.get("id"));
        assertEquals("Chartered Structural Engineer", subject.get("qualification"));
        Map<?, ?> schema = (Map<?, ?>) claims.get("credentialSchema");
        assertEquals("JsonSchema", schema.get("type"));
        assertNotNull(claims.get("credentialStatus"));
    }

    @Test
    void roundTripVerifiesAgainstIssuerKey() throws Exception {
        String credential = formatter.format(request());
        JsonLdVcParser.ParsedCredential parsed = new JsonLdVcParser().parse(credential);

        JWK publicKey = JWK.parse(issuerKey.getPublicJwk());
        assertTrue(new JsonLdVcParser().verify(parsed, publicKey));
        assertEquals("hkt_profcred_v1", parsed.getCredentialType());
        assertEquals("did:web:issuers.example.hkt:professional-body-a", parsed.getIssuer());
        assertEquals(issuerKey.getKid(), parsed.getKid());
        assertEquals("profile-hk-engineer-1", parsed.getCredentialSubject().get("id"));
    }

    @Test
    void tamperedCredentialDoesNotVerify() throws Exception {
        String credential = formatter.format(request());
        JsonLdVcParser.ParsedCredential parsed = new JsonLdVcParser().parse(credential);
        JWK publicKey = JWK.parse(issuerKey.getPublicJwk());

        // A different issuer key must not verify the signature
        KeyDescriptor otherKey = keyService.generateKey(TENANT, "did:web:issuers.example.hkt:other", "EdDSA");
        assertTrue(!new JsonLdVcParser().verify(parsed, JWK.parse(otherKey.getPublicJwk())));
    }
}
