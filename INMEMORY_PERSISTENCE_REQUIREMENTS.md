<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Requirements: Making InMemoryPersistenceServiceImpl a Production-Ready Persistence Manager

## Overview

This document outlines what it would take to make `InMemoryPersistenceServiceImpl` (currently in `services/src/test/java/`) a full-blown persistence manager available as an alternative to Elasticsearch and OpenSearch persistence managers, while maintaining the ability for unit tests to instantiate it directly.

## Current State

- **Location**: `services/src/test/java/org/apache/unomi/services/impl/InMemoryPersistenceServiceImpl.java`
- **Status**: Test-only implementation
- **Implements**: `PersistenceService` interface
- **Key Features**: Already implements most PersistenceService methods, includes refresh delay simulation, tenant transformations, file storage

## Required Changes

### 1. Project Structure & Module Organization

#### 1.1 Create New Module Structure
```
persistence-inmemory/
├── pom.xml (parent)
├── core/
│   ├── pom.xml
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── org/apache/unomi/persistence/inmemory/
│   │   │   │       └── InMemoryPersistenceServiceImpl.java
│   │   │   └── resources/
│   │   │       └── org.apache.unomi.persistence.inmemory.cfg
│   │   └── test/
│   │       └── java/ (test utilities if needed)
└── target/
```

#### 1.2 Move Implementation Class
- Move `InMemoryPersistenceServiceImpl.java` from `services/src/test/java/` to `persistence-inmemory/core/src/main/java/org/apache/unomi/persistence/inmemory/`
- Update package name from `org.apache.unomi.services.impl` to `org.apache.unomi.persistence.inmemory`

#### 1.3 Keep Test Access
- **Option A (Recommended)**: Keep a test helper class in `services/src/test/java` that can instantiate the moved class directly
- **Option B**: Make the constructor public and accessible from tests
- **Option C**: Create a test-specific factory class

### 2. OSGi Service Registration with Declarative Services (DS) Annotations

#### 2.1 Implement OSGi DS Annotations
Use OSGi Declarative Services annotations instead of Blueprint XML for type-safe, modern OSGi integration.

**Required annotations:**
- `@Component` - Declares the component and its service interfaces
- `@Reference` - Declares service dependencies
- `@ReferenceCardinality` - For optional/multiple references
- `@Activate` - Component activation lifecycle
- `@Deactivate` - Component deactivation lifecycle
- `@Modified` - Configuration update handling
- `@Designate` - Links to configuration metadata
- `@ObjectClassDefinition` - Defines configuration properties

