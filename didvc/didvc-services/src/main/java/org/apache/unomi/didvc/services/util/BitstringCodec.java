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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * Encoder/decoder for W3C Bitstring Status List v1.0 bitstrings.
 *
 * <p>Per the specification, a bitstring is GZIP-compressed (RFC 1952) with a
 * fixed minimal header — no optional header fields, MTIME 0, and the OS byte
 * set to 0xFF (unknown) — then base64url-encoded without padding. Java's
 * GZIPOutputStream writes OS 0, so the GZIP framing is produced here
 * explicitly to stay spec-conformant.</p>
 *
 * <p>Bits are indexed MSB-first: bit 0 of an entry is the most significant bit
 * of the first byte. A set bit means the status applies (e.g. revoked).</p>
 */
public final class BitstringCodec {

    /**
     * GZIP header: ID1 ID2 CM=8 FLG=0 MTIME=0(4) XFL=0 OS=0xFF.
     */
    private static final byte[] GZIP_HEADER = new byte[]{
            (byte) 0x1f, (byte) 0x8b, 8, 0, 0, 0, 0, 0, 0, (byte) 0xFF
    };

    private BitstringCodec() {
    }

    /**
     * Encodes a raw bitstring (byte array, MSB-first bit order) into the
     * base64url representation used by the {@code encodedList} property.
     *
     * @param bitstring the raw bitstring
     * @return the encoded list
     */
    public static String encode(byte[] bitstring) {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(bitstring);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(bitstring.length / 2, 32));
            out.write(GZIP_HEADER, 0, GZIP_HEADER.length);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                out.write(buffer, 0, count);
            }
            CRC32 crc = new CRC32();
            crc.update(bitstring);
            byte[] trailer = new byte[8];
            writeLittleEndianInt(trailer, 0, (int) crc.getValue());
            writeLittleEndianInt(trailer, 4, bitstring.length);
            out.write(trailer, 0, trailer.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
        } finally {
            deflater.end();
        }
    }

    /**
     * Decodes an {@code encodedList} value back into the raw bitstring.
     *
     * @param encodedList the encoded list
     * @return the raw bitstring
     */
    public static byte[] decode(String encodedList) {
        if (encodedList == null || encodedList.isEmpty()) {
            throw new IllegalArgumentException("encodedList must not be empty");
        }
        byte[] compressed = Base64.getUrlDecoder().decode(pad(encodedList));
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gis.readAllBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid bitstring encoding", e);
        }
    }

    /**
     * Reads a bit, MSB-first within each byte.
     *
     * @param bitstring the raw bitstring
     * @param index     the bit index
     * @return true when the bit is set
     */
    public static boolean getBit(byte[] bitstring, int index) {
        checkIndex(bitstring, index);
        return (bitstring[index / 8] & (1 << (7 - (index % 8)))) != 0;
    }

    /**
     * Sets or clears a bit, MSB-first within each byte.
     *
     * @param bitstring the raw bitstring
     * @param index     the bit index
     * @param value     the bit value
     */
    public static void setBit(byte[] bitstring, int index, boolean value) {
        checkIndex(bitstring, index);
        if (value) {
            bitstring[index / 8] |= (byte) (1 << (7 - (index % 8)));
        } else {
            bitstring[index / 8] &= (byte) ~(1 << (7 - (index % 8)));
        }
    }

    private static void checkIndex(byte[] bitstring, int index) {
        if (index < 0 || index >= bitstring.length * 8) {
            throw new IndexOutOfBoundsException("Bit index " + index + " out of range for "
                    + bitstring.length + " bytes");
        }
    }

    private static void writeLittleEndianInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
        target[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static String pad(String base64url) {
        int remainder = base64url.length() % 4;
        return remainder == 0 ? base64url : base64url + "====".substring(remainder);
    }
}
