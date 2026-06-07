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

# LLM analysis guide — Apache Unomi integration test archive

Read this file **before** analyzing Karaf logs or exceptions in this archive.

**Start with `run-context.txt`** for operator notes, build/Maven options (search engine, heap sizes), and a system snapshot.

**Compare multiple runs** with `test-results.tsv` (per-test PASS/FAIL/ERROR) and `archives/runs-index.tsv` (one row per capture). Run:

```bash
./compare-it-runs.sh --last 3
```

## What actually failed

**Source of truth for test pass/fail:** `failsafe-reports/`

1. `failsafe-reports/failsafe-summary.xml` — counts (failures, errors, skipped)
2. `failsafe-reports/org.apache.unomi.itests.AllITs.txt` — human-readable failure list and stack traces
3. `failsafe-reports/TEST-org.apache.unomi.itests.AllITs.xml` — structured JUnit XML

If a test is **green** in failsafe reports, do **not** treat related Karaf ERROR/WARN lines as regressions.

## Expected noise in Karaf logs (failure hardening)

Many integration tests **intentionally** trigger invalid requests, bad schemas, or security blocks. The server logs ERROR/WARN/Exception lines for those scenarios. That is normal.

The project encodes allowed log noise in `LogChecker.createLogChecker()` overrides (`InputValidationIT`, `JSONSchemaIT`, `CopyPropertiesActionIT`, …). This archive includes:

- **`expected-karaf-log-patterns.txt`** — substring patterns extracted from those tests (keep in sync with Java)
- **`karaf-unexpected-candidates.log`** (if present) — ERROR/WARN/Exception lines that **did not** match any expected pattern

### How to use `expected-karaf-log-patterns.txt`

| Prefix | Meaning |
|--------|---------|
| `SUBSTRING: <text>` | If a log line **contains** `<text>`, treat it as **expected** (ignore for root-cause analysis) |
| `MULTIPART: a \| b \| c` | All parts must appear on the **same line**, in order — also expected |

### Common intentional scenarios (not bugs by themselves)

| Area | Examples in logs | Tests |
|------|------------------|-------|
| JSON Schema validation | `Schema not found for event type: dummy`, `Validation error`, `JsonSchemaException` | `JSONSchemaIT` |
| REST input validation | `Response status code: 400`, `Invalid parameter`, `InvalidRequestExceptionMapper` | `InputValidationIT` |
| Dummy fixtures | `dummy_scope`, `event type: dummy`, `dummy_workspace` | Schema/validation ITs |
| Copy property edge cases | `Impossible to copy the property` | `CopyPropertiesActionIT` |
| Security / auth probes | HTTP 401/403, rejected scripting payloads | `GraphQLServletSecurityIT`, `ContextServletIT` |

### Analysis workflow

1. Start from **failsafe failures** — list failing test methods only.
2. For each failure, read its stack trace in `AllITs.txt` / XML.
3. Open **`exam/.../karaf-triage-summary.txt`** for artifact priority and top recurring errors.
4. Read **`karaf-failure-correlation.log`** when `failed-tests.txt` is non-empty — server excerpts near failing class/method anchors.
5. Use **`karaf-unexpected-candidates.log`** for novel server-side clues. Blocks use `--- block N (lines X-Y) ---` with merged context and full stack traces (ANSI stripped). Rolled logs (`karaf.log.1`, …) are merged; see `karaf-log-segments.txt`.
6. **Ignore** lines in `expected-karaf-log-patterns.txt` unless they explain a **failing** test. Also ignore **BundleWatcher** startup WARN (LogChecker fast path).
7. Do **not** report "many exceptions in karaf.log" if failsafe shows fewer failures and exceptions match expected hardening patterns.

## Comparing runs (systematic vs flaky)

When analyzing **multiple** captures:

1. Read `archives/runs-index.tsv` for a timeline (search engine, heaps, failure counts, operator notes).
2. For each run, open `test-results.tsv` — columns: `test_class`, `test_method`, `status`, `elapsed_s`.
3. Open `comparison-last-3.txt` in this capture (auto-generated when 2+ archives exist), or `archives/latest-comparison.txt`, or run `./compare-it-runs.sh --last N`.

| Classification | Meaning |
|----------------|---------|
| **Systematic** | FAIL/ERROR in **every** run compared — likely a real bug or broken assumption |
| **Flaky** | Mixed PASS and FAIL/ERROR across runs — timing, resource pressure, or ordering |
| **Regression** | PASS in an earlier run, FAIL/ERROR in the latest |
| **Fixed** | FAIL/ERROR earlier, PASS in the latest |

Correlate flaky tests with `operator.note` and `run-context.txt` system snapshots (swap, load, heap).

## File map

| File | Use |
|------|-----|
| `failsafe-reports/` | Test results (primary) |
| `surefire-reports/` | Small unit tests in itests module (pre-IT) |
| `exam/.../karaf-triage-summary.txt` | **Start here for logs** — volume stats, artifact priority, top recurring errors |
| `exam/.../karaf-failure-correlation.log` | Karaf excerpts near failing tests (from `failed-tests.txt` anchors) |
| `exam/.../karaf-exception-index.tsv` | Ranked recurring exception/error messages (`rank`, `count`, `first_line`, `sample`) |
| `exam/.../karaf-recent.log` | Full merged log (≤20k lines) or tail for timing context |
| `exam/.../karaf-log-segments.txt` | Rollover segment order when `karaf.log.1`, `.2`, … exist (16MB roll policy) |
| `exam/.../karaf.log[.N]` | Full segment files only with `--full-karaf` |
| `exam/.../karaf-errors-warnings.log` | Merged ERROR/WARN blocks (**15+15** context, extended through stack traces; overlaps merged) |
| `exam/.../karaf-unexpected-candidates.log` | Blocks where triggers are **not** globally ignored and **not** in expected patterns |
| `test-results.tsv` | Per-test PASS/FAIL/ERROR manifest for cross-run diff |
| `run-summary.properties` | Machine-readable run metadata (engine, heaps, counts, `run.fingerprint`) |
| `failed-tests.txt` | One failing test per line (`ClassName.method`) |
| `archives/runs-index.tsv` | Index of all captures under `archives/` |
| `comparison-last-3.txt` | Auto compare of last 3 captures (systematic / flaky / regression / fixed) |
| `archives/latest-comparison.txt` | Same report, always overwritten on each archive |
| `run-context.txt` | **Start here** — operator notes, build/Maven trace, inferred options, system snapshot |
| `run-config/it-run-trace.properties` | Raw trace from `./build.sh --integration-tests` (if present) |
| `expected-karaf-log-patterns.txt` | Ignore list for hardening tests |
| `run-config/*-port.properties` | Search engine Docker port mappings |
| `manifest.txt` | Capture metadata |

## Output format

When reporting findings:

- Separate **confirmed test failures** (from failsafe) from **informational log noise**
- Cite the failing test class and method name
- Quote only **unexpected** log lines (or lines tied to a failing test)
- Suggest fixes only for failures that are not explained by intentional hardening