**Implementation:**
```java
package org.apache.unomi.persistence.inmemory;

import org.apache.unomi.api.services.ExecutionContextManager;
import org.apache.unomi.api.tenants.TenantTransformationListener;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.conditions.evaluator.ConditionEvaluatorDispatcher;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.SynchronousBundleListener;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component(
    service = {PersistenceService.class, SynchronousBundleListener.class},
    configurationPid = "org.apache.unomi.persistence.inmemory",
    immediate = true
)
@Designate(ocd = InMemoryPersistenceServiceImpl.Config.class)
public class InMemoryPersistenceServiceImpl 
    implements PersistenceService, SynchronousBundleListener {
    
    @ObjectClassDefinition(
        name = "Apache Unomi In-Memory Persistence Configuration",
        description = "Configuration for the in-memory persistence service"
    )
    public @interface Config {
        @AttributeDefinition(
            name = "Storage Directory",
            description = "Directory where persisted items are stored (if file storage is enabled)"
        )
        String storage_dir() default "data/persistence";
        
        @AttributeDefinition(
            name = "File Storage Enabled",
            description = "Whether to persist items to disk"
        )
        boolean fileStorage_enabled() default true;
        
        @AttributeDefinition(
            name = "Clear Storage on Init",
            description = "Whether to clear storage directory on initialization"
        )
        boolean clearStorage_onInit() default false;
        
        @AttributeDefinition(
            name = "Pretty Print JSON",
            description = "Whether to pretty print JSON in stored files"
        )
        boolean prettyPrintJson() default true;
        
        @AttributeDefinition(
            name = "Simulate Refresh Delay",
            description = "Whether to simulate Elasticsearch refresh delay behavior"
        )
        boolean simulateRefreshDelay() default true;
        
        @AttributeDefinition(
            name = "Refresh Interval (ms)",
            description = "Refresh interval in milliseconds when simulating refresh delay"
        )
        int refreshIntervalMs() default 1000;
        
        @AttributeDefinition(
            name = "Default Query Limit",
            description = "Default limit for queries"
        )
        int defaultQueryLimit() default 10;
        
        @AttributeDefinition(
            name = "Item Type to Refresh Policy",
            description = "Map of item types to refresh policies (JSON format)"
        )
        String itemTypeToRefreshPolicy() default "";
    }
    
    private ConditionEvaluatorDispatcher conditionEvaluatorDispatcher;
    private volatile ExecutionContextManager executionContextManager;
    private BundleContext bundleContext;
    
    // Configuration properties
    private String storageDir;
    private boolean fileStorageEnabled;
    private boolean clearStorageOnInit;
    private boolean prettyPrintJson;
    private boolean simulateRefreshDelay;
    private int refreshIntervalMs;
    private int defaultQueryLimit;
    private String itemTypeToRefreshPolicy;
    
    // Reference to ConditionEvaluatorDispatcher (required)
    @Reference
    public void setConditionEvaluatorDispatcher(ConditionEvaluatorDispatcher dispatcher) {
        this.conditionEvaluatorDispatcher = dispatcher;
    }
    
    public void unsetConditionEvaluatorDispatcher(ConditionEvaluatorDispatcher dispatcher) {
        this.conditionEvaluatorDispatcher = null;
    }
    
    // Reference to ExecutionContextManager (optional)
    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    public void setExecutionContextManager(ExecutionContextManager manager) {
        this.executionContextManager = manager;
    }
    
    public void unsetExecutionContextManager(ExecutionContextManager manager) {
        this.executionContextManager = null;
    }
    
    // Reference list for TenantTransformationListeners (optional, multiple)
    private final List<TenantTransformationListener> transformationListeners = 
        new CopyOnWriteArrayList<>();
    
    @Reference(
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    public void bindTransformationListener(TenantTransformationListener listener) {
        transformationListeners.add(listener);
        addTransformationListener(listener);
    }
    
    public void unbindTransformationListener(TenantTransformationListener listener) {
        transformationListeners.remove(listener);
        removeTransformationListener(listener);
    }
    
    // Component activation
    @Activate
    public void activate(BundleContext bundleContext, Config config) {
        this.bundleContext = bundleContext;
        modified(config);
        start();
    }
    
    // Configuration update
    @Modified
    public void modified(Config config) {
        this.storageDir = config.storage_dir();
        this.fileStorageEnabled = config.fileStorage_enabled();
        this.clearStorageOnInit = config.clearStorage_onInit();
        this.prettyPrintJson = config.prettyPrintJson();
        this.simulateRefreshDelay = config.simulateRefreshDelay();
        this.refreshIntervalMs = config.refreshIntervalMs();
        this.defaultQueryLimit = config.defaultQueryLimit();
        this.itemTypeToRefreshPolicy = config.itemTypeToRefreshPolicy();
        
        // Reinitialize if needed
        if (isActive()) {
            // Handle configuration changes at runtime
        }
    }
    
    // Component deactivation
    @Deactivate
    public void deactivate() {
        stop();
        this.bundleContext = null;
    }
    
    // Bundle lifecycle (SynchronousBundleListener)
    @Override
    public void bundleChanged(BundleEvent event) {
        // Handle bundle lifecycle events if needed
    }
    
    // Existing implementation methods...
    // (keep all existing PersistenceService implementation methods)
    
    private void start() {
        // Initialize service
        if (simulateRefreshDelay) {
            startRefreshThread();
        }
        if (fileStorageEnabled) {
            loadPersistedItems();
        }
    }
    
    private void stop() {
        shutdown();
    }
    
    private boolean isActive() {
        return bundleContext != null;
    }
}
```

#### 2.2 Maven Bundle Plugin Configuration
The Maven Bundle Plugin must be configured to process DS annotations:

