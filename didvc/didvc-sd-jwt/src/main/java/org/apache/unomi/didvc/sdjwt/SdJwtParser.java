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

import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses combined SD-JWT presentations
 * ({@code <JWS>~<disclosure>~...~<KB-JWT>}) and validates the disclosure
 * digests against the signed payload's {@code _sd} array.
 */
public class SdJwtParser {

    /**
     * Parses a combined SD-JWT presentation.
     *
     * @param combinedPresentation the presentation string
     * @return the parsed presentation
     * @throws ParseException          when the JWS parts are unreadable
     * @throws IllegalArgumentException when disclosures do not match the signed digests
     */
    @SuppressWarnings("unchecked")
    public SdJwtPresentation parse(String combinedPresentation) throws ParseException {
        if (combinedPresentation == null || combinedPresentation.isEmpty()) {
            throw new IllegalArgumentException("Presentation must not be empty");
        }
        String[] parts = combinedPresentation.split("~");
        SignedJWT credential = SignedJWT.parse(parts[0]);
        List<String> disclosures = new ArrayList<>();
        SignedJWT keyBindingJwt = null;
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty() && parts[i].split("\\.").length == 3) {
                keyBindingJwt = SignedJWT.parse(parts[i]);
            } else if (!parts[i].isEmpty()) {
                disclosures.add(parts[i]);
            }
        }

        Map<String, Object> claims = credential.getJWTClaimsSet().toJSONObject();
        Map<String, Object> disclosedClaims = new LinkedHashMap<>();
        Object sdClaim = claims.get("_sd");
        if (sdClaim != null) {
            if (!(sdClaim instanceof List)) {
                throw new IllegalArgumentException("_sd must be an array");
            }
            Set<String> expectedDigests = new HashSet<>((List<String>) sdClaim);
            for (String disclosure : disclosures) {
                String digest = SdJwtDigest.digestOf(disclosure);
                if (!expectedDigests.contains(digest)) {
                    throw new IllegalArgumentException("Disclosure digest is not signed in _sd: " + digest);
                }
                List<Object> disclosureParts = SdJwtDigest.decodeDisclosure(disclosure);
                if (disclosureParts.size() != 3 || !(disclosureParts.get(1) instanceof String)) {
                    throw new IllegalArgumentException("Malformed disclosure: " + disclosure);
                }
                disclosedClaims.put((String) disclosureParts.get(1), disclosureParts.get(2));
            }
        } else if (!disclosures.isEmpty()) {
            throw new IllegalArgumentException("Disclosures present but payload has no _sd array");
        }

        Map<String, Object> keyBindingClaims = keyBindingJwt == null
                ? null : keyBindingJwt.getJWTClaimsSet().toJSONObject();
        return new SdJwtPresentation(credential, claims, disclosedClaims, disclosures,
                keyBindingJwt, keyBindingClaims);
    }
}
