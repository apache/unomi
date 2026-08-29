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

# DID-VC performance results — Phase 8 (T-8.3)

Target (FR-L2/FR-P5): the verification surface holds a **sub-second
p95** at peak volumes, so instances autoscale horizontally. All figures
below are measured by tests that run in every CI build — they are
functional guards (regression ceiling), not benchmark reports; the
numbers are from a CI-class runner (2 vCPU) and a developer workstation
respectively.

## Measured paths

| Path | Load shape | p95 target | Measured | Test |
|---|---|---|---|---|
| M2M bearer verification `POST /{tenant}/m2m/verify` | 200 requests, 8 concurrent (customs peak burst) | < 1 s | **passes in-suite** (assertion green across CI runs) | `M2mVerificationIntegrationTest.loadTestHoldsSubSecondP95AtCustomsPeakVolume` |
| OID4VP full cycle: authorize → signed request object → key-bound `direct_post` | 60 cycles, 6 concurrent (bank onboarding peak shape; each cycle issues a fresh credential) | < 1 s per cycle | **passes in-suite** | `VpVerificationLoadTest.authorizationAndPresentationCycleHoldsSubSecondP95` |
| M2M batch `POST /{tenant}/m2m/verify-batch` | N records per call | linear in N; no per-record state kept | per-record outcomes asserted; batch test green | `M2mVerificationIntegrationTest.batchVerificationReturnsPerRecordOutcomes` |
| Manifest batch pipeline (logistics) | 25 manifests/cycle, per-record audit | functional bound | 25 audit records + 25 sink publications asserted | `ManifestBatchProcessorTest.batchOfNManifestsProcessesWithPerRecordAudit` |

## Reproducing

The in-suite load guards run with the standard test chain:

```bash
mvn -pl bom,didvc/didvc-sd-jwt,didvc/didvc-metering,didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest,didvc/didvc-edge,didvc/didvc-openid-gateway -am test
```

For a standalone load run against a deployed edge (M2M path), use the
interop load driver — the API key comes from the environment, never a
committed literal:

```bash
export EDGE_API_KEY="$(openssl rand -hex 24)"   # same key the edge was started with
cd didvc/interop && npx tsx load-test.ts --edge https://edge.example.hkt --iterations 500 --concurrency 16
```

`load-test.ts` prints the p50/p95/p99 and fails non-zero when the p95
target (default 1000 ms) is exceeded.

## Notes and open items

- The OID4VP cycle includes the signed authorization-request object,
  the SD-JWT + key-binding validation and nonce single-use consumption —
  the full verifier path, not a cached stub.
- Both load guards are stateless by construction (the M2M endpoint
  keeps no per-request state; the OID4VP nonce store is the only shared
  state, swappable for Redis across a fleet).
- Capacity sizing for production peak volumes is an operator exercise:
  the guards certify the per-request cost ceiling that autoscaling
  multiplies, not a specific QPS figure.