**persistence-inmemory/core/pom.xml:**
```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <configuration>
        <instructions>
            <Export-Package>
                org.apache.unomi.persistence.inmemory
            </Export-Package>
            <_dsannotations>*</_dsannotations>
            <_dsannotations-options>inherit</_dsannotations-options>
            <_metatypeannotations>*</_metatypeannotations>
            <_metatypeannotations-options>version;nested</_metatypeannotations-options>
        </instructions>
    </configuration>
</plugin>
```

### 3. Configuration Management

#### 3.1 Configuration File (Optional)
Create `persistence-inmemory/core/src/main/resources/org.apache.unomi.persistence.inmemory.cfg` for default configuration:
```
storage.dir=data/persistence
fileStorage.enabled=true
clearStorage.onInit=false
prettyPrintJson=true
simulateRefreshDelay=true
refreshIntervalMs=1000
defaultQueryLimit=10
itemTypeToRefreshPolicy=
```

**Note**: With DS annotations, configuration is primarily managed through the `@ObjectClassDefinition` interface. The `.cfg` file provides default values that can be overridden via Configuration Admin or environment variables.

#### 3.2 Configuration Update Support
The `@Modified` annotation method handles runtime configuration changes automatically:

```java
@Modified
public void modified(Config config) {
    // Configuration is automatically injected as Config object
    this.storageDir = config.storage_dir();
    this.fileStorageEnabled = config.fileStorage_enabled();
    // ... update all properties
    
    // Handle runtime configuration changes
    if (isActive()) {
        // Reinitialize components that depend on configuration
        if (simulateRefreshDelay && refreshThread == null) {
            startRefreshThread();
        } else if (!simulateRefreshDelay && refreshThread != null) {
            stopRefreshThread();
        }
    }
}
```

**Benefits of DS annotations for configuration:**
- Type-safe configuration (no Dictionary casting)
- Compile-time validation
- IDE autocomplete support
- Automatic property name mapping (underscores to dots)
- Real-time updates via `@Modified` without restart

### 4. OSGi Service Dependencies

#### 4.1 ExecutionContextManager Integration
With DS annotations, service binding is handled automatically via `@Reference`:

```java
@Reference(cardinality = ReferenceCardinality.OPTIONAL)
public void setExecutionContextManager(ExecutionContextManager manager) {
    this.executionContextManager = manager;
}

public void unsetExecutionContextManager(ExecutionContextManager manager) {
    this.executionContextManager = null;
}
```

**Benefits:**
- Automatic service tracking (no manual ServiceTracker needed)
- Thread-safe binding/unbinding
- Optional cardinality allows service to start even if ExecutionContextManager is unavailable

#### 4.2 Tenant Transformation Listeners
Multiple listeners are handled via `@Reference` with `MULTIPLE` cardinality:

```java
@Reference(
    cardinality = ReferenceCardinality.MULTIPLE,
    policy = ReferencePolicy.DYNAMIC
)
public void bindTransformationListener(TenantTransformationListener listener) {
    transformationListeners.add(listener);
    addTransformationListener(listener);
}

public void unbindTransformationListener(TenantTransformationListener listener) {
    transformationListeners.remove(listener);
    removeTransformationListener(listener);
}
```

**Benefits:**
- Automatic tracking of multiple services
- Dynamic policy allows services to be added/removed at runtime
- No manual ServiceTracker or ServiceListener needed

### 5. Lifecycle Management

#### 5.1 Component Lifecycle with DS Annotations
With DS annotations, lifecycle is managed through `@Activate`, `@Modified`, and `@Deactivate`:

```java
@Activate
public void activate(BundleContext bundleContext, Config config) {
    this.bundleContext = bundleContext;
    modified(config);  // Apply configuration
    start();  // Initialize service
}

@Modified
public void modified(Config config) {
    // Update configuration properties
    // Handle runtime configuration changes
}

@Deactivate
public void deactivate() {
    stop();  // Cleanup and shutdown
    this.bundleContext = null;
}

private void start() {
    // Initialize service
    if (simulateRefreshDelay) {
        startRefreshThread();
    }
    if (fileStorageEnabled) {
        loadPersistedItems();
    }
}

private void stop() {
    // Shutdown refresh thread
    shutdown();
    // Optionally persist state
}
```

