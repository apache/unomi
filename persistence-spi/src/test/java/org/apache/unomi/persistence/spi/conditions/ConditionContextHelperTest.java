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
package org.apache.unomi.persistence.spi.conditions;

import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.scripting.ScriptExecutor;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for ConditionContextHelper parameter resolution,
 * including simple cases, chains, cycles, and edge cases.
 */
@RunWith(MockitoJUnitRunner.class)
public class ConditionContextHelperTest {

    @Mock
    private ScriptExecutor scriptExecutor;

    @Mock
    private DefinitionsService definitionsService;

    @Mock
    private TracerService tracerService;

    @Mock
    private RequestTracer requestTracer;

    private Map<String, Object> context;

    @Before
    public void setUp() {
        context = new HashMap<>();
        when(tracerService.getCurrentTracer()).thenReturn(requestTracer);
        when(requestTracer.isEnabled()).thenReturn(true);
    }

    // ========== Simple Parameter Reference Tests ==========

    @Test
    public void testSimpleParameterReference() {
        context.put("param1", "value1");
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Simple parameter reference should resolve to context value", "value1", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testParameterReferenceNotFound() {
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "nonexistent";
        
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertNull("Missing parameter reference should resolve to null", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testSimpleScriptExpression() {
        when(scriptExecutor.execute(eq("return 'scriptResult';"), eq(context)))
            .thenReturn("scriptResult");
        
        String value = ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'scriptResult';";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Script expression should execute and return result", "scriptResult", resolved.getParameterValues().get("testParam"));
    }

    // ========== Parameter Reference Chain Tests ==========

    @Test
    public void testTwoLevelParameterChain() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", "finalValue");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Two-level parameter chain should resolve to final value", "finalValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testThreeLevelParameterChain() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param3");
        context.put("param3", "finalValue");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Three-level parameter chain should resolve to final value", "finalValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testParameterToScriptChain() {
        context.put("param1", ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'scriptResult';");
        when(scriptExecutor.execute(eq("return 'scriptResult';"), eq(context)))
            .thenReturn("scriptResult");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Parameter to script chain should resolve correctly", "scriptResult", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testScriptToParameterChain() {
        context.put("param1", "finalValue");
        when(scriptExecutor.execute(eq("return 'parameter::param1';"), eq(context)))
            .thenReturn(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'parameter::param1';";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Script to parameter chain should resolve correctly", "finalValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testMixedParameterScriptChain() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'scriptResult';");
        context.put("param3", "finalValue");
        when(scriptExecutor.execute(eq("return 'scriptResult';"), eq(context)))
            .thenReturn(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param3");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Mixed parameter-script chain should resolve correctly", "finalValue", resolved.getParameterValues().get("testParam"));
    }

    // ========== Cyclic Reference Tests ==========

    @Test
    public void testDirectCyclicReference() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNull("Condition with cyclic reference should return null", resolved);
    }

