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

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Unit tests for YamlUtils fluent API.
 * Tests focus on our fluent API, not SnakeYaml's implementation.
 */
public class YamlUtilsTest {

    @Test
    public void testYamlMapBuilderCreate() {
        YamlUtils.YamlMapBuilder builder = YamlUtils.YamlMapBuilder.create();
        assertNotNull("Builder should be created", builder);
    }

    @Test
    public void testYamlMapBuilderPut() {
        Map<String, Object> map = YamlUtils.YamlMapBuilder.create()
            .put("key1", "value1")
            .put("key2", 42)
            .build();
        assertEquals("First value should be set", "value1", map.get("key1"));
        assertEquals("Second value should be set", 42, map.get("key2"));
    }

    @Test
    public void testYamlMapBuilderPutIfNotNull() {
        Map<String, Object> map = YamlUtils.YamlMapBuilder.create()
            .putIfNotNull("key1", "value1")
            .putIfNotNull("key2", null)
            .putIfNotNull("key3", "value3")
            .build();
        assertEquals("Non-null value should be set", "value1", map.get("key1"));
        assertFalse("Null value should not be set", map.containsKey("key2"));
        assertEquals("Another non-null value should be set", "value3", map.get("key3"));
    }

    @Test
    public void testYamlMapBuilderPutIf() {
        Map<String, Object> map = YamlUtils.YamlMapBuilder.create()
            .putIf("key1", "value1", true)
            .putIf("key2", "value2", false)
            .putIf("key3", "value3", true)
            .build();
        assertEquals("Value with true condition should be set", "value1", map.get("key1"));
        assertFalse("Value with false condition should not be set", map.containsKey("key2"));
        assertEquals("Another value with true condition should be set", "value3", map.get("key3"));
    }

    @Test
    public void testYamlMapBuilderPutIfNotEmpty() {
        Map<String, Object> map = YamlUtils.YamlMapBuilder.create()
            .putIfNotEmpty("key1", Arrays.asList("a", "b"))
            .putIfNotEmpty("key2", Collections.emptyList())
            .putIfNotEmpty("key3", null)
            .putIfNotEmpty("key4", Arrays.asList("c"))
            .build();
        assertTrue("Non-empty collection should be set", map.containsKey("key1"));
        assertFalse("Empty collection should not be set", map.containsKey("key2"));
        assertFalse("Null collection should not be set", map.containsKey("key3"));
        assertTrue("Another non-empty collection should be set", map.containsKey("key4"));
    }

    @Test
    public void testYamlMapBuilderChaining() {
        Map<String, Object> map = YamlUtils.YamlMapBuilder.create()
            .put("a", 1)
            .putIfNotNull("b", "value")
            .putIf("c", 3, true)
            .putIfNotEmpty("d", Arrays.asList(1, 2))
            .build();
        assertEquals("All valid entries should be added", 4, map.size());
    }