**Lifecycle sequence:**
1. `@Activate` called when component becomes satisfied (all required references available)
2. `@Modified` called when configuration changes (runtime updates)
3. `@Deactivate` called when component becomes unsatisfied or bundle stops

#### 5.2 Bundle Listener Implementation
The component implements `SynchronousBundleListener` as a service interface:

```java
@Override
public void bundleChanged(BundleEvent event) {
    // Handle bundle lifecycle events if needed
    // Typically used for cleanup or re-initialization
    // Note: This is registered as a service, not via manual registration
}
```

**Note**: With DS annotations, the component is automatically registered as a `SynchronousBundleListener` service (as declared in `@Component`), so no manual bundle context registration is needed.

### 6. Maven Configuration

#### 6.1 Parent POM
Create `persistence-inmemory/pom.xml`:
```xml
<project>
    <parent>
        <groupId>org.apache.unomi</groupId>
        <artifactId>unomi-root</artifactId>
        <version>3.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>unomi-persistence-inmemory</artifactId>
    <packaging>pom</packaging>
    <modules>
        <module>core</module>
    </modules>
</project>
```

#### 6.2 Core Module POM
Create `persistence-inmemory/core/pom.xml` with DS annotation dependencies:

```xml
<project>
    <parent>
        <groupId>org.apache.unomi</groupId>
        <artifactId>unomi-persistence-inmemory</artifactId>
        <version>3.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>unomi-persistence-inmemory-core</artifactId>
    <packaging>bundle</packaging>
    
    <dependencies>
        <!-- Unomi dependencies -->
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-persistence-spi</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-common</artifactId>
            <scope>provided</scope>
        </dependency>
        
        <!-- OSGi Declarative Services -->
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>org.osgi.service.component</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>org.osgi.service.component.annotations</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>org.osgi.service.metatype.annotations</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.osgi</groupId>
            <artifactId>osgi.core</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.felix</groupId>
                <artifactId>maven-bundle-plugin</artifactId>
                <configuration>
                    <instructions>
                        <Export-Package>
                            org.apache.unomi.persistence.inmemory
                        </Export-Package>
                        <Import-Package>
                            org.apache.unomi.*;resolution:=optional,
                            org.osgi.*;resolution:=optional,
                            *
                        </Import-Package>
                        <_dsannotations>*</_dsannotations>
                        <_dsannotations-options>inherit</_dsannotations-options>
                        <_metatypeannotations>*</_metatypeannotations>
                        <_metatypeannotations-options>version;nested</_metatypeannotations-options>
                        <Provide-Capability>
                            unomi.persistence;type=inmemory
                        </Provide-Capability>
                    </instructions>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**Key dependencies:**
- `org.osgi.service.component.annotations` - DS annotations (@Component, @Reference, @Activate, etc.)
- `org.osgi.service.metatype.annotations` - Metatype annotations (@ObjectClassDefinition, @AttributeDefinition, etc.)
- `org.osgi.service.component` - DS runtime API (provided by OSGi runtime)

**Maven Bundle Plugin configuration:**
- `_dsannotations=*` - Process all DS annotations
- `_metatypeannotations=*` - Process all metatype annotations
- These generate the component XML descriptor automatically

### 7. Constructor Refactoring

#### 7.1 Support Both Direct Instantiation and OSGi
The class needs to support:
- **Direct instantiation** (for tests): Keep existing constructors
- **OSGi DS injection** (for production): Configuration via `@Activate`/`@Modified`, services via `@Reference`

**Solution**: Keep constructors for tests, use DS annotations for OSGi:

```java
// Keep existing constructors for test instantiation
public InMemoryPersistenceServiceImpl(
    ExecutionContextManager executionContextManager, 
    ConditionEvaluatorDispatcher conditionEvaluatorDispatcher) {
    this.executionContextManager = executionContextManager;
    this.conditionEvaluatorDispatcher = conditionEvaluatorDispatcher;
    // Initialize with defaults for testing
    this.storageDir = "data/persistence";
    this.fileStorageEnabled = true;
    // ... etc
}

