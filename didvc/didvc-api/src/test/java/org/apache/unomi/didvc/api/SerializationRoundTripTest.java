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

package org.apache.unomi.didvc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.items.CredentialRecord;
import org.apache.unomi.didvc.api.items.DidDocumentRecord;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.items.PairwiseBindingRecord;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.items.TrustEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jackson serialization round-trip for the DID-VC domain model. The persisted
 * items must survive serialization/deserialization unchanged so the
 * PersistenceService backends can store them.
 */
class SerializationRoundTripTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void schemaRoundTrip() throws Exception {
        DidSchema schema = new DidSchema("hkt-kyc-v1");
        schema.setName("HKT Reusable KYC");
        schema.setVct("hkt_kyc_v1");
        schema.setDescription("Reusable KYC credential");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("kycLevel", "sanctionsClear")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("kycLevel")));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("kycLevel", "string");
        claimTypes.put("sanctionsClear", "boolean");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        schema.setTenantId("hkt");

        DidSchema result = objectMapper.readValue(objectMapper.writeValueAsString(schema), DidSchema.class);

        assertEquals(schema.getItemId(), result.getItemId());
        assertEquals(schema.getItemType(), result.getItemType());
        assertEquals("hkt_kyc_v1", result.getVct());
        assertEquals(schema.getAllowedClaims(), result.getAllowedClaims());
        assertEquals(schema.getRequiredClaims(), result.getRequiredClaims());
        assertEquals(schema.getClaimTypes(), result.getClaimTypes());
        assertEquals("hkt", result.getTenantId());
    }

    @Test
    void credentialRecordRoundTrip() throws Exception {
        CredentialRecord record = new CredentialRecord("cred-1");
        record.setSchemaId("hkt-kyc-v1");
        record.setSubjectId("profile-1");
        record.setSubjectType("profile");
        record.setKid("abc123");
        record.setStatusListId("list-1");
        record.setStatusListIndex(7);
        record.setFormat("vc+sd-jwt");
        record.setCredential("eyJhbGciOiJFZERTQSJ9.payload.signature");
        record.setIssuedAt(new Date(1000L));
        record.setExpiresAt(new Date(2000L));
        record.setRevoked(false);

        CredentialRecord result = objectMapper.readValue(objectMapper.writeValueAsString(record), CredentialRecord.class);

        assertEquals(record.getItemId(), result.getItemId());
        assertEquals("cred-1", result.getItemId());
        assertEquals("profile-1", result.getSubjectId());
        assertEquals(7, result.getStatusListIndex());
        assertEquals("vc+sd-jwt", result.getFormat());
        assertEquals(new Date(1000L), result.getIssuedAt());
        assertEquals(new Date(2000L), result.getExpiresAt());
        assertFalse(result.isRevoked());
    }

    @Test
    void statusListRecordRoundTrip() throws Exception {
        StatusListRecord record = new StatusListRecord("list-1");
        record.setStatusPurpose("revocation");
        record.setEncodedList("H4sIAAAAAAAA_-3BMQEAAADCoPVPbQwfoAA");
        record.setSize(16);
        record.setNextIndex(2);
        record.setIssuerDid("did:web:example.hkt");
        record.setStatusListId("https://example.hkt/didvc/statuslists/list-1");

        StatusListRecord result = objectMapper.readValue(objectMapper.writeValueAsString(record), StatusListRecord.class);

        assertEquals("revocation", result.getStatusPurpose());
        assertEquals(16, result.getSize());
        assertEquals(2, result.getNextIndex());
        assertEquals("did:web:example.hkt", result.getIssuerDid());
        assertEquals(record.getEncodedList(), result.getEncodedList());
    }

    @Test
    void trustEntryRoundTrip() throws Exception {
        TrustEntry entry = new TrustEntry("trust-1");
        entry.setIssuerDid("did:web:issuer.example.hkt");
        entry.setVct("hkt_kyc_v1");
        entry.setAccreditationLevel("accredited");
        entry.setValidFrom(new Date(1000L));
        entry.setValidUntil(new Date(2000L));
        entry.setStatus("active");

        TrustEntry result = objectMapper.readValue(objectMapper.writeValueAsString(entry), TrustEntry.class);

        assertEquals("did:web:issuer.example.hkt", result.getIssuerDid());
        assertEquals("hkt_kyc_v1", result.getVct());
        assertEquals("accredited", result.getAccreditationLevel());
        assertEquals("active", result.getStatus());
    }

    @Test
    void didDocumentRecordRoundTrip() throws Exception {
        DidDocumentRecord record = new DidDocumentRecord("did:web:example.hkt");
        record.setDomain("example.hkt");
        record.setJson("{\"id\":\"did:web:example.hkt\"}");
        record.setDeactivated(true);

        DidDocumentRecord result = objectMapper.readValue(objectMapper.writeValueAsString(record), DidDocumentRecord.class);

        assertEquals("did:web:example.hkt", result.getItemId());
        assertEquals("example.hkt", result.getDomain());
        assertTrue(result.isDeactivated());
    }

    @Test
    void keyDescriptorRoundTripAndNoPrivateMaterial() throws Exception {
        KeyDescriptor key = new KeyDescriptor("thumbprint-1");
        key.setAlg("EdDSA");
        key.setKeyType("OKP");
        key.setIssuerDid("did:web:example.hkt");
        key.setPublicJwk("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"abc\"}");

        KeyDescriptor result = objectMapper.readValue(objectMapper.writeValueAsString(key), KeyDescriptor.class);

        assertEquals("thumbprint-1", result.getKid());
        assertEquals("EdDSA", result.getAlg());
        assertEquals("OKP", result.getKeyType());
        JsonNode jwk = objectMapper.readTree(result.getPublicJwk());
        assertFalse(jwk.has("d"), "public JWK must never contain private key material");
    }

    @Test
    void pairwiseBindingRoundTrip() throws Exception {
        PairwiseBindingRecord binding = new PairwiseBindingRecord("binding-1");
        binding.setProfileId("profile-1");
        binding.setVerifierTenantId("bank-a");
        binding.setOpaqueReference("opaque-ref-for-bank-a");
        binding.setCreatedAt(new Date(1000L));

        PairwiseBindingRecord result = objectMapper.readValue(objectMapper.writeValueAsString(binding), PairwiseBindingRecord.class);

        assertEquals("profile-1", result.getProfileId());
        assertEquals("bank-a", result.getVerifierTenantId());
        assertEquals("opaque-ref-for-bank-a", result.getOpaqueReference());
    }

    @Test
    void didDocumentDataSerializesContextKey() throws Exception {
        DidDocumentData doc = new DidDocumentData();
        doc.setContext(Arrays.asList("https://www.w3.org/ns/did/v1"));
        doc.setId("did:web:example.hkt");
        DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
        method.setId("did:web:example.hkt#key-1");
        method.setType("JsonWebKey2020");
        method.setController("did:web:example.hkt");
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "OKP");
        jwk.put("crv", "Ed25519");
        jwk.put("x", "abc");
        method.setPublicKeyJwk(jwk);
        doc.addVerificationMethod(method);

        String json = objectMapper.writeValueAsString(doc);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("@context"), "DID document JSON must use the @context key");
        assertEquals("did:web:example.hkt", node.get("id").asText());
        assertEquals(1, node.get("verificationMethod").size());
        assertEquals("did:web:example.hkt#key-1", node.get("assertionMethod").get(0).asText());

        DidDocumentData roundTripped = objectMapper.readValue(json, DidDocumentData.class);
        assertEquals(doc.getId(), roundTripped.getId());
        assertEquals(1, roundTripped.getVerificationMethod().size());
        assertEquals("OKP", roundTripped.getVerificationMethod().get(0).getPublicKeyJwk().get("kty"));
    }
}
