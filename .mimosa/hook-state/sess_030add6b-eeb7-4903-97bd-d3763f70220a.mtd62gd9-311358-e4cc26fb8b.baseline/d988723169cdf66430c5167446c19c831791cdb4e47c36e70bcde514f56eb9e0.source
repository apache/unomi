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
 * Smoke tests for the DID-VC module inside the real Karaf container: service
 * presence and a did:web create/resolve round trip against the live
 * persistence backend.
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class DidvcSmokeIT extends BaseIT {

    private static final String SMOKE_TENANT = "didvc-smoke";

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
}
