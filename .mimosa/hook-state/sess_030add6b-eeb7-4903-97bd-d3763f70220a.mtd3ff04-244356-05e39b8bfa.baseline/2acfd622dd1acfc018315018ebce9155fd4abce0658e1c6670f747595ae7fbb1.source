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
 * digests against the signed payload, following the RFC 9901 §8.3
 * processing model: digests are honoured from {@code _sd} arrays at any
 * depth of the payload tree and from array-entry
 * {@code {"...": "<digest>"}} placeholders (including, recursively, inside
 * disclosed values); the disclosed claims are then assembled into the
 * Processed SD-JWT Payload.
 */
public class SdJwtParser {

    private static final String SD_KEY = "_sd";
    private static final String SD_ALG_KEY = "_sd_alg";
    private static final String SHA_256 = "sha-256";
    private static final String ARRAY_ENTRY_KEY = "...";

    /**
     * Parses a combined SD-JWT presentation.
     *
     * @param combinedPresentation the presentation string
     * @return the parsed presentation
     * @throws ParseException          when the JWS parts are unreadable
     * @throws IllegalArgumentException when disclosures do not match the signed
     *                                  digests, use the wrong shape for their
     *                                  position, or the payload declares an
     *                                  unsupported {@code _sd_alg}
     */
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
        // RFC 9901 §4.1.1: _sd_alg (top level only) must be understood;
        // sha-256 is the default when absent
        Object sdAlg = claims.get(SD_ALG_KEY);
        if (sdAlg != null && !SHA_256.equals(sdAlg)) {
            throw new IllegalArgumentException("Unsupported _sd_alg: " + sdAlg);
        }

        Map<String, List<Object>> disclosuresByDigest = new LinkedHashMap<>();
        for (String disclosure : disclosures) {
            List<Object> disclosureParts = SdJwtDigest.decodeDisclosure(disclosure);
            boolean objectClaimShape = disclosureParts.size() == 3 && disclosureParts.get(1) instanceof String;
            boolean arrayEntryShape = disclosureParts.size() == 2;
            if (!objectClaimShape && !arrayEntryShape) {
                throw new IllegalArgumentException("Malformed disclosure: " + disclosure);
            }
            disclosuresByDigest.put(SdJwtDigest.digestOf(disclosure), disclosureParts);
        }

        Assembly assembly = new Assembly(disclosuresByDigest);
        Map<String, Object> disclosedClaims = assembly.processObject(claims);
        for (String digest : disclosuresByDigest.keySet()) {
            if (!assembly.isReferenced(digest)) {
                throw new IllegalArgumentException("Disclosure digest is not signed in _sd: " + digest);
            }
        }

        Map<String, Object> keyBindingClaims = keyBindingJwt == null
                ? null : keyBindingJwt.getJWTClaimsSet().toJSONObject();
        return new SdJwtPresentation(credential, claims, disclosedClaims, disclosures,
                keyBindingJwt, keyBindingClaims);
    }

    /**
     * RFC 9901 §8.3 steps 3.b–3.f: assembles the Processed SD-JWT Payload
     * from the signed payload tree and the presented disclosures, and tracks
     * which disclosures were referenced (step 5: unreferenced disclosures
     * must be rejected) and that no digest occurs twice (step 4).
     */
    private static final class Assembly {

        private final Map<String, List<Object>> disclosuresByDigest;
        private final Set<String> referencedDigests = new HashSet<>();
        private final Set<String> seenDigests = new HashSet<>();

        Assembly(Map<String, List<Object>> disclosuresByDigest) {
            this.disclosuresByDigest = disclosuresByDigest;
        }

        boolean isReferenced(String digest) {
            return referencedDigests.contains(digest);
        }

        /**
         * Copies an object without {@code _sd}/{@code _sd_alg}, recursively
         * processes its values, and inserts object-claim disclosures whose
         * digests the object's {@code _sd} array lists.
         */
        Map<String, Object> processObject(Map<String, Object> object) {
            Object sdClaim = object.get(SD_KEY);
            if (sdClaim != null && !(sdClaim instanceof List)) {
                throw new IllegalArgumentException("_sd must be an array");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                if (SD_KEY.equals(entry.getKey()) || SD_ALG_KEY.equals(entry.getKey())) {
                    continue;
                }
                result.put(entry.getKey(), processValue(entry.getValue()));
            }
            if (sdClaim != null) {
                for (Object digest : (List<?>) sdClaim) {
                    if (!(digest instanceof String)) {
                        throw new IllegalArgumentException("_sd entries must be strings");
                    }
                    noteDigest((String) digest);
                    List<Object> disclosure = disclosuresByDigest.get(digest);
                    if (disclosure == null) {
                        // decoy digest or disclosure not presented: ignored
                        continue;
                    }
                    if (disclosure.size() != 3) {
                        throw new IllegalArgumentException(
                                "Disclosure for an object claim must be [salt, name, value]: " + digest);
                    }
                    String claimName = (String) disclosure.get(1);
                    if (SD_KEY.equals(claimName) || ARRAY_ENTRY_KEY.equals(claimName)) {
                        throw new IllegalArgumentException("Forbidden claim name in disclosure: " + claimName);
                    }
                    if (result.containsKey(claimName)) {
                        throw new IllegalArgumentException("Claim name already exists at this level: " + claimName);
                    }
                    result.put(claimName, processValue(disclosure.get(2)));
                }
            }
            return result;
        }

        /**
         * Recursively processes a payload value; arrays drop
         * {@code {"...": digest}} placeholders whose disclosure was not
         * presented and replace the others in-order with the disclosed
         * value (which must be a two-element {@code [salt, value]}
         * disclosure).
         */
        private Object processValue(Object value) {
            if (value instanceof Map) {
                return processObject(asObjectMap(value));
            }
            if (value instanceof List) {
                List<Object> result = new ArrayList<>();
                for (Object element : (List<?>) value) {
                    String placeholderDigest = arrayEntryDigest(element);
                    if (placeholderDigest == null) {
                        result.add(processValue(element));
                        continue;
                    }
                    noteDigest(placeholderDigest);
                    List<Object> disclosure = disclosuresByDigest.get(placeholderDigest);
                    if (disclosure == null) {
                        // array entry not disclosed: dropped from the view
                        continue;
                    }
                    if (disclosure.size() != 2) {
                        throw new IllegalArgumentException(
                                "Disclosure for an array entry must be [salt, value]: " + placeholderDigest);
                    }
                    result.add(processValue(disclosure.get(1)));
                }
                return result;
            }
            return value;
        }

        /**
         * Returns the digest when the element is an array-entry placeholder
         * {@code {"...": "<digest>"}} (exactly one key), else null.
         */
        private String arrayEntryDigest(Object element) {
            if (!(element instanceof Map)) {
                return null;
            }
            Map<String, Object> map = asObjectMap(element);
            if (map.size() != 1 || !map.containsKey(ARRAY_ENTRY_KEY)) {
                return null;
            }
            Object digest = map.get(ARRAY_ENTRY_KEY);
            if (!(digest instanceof String)) {
                throw new IllegalArgumentException("... placeholder must reference a string digest");
            }
            return (String) digest;
        }

        private void noteDigest(String digest) {
            // RFC 9901 §8.3 step 4: a digest occurring twice is rejected
            if (!seenDigests.add(digest)) {
                throw new IllegalArgumentException("Digest appears more than once: " + digest);
            }
            referencedDigests.add(digest);
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asObjectMap(Object value) {
            return (Map<String, Object>) value;
        }
    }
}