// For OSGi, configuration is injected via @Activate/@Modified methods
// Service dependencies are injected via @Reference methods
// No need for setter methods for configuration properties
```

**Key points:**
- **Configuration properties**: Handled via `@Activate`/`@Modified` methods with `Config` parameter (no setters needed)
- **Service dependencies**: Handled via `@Reference` methods (setter/unsetter pattern)
- **Test constructors**: Keep existing constructors for direct instantiation in tests
- **No Blueprint-style setters**: DS annotations eliminate the need for property setters

### 8. Missing PersistenceService Methods

Review and implement any missing methods from `PersistenceService` interface:
- `calculateStorageSize(String tenantId)` - Currently throws `UnsupportedOperationException`
- `getApiCallCount(String tenantId)` - Currently throws `UnsupportedOperationException`
- `migrateTenantData(...)` - Currently throws `UnsupportedOperationException`

**Decision needed**: Implement these methods or document why they're not applicable for in-memory storage.

### 9. Testing Considerations & Test Dependencies

#### 9.1 Test Dependencies Analysis

The test file `InMemoryPersistenceServiceImplTest.java` has several dependencies that need to be handled when moving the implementation to a new module:

**Direct Dependencies:**
1. **TestHelper** (`org.apache.unomi.services.TestHelper`)
   - Location: `services/src/test/java/org/apache/unomi/services/TestHelper.java`
   - Used by: Many test files across the services module
   - **Critical Issue**: Has hard dependency on `InMemoryPersistenceServiceImpl` (import, constant references, type checking)

2. **TestConditionEvaluators** (`org.apache.unomi.services.impl.TestConditionEvaluators`)
   - Location: `services/src/test/java/org/apache/unomi/services/impl/TestConditionEvaluators.java`
   - Used by: Multiple test files (EventServiceImplTest, RulesServiceImplTest, etc.)

3. **Test Item Classes**:
   - `SimpleItem` - `services/src/test/java/org/apache/unomi/services/impl/SimpleItem.java`
   - `NestedItem` - `services/src/test/java/org/apache/unomi/services/impl/NestedItem.java`
   - `TestMetadataItem` - Inner class in test file itself

4. **Test Infrastructure**:
   - `TestRequestTracer`, `TestTenantService`, `TestActionExecutorDispatcher`
   - `ExecutionContextManagerImpl` from `services-common` module

#### 9.2 Circular Dependency Problem

**Critical Issue**: `TestHelper` has hard references to `InMemoryPersistenceServiceImpl`:
- Direct import: `import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;`
- Constant reference: `InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR`
- Type checking: `instanceof InMemoryPersistenceServiceImpl`

If we move `InMemoryPersistenceServiceImpl` to `persistence-inmemory`, this creates a circular dependency:
- `services` tests → `persistence-inmemory` (for TestHelper)
- `persistence-inmemory` tests → `services` (for TestHelper)

**Solution**: Create a shared test utilities module that both can depend on, with refactored `TestHelper` that removes hard dependencies.

#### 9.3 Recommended Solution: Create Test Utilities Module

Create a new `test-utilities` module that both `services` and `persistence-inmemory` can depend on.

**Structure:**
```
test-utilities/
├── pom.xml
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/apache/unomi/test/
│   │           ├── TestHelper.java (refactored)
│   │           ├── TestConstants.java
│   │           ├── TestConditionEvaluators.java
│   │           ├── TestRequestTracer.java
│   │           ├── TestTenantService.java
│   │           ├── TestActionExecutorDispatcher.java
│   │           └── items/
│   │               ├── SimpleItem.java
│   │               ├── NestedItem.java
│   │               └── TestMetadataItem.java
│   └── test/
│       └── java/ (if needed)
```

**Key Refactoring Changes:**

1. **Extract Constants**:
   Create `org.apache.unomi.test.TestConstants.java`:
   ```java
   public class TestConstants {
       public static final String DEFAULT_STORAGE_DIR = "data/persistence";
   }
   ```

2. **Refactor TestHelper**:
   - Remove `import org.apache.unomi.services.impl.InMemoryPersistenceServiceImpl;`
   - Replace `InMemoryPersistenceServiceImpl.DEFAULT_STORAGE_DIR` with `TestConstants.DEFAULT_STORAGE_DIR`
   - Change type checks from `instanceof InMemoryPersistenceServiceImpl` to interface-based checks:
     ```java
     // Before:
     if (persistenceService instanceof InMemoryPersistenceServiceImpl) {
         ((InMemoryPersistenceServiceImpl) persistenceService).purge((Date)null);
     }
     
     // After:
     if (persistenceService != null) {
         persistenceService.purge((Date)null);
     }
     ```

3. **Move Test Item Classes**:
   - Move `SimpleItem`, `NestedItem` to `test-utilities`
   - Extract `TestMetadataItem` from inner class to separate file
   - Update package to `org.apache.unomi.test.items`

4. **Update Package Names**:
   - `org.apache.unomi.services.TestHelper` → `org.apache.unomi.test.TestHelper`
   - `org.apache.unomi.services.impl.TestConditionEvaluators` → `org.apache.unomi.test.TestConditionEvaluators`
   - Update all imports in test files

#### 9.4 Maven Configuration for Test Utilities

**test-utilities/pom.xml:**
```xml
<project>
    <parent>
        <groupId>org.apache.unomi</groupId>
        <artifactId>unomi-root</artifactId>
        <version>3.1.0-SNAPSHOT</version>
    </parent>
    <artifactId>unomi-test-utilities</artifactId>
    <packaging>jar</packaging>
    
    <dependencies>
        <!-- Core dependencies -->
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-api</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-persistence-spi</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-services-common</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.unomi</groupId>
            <artifactId>unomi-tracing-api</artifactId>
            <scope>provided</scope>
        </dependency>
        
        <!-- Test dependencies -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-core</artifactId>
            <scope>provided</scope>
        </dependency>
        
        <!-- Utilities -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>commons-beanutils</groupId>
            <artifactId>commons-beanutils</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

