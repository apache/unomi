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

package org.apache.unomi.didvc.sdjwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SD-JWT issuance and presentation round trip: selective disclosure,
 * signature verification, key binding and tamper rejection.
 */
class SdJwtRoundTripTest {

    private OctetKeyPair issuerKey;
    private OctetKeyPair holderKey;

    @BeforeEach
    void setUp() throws Exception {
        issuerKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        holderKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
    }

    private String issueCredential() throws Exception {
        SdJwtBuilder.CredentialPayload payload = new SdJwtBuilder.CredentialPayload();
        payload.setVct("hkt_kyc_v1");
        payload.setIss("did:web:id.example.hkt");
        payload.setSub("didvc:pairwise:abc123");
        payload.setIssuedAt(new Date(1000L * 1_700_000_000L));
        payload.setExpiresAt(new Date(1000L * 1_700_000_000L + 365L * 24 * 3600 * 1000));
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        Map<String, Object> statusList = new java.util.LinkedHashMap<>();
        statusList.put("idx", 7);
        statusList.put("uri", "https://id.example.hkt/didvc/statuslists/list-1");
        status.put("status_list", statusList);
        payload.setStatus(status);
        payload.setCnf(SdJwtBuilder.cnfForJwk(holderKey.toPublicJWK()));
        payload.getAlwaysDisclosed().put("kycLevel", "REMOTE_FULL");
        payload.getSelectivelyDisclosed().put("givenName", "Yat");
        payload.getSelectivelyDisclosed().put("nationality", "HK");

        SdJwtBuilder builder = new SdJwtBuilder();
        return builder.build(payload, new com.nimbusds.jose.crypto.Ed25519Signer(issuerKey),
                JWSAlgorithm.EdDSA, issuerKey.computeThumbprint().toString());
    }

    @Test
    void roundTripWithAllDisclosures() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        assertEquals(3, parts.length, "JWS plus two disclosures");
        assertTrue(sdJwt.endsWith("~"), "issuance serialization must end with the RFC 9901 trailing tilde");

        SdJwtPresentation presentation = new SdJwtParser().parse(sdJwt);
        assertTrue(presentation.verifySignature(issuerKey.toPublicJWK()));
        assertEquals("hkt_kyc_v1", presentation.getClaims().get("vct"));
        assertEquals("REMOTE_FULL", presentation.getClaims().get("kycLevel"));
        assertEquals("Yat", presentation.getDisclosedClaims().get("givenName"));
        assertEquals("HK", presentation.getDisclosedClaims().get("nationality"));
        // The disclosed view is the full processed payload with _sd removed
        assertNotNull(presentation.getClaims().get("_sd"));
        assertFalse(presentation.getDisclosedClaims().containsKey("_sd"));
        assertFalse(presentation.getDisclosedClaims().containsKey("_sd_alg"));
        assertNull(presentation.getKeyBindingJwt());
    }

    @Test
    void selectiveDisclosureRevealsOnlyChosenClaim() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        // Present only the givenName disclosure
        String partial = parts[0] + "~" + parts[1];
        SdJwtPresentation presentation = new SdJwtParser().parse(partial);
        assertTrue(presentation.getDisclosedClaims().containsKey("givenName"));
        assertFalse(presentation.getDisclosedClaims().containsKey("nationality"));
    }

    @Test
    void forgedDisclosureRejected() throws Exception {
        String sdJwt = issueCredential();
        String forged = SdJwtDigest.buildDisclosure("givenName", "Evil");
        assertThrows(IllegalArgumentException.class,
                () -> new SdJwtParser().parse(sdJwt + "~" + forged));
    }

    @Test
    void tamperedPayloadRejected() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        String tamperedJws = parts[0].substring(0, parts[0].length() - 4) + "AAAA";
        SdJwtPresentation presentation = new SdJwtParser().parse(tamperedJws + "~" + parts[1] + "~" + parts[2]);
        assertFalse(presentation.verifySignature(issuerKey.toPublicJWK()));
    }

    @Test
    void wrongIssuerKeyRejected() throws Exception {
        OctetKeyPair otherKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        SdJwtPresentation presentation = new SdJwtParser().parse(issueCredential());
        assertFalse(presentation.verifySignature(otherKey.toPublicJWK()));
    }

    @Test
    void keyBindingRoundTrip() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        String preKeyBinding = parts[0] + "~" + parts[1] + "~" + parts[2] + "~";
        String kbJwt = new KeyBindingJwtBuilder().build(holderKey, "nonce-123", "https://verify.hkt/didvc",
                preKeyBinding, new Date());
        String presentation = parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + kbJwt;

        SdJwtPresentation parsed = new SdJwtParser().parse(presentation);
        assertNotNull(parsed.getKeyBindingJwt());
        parsed.verifyKeyBinding("nonce-123", "https://verify.hkt/didvc",
                System.currentTimeMillis() / 1000);
    }

    @Test
    void keyBindingWrongNonceRejected() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        String kbJwt = new KeyBindingJwtBuilder().build(holderKey, "nonce-123", "https://verify.hkt/didvc",
                parts[0] + "~" + parts[1] + "~" + parts[2] + "~", new Date());
        SdJwtPresentation parsed = new SdJwtParser().parse(
                parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + kbJwt);
        assertThrows(SecurityException.class,
                () -> parsed.verifyKeyBinding("nonce-OTHER", "https://verify.hkt/didvc",
                        System.currentTimeMillis() / 1000));
    }

    @Test
    void keyBindingSignedByOtherHolderRejected() throws Exception {
        String sdJwt = issueCredential();
        String[] parts = sdJwt.split("~");
        OctetKeyPair attackerKey = new OctetKeyPairGenerator(Curve.Ed25519).generate();
        String kbJwt = new KeyBindingJwtBuilder().build(attackerKey, "nonce-123", "https://verify.hkt/didvc",
                parts[0] + "~" + parts[1] + "~" + parts[2] + "~", new Date());
        SdJwtPresentation parsed = new SdJwtParser().parse(
                parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + kbJwt);
        assertThrows(SecurityException.class,
                () -> parsed.verifyKeyBinding("nonce-123", "https://verify.hkt/didvc",
                        System.currentTimeMillis() / 1000));
    }

    @Test
    void presentationWithoutKeyBindingRejected() throws Exception {
        SdJwtPresentation parsed = new SdJwtParser().parse(issueCredential());
        assertThrows(SecurityException.class,
                () -> parsed.verifyKeyBinding("nonce-123", "https://verify.hkt/didvc",
                        System.currentTimeMillis() / 1000));
    }
}
