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

package org.apache.unomi.didvc.gateway;

import org.bouncycastle.crypto.digests.KeccakDigest;

import java.math.BigInteger;

/**
 * Minimal ABI encoding for the gateway's EVM anchor contract:
 *
 * <pre>
 * contract DidAnchorRegistry {
 *     function anchor(bytes32 didHash, bytes32 docHash) external;
 *     function resolve(bytes32 didHash) external view
 *         returns (bytes32 docHash, uint64 timestamp, address controller);
 * }
 * </pre>
 *
 * Only the two functions above are encoded — no general ABI layer.
 * Function selectors are the leading 4 bytes of keccak-256 of the
 * canonical signature.
 */
public final class EvmAbi {

    /** anchor(bytes32,bytes32) selector — keccak-256 of the signature. */
    public static final String ANCHOR_SELECTOR = selector("anchor(bytes32,bytes32)");
    /** resolve(bytes32) selector. */
    public static final String RESOLVE_SELECTOR = selector("resolve(bytes32)");

    private static final int WORD_HEX = 64;

    private EvmAbi() {
    }

    /**
     * keccak-256 of a canonical signature, first 4 bytes as 0x-hex.
     *
     * @param signature the canonical function signature
     * @return the 0x-prefixed 8-hex-character selector
     */
    public static String selector(String signature) {
        KeccakDigest keccak = new KeccakDigest(256);
        byte[] input = signature.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        keccak.update(input, 0, input.length);
        byte[] digest = new byte[32];
        keccak.doFinal(digest, 0);
        return "0x" + hex(digest).substring(0, 8);
    }

    /**
     * Encodes {@code anchor(bytes32 didHash, bytes32 docHash)} calldata.
     *
     * @param didHash 32-byte DID hash as 0x-hex (padded/truncated to 32)
     * @param docHash 32-byte document hash as 0x-hex
     * @return the 0x-prefixed calldata
     */
    public static String encodeAnchor(String didHash, String docHash) {
        return ANCHOR_SELECTOR + word(didHash) + word(docHash);
    }

    /**
     * Encodes {@code resolve(bytes32 didHash)} calldata.
     *
     * @param didHash 32-byte DID hash as 0x-hex
     * @return the 0x-prefixed calldata
     */
    public static String encodeResolve(String didHash) {
        return RESOLVE_SELECTOR + word(didHash);
    }

    /**
     * Decodes the anchor contract's {@code resolve} return data
     * (bytes32 docHash, uint64 timestamp, address controller).
     *
     * @param returnData the 0x-prefixed hex return data
     * @return the decoded anchor parts (documentHash, timestamp, controller)
     */
    public static String[] decodeResolveResult(String returnData) {
        String body = strip0x(returnData);
        if (body.length() < WORD_HEX * 3) {
            throw new IllegalArgumentException("resolve return data too short");
        }
        String documentHash = "0x" + body.substring(0, WORD_HEX);
        long timestamp = new BigInteger(body.substring(WORD_HEX * 2, WORD_HEX * 3), 16)
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        String controller = "0x" + body.substring(WORD_HEX * 2 + 24, WORD_HEX * 3);
        return new String[]{documentHash, String.valueOf(timestamp), controller};
    }

    /**
     * Left-pads a hash word to 32 bytes of hex (truncating overlong
     * values to their last 32 bytes).
     *
     * @param value the 0x-hex value
     * @return 64 hex characters
     */
    static String word(String value) {
        String body = strip0x(value);
        if (body.length() > WORD_HEX) {
            body = body.substring(body.length() - WORD_HEX);
        }
        StringBuilder padded = new StringBuilder();
        for (int i = body.length(); i < WORD_HEX; i++) {
            padded.append('0');
        }
        return padded.append(body).toString();
    }

    static String strip0x(String value) {
        return value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
    }

    static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
