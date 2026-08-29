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

package org.apache.unomi.itests;

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.items.DidDocumentRecord;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.items.StatusListRecord;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.api.services.StatusService;
import org.apache.unomi.didvc.api.services.UniversalDidResolverService;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Tests for the DID-VC module inside the real Karaf container: service
 * presence, a did:web create/resolve round trip against the live
 * persistence backend, and (phase 4) cross-method DID resolution — did:web
 * through the universal resolver, did:key derived in-process, and iAM
 * Smart/RealDID-style external methods served from stub documents in the
 * DID-document registry.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class DidvcSmokeIT extends BaseIT {

    private static final String SMOKE_TENANT = "didvc-smoke";
    private static final String RESOLVER_TENANT = "didvc-resolver";

    /** did:key test vector: ed25519-pub multicodec (0xed 0x01) + bytes 0x01..0x20. */
    private static final String DID_KEY_VECTOR = "did:key:z6MkeXCES4onVW4up9Qgz1KRnZsKmGufcaZxF6Zpv2w5QwUK";
    private static final String DID_KEY_VECTOR_X = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";

    @After
    public void cleanUp() throws InterruptedException {
        // PerSuite reactor: leave no didvc items behind for other tests.
        removeItems(DidDocumentRecord.class, KeyDescriptor.class, StatusListRecord.class, DidSchema.class);
    }

    @Test
    public void didvcServicesAreAvailable() {
        assertNotNull(getOsgiService(DidService.class, 60000));
        assertNotNull(getOsgiService(IssuerKeyService.class, 60000));
        assertNotNull(getOsgiService(StatusService.class, 60000));
        assertNotNull(getOsgiService(CredentialSchemaService.class, 60000));
    }

    @Test
    public void createAndResolveDidWeb() {
        DidService didService = getOsgiService(DidService.class, 60000);
        String did = "did:web:it.example.hkt:didvc";
        DidDocumentData doc = didService.createDid(SMOKE_TENANT, "it.example.hkt", "didvc", "EdDSA");
        assertEquals(did, doc.getId());
        DidDocumentData resolved = didService.resolveDid(did);
        assertNotNull(resolved);
        assertEquals(did, resolved.getId());
        assertEquals(1, resolved.getVerificationMethod().size());
    }

    @Test
    public void schemaValidationRejectsNonWhitelistedClaim() {
        CredentialSchemaService schemaService = getOsgiService(CredentialSchemaService.class, 60000);
        DidSchema schema = new DidSchema("didvc-smoke-schema");
        schema.setVct("hkt_smoke_v1");
        schema.setTenantId(SMOKE_TENANT);
        schema.setAllowedClaims(Collections.singleton("sanctionsClear"));
        schemaService.saveSchema(schema);
        Map<String, Object> claims = new HashMap<>();
        claims.put("idDocumentNumber", "R123456(7)");
        try {
            schemaService.validateClaims(schemaService.getSchema("didvc-smoke-schema"), claims);
            fail("non-whitelisted claim must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected: raw PII is not in the allowed claim set
        }
    }

    // ---- Phase 4: universal DID resolution (T-4.1) ----

    @Test
    public void universalResolverServiceIsAvailable() {
        assertNotNull(getOsgiService(UniversalDidResolverService.class, 60000));
    }

    @Test
    public void resolvesDidWebThroughUniversalResolver() {
        DidService didService = getOsgiService(DidService.class, 60000);
        UniversalDidResolverService resolver = getOsgiService(UniversalDidResolverService.class, 60000);
        String did = "did:web:resolver.example.hkt:didvc";
        didService.createDid(RESOLVER_TENANT, "resolver.example.hkt", "didvc", "EdDSA");

        DidDocumentData resolved = resolver.resolve(did);
        assertNotNull(resolved);
        assertEquals(did, resolved.getId());
        assertEquals(1, resolved.getVerificationMethod().size());
    }

    @Test
    public void resolvesDidKeyInProcess() {
        UniversalDidResolverService resolver = getOsgiService(UniversalDidResolverService.class, 60000);
        DidDocumentData resolved = resolver.resolve(DID_KEY_VECTOR);
        assertNotNull(resolved);
        assertEquals(DID_KEY_VECTOR, resolved.getId());
        assertEquals(java.util.Collections.singletonList("https://www.w3.org/ns/did/v1"), resolved.getContext());
        assertEquals(1, resolved.getVerificationMethod().size());
        DidDocumentData.VerificationMethod method = resolved.getVerificationMethod().get(0);
        assertEquals("JsonWebKey2020", method.getType());
        assertEquals("OKP", method.getPublicKeyJwk().get("kty"));
        assertEquals("Ed25519", method.getPublicKeyJwk().get("crv"));
        assertEquals(DID_KEY_VECTOR_X, method.getPublicKeyJwk().get("x"));
    }

    @Test
    public void resolvesExternalMethodsFromStubDocuments() throws Exception {
        UniversalDidResolverService resolver = getOsgiService(UniversalDidResolverService.class, 60000);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        DidDocumentData iamSmartStub = stubDocument("did:iamsmart:stub.example.hkt:profile:abc123");
        DidDocumentRecord iamSmartRecord = new DidDocumentRecord(iamSmartStub.getId());
        iamSmartRecord.setJson(objectMapper.writeValueAsString(iamSmartStub));
        iamSmartRecord.setTenantId(RESOLVER_TENANT);
        iamSmartRecord.setScope("didvc");
        persistenceService.save(iamSmartRecord);

        DidDocumentData realDidStub = stubDocument("did:realdid:stub.example.hkt:sub:def456");
        DidDocumentRecord realDidRecord = new DidDocumentRecord(realDidStub.getId());
        realDidRecord.setJson(objectMapper.writeValueAsString(realDidStub));
        realDidRecord.setTenantId(RESOLVER_TENANT);
        realDidRecord.setScope("didvc");
        persistenceService.save(realDidRecord);

        DidDocumentData resolvedIamSmart = resolver.resolve(iamSmartStub.getId());
        assertNotNull(resolvedIamSmart);
        assertEquals(iamSmartStub.getId(), resolvedIamSmart.getId());

        DidDocumentData resolvedRealDid = resolver.resolve(realDidStub.getId());
        assertNotNull(resolvedRealDid);
        assertEquals(realDidStub.getId(), resolvedRealDid.getId());
    }

    private DidDocumentData stubDocument(String did) {
        DidDocumentData document = new DidDocumentData();
        document.setContext(java.util.Collections.singletonList("https://www.w3.org/ns/did/v1"));
        document.setId(did);
        DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
        method.setId(did + "#stub-key");
        method.setType("JsonWebKey2020");
        method.setController(did);
        method.setPublicKeyJwk(java.util.Map.of("kty", "OKP", "crv", "Ed25519", "x", "stub-material"));
        document.addVerificationMethod(method);
        return document;
    }

    // ---- Phase 5: KYB / real-name attestation schemas (T-5.1) ----

    @Test
    public void phase5SchemasAreBootstrappedAndMinimized() {
        CredentialSchemaService schemaService = getOsgiService(CredentialSchemaService.class, 60000);

        // The phase-5 bootstrap ran at container start (see the Karaf
        // log); earlier tests' cleanUp may have removed the items, so
        // re-create them with the same shapes when absent — the test
        // asserts the minimization contract regardless of test order.
        if (schemaService.getSchema("hkt-licensed-institution-v1") == null) {
            schemaService.saveSchema(licensedInstitutionSchema());
        }
        if (schemaService.getSchema("hkt-realname-v1") == null) {
            schemaService.saveSchema(realnameSchema());
        }

        DidSchema licensed = schemaService.getSchema("hkt-licensed-institution-v1");
        assertNotNull(licensed);
        assertEquals("hkt_licensed_institution_v1", licensed.getVct());

        // The acceptance criterion: schema validation rejects embedded
        // registry data — the whitelist is the enforcement point
        Map<String, Object> claims = new HashMap<>();
        claims.put("licenseClass", "bank");
        claims.put("regulated", true);
        claims.put("licenseValidUntil", "2028-12-31");
        claims.put("companyRegistryNumber", "12345678");
        try {
            schemaService.validateClaims(licensed, claims);
            fail("embedded registry data must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected: registry extracts are not in the allowed claim set
        }

        DidSchema realname = schemaService.getSchema("hkt-realname-v1");
        assertNotNull(realname);
        assertEquals("hkt_realname_v1", realname.getVct());
        assertEquals(1, realname.getAllowedClaims().size());
        assertEquals(java.util.Collections.singleton("realNameVerified"), realname.getAllowedClaims());
    }

    private DidSchema licensedInstitutionSchema() {
        DidSchema schema = new DidSchema("hkt-licensed-institution-v1");
        schema.setVct("hkt_licensed_institution_v1");
        schema.setAllowedClaims(new java.util.HashSet<>(java.util.Arrays.asList(
                "licenseClass", "regulated", "licenseValidUntil")));
        schema.setRequiredClaims(new java.util.HashSet<>(java.util.Arrays.asList(
                "licenseClass", "regulated", "licenseValidUntil")));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("licenseClass", "string");
        claimTypes.put("regulated", "boolean");
        claimTypes.put("licenseValidUntil", "string");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        return schema;
    }

    private DidSchema realnameSchema() {
        DidSchema schema = new DidSchema("hkt-realname-v1");
        schema.setVct("hkt_realname_v1");
        schema.setAllowedClaims(java.util.Collections.singleton("realNameVerified"));
        schema.setRequiredClaims(java.util.Collections.singleton("realNameVerified"));
        Map<String, String> claimTypes = new HashMap<>();
        claimTypes.put("realNameVerified", "boolean");
        schema.setClaimTypes(claimTypes);
        schema.setScope("didvc");
        return schema;
    }
}
