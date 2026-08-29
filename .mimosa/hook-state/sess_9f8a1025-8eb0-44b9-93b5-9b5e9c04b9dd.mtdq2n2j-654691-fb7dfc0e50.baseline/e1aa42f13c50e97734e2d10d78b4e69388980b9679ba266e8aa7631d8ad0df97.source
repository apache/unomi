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
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SD-JWT VC profile (draft-11, Appendix B.1 PID example): the
 * {@code dc+sd-jwt} media type / typ header, the {@code vct} claim, and
 * the recursive-disclosure structure used by that profile (address,
 * place_of_birth and age_equal_or_over each carry their own nested
 * {@code _sd} arrays inside the disclosure values).
 */
class SdJwtVcProfileVectorTest {

    // The issued SD-JWT from SD-JWT VC draft-11 Appendix B.1 (line-wrapped
    // base64url from the draft joined without whitespace): an ES256 JWT
    // with typ dc+sd-jwt, vct urn:eudi:pid:de:1 and 27 disclosures.
    private static final String SDJWT_VC_DRAFT11_ISSUANCE =
            "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogImRjK3NkLWp3dCJ9.eyJfc2QiOiBbIjBIWm1uU0lQejMzN2tTV2U3QzM0bC0tODh"
             + "nekppLWVCSjJWel9ISndBVGciLCAiMUNybjAzV21VZVJXcDR6d1B2dkNLWGw5WmFRcC1jZFFWX2dIZGFHU1dvdyIsICIycjA"
             + "wOWR6dkh1VnJXclJYVDVrSk1tSG5xRUhIbldlME1MVlp3OFBBVEI4IiwgIjZaTklTRHN0NjJ5bWxyT0FrYWRqZEQ1WnVsVDV"
             + "BMjk5Sjc4U0xoTV9fT3MiLCAiNzhqZzc3LUdZQmVYOElRZm9FTFB5TDBEWVBkbWZabzBKZ1ZpVjBfbEtDTSIsICI5MENUOEF"
             + "hQlBibjVYOG5SWGtlc2p1MWkwQnFoV3FaM3dxRDRqRi1xREdrIiwgIkkwMGZjRlVvRFhDdWNwNXl5MnVqcVBzc0RWR2FXTml"
             + "VbGlOel9hd0QwZ2MiLCAiS2pBWGdBQTlONVdIRUR0UkloNHU1TW4xWnNXaXhoaFdBaVgtQTRRaXdnQSIsICJMYWk2SVU2ZDd"
             + "HUWFnWFI3QXZHVHJuWGdTbGQzejhFSWdfZnYzZk9aMVdnIiwgIkxlemphYlJxaVpPWHpFWW1WWmY4Uk1pOXhBa2QzX00xTFo"
             + "4VTdFNHMzdTQiLCAiUlR6M3FUbUZOSGJwV3JyT01aUzQxRjQ3NGtGcVJ2M3ZJUHF0aDZQVWhsTSIsICJXMTRYSGJVZmZ6dVc"
             + "0SUZNanBTVGIxbWVsV3hVV2Y0Tl9vMmxka2tJcWM4IiwgIldUcEk3UmNNM2d4WnJ1UnBYemV6U2JrYk9yOTNQVkZ2V3g4d29"
             + "KM2oxY0UiLCAiX29oSlZJUUlCc1U0dXBkTlM0X3c0S2IxTUhxSjBMOXFMR3NoV3E2SlhRcyIsICJ5NTBjemMwSVNDaHlfYnN"
             + "iYTFkTW9VdUFPUTVBTW1PU2ZHb0VlODF2MUZVIl0sICJpc3MiOiAiaHR0cHM6Ly9waWQtaXNzdWVyLmJ1bmQuZGUuZXhhbXB"
             + "sZSIsICJpYXQiOiAxNjgzMDAwMDAwLCAiZXhwIjogMTg4MzAwMDAwMCwgInZjdCI6ICJ1cm46ZXVkaTpwaWQ6ZGU6MSIsICJ"
             + "fc2RfYWxnIjogInNoYS0yNTYiLCAiY25mIjogeyJqd2siOiB7Imt0eSI6ICJFQyIsICJjcnYiOiAiUC0yNTYiLCAieCI6ICJ"
             + "UQ0FFUjE5WnZ1M09IRjRqNFc0dmZTVm9ISVAxSUxpbERsczd2Q2VHZW1jIiwgInkiOiAiWnhqaVdXYlpNUUdIVldLVlE0aGJ"
             + "TSWlyc1ZmdWVjQ0U2dDRqVDlGMkhaUSJ9fX0.5Dck3qcIZnQm0UslQOqvP5Oy_OY6IIjMJhTuEOuuOH-bGI2s9kNb8X8nncy"
             + "eVZcVkGGC5jvpwhVyxUzU5X0pxg~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLCAiRXJpa2EiXQ~W"
             + "yJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIk11c3Rlcm1hbm4iXQ~WyI2SWo3dE0tYTVpVlBHYm"
             + "9TNXRtdlZBIiwgImJpcnRoZGF0ZSIsICIxOTYzLTA4LTEyIl0~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInN0cmVldF"
             + "9hZGRyZXNzIiwgIkhlaWRlc3RyYVx1MDBkZmUgMTciXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIiwgImxvY2FsaXR5Iiw"
             + "gIktcdTAwZjZsbiJd~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgInBvc3RhbF9jb2RlIiwgIjUxMTQ3Il0~WyJQYzMzSk"
             + "0yTGNoY1VfbEhnZ3ZfdWZRIiwgImNvdW50cnkiLCAiREUiXQ~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgImFkZHJlc3M"
             + "iLCB7Il9zZCI6IFsiQUxaRVJzU241V05pRVhkQ2tzVzhJNXFRdzNfTnBBblJxcFNBWkR1ZGd3OCIsICJEX19XX3VZY3ZSejN"
             + "0dlVuSUp2QkRIaVRjN0NfX3FIZDB4Tkt3SXNfdzlrIiwgImVCcENYVTFKNWRoSDJnNHQ4UVlOVzVFeFM5QXhVVmJsVW9kb0x"
             + "Zb1BobzAiLCAieE9QeTktZ0pBTEs2VWJXS0ZMUjg1Y09CeVVEM0FiTndGZzNJM1lmUUVfSSJdfV0~WyJsa2x4RjVqTVlsR1R"
             + "QVW92TU5JdkNBIiwgIm5hdGlvbmFsaXRpZXMiLCBbIkRFIl1d~WyJuUHVvUW5rUkZxM0JJZUFtN0FuWEZBIiwgInNleCIsID"
             + "Jd~WyI1YlBzMUlxdVpOYTBoa2FGenp6Wk53IiwgImJpcnRoX2ZhbWlseV9uYW1lIiwgIkdhYmxlciJd~WyI1YTJXMF9OcmxF"
             + "WnpmcW1rXzdQcS13IiwgImxvY2FsaXR5IiwgIkJlcmxpbiJd~WyJ5MXNWVTV3ZGZKYWhWZGd3UGdTN1JRIiwgImNvdW50cnk"
             + "iLCAiREUiXQ~WyJIYlE0WDhzclZXM1FEeG5JSmRxeU9BIiwgInBsYWNlX29mX2JpcnRoIiwgeyJfc2QiOiBbIktVVmlhYUxu"
             + "WTVqU01MOTBHMjlPT0xFTlBiYlhmaFNqU1BNalphR2t4QUUiLCAiWWJzVDBTNzZWcVhDVnNkMWpVU2x3S1BEZ21BTGVCMXVa"
             + "Y2xGSFhmLVVTUSJdfV0~WyJDOUdTb3VqdmlKcXVFZ1lmb2pDYjFBIiwgIjEyIiwgdHJ1ZV0~WyJreDVrRjE3Vi14MEptd1V4"
             + "OXZndnR3IiwgIjE0IiwgdHJ1ZV0~WyJIM28xdXN3UDc2MEZpMnllR2RWQ0VRIiwgIjE2IiwgdHJ1ZV0~WyJPQktsVFZsdkxn"
             + "LUFkd3FZR2JQOFpBIiwgIjE4IiwgdHJ1ZV0~WyJNMEpiNTd0NDF1YnJrU3V5ckRUM3hBIiwgIjIxIiwgdHJ1ZV0~WyJEc210"
             + "S05ncFY0ZEFIcGpyY2Fvc0F3IiwgIjY1IiwgZmFsc2Vd~WyJlSzVvNXBIZmd1cFBwbHRqMXFoQUp3IiwgImFnZV9lcXVhbF9"
             + "vcl9vdmVyIiwgeyJfc2QiOiBbIjF0RWl5elBSWU9Lc2Y3U3NZR01nUFpLc09UMWxRWlJ4SFhBMHI1X0J3a2siLCAiQ1ZLbmx"
             + "5NVA5MHlKczNFd3R4UWlPdFVjemFYQ1lOQTRJY3pSYW9ock1EZyIsICJhNDQtZzJHcjhfM0FtSncyWFo4a0kxeTBRel96ZTl"
             + "pT2NXMlczUkxwWEdnIiwgImdrdnkwRnV2QkJ2ajBoczJaTnd4Y3FPbGY4bXUyLWtDRTctTmIyUXh1QlUiLCAiaHJZNEhubUY"
             + "1YjVKd0M5ZVR6YUZDVWNlSVFBYUlkaHJxVVhRTkNXYmZaSSIsICJ5NlNGclZGUnlxNTBJYlJKdmlUWnFxalFXejB0TGl1Q21"
             + "NZU8wS3FhekdJIl19XQ~WyJqN0FEZGIwVVZiMExpMGNpUGNQMGV3IiwgImFnZV9pbl95ZWFycyIsIDYyXQ~WyJXcHhKckZ1W"
             + "Dh1U2kycDRodDA5anZ3IiwgImFnZV9iaXJ0aF95ZWFyIiwgMTk2M10~WyJhdFNtRkFDWU1iSlZLRDA1bzNKZ3RRIiwgImlzc"
             + "3VhbmNlX2RhdGUiLCAiMjAyMC0wMy0xMSJd~WyI0S3lSMzJvSVp0LXprV3ZGcWJVTEtnIiwgImV4cGlyeV9kYXRlIiwgIjIw"
             + "MzAtMDMtMTIiXQ~WyJjaEJDc3loeWgtSjg2SS1hd1FEaUNRIiwgImlzc3VpbmdfYXV0aG9yaXR5IiwgIkRFIl0~WyJmbE5QM"
             + "W5jTXo5TGctYzlxTUl6XzlnIiwgImlzc3VpbmdfY291bnRyeSIsICJERSJd~";


