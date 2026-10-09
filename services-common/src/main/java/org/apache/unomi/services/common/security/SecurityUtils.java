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

package org.apache.unomi.services.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Utility class for security-related helpers, such as safe logging of sensitive values.
 */
public class SecurityUtils {

    private static final String MASK_SUFFIX = "****";
    private static final int VISIBLE_PREFIX_LENGTH = 4;

    /**
     * Masks a secret value for safe use in log statements.
     * Shows the first {@value #VISIBLE_PREFIX_LENGTH} characters followed by {@code ****},
     * or {@code ****} entirely if the secret is null or too short to reveal safely.
     *
     * @param secret the secret to mask (e.g. an API key or shared token)
     * @return a masked representation safe for logging
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.length() <= VISIBLE_PREFIX_LENGTH) {
            return MASK_SUFFIX;
        }
        return secret.substring(0, VISIBLE_PREFIX_LENGTH) + MASK_SUFFIX;
    }

    /**
     * Compares two strings in constant time relative to their UTF-8 byte lengths.
     * Different lengths still return {@code false}; a dummy compare runs so the call cost does not
     * collapse to a single length check.
     *
     * @param left  first value, may be {@code null}
     * @param right second value, may be {@code null}
     * @return {@code true} only when both are non-null and their UTF-8 bytes are equal
     */
    public static boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            MessageDigest.isEqual(leftBytes, leftBytes);
            return false;
        }
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }
}
