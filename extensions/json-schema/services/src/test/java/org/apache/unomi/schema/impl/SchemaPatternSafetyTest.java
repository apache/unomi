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
package org.apache.unomi.schema.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards the identifier-validation patterns in the shipped schemas against a linear-time regression.
 *
 * These patterns run on identifiers taken from public tracking requests, so they must evaluate in
 * linear time. A pattern with overlapping quantified alternatives (for example
 * {@code ^(\w|[-_@\.]){0,60}$}, where {@code _} matches both branches) degrades to exponential
 * backtracking on the java.util.regex engine, so a bounded id can pin a validation thread. This test
 * fails if any shipped id pattern exhibits that behaviour, and also pins down the accepted/rejected
 * identifier space so a "simplification" cannot quietly change validation semantics.
 */
public class SchemaPatternSafetyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] SCHEMA_RESOURCES = {
            "/META-INF/cxs/schemas/events/event.json",
            "/META-INF/cxs/schemas/items/item.json"
    };

    private List<String> collectPatterns(String resource) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            assertTrue("Missing shipped schema resource " + resource, is != null);
            List<String> patterns = new ArrayList<>();
            collectPatterns(MAPPER.readTree(is), patterns);
            return patterns;
        }
    }

    private void collectPatterns(JsonNode node, List<String> patterns) {
        if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                if ("pattern".equals(field.getKey()) && field.getValue().isTextual()) {
                    patterns.add(field.getValue().asText());
                } else {
                    collectPatterns(field.getValue(), patterns);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectPatterns(child, patterns);
            }
        }
    }

    @Test(timeout = 5000)
    public void idPatternsAreLinearTimeAndKeepSemantics() throws Exception {
        // The classic exponential-backtracking probe for the old id pattern: a run of characters that
        // are valid under both alternatives, followed by one invalid character forcing full backtracking.
        StringBuilder probe = new StringBuilder();
        for (int i = 0; i < 59; i++) {
            probe.append('_');
        }
        probe.append('!');

        List<String> patterns = new ArrayList<>();
        for (String resource : SCHEMA_RESOURCES) {
            patterns.addAll(collectPatterns(resource));
        }
        assertFalse("Expected id patterns in the shipped schemas", patterns.isEmpty());

        for (String patternString : patterns) {
            Pattern pattern = Pattern.compile(patternString);

            // Must finish effectively instantly; the test-level timeout catches exponential backtracking.
            assertFalse("Probe input must be rejected by " + patternString,
                    pattern.matcher(probe).matches());

            // Previously accepted identifiers must still be accepted.
            assertTrue(pattern.matcher("user_name-123@example.com").matches());
            assertTrue(pattern.matcher("").matches());
            assertTrue(pattern.matcher("a.b-c_d@e").matches());

            // Previously rejected identifiers must still be rejected.
            assertFalse(pattern.matcher("white space").matches());
            assertFalse(pattern.matcher("slash/id").matches());
            StringBuilder tooLong = new StringBuilder();
            for (int i = 0; i < 61; i++) {
                tooLong.append('a');
            }
            assertFalse(pattern.matcher(tooLong).matches());
        }
    }
}