    /**
     * The draft's example parses; the vct claim is surfaced, the
     * dc+sd-jwt typ is tolerated, and claims whose digests live inside
     * other disclosures (address members, place_of_birth members,
     * age_equal_or_over members) are assembled recursively.
     */
    @Test
    void draft11ExampleParsesWithVctAndRecursiveDisclosures() throws Exception {
        SdJwtPresentation presentation = new SdJwtParser().parse(SDJWT_VC_DRAFT11_ISSUANCE);
        assertEquals(27, presentation.getDisclosures().size());

        assertEquals("dc+sd-jwt", presentation.getCredential().getHeader().getType().toString());
        assertEquals("urn:eudi:pid:de:1", presentation.getClaims().get("vct"));
        assertEquals("sha-256", presentation.getClaims().get("_sd_alg"));
        assertEquals("https://pid-issuer.bund.de.example", presentation.getClaims().get("iss"));

        Map<String, Object> disclosed = presentation.getDisclosedClaims();
        assertEquals("Erika", disclosed.get("given_name"));
        assertEquals("Mustermann", disclosed.get("family_name"));
        assertEquals("1963-08-12", disclosed.get("birthdate"));
        assertEquals(2, ((Number) disclosed.get("sex")).intValue());
        assertEquals(62, ((Number) disclosed.get("age_in_years")).intValue());
        assertEquals(1963, ((Number) disclosed.get("age_birth_year")).intValue());
        assertEquals("Gabler", disclosed.get("birth_family_name"));
        assertEquals("2020-03-11", disclosed.get("issuance_date"));
        assertEquals("2030-03-12", disclosed.get("expiry_date"));
        assertEquals("DE", disclosed.get("issuing_authority"));
        assertEquals("DE", disclosed.get("issuing_country"));
        assertEquals(List.of("DE"), disclosed.get("nationalities"));

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) disclosed.get("address");
        assertEquals("Heidestra\u00dfe 17", address.get("street_address"));
        assertEquals("K\u00f6ln", address.get("locality"));
        assertEquals("51147", address.get("postal_code"));
        assertEquals("DE", address.get("country"));
        assertEquals(4, address.size());

