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
package org.apache.unomi.services.impl;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.actions.Action;
import org.apache.unomi.api.actions.ActionType;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.rules.Rule;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.InvalidObjectInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TypeResolutionServiceImplTest {

    private TypeResolutionServiceImpl typeResolutionService;

    @Mock
    private DefinitionsService definitionsService;

    private ConditionType testConditionType;
    private ActionType testActionType;

    @BeforeEach
    public void setUp() {
        typeResolutionService = new TypeResolutionServiceImpl(definitionsService);

        // Create test condition type
        testConditionType = new ConditionType(new Metadata());
        testConditionType.setItemId("testConditionType");
        testConditionType.getMetadata().setName("Test Condition Type");

        // Create test action type
        testActionType = new ActionType(new Metadata());
        testActionType.setItemId("testActionType");
        testActionType.getMetadata().setName("Test Action Type");
        testActionType.setActionExecutor("testActionExecutor");
    }

    @Test
    public void testResolveConditionType_Success() {
        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);

        boolean resolved = typeResolutionService.resolveConditionType(condition, "test context");

        assertTrue(resolved, "Condition type should resolve successfully when definition exists");
        assertNotNull(condition.getConditionType(), "Condition should have its type set after resolution");
        assertEquals(testConditionType, condition.getConditionType(), "Condition type should match the definition");
    }

    @Test
    public void testResolveConditionType_NotFound() {
        Condition condition = new Condition();
        condition.setConditionTypeId("nonExistentType");

        when(definitionsService.getConditionType("nonExistentType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveConditionType(condition, "test context");

        assertFalse(resolved, "Condition type should not resolve when definition doesn't exist");
        assertNull(condition.getConditionType(), "Condition should not have its type set when resolution fails");
    }

    @Test
    public void testResolveConditionType_NullCondition() {
        boolean resolved = typeResolutionService.resolveConditionType(null, "test context");

        assertFalse(resolved, "Null condition should return false");
    }

    @Test
    public void testResolveConditionType_WithNestedConditions() {
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");

        Condition childCondition = new Condition();
        childCondition.setConditionTypeId("childConditionType");

        ConditionType parentType = new ConditionType(new Metadata());
        parentType.setItemId("parentConditionType");
        parentCondition.setParameter("subCondition", childCondition);

        ConditionType childType = new ConditionType(new Metadata());
        childType.setItemId("childConditionType");

        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);
        when(definitionsService.getConditionType("childConditionType")).thenReturn(childType);

        boolean resolved = typeResolutionService.resolveConditionType(parentCondition, "test context");

        assertTrue(resolved, "Parent condition with nested child should resolve successfully");
        assertNotNull(parentCondition.getConditionType(), "Parent condition should have its type set");
        assertNotNull(childCondition.getConditionType(), "Child condition should have its type set");
    }

    @Test
    public void testResolveActionType_Success() {
        Action action = new Action();
        action.setActionTypeId("testActionType");

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveActionType(action);

        assertTrue(resolved, "Action type should resolve successfully when definition exists");
        assertNotNull(action.getActionType(), "Action should have its type set after resolution");
        assertEquals(testActionType, action.getActionType(), "Action type should match the definition");
    }

    @Test
    public void testResolveActionType_NotFound() {
        Action action = new Action();
        action.setActionTypeId("nonExistentActionType");

        when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveActionType(action);

        assertFalse(resolved, "Action type should not resolve when definition doesn't exist");
        assertNull(action.getActionType(), "Action should not have its type set when resolution fails");
    }

    @Test
    public void testResolveActionTypes_WithMultipleActions() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Action action1 = new Action();
        action1.setActionTypeId("testActionType");
        Action action2 = new Action();
        action2.setActionTypeId("testActionType");

        rule.setActions(Arrays.asList(action1, action2));

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveActionTypes(rule, false);

        assertTrue(resolved, "All actions should resolve successfully");
        assertNotNull(action1.getActionType(), "First action should have its type set");
        assertNotNull(action2.getActionType(), "Second action should have its type set");
    }

    @Test
    public void testResolveActionTypes_WithUnresolvedAction() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Action action1 = new Action();
        action1.setActionTypeId("testActionType");
        Action action2 = new Action();
        action2.setActionTypeId("nonExistentActionType");

        rule.setActions(Arrays.asList(action1, action2));

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);
        when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveActionTypes(rule, false);

        assertFalse(resolved, "Should return false when any action fails to resolve");
        assertNotNull(action1.getActionType(), "First action should have its type set");
        assertNull(action2.getActionType(), "Second action should not have its type set");
    }

    @Test
    public void testResolveCondition_WithMetadataItem_Success() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        segment.setCondition(condition);

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);

        boolean resolved = typeResolutionService.resolveCondition("segments", segment, condition, "test context");

        assertTrue(resolved, "Condition should resolve successfully");
        assertFalse(segment.getMetadata().isMissingPlugins(), "missingPlugins should be false when resolution succeeds");
        assertFalse(typeResolutionService.isInvalid("segments", "testSegment"), "Segment should not be marked as invalid");
    }

    @Test
    public void testResolveCondition_WithMetadataItem_Failure() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition = new Condition();
        condition.setConditionTypeId("nonExistentType");
        segment.setCondition(condition);

        when(definitionsService.getConditionType("nonExistentType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveCondition("segments", segment, condition, "test context");

        assertFalse(resolved, "Condition should not resolve when type doesn't exist");
        assertTrue(segment.getMetadata().isMissingPlugins(), "missingPlugins should be true when resolution fails");
        assertTrue(typeResolutionService.isInvalid("segments", "testSegment"), "Segment should be marked as invalid");
        assertNotNull(typeResolutionService.getInvalidationReason("segments", "testSegment"), "Should have invalidation reason");
    }

    @Test
    public void testResolveCondition_WithMetadataItem_NullCondition() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));
        segment.getMetadata().setMissingPlugins(true); // Set to true initially

        boolean resolved = typeResolutionService.resolveCondition("segments", segment, null, "test context");

        assertTrue(resolved, "Null condition should be considered valid");
        assertFalse(segment.getMetadata().isMissingPlugins(), "missingPlugins should be cleared for null condition");
    }

    @Test
    public void testResolveRule_Success() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("testActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveRule("rules", rule);

        assertTrue(resolved, "Rule should resolve successfully when both condition and actions resolve");
        assertFalse(rule.getMetadata().isMissingPlugins(), "missingPlugins should be false when all types resolve");
        assertFalse(typeResolutionService.isInvalid("rules", "testRule"), "Rule should not be marked as invalid");
    }

    @Test
    public void testResolveRule_WithUnresolvedCondition() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Condition condition = new Condition();
        condition.setConditionTypeId("nonExistentConditionType");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("testActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("nonExistentConditionType")).thenReturn(null);
        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveRule("rules", rule);

        assertFalse(resolved, "Rule should not resolve when condition fails");
        assertTrue(rule.getMetadata().isMissingPlugins(), "missingPlugins should be true when condition fails");
        assertTrue(typeResolutionService.isInvalid("rules", "testRule"), "Rule should be marked as invalid");
    }

    @Test
    public void testResolveRule_WithUnresolvedActions() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("nonExistentActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveRule("rules", rule);

        assertFalse(resolved, "Rule should not resolve when actions fail");
        assertTrue(rule.getMetadata().isMissingPlugins(), "missingPlugins should be true when actions fail");
        assertTrue(typeResolutionService.isInvalid("rules", "testRule"), "Rule should be marked as invalid");
    }

    @Test
    public void testResolveRule_WithNullCondition() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));
        rule.setCondition(null);

        Action action = new Action();
        action.setActionTypeId("testActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveRule("rules", rule);

        assertTrue(resolved, "Rule should resolve when condition is null but actions resolve");
        assertFalse(rule.getMetadata().isMissingPlugins(), "missingPlugins should be false");
    }

    @Test
    public void testResolveActions_WithRule() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Action action = new Action();
        action.setActionTypeId("testActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        boolean resolved = typeResolutionService.resolveActions("rules", rule);

        assertTrue(resolved, "Actions should resolve successfully");
        assertNotNull(action.getActionType(), "Action should have its type set");
        // Note: missingPlugins is only cleared in resolveRule, not in resolveActions alone
    }

    @Test
    public void testMarkInvalid_AndMarkValid() {
        typeResolutionService.markInvalid("rules", "rule1", "Test reason");

        assertTrue(typeResolutionService.isInvalid("rules", "rule1"), "Rule should be marked as invalid");
        assertEquals("Test reason", typeResolutionService.getInvalidationReason("rules", "rule1"), "Should return the invalidation reason");

        typeResolutionService.markValid("rules", "rule1");

        assertFalse(typeResolutionService.isInvalid("rules", "rule1"), "Rule should be marked as valid after markValid");
        assertNull(typeResolutionService.getInvalidationReason("rules", "rule1"), "Should return null for valid objects");
    }

    @Test
    public void testGetAllInvalidObjects() {
        typeResolutionService.markInvalid("rules", "rule1", "Reason 1");
        typeResolutionService.markInvalid("rules", "rule2", "Reason 2");
        typeResolutionService.markInvalid("segments", "segment1", "Reason 3");

        Map<String, Map<String, InvalidObjectInfo>> allInvalid = typeResolutionService.getAllInvalidObjects();

        assertEquals(2, allInvalid.size(), "Should have two object types");
        assertTrue(allInvalid.containsKey("rules"), "Should contain rules");
        assertTrue(allInvalid.containsKey("segments"), "Should contain segments");
        assertEquals(2, allInvalid.get("rules").size(), "Should have 2 invalid rules");
        assertEquals(1, allInvalid.get("segments").size(), "Should have 1 invalid segment");
    }

    @Test
    public void testGetInvalidObjects_ByType() {
        typeResolutionService.markInvalid("rules", "rule1", "Reason 1");
        typeResolutionService.markInvalid("rules", "rule2", "Reason 2");
        typeResolutionService.markInvalid("segments", "segment1", "Reason 3");

        Map<String, InvalidObjectInfo> invalidRules = typeResolutionService.getInvalidObjects("rules");

        assertEquals(2, invalidRules.size(), "Should have 2 invalid rules");
        assertTrue(invalidRules.containsKey("rule1"), "Should contain rule1");
        assertTrue(invalidRules.containsKey("rule2"), "Should contain rule2");
    }

    @Test
    public void testGetTotalInvalidObjectCount() {
        typeResolutionService.markInvalid("rules", "rule1", "Reason 1");
        typeResolutionService.markInvalid("rules", "rule2", "Reason 2");
        typeResolutionService.markInvalid("segments", "segment1", "Reason 3");

        int total = typeResolutionService.getTotalInvalidObjectCount();

        assertEquals(3, total, "Should have 3 total invalid objects");
    }

    @Test
    public void testGetInvalidObjectIds() {
        typeResolutionService.markInvalid("rules", "rule1", "Reason 1");
        typeResolutionService.markInvalid("rules", "rule2", "Reason 2");

        Set<String> invalidIds = typeResolutionService.getInvalidObjectIds("rules");

        assertEquals(2, invalidIds.size(), "Should have 2 invalid rule IDs");
        assertTrue(invalidIds.contains("rule1"), "Should contain rule1");
        assertTrue(invalidIds.contains("rule2"), "Should contain rule2");
    }

    @Test
    public void testResolveCondition_ClearsMissingPlugins_WhenResolved() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));
        segment.getMetadata().setMissingPlugins(true); // Initially set to true

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        segment.setCondition(condition);

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);

        typeResolutionService.resolveCondition("segments", segment, condition, "test context");

        assertFalse(segment.getMetadata().isMissingPlugins(), "missingPlugins should be cleared when condition resolves successfully");
    }

    @Test
    public void testResolveRule_ClearsMissingPlugins_WhenBothResolve() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));
        rule.getMetadata().setMissingPlugins(true); // Initially set to true

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("testActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

        typeResolutionService.resolveRule("rules", rule);

        assertFalse(rule.getMetadata().isMissingPlugins(), "missingPlugins should be cleared when both condition and actions resolve");
    }

    @Test
    public void testResolveConditionType_WithParentCondition() {
        ConditionType parentType = new ConditionType(new Metadata());
        parentType.setItemId("parentConditionType");

        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("parentConditionType");

        testConditionType.setParentCondition(parentCondition);

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        when(definitionsService.getConditionType("parentConditionType")).thenReturn(parentType);

        boolean resolved = typeResolutionService.resolveConditionType(condition, "test context");

        assertTrue(resolved, "Condition with parent should resolve successfully");
        assertNotNull(condition.getConditionType(), "Condition should have its type set");
        assertNotNull(testConditionType.getParentCondition().getConditionType(), "Parent condition should have its type set");
    }

    @Test
    public void testResolveConditionType_WithUnresolvedParent() {
        Condition parentCondition = new Condition();
        parentCondition.setConditionTypeId("nonExistentParentType");

        testConditionType.setParentCondition(parentCondition);

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");

        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        when(definitionsService.getConditionType("nonExistentParentType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveConditionType(condition, "test context");

        assertFalse(resolved, "Condition should not resolve when parent condition fails");
        assertNull(condition.getConditionType(), "Condition should not have its type set when parent fails");
    }

    @Test
    public void testResolveValueType_Null() {
        assertDoesNotThrow(() -> typeResolutionService.resolveValueType(null), "resolveValueType should handle null gracefully");
    }

    @Test
    public void testResolveValueType_SetsValueTypeOnPropertyType() {
        org.apache.unomi.api.PropertyType propertyType = new org.apache.unomi.api.PropertyType();
        propertyType.setMetadata(new Metadata("testProp"));
        propertyType.setValueTypeId("string");

        org.apache.unomi.api.ValueType valueType = new org.apache.unomi.api.ValueType("string");
        when(definitionsService.getValueType("string")).thenReturn(valueType);

        typeResolutionService.resolveValueType(propertyType);

        assertNotNull(propertyType.getValueType(), "resolveValueType should set the valueType on the PropertyType");
        assertEquals("string", propertyType.getValueType().getId(), "ValueType should match the looked-up type");
    }

    @Test
    public void testResolveValueType_MissingType_LeavesNull() {
        org.apache.unomi.api.PropertyType propertyType = new org.apache.unomi.api.PropertyType();
        propertyType.setMetadata(new Metadata("testProp"));
        propertyType.setValueTypeId("nonExistent");

        when(definitionsService.getValueType("nonExistent")).thenReturn(null);

        typeResolutionService.resolveValueType(propertyType);

        assertNull(propertyType.getValueType(), "resolveValueType should leave valueType null when type is not found");
    }

    @Test
    public void testResolveActionTypes_IgnoreErrors_StillReturnsFalseOnPartialFailure() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Action action1 = new Action();
        action1.setActionTypeId("testActionType");
        Action action2 = new Action();
        action2.setActionTypeId("nonExistentActionType");
        rule.setActions(Arrays.asList(action1, action2));

        when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);
        when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

        // ignoreErrors only suppresses the warning log; return value still reflects resolution outcome
        boolean resolved = typeResolutionService.resolveActionTypes(rule, true);

        assertFalse(resolved, "resolveActionTypes should return false when any action fails, regardless of ignoreErrors");
        assertNotNull(action1.getActionType(), "Successfully resolved action should have its type set");
    }

    @Test
    public void testResolveActionTypes_NullActions_IgnoreErrors_SuppressesWarning() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));
        rule.setActions(null);

        // ignoreErrors=true suppresses the null-actions warning; method still returns false
        boolean resolved = typeResolutionService.resolveActionTypes(rule, true);

        assertFalse(resolved, "resolveActionTypes should return false when actions are null, even with ignoreErrors=true");
    }

    @Test
    public void testIsInvalid_WithNullParameters() {
        assertFalse(typeResolutionService.isInvalid(null, "rule1"), "Should return false for null objectType");
        assertFalse(typeResolutionService.isInvalid("rules", null), "Should return false for null objectId");
        assertFalse(typeResolutionService.isInvalid(null, null), "Should return false for both null");
    }

    @Test
    public void testMarkInvalid_WithNullParameters() {
        assertDoesNotThrow(() -> typeResolutionService.markInvalid(null, "rule1", "reason"), "Should handle null objectType");
        assertDoesNotThrow(() -> typeResolutionService.markInvalid("rules", null, "reason"), "Should handle null objectId");
        assertDoesNotThrow(() -> typeResolutionService.markInvalid("rules", "rule1", null), "Should handle null reason");
    }

    @Test
    public void testResolveCondition_UpdatesInvalidTracking() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition = new Condition();
        condition.setConditionTypeId("testConditionType");
        segment.setCondition(condition);

        // First, mark as invalid
        typeResolutionService.markInvalid("segments", "testSegment", "Previous reason");
        assertTrue(typeResolutionService.isInvalid("segments", "testSegment"), "Should be invalid initially");

        // Then resolve successfully
        when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
        typeResolutionService.resolveCondition("segments", segment, condition, "test context");

        assertFalse(typeResolutionService.isInvalid("segments", "testSegment"), "Should be marked as valid after successful resolution");
    }

    // --- Regression tests for review findings ---

    @Test
    public void setDefinitionsService_null_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> typeResolutionService.setDefinitionsService(null),
            "setDefinitionsService(null) must throw IllegalArgumentException");
    }

    @Test
    public void resolveValueType_unknownTypeId_doesNotThrow() {
        org.apache.unomi.api.PropertyType propertyType = new org.apache.unomi.api.PropertyType();
        propertyType.setMetadata(new Metadata("testProp"));
        propertyType.setValueTypeId("nonExistentType");
        when(definitionsService.getValueType("nonExistentType")).thenReturn(null);

        assertDoesNotThrow(() -> typeResolutionService.resolveValueType(propertyType),
            "resolveValueType must not throw when type lookup returns null");
        assertNull(propertyType.getValueType(), "ValueType must remain null when lookup fails");
    }

    @Test
    public void updateEncounter_concurrentAccess_isThreadSafe() throws InterruptedException {
        InvalidObjectInfo info = new InvalidObjectInfo("rules", "rule1", "test");
        int threads = 10;
        int iterationsPerThread = 100;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threads);
        java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    info.updateEncounter(null, null, "ctx-" + j);
                }
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        assertEquals(1 + threads * iterationsPerThread, info.getEncounterCount(),
            "All concurrent increments must be counted without loss");
    }

    @Test
    public void updateEncounter_readDuringConcurrentWrite_neverObservesCorruptCount() throws InterruptedException {
        InvalidObjectInfo info = new InvalidObjectInfo("rules", "rule1", "test");
        int writerThreads = 4;
        int readerThreads = 4;
        int iterations = 200;
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(writerThreads + readerThreads);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(writerThreads + readerThreads);
        java.util.concurrent.atomic.AtomicBoolean corrupt = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < writerThreads; i++) {
            executor.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < iterations; j++) {
                    info.updateEncounter(null, null, "ctx");
                }
                doneLatch.countDown();
            });
        }
        for (int i = 0; i < readerThreads; i++) {
            executor.submit(() -> {
                try { startLatch.await(); } catch (InterruptedException ignored) {}
                for (int j = 0; j < iterations; j++) {
                    if (info.getEncounterCount() < 1) {
                        corrupt.set(true);
                    }
                }
                doneLatch.countDown();
            });
        }
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();
        assertFalse(corrupt.get(), "Concurrent reads must never observe a corrupted (< 1) encounter count");
    }

    // Tests for enhanced InvalidObjectInfo functionality

    @Test
    public void testInvalidObjectInfo_WithDetailedInformation_FromResolveCondition() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition = new Condition();
        condition.setConditionTypeId("missingCondType");
        segment.setCondition(condition);

        when(definitionsService.getConditionType("missingCondType")).thenReturn(null);

        typeResolutionService.resolveCondition("segments", segment, condition, "segment testSegment");

        Map<String, InvalidObjectInfo> invalidSegments = typeResolutionService.getInvalidObjects("segments");
        InvalidObjectInfo info = invalidSegments.get("testSegment");

        assertNotNull(info, "InvalidObjectInfo should be created");
        assertEquals("segments", info.getObjectType(), "Object type should match");
        assertEquals("testSegment", info.getObjectId(), "Object ID should match");
        assertTrue(info.getReason().contains("Unresolved condition type"), "Reason should mention condition type");
        assertEquals(1, info.getMissingConditionTypeIds().size(), "Should have 1 missing condition type");
        assertEquals("missingCondType", info.getMissingConditionTypeIds().get(0), "Should contain missing condition type");
        assertTrue(info.getMissingActionTypeIds().isEmpty(), "Should have no missing action types");
        assertEquals(1, info.getContextNames().size(), "Should have 1 context");
        assertTrue(info.getContextNames().contains("segment testSegment"), "Should contain context name");
        assertEquals(1, info.getEncounterCount(), "Should have encounter count of 1");
        assertTrue(info.getFirstSeenTimestamp() > 0, "Should have first seen timestamp");
        assertEquals(info.getFirstSeenTimestamp(), info.getLastSeenTimestamp(), "First and last seen should be equal on first encounter");
    }

    @Test
    public void testInvalidObjectInfo_UpdateEncounter_AccumulatesInformation() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition1 = new Condition();
        condition1.setConditionTypeId("missingCond1");
        Condition condition2 = new Condition();
        condition2.setConditionTypeId("missingCond2");

        when(definitionsService.getConditionType("missingCond1")).thenReturn(null);
        when(definitionsService.getConditionType("missingCond2")).thenReturn(null);

        // First encounter
        typeResolutionService.resolveCondition("segments", segment, condition1, "context1");

        InvalidObjectInfo info1 = typeResolutionService.getInvalidObjects("segments").get("testSegment");
        long firstSeen = info1.getFirstSeenTimestamp();
        int firstEncounterCount = info1.getEncounterCount();

        assertEquals(1, firstEncounterCount, "First encounter should have count of 1");
        assertEquals(1, info1.getMissingConditionTypeIds().size(), "Should have 1 missing condition type initially");

        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Second encounter with different missing type
        typeResolutionService.resolveCondition("segments", segment, condition2, "context2");

        InvalidObjectInfo info2 = typeResolutionService.getInvalidObjects("segments").get("testSegment");

        assertEquals(firstSeen, info2.getFirstSeenTimestamp(), "First seen timestamp should not change");
        assertTrue(info2.getLastSeenTimestamp() >= firstSeen, "Last seen timestamp should be updated");
        assertEquals(2, info2.getEncounterCount(), "Encounter count should be incremented");
        assertEquals(2, info2.getMissingConditionTypeIds().size(), "Should accumulate all missing condition types");
        assertTrue(info2.getMissingConditionTypeIds().contains("missingCond1"), "Should contain initial condition type");
        assertTrue(info2.getMissingConditionTypeIds().contains("missingCond2"), "Should contain new condition type");
        assertEquals(2, info2.getContextNames().size(), "Should accumulate all contexts");
        assertTrue(info2.getContextNames().contains("context1"), "Should contain initial context");
        assertTrue(info2.getContextNames().contains("context2"), "Should contain additional context");
    }

    @Test
    public void testResolveCondition_CollectsMissingConditionTypeIds() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition = new Condition();
        condition.setConditionTypeId("nonExistentType");
        segment.setCondition(condition);

        when(definitionsService.getConditionType("nonExistentType")).thenReturn(null);

        typeResolutionService.resolveCondition("segments", segment, condition, "segment testSegment");

        Map<String, InvalidObjectInfo> invalidSegments = typeResolutionService.getInvalidObjects("segments");
        InvalidObjectInfo info = invalidSegments.get("testSegment");

        assertNotNull(info, "InvalidObjectInfo should be created");
        assertEquals(1, info.getMissingConditionTypeIds().size(), "Should have 1 missing condition type");
        assertEquals("nonExistentType", info.getMissingConditionTypeIds().get(0), "Should contain the missing condition type ID");
        assertEquals(1, info.getContextNames().size(), "Should have context name");
        assertTrue(info.getContextNames().contains("segment testSegment"), "Should contain the context name");
    }

    @Test
    public void testResolveActions_CollectsMissingActionTypeIds() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Action action1 = new Action();
        action1.setActionTypeId("nonExistentAction1");
        Action action2 = new Action();
        action2.setActionTypeId("nonExistentAction2");
        rule.setActions(Arrays.asList(action1, action2));

        when(definitionsService.getActionType("nonExistentAction1")).thenReturn(null);
        when(definitionsService.getActionType("nonExistentAction2")).thenReturn(null);

        typeResolutionService.resolveActions("rules", rule);

        Map<String, InvalidObjectInfo> invalidRules = typeResolutionService.getInvalidObjects("rules");
        InvalidObjectInfo info = invalidRules.get("testRule");

        assertNotNull(info, "InvalidObjectInfo should be created");
        assertEquals(2, info.getMissingActionTypeIds().size(), "Should have 2 missing action types");
        assertTrue(info.getMissingActionTypeIds().contains("nonExistentAction1"), "Should contain first missing action type");
        assertTrue(info.getMissingActionTypeIds().contains("nonExistentAction2"), "Should contain second missing action type");
        assertEquals(1, info.getContextNames().size(), "Should have context name");
        assertTrue(info.getContextNames().contains("rule testRule"), "Should contain the context name");
    }

    @Test
    public void testResolveRule_CollectsBothMissingConditionAndActionTypes() {
        Rule rule = new Rule();
        rule.setItemId("testRule");
        rule.setMetadata(new Metadata("testRule"));

        Condition condition = new Condition();
        condition.setConditionTypeId("nonExistentConditionType");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("nonExistentActionType");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("nonExistentConditionType")).thenReturn(null);
        when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

        typeResolutionService.resolveRule("rules", rule);

        Map<String, InvalidObjectInfo> invalidRules = typeResolutionService.getInvalidObjects("rules");
        InvalidObjectInfo info = invalidRules.get("testRule");

        assertNotNull(info, "InvalidObjectInfo should be created");
        assertEquals(1, info.getMissingConditionTypeIds().size(), "Should have 1 missing condition type");
        assertEquals("nonExistentConditionType", info.getMissingConditionTypeIds().get(0), "Should contain missing condition type");
        assertEquals(1, info.getMissingActionTypeIds().size(), "Should have 1 missing action type");
        assertEquals("nonExistentActionType", info.getMissingActionTypeIds().get(0), "Should contain missing action type");
        assertTrue(info.getReason().contains("Unresolved condition type"), "Reason should mention condition type");
        assertTrue(info.getReason().contains("Unresolved action type"), "Reason should mention action type");
    }

    @Test
    public void testInvalidObjectInfo_TimestampFields() {
        typeResolutionService.markInvalid("rules", "rule1", "Test reason");

        InvalidObjectInfo info1 = typeResolutionService.getInvalidObjects("rules").get("rule1");
        long firstSeen = info1.getFirstSeenTimestamp();
        long lastSeen = info1.getLastSeenTimestamp();

        assertTrue(firstSeen > 0, "First seen timestamp should be set");
        assertEquals(firstSeen, lastSeen, "First and last seen should be equal on first encounter");

        // Wait a bit
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Second encounter
        typeResolutionService.markInvalid("rules", "rule1", "Updated reason");

        InvalidObjectInfo info2 = typeResolutionService.getInvalidObjects("rules").get("rule1");

        assertEquals(firstSeen, info2.getFirstSeenTimestamp(), "First seen timestamp should not change");
        assertTrue(info2.getLastSeenTimestamp() >= firstSeen, "Last seen timestamp should be updated");
        assertTrue(info2.getLastSeenTimestamp() >= lastSeen, "Last seen should be greater than or equal to previous last seen");
    }

    @Test
    public void testInvalidObjectInfo_BackwardCompatibility() {
        // Test that the basic markInvalid(String, String, String) still works
        typeResolutionService.markInvalid("rules", "rule1", "Simple reason");

        InvalidObjectInfo info = typeResolutionService.getInvalidObjects("rules").get("rule1");

        assertNotNull(info, "InvalidObjectInfo should be created");
        assertEquals("rules", info.getObjectType(), "Object type should match");
        assertEquals("rule1", info.getObjectId(), "Object ID should match");
        assertEquals("Simple reason", info.getReason(), "Reason should match");
        assertTrue(info.getMissingConditionTypeIds().isEmpty(), "Missing condition types should be empty when not provided");
        assertTrue(info.getMissingActionTypeIds().isEmpty(), "Missing action types should be empty when not provided");
        assertTrue(info.getContextNames().isEmpty(), "Context names should be empty when not provided");
        assertEquals(1, info.getEncounterCount(), "Encounter count should be 1");
    }

    @Test
    public void testInvalidObjectInfo_ToString() {
        Rule rule = new Rule();
        rule.setItemId("rule1");
        rule.setMetadata(new Metadata("rule1"));

        Condition condition = new Condition();
        condition.setConditionTypeId("missingCond1");
        rule.setCondition(condition);

        Action action = new Action();
        action.setActionTypeId("missingAction1");
        rule.setActions(Collections.singletonList(action));

        when(definitionsService.getConditionType("missingCond1")).thenReturn(null);
        when(definitionsService.getActionType("missingAction1")).thenReturn(null);

        typeResolutionService.resolveRule("rules", rule);

        InvalidObjectInfo info = typeResolutionService.getInvalidObjects("rules").get("rule1");
        String toString = info.toString();

        assertTrue(toString.contains("rules"), "toString should contain object type");
        assertTrue(toString.contains("rule1"), "toString should contain object ID");
        assertTrue(toString.contains("Unresolved"), "toString should contain reason");
        assertTrue(toString.contains("missingConditionTypes"), "toString should contain missing condition types");
        assertTrue(toString.contains("missingActionTypes"), "toString should contain missing action types");
        assertTrue(toString.contains("contexts"), "toString should contain contexts");
    }

    @Test
    public void testMarkInvalid_OnlyLogsFirstEncounter() {
        // This test verifies that logging only happens on first encounter
        // We can't easily test logging without a logging framework, but we can verify
        // that the encounter count increases and information accumulates

        typeResolutionService.markInvalid("rules", "rule1", "First reason");

        InvalidObjectInfo info1 = typeResolutionService.getInvalidObjects("rules").get("rule1");
        assertEquals(1, info1.getEncounterCount(), "First encounter should have count of 1");

        typeResolutionService.markInvalid("rules", "rule1", "Second reason");

        InvalidObjectInfo info2 = typeResolutionService.getInvalidObjects("rules").get("rule1");
        assertEquals(2, info2.getEncounterCount(), "Second encounter should have count of 2");
        // The reason should still be the first one (not updated)
        assertEquals("First reason", info2.getReason(), "Reason should remain the first one");
        assertSame(info1, info2, "Second markInvalid call must update the existing entry, not replace it");
    }

    @Test
    public void testResolveCondition_WithMultipleEncounters() {
        Segment segment = new Segment();
        segment.setItemId("testSegment");
        segment.setMetadata(new Metadata("testSegment"));

        Condition condition1 = new Condition();
        condition1.setConditionTypeId("missingType1");
        Condition condition2 = new Condition();
        condition2.setConditionTypeId("missingType2");

        when(definitionsService.getConditionType("missingType1")).thenReturn(null);
        when(definitionsService.getConditionType("missingType2")).thenReturn(null);

        // First encounter with one missing type
        typeResolutionService.resolveCondition("segments", segment, condition1, "context1");

        InvalidObjectInfo info1 = typeResolutionService.getInvalidObjects("segments").get("testSegment");
        assertEquals(1, info1.getEncounterCount(), "Should have 1 encounter");
        assertEquals(1, info1.getMissingConditionTypeIds().size(), "Should have 1 missing condition type");

        // Second encounter with different missing type
        typeResolutionService.resolveCondition("segments", segment, condition2, "context2");

        InvalidObjectInfo info2 = typeResolutionService.getInvalidObjects("segments").get("testSegment");
        assertEquals(2, info2.getEncounterCount(), "Should have 2 encounters");
        assertEquals(2, info2.getMissingConditionTypeIds().size(), "Should accumulate both missing condition types");
        assertTrue(info2.getMissingConditionTypeIds().contains("missingType1"), "Should contain first missing type");
        assertTrue(info2.getMissingConditionTypeIds().contains("missingType2"), "Should contain second missing type");
        assertEquals(2, info2.getContextNames().size(), "Should have both contexts");
    }

    // TC6: resolveActions(null) must return true, consistent with resolveCondition(null) and resolveRule(null)
    @Test
    public void resolveActions_nullRule_returnsTrue() {
        assertTrue(typeResolutionService.resolveActions("rules", null),
            "resolveActions with null rule must return true (consistent with resolveCondition and resolveRule)");
    }

    // TC4: markValid on the last object for a type removes the outer key from getAllInvalidObjects()
    @Test
    public void markValid_lastObjectForType_removesOuterKey() {
        typeResolutionService.markInvalid("rules", "rule1", "reason");
        assertTrue(typeResolutionService.getAllInvalidObjects().containsKey("rules"),
            "Outer key must exist after markInvalid");

        typeResolutionService.markValid("rules", "rule1");
        assertFalse(typeResolutionService.getAllInvalidObjects().containsKey("rules"),
            "Outer key must be removed when the last invalid object for a type is marked valid");
    }

    // TC3: pre-resolved root (conditionType != null) with an unresolvable child returns false;
    // the root type must NOT be rolled back since it was set before this call
    @Test
    public void resolveConditionType_preResolvedRootWithUnresolvedChild_returnsFalse() {
        ConditionType rootType = new ConditionType(new Metadata());
        rootType.setItemId("rootType");

        Condition root = new Condition();
        root.setConditionType(rootType); // already resolved — type lookup is skipped
        root.setConditionTypeId("rootType");

        Condition child = new Condition();
        child.setConditionTypeId("unresolvedChildType");
        root.setParameter("subCondition", child);

        when(definitionsService.getConditionType("unresolvedChildType")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveConditionType(root, "test");

        assertFalse(resolved, "Should return false when pre-resolved root has an unresolvable child");
        assertNotNull(root.getConditionType(),
            "Pre-resolved root type must not be rolled back — it was set before this call");
        assertNull(child.getConditionType(), "Unresolvable child must remain null");
    }

    // Test A: resolveRule with a null rule must return true
    @Test
    public void testResolveRule_NullRule_ReturnsTrue() {
        assertTrue(typeResolutionService.resolveRule("rules", null), "resolveRule(null) must return true");
    }

    // Test B: resolveActionTypes with an empty (non-null) actions list returns false;
    // missingPlugins is NOT set on the rule (structural error, not a missing plugin)
    @Test
    public void testResolveActionTypes_emptyActions_returnsFalse_doesNotSetMissingPlugins() {
        Rule rule = new Rule();
        rule.setItemId("emptyActionsRule");
        rule.setMetadata(new Metadata("emptyActionsRule"));
        rule.setActions(Collections.emptyList());

        boolean resolved = typeResolutionService.resolveActionTypes(rule, false);

        assertFalse(resolved, "resolveActionTypes with an empty list must return false");
        assertFalse(rule.getMetadata().isMissingPlugins(),
            "missingPlugins must NOT be set for a structurally-empty actions list (no plugins are actually missing)");
    }

    // Test C: resolveConditionType with null conditionTypeId returns false
    @Test
    public void testResolveConditionType_withNullConditionTypeId_returnsFalse() {
        Condition condition = new Condition();
        // conditionTypeId is null by default — no ID to look up
        boolean resolved = typeResolutionService.resolveConditionType(condition, "test");
        assertFalse(resolved, "resolveConditionType must return false when conditionTypeId is null");
        assertNull(condition.getConditionType(), "conditionType must remain null when conditionTypeId is null");
    }

    // Test D: getAllInvalidObjectIds returns a map covering all object types with their IDs
    @Test
    public void testGetAllInvalidObjectIds_returnsAllTrackedIds() {
        typeResolutionService.markInvalid("rules", "rule1", "reason A");
        typeResolutionService.markInvalid("segments", "seg1", "reason B");

        Map<String, Set<String>> allIds = typeResolutionService.getAllInvalidObjectIds();

        assertTrue(allIds.containsKey("rules"), "Map must contain 'rules' key");
        assertTrue(allIds.containsKey("segments"), "Map must contain 'segments' key");
        assertTrue(allIds.get("rules").contains("rule1"), "rules set must contain rule1");
        assertTrue(allIds.get("segments").contains("seg1"), "segments set must contain seg1");
    }

    @Nested
    class ResolutionTests {
        @Test
        public void testResolveCondition_Success() {
            Segment segment = new Segment();
            segment.setItemId("testSegment");
            segment.setMetadata(new Metadata("testSegment"));

            Condition condition = new Condition();
            condition.setConditionTypeId("testConditionType");
            segment.setCondition(condition);

            when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);

            boolean resolved = typeResolutionService.resolveCondition("segments", segment, condition, "test context");

            assertTrue(resolved, "Condition type should resolve successfully");
            assertFalse(segment.getMetadata().isMissingPlugins(), "missingPlugins should be false when resolution succeeds");
            assertFalse(typeResolutionService.isInvalid("segments", "testSegment"), "Segment should not be marked as invalid");
        }

        @Test
        public void testResolveCondition_WithUnresolvedType() {
            Segment segment = new Segment();
            segment.setItemId("testSegment");
            segment.setMetadata(new Metadata("testSegment"));

            Condition condition = new Condition();
            condition.setConditionTypeId("nonExistentType");
            segment.setCondition(condition);

            when(definitionsService.getConditionType("nonExistentType")).thenReturn(null);

            boolean resolved = typeResolutionService.resolveCondition("segments", segment, condition, "test context");

            assertFalse(resolved, "Should return false when condition type cannot be resolved");
            assertTrue(segment.getMetadata().isMissingPlugins(), "missingPlugins should be true when resolution fails");
            assertTrue(typeResolutionService.isInvalid("segments", "testSegment"), "Segment should be marked as invalid");
        }

        @Test
        public void testResolveActions_Success() {
            Rule rule = new Rule();
            rule.setItemId("testRule");
            rule.setMetadata(new Metadata("testRule"));

            Action action = new Action();
            action.setActionTypeId("testActionType");
            rule.setActions(Collections.singletonList(action));

            when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

            boolean resolved = typeResolutionService.resolveActions("rules", rule);

            assertTrue(resolved, "Action types should resolve successfully");
            assertFalse(rule.getMetadata().isMissingPlugins(), "missingPlugins should be false when resolution succeeds");
        }

        @Test
        public void testResolveActions_WithUnresolvedType() {
            Rule rule = new Rule();
            rule.setItemId("testRule");
            rule.setMetadata(new Metadata("testRule"));

            Action action = new Action();
            action.setActionTypeId("nonExistentActionType");
            rule.setActions(Collections.singletonList(action));

            when(definitionsService.getActionType("nonExistentActionType")).thenReturn(null);

            boolean resolved = typeResolutionService.resolveActions("rules", rule);

            assertFalse(resolved, "Should return false when action type cannot be resolved");
            assertTrue(rule.getMetadata().isMissingPlugins(), "missingPlugins should be true when resolution fails");
        }

        @Test
        public void testResolveRule_Success() {
            Rule rule = new Rule();
            rule.setItemId("testRule");
            rule.setMetadata(new Metadata("testRule"));

            Condition condition = new Condition();
            condition.setConditionTypeId("testConditionType");
            rule.setCondition(condition);

            Action action = new Action();
            action.setActionTypeId("testActionType");
            rule.setActions(Collections.singletonList(action));

            when(definitionsService.getConditionType("testConditionType")).thenReturn(testConditionType);
            when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

            boolean resolved = typeResolutionService.resolveRule("rules", rule);

            assertTrue(resolved, "Rule should resolve successfully when both condition and actions resolve");
            assertFalse(rule.getMetadata().isMissingPlugins(), "missingPlugins should be false when all types resolve");
            assertFalse(typeResolutionService.isInvalid("rules", "testRule"), "Rule should not be marked as invalid");
        }

        @Test
        public void testResolveRule_WithUnresolvedCondition() {
            Rule rule = new Rule();
            rule.setItemId("testRule");
            rule.setMetadata(new Metadata("testRule"));

            Condition condition = new Condition();
            condition.setConditionTypeId("nonExistentConditionType");
            rule.setCondition(condition);

            Action action = new Action();
            action.setActionTypeId("testActionType");
            rule.setActions(Collections.singletonList(action));

            when(definitionsService.getConditionType("nonExistentConditionType")).thenReturn(null);
            when(definitionsService.getActionType("testActionType")).thenReturn(testActionType);

            boolean resolved = typeResolutionService.resolveRule("rules", rule);

            assertFalse(resolved, "Should return false when condition type cannot be resolved");
            assertTrue(rule.getMetadata().isMissingPlugins(), "missingPlugins should be true when resolution fails");
            assertTrue(typeResolutionService.isInvalid("rules", "testRule"), "Rule should be marked as invalid");
        }
    }

    // -----------------------------------------------------------------------
    // Regression tests for review findings: cycle, max-depth, sibling rollback
    // -----------------------------------------------------------------------

    @Test
    public void resolveConditionType_circularParentChain_returnsFalse() {
        // typeA's parentCondition points to typeB, typeB's parentCondition points back to typeA
        ConditionType typeA = new ConditionType(new Metadata());
        typeA.setItemId("cycleTypeA");
        ConditionType typeB = new ConditionType(new Metadata());
        typeB.setItemId("cycleTypeB");

        Condition parentOfA = new Condition();
        parentOfA.setConditionTypeId("cycleTypeB");
        typeA.setParentCondition(parentOfA);

        Condition parentOfB = new Condition();
        parentOfB.setConditionTypeId("cycleTypeA");
        typeB.setParentCondition(parentOfB);

        when(definitionsService.getConditionType("cycleTypeA")).thenReturn(typeA);
        when(definitionsService.getConditionType("cycleTypeB")).thenReturn(typeB);

        Condition root = new Condition();
        root.setConditionTypeId("cycleTypeA");

        boolean resolved = typeResolutionService.resolveConditionType(root, "cycle test");

        assertFalse(resolved, "Must return false for a circular parent chain");
        assertNull(root.getConditionType(), "Condition type must be rolled back on cycle detection");
    }

    @Test
    public void resolveConditionType_exceedsMaxDepth_returnsFalse() {
        // Build a chain of 1002 conditions as nested sub-condition parameters.
        // Each depth increment passes through resolveConditionTypeInternal, so depth > 1000 triggers the guard.
        int chainLength = 1002;
        Condition[] conditions = new Condition[chainLength];
        for (int i = 0; i < chainLength; i++) {
            conditions[i] = new Condition();
            conditions[i].setConditionTypeId("depthType" + i);
        }
        for (int i = 0; i < chainLength - 1; i++) {
            conditions[i].setParameter("sub", conditions[i + 1]);
        }

        when(definitionsService.getConditionType(startsWith("depthType")))
            .thenAnswer(inv -> {
                String id = inv.getArgument(0);
                ConditionType ct = new ConditionType(new Metadata());
                ct.setItemId(id);
                return ct;
            });

        boolean resolved = typeResolutionService.resolveConditionType(conditions[0], "depth test");

        assertFalse(resolved, "Must return false when recursion depth exceeds MAX_RECURSION_DEPTH");
    }

    @Test
    public void resolveConditionType_threeSiblings_thirdFailsRollsBackAll() {
        // parent has three sub-conditions as a list; child1 and child2 resolve, child3 does not.
        // On failure, all resolved siblings and the parent must be rolled back.
        ConditionType parentType = new ConditionType(new Metadata());
        parentType.setItemId("rollbackParent");
        ConditionType child1Type = new ConditionType(new Metadata());
        child1Type.setItemId("rollbackChild1");
        ConditionType child2Type = new ConditionType(new Metadata());
        child2Type.setItemId("rollbackChild2");

        Condition parent = new Condition();
        parent.setConditionTypeId("rollbackParent");
        Condition child1 = new Condition();
        child1.setConditionTypeId("rollbackChild1");
        Condition child2 = new Condition();
        child2.setConditionTypeId("rollbackChild2");
        Condition child3 = new Condition();
        child3.setConditionTypeId("rollbackMissing");

        parent.setParameter("subConditions", Arrays.asList(child1, child2, child3));

        when(definitionsService.getConditionType("rollbackParent")).thenReturn(parentType);
        when(definitionsService.getConditionType("rollbackChild1")).thenReturn(child1Type);
        when(definitionsService.getConditionType("rollbackChild2")).thenReturn(child2Type);
        when(definitionsService.getConditionType("rollbackMissing")).thenReturn(null);

        boolean resolved = typeResolutionService.resolveConditionType(parent, "rollback test");

        assertFalse(resolved, "Must return false when any sibling fails to resolve");
        assertNull(parent.getConditionType(), "Parent type must be rolled back");
        assertNull(child1.getConditionType(), "child1 type must be rolled back after third sibling failure");
        assertNull(child2.getConditionType(), "child2 type must be rolled back after third sibling failure");
        assertNull(child3.getConditionType(), "child3 type must remain null (was never resolved)");
    }

    @Test
    public void updateEncounter_concurrentUpdates_noDuplicateMissingTypeIds() throws InterruptedException {
        // CopyOnWriteArraySet deduplication: concurrent threads adding the same type ID
        // must not produce duplicates in the returned list.
        InvalidObjectInfo info = new InvalidObjectInfo("rules", "rule1", "test");
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        List<String> singletonList = Collections.singletonList("sameConditionType");
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                info.updateEncounter(singletonList, null, "ctx");
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();

        List<String> ids = info.getMissingConditionTypeIds();
        assertEquals(1, ids.size(),
            "Concurrent adds of the same type ID must not produce duplicates");
        assertEquals("sameConditionType", ids.get(0));
    }

    @Test
    public void resolveRule_nullActions_doesNotSetMissingPlugins() {
        // Structural config error (null actions) must not set missingPlugins —
        // that flag is reserved for genuinely missing plugin types.
        Rule rule = new Rule();
        rule.setItemId("emptyRule");
        rule.setMetadata(new Metadata("emptyRule"));
        rule.setCondition(null);
        rule.setActions(null);

        boolean resolved = typeResolutionService.resolveRule("rules", rule);

        assertFalse(resolved, "Must return false when actions are null");
        assertFalse(rule.getMetadata().isMissingPlugins(),
            "missingPlugins must NOT be set for structural config errors like null actions");
    }

    @Test
    public void resolveCondition_nestedChildFails_reportsLeafTypeIdNotRoot() {
        // When a nested sub-condition fails, InvalidObjectInfo must name the leaf type,
        // not the root booleanCondition wrapper.
        ConditionType boolType = new ConditionType(new Metadata());
        boolType.setItemId("booleanCondition");

        Condition root = new Condition();
        root.setConditionTypeId("booleanCondition");
        // Pre-set root's conditionType so that rollback on leaf failure leaves root resolved;
        // findUnresolvedConditionTypeId() will then drill into children to find the leaf.
        root.setConditionType(boolType);
        Condition leaf = new Condition();
        leaf.setConditionTypeId("missingLeafType");
        root.setParameter("subConditions", Collections.singletonList(leaf));

        Segment segment = new Segment();
        segment.setItemId("seg1");
        segment.setMetadata(new Metadata("seg1"));

        when(definitionsService.getConditionType("booleanCondition")).thenReturn(boolType);
        when(definitionsService.getConditionType("missingLeafType")).thenReturn(null);

        typeResolutionService.resolveCondition("segments", segment, root, "test");

        Map<String, InvalidObjectInfo> invalids = typeResolutionService.getInvalidObjects("segments");
        InvalidObjectInfo info = invalids.get("seg1");
        assertNotNull(info, "Segment must be recorded as invalid");
        assertEquals(1, info.getMissingConditionTypeIds().size());
        assertEquals("missingLeafType", info.getMissingConditionTypeIds().get(0),
            "Reported missing type must be the failing leaf, not the root wrapper");
        assertTrue(info.getReason().contains("missingLeafType"),
            "Reason text must name the actual failing leaf type");
    }
}