**Update Root POM:**
Add to root `pom.xml`:
```xml
<modules>
    ...
    <module>test-utilities</module>
    <module>persistence-inmemory</module>
    ...
</modules>
```

**Update Services Module (test scope):**
```xml
<dependency>
    <groupId>org.apache.unomi</groupId>
    <artifactId>unomi-test-utilities</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
```

**Update Persistence-InMemory Module (test scope):**
```xml
<dependency>
    <groupId>org.apache.unomi</groupId>
    <artifactId>unomi-test-utilities</artifactId>
    <version>${project.version}</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.apache.unomi</groupId>
    <artifactId>unomi-services-common</artifactId>
    <scope>test</scope>
</dependency>
```

#### 9.5 Test File Migration

**Move Test File:**
- Move `InMemoryPersistenceServiceImplTest.java` from `services/src/test/java/` to `persistence-inmemory/core/src/test/java/org/apache/unomi/persistence/inmemory/`
- Update package to `org.apache.unomi.persistence.inmemory`
- Update imports to use `org.apache.unomi.test.*` instead of `org.apache.unomi.services.*`

**Update All Test Files:**
Update all test files that use TestHelper:
- `EventServiceImplTest`
- `RulesServiceImplTest`
- `SegmentServiceImplTest`
- `GoalsServiceImplTest`
- `ClusterServiceImplTest`
- `ConditionValidationServiceImplTest`
- `SchedulerServiceImplTest`
- And any others

#### 9.6 Dependency Graph (No Circular Dependencies)

```
services (main)
  └─> test-utilities (test) [uses interfaces only]
  
persistence-inmemory (main)
  └─> test-utilities (test) [uses interfaces only]
  └─> services-common (test) [for ExecutionContextManagerImpl]
  
test-utilities (main)
  └─> api (provided)
  └─> persistence-spi (provided)
  └─> services-common (provided)
  └─> tracing-api (provided)
```

**No circular dependencies!** Test-utilities only uses interfaces, not implementations.

#### 9.7 Alternative: Minimal Refactoring Approach

If creating a new module is too much work initially:

