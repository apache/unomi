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

# Apache Unomi Coding Guidelines

This document outlines coding conventions and guidelines for the Apache Unomi project. It covers both standard Java best practices and project-specific conventions.

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Naming Conventions](#naming-conventions)
3. [Code Patterns](#code-patterns)
4. [OSGi Service Implementation](#osgi-service-implementation)
5. [Maven POM Organization](#maven-pom-organization)
6. [Testing Guidelines](#testing-guidelines)
7. [Project Goals](#project-goals)

---

## Quick Reference

### Standard Java Best Practices

This project follows standard Java best practices. For comprehensive guidelines, refer to:
- [Oracle Java Code Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **Effective Java** by Joshua Bloch
- [Java Platform Documentation](https://docs.oracle.com/javase/)

**Key Principles**: Code clarity, consistency, documentation, proper error handling, resource management, immutability, thread safety.

### Checkstyle Rules

Key rules: JavaDoc for public classes/methods, remove unused imports, UPPER_CASE constants, no magic numbers (exceptions: -1, 0, 1, 2, 3, 17, 24, 31, 37, 60, 255, 256, 1000), always use braces, equals/hashCode together.

Run: `mvn clean install -P checkstyle`

---

## Naming Conventions

### Item Classes

All classes extending `org.apache.unomi.api.Item` **must** define:
```java
public static final String ITEM_TYPE = "myItem";
```

**Example**:
```java
public class Profile extends Item {
    public static final String ITEM_TYPE = "profile";
}
```

### Package Structure

- **API**: `org.apache.unomi.api.*` - Public APIs
- **Services**: `org.apache.unomi.services.impl.*` - Internal implementations
- **Extensions**: `org.apache.unomi.*.services.*` - Extension-specific services
- **Plugins**: `org.apache.unomi.plugin.*` - Plugin implementations

### Class Naming

- **Services**: `*Service` (interface), `*ServiceImpl` (implementation)
- **Actions**: `*ActionExecutor`
- **Conditions**: `*ConditionEvaluator`
- **Queries**: `*QueryBuilder`

---

## Code Patterns

### Logging

Use SLF4J with `LoggerFactory.getLogger(ClassName.class)` (not `.getName()`):

```java
private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);

LOGGER.info("Processing item: {}", itemId);  // Parameterized logging
LOGGER.error("Error processing item: {}", itemId, exception);  // Exception last
```

**Best Practices**: Include context (itemId, tenantId, eventType), use parameterized logging, pass exceptions as last parameter.

### Collections

- **Thread-safe**: `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `AtomicBoolean/Integer/Long`
- **Immutable**: `Collections.emptyList()`, `Collections.singletonList()`, `Collections.unmodifiableList()`
- **Lazy init**: `map.computeIfAbsent(key, k -> new ArrayList<>()).add(value)`
- **Initialization**: Use explicit constructors, set initial capacity when known

### Exception Handling

```java
try {
    // operation
} catch (Exception e) {
    LOGGER.error("Error processing item: {}", itemId, e);
    throw new ProcessingException("Failed to process item: " + itemId, e);
}
```

- Create custom exceptions for domain errors
- Always log with context
- Use `ExceptionMapper` for REST endpoints

### Null Checks and Validation

```java
Objects.requireNonNull(item, "Item cannot be null");
if (collection.isEmpty()) { /* ... */ }  // Not size() == 0
```

- Validate early (fail-fast)
- Use `Objects.requireNonNull()` for required parameters
- Check collections with `isEmpty()`

### Resource Management

```java
try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
    // use resource
}
```

- Always use try-with-resources for `AutoCloseable`
- Clean up in `@Deactivate`/`preDestroy()`: close trackers, shutdown executors, cancel timers

### Concurrency

- Use `ExecutorService` for async operations (shutdown in `@Deactivate`)
- Prefer `ConcurrentHashMap` over `synchronizedMap`
- Use `volatile` for simple flags, `AtomicReference` for object references
- Document thread-safety guarantees

### Execution Context

```java
contextManager.executeAsSystem(() -> { /* system operation */ });
contextManager.executeAsTenant(tenantId, () -> { /* tenant operation */ });
```

- Contexts are `ThreadLocal` - don't share across threads
- Always restore in `finally` blocks

### Service Lifecycle

**OSGi DS (preferred)**:
```java
@Component(service = MyService.class, configurationPid = "org.apache.unomi.myservice")
public class MyServiceImpl implements MyService {
    @Activate
    public void activate(MyServiceConfig config) { /* init */ }
    
    @Deactivate
    public void deactivate() { /* cleanup */ }
    
    @Modified
    public void modified(MyServiceConfig config) { /* config change */ }
}
```

**Blueprint (legacy)**: Use `postConstruct()` and `preDestroy()`.

### REST API Endpoints

```java
@Path("/profiles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Component(service = ProfileEndpoint.class, property = "osgi.jaxrs.resource=true")
public class ProfileEndpoint {
    @Reference
    private ProfileService profileService;
    
    @GET
    @Path("/{id}")
    public Profile getProfile(@PathParam("id") String id) {
        return profileService.load(id);
    }
}
```

### OSGi Service References

```java
@Reference(cardinality = ReferenceCardinality.MANDATORY)
private PersistenceService persistenceService;

@Reference(cardinality = ReferenceCardinality.OPTIONAL)
private MetricsService metricsService;

@Reference(cardinality = ReferenceCardinality.MULTIPLE)
private List<ActionExecutor> actionExecutors;
```

**Dynamic binding**: Use `bind`/`unbind` methods with `CopyOnWriteArrayList` or `ConcurrentHashMap`.

**Service Trackers**: Always close in `@Deactivate`:
```java
@Deactivate
public void deactivate() {
    if (serviceTracker != null) {
        serviceTracker.close();
        serviceTracker = null;
    }
}
```

### Serialization

- All `Item` subclasses must be `Serializable`
- Use `serialVersionUID` for version control
- Use `CustomObjectMapper` for Unomi-specific serialization
- Use `ItemDeserializer` for polymorphic Item deserialization

### Builder Patterns

- Use `ConditionBuilder` for complex condition trees
- Use `TaskBuilder` for scheduled tasks
- Return `this` from builder methods for chaining

### Metrics

```java
int result = new MetricAdapter<Integer>(metricsService, "ClassName.operation") {
    @Override
    public Integer execute(Object... args) throws Exception {
        return performOperation();
    }
}.runWithTimer();
```

- Check `metricsService != null && metricsService.isActivated()` before updating
- Use descriptive names: `ClassName.operationName`

### Validation Patterns

Use `ValidationError` with context (parameter name, condition ID, etc.):
```java
errors.add(new ValidationError(
    param.getId(),
    "Required parameter is missing",
    ValidationErrorType.MISSING_REQUIRED_PARAMETER,
    condition.getConditionTypeId(),
    type.getItemId(),
    context,
    null
));
```

### Query Building

- Register query builders with dispatcher
- Handle null conditions and missing builders gracefully
- Use thread-safe collections for query builder maps

### Graceful Shutdown

```java
private volatile boolean shutdownNow = false;

public void preDestroy() {
    shutdownNow = true;  // Set flag first
    // Cancel tasks, close trackers, shutdown executors
}

public void processItems() {
    while (!shutdownNow) {
        if (shutdownNow) return;  // Check flag in loop
        processItem(getNextItem());
    }
}
```

**Shutdown sequence**: Set flag → Cancel tasks → Close trackers → Shutdown executors → Release references → Clear collections.

---

## OSGi Service Implementation

### Migrate from Blueprint to DS Annotations

**Goal**: Use OSGi Declarative Services (DS) annotations instead of Blueprint XML.

**Benefits**: Type-safe references, better IDE support, reduced boilerplate, compile-time validation.

**Strategy**: New services use DS; migrate existing services gradually when modifying.

### Configuration Management

**CRITICAL**: Use OSGi Managed Services with `@Modified` for real-time updates (no restart required).

**Example**:
```java
@Component(service = WebConfig.class, configurationPid = "org.apache.unomi.web")
@Designate(ocd = WebConfig.Config.class)
public class WebConfig {
    @ObjectClassDefinition(name = "Apache Unomi Web Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Context Server Domain")
        String contextserver_domain() default "";
    }
    
    @Activate
    public void activate(Config config) { modified(config); }
    
    @Modified
    public void modified(Config config) {
        // Configuration updated in real-time without restart
        this.contextserverDomain = config.contextserver_domain();
    }
}
```

### Environment Variables

**CRITICAL**: All configuration must be wired to environment variables for Docker.

**Pattern** in `custom.system.properties`:
```properties
org.apache.unomi.my.property=${env:UNOMI_MY_PROPERTY:-defaultValue}
```

**Naming**: `UNOMI_{CATEGORY}_{PROPERTY_NAME}` (uppercase, underscores, replace dots with underscores).

**Example**:
```properties
org.osgi.service.http.port=${env:UNOMI_HTTP_PORT:-8181}
org.apache.unomi.elasticsearch.addresses=${env:UNOMI_ELASTICSEARCH_ADDRESSES:-localhost:9200}
```

**Docker**:
```bash
docker run -e UNOMI_HTTP_PORT=8080 -e UNOMI_ELASTICSEARCH_ADDRESSES=elasticsearch:9200 apache/unomi:latest
```

**Priority** (highest to lowest): Environment variables → Custom config files → Default config files.

**Real-time updates**: Environment variables set initial state; `@Modified` allows runtime changes without restart (for tests, future UI, operational flexibility).

**Adding new property**:
1. Add to `custom.system.properties` with `${env:UNOMI_*:-default}`
2. Add to OSGi service `@ObjectClassDefinition` interface
3. Document in user docs

---

## Maven POM Organization

### Dependency Order

1. **Unomi dependencies** (`org.apache.unomi.*`)
2. **Standard API Dependencies** (Java/Jakarta EE, OSGi, JSR/JCP specs)
3. **Libraries** (all other third-party)
4. **Test dependencies** (scope=test)

**Standard API Dependencies**: `javax.*`, `jakarta.*`, `org.osgi.*` packages (typically `scope=provided`).

**Example**:
```xml
<dependencies>
    <!-- Unomi dependencies -->
    <dependency>
        <groupId>org.apache.unomi</groupId>
        <artifactId>unomi-api</artifactId>
    </dependency>
    
    <!-- Standard API Dependencies -->
    <dependency>
        <groupId>org.osgi</groupId>
        <artifactId>osgi.core</artifactId>
        <scope>provided</scope>
    </dependency>
    
    <!-- Libraries -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Test dependencies -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Dependency Version Management

**CRITICAL**: Do NOT hardcode versions. All versions managed through:
1. **BOM** (`unomi-bom`) - third-party dependencies
2. **Root POM** - additional dependencies and properties
3. **BOM Artifacts** (`unomi-bom-artifacts`) - Unomi internal dependencies

**Adding a dependency**:
1. Check if managed in `bom/pom.xml` or `bom/artifacts/pom.xml`
2. If not, add version property to root `pom.xml`, then add to appropriate BOM
3. Use in module `pom.xml` **without** `<version>` tag

**Example**:
```xml
<!-- Root pom.xml -->
<properties>
    <my-library.version>1.2.3</my-library.version>
</properties>

<!-- bom/pom.xml -->
<dependencyManagement>
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>my-library</artifactId>
        <version>${my-library.version}</version>
    </dependency>
</dependencyManagement>

<!-- Module pom.xml -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>my-library</artifactId>
    <!-- NO <version> tag -->
</dependency>
```

**Exceptions**: Versions allowed only in root `pom.xml` properties, BOM files, and parent POM references.

**See also**: `DEPENDENCY_ORGANIZATION_REPORT.md`

---

## Testing Guidelines

### Unit Tests (JUnit 5)

**Framework**: JUnit 5 (Jupiter), Mockito with `@ExtendWith(MockitoExtension.class)`

**Conventions**:
- Use `org.junit.jupiter.api.*` exclusively
- Use `@BeforeEach`/`@AfterEach` for lifecycle
- Use `@Tag` instead of `@Category`
- Use `assertThrows()` instead of `@Test(expected = ...)`
- Assertions: message as last parameter, include context (itemId, tenantId, etc.)

**Example**:
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private PersistenceService persistenceService;
    
    @Test
    void testMethod() {
        String itemId = "test-item-123";
        when(persistenceService.load(itemId, MyItem.class)).thenReturn(new MyItem(itemId));
        
        MyItem result = myService.loadItem(itemId);
        
        assertNotNull(result, "Should load item with id: " + itemId);
        assertEquals(itemId, result.getItemId(), "Item ID should match");
    }
}
```

### Integration Tests (JUnit 4)

**Framework**: JUnit 4 (Pax Exam)

**Conventions**:
- Extend `org.apache.unomi.itests.BaseIT`
- **NEVER** create dependencies between tests
- Use `getOsgiService(ServiceClass.class, timeout)` for OSGi services
- Clean up test data in `@After`

**Example**:
```java
public class MyServiceIT extends BaseIT {
    private String testItemId;
    
    @Before
    public void setUp() {
        testItemId = "test-item-" + System.currentTimeMillis();
    }
    
    @Test
    public void testMyService() throws Exception {
        // Test implementation
    }
    
    @After
    public void tearDown() {
        if (testItemId != null) {
            try {
                persistenceService.remove(testItemId, MyItem.class);
            } catch (Exception e) {
                // Log but don't fail
            }
        }
    }
}
```

**Running**: `mvn clean install -P integration-tests` (or `-Duse.opensearch=true` for OpenSearch)

### Test Best Practices

**AVOID `Thread.sleep()`** - Use:
- **Awaitility** (unit tests): `await().atMost(10, TimeUnit.SECONDS).until(() -> condition)`
- **BaseIT helpers** (integration): `keepTrying("message", supplier, predicate, timeout, retries)`
- **CountDownLatch/CyclicBarrier** (thread synchronization)

**When `Thread.sleep()` is acceptable**: Only in helper methods implementing retry logic, testing time-based behavior, or as last resort (< 100ms with justification).

**Test Execution Time**:
- **Targets**: Unit tests < 1s (most < 100ms), Integration tests < 10s (most < 5s)
- **Strategies**: Use mocks, in-memory implementations, efficient waits, minimize I/O, parallelize when possible
- **Long tests**: Document reason, consider splitting, use `@Tag("slow")`, ensure value justifies time

**Performance and Reliability**:
- Use `ExecutorService` for concurrency tests (shutdown in teardown)
- Use fixed seeds for randomness: `new Random(42)`
- Use `@TempDir` (JUnit 5) or `Files.createTempDirectory()` (integration), always clean up
- Aggregate exceptions in concurrency tests, assert emptiness with list in message
- Use `TimeUnit` constants, not raw millisecond literals
- Assert on structured fields, not `toString()` (unless that's the behavior)
- Assert collection size before contents
- Compile regex patterns as `static final`
- No static mutable state (or reset in `@BeforeEach`/`@AfterEach`)
- Generate unique IDs per test, clean up data
- Prefer condition-based waits over global timeouts
- Isolate state for parallel execution

**See also**: `itests/README.md`

### JUnit 4 to JUnit 5 Migration Plan

**Status**: **NOT YET STARTED** - Planned migration for consistency.

**Key Differences**:
- Package: `org.junit.*` → `org.junit.jupiter.api.*`
- Lifecycle: `@Before`/`@After` → `@BeforeEach`/`@AfterEach`
- Assertions: Message first → Message last parameter
- Exceptions: `@Test(expected = ...)` → `assertThrows(...)`
- Categories: `@Category` → `@Tag`
- Runner: `@RunWith` → `@ExtendWith`

**Strategy**: Verify Pax Exam JUnit 5 support, migrate incrementally in separate branch/PR.

---

## Project Goals

### 1. Migrate from Blueprint to DS Annotations

**Goal**: All services use OSGi DS annotations.

**Strategy**: New services use DS; migrate existing when modifying.

### 2. Merge Plugins and Extensions

**Goal**: Consolidate `plugins/` and `extensions/` into unified extension mechanism.

**Rationale**: Reduces confusion, simplifies structure, easier to understand.

### 3. Prefer Plugins Over New Services

**Goal**: Use plugins for new functionality instead of core services.

**When to use plugins**: Custom actions/conditions, external integrations, domain-specific features, optional functionality.

**When to use core services**: Fundamental persistence, core event processing, essential profile/segment management, critical infrastructure.

### 4. Migrate Integration Tests to JUnit 5

**Status**: **NOT YET STARTED** - See [JUnit 4 to JUnit 5 Migration Plan](#junit-4-to-junit-5-migration-plan).

### 5. Increase Unit Test Coverage

**Focus**: Service implementations, complex business logic, utility classes, error handling paths.

**Strategy**: Add tests when modifying code, require tests for new features, use JaCoCo to track progress.

### 6. Standardize Logger Initialization

**Goal**: Use `LoggerFactory.getLogger(ClassName.class)` (not `.getName()`).

**Strategy**: New code uses standard form; update existing when modifying.

### 7. Code Quality and Maintainability

**Ongoing**: Reduce duplication, improve documentation, refactor complex methods, follow SOLID principles, keep dependencies updated, address technical debt incrementally.

---

## Additional Resources

- **Apache Unomi Website**: https://unomi.apache.org
- **Contribution Guidelines**: https://unomi.apache.org/contribute.html
- **JIRA Issue Tracker**: https://issues.apache.org/jira/browse/UNOMI
- **Integration Test README**: `itests/README.md`
- **Dependency Organization Report**: `DEPENDENCY_ORGANIZATION_REPORT.md`

---

## Questions or Suggestions?

1. Open a discussion on the Apache Unomi developer mailing list
2. Create a JIRA issue
3. Submit a pull request with proposed changes

---

*Last updated: November 2025*
