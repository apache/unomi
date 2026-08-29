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

import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.ConsentGrantRecord;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.ConsentBridgeService;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.IssuanceService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.didvc.sdjwt.SdJwtParser;
import org.apache.unomi.didvc.sdjwt.SdJwtPresentation;
import org.apache.unomi.didvc.services.MockPersistence;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end issuance through the orchestration service against a mock
 * persistence backend: schema validation, consent gating, status
 * allocation, SD-JWT formatting and revocation.
 */
class IssuanceServiceImplTest {

    private static final String TENANT = "hkt";
    private static final String VERIFIER_CATEGORY = "financial-institution";

    private PersistenceService persistenceService;
    private IssuerKeyService keyService;
    private StatusService statusService;
    private CredentialSchemaService schemaService;
    private ConsentBridgeService consentBridgeService;
    private IssuanceService issuanceService;
    private KeyDescriptor issuerKey;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
        statusService = new StatusServiceImpl();
        ((StatusServiceImpl) statusService).setPersistenceService(persistenceService);
        ((StatusServiceImpl) statusService).setIssuerKeyService(keyService);
        schemaService = new CredentialSchemaServiceImpl();
        ((CredentialSchemaServiceImpl) schemaService).setPersistenceService(persistenceService);
        consentBridgeService = new ConsentBridgeServiceImpl();
        ((ConsentBridgeServiceImpl) consentBridgeService).setPersistenceService(persistenceService);
        SdJwtVcFormatter formatter = new SdJwtVcFormatter();
        formatter.setIssuerKeyService(keyService);
        formatter.setSchemaService(schemaService);
        issuanceService = new IssuanceServiceImpl();
        ((IssuanceServiceImpl) issuanceService).setPersistenceService(persistenceService);
        ((IssuanceServiceImpl) issuanceService).setSchemaService(schemaService);
        ((IssuanceServiceImpl) issuanceService).setConsentBridgeService(consentBridgeService);
        ((IssuanceServiceImpl) issuanceService).setStatusService(statusService);
        ((IssuanceServiceImpl) issuanceService).setIssuerKeyService(keyService);
        ((IssuanceServiceImpl) issuanceService).setDefaultFormatter(formatter);

        DidSchema schema = new DidSchema(TENANT + "-kyc-v1");
        schema.setVct("hkt_kyc_v1");
        schema.setTenantId(TENANT);
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear", "givenName")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("kycLevel")));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("kycLevel", "string");
        claimTypes.put("sanctionsClear", "boolean");
        claimTypes.put("givenName", "string");
        schema.setClaimTypes(claimTypes);
        schemaService.saveSchema(schema);

        ConsentGrantRecord grant = new ConsentGrantRecord("grant-1");
        grant.setSubjectId("profile-1");
        grant.setSchemaId(schema.getItemId());
        grant.setVerifierCategory(VERIFIER_CATEGORY);
        grant.setClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear", "givenName")));
        consentBridgeService.saveGrant(grant);

        issuerKey = keyService.generateKey(TENANT, "did:web:id.example.hkt", "EdDSA");
    }

    private CredentialIssueRequest kycRequest() {
        CredentialIssueRequest request = new CredentialIssueRequest();
        request.setTenantId(TENANT);
        request.setSchemaId(TENANT + "-kyc-v1");
        request.setSubjectId("profile-1");
        request.setSubjectType("profile");
        request.setKid(issuerKey.getKid());
        request.setVerifierCategory(VERIFIER_CATEGORY);
        request.getAlwaysDisclosedClaims().put("kycLevel", "REMOTE_FULL");
        request.getSelectivelyDisclosedClaims().put("givenName", "Yat");
        request.getSelectivelyDisclosedClaims().put("sanctionsClear", true);
        return request;
    }

    @Test
    void issueFormatsVerifiableSdJwt() throws Exception {
        var record = issuanceService.issueCredential(kycRequest());

        assertEquals(TENANT + "-kyc-v1", record.getSchemaId());
        assertEquals("dc+sd-jwt", record.getFormat());
        assertNotNull(record.getCredential());
        assertNotNull(record.getStatusListId());
        assertTrue(record.getStatusListIndex() >= 0);
        assertFalse(record.isRevoked());

        // The credential must verify against the issuer key and carry the vct
        SdJwtPresentation presentation = new SdJwtParser().parse(record.getCredential());
        assertTrue(presentation.verifySignature(
                com.nimbusds.jose.jwk.OctetKeyPair.parse(issuerKey.getPublicJwk())));
        assertEquals("hkt_kyc_v1", presentation.getClaims().get("vct"));
        assertEquals("REMOTE_FULL", presentation.getClaims().get("kycLevel"));
        assertEquals(2, presentation.getDisclosedClaims().size());
        assertEquals("Yat", presentation.getDisclosedClaims().get("givenName"));
    }

    @Test
    void nonWhitelistedClaimRejected() {
        CredentialIssueRequest request = kycRequest();
        request.getAlwaysDisclosedClaims().put("idDocumentNumber", "R123456(7)");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> issuanceService.issueCredential(request));
        assertTrue(e.getMessage().contains("idDocumentNumber"));
    }

    @Test
    void missingRequiredClaimRejected() {
        CredentialIssueRequest request = kycRequest();
        request.getAlwaysDisclosedClaims().clear();
        assertThrows(IllegalArgumentException.class, () -> issuanceService.issueCredential(request));
    }

    @Test
    void claimWithoutConsentRejected() {
        CredentialIssueRequest request = kycRequest();
        request.setVerifierCategory("other-category");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> issuanceService.issueCredential(request));
        assertTrue(e.getMessage().contains("consent"));
    }

    @Test
    void unknownSchemaRejected() {
        CredentialIssueRequest request = kycRequest();
        request.setSchemaId("unknown-schema");
        assertThrows(IllegalArgumentException.class, () -> issuanceService.issueCredential(request));
    }

    @Test
    void revokeTakesEffect() {
        var record = issuanceService.issueCredential(kycRequest());
        assertFalse(issuanceService.isCredentialRevoked(record.getItemId()));
        issuanceService.revokeCredential(record.getItemId());
        assertTrue(issuanceService.isCredentialRevoked(record.getItemId()));
        assertTrue(issuanceService.getCredential(record.getItemId()).isRevoked());
    }
}