1. **Keep test utilities in services** (temporarily)
2. **Use test-jar** to make them available to persistence-inmemory
3. **Refactor TestHelper** to remove hard dependency on InMemoryPersistenceServiceImpl
4. **Move test file** to persistence-inmemory but keep utilities in services

**Pros:**
- Less initial work
- Can migrate to test-utilities module later

**Cons:**
- Creates dependency: persistence-inmemory → services (test scope)
- Test utilities conceptually in wrong place
- Harder to reuse in other modules

### 10. Feature Integration

#### 10.1 Karaf Feature
Add to `kar/src/main/feature/feature.xml`:
```xml
<feature name="unomi-persistence-inmemory" version="${project.version}">
    <feature>unomi-persistence-spi</feature>
    <bundle>mvn:org.apache.unomi/unomi-persistence-inmemory-core/${project.version}</bundle>
</feature>
```

#### 10.2 Documentation
- Update README with in-memory persistence option
- Document configuration properties
- Document use cases (development, testing, small deployments)

### 11. Code Quality & Production Readiness

#### 11.1 Error Handling
- Add proper exception handling for file operations
- Add validation for configuration values
- Add logging for important operations

#### 11.2 Thread Safety
- Verify all concurrent operations are thread-safe
- Review `ConcurrentHashMap` usage
- Ensure refresh thread synchronization is correct

#### 11.3 Resource Management
- Ensure proper cleanup in `stop()` method
- Handle file locks properly
- Clean up temporary resources

### 12. Optional Enhancements

#### 12.1 Metrics Integration
Consider integrating with `MetricsService` (like ES/OS implementations):
```java
private MetricsService metricsService;

public void setMetricsService(MetricsService metricsService) {
    this.metricsService = metricsService;
}
```

#### 12.2 Health Check Integration
Implement health check support for monitoring.

## Implementation Checklist

### Test Utilities Module
- [ ] Create `test-utilities` module structure
- [ ] Create `pom.xml` with dependencies
- [ ] Extract `TestConstants` class
- [ ] Refactor `TestHelper` to remove `InMemoryPersistenceServiceImpl` dependency
- [ ] Move `TestConditionEvaluators` to test-utilities
- [ ] Move `TestRequestTracer` to test-utilities
- [ ] Move `TestTenantService` to test-utilities
- [ ] Move `TestActionExecutorDispatcher` to test-utilities
- [ ] Move `SimpleItem` to test-utilities
- [ ] Move `NestedItem` to test-utilities
- [ ] Extract `TestMetadataItem` from inner class
- [ ] Update package names
- [ ] Add to root POM

### Services Module Updates
- [ ] Add `unomi-test-utilities` dependency (test scope)
- [ ] Update all test file imports
- [ ] Update `TestHelper` usage in tests
- [ ] Remove old test utility files
- [ ] Verify all tests still pass

### Persistence-InMemory Module
- [ ] Create `persistence-inmemory` module structure
- [ ] Move implementation class to new location
- [ ] Update package name
- [ ] Add OSGi DS annotation dependencies to POM
- [ ] Add `@Component` annotation with service interfaces
- [ ] Add `@ObjectClassDefinition` interface for configuration
- [ ] Add `@Designate` annotation linking to config
- [ ] Add `@Reference` annotations for service dependencies
- [ ] Add `@Activate` method for component initialization
- [ ] Add `@Modified` method for configuration updates
- [ ] Add `@Deactivate` method for component cleanup
- [ ] Implement `SynchronousBundleListener` interface
- [ ] Create configuration file (`.cfg`) - optional defaults
- [ ] Configure Maven Bundle Plugin for DS annotations
- [ ] Create Maven POMs (parent + core) with DS dependencies
- [ ] Update root POM to include new module
- [ ] Add `unomi-test-utilities` dependency (test scope)
- [ ] Add `unomi-services-common` dependency (test scope)
- [ ] Move test file to new location
- [ ] Update package and imports in test file
- [ ] Add to Karaf feature
- [ ] Implement missing PersistenceService methods (or document why not)
- [ ] Add comprehensive error handling
- [ ] Review thread safety
- [ ] Add documentation
- [ ] Test OSGi deployment
- [ ] Test direct instantiation (for unit tests)
- [ ] Verify all tests pass

