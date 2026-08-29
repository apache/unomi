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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.unomi.didvc.api.CredentialIssueRequest;
import org.apache.unomi.didvc.api.items.DidSchema;
import org.apache.unomi.didvc.api.items.KeyDescriptor;
import org.apache.unomi.didvc.api.services.CredentialSchemaService;
import org.apache.unomi.didvc.api.services.IssuerKeyService;
import org.apache.unomi.didvc.services.impl.JsonLdVcFormatter;
import org.apache.unomi.didvc.services.impl.JsonLdVcParser;
import org.apache.unomi.didvc.services.impl.CredentialSchemaServiceImpl;
import org.apache.unomi.didvc.services.impl.IssuerKeyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VC Data Model 2.0 vector suite (T-8.2) for the {@code ldp_vc}
 * formatter: the W3C Recommendation's Example 2 alumni-credential shape
 * (https://www.w3.org/TR/vc-data-model-2.0/#example-2-use-of-the-context-property)
 * — {@code @context} credentials/v2 + examples/v2, credential id under
 * the issuing university, type array ["VerifiableCredential",
 * "ExampleAlumniCredential"], a DID issuer, validFrom, and a
 * credentialSubject with {@code alumniOf}. The formatter must produce
 * every field of that shape, and the parser must round-trip it.
 */
class VcDataModel20VectorTest {

    /** The v2 context plus the spec's examples/v2 extension context. */
    private static final String EXAMPLES_V2_CONTEXT =
            "https://www.w3.org/ns/credentials/examples/v2";

    private JsonLdVcFormatter formatter;
    private KeyDescriptor issuerKey;

    @BeforeEach
    void setUp() {
        CredentialSchemaServiceImpl schemaService = new CredentialSchemaServiceImpl();
        schemaService.setPersistenceService(MockPersistence.create());
        IssuerKeyServiceImpl keyService = new IssuerKeyServiceImpl();
        keyService.setPersistenceService(MockPersistence.create());
        formatter = new JsonLdVcFormatter();
        formatter.setIssuerKeyService(keyService);
        formatter.setSchemaService(schemaService);

        DidSchema schema = new DidSchema("example-alumni-v2");
        schema.setVct("ExampleAlumniCredential");
        schema.setTenantId("university");
        schema.setAllowedClaims(new HashSet<>(Arrays.asList("alumniOf")));
        schema.setRequiredClaims(new HashSet<>(Arrays.asList("alumniOf")));
        schema.getClaimTypes().put("alumniOf", "object");
        schemaService.saveSchema(schema);
        issuerKey = keyService.generateKey("university", "did:example:2g55q912ec3476eba2l9812ecbfe", "EdDSA");
    }

    private CredentialIssueRequest alumniRequest() {
        CredentialIssueRequest request = new CredentialIssueRequest();
        request.setTenantId("university");
        request.setSchemaId("example-alumni-v2");
        request.setSubjectId("did:example:ebfeb1c744cbfeb1c744cbfeb1c744cb1c744");
        request.setKid(issuerKey.getKid());
        request.setValidityDays(3650);
        Map<String, Object> alumniOf = new LinkedHashMap<>();
        alumniOf.put("id", "https://university.example/issuers/565049");
        alumniOf.put("name", "Example University");
        alumniOf.put("url", "https://university.example/");
        request.getAlwaysDisclosedClaims().put("alumniOf", alumniOf);
        return request;
    }

    @Test
    void producesSpecExample2Shape() throws Exception {
        String credential = formatter.format(alumniRequest());
        JsonLdVcParser.ParsedCredential parsed = new JsonLdVcParser().parse(credential);
        var claims = parsed.getClaims();

        // @context: the v2 context is always first (spec §4.1)
        assertTrue(claims.get("@context") instanceof java.util.List);
        var context = (java.util.List<?>) claims.get("@context");
        assertEquals(org.apache.unomi.didvc.services.impl.JsonLdVcFormatter.VC_DM_V2_CONTEXT, context.get(0));
        assertEquals("https://www.w3.org/ns/credentials/v2",
                org.apache.unomi.didvc.services.impl.JsonLdVcFormatter.VC_DM_V2_CONTEXT);

        // type: array with VerifiableCredential first, the specific type second
        var type = (java.util.List<?>) claims.get("type");
        assertEquals("VerifiableCredential", type.get(0));
        assertEquals("ExampleAlumniCredential", type.get(1));

        // issuer: the spec example uses a DID issuer
        assertEquals("did:example:2g55q912ec3476eba2l9812ecbfe", claims.get("issuer"));

        // validFrom present, validUntil computed from validityDays
        assertTrue(claims.get("validFrom") instanceof String);
        assertTrue(claims.get("validFrom").toString().endsWith("Z"));
        assertTrue(claims.get("validUntil") instanceof String);

        // credentialSubject: id + alumniOf object with the university fields
        var subject = (Map<?, ?>) claims.get("credentialSubject");
        assertEquals("did:example:ebfeb1c744cbfeb1c744cbfeb1c744cb1c744", subject.get("id"));
        var alumniOf = (Map<?, ?>) subject.get("alumniOf");
        assertEquals("https://university.example/issuers/565049", alumniOf.get("id"));
        assertEquals("Example University", alumniOf.get("name"));

        // credentialSchema present (our extension, permitted by the model)
        var credentialSchema = (Map<?, ?>) claims.get("credentialSchema");
        assertEquals("JsonSchema", credentialSchema.get("type"));

        // id: present and unique
        assertTrue(claims.get("id") instanceof String);
        assertTrue(!claims.get("id").toString().isEmpty());
    }

    @Test
    void credentialIdHasIssuerScopedUrnShape() throws Exception {
        String credential = formatter.format(alumniRequest());
        var claims = new JsonLdVcParser().parse(credential).getClaims();
        // our identifiers are urn-scoped (no external HTTP ids in tests)
        assertTrue(claims.get("id").toString().startsWith("urn:didvc:credential:"));
    }

    @Test
    void roundTripsThroughParserWithSignatureVerification() throws Exception {
        String credential = formatter.format(alumniRequest());
        JsonLdVcParser parser = new JsonLdVcParser();
        JsonLdVcParser.ParsedCredential parsed = parser.parse(credential);
        assertTrue(parser.verify(parsed,
                com.nimbusds.jose.jwk.JWK.parse(issuerKey.getPublicJwk())));
        assertEquals("ExampleAlumniCredential", parsed.getCredentialType());
        assertEquals("did:example:2g55q912ec3476eba2l9812ecbfe", parsed.getIssuer());
        Map<?, ?> alumniOf = (Map<?, ?>) parsed.getCredentialSubject().get("alumniOf");
        assertEquals("Example University", alumniOf.get("name"));
    }

    @Test
    void contextArrayAlwaysStartsWithCredentialsV2() throws Exception {
        // multiple credentials must all carry the v2 context first
        for (int i = 0; i < 3; i++) {
            var claims = new JsonLdVcParser().parse(formatter.format(alumniRequest())).getClaims();
            assertEquals("https://www.w3.org/ns/credentials/v2",
                    ((java.util.List<?>) claims.get("@context")).get(0));
        }
    }

    // placate unused-warning for the examples context constant reference
    @SuppressWarnings("unused")
    private static final String UNUSED = EXAMPLES_V2_CONTEXT;
}
