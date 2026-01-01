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

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.rules.Rule;
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
        YamlUtils.YamlConvertible convertible = (visited, maxDepth) -> {
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

    // ========== Circular Reference Detection Tests ==========

    @Test
    public void testRuleInheritanceChainNoCircularRef() {
        // Test that Rule -> MetadataItem -> Item inheritance chain doesn't produce false circular refs
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        metadata.setScope("systemscope");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testCondition");
        rule.setCondition(condition);
        
        Map<String, Object> result = rule.toYaml(null);
        assertNotNull("Rule should serialize to YAML", result);
        assertFalse("Should not contain circular reference marker", result.containsKey("$ref"));
        assertTrue("Should contain condition", result.containsKey("condition"));
        assertTrue("Should contain itemId from Item parent", result.containsKey("itemId"));
        assertTrue("Should contain metadata from MetadataItem parent", result.containsKey("metadata"));
    }

    @Test
    public void testRuleWithCircularReferenceInCondition() {
        // Test that a real circular reference (Rule referenced in condition's parameterValues) is detected
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testCondition");
        // Create a circular reference: condition's parameterValues contains the rule itself
        condition.getParameterValues().put("referencedRule", rule);
        rule.setCondition(condition);
        
        Map<String, Object> result = rule.toYaml(null);
        assertNotNull("Rule should serialize to YAML", result);
        assertTrue("Should contain condition", result.containsKey("condition"));
        
        // Check that the circular reference is detected in the condition's parameterValues
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        assertNotNull("Condition should be serialized", conditionMap);
        Map<String, Object> paramValues = (Map<String, Object>) conditionMap.get("parameterValues");
        assertNotNull("Parameter values should exist", paramValues);
        Map<String, Object> circularRef = (Map<String, Object>) paramValues.get("referencedRule");
        assertNotNull("Circular reference should be detected", circularRef);
        assertEquals("Should contain circular reference marker", "circular", circularRef.get("$ref"));
    }

    @Test
    public void testRuleWithCircularReferenceInActions() {
        // Test circular reference in actions list
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Action action = new Action();
        action.setActionTypeId("testAction");
        // Create circular reference: action's parameterValues contains the rule
        action.getParameterValues().put("triggeringRule", rule);
        rule.setActions(Collections.singletonList(action));
        
        Map<String, Object> result = rule.toYaml(null);
        assertNotNull("Rule should serialize to YAML", result);
        assertTrue("Should contain actions", result.containsKey("actions"));
        
        List<?> actions = (List<?>) result.get("actions");
        assertNotNull("Actions list should exist", actions);
        assertEquals("Should have one action", 1, actions.size());
        
        Map<String, Object> actionMap = (Map<String, Object>) actions.get(0);
        Map<String, Object> paramValues = (Map<String, Object>) actionMap.get("parameterValues");
        assertNotNull("Parameter values should exist", paramValues);
        Map<String, Object> circularRef = (Map<String, Object>) paramValues.get("triggeringRule");
        assertNotNull("Circular reference should be detected", circularRef);
        assertEquals("Should contain circular reference marker", "circular", circularRef.get("$ref"));
    }

    @Test
    public void testNestedCircularReference() {
        // Test nested circular reference: Rule -> Condition -> nested Condition -> Rule
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Condition outerCondition = new Condition();
        outerCondition.setConditionTypeId("outerCondition");
        
        Condition nestedCondition = new Condition();
        nestedCondition.setConditionTypeId("nestedCondition");
        // Nested condition references the rule
        nestedCondition.getParameterValues().put("ruleRef", rule);
        
        // Outer condition contains nested condition
        outerCondition.getParameterValues().put("nested", nestedCondition);
        rule.setCondition(outerCondition);
        
        Map<String, Object> result = rule.toYaml(null);
        assertNotNull("Rule should serialize to YAML", result);
        
        // Navigate through the nested structure
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        Map<String, Object> paramValues = (Map<String, Object>) conditionMap.get("parameterValues");
        Map<String, Object> nestedConditionMap = (Map<String, Object>) paramValues.get("nested");
        Map<String, Object> nestedParamValues = (Map<String, Object>) nestedConditionMap.get("parameterValues");
        Map<String, Object> circularRef = (Map<String, Object>) nestedParamValues.get("ruleRef");
        
        assertNotNull("Circular reference should be detected in nested structure", circularRef);
        assertEquals("Should contain circular reference marker", "circular", circularRef.get("$ref"));
    }

    @Test
    public void testMultipleCircularReferences() {
        // Test multiple circular references to the same object
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testCondition");
        // Multiple references to the same rule
        condition.getParameterValues().put("rule1", rule);
        condition.getParameterValues().put("rule2", rule);
        condition.getParameterValues().put("rule3", rule);
        rule.setCondition(condition);
        
        Map<String, Object> result = rule.toYaml(null);
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        Map<String, Object> paramValues = (Map<String, Object>) conditionMap.get("parameterValues");
        
        // All three references should show circular ref
        for (String key : Arrays.asList("rule1", "rule2", "rule3")) {
            Map<String, Object> circularRef = (Map<String, Object>) paramValues.get(key);
            assertNotNull("Circular reference should be detected for " + key, circularRef);
            assertEquals("Should contain circular reference marker for " + key, "circular", circularRef.get("$ref"));
        }
    }


    @Test
    public void testCircularReferenceInList() {
        // Test circular reference in a list
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testCondition");
        // List containing the rule itself
        condition.getParameterValues().put("ruleList", Arrays.asList(rule, "other", rule));
        rule.setCondition(condition);
        
        Map<String, Object> result = rule.toYaml(null);
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        Map<String, Object> paramValues = (Map<String, Object>) conditionMap.get("parameterValues");
        List<?> ruleList = (List<?>) paramValues.get("ruleList");
        
        assertNotNull("Rule list should exist", ruleList);
        assertEquals("List should have 3 elements", 3, ruleList.size());
        
        // First element should be circular ref
        Map<String, Object> circularRef1 = (Map<String, Object>) ruleList.get(0);
        assertEquals("First element should be circular ref", "circular", circularRef1.get("$ref"));
        
        // Second element should be string
        assertEquals("Second element should be string", "other", ruleList.get(1));
        
        // Third element should also be circular ref
        Map<String, Object> circularRef2 = (Map<String, Object>) ruleList.get(2);
        assertEquals("Third element should be circular ref", "circular", circularRef2.get("$ref"));
    }

    @Test
    public void testCircularReferenceInNestedMap() {
        // Test circular reference in nested map structure
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testCondition");
        // Nested map containing the rule
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("level1", new HashMap<String, Object>() {{
            put("level2", new HashMap<String, Object>() {{
                put("rule", rule);
            }});
        }});
        condition.getParameterValues().put("nested", nestedMap);
        rule.setCondition(condition);
        
        Map<String, Object> result = rule.toYaml(null);
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        Map<String, Object> paramValues = (Map<String, Object>) conditionMap.get("parameterValues");
        Map<String, Object> nested = (Map<String, Object>) paramValues.get("nested");
        Map<String, Object> level1 = (Map<String, Object>) nested.get("level1");
        Map<String, Object> level2 = (Map<String, Object>) level1.get("level2");
        Map<String, Object> circularRef = (Map<String, Object>) level2.get("rule");
        
        assertNotNull("Circular reference should be detected in nested map", circularRef);
        assertEquals("Should contain circular reference marker", "circular", circularRef.get("$ref"));
    }

    @Test
    public void testNoFalseCircularRefInInheritance() {
        // Test that inheritance chain (Rule -> MetadataItem -> Item) doesn't create false circular refs
        // This is the main bug we're fixing
        Rule rule = new Rule();
        rule.setItemId("test-rule");
        Metadata metadata = new Metadata("test-rule");
        metadata.setScope("systemscope");
        rule.setMetadata(metadata);
        
        Condition condition = new Condition();
        condition.setConditionTypeId("unavailableConditionType");
        condition.getParameterValues().put("comparisonOperator", "equals");
        condition.getParameterValues().put("propertyName", "testProperty");
        condition.getParameterValues().put("propertyValue", "testValue");
        rule.setCondition(condition);
        
        Action action = new Action();
        action.setActionTypeId("test");
        rule.setActions(Collections.singletonList(action));
        
        Map<String, Object> result = rule.toYaml(null);
        
        // Should NOT contain $ref: circular at the top level
        assertNotNull("Rule should serialize", result);
        assertFalse("Should not have false circular reference at top level", 
                    result.containsKey("$ref") && "circular".equals(result.get("$ref")));
        
        // Should contain all expected fields from inheritance chain
        assertTrue("Should contain itemId from Item", result.containsKey("itemId"));
        assertTrue("Should contain itemType from Item", result.containsKey("itemType"));
        assertEquals("itemType should be 'rule'", "rule", result.get("itemType"));
        assertTrue("Should contain metadata from MetadataItem", result.containsKey("metadata"));
        assertTrue("Should contain condition", result.containsKey("condition"));
        assertTrue("Should contain actions", result.containsKey("actions"));
        
        // Verify condition structure
        Map<String, Object> conditionMap = (Map<String, Object>) result.get("condition");
        assertNotNull("Condition should be present", conditionMap);
        assertEquals("Condition should have correct type", "unavailableConditionType", conditionMap.get("type"));
        
        // Verify actions structure
        List<?> actions = (List<?>) result.get("actions");
        assertNotNull("Actions should be present", actions);
        assertEquals("Should have one action", 1, actions.size());
    }

    @Test
    public void testItemTypeIsAlwaysIncluded() {
        // Test that itemType is always included in YAML output, even if null
        // This reflects the actual state of the object
        Rule rule = new Rule();
        Metadata metadata = new Metadata("test-id");
        metadata.setScope("systemscope");
        rule.setMetadata(metadata);
        
        Map<String, Object> result = rule.toYaml(null);
        
        // itemType should always be present in output (set in Item constructor for Rule)
        assertTrue("itemType should be included", result.containsKey("itemType"));
        assertEquals("itemType should be 'rule'", "rule", result.get("itemType"));
        
        // itemId should also always be included
        assertTrue("itemId should be included", result.containsKey("itemId"));
    }

    @Test
    public void testItemIdAndItemTypeIncludedEvenWhenNull() {
        // Test that itemId and itemType are always included, even when null
        // This ensures YAML output reflects the actual state of the object
        Rule rule = new Rule();
        // Explicitly set itemId and itemType to null to test null handling
        rule.setItemId(null);
        rule.setItemType(null);
        
        Map<String, Object> result = rule.toYaml(null);
        
        // Both should be included even if null
        assertTrue("itemId should be included even when null", result.containsKey("itemId"));
        assertNull("itemId should be null", result.get("itemId"));
        
        assertTrue("itemType should be included even when null", result.containsKey("itemType"));
        assertNull("itemType should be null", result.get("itemType"));
    }

    @Test
    public void testItemIdFromMetadata() {
        // Test that itemId is set from metadata and included in YAML
        Rule rule = new Rule();
        Metadata metadata = new Metadata("test-rule-id");
        metadata.setScope("systemscope");
        rule.setMetadata(metadata);
        
        Map<String, Object> result = rule.toYaml(null);
        
        // itemId should be set from metadata.getId()
        assertTrue("itemId should be included when set from metadata", result.containsKey("itemId"));
        assertEquals("itemId should match metadata id", "test-rule-id", result.get("itemId"));
    }

    @Test
    public void testVisitedSetIsSharedCorrectly() {
        // Test that visited set is properly shared across nested calls
        Rule rule1 = new Rule();
        rule1.setItemId("rule1");
        rule1.setMetadata(new Metadata("rule1"));
        
        Rule rule2 = new Rule();
        rule2.setItemId("rule2");
        rule2.setMetadata(new Metadata("rule2"));
        
        // rule1 references rule2, rule2 references rule1 (mutual circular reference)
        Condition condition1 = new Condition();
        condition1.setConditionTypeId("test");
        condition1.getParameterValues().put("otherRule", rule2);
        rule1.setCondition(condition1);
        
        Condition condition2 = new Condition();
        condition2.setConditionTypeId("test");
        condition2.getParameterValues().put("otherRule", rule1);
        rule2.setCondition(condition2);
        
        // Serialize rule1 - should detect circular ref when it encounters rule2 which references rule1
        Map<String, Object> result1 = rule1.toYaml(null);
        assertNotNull("Rule1 should serialize", result1);
        
        Map<String, Object> conditionMap1 = (Map<String, Object>) result1.get("condition");
        Map<String, Object> paramValues1 = (Map<String, Object>) conditionMap1.get("parameterValues");
        Map<String, Object> rule2Ref = (Map<String, Object>) paramValues1.get("otherRule");
        
        // rule2 should be serialized, but when it tries to reference rule1, it should detect circular ref
        assertNotNull("Rule2 reference should exist", rule2Ref);
        // rule2 itself should be fully serialized (not circular), but its condition's otherRule should be circular
        Map<String, Object> conditionMap2 = (Map<String, Object>) rule2Ref.get("condition");
        assertNotNull("Rule2's condition should exist", conditionMap2);
        Map<String, Object> paramValues2 = (Map<String, Object>) conditionMap2.get("parameterValues");
        Map<String, Object> rule1CircularRef = (Map<String, Object>) paramValues2.get("otherRule");
        assertNotNull("Circular reference to rule1 should be detected", rule1CircularRef);
        assertEquals("Should contain circular reference marker", "circular", rule1CircularRef.get("$ref"));
    }
}
