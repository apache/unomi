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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void queryString_handlesNullAndTruncatesLongValues() {
        assertEquals("", LogSanitizer.queryString(null));
        assertEquals("a=1", LogSanitizer.queryString("a=1"));
        String longQs = repeat("a=b&", 60); // > MAX_QUERY_STRING_LENGTH (200)
        String sanitized = LogSanitizer.queryString(longQs);
        assertTrue(sanitized.endsWith("...[truncated]"), "Long query string should be truncated: " + sanitized);
        assertTrue(sanitized.length() < longQs.length());
    }

    @Test
    void queryParameters_rendersKeyValuePairsAndTruncates() {
        Map<String, List<String>> params = new LinkedHashMap<>();
        params.put("q", Collections.singletonList("hello"));
        params.put("page", Collections.singletonList("2"));
        assertEquals("q=hello&page=2", LogSanitizer.queryParameters(params));

        // Exactly at the limit: 10 params — no truncation marker expected
        Map<String, List<String>> atLimit = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) atLimit.put("k" + i, Collections.singletonList("v" + i));
        String atLimitResult = LogSanitizer.queryParameters(atLimit);
        assertFalse(atLimitResult.contains("...[more params]"), "Should not truncate at exactly 10 params");
        assertEquals(9, countOccurrences(atLimitResult, '&'), "9 separators expected for 10 params");

        // 11 params — truncation marker must follow a separator cleanly
        Map<String, List<String>> overLimit = new LinkedHashMap<>(atLimit);
        overLimit.put("k10", Collections.singletonList("v10"));
        String overLimitResult = LogSanitizer.queryParameters(overLimit);
        assertTrue(overLimitResult.endsWith("...[more params]"), "Should end with truncation marker: " + overLimitResult);
        assertFalse(overLimitResult.contains("&...[more params]"),
                "Separator must not immediately precede truncation marker: " + overLimitResult);
    }

    private static int countOccurrences(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private static String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder(s.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }
}
