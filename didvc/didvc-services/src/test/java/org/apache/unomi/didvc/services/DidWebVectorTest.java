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

package org.apache.unomi.didvc.services;

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.services.impl.DidServiceImpl;
import org.apache.unomi.didvc.services.impl.IssuerKeyServiceImpl;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * did:web method spec examples (https://w3c-ccg.github.io/did-method-web/):
 * identifier construction, DID-to-URL resolution mapping (including
 * percent-encoded ports), and the DID Core document shape the spec's
 * Example 1 shows (did:v1 context, JsonWebKey2020 verification methods,
 * assertionMethod references).
 */
class DidWebVectorTest {

    private PersistenceService persistenceService;
    private DidService didService;

    @BeforeEach
    void setUp() {
        persistenceService = MockPersistence.create();
        IssuerKeyService keyService = new IssuerKeyServiceImpl();
        ((IssuerKeyServiceImpl) keyService).setPersistenceService(persistenceService);
        didService = new DidServiceImpl();
        ((DidServiceImpl) didService).setPersistenceService(persistenceService);
        ((DidServiceImpl) didService).setIssuerKeyService(keyService);
    }

    /**
     * Spec resolution mapping examples:
     * did:web:example.com -> https://example.com/.well-known/did.json;
     * colons become slashes for paths; a port is percent-encoded in the
     * DID and decoded for the URL.
     */
    @Test
    void specResolutionUrlMappingExamples() {
        assertEquals("https://example.com/.well-known/did.json",
                resolutionUrl("did:web:example.com"));
        assertEquals("https://w3c-ccg.github.io/.well-known/did.json",
                resolutionUrl("did:web:w3c-ccg.github.io"));
        assertEquals("https://w3c-ccg.github.io/user/alice/did.json",
                resolutionUrl("did:web:w3c-ccg.github.io:user:alice"));
        assertEquals("https://example.com:3000/user/alice/did.json",
                resolutionUrl("did:web:example.com%3A3000:user:alice"));
        assertEquals("https://localhost:8080/didvc/did.json",
                resolutionUrl("did:web:localhost%3A8080:didvc"));
    }

    /**
     * Identifiers built by the service follow the spec: the domain is used
     * verbatim (ports percent-encoded), path segments are joined with
     * colons in place of slashes.
     */
    @Test
    void createsSpecShapedIdentifiers() {
        assertEquals("did:web:example.com",
                didService.createDid("hkt", "example.com", null, "EdDSA").getId());
        assertEquals("did:web:example.com%3A3000:user:alice",
                didService.createDid("hkt", "example.com%3A3000", "user/alice", "EdDSA").getId());
        // ...and a path-based identifier maps back to the spec resolution URL
        DidDocumentData alice = didService.createDid("hkt", "w3c-ccg.github.io", "user/alice", "EdDSA");
        assertEquals("did:web:w3c-ccg.github.io:user:alice", alice.getId());
        assertEquals("https://w3c-ccg.github.io/user/alice/did.json", resolutionUrl(alice.getId()));
    }

    /**
     * The created document has the DID Core shape the spec's Example 1
     * shows: the did:v1 context, JsonWebKey2020 verification methods
     * controlled by the DID itself, and assertionMethod entries that
     * reference the verification method ids.
     */
    @Test
    void createdDocumentMatchesSpecExampleShape() {
        DidDocumentData doc = didService.createDid("hkt", "example.com", null, "EdDSA");
        assertEquals(List.of("https://www.w3.org/ns/did/v1"), doc.getContext());
        assertEquals("did:web:example.com", doc.getId());

        assertEquals(1, doc.getVerificationMethod().size());
        DidDocumentData.VerificationMethod method = doc.getVerificationMethod().get(0);
        assertEquals("JsonWebKey2020", method.getType());
        assertEquals("did:web:example.com", method.getController());
        assertTrue(method.getId().startsWith("did:web:example.com#"),
                "verification method id is a fragment of the DID");
        // JsonWebKey2020 embeds a JWK like the spec's first key (OKP/Ed25519)
        assertEquals("OKP", method.getPublicKeyJwk().get("kty"));
        assertEquals("Ed25519", method.getPublicKeyJwk().get("crv"));
        assertNotNull(method.getPublicKeyJwk().get("x"));

        // assertionMethod references the verification method id, as in the spec
        assertNotNull(doc.getAssertionMethod());
        assertEquals(1, doc.getAssertionMethod().size());
        assertEquals(method.getId(), doc.getAssertionMethod().get(0));

        // The same shape holds for an EC (P-256) key and a path-based DID
        DidDocumentData pathDoc = didService.createDid("hkt", "w3c-ccg.github.io", "user/alice", "ES256");
        assertEquals("did:web:w3c-ccg.github.io:user:alice", pathDoc.getId());
        DidDocumentData.VerificationMethod ecMethod = pathDoc.getVerificationMethod().get(0);
        assertEquals("JsonWebKey2020", ecMethod.getType());
        assertEquals("EC", ecMethod.getPublicKeyJwk().get("kty"));
        assertEquals("P-256", ecMethod.getPublicKeyJwk().get("crv"));
        assertEquals(ecMethod.getId(), pathDoc.getAssertionMethod().get(0));
    }

    /** Documents resolve back identically, keyed by the full did:web id. */
    @Test
    void documentsResolveById() {
        didService.createDid("hkt", "example.com", null, "EdDSA");
        didService.createDid("hkt", "w3c-ccg.github.io", "user/alice", "EdDSA");
        assertEquals("did:web:example.com", didService.resolveDid("did:web:example.com").getId());
        assertEquals("did:web:w3c-ccg.github.io:user:alice",
                didService.resolveDid("did:web:w3c-ccg.github.io:user:alice").getId());
    }

    /**
     * The did:web resolution algorithm from the spec: strip the prefix,
     * percent-decode the domain (port colon), replace remaining colons
     * with slashes, append /.well-known when no path is given, and always
     * end with /did.json.
     */
    private static String resolutionUrl(String did) {
        assertTrue(did.startsWith("did:web:"), "not a did:web id: " + did);
        String specificId = did.substring("did:web:".length());
        int separator = specificId.indexOf(':');
        String encodedDomain = separator < 0 ? specificId : specificId.substring(0, separator);
        String path = separator < 0 ? "" : specificId.substring(separator + 1).replace(':', '/');
        String domain = URLDecoder.decode(encodedDomain, StandardCharsets.UTF_8);
        return path.isEmpty()
                ? "https://" + domain + "/.well-known/did.json"
                : "https://" + domain + "/" + path + "/did.json";
    }
}
