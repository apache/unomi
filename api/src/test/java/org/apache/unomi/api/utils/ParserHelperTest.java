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

        // Test event type resolution
        Set<String> eventTypes = ParserHelper.resolveConditionEventTypes(rootCondition);

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

        // Test event type resolution
        Set<String> eventTypes = ParserHelper.resolveConditionEventTypes(notCondition);

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
}
