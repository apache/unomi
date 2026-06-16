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
package org.apache.unomi.persistence.spi.conditions.evaluator.impl;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Profile;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.TypeResolutionService;
import org.apache.unomi.metrics.MetricsService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluator;
import org.apache.unomi.scripting.ScriptExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TC1: unit tests for the paths changed in ConditionEvaluatorDispatcherImpl —
 * type resolution, parent fallback, and missing evaluator key.
 */
@RunWith(MockitoJUnitRunner.class)
public class ConditionEvaluatorDispatcherImplTest {

    private ConditionEvaluatorDispatcherImpl dispatcher;

    @Mock private MetricsService metricsService;
    @Mock private ScriptExecutor scriptExecutor;
    @Mock private DefinitionsService definitionsService;
    @Mock private TypeResolutionService typeResolutionService;

    private final Profile dummyProfile = new Profile("test-profile");

    @Before
    public void setUp() {
        dispatcher = new ConditionEvaluatorDispatcherImpl();
        dispatcher.setMetricsService(metricsService);
        dispatcher.setScriptExecutor(scriptExecutor);
    }

    // eval() with null conditionType and no DefinitionsService — must return false, not throw
    @Test
    public void eval_nullConditionType_noDefinitionsService_returnsFalse() {
        Condition condition = new Condition();
        condition.setConditionTypeId("unknownType");
        // conditionType left null; no definitionsService set

        boolean result = dispatcher.eval(condition, dummyProfile);

        assertFalse("Must return false gracefully when conditionType is null and no DefinitionsService is available", result);
    }

    // eval() with null conditionType, DS present but TypeResolutionService returns null — must return false
    @Test
    public void eval_nullConditionType_nullTypeResolutionService_returnsFalse() {
        dispatcher.setDefinitionsService(definitionsService);
        when(definitionsService.getTypeResolutionService()).thenReturn(null);

        Condition condition = new Condition();
        condition.setConditionTypeId("someType");

        boolean result = dispatcher.eval(condition, dummyProfile);

        assertFalse("Must return false when TypeResolutionService is unavailable", result);
    }

    // eval() with null conditionType, DS present, TypeResolutionService does not set a type — must return false
    @Test
    public void eval_nullConditionType_resolutionDoesNotSetType_returnsFalse() {
        dispatcher.setDefinitionsService(definitionsService);
        when(definitionsService.getTypeResolutionService()).thenReturn(typeResolutionService);
        // resolveConditionType returns false and does NOT set conditionType → stays null
        when(typeResolutionService.resolveConditionType(any(Condition.class), any(String.class))).thenReturn(false);

        Condition condition = new Condition();
        condition.setConditionTypeId("missingType");

        boolean result = dispatcher.eval(condition, dummyProfile);

        assertFalse("Must return false when type resolution attempt leaves conditionType null", result);
        verify(typeResolutionService).resolveConditionType(eq(condition), any(String.class));
    }

    // eval() where DS is null and conditionType has a parentCondition — must recurse to parent
    @Test
    public void eval_noDefinitionsService_parentConditionPresent_recursesFallback() {
        // Parent condition: no evaluator key, no parent → returns false at the "no evaluator" path
        ConditionType parentType = new ConditionType(new Metadata());
        parentType.setItemId("parentType");
        // conditionEvaluatorKey intentionally null → parent eval returns false too
        Condition parentCondition = new Condition(parentType);

        ConditionType childType = new ConditionType(new Metadata());
        childType.setItemId("childType");
        childType.setParentCondition(parentCondition);
        // No conditionEvaluatorKey on childType → null key triggers parent fallback (DS not wired path)

        Condition childCondition = new Condition(childType);
        childCondition.setParameter("someParam", "someValue");
        // No DefinitionsService → fallback recursion on embedded parent fires at lines 132-135

        boolean result = dispatcher.eval(childCondition, dummyProfile);

        // Recursion reaches parentCondition: no evaluator key, no parent → WARN + false
        assertFalse("Fallback to parent condition with no evaluator must return false", result);
    }

    // eval() where conditionEvaluatorKey is set but no evaluator is registered — must return false
    @Test
    public void eval_missingEvaluatorKey_returnsFalse() {
        ConditionType type = new ConditionType(new Metadata());
        type.setItemId("myType");
        type.setConditionEvaluator("nonExistentEvaluator");

        Condition condition = new Condition(type);
        // No DefinitionsService — type is already set so resolution is skipped; no parent → effectiveCondition=condition

        boolean result = dispatcher.eval(condition, dummyProfile);

        assertFalse("Must return false when evaluatorKey references an unregistered evaluator", result);
    }

    // eval() with a registered evaluator that always returns true — must return true
    @Test
    public void testEval_withRegisteredEvaluator_returnsResult() {
        // Register an evaluator that always returns true
        dispatcher.addEvaluator("testEval", (condition, item, ctx, d) -> true);

        Condition condition = new Condition();
        condition.setConditionTypeId("testEvalType");
        ConditionType conditionType = new ConditionType(new Metadata());
        conditionType.setConditionEvaluator("testEval");
        condition.setConditionType(conditionType);

        // Use a real Profile as item
        Profile profile = new Profile("test-profile-eval");

        boolean result = dispatcher.eval(condition, profile);
        assertTrue("Evaluator returning true should produce true", result);

        dispatcher.removeEvaluator("testEval");
    }

    // addEvaluator / removeEvaluator wiring round-trip
    @Test
    public void addRemoveEvaluator_registrationRoundTrip() {
        ConditionEvaluator mockEvaluator = mock(ConditionEvaluator.class);
        dispatcher.addEvaluator("myEval", mockEvaluator);

        dispatcher.removeEvaluator("myEval");

        // After removal, eval with that key must return false
        ConditionType type = new ConditionType(new Metadata());
        type.setConditionEvaluator("myEval");
        Condition condition = new Condition(type);

        boolean result = dispatcher.eval(condition, dummyProfile);
        assertFalse("After removal, evaluator key must not resolve to any registered evaluator", result);
    }
}
