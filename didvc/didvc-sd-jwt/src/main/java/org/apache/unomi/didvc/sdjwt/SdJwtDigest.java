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
 * Disclosure and digest helpers for SD-JWT (RFC 9901). A disclosure for an
 * object claim is a JSON array {@code [salt, claimName, claimValue]}; a
 * disclosure for an array entry is {@code [salt, entryValue]}. Its digest is
 * the base64url SHA-256 of its base64url-encoded form, listed in a signed
 * {@code _sd} array or referenced from an array-entry
 * {@code {"...": "<digest>"}} placeholder.
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
     * Computes the base64url SHA-256 over the SD-JWT as presented without
     * the key-binding JWT, used as the KB-JWT {@code sd_hash} claim
     * (RFC 9901 §4.3.1): the input is the exact US-ASCII bytes of
     * {@code <Issuer-signed JWT>~<Disclosure 1>~...~<Disclosure N>~}, i.e.
     * the JWT and every disclosure each followed by a tilde.
     *
     * @param sdJwtWithoutKeyBinding the presentation string without the KB-JWT
     * @return the sd_hash value
     */
    public static String hashOfSdJwt(String sdJwtWithoutKeyBinding) {
        return B64_URL.encodeToString(sha256(sdJwtWithoutKeyBinding.getBytes(StandardCharsets.US_ASCII)));
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