## Key Design Decisions

1. **Dual Mode Support**: The class must support both direct instantiation (tests) and OSGi DS injection (production). This is achieved by keeping constructors for tests and using DS annotations (`@Activate`, `@Modified`, `@Reference`) for OSGi.

2. **Package Location**: Move from `services.impl` to `persistence.inmemory` to match the pattern of ES/OS implementations.

3. **OSGi Declarative Services**: Use DS annotations instead of Blueprint XML for modern, type-safe OSGi integration. Benefits include compile-time validation, better IDE support, and reduced boilerplate.

4. **Test Utilities Module**: Create a shared `test-utilities` module to avoid circular dependencies. This module contains refactored `TestHelper` (without hard dependencies on `InMemoryPersistenceServiceImpl`), test item classes, and other shared test utilities. Both `services` and `persistence-inmemory` modules depend on it at test scope.

5. **TestHelper Refactoring**: Remove hard dependency on `InMemoryPersistenceServiceImpl` by:
   - Extracting constants to `TestConstants` class
   - Using `PersistenceService` interface instead of concrete class
   - Removing type-specific checks that require concrete class

6. **Test Access**: Maintain test access through direct instantiation (tests can still instantiate the class directly) and shared test utilities, ensuring tests don't break.

7. **Configuration**: Use OSGi Configuration Admin with `@ObjectClassDefinition` and `@Modified` for type-safe, runtime configuration updates without restart.

8. **Service Registration**: Register as OSGi service via `@Component` annotation with proper service interfaces and properties to allow selection between persistence implementations.

## Estimated Effort

- **Test Utilities Module**: 4-6 hours
  - Create module structure and POM
  - Refactor TestHelper to remove hard dependencies
  - Move test utility classes
  - Update package names
- **Refactoring TestHelper**: 2-3 hours
  - Extract constants
  - Remove InMemoryPersistenceServiceImpl dependencies
  - Update type checks
- **Updating All Test Files**: 3-4 hours
  - Update imports across all test files
  - Verify tests still pass
- **Persistence-InMemory Module Structure**: 2-4 hours
- **OSGi Integration**: 4-6 hours
- **Configuration Management**: 2-3 hours
- **Testing & Validation**: 4-6 hours
- **Documentation**: 2-3 hours
- **Total**: ~23-35 hours

## Risks & Considerations

1. **Breaking Tests**: Moving the class will break existing tests. Mitigation: Create test utilities module and update tests incrementally.

2. **Circular Dependency Risk**: `TestHelper` has hard dependency on `InMemoryPersistenceServiceImpl`. Mitigation: Refactor `TestHelper` to use interfaces and extract constants to shared `TestConstants` class.

3. **Test Utilities Migration**: Many test files depend on shared test utilities. Mitigation: Create `test-utilities` module that both `services` and `persistence-inmemory` can depend on, migrate incrementally.

4. **OSGi DS Annotations**: Using DS annotations is newer than Blueprint (used by ES/OS). Mitigation: Follow coding guidelines and existing DS annotation examples in the codebase. DS annotations provide better type safety and IDE support.

5. **Configuration Management**: Need to handle configuration updates at runtime. Mitigation: Use `@Modified` annotation method to handle configuration changes. DS annotations provide type-safe configuration via `@ObjectClassDefinition`.

6. **Performance**: In-memory storage may not scale for production. Mitigation: Document use cases clearly.

7. **Data Persistence**: File storage option exists but may not be as robust as ES/OS. Mitigation: Document limitations.

## Conclusion

Making `InMemoryPersistenceServiceImpl` a production-ready persistence manager is feasible and follows modern OSGi patterns. The main work involves:
1. Moving to proper module structure
2. Adding OSGi DS annotation-based service registration (modern approach)
3. Implementing lifecycle management with `@Activate`/`@Modified`/`@Deactivate`
4. Maintaining test compatibility

The implementation is already quite complete - it mainly needs OSGi DS integration and proper packaging. Using DS annotations instead of Blueprint provides better type safety, IDE support, and aligns with the project's coding guidelines for new services.


