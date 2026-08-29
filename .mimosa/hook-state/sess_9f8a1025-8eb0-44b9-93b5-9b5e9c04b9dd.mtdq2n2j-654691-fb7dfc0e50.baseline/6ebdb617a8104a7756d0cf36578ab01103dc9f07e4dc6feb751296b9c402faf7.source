<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache Unomi — CLAUDE.md

Apache Unomi is a Java/OSGi customer data platform running on Apache Karaf. Multi-module Maven
reactor; runtime code is packaged as OSGi bundles. **New code uses OSGi Declarative Services**
(`@Component`, `@Reference`, `@Activate`). Legacy modules still ship Blueprint XML
(`OSGI-INF/blueprint/blueprint.xml`) during migration — do not add new Blueprint files; see
`CODING_GUIDELINES.md` and `manual/.../writing-plugins.adoc`.

## Dual persistence backends — the #1 thing to know

Every persistence-related feature must be implemented **twice**, once per search engine, and the
two implementations must behave identically:

- `persistence-elasticsearch/core` — `ElasticSearchPersistenceServiceImpl.java` (Elasticsearch, ILM
  for rollover, `co.elastic.clients.elasticsearch.*` client)
- `persistence-opensearch/core` — `OpenSearchPersistenceServiceImpl.java` (OpenSearch, ISM for
  rollover, `org.opensearch.client.opensearch.*` client)

The two client libraries look similar (OpenSearch's Java client is a fork of Elastic's) but their
typed builder APIs differ in real, non-obvious ways — e.g. ES's `numberOfShards(String)` vs
OpenSearch's `numberOfShards(Integer)`. **Do not port code between the two by pattern-matching
alone — verify actual method signatures for the specific client version in use** (`mvn
dependency:tree` + decompile if needed; there is no live cluster available in most dev sandboxes to
test against directly).

ISM (OpenSearch) and ILM (ES) are conceptually parallel but not identical: ISM policies attach to
indices either via an explicit `plugins.index_state_management.policy_id` index setting or via the
policy's own `ism_template.index_patterns` matching at index-creation time — prefer the explicit
`policy_id` setting (set via `IndexSettings.customSettings(...)`, since there's no typed builder
field for it) over template pattern-matching, which is fragile (a past bug: the pattern list was
built from raw item-type names like `"event"` that never matched real index names like
`"context-event-000001"`).

**Composable index template priority**: `BaseIT` registers an IT-only catch-all template
(`unomi-it-zero-replicas`, pattern `*`, priority `1`) to force zero replicas on single-node test
clusters. Any real Unomi index template must use a priority well above that (convention: `100`) or
template registration fails outright — composable templates reject ambiguous same-priority
overlapping patterns rather than merging them.

**Single-node test clusters legitimately stay `yellow` forever** (no second node to host replicas).
Code that blocks on `HealthStatus.Green` will hang/fail in ITs. OpenSearch's persistence service has
a `minimalClusterState` config + `getHealthStatus(minimalClusterState)` helper for this — reuse it.
**Elasticsearch's persistence service has no equivalent wired in** (only the separate
`extensions/healthcheck` bundle knows about yellow-tolerance) — this is a known, unaddressed parity
gap, not an oversight to silently "fix" by inventing new config without discussing it with the user
first.

## Build

- Root `pom.xml` aggregates ~40 modules. Key ones: `api`, `common`, `persistence-spi`,
  `persistence-elasticsearch`, `persistence-opensearch`, `services`, `rest`, `graphql`, `wab`,
  `kar` (Karaf feature repo), `itests`.
- `./build.sh` is the canonical local build entrypoint (aligns with CI). Prefer it over ad hoc
  `mvn` invocations when validating a full build.
- The `itests` module only joins the reactor under the `integration-tests` (or `ci-build-itests`)
  profile — `mvn -pl itests ...` alone will fail with "module not found in reactor" unless that
  profile is active.
- Karaf `karaf-maven-plugin:verify` failures on unrelated modules (e.g. stale
  `target/classes` "(Is a directory)" errors) are usually stale local build state, not real
  regressions — don't chase them as if they were caused by your change without checking first.

## Integration tests (`itests/`)

- PaxExam + Karaf, `PerSuite` reactor strategy: **one shared Karaf container/cluster runs the
  entire suite**, not one per test class. Tests must clean up after themselves (`@After`) or they
  pollute later tests' assumptions.
- **`itests/pom.xml`'s failsafe config only includes `**/*AllITs.java`** — individual `*IT.java`
  classes are silently never run unless explicitly registered in `AllITs.java`'s `@SuiteClasses`
  list. This is easy to forget when adding a new IT.
- `BaseIT.java` is the shared base class: search-engine-agnostic HTTP helpers
  (`createSearchEngineHttpClient()`, `getSearchEngineBaseUrl()`), `keepTrying(...)` for
  poll-with-timeout assertions, and Karaf container config (`etc/custom.system.properties`
  overrides) shared by ES and OpenSearch runs. IT-only config differences between the two engines
  (e.g. `rollover.maxDocs`) must be set for **both** `org.apache.unomi.elasticsearch.*` and
  `org.apache.unomi.opensearch.*` properties, or one engine silently runs untested.
- `itests/kt.sh` is a helper for driving/inspecting a running IT Karaf instance (`kt.sh t` to run,
  `kt.sh l` to view the log) — see that script and any `itests/README*` for the current test-dev
  workflow rather than assuming.
- Run engine-specific ITs via the search-engine system property; check `BaseIT` /
  `AllITs`/`build.sh` for the exact flag rather than guessing.

## Working with subagents / cost awareness

Verifying an unfamiliar client library's exact API surface via jar decompilation (`javap`) is a
legitimate but expensive way to work when no live test environment is available — it burns a lot of
tool calls. When a fix's correctness can't be confirmed without actually running the code (e.g.
against a live Elasticsearch/OpenSearch container), prefer asking the user to run it and paste back
real output over exhaustively re-deriving behavior from bytecode.

## Memory

Session-persistent project memory for this repo lives outside the working tree (Claude's own memory
system) — check it for prior investigation before re-deriving things like client API quirks,
known-flaky tests, or in-progress uncommitted work state.
