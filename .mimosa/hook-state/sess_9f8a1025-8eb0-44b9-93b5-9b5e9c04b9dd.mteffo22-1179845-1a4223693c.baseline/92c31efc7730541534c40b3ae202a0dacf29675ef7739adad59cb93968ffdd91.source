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
import org.apache.unomi.api.services.ValueTypeValidator;
import org.apache.unomi.scripting.ScriptExecutor;
import org.apache.unomi.tracing.api.RequestTracer;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    private TracerService tracerService;

    @Mock
    private RequestTracer requestTracer;

    private Map<String, Object> context;

    @Before
    public void setUp() {
        context = new HashMap<>();
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
            condition, context, scriptExecutor, true);
        
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
            condition, context, scriptExecutor, true);
        
        assertNotNull("Resolved condition should not be null", resolved);
        // Type mismatch should log warning but still resolve
        assertEquals("Type mismatch should still resolve value but log warning", "notAnInteger", resolved.getParameterValues().get("testParam"));
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
            condition, context, scriptExecutor, true);
        
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

        assertSame("Condition without contextual parameters should be returned as-is", condition, resolved);
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

    // TC5: null scriptExecutor + script:: expression — getContextualCondition must return null
    // (RESOLUTION_ERROR propagated internally then converted to null at the public boundary)
    @Test
    public void testGetContextualCondition_nullScriptExecutor_scriptExpression_returnsNull() {
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put("value", ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 'result';");

        Condition condition = new Condition();
        condition.setParameterValues(paramValues);

        // Pass null as scriptExecutor — simulates OSGi service not yet wired
        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, null);

        assertNull("getContextualCondition must return null when scriptExecutor is null and a script:: value is present", resolved);
    }

    @Test
    public void testRegisteredValueTypeValidatorAcceptsComparisonOperator() {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Arrays.asList(
            new Parameter("propertyName", "string", false),
            new Parameter("comparisonOperator", "comparisonOperator", false),
            new Parameter("propertyValue", "string", false)));

        Condition condition = new Condition(conditionType);
        Map<String, Object> params = new HashMap<>();
        params.put("propertyName", "eventType");
        params.put("comparisonOperator", "equals");
        params.put("propertyValue", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "eventTypeValue");
        condition.setParameterValues(params);
        context.put("eventTypeValue", "view");

        Map<String, ValueTypeValidator> validators = Collections.singletonMap(
            "comparisonoperator",
            new ValueTypeValidator() {
                @Override
                public String getValueTypeId() {
                    return "comparisonOperator";
                }

                @Override
                public boolean validate(Object value) {
                    return "equals".equals(value);
                }

                @Override
                public String getValueTypeDescription() {
                    return "Value must be a valid comparison operator";
                }
            });

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, null, validators);

        assertNotNull(resolved);
        assertEquals("equals", resolved.getParameter("comparisonOperator"));
        assertEquals("view", resolved.getParameter("propertyValue"));
    }

    @Test
    public void testValueTypeValidatorRegistryUsedWhenExplicitMapIsNull() {
        ValueTypeValidatorRegistry registry = new ValueTypeValidatorRegistry();
        ValueTypeValidator comparisonValidator = new ValueTypeValidator() {
            @Override
            public String getValueTypeId() {
                return "comparisonOperator";
            }

            @Override
            public boolean validate(Object value) {
                return "equals".equals(value);
            }

            @Override
            public String getValueTypeDescription() {
                return "Value must be a valid comparison operator";
            }
        };
        registry.bindValidator(comparisonValidator);
        try {
            ConditionType conditionType = new ConditionType();
            conditionType.setItemId("eventPropertyCondition");
            conditionType.setParameters(Arrays.asList(
                new Parameter("propertyName", "string", false),
                new Parameter("comparisonOperator", "comparisonOperator", false),
                new Parameter("propertyValue", "string", false)));

            Condition condition = new Condition(conditionType);
            Map<String, Object> params = new HashMap<>();
            params.put("propertyName", "eventType");
            params.put("comparisonOperator", "equals");
            params.put("propertyValue", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "eventTypeValue");
            condition.setParameterValues(params);
            context.put("eventTypeValue", "view");

            Condition resolved = ConditionContextHelper.getContextualCondition(
                condition, context, scriptExecutor, true, null);

            assertNotNull(resolved);
            assertEquals("equals", resolved.getParameter("comparisonOperator"));
        } finally {
            registry.unbindValidator(comparisonValidator);
        }
    }

    // ========== Public utility API tests ==========

    @Test
    public void testIsParameterReference() {
        assertTrue(ConditionContextHelper.isParameterReference(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "key"));
        assertTrue(ConditionContextHelper.isParameterReference(ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return 1;"));
        assertFalse(ConditionContextHelper.isParameterReference("equals"));
        assertFalse(ConditionContextHelper.isParameterReference(null));
        assertFalse(ConditionContextHelper.isParameterReference(42));
        assertFalse(ConditionContextHelper.isParameterReference("parameter:not-a-reference"));
    }

    @Test
    public void testHasContextualParameter() {
        assertFalse(ConditionContextHelper.hasContextualParameter(null));
        assertFalse(ConditionContextHelper.hasContextualParameter("literal"));
        assertFalse(ConditionContextHelper.hasContextualParameter(Collections.emptyMap()));

        Map<String, Object> withRef = Collections.singletonMap(
            "k", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "x");
        assertTrue(ConditionContextHelper.hasContextualParameter(withRef));

        List<Object> listWithScript = Collections.singletonList(
            ConditionContextHelper.SCRIPT_EXPRESSION_PREFIX + "return true;");
        assertTrue(ConditionContextHelper.hasContextualParameter(listWithScript));

        Condition nested = createConditionWithParameter("p", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "x");
        assertTrue(ConditionContextHelper.hasContextualParameter(nested));
    }

    @Test
    public void testFoldToASCII() {
        assertNull(ConditionContextHelper.foldToASCII((String) null));
        assertEquals("cafe", ConditionContextHelper.foldToASCII("Caf\u00E9"));

        String[] array = new String[] { "Caf\u00E9" };
        assertEquals("cafe", ConditionContextHelper.foldToASCII(array)[0]);

        Collection<String> folded = ConditionContextHelper.foldToASCII(
            new ArrayList<>(Collections.singletonList("Caf\u00E9")));
        assertEquals("cafe", folded.iterator().next());

        assertEquals("cafe", ConditionContextHelper.forceFoldToASCII("Caf\u00E9"));
        assertNull(ConditionContextHelper.forceFoldToASCII(null));
    }

    // ========== Context merge and resolution edge cases ==========

    @Test
    public void testNullContextIsInitialized() {
        String value = ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "literalParam";
        Condition condition = createConditionWithParameter("literalParam", "fromCondition");
        condition.getParameterValues().put("resolved", value);

        Condition resolved = ConditionContextHelper.getContextualCondition(condition, null, scriptExecutor);

        assertNotNull(resolved);
        assertEquals("fromCondition", resolved.getParameter("resolved"));
    }

    @Test
    public void testConditionLiteralsMergedIntoContextButDoNotOverwrite() {
        context.put("sharedKey", "contextWins");

        Map<String, Object> params = new HashMap<>();
        params.put("sharedKey", "conditionValue");
        params.put("onlyOnCondition", "conditionOnly");
        params.put("resolved", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "onlyOnCondition");

        Condition condition = new Condition();
        condition.setParameterValues(params);

        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);

        assertNotNull(resolved);
        assertEquals("conditionOnly", resolved.getParameter("resolved"));
        assertEquals("contextWins", context.get("sharedKey"));
    }

    @Test
    public void testReferenceToSiblingLiteralOnSameCondition() {
        Map<String, Object> params = new HashMap<>();
        params.put("propertyValue", "view");
        params.put("propertyName", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "nameKey");
        params.put("nameKey", "eventType");

        Condition condition = new Condition();
        condition.setParameterValues(params);

        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);

        assertNotNull(resolved);
        assertEquals("eventType", resolved.getParameter("propertyName"));
    }

    @Test
    public void testResolutionDepthAtLimitSucceeds() {
        for (int i = 1; i < 50; i++) {
            context.put("param" + i, ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param" + (i + 1));
        }
        context.put("param50", "finalValue");

        Condition condition = createConditionWithParameter(
            "testParam", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);

        assertNotNull(resolved);
        assertEquals("finalValue", resolved.getParameter("testParam"));
    }

    @Test
    public void testCyclicReferenceInListSkipsElementButKeepsOthers() {
        context.put("good", "ok");

        List<Object> list = new ArrayList<>();
        list.add(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "good");
        list.add(ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "bad");
        list.add("literal");
        context.put("bad", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "bad");

        Condition condition = new Condition();
        condition.setParameterValues(Collections.singletonMap("items", list));

        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);

        assertNotNull(resolved);
        @SuppressWarnings("unchecked")
        List<Object> resolvedList = (List<Object>) resolved.getParameter("items");
        assertEquals(2, resolvedList.size());
        assertEquals("ok", resolvedList.get(0));
        assertEquals("literal", resolvedList.get(1));
    }

    @Test
    public void testCyclicReferenceInNestedMapNullsInnerMap() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "cycle");
        context.put("cycle", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "cycle");

        Condition condition = new Condition();
        condition.setParameterValues(Collections.singletonMap("nested", nested));

        Condition resolved = ConditionContextHelper.getContextualCondition(condition, context, scriptExecutor);

        assertNotNull(resolved);
        assertNull(resolved.getParameter("nested"));
    }

    @Test
    public void testNestedConditionTriggersResolutionButIsNotDeepResolved() {
        Condition inner = createConditionWithParameter(
            "inner", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "value");
        context.put("value", "resolved");

        Condition outer = new Condition();
        outer.setParameterValues(Collections.singletonMap("subCondition", inner));

        Condition resolved = ConditionContextHelper.getContextualCondition(outer, context, scriptExecutor);

        assertNotNull(resolved);
        assertTrue(resolved.getParameter("subCondition") instanceof Condition);
        Condition resolvedInner = (Condition) resolved.getParameter("subCondition");
        assertSame(inner, resolvedInner);
        assertEquals(
            ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "value",
            resolvedInner.getParameter("inner"));
    }

    // ========== Type validation edge cases ==========

    @Test
    public void testTypeCompatibilityLongForIntegerParameter() {
        context.put("param1", 42L);

        Condition condition = createIntegerParameterCondition(
            ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertEquals(42L, resolved.getParameter("testParam"));
    }

    @Test
    public void testTypeCompatibilityDateTypes() {
        Date now = new Date();
        context.put("param1", now);

        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("dateCondition");
        conditionType.setParameters(Collections.singletonList(new Parameter("testParam", "date", false)));

        Condition condition = createConditionWithParameter(
            "testParam", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertEquals(now, resolved.getParameter("testParam"));
    }

    @Test
    public void testTypeCompatibilityInstantForDateParameter() {
        Instant instant = Instant.parse("2024-01-15T10:00:00Z");
        context.put("param1", instant);

        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("dateCondition");
        conditionType.setParameters(Collections.singletonList(new Parameter("testParam", "date", false)));

        Condition condition = createConditionWithParameter(
            "testParam", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertEquals(instant, resolved.getParameter("testParam"));
    }

    @Test
    public void testValidateParameterTypesDisabledSkipsValidation() {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Collections.singletonList(
            new Parameter("comparisonOperator", "comparisonOperator", false)));

        Map<String, Object> params = new HashMap<>();
        params.put("comparisonOperator", "equals");
        params.put("other", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "missing");

        Condition condition = new Condition(conditionType);
        condition.setParameterValues(params);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, false);

        assertNotNull(resolved);
        assertEquals("equals", resolved.getParameter("comparisonOperator"));
        assertNull(resolved.getParameter("other"));
    }

    // ========== Multivalued parameter validation ==========

    @Test
    public void testMultivaluedStringListResolvesReferencesWithoutCollectionWarning() {
        context.put("tagA", "news");
        context.put("tagB", "sports");

        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Collections.singletonList(
            new Parameter("propertyValues", "string", true)));

        List<Object> values = Arrays.asList(
            ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "tagA",
            ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "tagB",
            "static");
        Condition condition = createConditionWithParameter("propertyValues", values);
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        @SuppressWarnings("unchecked")
        List<Object> resolvedValues = (List<Object>) resolved.getParameter("propertyValues");
        assertEquals(Arrays.asList("news", "sports", "static"), resolvedValues);
    }

    @Test
    public void testMultivaluedStringListValidatesEachElement() {
        AtomicInteger validationCalls = new AtomicInteger();
        ValueTypeValidator stringValidator = new ValueTypeValidator() {
            @Override
            public String getValueTypeId() {
                return "string";
            }

            @Override
            public boolean validate(Object value) {
                validationCalls.incrementAndGet();
                return value instanceof String;
            }

            @Override
            public String getValueTypeDescription() {
                return "Value must be a string";
            }
        };

        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Collections.singletonList(
            new Parameter("propertyValues", "string", true)));

        List<Object> values = Arrays.asList("a", 42, "c");
        Condition condition = createConditionWithParameter("propertyValues", values);
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, null,
            Collections.singletonMap("string", stringValidator));

        assertNotNull(resolved);
        assertEquals(3, validationCalls.get());
    }

    @Test
    public void testMultivaluedScalarValueStillResolvesButIsInvalidShape() {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Collections.singletonList(
            new Parameter("propertyValues", "string", true)));

        Condition condition = createConditionWithParameter("propertyValues", "onlyOne");
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertEquals("onlyOne", resolved.getParameter("propertyValues"));
    }

    @Test
    public void testNonMultivaluedCollectionStillResolvesButIsInvalidShape() {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Collections.singletonList(
            new Parameter("propertyValue", "string", false)));

        Condition condition = createConditionWithParameter(
            "propertyValue", Collections.singletonList("unexpected-list"));
        condition.setConditionType(conditionType);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertEquals(Collections.singletonList("unexpected-list"), resolved.getParameter("propertyValue"));
    }

    @Test
    public void testMultivaluedConditionListValidatesEachElement() {
        AtomicInteger validationCalls = new AtomicInteger();
        ValueTypeValidator conditionValidator = new ValueTypeValidator() {
            @Override
            public String getValueTypeId() {
                return "condition";
            }

            @Override
            public boolean validate(Object value) {
                validationCalls.incrementAndGet();
                return value instanceof Condition;
            }

            @Override
            public String getValueTypeDescription() {
                return "Value must be a condition";
            }
        };

        ConditionType innerType = new ConditionType();
        innerType.setItemId("eventPropertyCondition");

        Condition sub1 = new Condition(innerType);
        Condition sub2 = new Condition(innerType);
        List<Object> subConditions = Arrays.asList(sub1, sub2, "not-a-condition");

        ConditionType booleanType = new ConditionType();
        booleanType.setItemId("booleanCondition");
        booleanType.setParameters(Collections.singletonList(
            new Parameter("subConditions", "Condition", true)));

        Condition condition = new Condition(booleanType);
        condition.setParameterValues(Collections.singletonMap("subConditions", subConditions));

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, null,
            Collections.singletonMap("condition", conditionValidator));

        assertNotNull(resolved);
        assertEquals(3, validationCalls.get());
        @SuppressWarnings("unchecked")
        List<Object> resolvedSubs = (List<Object>) resolved.getParameter("subConditions");
        assertEquals(3, resolvedSubs.size());
        assertSame(sub1, resolvedSubs.get(0));
        assertSame(sub2, resolvedSubs.get(1));
        assertEquals("not-a-condition", resolvedSubs.get(2));
    }

    @Test
    public void testMultivaluedConditionListTypeCheckWithoutCustomValidator() {
        ConditionType innerType = new ConditionType();
        innerType.setItemId("eventPropertyCondition");

        Condition sub = new Condition(innerType);
        List<Object> subConditions = Arrays.asList(sub, "invalid");

        ConditionType booleanType = new ConditionType();
        booleanType.setItemId("booleanCondition");
        booleanType.setParameters(Collections.singletonList(
            new Parameter("subConditions", "Condition", true)));

        Condition condition = new Condition(booleanType);
        condition.setParameterValues(Collections.singletonMap("subConditions", subConditions));

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, null, Collections.emptyMap());

        assertNotNull(resolved);
        @SuppressWarnings("unchecked")
        List<Object> resolvedSubs = (List<Object>) resolved.getParameter("subConditions");
        assertEquals(2, resolvedSubs.size());
        assertTrue(resolvedSubs.get(0) instanceof Condition);
        assertEquals("invalid", resolvedSubs.get(1));
    }

    @Test
    public void testInvalidComparisonOperatorStillResolvesWithValidator() {
        Condition condition = createEventPropertyConditionWithReference(
            "notARealOperator", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "eventTypeValue");
        context.put("eventTypeValue", "view");

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, null,
            Collections.singletonMap("comparisonoperator", comparisonOperatorValidator()));

        assertNotNull(resolved);
        assertEquals("notARealOperator", resolved.getParameter("comparisonOperator"));
    }

    @Test
    public void testExplicitEmptyValidatorMapDoesNotUseRegistry() {
        ValueTypeValidator validator = comparisonOperatorValidator();
        ValueTypeValidatorRegistry registry = new ValueTypeValidatorRegistry();
        registry.bindValidator(validator);
        try {
            Condition condition = createEventPropertyConditionWithReference(
                "equals", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "eventTypeValue");
            context.put("eventTypeValue", "view");

            Condition resolved = ConditionContextHelper.getContextualCondition(
                condition, context, scriptExecutor, true, null,
                Collections.emptyMap());

            assertNotNull(resolved);
            assertEquals("equals", resolved.getParameter("comparisonOperator"));
        } finally {
            registry.unbindValidator(validator);
        }
    }

    @Test
    public void testExplicitValidatorMapOverridesRegistry() {
        ValueTypeValidator registryValidator = comparisonOperatorValidator();
        ValueTypeValidatorRegistry registry = new ValueTypeValidatorRegistry();
        registry.bindValidator(registryValidator);
        try {
            Condition condition = createEventPropertyConditionWithReference(
                "equals", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "eventTypeValue");
            context.put("eventTypeValue", "view");

            ValueTypeValidator strictValidator = new ValueTypeValidator() {
                @Override
                public String getValueTypeId() {
                    return "comparisonOperator";
                }

                @Override
                public boolean validate(Object value) {
                    return false;
                }

                @Override
                public String getValueTypeDescription() {
                    return "always invalid";
                }
            };

            Condition resolved = ConditionContextHelper.getContextualCondition(
                condition, context, scriptExecutor, true, null,
                Collections.singletonMap("comparisonoperator", strictValidator));

            assertNotNull(resolved);
            assertEquals("equals", resolved.getParameter("comparisonOperator"));
        } finally {
            registry.unbindValidator(registryValidator);
        }
    }

    @Test
    public void testValidationMismatchTracedWhenTracingEnabled() {
        when(tracerService.isTracingEnabled()).thenReturn(true);
        when(tracerService.getCurrentTracer()).thenReturn(requestTracer);
        when(requestTracer.isEnabled()).thenReturn(true);

        context.put("param1", "notAnInteger");
        Condition condition = createIntegerParameterCondition(
            ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "param1");

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true, tracerService);

        assertNotNull(resolved);
        verify(requestTracer).trace(eq("Parameter type mismatch detected"), anyMap());
    }

    @Test
    public void testNullParameterValueSkipsTypeValidation() {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testCondition");
        conditionType.setParameters(Collections.singletonList(new Parameter("testParam", "integer", false)));

        Map<String, Object> params = new HashMap<>();
        params.put("testParam", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "missing");
        params.put("trigger", ConditionContextHelper.PARAMETER_REFERENCE_PREFIX + "alsoMissing");

        Condition condition = new Condition(conditionType);
        condition.setParameterValues(params);

        Condition resolved = ConditionContextHelper.getContextualCondition(
            condition, context, scriptExecutor, true);

        assertNotNull(resolved);
        assertNull(resolved.getParameter("testParam"));
    }

    // ========== Helper Methods ==========

    private Condition createIntegerParameterCondition(Object testParamValue) {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("testCondition");
        conditionType.setParameters(Collections.singletonList(new Parameter("testParam", "integer", false)));

        Condition condition = createConditionWithParameter("testParam", testParamValue);
        condition.setConditionType(conditionType);
        return condition;
    }

    private Condition createEventPropertyConditionWithReference(String comparisonOperator, Object propertyValue) {
        ConditionType conditionType = new ConditionType();
        conditionType.setItemId("eventPropertyCondition");
        conditionType.setParameters(Arrays.asList(
            new Parameter("propertyName", "string", false),
            new Parameter("comparisonOperator", "comparisonOperator", false),
            new Parameter("propertyValue", "string", false)));

        Condition condition = new Condition(conditionType);
        Map<String, Object> params = new HashMap<>();
        params.put("propertyName", "eventType");
        params.put("comparisonOperator", comparisonOperator);
        params.put("propertyValue", propertyValue);
        condition.setParameterValues(params);
        return condition;
    }

    private static ValueTypeValidator comparisonOperatorValidator() {
        return new ValueTypeValidator() {
            @Override
            public String getValueTypeId() {
                return "comparisonOperator";
            }

            @Override
            public boolean validate(Object value) {
                return "equals".equals(value);
            }

            @Override
            public String getValueTypeDescription() {
                return "Value must be a valid comparison operator";
            }
        };
    }

    private Condition createConditionWithParameter(String paramName, Object paramValue) {
        Condition condition = new Condition();
        Map<String, Object> paramValues = new HashMap<>();
        paramValues.put(paramName, paramValue);
        condition.setParameterValues(paramValues);
        return condition;
    }
}

