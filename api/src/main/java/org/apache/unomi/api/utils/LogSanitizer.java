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
package org.apache.unomi.api.utils;

/**
 * Sanitizes untrusted, request-derived values before they are written to a log.
 * <p>
 * Anything that arrives over the network is attacker-controlled, so writing it verbatim into a log
 * makes the log itself an attack surface: an embedded newline lets an attacker forge log records
 * (making a real attack look like routine traffic, or implicating someone else), control characters
 * can corrupt terminals and log shippers, and an unbounded value can flood the log. Security
 * warnings are the worst place for this, because those are exactly the lines shipped to a SIEM and
 * trusted during incident response.
 * <p>
 * Values that never leave the server — enum names, role sets, hashes, rule configuration authored by
 * an administrator — do not need this. Use it for request bodies, headers, cookies, query and path
 * parameters, uploaded filenames, and event properties.
 */
public final class LogSanitizer {

    /** Long enough to identify a value, short enough that it cannot flood the log. */
    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
    }

    /**
     * Replaces every character that is not printable ASCII, and every log-format marker
     * ({@code \ { } % $}), with an underscore, then truncates. This removes the newlines and control
     * characters used for log injection, and neutralises markers that a downstream log formatter
     * might otherwise interpret.
     *
     * @param input the untrusted value, may be {@code null}
     * @return a value that is always safe to place in a log message; {@code "null"} when input was null
     */
    public static String forLogging(String input) {
        return forLogging(input, MAX_LENGTH);
    }

    /**
     * As {@link #forLogging(String)}, but with a caller-chosen length limit for contexts that need
     * more room (a request URL, an exception message) than the default.
     *
     * @param input     the untrusted value, may be {@code null}
     * @param maxLength the length beyond which the value is truncated
     * @return a value that is always safe to place in a log message; {@code "null"} when input was null
     */
    public static String forLogging(String input, int maxLength) {
        if (input == null) {
            return "null";
        }
        // Clamped: a negative limit would make substring throw, from inside a helper whose whole
        // contract is that it is always safe to call in a log statement. No caller passes one today,
        // but a computed limit (a remaining-budget calculation, say) would be an easy way to turn a
        // security-refusal log line into an uncaught exception.
        int limit = Math.max(0, maxLength);
        String value = input.length() > limit ? input.substring(0, limit) + "...[truncated]" : input;
        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x20 && c <= 0x7E && c != '\\' && c != '{' && c != '}' && c != '%' && c != '$') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }
}
