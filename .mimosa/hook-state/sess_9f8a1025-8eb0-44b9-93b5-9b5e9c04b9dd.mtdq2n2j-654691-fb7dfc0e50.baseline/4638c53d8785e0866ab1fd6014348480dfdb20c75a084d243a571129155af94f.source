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

package org.apache.unomi.didvc.services.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bitstring Codec: W3C Bitstring Status List encoding (GZIP with a fixed
 * minimal header and OS byte 0xFF, base64url without padding), MSB-first
 * bit indexing.
 */
class BitstringCodecTest {

    @Test
    void roundTripZeros() {
        byte[] bits = new byte[16];
        String encoded = BitstringCodec.encode(bits);
        assertArrayEquals(bits, BitstringCodec.decode(encoded));
    }

    @Test
    void roundTripWithSetBits() {
        byte[] bits = new byte[16];
        BitstringCodec.setBit(bits, 0, true);
        BitstringCodec.setBit(bits, 7, true);
        BitstringCodec.setBit(bits, 8, true);
        BitstringCodec.setBit(bits, 100, true);
        byte[] decoded = BitstringCodec.decode(BitstringCodec.encode(bits));
        assertArrayEquals(bits, decoded);
        assertTrue(BitstringCodec.getBit(decoded, 0));
        assertTrue(BitstringCodec.getBit(decoded, 7));
        assertTrue(BitstringCodec.getBit(decoded, 8));
        assertTrue(BitstringCodec.getBit(decoded, 100));
        assertFalse(BitstringCodec.getBit(decoded, 1));
    }

    @Test
    void gzipHeaderIsSpecConformant() {
        byte[] decodedHeader = Base64.getUrlDecoder()
                .decode(BitstringCodec.encode(new byte[32]))
                .length >= 10
                        ? Arrays.copyOfRange(Base64.getUrlDecoder().decode(BitstringCodec.encode(new byte[32])), 0, 10)
                        : new byte[0];
        // ID1 ID2
        assertEquals((byte) 0x1f, decodedHeader[0]);
        assertEquals((byte) 0x8b, decodedHeader[1]);
        // CM=8 (deflate)
        assertEquals(8, decodedHeader[2]);
        // FLG=0: no optional header fields
        assertEquals(0, decodedHeader[3]);
        // MTIME=0 (4 bytes) and XFL=0
        for (int i = 4; i < 9; i++) {
            assertEquals(0, decodedHeader[i]);
        }
        // OS=0xFF (unknown) per the Bitstring Status List specification
        assertEquals((byte) 0xFF, decodedHeader[9]);
    }

    @Test
    void encodingIsDeterministic() {
        byte[] bits = new byte[64];
        BitstringCodec.setBit(bits, 3, true);
        assertEquals(BitstringCodec.encode(bits), BitstringCodec.encode(bits));
    }

    @Test
    void bitOrderingIsMsbFirst() {
        byte[] bits = new byte[1];
        BitstringCodec.setBit(bits, 0, true);
        assertEquals((byte) 0x80, bits[0]);
        BitstringCodec.setBit(bits, 7, true);
        assertEquals((byte) 0x81, bits[0]);
        BitstringCodec.setBit(bits, 0, false);
        assertEquals((byte) 0x01, bits[0]);
    }

    @Test
    void decodeRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> BitstringCodec.decode("!!!not-base64url!!!"));
        assertThrows(IllegalArgumentException.class, () -> BitstringCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> BitstringCodec.decode(null));
    }

    @Test
    void indexOutOfRange() {
        byte[] bits = new byte[1];
        assertThrows(IndexOutOfBoundsException.class, () -> BitstringCodec.setBit(bits, 8, true));
        assertThrows(IndexOutOfBoundsException.class, () -> BitstringCodec.getBit(bits, -1));
    }
}
