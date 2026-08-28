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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Disclosure and digest helpers for SD-JWT (RFC 9901). A disclosure is a
 * JSON array {@code [salt, claimName, claimValue]}; its digest is the
 * base64url SHA-256 of its base64url-encoded form, listed in the signed
 * payload's {@code _sd} array.
 */
public final class SdJwtDigest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder B64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DECODER = Base64.getUrlDecoder();

    private SdJwtDigest() {
    }

    /**
     * Builds a disclosure for a claim and returns its base64url form.
     *
     * @param claimName  the claim name
     * @param claimValue the claim value
     * @return the base64url-encoded disclosure
     */
    public static String buildDisclosure(String claimName, Object claimValue) {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        List<Object> disclosure = new ArrayList<>(3);
        disclosure.add(B64_URL.encodeToString(salt));
        disclosure.add(claimName);
        disclosure.add(claimValue);
        return B64_URL.encodeToString(toJsonBytes(disclosure));
    }

    /**
     * Computes the digest of an encoded disclosure.
     *
     * @param encodedDisclosure the base64url-encoded disclosure
     * @return the base64url SHA-256 digest
     */
    public static String digestOf(String encodedDisclosure) {
        return B64_URL.encodeToString(sha256(encodedDisclosure.getBytes(StandardCharsets.US_ASCII)));
    }

    /**
     * Decodes a disclosure into its three parts.
     *
     * @param encodedDisclosure the base64url-encoded disclosure
     * @return {@code [salt, claimName, claimValue]}
     */
    public static List<Object> decodeDisclosure(String encodedDisclosure) {
        byte[] json = B64_URL_DECODER.decode(encodedDisclosure);
        return fromJsonBytes(json, List.class);
    }

    /**
     * Computes the base64url SHA-256 of the presented disclosures section
     * (the disclosures joined with {@code ~}), used as the KB-JWT
     * {@code sd_hash} claim.
     *
     * @param disclosures the presented disclosures, in order
     * @return the sd_hash value
     */
    public static String hashOfDisclosures(List<String> disclosures) {
        return B64_URL.encodeToString(sha256(String.join("~", disclosures).getBytes(StandardCharsets.US_ASCII)));
    }

    static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static byte[] toJsonBytes(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    static <T> T fromJsonBytes(byte[] json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }
}
