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

import org.apache.unomi.didvc.services.util.BitstringCodec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official spec vectors for {@link BitstringCodec}.
 *
 * <p>Empirical findings recorded while writing this test (verified by
 * decoding the spec vector before asserting anything):</p>
 *
 * <ul>
 *   <li>The W3C Bitstring Status List v1.0 spec value (Examples 3/5,
 *       multibase base64url with the leading {@code u} prefix) decodes to
 *       exactly 16384 bytes (16 KiB, 131072 bits) that are ALL ZERO. The
 *       statusListIndex values 94567 (revocation) and 23452 (suspension)
 *       shown in the spec's Example 1 belong to an illustrative
 *       credential and are NOT set in this encoded list, so
 *       {@code getBit(..., 94567)} and {@code getBit(..., 23452)} are both
 *       false — this matches the all-zero expansion the spec describes
 *       for the example.</li>
 *   <li>The vector's gzip header is {@code 1f 8b 08 00 00 00 00 00 00 03}
 *       (XFL=0, OS=3/Unix). Our codec pins OS=0xFF (unknown) per its own
 *       documented framing, so {@code encode(decode(vector))} is NOT
 *       byte-identical: it differs only in the OS byte at index 9. The
 *       DEFLATE body matches at Java's default compression level (6, in
 *       the zlib 4-9 band that produces the same 33 bytes), and the CRC32
 *       and ISIZE trailer values are reproduced exactly. The W3C v1.0
 *       spec text itself does not mandate specific gzip header bytes (it
 *       defers to RFC 1952), which is why decode-side properties plus the
 *       body/trailer equality are asserted instead of full byte
 *       identity.</li>
 * </ul>
 */
class BitstringStatusListVectorTest {

    /**
     * W3C Bitstring Status List v1.0, Examples 3 and 5:
     * https://www.w3.org/TR/vc-bitstring-status-list/ — multibase base64url
     * (leading 'u' is the multibase prefix).
     */
    private static final String W3C_MULTIBASE_VECTOR =
            "uH4sIAAAAAAAAA-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA";

    /**
     * StatusList2021 vector (identical bitstring, published without the
     * multibase prefix): W3C Verifiable Credentials Status List v2021,
     * https://www.w3.org/TR/2023/WD-vc-status-list-20230427/ — the
     * encodedList value of the StatusList2021Credential example.
     */
    private static final String STATUS_LIST_2021_VECTOR =
            "H4sIAAAAAAAAA-3BMQEAAADCoPVPbQwfoAAAAAAAAAAAAAAAAAAAAIC3AYbSVKsAQAAA";

    @Test
    void decodesW3CSpecVectorToSixteenKilobytes() {
        byte[] bitstring = BitstringCodec.decode(stripMultibasePrefix(W3C_MULTIBASE_VECTOR));
        assertEquals(16384, bitstring.length, "16 KiB list");
        assertEquals(131072, bitstring.length * 8, "131072 single-bit entries");
    }

    /**
     * The spec example is the all-zero expansion: no status is set. In
     * particular the Example 1 indexes 94567 (revocation) and 23452
     * (suspension) are not set in THIS encoded list — see class javadoc.
     */
    @Test
    void specVectorHasNoStatusesSet() {
        byte[] bitstring = BitstringCodec.decode(stripMultibasePrefix(W3C_MULTIBASE_VECTOR));
        assertTrue(allZero(bitstring), "example list is all zeros");
        assertFalse(BitstringCodec.getBit(bitstring, 94567));
        assertFalse(BitstringCodec.getBit(bitstring, 23452));
        assertFalse(BitstringCodec.getBit(bitstring, 0));
        assertFalse(BitstringCodec.getBit(bitstring, 131071));
    }

