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

package org.apache.unomi.didvc.services.impl;

import org.apache.unomi.didvc.api.DidDocumentData;
import org.apache.unomi.didvc.api.services.DidMethodResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code did:key} method adapter: derives the DID document in-process from
 * the multibase/multicodec-encoded public key embedded in the identifier
 * (W3C did:key method, ed25519-pub 2020 style keys — multicodec
 * {@code 0xed} in base58btc, the {@code z6Mk…} form). No network access;
 * resolution is a pure function of the DID.
 */
@Component(service = DidMethodResolver.class, property = "didvc.did.method=key", immediate = true)
public class DidKeyMethodResolver implements DidMethodResolver {

    public static final String METHOD = "key";

    private static final Logger LOGGER = LoggerFactory.getLogger(DidKeyMethodResolver.class);

    /** The did:key method spec's DID context. */
    private static final String DID_V1_CONTEXT = "https://www.w3.org/ns/did/v1";

    private static final int ED25519_PUB_MULTICODEC = 0xed;
    private static final int ED25519_PUB_LENGTH = 32;

    @Override
    public String getMethod() {
        return METHOD;
    }

    @Override
    public DidDocumentData resolve(String did) {
        String methodSpecificId = did.substring("did:key:".length());
        try {
            byte[] key = decodeKey(methodSpecificId);
            if (key.length != ED25519_PUB_LENGTH) {
                LOGGER.debug("Unsupported did:key length {} for {}", key.length, did);
                return null;
            }
            return documentFor(did, key);
        } catch (IllegalArgumentException e) {
            LOGGER.debug("Unsupported did:key encoding for {}: {}", did, e.getMessage());
            return null;
        }
    }

    private DidDocumentData documentFor(String did, byte[] ed25519PublicKey) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "OKP");
        jwk.put("crv", "Ed25519");
        jwk.put("x", Base64.getUrlEncoder().withoutPadding().encodeToString(ed25519PublicKey));

        DidDocumentData.VerificationMethod method = new DidDocumentData.VerificationMethod();
        method.setId(did + "#" + did.substring("did:key:".length()));
        method.setType("JsonWebKey2020");
        method.setController(did);
        method.setPublicKeyJwk(jwk);

        DidDocumentData document = new DidDocumentData();
        document.setContext(Arrays.asList(DID_V1_CONTEXT));
        document.setId(did);
        // addVerificationMethod also lists the key as an assertion method
        document.addVerificationMethod(method);
        return document;
    }

    /**
     * Decodes the multibase+multicodec method-specific identifier: the
     * multibase prefix selects the encoding, the leading varint carries the
     * multicodec key code, and the remainder is the raw public key.
     */
    private byte[] decodeKey(String methodSpecificId) {
        char prefix = methodSpecificId.charAt(0);
        byte[] decoded;
        if (prefix == 'z') {
            decoded = Base58.decode(methodSpecificId.substring(1));
        } else {
            throw new IllegalArgumentException("Unsupported multibase prefix '" + prefix + "'");
        }
        int offset = 0;
        long code = 0;
        int shift = 0;
        do {
            if (offset >= decoded.length) {
                throw new IllegalArgumentException("Truncated multicodec prefix");
            }
            code |= ((long) (decoded[offset] & 0x7f)) << shift;
            shift += 7;
        } while ((decoded[offset++] & 0x80) != 0 && shift < 56);
        if (code != ED25519_PUB_MULTICODEC) {
            throw new IllegalArgumentException("Unsupported multicodec 0x" + Long.toHexString(code));
        }
        return Arrays.copyOfRange(decoded, offset, decoded.length);
    }

    /**
     * Base58btc (Bitcoin alphabet) codec for the {@code z} multibase
     * prefix.
     */
    static final class Base58 {
        private static final char[] ALPHABET =
                "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
        private static final int[] INDEX = new int[128];

        static {
            for (int i = 0; i < INDEX.length; i++) {
                INDEX[i] = -1;
            }
            for (int i = 0; i < ALPHABET.length; i++) {
                INDEX[ALPHABET[i]] = i;
            }
        }

        private Base58() {
        }

        static String encode(byte[] input) {
            if (input.length == 0) {
                return "";
            }
            int zeros = 0;
            while (zeros < input.length && input[zeros] == 0) {
                zeros++;
            }
            byte[] copy = Arrays.copyOf(input, input.length);
            char[] encoded = new char[copy.length * 2];
            int outputStart = encoded.length;
            for (int inputStart = zeros; inputStart < copy.length; ) {
                encoded[--outputStart] = ALPHABET[divmod58(copy, inputStart)];
                if (copy[inputStart] == 0) {
                    inputStart++;
                }
            }
            while (outputStart < encoded.length && encoded[outputStart] == ALPHABET[0]) {
                outputStart++;
            }
            while (--zeros >= 0) {
                encoded[--outputStart] = ALPHABET[0];
            }
            return new String(encoded, outputStart, encoded.length - outputStart);
        }

        static byte[] decode(String input) {
            if (input.isEmpty()) {
                return new byte[0];
            }
            byte[] input58 = new byte[input.length()];
            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);
                int digit = c < 128 ? INDEX[c] : -1;
                if (digit < 0) {
                    throw new IllegalArgumentException("Invalid base58 character '" + c + "'");
                }
                input58[i] = (byte) digit;
            }
            int zeros = 0;
            while (zeros < input58.length && input58[zeros] == 0) {
                zeros++;
            }
            byte[] decoded = new byte[input.length()];
            int outputStart = decoded.length;
            for (int inputStart = zeros; inputStart < input58.length; ) {
                decoded[--outputStart] = divmod256(input58, inputStart);
                if (input58[inputStart] == 0) {
                    inputStart++;
                }
            }
            while (outputStart < decoded.length && decoded[outputStart] == 0) {
                outputStart++;
            }
            return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
        }

        private static byte divmod58(byte[] number, int startAt) {
            int remainder = 0;
            for (int i = startAt; i < number.length; i++) {
                int digit = number[i] & 0xff;
                int temp = remainder * 256 + digit;
                number[i] = (byte) (temp / 58);
                remainder = temp % 58;
            }
            return (byte) remainder;
        }

        private static byte divmod256(byte[] number, int startAt) {
            int remainder = 0;
            for (int i = startAt; i < number.length; i++) {
                int digit = number[i] & 0xff;
                int temp = remainder * 58 + digit;
                number[i] = (byte) (temp / 256);
                remainder = temp % 256;
            }
            return (byte) remainder;
        }
    }
}
