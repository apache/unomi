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
package org.apache.unomi.services.impl.profiles;

import org.apache.unomi.api.*;
import org.apache.unomi.api.services.ConditionValidationService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.ConditionEvaluatorDispatcher;
import org.apache.unomi.services.TestHelper;
import org.apache.unomi.services.common.security.ExecutionContextManagerImpl;
import org.apache.unomi.services.common.security.KarafSecurityService;
import org.apache.unomi.services.impl.*;
import org.apache.unomi.services.impl.cache.MultiTypeCacheServiceImpl;
import org.apache.unomi.services.impl.definitions.DefinitionsServiceImpl;
import org.apache.unomi.services.common.security.AuditServiceImpl;
import org.apache.unomi.services.impl.validation.ConditionValidationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;

import java.net.URL;
import java.util.*;
import java.net.MalformedURLException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProfileServiceImplTest {

    private ProfileServiceImpl profileService;
    private TestTenantService tenantService;
    private PersistenceService persistenceService;
    private DefinitionsServiceImpl definitionsService;
    private TestBundleContext bundleContext;
    private ExecutionContextManagerImpl executionContextManager;
    private MultiTypeCacheServiceImpl multiTypeCacheService;
    private KarafSecurityService securityService;
    private AuditServiceImpl auditService;
    private org.apache.unomi.api.services.SchedulerService schedulerService;
    private ConditionValidationService conditionValidationService;

    private static final String TENANT_1 = "tenant1";
    private static final String SYSTEM_TENANT = "system";

    @BeforeEach
    public void setUp() {
        bundleContext = new TestBundleContext();
        tenantService = new TestTenantService();


        securityService = TestHelper.createSecurityService();
        executionContextManager = TestHelper.createExecutionContextManager(securityService);

        // Create tenants using TestHelper
        TestHelper.setupCommonTestData(tenantService);

        conditionValidationService = new ConditionValidationServiceImpl();

        // Set up condition evaluator dispatcher
        ConditionEvaluatorDispatcher conditionEvaluatorDispatcher = TestConditionEvaluators.createDispatcher();

        multiTypeCacheService = new MultiTypeCacheServiceImpl();

        // Set up persistence service
        persistenceService = new InMemoryPersistenceServiceImpl(executionContextManager, conditionEvaluatorDispatcher);

        // Set up bundle context with predefined data
        bundleContext = new TestBundleContext();
        Bundle systemBundle = mock(Bundle.class);
        when(systemBundle.getBundleContext()).thenReturn(bundleContext);
        when(systemBundle.getBundleId()).thenReturn(0L);
        when(systemBundle.getSymbolicName()).thenReturn("org.apache.unomi.predefined");
        bundleContext.addBundle(systemBundle);

        // Create scheduler service using TestHelper
        schedulerService = TestHelper.createSchedulerService("profile-service-scheduler-node", persistenceService, executionContextManager, bundleContext, null, -1, true, true);

        // Set up definitions service
        definitionsService = TestHelper.createDefinitionService(persistenceService, bundleContext, schedulerService, multiTypeCacheService, executionContextManager, tenantService, conditionValidationService);

        // Set up value types
        ValueType stringType = new ValueType();
        stringType.setId("string");
        definitionsService.setValueType(stringType);

        // Create predefined property types
        URL propertyTypesUrl = getClass().getResource("/META-INF/cxs/properties/predefined-properties.json");
        when(bundleContext.getBundle().findEntries("META-INF/cxs/properties", "*.json", true))
                .thenReturn(Collections.enumeration(Arrays.asList(propertyTypesUrl)));

        // Create predefined personas
        URL personasUrl = getClass().getResource("/META-INF/cxs/personas/predefined-personas.json");
        when(bundleContext.getBundle().findEntries("META-INF/cxs/personas", "*.json", true))
                .thenReturn(Collections.enumeration(Arrays.asList(personasUrl)));

        // Set up profile service
        profileService = new ProfileServiceImpl();
        profileService.setBundleContext(bundleContext);
        profileService.setPersistenceService(persistenceService);
        profileService.setDefinitionsService(definitionsService);
        profileService.setContextManager(executionContextManager);
        profileService.setSchedulerService(schedulerService);
        profileService.setCacheService(multiTypeCacheService);
        profileService.postConstruct();

        // Load predefined data
        profileService.bundleChanged(new BundleEvent(BundleEvent.STARTED, systemBundle));
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Use the common tearDown method from TestHelper
        org.apache.unomi.services.TestHelper.tearDown(
            schedulerService,
            multiTypeCacheService,
            persistenceService,
            tenantService,
            TENANT_1, SYSTEM_TENANT
        );

        // Clean up references using the helper method
        org.apache.unomi.services.TestHelper.cleanupReferences(
            tenantService, securityService, executionContextManager, profileService,
            persistenceService, definitionsService, bundleContext, schedulerService,
            conditionValidationService, multiTypeCacheService, auditService
        );
    }

    @Test
    public void testPredefinedPropertyTypes() {
        // Test
        Collection<PropertyType> result = profileService.getTargetPropertyTypes("profiles");

        // Verify predefined properties exist
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify specific predefined property
        Optional<PropertyType> firstNameProp = result.stream()
                .filter(p -> p.getItemId().equals("firstName"))
                .findFirst();
        assertTrue(firstNameProp.isPresent());
        assertEquals("string", firstNameProp.get().getValueTypeId());
        assertEquals("profiles", firstNameProp.get().getTarget());
        assertEquals(SYSTEM_TENANT, firstNameProp.get().getTenantId());
    }

    @Test
    public void testPropertyTypeByTag_CurrentTenant() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Setup
            PropertyType propertyType = createPropertyType("prop1", "test", Collections.singleton("tag1"), Collections.emptySet());
            profileService.setPropertyType(propertyType);

            // Test
            Set<PropertyType> result = profileService.getPropertyTypeByTag("tag1");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(TENANT_1, result.iterator().next().getTenantId());
        });
    }

    @Test
    public void testPropertyTypeByTag_SystemTenant() {
        // Setup
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemPropertyType = createPropertyType("systemProp", "test", Collections.singleton("systemTag"), Collections.singleton("systemTag"));
            profileService.setPropertyType(systemPropertyType);
            return null;
        });

        // Test from tenant context
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Collection<PropertyType> result = profileService.getPropertyTypeByTag("systemTag");

            // Verify
            assertNotNull(result);
            assertFalse(result.isEmpty());
            PropertyType foundType = result.iterator().next();
            assertEquals(SYSTEM_TENANT, foundType.getTenantId());
            assertEquals("systemProp", foundType.getItemId());
            return null;
        });
    }

    @Test
    public void testPropertyTypeByTag_TenantOverride() {
        // Setup system tenant property
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemProperty = createPropertyType("prop1", "system-version", Collections.singleton("tag1"), Collections.emptySet());
            profileService.setPropertyType(systemProperty);
            return null;
        });

        // Setup tenant property
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            PropertyType tenantProperty = createPropertyType("prop1", "tenant-version", Collections.singleton("tag1"), Collections.emptySet());
            profileService.setPropertyType(tenantProperty);

            // Test
            Set<PropertyType> result = profileService.getPropertyTypeByTag("tag1");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            PropertyType resultProp = result.iterator().next();
            assertEquals(TENANT_1, resultProp.getTenantId());
            assertEquals("tenant-version", resultProp.getMetadata().getName());
            return null;
        });
    }

    @Test
    public void testPropertyTypeBySystemTag_CurrentTenant() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Setup
            PropertyType propertyType = createPropertyType("prop1", "test", Collections.emptySet(), Collections.singleton("systag1"));
            profileService.setPropertyType(propertyType);

            // Test
            Set<PropertyType> result = profileService.getPropertyTypeBySystemTag("systag1");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(TENANT_1, result.iterator().next().getTenantId());
        });
    }

    @Test
    public void testPropertyTypeBySystemTag_SystemTenant() {
        // Setup system tenant property
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemProperty = createPropertyType("prop1", "test", Collections.emptySet(), Collections.singleton("systag1"));
            profileService.setPropertyType(systemProperty);
            return null;
        });

        // Test from tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Set<PropertyType> result = profileService.getPropertyTypeBySystemTag("systag1");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(SYSTEM_TENANT, result.iterator().next().getTenantId());
            return null;
        });
    }

    @Test
    public void testTargetPropertyTypes_CurrentTenant() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            // Setup
            PropertyType propertyType = createPropertyType("prop1", "test", Collections.emptySet(), Collections.emptySet());
            propertyType.setTarget("profile");
            profileService.setPropertyType(propertyType);

            // Test
            Collection<PropertyType> result = profileService.getTargetPropertyTypes("profile");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(TENANT_1, result.iterator().next().getTenantId());
        });
    }

    @Test
    public void testTargetPropertyTypes_SystemTenant() {
        // Setup system tenant property
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemProperty = createPropertyType("prop1", "test", Collections.emptySet(), Collections.emptySet());
            systemProperty.setTarget("profile");
            profileService.setPropertyType(systemProperty);
            return null;
        });

        // Test from tenant1
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Collection<PropertyType> result = profileService.getTargetPropertyTypes("profile");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(SYSTEM_TENANT, result.iterator().next().getTenantId());
            return null;
        });
    }

    @Test
    public void testTargetPropertyTypes_TenantOverride() {
        // Setup system tenant property
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemProperty = createPropertyType("prop1", "system-version", Collections.emptySet(), Collections.emptySet());
            systemProperty.setTarget("profile");
            profileService.setPropertyType(systemProperty);
            return null;
        });

        // Setup tenant property and test
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            PropertyType tenantProperty = createPropertyType("prop1", "tenant-version", Collections.emptySet(), Collections.emptySet());
            tenantProperty.setTarget("profile");
            profileService.setPropertyType(tenantProperty);

            // Test
            Collection<PropertyType> result = profileService.getTargetPropertyTypes("profile");

            // Verify
            assertNotNull(result);
            assertEquals(1, result.size());
            PropertyType resultProp = result.iterator().next();
            assertEquals(TENANT_1, resultProp.getTenantId());
            assertEquals("tenant-version", resultProp.getMetadata().getName());
            return null;
        });
    }

    @Test
    public void testExistingProperties_WithTag() {
        // Setup
        PropertyType propertyType = createPropertyType("prop1", "test", Collections.singleton("tag1"), Collections.emptySet());
        propertyType.setTarget("profiles");
        profileService.setPropertyType(propertyType);

        // Add mapping
        persistenceService.setPropertyMapping(propertyType, Profile.ITEM_TYPE);

        // Test
        Set<PropertyType> result = profileService.getExistingProperties("tag1", Profile.ITEM_TYPE);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("prop1", result.iterator().next().getItemId());
    }

    @Test
    public void testExistingProperties_WithSystemTag() {
        // Setup
        PropertyType propertyType = createPropertyType("prop1", "test", Collections.emptySet(), Collections.singleton("systag1"));
        propertyType.setTarget("profiles");
        profileService.setPropertyType(propertyType);

        // Add mapping
        Map<String, Map<String, Object>> mapping = new HashMap<>();
        Map<String, Object> properties = new HashMap<>();
        properties.put("properties", Collections.singletonMap("prop1", Collections.emptyMap()));
        mapping.put("properties", properties);
        persistenceService.setPropertyMapping(propertyType, Profile.ITEM_TYPE);

        // Test
        Set<PropertyType> result = profileService.getExistingProperties("systag1", Profile.ITEM_TYPE, true);

        // Verify
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("prop1", result.iterator().next().getItemId());
    }

    @Test
    public void testDeletePropertyType() {
        // Setup
        PropertyType propertyType = createPropertyType("prop1", "test", Collections.emptySet(), Collections.emptySet());
        profileService.setPropertyType(propertyType);

        // Test delete
        boolean result = profileService.deletePropertyType("prop1");

        // Verify
        assertTrue(result);
        assertNull(persistenceService.load("prop1", PropertyType.class));
    }

    private PropertyType createPropertyType(String id, String target, Set<String> tags, Set<String> systemTags) {
        PropertyType propertyType = new PropertyType();
        Metadata metadata = new Metadata();
        metadata.setId(id);
        metadata.setName(target);
        metadata.setTags(tags);
        metadata.setSystemTags(systemTags);
        propertyType.setMetadata(metadata);
        propertyType.setTarget(target);
        propertyType.setValueTypeId("string");
        return propertyType;
    }

    @Test
    public void testPersonaInheritance_CurrentTenant() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Persona persona = new Persona("test-persona");
            persistenceService.save(persona);

            // Test
            Persona result = profileService.loadPersona("test-persona");

            // Verify
            assertNotNull(result);
            assertEquals(TENANT_1, result.getTenantId());
            return null;
        });
    }

    @Test
    public void testPersonaInheritance_SystemTenant() {
        // Setup
        executionContextManager.executeAsSystem(() -> {
            Persona persona = new Persona("test-persona");
            persistenceService.save(persona);
            return null;
        });

        // Switch to tenant1 and test
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Persona result = profileService.loadPersona("test-persona");

            // Verify
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            return null;
        });
    }

    @Test
    public void testPersonaInheritance_TenantOverride() {
        // Setup system persona
        executionContextManager.executeAsSystem(() -> {
            Persona systemPersona = new Persona("test-persona");
            systemPersona.setProperty("version", "system");
            persistenceService.save(systemPersona);
            return null;
        });

        // Setup tenant persona and test
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Persona tenantPersona = new Persona("test-persona");
            tenantPersona.setProperty("version", "tenant");
            persistenceService.save(tenantPersona);

            // Test
            Persona result = profileService.loadPersona("test-persona");

            // Verify
            assertNotNull(result);
            assertEquals(TENANT_1, result.getTenantId());
            assertEquals("tenant", result.getProperty("version"));
            return null;
        });
    }

    @Test
    public void testPropertyTypeByTagInheritance_MergeResults() {
        // Setup system properties
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemOnlyProp = new PropertyType();
            systemOnlyProp.setMetadata(new Metadata("system-only-prop"));
            systemOnlyProp.setTarget("profiles");
            profileService.setPropertyType(systemOnlyProp);

            PropertyType systemOverrideProp = new PropertyType();
            systemOverrideProp.setMetadata(new Metadata("override-prop"));
            systemOverrideProp.setTarget("profiles");
            systemOverrideProp.getMetadata().setSystemTags(Collections.singleton("system"));
            profileService.setPropertyType(systemOverrideProp);
            return null;
        });

        // Setup tenant properties and test
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            PropertyType tenantOverrideProp = new PropertyType();
            tenantOverrideProp.setMetadata(new Metadata("override-prop"));
            tenantOverrideProp.setTarget("profiles");
            tenantOverrideProp.getMetadata().setSystemTags(Collections.singleton("tenant"));
            profileService.setPropertyType(tenantOverrideProp);

            PropertyType tenantOnlyProp = new PropertyType();
            tenantOnlyProp.setMetadata(new Metadata("tenant-only-prop"));
            tenantOnlyProp.setTarget("profiles");
            profileService.setPropertyType(tenantOnlyProp);

            // Test
            Collection<PropertyType> result = profileService.getTargetPropertyTypes("profiles");

            // Verify
            assertNotNull(result);
            assertEquals(4, result.size());  // Should have 4 unique properties (3 test properties + firstName)

            Map<String, PropertyType> resultMap = new HashMap<>();
            for (PropertyType prop : result) {
                resultMap.put(prop.getMetadata().getId(), prop);
            }

            assertTrue(resultMap.containsKey("system-only-prop"));
            assertTrue(resultMap.containsKey("override-prop"));
            assertTrue(resultMap.containsKey("tenant-only-prop"));
            assertTrue(resultMap.containsKey("firstName"));  // Predefined property

            // Verify the overridden property has tenant version
            assertTrue(resultMap.get("override-prop").getMetadata().getSystemTags().contains("tenant"));
            return null;
        });
    }

    @Test
    public void testPredefinedPersonas() {
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Persona result = profileService.loadPersona("testPersona");

            // Verify predefined persona exists
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertEquals("Test", result.getProperty("firstName"));
            assertEquals("Persona", result.getProperty("lastName"));
            assertEquals(30, result.getProperty("age"));
            assertTrue(((List<String>)result.getSystemProperties().get("systemTags")).contains("predefinedPersona"));
            return null;
        });
    }

    @Test
    public void testPersonaInheritance_SystemFallback() {
        // Setup system persona only
        executionContextManager.executeAsSystem(() -> {
            Persona systemPersona = new Persona();
            systemPersona.setItemId("systemOnlyPersona");
            systemPersona.setProperties(Collections.singletonMap("role", "system"));
            persistenceService.save(systemPersona);
            return null;
        });

        // Test from tenant context
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            Persona result = profileService.loadPersona("systemOnlyPersona");

            // Verify system persona is returned
            assertNotNull(result);
            assertEquals(SYSTEM_TENANT, result.getTenantId());
            assertEquals("system", result.getProperty("role"));
            return null;
        });
    }

    @Test
    public void testPropertyTypeBySystemTag_TenantOverride() {
        // Setup system property type
        executionContextManager.executeAsSystem(() -> {
            PropertyType systemPropertyType = createPropertyType("sharedProp", "test", Collections.singleton("sharedTag"), Collections.singleton("systemTag"));
            profileService.setPropertyType(systemPropertyType);
            return null;
        });

        // Setup tenant property type and test
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            PropertyType tenantPropertyType = createPropertyType("sharedProp", "test", Collections.singleton("tenantTag"), Collections.singleton("systemTag"));
            profileService.setPropertyType(tenantPropertyType);

            // Test
            Collection<PropertyType> result = profileService.getPropertyTypeBySystemTag("systemTag");

            // Verify tenant property type overrides system one
            assertNotNull(result);
            assertFalse(result.isEmpty());
            PropertyType foundType = result.iterator().next();
            assertEquals(TENANT_1, foundType.getTenantId());
            assertTrue(foundType.getMetadata().getTags().contains("tenantTag"));
            return null;
        });
    }

    @Test
    public void testPersonaWithSessions_SystemTenant() {
        // Setup system persona
        executionContextManager.executeAsSystem(() -> {
            Persona systemPersona = new Persona();
            systemPersona.setItemId("personaWithSessions");
            systemPersona.setProperties(Collections.singletonMap("role", "system"));
            persistenceService.save(systemPersona);
            return null;
        });

        // Test from tenant context
        executionContextManager.executeAsTenant(TENANT_1, () -> {
            PersonaWithSessions result = profileService.loadPersonaWithSessions("personaWithSessions");

            // Verify system persona is returned with sessions
            assertNotNull(result);
            assertNotNull(result.getPersona());
            assertEquals(SYSTEM_TENANT, result.getPersona().getTenantId());
            assertEquals("system", result.getPersona().getProperty("role"));
            return null;
        });
    }

    @Test
    public void testLoadPredefinedPropertyTypes() {
        // Setup a specific test bundle for this test
        Bundle testBundle = mock(Bundle.class);
        when(testBundle.getBundleContext()).thenReturn(bundleContext);
        when(testBundle.getBundleId()).thenReturn(123L);
        when(testBundle.getSymbolicName()).thenReturn("org.apache.unomi.test.properties");
        bundleContext.addBundle(testBundle);

        // Create a test property type JSON URL with a custom target in the path
        URL propertyTypeUrl = getClass().getResource("/META-INF/cxs/properties/predefined-properties.json");

        // Reset and set up the mock to return our test URL
        reset(bundleContext.getBundle());
        when(bundleContext.getBundle().findEntries("META-INF/cxs/properties", "*.json", true))
                .thenReturn(Collections.enumeration(Arrays.asList(propertyTypeUrl)));

        // Trigger the bundle event to load property types via the CacheableTypeConfig system
        profileService.bundleChanged(new BundleEvent(BundleEvent.STARTED, testBundle));

        // Verify property types were loaded correctly
        Collection<PropertyType> result = profileService.getTargetPropertyTypes("profiles");

        // Verify that the predefined property exists and has the correct target
        Optional<PropertyType> firstNameProp = result.stream()
                .filter(p -> p.getItemId().equals("firstName"))
                .findFirst();

        assertTrue(firstNameProp.isPresent());
        assertEquals("profiles", firstNameProp.get().getTarget());
        assertEquals("string", firstNameProp.get().getValueTypeId());

        // Direct test of the setPropertyTypeTarget method that's used by the URL-aware processor
        URL mockUrl;
        try {
            mockUrl = new URL("file:/path/to/META-INF/cxs/properties/customTarget/test-property.json");
            PropertyType testPropertyType = new PropertyType();
            testPropertyType.setMetadata(new Metadata("test-property"));
            testPropertyType.setTarget(""); // Empty target

            // Call the method directly to test target setting logic
            profileService.setPropertyTypeTarget(mockUrl, testPropertyType);

            // Verify the target was set correctly from the path
            assertEquals("customTarget", testPropertyType.getTarget());
        } catch (MalformedURLException e) {
            fail("Failed to create test URL: " + e.getMessage());
        }
    }

}