    @Test
    public void testYamlMapBuilderNullKeyThrowsException() {
        try {
            YamlUtils.YamlMapBuilder.create().put(null, "value");
            fail("Null key should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testYamlMapBuilderNullKeyInPutIfNotNull() {
        try {
            YamlUtils.YamlMapBuilder.create().putIfNotNull(null, "value");
            fail("Null key in putIfNotNull should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testYamlMapBuilderNullKeyInPutIf() {
        try {
            YamlUtils.YamlMapBuilder.create().putIf(null, "value", true);
            fail("Null key in putIf should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testYamlMapBuilderNullKeyInPutIfNotEmpty() {
        try {
            YamlUtils.YamlMapBuilder.create().putIfNotEmpty(null, Arrays.asList(1));
            fail("Null key in putIfNotEmpty should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testYamlMapBuilderBuildReturnsNewMap() {
        YamlUtils.YamlMapBuilder builder = YamlUtils.YamlMapBuilder.create();
        builder.put("key", "value");
        Map<String, Object> map1 = builder.build();
        Map<String, Object> map2 = builder.build();
        assertNotSame("Each build() should return a new map", map1, map2);
        assertEquals("Both maps should have same content", map1, map2);
    }

    @Test
    public void testSetToSortedList() {
        Set<String> set = new LinkedHashSet<>(Arrays.asList("zebra", "apple", "banana"));
        List<String> result = YamlUtils.setToSortedList(set);
        assertNotNull("Result should not be null", result);
        assertEquals("Set should be converted to sorted list", Arrays.asList("apple", "banana", "zebra"), result);
    }

    @Test
    public void testSetToSortedListNull() {
        List<String> result = YamlUtils.setToSortedList((Set<String>) null);
        assertNull("Null set should return null", result);
    }

    @Test
    public void testSetToSortedListEmpty() {
        List<String> result = YamlUtils.setToSortedList(Collections.<String>emptySet());
        assertNull("Empty set should return null", result);
    }

    @Test
    public void testSetToSortedListWithMapper() {
        Set<Integer> set = new LinkedHashSet<>(Arrays.asList(3, 1, 2));
        List<String> result = YamlUtils.setToSortedList(set, String::valueOf);
        assertNotNull("Result should not be null", result);
        assertEquals("Set should be converted to sorted list using mapper", Arrays.asList("1", "2", "3"), result);
    }

    @Test
    public void testSetToSortedListWithMapperNull() {
        try {
            YamlUtils.<Integer, String>setToSortedList(Collections.singleton(1), null);
            fail("Null mapper should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testSetToSortedListWithMapperNullSet() {
        List<String> result = YamlUtils.setToSortedList(null, String::valueOf);
        assertNull("Null set should return null even with mapper", result);
    }

    @Test
    public void testToYamlValueWithYamlConvertible() {
        YamlUtils.YamlConvertible convertible = () -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("test", "value");
            return map;
        };
        Set<Object> visited = new HashSet<>();
        Object result = YamlUtils.toYamlValue(convertible, visited);
        assertTrue("YamlConvertible should be converted to Map", result instanceof Map);
        Map<?, ?> map = (Map<?, ?>) result;
        assertEquals("Converted map should contain test value", "value", map.get("test"));
    }

    @Test
    public void testToYamlValueWithList() {
        List<Object> list = Arrays.asList("a", "b", "c");
        Set<Object> visited = new HashSet<>();
        Object result = YamlUtils.toYamlValue(list, visited);
        assertTrue("List should remain a List", result instanceof List);
        assertEquals("List should be unchanged", list, result);
    }

    @Test
    public void testToYamlValueWithMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");
        Set<Object> visited = new HashSet<>();
        Object result = YamlUtils.toYamlValue(map, visited);
        assertTrue("Map should remain a Map", result instanceof Map);
        assertEquals("Map should contain key-value", "value", ((Map<?, ?>) result).get("key"));
    }

    @Test
    public void testToYamlValueWithNull() {
        Set<Object> visited = new HashSet<>();
        Object result = YamlUtils.toYamlValue(null, visited);
        assertNull("Null should return null", result);
    }

    @Test
    public void testToYamlValueWithPrimitive() {
        Set<Object> visited = new HashSet<>();
        Object result = YamlUtils.toYamlValue(42, visited);
        assertEquals("Primitive should remain unchanged", 42, result);
    }

    @Test
    public void testCircularRef() {
        Map<String, Object> result = YamlUtils.circularRef();
        assertNotNull("circularRef should return a map", result);
        assertEquals("Should contain $ref: circular", "circular", result.get("$ref"));
        assertEquals("Should have only one entry", 1, result.size());
    }

    @Test
    public void testFormatBasic() {
        // Just verify format() works - we don't test SnakeYaml's output format
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("key", "value");
        String result = YamlUtils.format(map);
        assertNotNull("Format should return a string", result);
        assertTrue("Format should contain key", result.contains("key"));
        assertTrue("Format should contain value", result.contains("value"));
    }
}
