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
package org.apache.unomi.api;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link Parameter} YAML and accessors.
 */
public class ParameterTest {

    @Test
    public void testToYamlMaxDepthZeroIncludesExpectedKeys() {
        Parameter p = new Parameter();
        p.setId("pid");
        p.setType("string");
        p.setMultivalued(true);
        p.setDefaultValue("def");
        Map<String, Object> y = p.toYaml(null, 0);
        assertEquals("pid", y.get("id"));
        assertEquals("string", y.get("type"));
        assertTrue(y.containsKey("multivalued"));
        assertEquals("<max depth exceeded>", y.get("defaultValue"));
    }

    @Test
    public void testToYamlMaxDepthZeroAddsDefaultValueTruncationMarkerEvenWhenUnset() {
        Parameter p = new Parameter();
        p.setMultivalued(false);
        Map<String, Object> y = p.toYaml(null, 0);
        assertFalse(y.containsKey("id"));
        assertFalse(y.containsKey("type"));
        assertFalse(y.containsKey("multivalued"));
        assertEquals("<max depth exceeded>", y.get("defaultValue"));
    }

    @Test
    public void testToYamlNormalPath() {
        Parameter p = new Parameter("id1", "number", false);
        p.setDefaultValue(42);
        Map<String, Object> y = p.toYaml(null, 10);
        assertEquals("id1", y.get("id"));
        assertEquals("number", y.get("type"));
        assertFalse(y.containsKey("multivalued"));
        assertEquals(42, y.get("defaultValue"));
    }

    @Test
    public void testToYamlMultivaluedTrueAddsFlag() {
        Parameter p = new Parameter("i", "t", true);
        Map<String, Object> y = p.toYaml(null, 10);
        assertEquals(Boolean.TRUE, y.get("multivalued"));
    }

    @Test
    public void testToStringIsNonEmptyYaml() {
        Parameter p = new Parameter("x", "boolean", false);
        String s = p.toString();
        assertNotNull(s);
        assertTrue(s.length() > 0);
        assertTrue(s.contains("x"));
    }
}
