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
package org.apache.unomi.services.impl.validation;

import org.apache.unomi.api.Metadata;
import org.apache.unomi.api.Parameter;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.conditions.ConditionValidation;
import org.apache.unomi.api.services.ConditionValidationService.ValidationError;
import org.apache.unomi.api.services.ConditionValidationService.ValidationErrorType;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.ValueTypeValidator;
import org.apache.unomi.api.tenants.TenantService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.tracing.api.TracerService;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.osgi.framework.BundleContext;

import java.util.*;

import static org.junit.Assert.*;

public class ConditionValidationServiceImplTest {

    private ConditionValidationServiceImpl conditionValidationService;

    private TracerService tracerService;
    private TenantService tenantService;
    private KarafSecurityService securityService;
    private ExecutionContextManagerImpl executionContextManager;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private PersistenceService persistenceService;
    private SchedulerService schedulerService;

    @Mock
    private BundleContext bundleContext;

    private static final String EVENT_CONDITION_TAG = "eventCondition";
    private static final String PROFILE_CONDITION_TAG = "profileCondition";
    private static final String SESSION_CONDITION_TAG = "sessionCondition";

    // Helper methods for creating common condition types
    private ConditionType createBooleanConditionType() {
        ConditionType type = new ConditionType(new Metadata());
        type.setItemId("booleanCondition");
        type.getMetadata().setSystemTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG, PROFILE_CONDITION_TAG)));

        // Add subConditions parameter with validation
        List<Parameter> parameters = new ArrayList<>();
        Parameter subConditionsParam = new Parameter("subConditions", "Condition", true);
        ConditionValidation validation = new ConditionValidation();
        validation.setRequired(true);
        // Allow both event and profile conditions in the subConditions
        validation.setAllowedConditionTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG, PROFILE_CONDITION_TAG)));
        subConditionsParam.setValidation(validation);
        parameters.add(subConditionsParam);
        type.setParameters(parameters);

        return type;
    }

    private ConditionType createProfilePropertyConditionType() {
        ConditionType type = new ConditionType(new Metadata());
        type.setItemId("profilePropertyCondition");
        type.getMetadata().setSystemTags(new HashSet<>(Collections.singletonList(PROFILE_CONDITION_TAG)));
        List<Parameter> parameters = new ArrayList<>();

        // Add parameters with proper validation
        Parameter propertyNameParam = new Parameter("propertyName", "string", false);
        ConditionValidation propertyNameValidation = new ConditionValidation();
        propertyNameValidation.setRequired(true);
        propertyNameParam.setValidation(propertyNameValidation);
        parameters.add(propertyNameParam);

        Parameter operatorParam = new Parameter("comparisonOperator", "comparisonOperator", false);
        ConditionValidation operatorValidation = new ConditionValidation();
        operatorValidation.setRequired(true);
        operatorParam.setValidation(operatorValidation);
        parameters.add(operatorParam);

        Parameter valueParam = new Parameter("propertyValue", "string", false);
        ConditionValidation valueValidation = new ConditionValidation();
        valueValidation.setRequired(true);
        valueParam.setValidation(valueValidation);
        parameters.add(valueParam);

        type.setParameters(parameters);
        return type;
    }

    private ConditionType createEventPropertyConditionType() {
        ConditionType type = new ConditionType(new Metadata());
        type.setItemId("eventPropertyCondition");
        type.getMetadata().setSystemTags(new HashSet<>(Collections.singletonList(EVENT_CONDITION_TAG)));
        List<Parameter> parameters = new ArrayList<>();

        // Add parameters with proper validation
        Parameter propertyNameParam = new Parameter("propertyName", "string", false);
        ConditionValidation propertyNameValidation = new ConditionValidation();
        propertyNameValidation.setRequired(true);
        propertyNameParam.setValidation(propertyNameValidation);
        parameters.add(propertyNameParam);

        Parameter operatorParam = new Parameter("comparisonOperator", "comparisonOperator", false);
        ConditionValidation operatorValidation = new ConditionValidation();
        operatorValidation.setRequired(true);
        operatorParam.setValidation(operatorValidation);
        parameters.add(operatorParam);

        Parameter valueParam = new Parameter("propertyValue", "string", false);
        ConditionValidation valueValidation = new ConditionValidation();
        valueValidation.setRequired(true);
        valueParam.setValidation(valueValidation);
        parameters.add(valueParam);

        type.setParameters(parameters);
        return type;
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        tracerService = TestHelper.createTracerService();
        tenantService = new TestTenantService();

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        // Set up bundle context using TestHelper
        bundleContext = TestHelper.createMockBundleContext();

        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService("condition-validation-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

        conditionValidationService = (ConditionValidationServiceImpl) TestHelper.createConditionValidationService();

    }

    @After
    public void tearDown() throws Exception {
        // Use the common tearDown method from TestHelper
        TestHelper.tearDown(
            schedulerService,
            multiTypeCacheService,
            persistenceService,
            tenantService
        );
        
        // Clean up references using the helper method
        TestHelper.cleanupReferences(
            tenantService, securityService, executionContextManager, conditionValidationService,
            persistenceService, schedulerService, multiTypeCacheService, bundleContext,
            tracerService
        );
    }

    private ConditionType createConditionType(String id, String... systemTags) {
        ConditionType type = new ConditionType(new Metadata());
        type.setItemId(id);
        type.getMetadata().setSystemTags(new HashSet<>(Arrays.asList(systemTags)));
        return type;
    }

    // Helper methods for test setup
    private ConditionType createConditionTypeWithParameter(String paramName, String paramType, boolean multivalued) {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();
        Parameter param = new Parameter(paramName, paramType, multivalued);
        parameters.add(param);
        type.setParameters(parameters);
        return type;
    }

    private ConditionType createConditionTypeWithValidation(String paramName, String paramType, ConditionValidation validation) {
        ConditionType type = createConditionTypeWithParameter(paramName, paramType, false);
        type.getParameters().get(0).setValidation(validation);
        return type;
    }

    private void assertSingleError(List<ValidationError> errors, String expectedParameterName) {
        assertEquals(1, errors.size());
        assertEquals(expectedParameterName, errors.get(0).getParameterName());
    }

    private void assertSingleErrorWithContext(List<ValidationError> errors, String expectedConditionId,
            String expectedParameterName, String expectedConditionTypeName) {
        assertEquals(1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(expectedConditionId, error.getConditionId());
        assertEquals(expectedParameterName, error.getParameterName());
        assertEquals(expectedConditionTypeName, error.getConditionTypeName());
    }

    private void assertNoErrors(List<ValidationError> errors) {
        if (!errors.isEmpty()) {
            System.out.println("Validation errors found:");
            for (ValidationError error : errors) {
                System.out.println("Detailed error: " + error.getDetailedMessage());
                System.out.println("---");
            }
        }
        assertTrue(errors.isEmpty());
    }

    private Condition createConditionWithValue(ConditionType type, String paramName, Object value) {
        Condition condition = new Condition(type);
        condition.setParameter(paramName, value);
        return condition;
    }

    @Test
    public void testNullCondition() {
        List<ValidationError> errors = conditionValidationService.validate(null);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.MISSING_REQUIRED_PARAMETER, errors.get(0).getType());
    }

    @Test
    public void testNullConditionType() {
        Condition condition = new Condition();
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_CONDITION_TYPE, errors.get(0).getType());
    }

    @Test
    public void testBasicTypeValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();
        parameters.add(new Parameter("stringParam", "string", false));
        parameters.add(new Parameter("intParam", "integer", false));
        type.setParameters(parameters);

        Condition condition = new Condition(type);
        condition.setParameter("stringParam", "test");
        condition.setParameter("intParam", 42);

        assertNoErrors(conditionValidationService.validate(condition));

        condition.setParameter("stringParam", 123);
        condition.setParameter("intParam", "not a number");

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.getType() == ValidationErrorType.INVALID_VALUE));
    }

    @Test
    public void testMultivaluedValidation() {
        ConditionType type = createConditionTypeWithParameter("tags", "string", true);

        // Test valid collection
        Condition condition = createConditionWithValue(type, "tags", Arrays.asList("tag1", "tag2"));
        assertNoErrors(conditionValidationService.validate(condition));

        // Test non-collection value
        condition = createConditionWithValue(type, "tags", "single value");
        assertSingleError(conditionValidationService.validate(condition), "tags");

        // Test invalid type in collection
        condition = createConditionWithValue(type, "tags", Arrays.asList("tag1", 123, "tag3"));
        assertSingleError(conditionValidationService.validate(condition), "tags[1]");
    }

    @Test
    public void testRequiredParameterValidation() {
        ConditionValidation validation = new ConditionValidation();
        validation.setRequired(true);
        ConditionType type = createConditionTypeWithValidation("required", "string", validation);

        // Test missing required parameter
        Condition condition = new Condition(type);
        assertSingleError(conditionValidationService.validate(condition), "required");

        // Test with required parameter
        condition = createConditionWithValue(type, "required", "value");
        assertNoErrors(conditionValidationService.validate(condition));
    }

    @Test
    public void testRecommendedParameterValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("recommended", "string", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setRecommended(true);
        param.setValidation(validation);
        parameters.add(param);
        type.setParameters(parameters);

        // Test missing recommended parameter
        Condition condition = new Condition(type);
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.MISSING_RECOMMENDED_PARAMETER, errors.get(0).getType());
    }

    @Test
    public void testAllowedValuesValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("operator", "string", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setAllowedValues(new HashSet<>(Arrays.asList("and", "or")));
        param.setValidation(validation);
        parameters.add(param);
        type.setParameters(parameters);

        // Test invalid value
        Condition condition = new Condition(type);
        condition.setParameter("operator", "not");
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());

        // Test valid value
        condition.setParameter("operator", "and");
        errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testConditionTagValidation() {
        // Create parent condition type that accepts only event conditions
        ConditionType parentType = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("subCondition", "Condition", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setAllowedConditionTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG)));
        param.setValidation(validation);
        parameters.add(param);
        parentType.setParameters(parameters);

        // Create sub-condition with wrong tag (profile condition)
        ConditionType profileType = createConditionType("profilePropertyCondition", PROFILE_CONDITION_TAG);
        Condition profileCondition = new Condition(profileType);

        // Test invalid condition tag
        Condition condition = new Condition(parentType);
        condition.setParameter("subCondition", profileCondition);
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_CONDITION_TYPE, errors.get(0).getType());

        // Test valid condition tag (event condition)
        ConditionType eventType = createConditionType("eventPropertyCondition", EVENT_CONDITION_TAG);
        Condition eventCondition = new Condition(eventType);
        condition.setParameter("subCondition", eventCondition);
        errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());

        // Test condition with multiple valid tags
        ConditionType booleanType = createConditionType("booleanCondition", EVENT_CONDITION_TAG, PROFILE_CONDITION_TAG);
        Condition booleanCondition = new Condition(booleanType);
        condition.setParameter("subCondition", booleanCondition);
        errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testDisallowedConditionTypesValidation() {
        // Create parent condition type that disallows certain condition types
        ConditionType parentType = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("subCondition", "Condition", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setDisallowedConditionTypes(new HashSet<>(Arrays.asList("booleanCondition")));
        param.setValidation(validation);
        parameters.add(param);
        parentType.setParameters(parameters);

        // Create allowed condition type
        ConditionType eventType = createEventPropertyConditionType();
        Condition eventCondition = new Condition(eventType);
        eventCondition.setParameter("propertyName", "test");
        eventCondition.setParameter("comparisonOperator", "equals");
        eventCondition.setParameter("propertyValue", "value");

        // Test with allowed condition type
        Condition parentCondition = new Condition(parentType);
        parentCondition.setParameter("subCondition", eventCondition);
        List<ValidationError> errors = conditionValidationService.validate(parentCondition);
        assertNoErrors(errors);

        // Test with disallowed condition type
        ConditionType booleanType = createBooleanConditionType();
        Condition booleanCondition = new Condition(booleanType);
        // Set required parameters for the boolean condition
        booleanCondition.setParameter("subConditions", Collections.singletonList(eventCondition));
        parentCondition.setParameter("subCondition", booleanCondition);
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_CONDITION_TYPE, errors.get(0).getType());
    }

    @Test
    public void testExclusiveParameterValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        // Create exclusive parameters
        Parameter stringParam = new Parameter("stringValue", "string", false);
        Parameter intParam = new Parameter("intValue", "integer", false);

        ConditionValidation validation1 = new ConditionValidation();
        validation1.setExclusive(true);
        validation1.setExclusiveGroup("value");
        stringParam.setValidation(validation1);

        ConditionValidation validation2 = new ConditionValidation();
        validation2.setExclusive(true);
        validation2.setExclusiveGroup("value");
        intParam.setValidation(validation2);

        parameters.add(stringParam);
        parameters.add(intParam);
        type.setParameters(parameters);

        // Test exclusive violation
        Condition condition = new Condition(type);
        condition.setParameter("stringValue", "test");
        condition.setParameter("intValue", 42);

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION, errors.get(0).getType());

        // Test valid exclusive parameters
        condition.setParameter("intValue", null);
        errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());
    }

    @Test
    public void testBackwardCompatibility() {
        // Create condition type without any validation rules
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("value", "string", false);
        parameters.add(param);
        type.setParameters(parameters);

        // Test with valid value
        Condition condition = new Condition(type);
        condition.setParameter("value", "test");
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());

        // Test with invalid value type
        condition.setParameter("value", 123);
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
    }

    @Test
    public void testNestedConditionValidation() {
        // Create parent condition type that accepts event conditions
        ConditionType parentType = new ConditionType(new Metadata());
        List<Parameter> parentParams = new ArrayList<>();

        Parameter subCondParam = new Parameter("subCondition", "Condition", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setRequired(true);
        validation.setAllowedConditionTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG)));
        subCondParam.setValidation(validation);
        parentParams.add(subCondParam);
        parentType.setParameters(parentParams);

        // Create child condition type with event tag
        ConditionType childType = createEventPropertyConditionType();

        // Create nested conditions
        Condition childCondition = new Condition(childType);
        childCondition.setParameter("propertyName", "test");
        childCondition.setParameter("comparisonOperator", "equals");
        childCondition.setParameter("propertyValue", "value");

        Condition parentCondition = new Condition(parentType);
        parentCondition.setParameter("subCondition", childCondition);

        // Test valid nested conditions
        List<ValidationError> errors = conditionValidationService.validate(parentCondition);
        assertTrue(errors.isEmpty());

        // Test missing required parameter in child
        childCondition.setParameter("propertyName", null);
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.MISSING_REQUIRED_PARAMETER, errors.get(0).getType());

        // Test invalid condition tag
        ConditionType profileType = createProfilePropertyConditionType();
        Condition profileCondition = new Condition(profileType);
        profileCondition.setParameter("propertyName", "test");
        profileCondition.setParameter("comparisonOperator", "equals");
        profileCondition.setParameter("propertyValue", "value");
        parentCondition.setParameter("subCondition", profileCondition);
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_CONDITION_TYPE, errors.get(0).getType());
    }

    @Test
    public void testCustomTypeValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("customObject", "object", false);
        ConditionValidation validation = new ConditionValidation();
        validation.setCustomType(String.class);
        param.setValidation(validation);
        parameters.add(param);
        type.setParameters(parameters);

        // Test with valid custom type value
        Condition condition = new Condition(type);
        condition.setParameter("customObject", "test");
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());

        // Test with invalid custom type value
        condition.setParameter("customObject", 123);
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
    }

    @Test
    public void testSpecificTypeValidations() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter floatParam = new Parameter("floatValue", "float", false);
        Parameter doubleParam = new Parameter("doubleValue", "double", false);
        Parameter dateParam = new Parameter("dateValue", "Date", false);
        parameters.add(floatParam);
        parameters.add(doubleParam);
        parameters.add(dateParam);
        type.setParameters(parameters);

        Condition condition = new Condition(type);

        // Test valid values
        condition.setParameter("floatValue", 1.5f);
        condition.setParameter("doubleValue", 2.5d);
        condition.setParameter("dateValue", new Date());
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());

        // Test invalid float
        condition.setParameter("floatValue", "not a float");
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());

        // Test invalid double
        condition.setParameter("floatValue", 1.5f);
        condition.setParameter("doubleValue", "not a double");
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());

        // Test invalid date
        condition.setParameter("doubleValue", 2.5d);
        condition.setParameter("dateValue", "not a date");
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
    }

    @Test
    public void testInvalidConditionMetadata() {
        ConditionType parentType = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("subCondition", "condition", false);
        ConditionValidation validation = new ConditionValidation();
        param.setValidation(validation);
        parameters.add(param);
        parentType.setParameters(parameters);

        // Create sub-condition with null metadata
        ConditionType subType = new ConditionType(null);
        Condition subCondition = new Condition(subType);

        // Test condition with null metadata
        Condition condition = new Condition(parentType);
        condition.setParameter("subCondition", subCondition);
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
        assertEquals("Value must be a valid condition with a condition type and metadata", errors.get(0).getMessage());
    }

    @Test
    public void testContextInformation() {
        // Create condition with context-specific validation
        ConditionType conditionType = createProfilePropertyConditionType();
        Condition condition = new Condition(conditionType);
        // Only set propertyName, missing required comparisonOperator and propertyValue
        condition.setParameter("propertyName", "someValue");

        // Validate and check results
        List<ValidationError> errors = conditionValidationService.validate(condition);

        assertFalse("Should have validation errors", errors.isEmpty());
        assertTrue("Should have at least one error", errors.size() >= 1);
        ValidationError error = errors.get(0);
        assertEquals(ValidationErrorType.MISSING_REQUIRED_PARAMETER, error.getType());
        assertNotNull("Error should have context", error.getContext());
        assertTrue("Context should contain location information",
            error.getContext().containsKey("location") || error.getContext().containsKey("parameterType"));
    }

    @Test
    public void testValidatorBindingAndUnbinding() {
        // Create a test validator
        ValueTypeValidator testValidator = new ValueTypeValidator() {
            @Override
            public String getValueTypeId() {
                return "test";
            }

            @Override
            public boolean validate(Object value) {
                return value instanceof String;
            }

            @Override
            public String getValueTypeDescription() {
                return "Value must be a test string";
            }
        };

        // Test binding
        conditionValidationService.bindValidator(testValidator);

        // Create a condition using the test type
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();
        Parameter param = new Parameter("testValue", "test", false);
        parameters.add(param);
        type.setParameters(parameters);

        Condition condition = new Condition(type);
        condition.setParameter("testValue", "valid string");
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty());

        // Test invalid value
        condition.setParameter("testValue", 123);
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
        assertEquals("Value must be a test string", errors.get(0).getMessage());

        // Test unbinding
        conditionValidationService.unbindValidator(testValidator);
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
        assertEquals("No validator found for type: test", errors.get(0).getMessage());
    }

    @Test
    public void testAllBuiltInValidators() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        // Add parameters for all built-in types
        Map<String, Object> validValues = new HashMap<>();
        validValues.put("stringValue", "test");
        validValues.put("integerValue", 42);
        validValues.put("longValue", 123L);
        validValues.put("floatValue", 3.14f);
        validValues.put("doubleValue", 2.718);
        validValues.put("booleanValue", true);
        validValues.put("dateValue", new Date());
        validValues.put("operatorValue", "equals");

        // Add parameters and set valid values
        validValues.forEach((name, value) -> {
            String paramType = name.replace("Value", "").toLowerCase();
            // Special case for operator to use comparisonOperator type
            if (paramType.equals("operator")) {
                paramType = "comparisonOperator";
            }
            parameters.add(new Parameter(name, paramType, false));
        });
        type.setParameters(parameters);

        // Test valid values
        Condition condition = new Condition(type);
        validValues.forEach(condition::setParameter);
        assertNoErrors(conditionValidationService.validate(condition));

        // Test invalid values
        Map<String, Object> invalidValues = new HashMap<>();
        invalidValues.put("stringValue", 123);
        invalidValues.put("integerValue", "not a number");
        invalidValues.put("longValue", 3.14);
        invalidValues.put("floatValue", "not a float");
        invalidValues.put("doubleValue", "not a double");
        invalidValues.put("booleanValue", "not a boolean");
        invalidValues.put("dateValue", "not a date");
        invalidValues.put("operatorValue", "invalid");

        condition = new Condition(type);
        invalidValues.forEach(condition::setParameter);
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(invalidValues.size(), errors.size());
        assertTrue(errors.stream().allMatch(e -> e.getType() == ValidationErrorType.INVALID_VALUE));
    }

    @Test
    public void testUnknownTypeValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("value", "unknown_type", false);
        parameters.add(param);
        type.setParameters(parameters);

        Condition condition = new Condition(type);
        condition.setParameter("value", "any value");

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());
        assertEquals("No validator found for type: unknown_type", errors.get(0).getMessage());
    }

    @Test
    public void testMultivaluedWithMixedTypes() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("values", "integer", true);
        parameters.add(param);
        type.setParameters(parameters);

        // Test with mixed valid and invalid types
        Condition condition = new Condition(type);
        condition.setParameter("values", Arrays.asList(1, "not a number", 3, true));

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.getType() == ValidationErrorType.INVALID_VALUE));
    }

    @Test
    public void testObjectTypeWithoutCustomType() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("object", "object", false);
        // Note: no customType set in validation
        parameters.add(param);
        type.setParameters(parameters);

        Condition condition = new Condition(type);
        condition.setParameter("object", new Object());

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty()); // Should pass without customType validation
    }

    @Test
    public void testNullValueValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        Parameter param = new Parameter("value", "string", false);
        parameters.add(param);
        type.setParameters(parameters);

        Condition condition = new Condition(type);
        condition.setParameter("value", null);

        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertTrue(errors.isEmpty()); // Null values should be allowed unless required
    }

    @Test
    public void testDetailedErrorMessages() {
        // Create a condition with invalid parameter
        ConditionType conditionType = createProfilePropertyConditionType();
        Condition condition = new Condition(conditionType);
        condition.setParameter("propertyName", 123); // Integer when string expected

        // Validate and check results
        List<ValidationError> errors = conditionValidationService.validate(condition);

        assertFalse("Should have validation errors", errors.isEmpty());
        ValidationError error = errors.get(0);
        assertEquals(ValidationErrorType.INVALID_VALUE, error.getType());
        assertNotNull("Should have context information", error.getContext());
        assertTrue("Context should contain parameter type", error.getContext().containsKey("parameterType"));
    }

    @Test
    public void testNestedErrorReporting() {
        // Create parent condition
        ConditionType booleanType = createBooleanConditionType();
        Condition parentCondition = new Condition(booleanType);
        parentCondition.setConditionType(booleanType);

        // Create child condition with error
        ConditionType profileType = createProfilePropertyConditionType();
        Condition childCondition = new Condition(profileType);
        childCondition.setConditionType(profileType);

        // Set an invalid type for propertyName (integer instead of string)
        childCondition.setParameter("propertyName", 123);
        // Set other required parameters to avoid additional validation errors
        childCondition.setParameter("comparisonOperator", "equals");
        childCondition.setParameter("propertyValue", "test");

        // Set the child condition in the parent's subConditions parameter
        parentCondition.setParameter("subConditions", Collections.singletonList(childCondition));

        // Validate and check results
        List<ValidationError> errors = conditionValidationService.validate(parentCondition);

        assertEquals("Should have one validation error", 1, errors.size());
        ValidationError error = errors.get(0);
        assertEquals(ValidationErrorType.INVALID_VALUE, error.getType());
        assertNotNull("Error should have context", error.getContext());
        assertTrue("Context should contain location info", error.getContext().containsKey("location"));
    }

    @Test
    public void testMultivaluedConditionValidation() {
        // Create parent condition type that accepts multiple sub-conditions
        ConditionType parentType = createBooleanConditionType();
        Condition parentCondition = new Condition(parentType);

        // Create valid child conditions
        ConditionType profileType = createProfilePropertyConditionType();
        Condition childCondition1 = new Condition(profileType);
        childCondition1.setParameter("propertyName", "test1");
        childCondition1.setParameter("comparisonOperator", "equals");
        childCondition1.setParameter("propertyValue", "value1");

        Condition childCondition2 = new Condition(profileType);
        childCondition2.setParameter("propertyName", "test2");
        childCondition2.setParameter("comparisonOperator", "equals");
        childCondition2.setParameter("propertyValue", "value2");

        // Test with valid list of conditions
        parentCondition.setParameter("subConditions", Arrays.asList(childCondition1, childCondition2));
        List<ValidationError> errors = conditionValidationService.validate(parentCondition);
        assertNoErrors(errors);

        // Test with invalid condition in the list (missing required parameters)
        Condition invalidChildCondition = new Condition(profileType);
        parentCondition.setParameter("subConditions", Arrays.asList(childCondition1, invalidChildCondition));
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(3, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.getType() == ValidationErrorType.MISSING_REQUIRED_PARAMETER && e.getParameterName().equals("propertyName")));
        assertTrue(errors.stream().anyMatch(e -> e.getType() == ValidationErrorType.MISSING_REQUIRED_PARAMETER && e.getParameterName().equals("comparisonOperator")));
        assertTrue(errors.stream().anyMatch(e -> e.getType() == ValidationErrorType.MISSING_REQUIRED_PARAMETER && e.getParameterName().equals("propertyValue")));

        // Test with non-condition object in the list
        parentCondition.setParameter("subConditions", Arrays.asList(childCondition1, "not a condition"));
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());

        // Test with null in the list (should be allowed as per validator)
        parentCondition.setParameter("subConditions", Arrays.asList(childCondition1, null));
        errors = conditionValidationService.validate(parentCondition);
        assertNoErrors(errors);

        // Test with empty list (should fail as subConditions is required)
        parentCondition.setParameter("subConditions", Collections.emptyList());
        errors = conditionValidationService.validate(parentCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.MISSING_REQUIRED_PARAMETER, errors.get(0).getType());
    }

    @Test
    public void testMultivaluedParameterValidationForAllTypes() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        // Add multivalued parameters for all basic types
        parameters.add(new Parameter("strings", "string", true));
        parameters.add(new Parameter("integers", "integer", true));
        parameters.add(new Parameter("longs", "long", true));
        parameters.add(new Parameter("floats", "float", true));
        parameters.add(new Parameter("doubles", "double", true));
        parameters.add(new Parameter("booleans", "boolean", true));
        parameters.add(new Parameter("dates", "date", true));
        parameters.add(new Parameter("operators", "comparisonOperator", true));
        parameters.add(new Parameter("conditions", "condition", true));

        type.setParameters(parameters);
        Condition condition = new Condition(type);

        // Test valid values for each type
        Map<String, List<?>> validValues = new HashMap<>();
        validValues.put("strings", Arrays.asList("test1", "test2"));
        validValues.put("integers", Arrays.asList(1, 2, 3));
        validValues.put("longs", Arrays.asList(1L, 2L, 3L));
        validValues.put("floats", Arrays.asList(1.1f, 2.2f));
        validValues.put("doubles", Arrays.asList(1.1d, 2.2d));
        validValues.put("booleans", Arrays.asList(true, false));
        validValues.put("dates", Arrays.asList(new Date(), new Date()));
        validValues.put("operators", Arrays.asList("equals", "notEquals"));

        // Create valid conditions for the conditions list
        ConditionType profileType = createProfilePropertyConditionType();
        Condition subCondition1 = new Condition(profileType);
        subCondition1.setParameter("propertyName", "test1");
        subCondition1.setParameter("comparisonOperator", "equals");
        subCondition1.setParameter("propertyValue", "value1");

        Condition subCondition2 = new Condition(profileType);
        subCondition2.setParameter("propertyName", "test2");
        subCondition2.setParameter("comparisonOperator", "equals");
        subCondition2.setParameter("propertyValue", "value2");

        validValues.put("conditions", Arrays.asList(subCondition1, subCondition2));

        // Test all valid values
        validValues.forEach(condition::setParameter);
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertNoErrors(errors);

        // Test invalid values for each type
        Map<String, List<?>> invalidValues = new HashMap<>();
        invalidValues.put("strings", Arrays.asList("test", 123));
        invalidValues.put("integers", Arrays.asList(1, "not a number"));
        invalidValues.put("longs", Arrays.asList(1L, "not a long"));
        invalidValues.put("floats", Arrays.asList(1.1f, "not a float"));
        invalidValues.put("doubles", Arrays.asList(1.1d, "not a double"));
        invalidValues.put("booleans", Arrays.asList(true, "not a boolean"));
        invalidValues.put("dates", Arrays.asList(new Date(), "not a date"));
        invalidValues.put("operators", Arrays.asList("equals", "invalid_operator"));
        invalidValues.put("conditions", Arrays.asList(subCondition1, "not a condition"));

        // Test each invalid value type separately
        invalidValues.forEach((paramName, invalidList) -> {
            condition.setParameterValues(new LinkedHashMap<>()); // clear the previous parameters
            condition.setParameter(paramName, invalidList);
            List<ValidationError> paramErrors = conditionValidationService.validate(condition);
            assertEquals("Parameter " + paramName + " should have one error", 1, paramErrors.size());
            assertEquals(ValidationErrorType.INVALID_VALUE, paramErrors.get(0).getType());
        });
    }

    @Test
    public void testComplexExclusiveParameterValidation() {
        ConditionType type = new ConditionType(new Metadata());
        List<Parameter> parameters = new ArrayList<>();

        // Create parameters for first exclusive group (value group)
        Parameter stringParam = new Parameter("stringValue", "string", false);
        Parameter intParam = new Parameter("intValue", "integer", false);
        ConditionValidation validation1 = new ConditionValidation();
        validation1.setExclusive(true);
        validation1.setExclusiveGroup("value");
        stringParam.setValidation(validation1);
        ConditionValidation validation2 = new ConditionValidation();
        validation2.setExclusive(true);
        validation2.setExclusiveGroup("value");
        intParam.setValidation(validation2);

        // Create parameters for second exclusive group (operator group)
        Parameter equalsParam = new Parameter("equals", "string", false);
        Parameter rangeParam = new Parameter("range", "object", false);
        ConditionValidation validation3 = new ConditionValidation();
        validation3.setExclusive(true);
        validation3.setExclusiveGroup("operator");
        equalsParam.setValidation(validation3);
        ConditionValidation validation4 = new ConditionValidation();
        validation4.setExclusive(true);
        validation4.setExclusiveGroup("operator");
        rangeParam.setValidation(validation4);

        parameters.add(stringParam);
        parameters.add(intParam);
        parameters.add(equalsParam);
        parameters.add(rangeParam);
        type.setParameters(parameters);

        // Test valid combinations
        Condition condition = new Condition(type);
        condition.setParameter("stringValue", "test");
        condition.setParameter("equals", "value");
        List<ValidationError> errors = conditionValidationService.validate(condition);
        assertNoErrors(errors);

        // Test violation in first group
        condition.setParameter("intValue", 42);
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION, errors.get(0).getType());

        // Test violation in second group
        condition = new Condition(type);
        condition.setParameter("stringValue", "test");
        condition.setParameter("equals", "value");
        condition.setParameter("range", new Object());
        errors = conditionValidationService.validate(condition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION, errors.get(0).getType());

        // Test violations in both groups
        condition.setParameter("intValue", 42);
        errors = conditionValidationService.validate(condition);
        assertEquals(2, errors.size());
        assertTrue(errors.stream().allMatch(e -> e.getType() == ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION));
    }

    @Test
    public void testDeepNestedConditionValidation() {
        // Create a three-level deep condition structure with mixed validation rules

        // Level 3 (deepest) - Event property condition
        ConditionType eventType = createEventPropertyConditionType();
        Condition eventCondition = new Condition(eventType);
        eventCondition.setParameter("propertyName", "test");
        eventCondition.setParameter("comparisonOperator", "equals");
        eventCondition.setParameter("propertyValue", "value");

        // Level 2 - Boolean condition with exclusive parameters
        ConditionType booleanType = new ConditionType(new Metadata());
        List<Parameter> booleanParams = new ArrayList<>();

        Parameter operatorParam = new Parameter("operator", "string", false);
        Parameter subConditionsParam = new Parameter("subConditions", "Condition", true);

        ConditionValidation operatorValidation = new ConditionValidation();
        operatorValidation.setExclusive(true);
        operatorValidation.setExclusiveGroup("operator");
        operatorValidation.setAllowedValues(new HashSet<>(Arrays.asList("and", "or")));
        operatorParam.setValidation(operatorValidation);

        ConditionValidation subConditionsValidation = new ConditionValidation();
        subConditionsValidation.setRequired(true);
        subConditionsValidation.setAllowedConditionTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG)));
        subConditionsParam.setValidation(subConditionsValidation);

        booleanParams.add(operatorParam);
        booleanParams.add(subConditionsParam);
        booleanType.setParameters(booleanParams);
        booleanType.getMetadata().setSystemTags(new HashSet<>(Arrays.asList(EVENT_CONDITION_TAG)));

        Condition booleanCondition = new Condition(booleanType);
        booleanCondition.setParameter("operator", "and");
        booleanCondition.setParameter("subConditions", Collections.singletonList(eventCondition));

        // Level 1 (root) - Container condition with required and exclusive parameters
        ConditionType containerType = new ConditionType(new Metadata());
        List<Parameter> containerParams = new ArrayList<>();

        Parameter typeParam = new Parameter("type", "string", false);
        Parameter conditionParam = new Parameter("condition", "Condition", false);
        Parameter filterParam = new Parameter("filter", "string", false);

        ConditionValidation typeValidation = new ConditionValidation();
        typeValidation.setRequired(true);
        typeValidation.setAllowedValues(new HashSet<>(Arrays.asList("include", "exclude")));
        typeParam.setValidation(typeValidation);

        ConditionValidation conditionValidation = new ConditionValidation();
        conditionValidation.setExclusive(true);
        conditionValidation.setExclusiveGroup("content");
        conditionParam.setValidation(conditionValidation);

        ConditionValidation filterValidation = new ConditionValidation();
        filterValidation.setExclusive(true);
        filterValidation.setExclusiveGroup("content");
        filterParam.setValidation(filterValidation);

        containerParams.add(typeParam);
        containerParams.add(conditionParam);
        containerParams.add(filterParam);
        containerType.setParameters(containerParams);

        // Test valid deep nested structure
        Condition containerCondition = new Condition(containerType);
        containerCondition.setParameter("type", "include");
        containerCondition.setParameter("condition", booleanCondition);
        List<ValidationError> errors = conditionValidationService.validate(containerCondition);
        assertNoErrors(errors);

        // Test missing required parameter at root level
        containerCondition.setParameter("type", null);
        errors = conditionValidationService.validate(containerCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.MISSING_REQUIRED_PARAMETER, errors.get(0).getType());

        // Test exclusive parameter violation at root level
        containerCondition.setParameter("type", "include");
        containerCondition.setParameter("filter", "some filter");
        errors = conditionValidationService.validate(containerCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.EXCLUSIVE_PARAMETER_VIOLATION, errors.get(0).getType());

        // Test invalid value in middle level
        containerCondition.setParameter("filter", null);
        booleanCondition.setParameter("operator", "invalid");
        errors = conditionValidationService.validate(containerCondition);
        assertEquals(1, errors.size());
        assertEquals(ValidationErrorType.INVALID_VALUE, errors.get(0).getType());

        // Test missing required parameter in deepest level
        eventCondition.setParameter("propertyName", null);
        errors = conditionValidationService.validate(containerCondition);
        assertEquals(2, errors.size()); // One for invalid operator, one for missing propertyName
        assertTrue(errors.stream().anyMatch(e -> e.getType() == ValidationErrorType.MISSING_REQUIRED_PARAMETER));
    }
}
