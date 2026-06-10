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
package org.apache.unomi.rest.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LogSanitizer}, guarding the log-injection defenses shared by the exception mappers.
 */
class LogSanitizerTest {

    @Test
    void forLogging_replacesControlCharacters() {
        assertEquals("a_b", LogSanitizer.forLogging("a\nb"));
        assertEquals("a_b", LogSanitizer.forLogging("a\rb"));
        assertEquals("a_b", LogSanitizer.forLogging("a\tb"));
    }

    @Test
    void forLogging_replacesLogFormatMarkers() {
        assertEquals("a_b_c_d_e_", LogSanitizer.forLogging("a{b}c%d$e\\"));
    }

    @Test
    void forLogging_keepsPlainText() {
        assertEquals("GET /context.json", LogSanitizer.forLogging("GET /context.json"));
    }

    @Test
    void forLogging_handlesNull() {
        assertEquals("", LogSanitizer.forLogging(null));
    }

    @Test
    void url_handlesNullAndTruncatesLongValues() {
        assertEquals("null", LogSanitizer.url(null));
        String longUrl = repeat("a", 600);
        String sanitized = LogSanitizer.url(longUrl);
        assertTrue(sanitized.endsWith("...[truncated]"), "Long URL should be truncated: " + sanitized);
        assertTrue(sanitized.length() < longUrl.length());
    }

    @Test
    void httpMethod_normalizesAndWhitelists() {
        assertEquals("GET", LogSanitizer.httpMethod("get"));
        assertEquals("POST", LogSanitizer.httpMethod("post"));
        assertEquals("UNKNOWN", LogSanitizer.httpMethod(null));
        assertEquals("UNKNOWN", LogSanitizer.httpMethod(""));
        // Non-standard methods are still sanitized but not blindly trusted.
        assertEquals("WEIRD", LogSanitizer.httpMethod("weird"));
    }

    @Test
    void className_keepsValidIdentifiersAndStripsTheRest() {
        assertEquals("com.example.Foo$Bar", LogSanitizer.className("com.example.Foo$Bar"));
        assertEquals("bad_name_", LogSanitizer.className("bad name!"));
        assertEquals("Unknown", LogSanitizer.className(null));
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