        @SuppressWarnings("unchecked")
        Map<String, Object> placeOfBirth = (Map<String, Object>) disclosed.get("place_of_birth");
        assertEquals("Berlin", placeOfBirth.get("locality"));
        assertEquals("DE", placeOfBirth.get("country"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ageEqualOrOver = (Map<String, Object>) disclosed.get("age_equal_or_over");
        assertEquals(Boolean.TRUE, ageEqualOrOver.get("12"));
        assertEquals(Boolean.TRUE, ageEqualOrOver.get("18"));
        assertEquals(Boolean.FALSE, ageEqualOrOver.get("65"));
        assertEquals(6, ageEqualOrOver.size());
    }

    /**
     * Our SdJwtBuilder emits the profile shape: typ dc+sd-jwt and
     * _sd_alg sha-256, and parse(build(...)) round-trips.
     */
    @Test
    void builderEmitsProfileShapeAndRoundTrips() throws Exception {
        ECKey issuerKey = new ECKeyGenerator(Curve.P_256).generate();

        SdJwtBuilder.CredentialPayload payload = new SdJwtBuilder.CredentialPayload();
        payload.setVct("https://credentials.example.com/identity_credential");
        payload.setIss("https://issuer.example.com");
        payload.setIssuedAt(new Date(1000L * 1683000000L));
        payload.getAlwaysDisclosed().put("family_name", "Doe");
        payload.getSelectivelyDisclosed().put("given_name", "John");
        String sdJwt = new SdJwtBuilder().build(payload, new ECDSASigner(issuerKey),
                JWSAlgorithm.ES256, issuerKey.computeThumbprint().toString());

        assertTrue(sdJwt.endsWith("~"));
        String[] parts = sdJwt.split("~");
        assertEquals(2, parts.length, "JWS plus one disclosure (trailing empty dropped by split)");

        SdJwtPresentation presentation = new SdJwtParser().parse(sdJwt);
        assertEquals("dc+sd-jwt", presentation.getCredential().getHeader().getType().toString());
        assertEquals("https://credentials.example.com/identity_credential",
                presentation.getClaims().get("vct"));
        assertEquals("sha-256", presentation.getClaims().get("_sd_alg"));
        assertNotNull(presentation.getClaims().get("_sd"));
        assertTrue(presentation.verifySignature(issuerKey.toPublicJWK()));
        assertEquals("Doe", presentation.getDisclosedClaims().get("family_name"));
        assertEquals("John", presentation.getDisclosedClaims().get("given_name"));

        // The vct is surfaced in the processed payload as well
        assertEquals("https://credentials.example.com/identity_credential",
                presentation.getDisclosedClaims().get("vct"));
    }

    /**
     * Holder key binding over a profile-shaped credential built locally:
     * sd_hash covers the exact pre-KB presentation string.
     */
    @Test
    void keyBindingOverLocallyBuiltCredential() throws Exception {
        ECKey issuerKey = new ECKeyGenerator(Curve.P_256).generate();
        ECKey holderKey = new ECKeyGenerator(Curve.P_256).generate();

        SdJwtBuilder.CredentialPayload payload = new SdJwtBuilder.CredentialPayload();
        payload.setVct("urn:eudi:pid:de:1");
        payload.setIss("https://pid-issuer.example.com");
        payload.setIssuedAt(new Date(1000L * 1683000000L));
        payload.setCnf(SdJwtBuilder.cnfForJwk(holderKey.toPublicJWK()));
        payload.getSelectivelyDisclosed().put("given_name", "Erika");
        String sdJwt = new SdJwtBuilder().build(payload, new ECDSASigner(issuerKey),
                JWSAlgorithm.ES256, issuerKey.computeThumbprint().toString());

        String[] parts = sdJwt.split("~");
        String preKeyBinding = parts[0] + "~" + parts[1] + "~";
        String kbJwt = new KeyBindingJwtBuilder().build(holderKey, "nonce-1", "https://verifier.example.com",
                preKeyBinding, new Date(1000L * 1683000060L));
        SdJwtPresentation presentation = new SdJwtParser().parse(
                parts[0] + "~" + parts[1] + "~" + kbJwt);
        presentation.verifyKeyBinding("nonce-1", "https://verifier.example.com", 1683000060L + 30);
        assertEquals("Erika", presentation.getDisclosedClaims().get("given_name"));

        // Sanity: the sd_hash input is the presentation minus the KB part
        assertEquals(SdJwtDigest.hashOfSdJwt(preKeyBinding),
                presentation.getKeyBindingClaims().get("sd_hash"));
    }
}
