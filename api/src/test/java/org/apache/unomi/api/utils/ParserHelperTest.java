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

import org.apache.unomi.api.*;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.services.DefinitionsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ParserHelperTest {

    @Mock
    private DefinitionsService definitionsService;

    private Event event;
    private Profile profile;
    private Session session;

    @Before
    public void setUp() {
        // Create real objects instead of mocks
        profile = new Profile();
        profile.setItemId("testProfile");
        Map<String, Object> properties = new HashMap<>();
        properties.put("property1", "profile value");
        properties.put("testProperty", "test value");
        profile.setProperties(properties);

        session = new Session();
        session.setItemId("testSession");
        Map<String, Object> sessionProperties = new HashMap<>();
        sessionProperties.put("property2", "session value");
        session.setProperties(sessionProperties);

        event = new Event();
        event.setItemId("testEvent");
        event.setProfile(profile);
        event.setSession(session);
        Map<String, Object> eventProperties = new HashMap<>();
        eventProperties.put("testEventProperty", "event value");
        event.setProperties(eventProperties);
    }

    @Test
    public void testResolveConditionType() {
        // Create test condition
        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");

        // Create test condition type
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testConditionType");

        // Mock definitions service
        when(definitionsService.getConditionType("testConditionType")).thenReturn(conditionType);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertTrue("Condition type should be resolved", result);
        assertEquals("Condition type should be set", conditionType, condition.getConditionType());
    }

    @Test
    public void testResolveConditionTypeWithParent() {
        // Create test condition
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");

        // Create parent condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");

        // Create condition types
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        childType.setParentCondition(parentCondition);

        ConditionType parentType = new ConditionType();
        parentType.setItemId("parentConditionType");

        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertTrue("Condition type should be resolved", result);
        assertEquals("Child condition type should be set", childType, condition.getConditionType());
        assertEquals("Parent condition type should be set", parentType,
            condition.getConditionType().getParentCondition().getConditionType());
    }

    @Test
    public void testResolveActionType() {
        // Create test action
        Action action = new Action();
        action.setActionTypeId("testActionType");

        // Create test action type
        ActionType actionType = new ActionType();
        actionType.setItemId("testActionType");

        // Mock definitions service
        when(definitionsService.getActionType("testActionType")).thenReturn(actionType);

        // Test resolution
        boolean result = ParserHelper.resolveActionType(definitionsService, action);
        assertTrue("Action type should be resolved", result);
        assertEquals("Action type should be set", actionType, action.getActionType());
    }

    @Test
    public void testResolveActionTypes() {
        // Create test rule with actions
        Rule rule = new Rule();
        Action action1 = new Action();
        action1.setActionTypeId("action1");
        Action action2 = new Action();
        action2.setActionTypeId("action2");
        rule.setActions(Arrays.asList(action1, action2));

        // Create action types
        ActionType actionType1 = new ActionType();
        actionType1.setItemId("action1");
        ActionType actionType2 = new ActionType();
        actionType2.setItemId("action2");

        // Mock definitions service
        when(definitionsService.getActionType("action1")).thenReturn(actionType1);
        when(definitionsService.getActionType("action2")).thenReturn(actionType2);

        // Test resolution
        boolean result = ParserHelper.resolveActionTypes(definitionsService, rule, false);
        assertTrue("Action types should be resolved", result);
        assertEquals("Action type 1 should be set", actionType1, action1.getActionType());
        assertEquals("Action type 2 should be set", actionType2, action2.getActionType());
    }

    @Test
    public void testResolveValueType() {
        // Create test property type
        PropertyType propertyType = new PropertyType();
        propertyType.setValueTypeId("testValueType");

        // Create test value type
        ValueType valueType = new ValueType();
        valueType.setId("testValueType");

        // Mock definitions service
        when(definitionsService.getValueType("testValueType")).thenReturn(valueType);

        // Test resolution
        ParserHelper.resolveValueType(definitionsService, propertyType);
        assertEquals("Value type should be set", valueType, propertyType.getValueType());
    }

    @Test
    public void testParseMapWithPlaceholders() {
        // Set up test data
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("key1", "${profileProperty::property1}");
        inputMap.put("key2", "static value");
        inputMap.put("key3", "${sessionProperty::property2}");

        // Test parsing
        Map<String, Object> result = ParserHelper.parseMap(event, inputMap, ParserHelper.DEFAULT_VALUE_EXTRACTORS);

        assertEquals("Profile property should be resolved", "profile value", result.get("key1"));
        assertEquals("Static value should remain unchanged", "static value", result.get("key2"));
        assertEquals("Session property should be resolved", "session value", result.get("key3"));
    }

    @Test
    public void testExtractValue() throws Exception {
        // Test value extraction
        Object result = ParserHelper.extractValue("simpleProfileProperty::testProperty",
            event, ParserHelper.DEFAULT_VALUE_EXTRACTORS);

        assertEquals("Property value should be extracted", "test value", result);
    }

    @Test
    public void testExtractNestedValue() throws Exception {
        // Test nested property extraction
        Object result = ParserHelper.extractValue("profileProperty::property1",
            event, ParserHelper.DEFAULT_VALUE_EXTRACTORS);

        assertEquals("Nested property value should be extracted", "profile value", result);
    }

    @Test
    public void testExtractSessionValue() throws Exception {
        // Test session property extraction
        Object result = ParserHelper.extractValue("sessionProperty::property2",
            event, ParserHelper.DEFAULT_VALUE_EXTRACTORS);

        assertEquals("Session property value should be extracted", "session value", result);
    }

    @Test
    public void testExtractEventValue() throws Exception {
        // Test event property extraction
        Object result = ParserHelper.extractValue("eventProperty::properties.testEventProperty",
            event, ParserHelper.DEFAULT_VALUE_EXTRACTORS);

        assertEquals("Event property value should be extracted", "event value", result);
    }

    @Test
    public void testHasContextualParameter() {
        // Set up test data
        Map<String, Object> values = new HashMap<>();
        values.put("key1", "profileProperty::property1");
        values.put("key2", "static value");
        values.put("nested", Collections.singletonMap("key3", "sessionProperty::property2"));

        // Test contextual parameter detection
        assertTrue("Should detect contextual parameter in root",
            ParserHelper.hasContextualParameter(values, ParserHelper.DEFAULT_VALUE_EXTRACTORS));
        assertTrue("Should detect contextual parameter in nested map",
            ParserHelper.hasContextualParameter(values, ParserHelper.DEFAULT_VALUE_EXTRACTORS));
    }

    @Test
    public void testVisitConditions() {
        // Create test conditions
        Condition rootCondition = new Condition();
        rootCondition.setConditionTypeId("root");

        Condition childCondition = new Condition();
        childCondition.setConditionTypeId("child");

        rootCondition.setParameter("subCondition", childCondition);
        rootCondition.setParameter("subConditions", Collections.singletonList(childCondition));

        // Create visitor to track visits
        final List<String> visitedTypes = new ArrayList<>();
        ParserHelper.ConditionVisitor visitor = new ParserHelper.ConditionVisitor() {
            @Override
            public void visit(Condition condition) {
                visitedTypes.add(condition.getConditionTypeId());
            }

            @Override
            public void postVisit(Condition condition) {
                // Not testing post-visit in this test
            }
        };

        // Test visiting
        ParserHelper.visitConditions(rootCondition, visitor);

        assertEquals("Should visit all conditions", Arrays.asList("root", "child", "child"), visitedTypes);
    }

    @Test
    public void testResolveConditionEventTypes() {
        // Create test condition structure
        Condition rootCondition = new Condition();
        rootCondition.setConditionTypeId("eventTypeCondition");
        rootCondition.setParameter("eventTypeId", "testEvent");

        // Mock definitionsService to return null for condition types (not needed for this simple parameter extraction test)
        when(definitionsService.getConditionType(anyString())).thenReturn(null);

        // Test event type resolution
        Set<String> eventTypes = ParserHelper.resolveConditionEventTypes(rootCondition, definitionsService);

        assertTrue("Should contain event type", eventTypes.contains("testEvent"));
        assertEquals("Should only contain one event type", 1, eventTypes.size());
    }

    @Test
    public void testResolveConditionEventTypesWithNegation() {
        // Create test condition structure with negation
        Condition notCondition = new Condition();
        notCondition.setConditionTypeId("notCondition");

        Condition eventCondition = new Condition();
        eventCondition.setConditionTypeId("eventTypeCondition");
        eventCondition.setParameter("eventTypeId", "testEvent");

        notCondition.setParameter("subCondition", eventCondition);

        // Mock definitionsService to return null for condition types (not needed for this simple parameter extraction test)
        when(definitionsService.getConditionType(anyString())).thenReturn(null);

        // Test event type resolution
        Set<String> eventTypes = ParserHelper.resolveConditionEventTypes(notCondition, definitionsService);

        assertTrue("Should use wildcard for negated event condition", eventTypes.contains("*"));
        assertEquals("Should only contain wildcard", 1, eventTypes.size());
    }

    @Test
    public void testResolveConditionTypeWithEventTypeCondition() {
        // Create test condition based on eventTypeCondition.json
        Condition condition = new Condition();
        condition.setConditionTypeId("eventTypeCondition");
        condition.setParameter("eventTypeId", "testEvent");

        // Create parent condition type (eventPropertyCondition)
        ConditionType parentType = new ConditionType();
        parentType.setItemId("eventPropertyCondition");

        // Create child condition type (eventTypeCondition)
        ConditionType childType = new ConditionType();
        childType.setItemId("eventTypeCondition");

        // Create parent condition as defined in eventTypeCondition.json
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("eventPropertyCondition");
        Map<String, Object> parameterValues = new HashMap<>();
        parameterValues.put("propertyName", "eventType");
        parameterValues.put("propertyValue", "parameter::eventTypeId");
        parameterValues.put("comparisonOperator", "equals");
        parentCondition.setParameterValues(parameterValues);

        // Set parent condition
        childType.setParentCondition(parentCondition);

        // Mock definitions service
        when(definitionsService.getConditionType("eventTypeCondition")).thenReturn(childType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(parentType);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertTrue("Condition type should be resolved", result);
        assertEquals("Child condition type should be set", childType, condition.getConditionType());

        // Verify parent condition is properly resolved
        Condition resolvedParentCondition = condition.getConditionType().getParentCondition();
        assertNotNull("Parent condition should be set", resolvedParentCondition);
        assertEquals("Parent condition type should be set", parentType, resolvedParentCondition.getConditionType());
        assertEquals("Parent condition propertyName should match", "eventType", resolvedParentCondition.getParameter("propertyName"));
        assertEquals("Parent condition propertyValue should match", "parameter::eventTypeId", resolvedParentCondition.getParameter("propertyValue"));
        assertEquals("Parent condition comparisonOperator should match", "equals", resolvedParentCondition.getParameter("comparisonOperator"));
    }

    @Test
    public void testResolveConditionTypeWithMultiLevelParents() {
        // Create test condition
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");

        // Create parent conditions chain: child -> parent1 -> parent2 -> parent3
        Condition parent1Condition = new Condition();
        parent1Condition.setConditionTypeId("parent1Type");
        Map<String, Object> parent1Params = new HashMap<>();
        parent1Params.put("parent1Param", "parent1Value");
        parent1Condition.setParameterValues(parent1Params);

        Condition parent2Condition = new Condition();
        parent2Condition.setConditionTypeId("parent2Type");
        Map<String, Object> parent2Params = new HashMap<>();
        parent2Params.put("parent2Param", "parent2Value");
        parent2Condition.setParameterValues(parent2Params);

        Condition parent3Condition = new Condition();
        parent3Condition.setConditionTypeId("parent3Type");
        Map<String, Object> parent3Params = new HashMap<>();
        parent3Params.put("parent3Param", "parent3Value");
        parent3Condition.setParameterValues(parent3Params);

        // Create condition types and link them
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        childType.setParentCondition(parent1Condition);

        ConditionType parent1Type = new ConditionType();
        parent1Type.setItemId("parent1Type");
        parent1Type.setParentCondition(parent2Condition);

        ConditionType parent2Type = new ConditionType();
        parent2Type.setItemId("parent2Type");
        parent2Type.setParentCondition(parent3Condition);

        ConditionType parent3Type = new ConditionType();
        parent3Type.setItemId("parent3Type");

        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("parent1Type")).thenReturn(parent1Type);
        when(definitionsService.getConditionType("parent2Type")).thenReturn(parent2Type);
        when(definitionsService.getConditionType("parent3Type")).thenReturn(parent3Type);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertTrue("Condition type should be resolved", result);
        assertEquals("Child condition type should be set", childType, condition.getConditionType());

        // Verify parent conditions are properly resolved
        Condition resolvedParent1 = condition.getConditionType().getParentCondition();
        assertNotNull("Parent1 condition should be set", resolvedParent1);
        assertEquals("Parent1 condition type should be set", parent1Type, resolvedParent1.getConditionType());
        assertEquals("Parent1 param should match", "parent1Value", resolvedParent1.getParameter("parent1Param"));

        Condition resolvedParent2 = resolvedParent1.getConditionType().getParentCondition();
        assertNotNull("Parent2 condition should be set", resolvedParent2);
        assertEquals("Parent2 condition type should be set", parent2Type, resolvedParent2.getConditionType());
        assertEquals("Parent2 param should match", "parent2Value", resolvedParent2.getParameter("parent2Param"));

        Condition resolvedParent3 = resolvedParent2.getConditionType().getParentCondition();
        assertNotNull("Parent3 condition should be set", resolvedParent3);
        assertEquals("Parent3 condition type should be set", parent3Type, resolvedParent3.getConditionType());
        assertEquals("Parent3 param should match", "parent3Value", resolvedParent3.getParameter("parent3Param"));
    }

    @Test
    public void testResolveConditionTypeWithCircularParentReference() {
        // Create test condition
        Condition condition = new Condition();
        condition.setConditionTypeId("conditionA");

        // Create circular parent conditions: A -> B -> C -> A
        Condition conditionB = new Condition();
        conditionB.setConditionTypeId("conditionB");
        Map<String, Object> paramsB = new HashMap<>();
        paramsB.put("paramB", "valueB");
        conditionB.setParameterValues(paramsB);

        Condition conditionC = new Condition();
        conditionC.setConditionTypeId("conditionC");
        Map<String, Object> paramsC = new HashMap<>();
        paramsC.put("paramC", "valueC");
        conditionC.setParameterValues(paramsC);

        Condition circularConditionA = new Condition();
        circularConditionA.setConditionTypeId("conditionA");
        Map<String, Object> paramsA = new HashMap<>();
        paramsA.put("paramA", "valueA");
        circularConditionA.setParameterValues(paramsA);

        // Create condition types with circular references
        ConditionType typeA = new ConditionType();
        typeA.setItemId("conditionA");
        typeA.setParentCondition(conditionB);

        ConditionType typeB = new ConditionType();
        typeB.setItemId("conditionB");
        typeB.setParentCondition(conditionC);

        ConditionType typeC = new ConditionType();
        typeC.setItemId("conditionC");
        typeC.setParentCondition(circularConditionA); // Creates circular reference back to A

        // Mock definitions service with proper verification
        when(definitionsService.getConditionType("conditionA")).thenReturn(typeA);
        when(definitionsService.getConditionType("conditionB")).thenReturn(typeB);
        when(definitionsService.getConditionType("conditionC")).thenReturn(typeC);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertFalse("Condition type resolution should fail due to circular reference", result);

        // Verify that the condition type is not set to prevent infinite loops
        assertNull("Condition type should not be set for circular reference", condition.getConditionType());

        // Verify that the definitions service was called for each condition type
        // We expect multiple calls for conditionA due to the circular reference
        verify(definitionsService, atLeast(1)).getConditionType("conditionA");
        verify(definitionsService, times(1)).getConditionType("conditionB");
        verify(definitionsService, times(1)).getConditionType("conditionC");
    }

    @Test
    public void testResolveConditionTypeWithParentSubConditions() {
        // Create test condition
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");

        // Create parent condition with subConditions
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");

        // Create subConditions for parent
        Condition subCondition1 = new Condition();
        subCondition1.setConditionTypeId("subCondition1Type");
        
        Condition subCondition2 = new Condition();
        subCondition2.setConditionTypeId("subCondition2Type");

        // Set up parent condition's parameters including subConditions
        parentCondition.setParameter("operator", "and");
        parentCondition.setParameter("subConditions", Arrays.asList(subCondition1, subCondition2));

        // Create condition types
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        childType.setParentCondition(parentCondition);

        ConditionType parentType = new ConditionType();
        parentType.setItemId("parentConditionType");

        ConditionType subCondition1Type = new ConditionType();
        subCondition1Type.setItemId("subCondition1Type");

        ConditionType subCondition2Type = new ConditionType();
        subCondition2Type.setItemId("subCondition2Type");

        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);
        when(definitionsService.getConditionType("subCondition1Type")).thenReturn(subCondition1Type);
        when(definitionsService.getConditionType("subCondition2Type")).thenReturn(subCondition2Type);

        // Test resolution
        boolean result = ParserHelper.resolveConditionType(definitionsService, condition, "testContext");
        assertTrue("Condition type should be resolved", result);

        // Verify child condition type is set
        assertEquals("Child condition type should be set", childType, condition.getConditionType());

        // Verify parent condition is properly resolved
        Condition resolvedParent = condition.getConditionType().getParentCondition();
        assertNotNull("Parent condition should be set", resolvedParent);
        assertEquals("Parent condition type should be set", parentType, resolvedParent.getConditionType());

        // Verify subConditions are properly resolved
        @SuppressWarnings("unchecked")
        List<Condition> resolvedSubConditions = (List<Condition>) resolvedParent.getParameter("subConditions");
        assertNotNull("SubConditions should be present", resolvedSubConditions);
        assertEquals("Should have two subConditions", 2, resolvedSubConditions.size());
        assertEquals("First subCondition type should be set", subCondition1Type, resolvedSubConditions.get(0).getConditionType());
        assertEquals("Second subCondition type should be set", subCondition2Type, resolvedSubConditions.get(1).getConditionType());
    }

    @Test
    public void testResolveBooleanConditionWithParentConditionUsingBooleanCondition() {
        // Test scenario: booleanCondition A has subCondition B
        // subCondition B has parentCondition which is booleanCondition C
        // This should NOT be detected as a circular reference because they are different instances
        Condition booleanConditionA = new Condition();
        booleanConditionA.setConditionTypeId("booleanCondition");
        booleanConditionA.setParameter("operator", "and");
        
        Condition subConditionB = new Condition();
        subConditionB.setConditionTypeId("eventPropertyCondition");
        Map<String, Object> subConditionBParams = new HashMap<>();
        subConditionBParams.put("propertyName", "testProperty");
        subConditionBParams.put("propertyValue", "testValue");
        subConditionB.setParameterValues(subConditionBParams);
        booleanConditionA.setParameter("subConditions", Arrays.asList(subConditionB));
        
        Condition booleanConditionC = new Condition();
        booleanConditionC.setConditionTypeId("booleanCondition");
        booleanConditionC.setParameter("operator", "or");
        
        Condition subConditionD = new Condition();
        subConditionD.setConditionTypeId("profilePropertyCondition");
        booleanConditionC.setParameter("subConditions", Arrays.asList(subConditionD));
        
        ConditionType booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
        
        ConditionType eventPropertyConditionType = new ConditionType();
        eventPropertyConditionType.setItemId("eventPropertyCondition");
        eventPropertyConditionType.setParentCondition(booleanConditionC);
        
        ConditionType profilePropertyConditionType = new ConditionType();
        profilePropertyConditionType.setItemId("profilePropertyCondition");
        
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanConditionType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        when(definitionsService.getConditionType("profilePropertyCondition")).thenReturn(profilePropertyConditionType);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, booleanConditionA, "testContext");
        assertTrue("BooleanCondition with parent condition using booleanCondition should resolve successfully", result);
        assertEquals("Root booleanCondition type should be set", booleanConditionType, booleanConditionA.getConditionType());
    }

    @Test
    public void testSelfReferencingCycle() {
        // Test self-referencing condition hits depth limit
        Condition a = new Condition();
        a.setConditionTypeId("booleanCondition");
        a.setParameter("subConditions", Arrays.asList(a));
        
        ConditionType booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanConditionType);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, a, "testContext");
        assertFalse("Self-referencing condition should hit depth limit", result);
    }

    @Test
    public void testMultipleBranchesWithSameConditionType() {
        // Test that multiple branches can use the same condition type without false positives
        Condition root = new Condition();
        root.setConditionTypeId("booleanCondition");
        
        Condition branch1 = new Condition();
        branch1.setConditionTypeId("booleanCondition");
        
        Condition branch2 = new Condition();
        branch2.setConditionTypeId("booleanCondition");
        
        root.setParameter("subConditions", Arrays.asList(branch1, branch2));
        
        ConditionType booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanConditionType);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, root, "testContext");
        assertTrue("Multiple branches using the same condition type should not be a false positive", result);
    }

    @Test
    public void testCycleInParentConditionChain() {
        // Test cycle in parent condition chain: A has parent B, B has parent C, C has parent B
        Condition root = new Condition();
        root.setConditionTypeId("conditionA");
        
        Condition parentB = new Condition();
        parentB.setConditionTypeId("conditionB");
        
        ConditionType typeA = new ConditionType();
        typeA.setItemId("conditionA");
        typeA.setParentCondition(parentB);
        
        ConditionType typeB = new ConditionType();
        typeB.setItemId("conditionB");
        
        Condition parentC = new Condition();
        parentC.setConditionTypeId("conditionC");
        
        ConditionType typeC = new ConditionType();
        typeC.setItemId("conditionC");
        
        typeB.setParentCondition(parentC);
        typeC.setParentCondition(parentB);
        
        when(definitionsService.getConditionType("conditionA")).thenReturn(typeA);
        when(definitionsService.getConditionType("conditionB")).thenReturn(typeB);
        when(definitionsService.getConditionType("conditionC")).thenReturn(typeC);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, root, "testContext");
        assertFalse("Cycle in parent condition chain (B->C->B) should be detected", result);
    }

    @Test
    public void testNestedBooleanConditionsWithoutCycle() {
        // Test deeply nested booleanConditions that don't form a cycle
        Condition root = new Condition();
        root.setConditionTypeId("booleanCondition");
        
        Condition b1 = new Condition();
        b1.setConditionTypeId("booleanCondition");
        
        Condition b2 = new Condition();
        b2.setConditionTypeId("booleanCondition");
        
        Condition b3 = new Condition();
        b3.setConditionTypeId("booleanCondition");
        
        root.setParameter("subConditions", Arrays.asList(b1));
        b1.setParameter("subConditions", Arrays.asList(b2));
        b2.setParameter("subConditions", Arrays.asList(b3));
        
        ConditionType booleanConditionType = new ConditionType();
        booleanConditionType.setItemId("booleanCondition");
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanConditionType);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, root, "testContext");
        assertTrue("Deeply nested booleanConditions without cycle should succeed", result);
    }

    @Test
    public void testUpDownBackUpCycle() {
        // Test up → down → back up cycle: Root A -> parameter B -> B's parent C -> C's parent B (cycle in parent chain)
        // This creates a cycle in the parent chain that should be detected
        Condition rootA = new Condition();
        rootA.setConditionTypeId("typeA");
        
        Condition paramB = new Condition();
        paramB.setConditionTypeId("typeB");
        rootA.setParameter("subConditions", Arrays.asList(paramB));
        
        Condition parentC = new Condition();
        parentC.setConditionTypeId("typeC");
        
        Condition parentB = new Condition();
        parentB.setConditionTypeId("typeB");
        
        ConditionType typeA = new ConditionType();
        typeA.setItemId("typeA");
        
        ConditionType typeB = new ConditionType();
        typeB.setItemId("typeB");
        typeB.setParentCondition(parentC);
        
        ConditionType typeC = new ConditionType();
        typeC.setItemId("typeC");
        typeC.setParentCondition(parentB);
        
        when(definitionsService.getConditionType("typeA")).thenReturn(typeA);
        when(definitionsService.getConditionType("typeB")).thenReturn(typeB);
        when(definitionsService.getConditionType("typeC")).thenReturn(typeC);
        
        boolean result = ParserHelper.resolveConditionType(definitionsService, rootA, "testContext");
        assertFalse("Up → down → back up cycle (B->C->B) should be detected", result);
    }

    @Test
    public void testResolveEffectiveConditionDeepCopyNestedConditions() {
        // Create a condition type with parent condition containing nested conditions
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        
        // Create parent condition with nested conditions
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("booleanCondition");
        parentCondition.setParameter("operator", "and");
        
        // Create nested conditions
        Condition nested1 = new Condition();
        nested1.setConditionTypeId("eventTypeCondition");
        nested1.setParameter("eventTypeId", "view");
        
        Condition nested2 = new Condition();
        nested2.setConditionTypeId("eventPropertyCondition");
        nested2.setParameter("propertyName", "testProperty");
        nested2.setParameter("propertyValue", "testValue");
        
        parentCondition.setParameter("subConditions", Arrays.asList(nested1, nested2));
        childType.setParentCondition(parentCondition);
        
        // Create condition types
        ConditionType booleanType = new ConditionType();
        booleanType.setItemId("booleanCondition");
        
        ConditionType eventTypeConditionType = new ConditionType();
        eventTypeConditionType.setItemId("eventTypeCondition");
        
        ConditionType eventPropertyConditionType = new ConditionType();
        eventPropertyConditionType.setItemId("eventPropertyCondition");
        
        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanType);
        when(definitionsService.getConditionType("eventTypeCondition")).thenReturn(eventTypeConditionType);
        when(definitionsService.getConditionType("eventPropertyCondition")).thenReturn(eventPropertyConditionType);
        
        // Create condition to resolve
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");
        condition.setParameter("customParam", "customValue");
        
        // Resolve effective condition
        Map<String, Object> context = new HashMap<>();
        Condition effectiveCondition = ParserHelper.resolveEffectiveCondition(
            condition, definitionsService, context, "test context");
        
        // Verify effective condition is the parent (booleanCondition)
        assertNotNull("Effective condition should not be null", effectiveCondition);
        assertEquals("Effective condition should be booleanCondition", 
            "booleanCondition", effectiveCondition.getConditionTypeId());
        
        // Verify nested conditions are deep copied (not shared references)
        @SuppressWarnings("unchecked")
        List<Condition> effectiveSubConditions = (List<Condition>) effectiveCondition.getParameter("subConditions");
        assertNotNull("SubConditions should be present", effectiveSubConditions);
        assertEquals("Should have two subConditions", 2, effectiveSubConditions.size());
        
        // Verify nested conditions are independent (modifying copy doesn't affect original)
        Condition originalNested1 = nested1;
        Condition copiedNested1 = effectiveSubConditions.get(0);
        
        // Modify the copied nested condition
        copiedNested1.setParameter("eventTypeId", "modified");
        
        // Verify original is not affected
        assertEquals("Original nested condition should not be modified", 
            "view", originalNested1.getParameter("eventTypeId"));
        
        // Verify copied nested condition is modified
        assertEquals("Copied nested condition should be modified", 
            "modified", copiedNested1.getParameter("eventTypeId"));
        
        // Verify nested conditions have their types resolved
        assertNotNull("First nested condition type should be resolved", 
            effectiveSubConditions.get(0).getConditionType());
        assertNotNull("Second nested condition type should be resolved", 
            effectiveSubConditions.get(1).getConditionType());
        
        // Verify nested condition parameters are preserved
        assertEquals("First nested condition should have eventTypeId parameter", 
            "modified", effectiveSubConditions.get(0).getParameter("eventTypeId"));
        assertEquals("Second nested condition should have propertyName parameter", 
            "testProperty", effectiveSubConditions.get(1).getParameter("propertyName"));
        assertEquals("Second nested condition should have propertyValue parameter", 
            "testValue", effectiveSubConditions.get(1).getParameter("propertyValue"));
    }

    @Test
    public void testResolveEffectiveConditionDeepCopySingleNestedCondition() {
        // Test deep copy with a single nested condition (not in a collection)
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        
        // Create parent condition with single nested condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");
        
        Condition nested = new Condition();
        nested.setConditionTypeId("nestedConditionType");
        nested.setParameter("nestedParam", "nestedValue");
        
        parentCondition.setParameter("subCondition", nested);
        childType.setParentCondition(parentCondition);
        
        // Create condition types
        ConditionType parentType = new ConditionType();
        parentType.setItemId("parentConditionType");
        
        ConditionType nestedType = new ConditionType();
        nestedType.setItemId("nestedConditionType");
        
        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);
        when(definitionsService.getConditionType("nestedConditionType")).thenReturn(nestedType);
        
        // Create condition to resolve
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");
        
        // Resolve effective condition
        Map<String, Object> context = new HashMap<>();
        Condition effectiveCondition = ParserHelper.resolveEffectiveCondition(
            condition, definitionsService, context, "test context");
        
        // Verify nested condition is deep copied
        Condition effectiveNested = (Condition) effectiveCondition.getParameter("subCondition");
        assertNotNull("Nested condition should be present", effectiveNested);
        
        // Verify it's a deep copy (not the same reference)
        assertNotSame("Nested condition should be a copy, not the same reference", 
            nested, effectiveNested);
        
        // Modify the copied nested condition
        effectiveNested.setParameter("nestedParam", "modifiedValue");
        
        // Verify original is not affected
        assertEquals("Original nested condition should not be modified", 
            "nestedValue", nested.getParameter("nestedParam"));
        
        // Verify copied nested condition is modified
        assertEquals("Copied nested condition should be modified", 
            "modifiedValue", effectiveNested.getParameter("nestedParam"));
    }

    @Test
    public void testResolveEffectiveConditionDeepCopyRecursiveNesting() {
        // Test deep copy with recursively nested conditions (nested condition contains another nested condition)
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        
        // Create parent condition with nested condition that itself has a nested condition
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("booleanCondition");
        parentCondition.setParameter("operator", "and");
        
        Condition nested1 = new Condition();
        nested1.setConditionTypeId("booleanCondition");
        nested1.setParameter("operator", "or");
        
        Condition nested2 = new Condition();
        nested2.setConditionTypeId("eventTypeCondition");
        nested2.setParameter("eventTypeId", "view");
        
        nested1.setParameter("subConditions", Arrays.asList(nested2));
        parentCondition.setParameter("subConditions", Arrays.asList(nested1));
        childType.setParentCondition(parentCondition);
        
        // Create condition types
        ConditionType booleanType = new ConditionType();
        booleanType.setItemId("booleanCondition");
        
        ConditionType eventTypeConditionType = new ConditionType();
        eventTypeConditionType.setItemId("eventTypeCondition");
        
        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("booleanCondition")).thenReturn(booleanType);
        when(definitionsService.getConditionType("eventTypeCondition")).thenReturn(eventTypeConditionType);
        
        // Create condition to resolve
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");
        
        // Resolve effective condition
        Map<String, Object> context = new HashMap<>();
        Condition effectiveCondition = ParserHelper.resolveEffectiveCondition(
            condition, definitionsService, context, "test context");
        
        // Verify all levels are deep copied
        @SuppressWarnings("unchecked")
        List<Condition> level1SubConditions = (List<Condition>) effectiveCondition.getParameter("subConditions");
        assertNotNull("Level 1 subConditions should be present", level1SubConditions);
        assertEquals("Should have one level 1 subCondition", 1, level1SubConditions.size());
        
        Condition level1Nested = level1SubConditions.get(0);
        assertNotSame("Level 1 nested condition should be a copy", nested1, level1Nested);
        
        @SuppressWarnings("unchecked")
        List<Condition> level2SubConditions = (List<Condition>) level1Nested.getParameter("subConditions");
        assertNotNull("Level 2 subConditions should be present", level2SubConditions);
        assertEquals("Should have one level 2 subCondition", 1, level2SubConditions.size());
        
        Condition level2Nested = level2SubConditions.get(0);
        assertNotSame("Level 2 nested condition should be a copy", nested2, level2Nested);
        
        // Verify modifying deeply nested condition doesn't affect original
        level2Nested.setParameter("eventTypeId", "modified");
        assertEquals("Original level 2 nested condition should not be modified", 
            "view", nested2.getParameter("eventTypeId"));
        assertEquals("Copied level 2 nested condition should be modified", 
            "modified", level2Nested.getParameter("eventTypeId"));
    }

    @Test
    public void testResolveEffectiveConditionPreservesParameters() {
        // Test that all parameters are preserved in the deep copy
        ConditionType childType = new ConditionType();
        childType.setItemId("childConditionType");
        
        // Create parent condition with various parameter types
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");
        parentCondition.setParameter("stringParam", "stringValue");
        parentCondition.setParameter("intParam", 42);
        parentCondition.setParameter("boolParam", true);
        parentCondition.setParameter("listParam", Arrays.asList("item1", "item2"));
        
        // Create nested condition
        Condition nested = new Condition();
        nested.setConditionTypeId("nestedConditionType");
        nested.setParameter("nestedString", "nestedValue");
        parentCondition.setParameter("nestedCondition", nested);
        
        childType.setParentCondition(parentCondition);
        
        // Create condition types
        ConditionType parentType = new ConditionType();
        parentType.setItemId("parentConditionType");
        
        ConditionType nestedType = new ConditionType();
        nestedType.setItemId("nestedConditionType");
        
        // Mock definitions service
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);
        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);
        when(definitionsService.getConditionType("nestedConditionType")).thenReturn(nestedType);
        
        // Create condition to resolve with additional parameters
        Condition condition = new Condition();
        condition.setConditionTypeId("childConditionType");
        condition.setParameter("customParam", "customValue");
        
        // Resolve effective condition
        Map<String, Object> context = new HashMap<>();
        Condition effectiveCondition = ParserHelper.resolveEffectiveCondition(
            condition, definitionsService, context, "test context");
        
        // Verify all parameter types are preserved
        assertEquals("String parameter should be preserved", 
            "stringValue", effectiveCondition.getParameter("stringParam"));
        assertEquals("Integer parameter should be preserved", 
            42, effectiveCondition.getParameter("intParam"));
        assertEquals("Boolean parameter should be preserved", 
            true, effectiveCondition.getParameter("boolParam"));
        assertEquals("List parameter should be preserved", 
            Arrays.asList("item1", "item2"), effectiveCondition.getParameter("listParam"));
        
        // Verify nested condition parameter is preserved
        Condition effectiveNested = (Condition) effectiveCondition.getParameter("nestedCondition");
        assertNotNull("Nested condition should be present", effectiveNested);
        assertEquals("Nested condition parameter should be preserved", 
            "nestedValue", effectiveNested.getParameter("nestedString"));
        
        // Verify condition's custom parameter is merged (highest priority)
        assertEquals("Condition parameter should override parent parameter if same key", 
            "customValue", effectiveCondition.getParameter("customParam"));
    }

    @Test
    public void testResolveEffectiveConditionWithNullCondition() {
        // Test that null condition is handled gracefully
        Map<String, Object> context = new HashMap<>();
        Condition result = ParserHelper.resolveEffectiveCondition(
            null, definitionsService, context, "test context");
        
        assertNull("Null condition should return null", result);
    }

    @Test
    public void testResolveEffectiveConditionWithNoParent() {
        // Test condition without parent condition
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testConditionType");
        
        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        condition.setParameter("param1", "value1");
        
        when(definitionsService.getConditionType("testConditionType")).thenReturn(conditionType);
        
        Map<String, Object> context = new HashMap<>();
        Condition result = ParserHelper.resolveEffectiveCondition(
            condition, definitionsService, context, "test context");
        
        // Should return the original condition (no parent to resolve)
        assertNotNull("Result should not be null", result);
        assertEquals("Should return original condition when no parent", 
            condition, result);
        assertEquals("Parameter should be preserved", 
            "value1", result.getParameter("param1"));
    }

    @Test
    public void testConditionDeepCopy() {
        // Test direct deep copy method on Condition
        Condition original = new Condition();
        original.setConditionTypeId("testConditionType");
        original.setParameter("stringParam", "stringValue");
        original.setParameter("intParam", 42);
        original.setParameter("boolParam", true);
        
        // Create nested condition
        Condition nested = new Condition();
        nested.setConditionTypeId("nestedConditionType");
        nested.setParameter("nestedParam", "nestedValue");
        original.setParameter("nestedCondition", nested);
        
        // Create nested condition in collection
        Condition nestedInList = new Condition();
        nestedInList.setConditionTypeId("nestedInListType");
        nestedInList.setParameter("listParam", "listValue");
        original.setParameter("nestedList", Arrays.asList(nestedInList));
        
        // Perform deep copy
        Condition copied = original.deepCopy();
        
        // Verify it's a copy, not the same reference
        assertNotSame("Copied condition should be a different object", original, copied);
        
        // Verify basic properties are copied
        assertEquals("Condition type ID should be copied", 
            "testConditionType", copied.getConditionTypeId());
        assertEquals("String parameter should be copied", 
            "stringValue", copied.getParameter("stringParam"));
        assertEquals("Integer parameter should be copied", 
            42, copied.getParameter("intParam"));
        assertEquals("Boolean parameter should be copied", 
            true, copied.getParameter("boolParam"));
        
        // Verify nested condition is deep copied
        Condition copiedNested = (Condition) copied.getParameter("nestedCondition");
        assertNotNull("Nested condition should be present", copiedNested);
        assertNotSame("Nested condition should be a different object", nested, copiedNested);
        assertEquals("Nested condition type ID should be copied", 
            "nestedConditionType", copiedNested.getConditionTypeId());
        assertEquals("Nested condition parameter should be copied", 
            "nestedValue", copiedNested.getParameter("nestedParam"));
        
        // Verify nested condition in list is deep copied
        @SuppressWarnings("unchecked")
        List<Condition> copiedList = (List<Condition>) copied.getParameter("nestedList");
        assertNotNull("Nested list should be present", copiedList);
        assertEquals("Nested list should have one item", 1, copiedList.size());
        assertNotSame("Nested condition in list should be a different object", 
            nestedInList, copiedList.get(0));
        assertEquals("Nested condition in list type ID should be copied", 
            "nestedInListType", copiedList.get(0).getConditionTypeId());
        assertEquals("Nested condition in list parameter should be copied", 
            "listValue", copiedList.get(0).getParameter("listParam"));
        
        // Verify modifying copied condition doesn't affect original
        copied.setParameter("stringParam", "modified");
        assertEquals("Original parameter should not be modified", 
            "stringValue", original.getParameter("stringParam"));
        assertEquals("Copied parameter should be modified", 
            "modified", copied.getParameter("stringParam"));
        
        // Verify modifying nested condition doesn't affect original
        copiedNested.setParameter("nestedParam", "modifiedNested");
        assertEquals("Original nested parameter should not be modified", 
            "nestedValue", nested.getParameter("nestedParam"));
        assertEquals("Copied nested parameter should be modified", 
            "modifiedNested", copiedNested.getParameter("nestedParam"));
        
        // Verify modifying nested condition in list doesn't affect original
        copiedList.get(0).setParameter("listParam", "modifiedList");
        assertEquals("Original nested list parameter should not be modified", 
            "listValue", nestedInList.getParameter("listParam"));
        assertEquals("Copied nested list parameter should be modified", 
            "modifiedList", copiedList.get(0).getParameter("listParam"));
    }

    @Test
    public void testConditionDeepCopyWithNullValues() {
        // Test deep copy handles null values gracefully
        Condition original = new Condition();
        original.setConditionTypeId("testConditionType");
        original.setParameter("nullParam", null);
        original.setParameter("stringParam", "value");
        
        Condition copied = original.deepCopy();
        
        assertNotNull("Copied condition should not be null", copied);
        assertNull("Null parameter should remain null", copied.getParameter("nullParam"));
        assertEquals("String parameter should be copied", 
            "value", copied.getParameter("stringParam"));
    }

    @Test
    public void testConditionDeepCopyWithEmptyParameters() {
        // Test deep copy with empty parameter map
        Condition original = new Condition();
        original.setConditionTypeId("testConditionType");
        
        Condition copied = original.deepCopy();
        
        assertNotNull("Copied condition should not be null", copied);
        assertEquals("Condition type ID should be copied", 
            "testConditionType", copied.getConditionTypeId());
        assertNotNull("Parameter values map should exist", copied.getParameterValues());
        assertTrue("Parameter values map should be empty", copied.getParameterValues().isEmpty());
    }
}