    @Test
    void reencodeReproducesSpecVectorExceptOsByte() {
        String encodedList = stripMultibasePrefix(W3C_MULTIBASE_VECTOR);
        byte[] expected = java.util.Base64.getUrlDecoder().decode(pad(encodedList));
        byte[] actual = java.util.Base64.getUrlDecoder()
                .decode(pad(BitstringCodec.encode(BitstringCodec.decode(encodedList))));

        assertEquals(expected.length, actual.length, "same total size (header + 33-byte body + trailer)");
        // RFC 1952 framing: magic, CM=deflate, FLG=0, MTIME=0, XFL=0
        assertEquals(0x1f, actual[0] & 0xFF);
        assertEquals(0x8b, actual[1] & 0xFF);
        assertEquals(8, actual[2] & 0xFF);
        assertEquals(0, actual[3] & 0xFF);
        assertEquals(0L, actual[4] | actual[5] << 8 | actual[6] << 16 | actual[7] << 24, "MTIME zero");
        assertEquals(0, actual[8] & 0xFF, "XFL zero");
        // The vector was produced with OS=3 (Unix); the codec writes OS=0xFF
        assertEquals(0x03, expected[9] & 0xFF);
        assertEquals(0xFF, actual[9] & 0xFF);
        // Everything else — DEFLATE body, CRC32 and ISIZE — is identical
        byte[] expectedRest = concat(Arrays.copyOfRange(expected, 0, 9),
                Arrays.copyOfRange(expected, 10, expected.length));
        byte[] actualRest = concat(Arrays.copyOfRange(actual, 0, 9),
                Arrays.copyOfRange(actual, 10, actual.length));
        assertArrayEquals(expectedRest, actualRest, "header tail, deflate body and trailer identical");

        // Trailer correctness against the decoded bitstring
        byte[] bitstring = BitstringCodec.decode(encodedList);
        CRC32 crc = new CRC32();
        crc.update(bitstring);
        assertEquals((int) crc.getValue(), littleEndianInt(actual, actual.length - 8),
                "CRC32 over the raw bitstring");
        assertEquals(16384, littleEndianInt(actual, actual.length - 4), "ISIZE");
    }

    @Test
    void statusList2021VectorDecodesIdentically() {
        // The StatusList2021 spec example carries the same bitstring without
        // the multibase prefix; a StatusList2021 implementation feeds the
        // encodedList value straight into the codec
        byte[] bitstring = BitstringCodec.decode(STATUS_LIST_2021_VECTOR);
        assertEquals(16384, bitstring.length);
        assertTrue(allZero(bitstring));
    }

    @Test
    void setBitEncodeDecodeRoundTrip() {
        byte[] bitstring = new byte[16384];
        BitstringCodec.setBit(bitstring, 94567, true);
        BitstringCodec.setBit(bitstring, 23452, true);
        BitstringCodec.setBit(bitstring, 0, true);
        BitstringCodec.setBit(bitstring, 131071, true);

        byte[] decoded = BitstringCodec.decode(BitstringCodec.encode(bitstring));
        assertArrayEquals(bitstring, decoded, "codec round-trips its own output exactly");
        assertTrue(BitstringCodec.getBit(decoded, 94567));
        assertTrue(BitstringCodec.getBit(decoded, 23452));
        assertTrue(BitstringCodec.getBit(decoded, 0));
        assertTrue(BitstringCodec.getBit(decoded, 131071));
        assertFalse(BitstringCodec.getBit(decoded, 1));
        assertFalse(BitstringCodec.getBit(decoded, 131070));
    }

    private static String stripMultibasePrefix(String multibase) {
        assertTrue(multibase.startsWith("u"), "base64url multibase prefix");
        return multibase.substring(1);
    }

    private static boolean allZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8
                | (bytes[offset + 2] & 0xFF) << 16 | (bytes[offset + 3] & 0xFF) << 24;
    }

    private static String pad(String base64url) {
        int remainder = base64url.length() % 4;
        return remainder == 0 ? base64url : base64url + "====".substring(remainder);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
