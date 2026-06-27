<!--
  ~ Licensed to the Apache Software Foundation (ASF) under one or more
  ~ contributor license agreements.  See the NOTICE file distributed with
  ~ this work for additional information regarding copyright ownership.
  ~ The ASF licenses this file to You under the Apache License, Version 2.0
  ~ (the "License"); you may not use this file except in compliance with
  ~ the License.  You may obtain a copy of the License at
  ~
  ~      http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

Apache Unomi Integration Tests
=================================

## Overview

The integration tests verify Apache Unomi end-to-end: REST and GraphQL APIs, profile
resolution, segmentation, rules, event processing, JSON schema validation, authentication,
and data migration.

**How they work**

- **[Pax Exam](https://ops4j1.jira.com/wiki/spaces/PAXEXAM4/overview)** provisions and
  starts a real Karaf/OSGi container with all Unomi bundles installed.
- **Docker** starts a real Elasticsearch or OpenSearch instance (managed by the
  `docker-maven-plugin`).
- **Maven Failsafe** runs a single entry point — `AllITs` — which aggregates all test
  classes. Each test class extends `BaseIT`, which handles Karaf startup, OSGi service
  injection, and common test utilities.

A full IT run typically takes 20–30 minutes. The Karaf instance is created fresh for each
run under `itests/target/exam/` with a UUID directory name.

**Available tools in this directory**

| Script | Purpose |
|--------|---------|
| `build.sh` (project root) | Recommended way to run and configure IT runs |
| `kt.sh` | Live Karaf inspection during a run (log, tail, grep, debug) |
| `archive-it-run.sh` | Capture IT run artifacts before the next build wipes them |
| `compare-it-runs.sh` | Diff multiple captures to classify flaky vs systematic failures |
| `sample-it-memory.sh` | Memory sampling, summarize, operator note, and cross-platform verify |
| `jacoco-report.sh` | Generate a JaCoCo coverage report after a run |


---

## Memory sizing analysis

`./build.sh --integration-tests` starts a background memory sampler automatically.
Results land in `itests/target/`:

| File | Purpose |
|------|---------|
| `memory-samples.tsv` | Time-series samples (Karaf heap, search heap, Docker RSS, swap) |
| `memory-summary.txt` | Peak usage, headroom %, peak Karaf GCT (seconds), swap warnings |
| `memory-sampler.log` | Sampler diagnostics |

Disable sampling with `--no-memory-sampler`, or change the interval with
`--memory-interval 60` (default: 30s).

Each sample uses one header-aware `jstat -gc` attach (cached `MaxHeapSize` per Karaf PID,
with `jinfo`/`jcmd`/trace fallbacks), a filtered ES `/_nodes/stats/jvm` request, and
`docker stats` on the IT container only (`itests-elasticsearch` or `itests-opensearch`).
System metrics use `vm_stat`/`sysctl` on macOS and `free`/`/proc/loadavg` on Linux.

Swap warnings fire only when available RAM drops below 2 GB during the run or swap
grows by more than 256 MB (avoids false positives from baseline macOS swap).

Verify locally:

```bash
cd itests
./sample-it-memory.sh verify
```

Manual usage:

```bash
./itests/sample-it-memory.sh start --interval 30
./itests/sample-it-memory.sh stop    # writes memory-summary.txt
./itests/sample-it-memory.sh summarize
./itests/sample-it-memory.sh operator-note --print-only
```

After a run, `build.sh` writes `itests/target/it-run-operator-note.txt` with outcome,
heap config, test counts, and memory peaks. `archive-it-run.sh` uses that file as the
default operator note when you omit `-m` (override with `-m` or `--message-file` as before).

The archive also includes memory files and adds peak metrics to `run-summary.properties`.
On GitHub Actions, download the `it-memory-metrics-*` artifact from the workflow run.

Compare heap configurations across runs using `archives/runs-index.tsv` (configured
heaps + observed peaks from `memory.peak.*` fields in each capture).

---

## Writing Integration Tests

### Class structure

Every IT class must extend `BaseIT` and carry two Pax Exam annotations:

```java
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)   // one Karaf container shared across the whole suite
public class MyFeatureIT extends BaseIT {

    @Before
    public void setUp() throws InterruptedException {
        // create scopes, load fixtures, etc.
    }

    @After
    public void tearDown() {
        // delete everything your test created
    }

    @Test
    public void testSomething() throws Exception {
        // ...
    }
}
```

`BaseIT.waitForStartup()` runs before your `@Before` and handles Karaf startup, service
injection, and log-checker initialisation. Do not call it yourself.

### Available services

`BaseIT` injects all core Unomi services as protected fields. The most commonly used:

| Field | Service |
|-------|---------|
| `profileService` | Load, save, and query profiles |
| `eventService` | Send and query events |
| `segmentService` | Evaluate and manage segments |
| `rulesService` | Create, update, and refresh rules |
| `definitionsService` | Condition and action type definitions |
| `persistenceService` | Low-level index refresh and direct queries |
| `schemaService` | JSON schema registration and validation |
| `scopeService` | Scope lifecycle |
| `privacyService` | Anonymisation and deletion |

For any service not listed, use `getOsgiService(MyService.class, 60000)`.

### Polling helpers

Never use raw `Thread.sleep`. Use the polling helpers from `BaseIT`:

```java
// Wait until a condition becomes true — fails after retries
Profile p = keepTrying("Profile not found",
    () -> profileService.load(profileId),
    Objects::nonNull,
    DEFAULT_TRYING_TIMEOUT,   // 1000 ms between attempts
    DEFAULT_TRYING_TRIES);    // 10 retries max

// Wait until something is deleted / returns null
waitForNullValue("Profile still exists",
    () -> profileService.load(profileId),
    DEFAULT_TRYING_TIMEOUT, DEFAULT_TRYING_TRIES);

// Assert a condition stays true throughout the period (stability check)
shouldBeTrueUntilEnd("Segment membership changed unexpectedly",
    () -> segmentService.isProfileInSegment(profile, segmentId),
    result -> result,
    DEFAULT_TRYING_TIMEOUT, DEFAULT_SHOULDBETRUE_TRIES);  // 5 retries

// Convenience: wait for a specific profile property value
waitForProfileProperty(profileId, "email", "test@example.org");
```

### HTTP helpers

`BaseIT` provides thin wrappers around Apache HttpClient, pre-configured with auth:

```java
// GET — deserialises JSON response directly
Profile profile = get("/cxs/profiles/" + profileId, Profile.class);

// POST — body is a classpath resource path (under src/test/resources)
CloseableHttpResponse resp = post("/cxs/profiles/search", "queries/profile-search.json");

// POST with explicit content type
CloseableHttpResponse resp = post("/cxs/eventcollector", "events/view.json", JSON_CONTENT_TYPE);

// DELETE
CloseableHttpResponse resp = delete("/cxs/profiles/" + profileId);

// Full URL construction (port is wired automatically)
String url = getFullUrl("/cxs/profiles/" + profileId);
```

### Resource helpers

Place JSON fixtures under `src/test/resources/` and load them with:

```java
String json = resourceAsString("events/view-event.json");         // from bundle resources
String json = bundleResourceAsString("events/view-event.json");   // from OSGi bundle
```

### Log checking

After each test, `BaseIT` scans Karaf logs for unexpected ERROR/WARN lines. If your test
intentionally triggers errors (e.g. sending invalid input), suppress the expected noise:

```java
// Override in your class to declare expected patterns
@Override
protected LogChecker createLogChecker() {
    return LogChecker.builder()
        .addIgnoredSubstring("Response status code: 400")
        .addIgnoredMultiPart("Schema", "not found")
        .build();
}

// Or add patterns dynamically inside a test method
addIgnoredLogSubstring("invalid input for test");
```

### Best practices

- **Use unique IDs.** Prefix profile IDs, session IDs, and scope names with your test
  class name or a UUID to avoid collisions with other tests running in the same suite.
- **Never depend on another test.** Execution order is not guaranteed, even within the
  same class. Each test must create its own state.
- **Always clean up.** Delete what your test created in `@After`. At minimum use unique
  IDs so leftover data does not affect other tests.
- **Force index refresh when needed.** After writes, call
  `persistenceService.refresh()` or use `query.setForceRefresh(true)` before asserting
  on persistence-backed data.
- **Register your class in `AllITs`.** New test classes must be added to
  `AllITs.java` to be picked up by the Failsafe runner.

---

## Running Integration Tests

The recommended way is through `build.sh` from the project root:

```bash
# Run with Elasticsearch (default)
./build.sh --integration-tests

# Run with OpenSearch
./build.sh --integration-tests --use-opensearch

# Run a single test class
./build.sh --integration-tests --single-test org.apache.unomi.itests.BasicIT

# Run a specific test method
./build.sh --integration-tests --single-test org.apache.unomi.itests.ContextServletIT#testContextEndpointAuthentication
```

You can also invoke Maven directly from the project root:

```bash
# Run with Elasticsearch (default)
mvn clean install -P integration-tests

# Run with OpenSearch
# Use the property only — do not pass -P opensearch or !elasticsearch
mvn clean install -P integration-tests -Duse.opensearch=true

# Run a single test class
mvn clean install -P integration-tests -Dit.test=org.apache.unomi.itests.BasicIT

# Run a specific test method
mvn clean install -P integration-tests -Dit.test=org.apache.unomi.itests.ContextServletIT#testContextEndpointAuthentication

# Run all methods matching a pattern
mvn clean install -P integration-tests -Dit.test=org.apache.unomi.itests.ContextServletIT#test*Authentication*
```

See the [Maven Failsafe plugin docs](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/single-test.html) for more filtering options.

### Bypassing the Maven Build Cache

If a cached build is interfering with test execution, use `--purge-maven-cache` to wipe
the local Maven cache before building:

```bash
./build.sh --integration-tests --purge-maven-cache
```

This removes `~/.m2/build-cache`, `~/.m2/dependency-cache`, and
`~/.m2/dependency-cache_v2`. It cannot be combined with `--offline`.

---

## Debugging Integration Tests

### Attaching a remote debugger to the test JVM

Use `--it-debug` to enable remote debugging of the test code running inside Karaf.
Add `--it-debug-suspend` to pause Karaf until your debugger connects:

```bash
# Via build.sh — pause until debugger connects (port 5006)
./build.sh --integration-tests --it-debug --it-debug-suspend

# Custom port
./build.sh --integration-tests --it-debug --it-debug-suspend --it-debug-port 5008

# Via Maven (from project root)
mvn clean install -P integration-tests -Dit.karaf.debug=hold:true,port=5006
```

Connect your IDE remote debugger to `localhost:5006` (or whichever port you chose).

Supported `it.karaf.debug` parameters (comma-separated):

| Parameter    | `build.sh` equivalent   | Description                             |
|--------------|-------------------------|-----------------------------------------|
| `hold:true`  | `--it-debug-suspend`    | Pause until a debugger connects         |
| `hold:false` | `--it-debug` (default)  | Enable debug without pausing            |
| `port:XXXX`  | `--it-debug-port XXXX`  | Change the debug port (default: 5006)   |

### Karaf Resolver debug logging

To diagnose bundle refresh or feature installation issues:

```bash
# Via build.sh
./build.sh --integration-tests --resolver-debug

# Via Maven
mvn clean install -P integration-tests -Dit.unomi.resolver.debug=true
```

This enables DEBUG logging for `org.osgi.service.resolver`, `org.apache.karaf.features`,
`org.apache.karaf.resolver`, `org.osgi.framework`, and `org.osgi.service.packageadmin`.

### Live Karaf inspection with `kt.sh`

During a running test, the Karaf instance lives under `target/exam/` with a UUID directory
name. `kt.sh` locates it automatically and gives you convenient shortcuts:

```bash
cd itests
./kt.sh tail          # follow the log in real time
./kt.sh grep ERROR    # search for errors
./kt.sh log           # open the full log in less
./kt.sh dir           # print the path to the Karaf directory
./kt.sh pushd         # cd into the Karaf directory (use popd to return)
./kt.sh start         # start the Karaf instance
./kt.sh debug         # start Karaf in debug mode (port 5005)
./kt.sh console       # start Karaf in foreground console mode
./kt.sh stop          # stop the running Karaf instance
```

All commands have single-letter aliases (`t`, `g`, `l`, `i`, `p`, `s`, `d`, `c`, `x`).
Run `./kt.sh help` for the full list.

---

## Analyzing IT Run Failures

### Build trace

When you run integration tests via `build.sh`, a file is written to
`itests/target/it-run-trace.properties` capturing the exact Maven command, search engine,
heap sizes, flags, and timestamps. This lets you reproduce a reported failure precisely.

### Capturing a run for post-mortem analysis

After a test run, call `archive-it-run.sh` to snapshot the artifacts before the next build
wipes `itests/target/`:

```bash
cd itests
./archive-it-run.sh                                    # uses auto-generated operator note
./archive-it-run.sh -m "Heavy swap, 2 failures in GraphQLListIT"   # override auto note
./archive-it-run.sh --full-karaf                                    # include complete Karaf log
```

Each capture is saved under `itests/archives/it-run-YYYYMMDD-HHMMSS/` and includes:

- Failsafe and surefire reports
- Karaf log tail and a filtered error/warning extract (unexpected errors only)
- Engine logs (Elasticsearch / OpenSearch)
- Build trace and run context
- A `test-results.tsv` with one row per test (for cross-run comparison)

The archive also strips out expected Karaf noise — errors that tests deliberately trigger
(bad schemas, auth probes, invalid input) — so only genuine unexpected errors stand out.

The `itests/archives/` directory is gitignored.

### Comparing runs to distinguish flaky vs systematic failures

Once you have two or more captures, use `compare-it-runs.sh` to diff them:

```bash
cd itests
./compare-it-runs.sh --last 3
./compare-it-runs.sh archives/it-run-20260601-120000 archives/it-run-20260602-120000
```

Each test is classified as consistently failing, consistently passing, or flaky across the
selected runs.

---

## Coverage Report

To generate a JaCoCo coverage report after running integration tests:

```bash
cd itests
./jacoco-report.sh
```

The report is generated under `itests/target/site/jacoco/`.

---

## Migration Tests

Migration can be tested by restoring an Elasticsearch snapshot from an older Unomi version
and running the migration command.

The snapshot is unpacked by Maven during the build (via `maven-antrun-plugin`) and
Elasticsearch is configured to allow the snapshot repository:

```xml
<path.repo>${project.build.directory}/snapshots_repository</path.repo>
```

Provide a migration config file to avoid interactive prompts (in `BaseIT`):

```java
replaceConfigurationFile("etc/org.apache.unomi.migration.cfg",
    new File("src/test/resources/migration/org.apache.unomi.migration.cfg"))
```

Example config:

```properties
esAddress = http://localhost:9400
httpClient.trustAllCertificates = true
indexPrefix = context
```

Restore the snapshot and trigger migration in the first test of the suite:

```java
public class Migrate16xTo200IT extends BaseIT {

    @Override
    @Before
    public void waitForStartup() throws InterruptedException {
        try (CloseableHttpClient httpClient = HttpUtils.initHttpClient(true)) {
            // Create snapshot repo
            HttpUtils.executePutRequest(httpClient,
                "http://localhost:9400/_snapshot/snapshots_repository/",
                resourceAsString("migration/create_snapshots_repository.json"), null);
            // Verify snapshot exists
            String snapshot = HttpUtils.executeGetRequest(httpClient,
                "http://localhost:9400/_snapshot/snapshots_repository/snapshot_3", null);
            if (snapshot == null || !snapshot.contains("snapshot_3")) {
                throw new RuntimeException("Unable to retrieve 1.6.x snapshot for ES restore");
            }
            // Restore the snapshot
            HttpUtils.executePostRequest(httpClient,
                "http://localhost:9400/_snapshot/snapshots_repository/snapshot_3/_restore?wait_for_completion=true",
                "{}", null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executeCommand("unomi:migrate 1.6.0 true");
        super.waitForStartup();
    }

    @After
    public void cleanup() throws InterruptedException {
        // clean up data created by this test
    }

    @Test
    public void checkMigratedData() throws Exception {
        // call Unomi services to verify the migrated data is correct
    }
}
```

### Updating a migration snapshot

To modify an existing snapshot (e.g. `snapshot_3`, taken on Unomi 1.6.x / Elasticsearch 7.11.0):

1. Start Elasticsearch 7.11.0 with the snapshot repository path configured:

    **Docker (recommended):**
    ```bash
    docker run -p 9200:9200 \
      -e path.repo="/tmp/snapshots_repository" \
      -e discovery.type=single-node \
      docker.elastic.co/elasticsearch/elasticsearch:7.11.0
    ```

    **Local install:** edit `elasticsearch.yml` and add:
    ```yaml
    path:
      repo:
        - /path/to/snapshots_repository
    ```

2. Extract and copy the snapshot zip:

    **Docker:**
    ```bash
    docker cp src/test/resources/migration/snapshots_repository.zip \
      <container_id>:/tmp/snapshots_repository.zip
    # then inside the container:
    unzip /tmp/snapshots_repository.zip -d /tmp
    ```

    **Local install:** unzip `src/test/resources/migration/snapshots_repository.zip`
    to your configured `path.repo` directory.

3. Register the snapshot repository:

    ```
    PUT /_snapshot/snapshots_repository/
    { "type": "fs", "settings": { "location": "snapshots" } }
    ```

4. Verify and restore the snapshot:

    ```
    GET  /_snapshot/snapshots_repository/snapshot_3
    POST /_snapshot/snapshots_repository/snapshot_3/_restore?wait_for_completion=true
    {}
    ```

5. Configure Unomi to connect to your running Elasticsearch instance, then start the
   matching Unomi version. Once it is up, add or modify the data you want captured in
   the new snapshot:
   - create new events
   - create profiles with new properties to be migrated
   - create rules, segments, etc.

   **Important:** add to the existing data — do not remove existing items, as they are
   likely relied upon by current migration tests.

6. Delete and recreate the snapshot (check the Elasticsearch logs to confirm it completes):

    ```
    DELETE /_snapshot/snapshots_repository/snapshot_3
    PUT    /_snapshot/snapshots_repository/snapshot_3
    DELETE /_snapshot/snapshots_repository
    ```

7. Zip and replace in test resources:

    **Docker:**
    ```bash
    # inside the container
    zip -r /tmp/snapshots_repository.zip /tmp/snapshots_repository
    # copy back to host
    docker cp <container_id>:/tmp/snapshots_repository.zip \
      src/test/resources/migration/snapshots_repository.zip
    ```

    **Local install:**
    ```bash
    zip -r snapshots_repository.zip /path/to/snapshots_repository
    cp snapshots_repository.zip src/test/resources/migration/snapshots_repository.zip
    ```

8. Update the migration test class to verify that the data you added in step 5 is
   correctly migrated.

---

## Known Issues

**OpenSearch `QueryGroupTask` warnings**

OpenSearch test logs contain lines like:

    opensearch> [WARN][o.o.w.QueryGroupTask] QueryGroup _id can't be null ...

This is a known bug in OpenSearch 2.18 with no functional impact.
Tracked at: https://github.com/opensearch-project/OpenSearch/issues/16874