    @Test
    public void testTwoLevelCyclicReference() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNull("Condition with two-level cyclic reference should return null", resolved);
    }

    @Test
    public void testThreeLevelCyclicReference() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param3");
        context.put("param3", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNull("Condition with three-level cyclic reference should return null", resolved);
    }

    @Test
    public void testCyclicReferenceWithScript() {
        context.put("param1", ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'parameter::param1';");
        when(scriptExecutor.execute(eq("return 'parameter::param1';"), eq(context)))
            .thenReturn(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNull("Condition with script-based cyclic reference should return null", resolved);
    }

    // ========== Maximum Depth Tests ==========

    @Test
    public void testMaximumDepthExceeded() {
        // Create a chain that exceeds MAX_RESOLUTION_DEPTH (50)
        for (int i = 1; i <= 51; i++) {
            if (i < 51) {
                context.put("param" + i, ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param" + (i + 1));
            } else {
                context.put("param" + i, "finalValue");
            }
        }
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNull("Condition exceeding maximum depth should return null", resolved);
    }

    // ========== Nested Structure Tests ==========

    @Test
    public void testParameterReferenceInMap() {
        context.put("param1", "value1");
        context.put("param2", "value2");
        
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("key1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        paramValues.put("key2", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        
        Condition condition = new Condition();
        condition.setParameterValues(paramValues);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedValues = (Map<String, Object>) resolved.getParameterValues();
        assertEquals("Parameter reference in map should resolve", "value1", resolvedValues.get("key1"));
        assertEquals("Parameter reference in map should resolve", "value2", resolvedValues.get("key2"));
    }

    @Test
    public void testParameterReferenceInList() {
        context.put("param1", "value1");
        context.put("param2", "value2");
        
        List<Object> paramValues = new ArrayList<>();
        paramValues.add(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        paramValues.add(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        paramValues.add("directValue");
        
        Condition condition = new Condition();
        condition.setParameterValues(Collections.singletonMap("listParam", paramValues));
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        @SuppressWarnings("unchecked")
        List<Object> resolvedList = (List<Object>) ((Map<String, Object>) resolved.getParameterValues()).get("listParam");
        assertNotNull("Resolved list should not be null", resolvedList);
        assertEquals("List should have 3 elements", 3, resolvedList.size());
        assertEquals("First list element should resolve", "value1", resolvedList.get(0));
        assertEquals("Second list element should resolve", "value2", resolvedList.get(1));
        assertEquals("Third list element should remain unchanged", "directValue", resolvedList.get(2));
    }

    @Test
    public void testNestedMapWithChains() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", "finalValue");
        
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("nestedKey", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("outerKey", nestedMap);
        
        Condition condition = new Condition();
        condition.setParameterValues(paramValues);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        @SuppressWarnings("unchecked")
        Map<String, Object> outerMap = (Map<String, Object>) resolved.getParameterValues();
        @SuppressWarnings("unchecked")
        Map<String, Object> innerMap = (Map<String, Object>) outerMap.get("outerKey");
        assertEquals("Nested map with parameter chain should resolve correctly", "finalValue", innerMap.get("nestedKey"));
    }

    @Test
    public void testNestedListWithChains() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", "finalValue");
        
        List<Object> nestedList = new ArrayList<>();
        nestedList.add(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("listKey", nestedList);
        
        Condition condition = new Condition();
        condition.setParameterValues(paramValues);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        @SuppressWarnings("unchecked")
        List<Object> resolvedList = (List<Object>) ((Map<String, Object>) resolved.getParameterValues()).get("listKey");
        assertEquals("Nested list with parameter chain should resolve correctly", "finalValue", resolvedList.get(0));
    }

    // ========== Type Validation Tests ==========

    @Test
    public void testTypeValidationWithCorrectType() {
        context.put("param1", 42);
        
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testCondition");
        Parameter param = new Parameter();
        param.setId("testParam");
        param.setType("integer");
        conditionType.setParameters(Collections.singletonList(param));
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        condition.setConditionType(conditionType);
        
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, definitionsService, tracerService);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Integer parameter should resolve correctly", 42, resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testTypeValidationWithTypeMismatch() {
        context.put("param1", "notAnInteger");
        
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testCondition");
        Parameter param = new Parameter();
        param.setId("testParam");
        param.setType("integer");
        conditionType.setParameters(Collections.singletonList(param));
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        condition.setConditionType(conditionType);
        
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, definitionsService, tracerService);
        
        assertNotNull("Resolved condition should not be null", resolved);
        // Type mismatch should log warning but still resolve
        assertEquals("Type mismatch should still resolve value but log warning", "notAnInteger", resolved.getParameterValues().get("testParam"));
        verify(requestTracer, atLeastOnce()).trace(anyString(), any(Map.class));
    }

    @Test
    public void testTypeValidationWithChain() {
        context.put("param1", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        context.put("param2", 42);
        
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testCondition");
        Parameter param = new Parameter();
        param.setId("testParam");
        param.setType("integer");
        conditionType.setParameters(Collections.singletonList(param));
        
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        condition.setConditionType(conditionType);
        
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, definitionsService, tracerService);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Parameter chain should resolve and validate type correctly", 42, resolved.getParameterValues().get("testParam"));
    }

    // ========== Edge Case Tests ==========

    @Test
    public void testNullValue() {
        Condition condition = createConditionWithParameter("testParam", null);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertNull("Null value should remain null", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testEmptyContext() {
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, new HashMap<>(), scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertNull("Parameter reference in empty context should resolve to null", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testNonReferenceValue() {
        Condition condition = createConditionWithParameter("testParam", "directValue");
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Non-reference value should remain unchanged", "directValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testConditionWithoutParameterReferences() {
        Condition condition = createConditionWithParameter("testParam", "directValue");
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        // Should return the same condition instance when no references present
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Condition without parameter references should remain unchanged", "directValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testScriptReturningNull() {
        when(scriptExecutor.execute(anyString(), eq(context))).thenReturn(null);
        
        String value = ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return null;";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertNull("Script returning null should resolve to null", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testScriptReturningParameterReference() {
        context.put("param1", "finalValue");
        when(scriptExecutor.execute(eq("return 'parameter::param1';"), eq(context)))
            .thenReturn(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        
        String value = ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'parameter::param1';";
        Condition condition = createConditionWithParameter("testParam", value);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        assertEquals("Script returning parameter reference should continue resolving", "finalValue", resolved.getParameterValues().get("testParam"));
    }

    @Test
    public void testMultipleParametersWithMixedReferences() {
        context.put("param1", "value1");
        context.put("param2", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        when(scriptExecutor.execute(eq("return 'scriptResult';"), eq(context)))
            .thenReturn("scriptResult");
        
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("direct", "directValue");
        paramValues.put("paramRef", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        paramValues.put("chainRef", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param2");
        paramValues.put("scriptRef", ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'scriptResult';");
        
        Condition condition = new Condition();
        condition.setParameterValues(paramValues);
        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor);
        
        assertNotNull("Resolved condition should not be null", resolved);
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedValues = (Map<String, Object>) resolved.getParameterValues();
        assertEquals("Direct value should remain unchanged", "directValue", resolvedValues.get("direct"));
        assertEquals("Parameter reference should resolve", "value1", resolvedValues.get("paramRef"));
        assertEquals("Parameter chain should resolve", "value1", resolvedValues.get("chainRef"));
        assertEquals("Script reference should execute", "scriptResult", resolvedValues.get("scriptRef"));
    }

    // ========== Helper Methods ==========

    private Condition createConditionWithParameter(String paramName, Object paramValue) {
        Condition condition = new Condition();
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put(paramName, paramValue);
        condition.setParameterValues(paramValues);
        return condition;
    }
}

