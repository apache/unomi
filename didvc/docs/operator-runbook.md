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

# DID-VC Operator Runbook

Operating the Apache Unomi DID-VC stack: the OSGi platform bundles
(`didvc-api`, `didvc-services`, `didvc-rest`, deployed in Karaf) and the
Credential Edge (`didvc-edge`, Spring Boot). Companion documents:
[tenant-onboarding-guide.md](tenant-onboarding-guide.md),
[credential-schema-cookbook.md](credential-schema-cookbook.md),
[compliance-handbook.md](compliance-handbook.md).

Build-plan reference: `.local-notes/hkt-did-vc/TODO.md` (task IDs cited
below as T-x.y). Items from phases 4–7 are **planned, not built**.

## 1. Prerequisites

| Component | Requirement | Notes |
|---|---|---|
| Java | JDK 17 | Ed25519/ES256 baseline (T-0.3) |
| Docker | Docker Engine + compose plugin | Local dev stack; containerized Karaf for ITs |
| Karaf | Apache Karaf distribution of Unomi (`package/` pipeline) | Hosts the `unomi-did-vc` feature |
| Search engine | Elasticsearch 9 **or** OpenSearch 3 | Provisioned by `setup-elasticsearch.sh` / `setup-opensearch.sh`; deliberately not in the compose file |
| PostgreSQL 16 | prod: audit log + relational metadata | JDBC audit store (`didvc_audit_log` table) |
| Kafka 3.9 | prod: audit/metering bus | Topic `didvc-metering` (auto-create enabled in dev) |
| Redis 7 | prod: edge nonce store | `didvc.edge.redis-enabled=true` |

Build from the repository root:

```bash
./build.sh        # full canonical build
# or the didvc subset:
mvn -pl bom,didvc/didvc-sd-jwt,didvc/didvc-metering,didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest,didvc/didvc-edge -am test
```

## 2. Topology: what runs where

- **In Karaf** — DID registry, issuer key metadata, schemas, status lists,
  trust registry, pairwise bindings, consent bridge, issuance
  orchestration, admin REST (CXF, port 8181 by default).
- **Edge (Spring Boot jar)** — OID4VCI issuer and OID4VP verifier
  endpoints under `/{tenant}/...`. The edge is **stateless**: credentials,
  status lists and trust state live in the platform behind the
  `PlatformApi` (REST); only ephemeral nonces, pre-authorized codes and
  access tokens are held locally (in-memory maps, 10-minute TTLs).
- **Contract between runtimes** — the edge calls the platform REST API
  (API key header) and emits audit/metering records. A degraded edge does
  not degrade trust data.

## 3. Configuration reference (`didvc.edge.*`)

All properties bind to `EdgeProperties` (Spring relaxed binding: camelCase
fields accept kebab-case keys). Plus one conditional flag owned by
`DidvcEdgeConfiguration`.

| Property | Default | Purpose | Production guidance |
|---|---|---|---|
| `didvc.edge.issuer-base-url` | `http://localhost:8080` | Public base URL of this edge; published as `credential_issuer` and used as the key-binding audience | Must be an **HTTPS** URL on the public domain; wallets and the conformance suite derive all endpoints from it |
| `didvc.edge.platform-base-url` | `http://localhost:8181` | Base URL of the Unomi platform REST API | Internal address; keep off the public network; TLS or private network between edge and platform |
| `didvc.edge.platform-api-key` | *(empty)* | API key presented to the platform on every call (`X-Api-Key` header) | Provision from a vault / environment variable — **never a literal in a config file** |
| `didvc.edge.internal-api-key` | *(empty)* | API key required on the internal offer-creation endpoint (`POST /{tenant}/internal/offers`) | Same custody rules as above. Empty disables the check — never leave empty in production |
| `didvc.edge.verification-fee-minor-units` | `150` | Fee billed per successful verification, minor units | Align with the commercial contract per deployment |
| `didvc.edge.verification-fee-currency` | `HKD` | Fee currency | Billing-feed contract (T-3.4) |
| `didvc.edge.request-signing-secret` | random UUID per boot | HS256 secret for signing OID4VP authorization request objects | Defaults to a random per-boot value valid for a **single instance**; set it explicitly (from vault/env) when the verifier front end is load-balanced, and rotate on schedule |
| `didvc.edge.redis-enabled` | *(unset → false)* | Not an `EdgeProperties` field: `@ConditionalOnProperty` in `DidvcEdgeConfiguration` that swaps the nonce store to `RedisNonceStore` (SET NX / GETDEL — atomic single-use semantics across a scaled fleet) | Enable whenever more than one edge instance serves verification; requires `spring.data.redis.*` connection settings, with AUTH and private network |
| `spring.data.redis.host` / `.port` / `.password` | Spring defaults | Redis connection for the nonce store | Enable Redis AUTH; network-restrict to the edge fleet |

Demo profile (`--spring.profiles.active=demo`): runs the edge against an
in-memory platform that issues real SD-JWT credentials and adds
`/demo/issuer-kid` and `/demo/issuer-jwk` — development only, never
production.

## 4. Local dev stack

One command (Kafka 9092, PostgreSQL 5433→5432 db/user/password `didvc`,
Redis 6379):

```bash
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml up -d
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml down
```

The compose file deliberately excludes Elasticsearch/OpenSearch — use the
existing `setup-elasticsearch.sh` or `setup-opensearch.sh` scripts. The
audit JDBC store and Kafka metering sink are exercised against this stack
by the module tests' H2 equivalents and the live smoke IT
(`DidvcSmokeIT`).

Running the edge standalone in demo mode:

```bash
# Provision the key out of band; the wallet/interop scripts read the
# same environment variable
export DIDVC_INTERNAL_API_KEY="$(openssl rand -hex 24)"
java -jar didvc/didvc-edge/target/unomi-did-vc-edge-*.jar \
  --spring.profiles.active=demo --server.port=8081 \
  --didvc.edge.internal-api-key="$DIDVC_INTERNAL_API_KEY"
```

Local conformance-suite running against the edge:
[didvc/scripts/LOCAL-CONFORMANCE.md](../scripts/LOCAL-CONFORMANCE.md).

### Edge API surface inventory (phases 2–7)

| Surface | Endpoints | Auth |
|---|---|---|
| OID4VCI issuer | `/{tenant}/.well-known/openid-credential-issuer`, `/{tenant}/token`, `/{tenant}/credential`, `/batch-credential`, `/deferred-credential`, `/{tenant}/internal/offers` (admin) | access tokens; internal offers via `X-Api-Key` |
| OID4VP verifier | `/{tenant}/vp/authorize`, `/{tenant}/vp/request/{id}`, `/{tenant}/vp/direct_post` (`claim_level_response` for zero-PII outcomes) | per-request nonces |
| Wallet backend | `/wallet/{walletId}/offers`, `/credentials[/{id}]`, `/presentations`, `/jwks` | app-session (front in production) |
| M2M verification | `/{tenant}/m2m/verify`, `/m2m/verify-batch` | `X-Api-Key` (`didvc.edge.m2m-api-keys`); mTLS at the ingress |
| Single Window | `/{tenant}/customs/declarations` | `X-Api-Key` |
| SCC filing | `/{tenant}/scc/filing-export` | platform |
| GB/Z 185 bridge | `/{tenant}/gbz185/verify` | per-tenant trusted-issuer keys + policy map |
| Agent admission | `/{tenant}/agents/admit`, `/{tenant}/agents/admission/{keyHash}` | platform; per-call live re-verification |

Operational keys (all environment/secret-service provisioned, never
committed): `DIDVC_INTERNAL_API_KEY` (internal offers), `DIDVC_PKCS11_PIN`
(HSM token), `didvc.edge.m2m-api-keys` (M2M/customs), per-run keys in CI
(`ci-*`). HSM signing proof: `didvc/scripts/run-hsm-softhsm2-proof.sh`.

## 5. Deploying the platform bundles vs the edge jar

**OSGi bundles (platform side).** The feature `unomi-did-vc` is declared
in `kar/src/main/feature/feature.xml` and pulls in `unomi-services` and
`unomi-cxs-privacy-extension-services` plus the three bundles
`unomi-did-vc-api`, `unomi-did-vc-services`, `unomi-did-vc-rest`
(installed `start="false"`, i.e. started after their feature
dependencies). Install into a running Karaf:

```bash
karaf@root()> feature:install unomi-did-vc
karaf@root()> bundle:list | grep did-vc     # expect Resolved/Active
```

Verify no Unomi core file was modified (design invariant, FR-ID1): the
module ships only as additional bundles.

**Edge jar.** Plain Spring Boot executable:

```bash
java -jar didvc/didvc-edge/target/unomi-did-vc-edge-*.jar \
  --didvc.edge.issuer-base-url=https://credentials.example.hk \
  --didvc.edge.platform-base-url=https://unomi-internal.example.hk:8181 \
  --didvc.edge.platform-api-key=$PLATFORM_API_KEY \
  --didvc.edge.internal-api-key=$INTERNAL_API_KEY \
  --didvc.edge.request-signing-secret=$REQUEST_SIGNING_SECRET \
  --didvc.edge.redis-enabled=true
```

In production, swap the in-memory audit/metering beans for the JDBC audit
store (PostgreSQL, `didvc_audit_log`) and the Kafka metering sink
(`didvc-metering` topic) via configuration beans
(`DidvcEdgeConfiguration` documents these swap points).

## 6. Key management (IssuerKeyService)

- `kid` = RFC 7638 JWK thumbprint of the generated key. Algorithms:
  `EdDSA` (Ed25519/OKP) and `ES256` (P-256/EC) only.
- **Only public JWKs are persisted** (`didvc-key-descriptor` items). Issuer
  private keys never touch persistence or logs.
- The in-process key-material provider is the **HSM/KMS replacement
  point**: after a platform restart, private key material is gone and
  signing fails with
  *"Private key material not available for kid …: after a restart, keys
  must be re-loaded from the HSM/KMS provider"*. PKCS#11 HSM signing
  behind `IssuerKeyService` is **planned** (T-7.3, phase 7) — until then,
  treat restarts as key-material loss events: re-provision keys and rotate
  the DID verification method before resuming issuance.
- Rotation window: `KeyDescriptor.rotationDueDate` defaults to **180
  days** after generation. Monitor and rotate via
  `POST /didvc/dids/{did}/rotate` (which generates a new key and updates
  the DID document's verification method).

## 7. Status list publishing cadence

- Status lists are W3C Bitstring Status List documents
  (`didvc-status-list` items): GZIP+base64url-encoded bitstring, default
  size 1024 entries, purpose `revocation` or `suspension`.
- Issuance auto-allocates an index on the tenant/issuer default
  revocation list (creating it on first use).
- Bit flips (`revoke`) take effect for **platform-mediated checks
  immediately** — every edge verification consults the status list per
  call.
- `POST /didvc/statuslists/{id}/publish` (body: `{"kid": "..."}`) signs
  the list into a status-list JWT for **external verifiers that fetch the
  list themselves**. Publishing is operator-driven; there is no built-in
  scheduler. Recommended cadence: republish after every revocation batch
  (or on a fixed interval, e.g. hourly/daily, if external fetchers rely on
  the signed list). A StatusList2021-compatible JWT is also available via
  the service's `buildStatusList2021Jwt` adapter.

## 8. Revocation runbook

1. Identify the credential record id (`GET /didvc/credentials/{recordId}`
   to confirm subject/schema/status index).
2. Revoke: `DELETE /didvc/credentials/{recordId}`. This flips the status
   bit, sets `revoked=true` on the record and emits a `didvcRevoked` event.
3. Verify state: `GET /didvc/credentials/{recordId}/revoked` →
   `{"recordId": "...", "revoked": true}` (also confirmable per index:
   `GET /didvc/statuslists/{id}/revoked?index=N`).
4. Propagate: the next verification through the edge fails with
   *"credential is revoked"* — no further action needed for edge-mediated
   verification. For counterparties that fetch the signed list directly,
   **republish** (see §7).
5. Check the audit log for the `didvcRevoked` event and Unomi events for
   downstream segmentation.

Revocation-authority approval workflows (governance charter W-3, T-7.1
RBAC on admin APIs) are **planned**; until then, restrict access to the
platform admin REST at the network/gateway layer.

## 9. Monitoring & audit

**Hash-chained audit log.** Every issuance, revocation and accepted
verification appends an `AuditRecord`
(`didvcIssued`, `didvcRevoked`, `didvpVerified`, `didvcOfferSent`). Each
record's SHA-256 hash covers
`seq | prev_hash | event_type | actor | subject_ref | payload | created_at`,
chained from `GENESIS_HASH = "genesis"`. Store backends: in-memory (edge
default) and JDBC (`didvc_audit_log` table, PostgreSQL-compatible,
append-only; verified against live PostgreSQL in T-3.5).

**Tamper detection** — `AuditLogService.verifyChain()` recomputes the full
chain (sequence numbers, predecessor links, per-row hashes) and returns
false on any mutation. Run it as a scheduled ops job against the JDBC
store (library call from `didvc-metering`; also exercised by the module
tests). Equivalent SQL-level spot check: recompute
`SHA-256(seq || '|' || prev_hash || '|' || event_type || '|' || COALESCE(actor,'') || '|' || COALESCE(subject_ref,'') || '|' || COALESCE(payload,'') || '|' || created_at)`
per row and compare with `hash`, then confirm each row's `prev_hash`
equals the previous row's `hash` (row 1 anchors at `genesis`).

**Metering.** Each successful verification produces a
`VerificationMeteringRecord` (partner, amount, currency, vct,
verifier-scoped `subjectRef` — no PII). The Kafka sink publishes JSON to
topic **`didvc-metering`**, keyed by the record's globally unique event id
so log compaction and consumer deduplication make billing idempotent.
Monitor topic lag and producer errors (`acks=all`).

**Platform events.** `didvcIssued` / `didvcRevoked` / `didvpVerified` /
`didvcOfferSent` flow through the Unomi event service — segmentation,
scoring and further audit are ordinary event consumers.

## 10. Backup & recovery — what state lives where

| State | Where | Backup approach |
|---|---|---|
| Schemas, credential records, status list records, trust entries, DID document records, key descriptors (public only), pairwise bindings, consent grants | Platform persistence via `PersistenceService` SPI → Elasticsearch 9 or OpenSearch 3 (`context-didvc-*` style indices for the hyphenated item types) | Standard ES/OS snapshots; index templates use priority ≥ 100 |
| Audit log | PostgreSQL `didvc_audit_log` (prod) | `pg_dump`/continuous archiving; **append-only** — grants no UPDATE/DELETE in production |
| Metering stream | Kafka `didvc-metering` | Topic replication + retention per billing contract |
| Edge protocol state (nonces, pre-authorized codes, access tokens, VP request contexts) | Edge memory or Redis | **Not backed up** — ephemeral by design; TTL ≤ 10 min; losing it forces wallets to restart in-flight flows |
| Issuer private keys | In-process provider only (HSM/KMS point, T-7.3 planned) | Cannot and must not be backed up from the app; re-provision from the HSM/KMS custodian |

Recovery: restore ES/OS snapshots + PostgreSQL, restart Karaf feature
(`feature:refresh`/restart bundles), restart the edge, re-provision key
material, run `verifyChain()` and confirm the Kafka consumer position
before resuming billing.

## 11. Troubleshooting

| Symptom / error | Cause | Action |
|---|---|---|
| *"nonce was not issued or has already been consumed"* (OID4VP `direct_post`) | Nonce single-use enforcement: consumed once fleet-wide (Redis) or per instance (in-memory); also expires with the 10-minute request TTL | Wallet must use a fresh authorization request; check Redis connectivity (`didvc.edge.redis-enabled` set but Redis down → check `spring.data.redis.*`); on multi-instance fleets **always** enable Redis or nonces issued on one instance are unknown on another |
| *"nonce does not match the authorization request"* | Submission nonce differs from the one pinned in the request object | Retry with the nonce from `GET /{tenant}/vp/request/{id}` |
| *"issuer is not trusted by this verifier"* | No active trust entry matching (verifier tenant, issuer DID, vct) within its validity window — entries are skipped unless `status=active`, `validFrom ≤ now < validUntil` | `GET /didvc/trust-check?verifierTenantId=&issuerDid=&vct=` to confirm; fix/extend the entry (see onboarding guide §7) |
| *"credential is revoked"* | Status bit flipped (per-call check) | Intended behavior; see §8 if unexpected |
| *"credential has expired"* / *"credential is not yet valid"* | `exp`/`nbf` window | Reissue via refresh lifecycle (90-day refresh window default) |
| *"Private key material not available for kid …"* | Platform restarted; in-process key material lost (HSM/KMS integration is T-7.3, planned) | Re-provision keys, rotate DID verification method, re-issue affected credentials |
| *"Claim 'x' is not in the allowed claim set of schema …"* | Claim whitelist (data minimization) rejected a non-whitelisted claim | Map the attribute to a whitelisted claim in the schema (see cookbook) |
| Edge can't reach platform | Wrong `platform-base-url` / API key | Check `X-Api-Key` propagation, TLS, network placement |
| Conformance suite fails to reach the edge | Public HTTPS URL required | The suite is hosted; use the cloudflared tunnel approach in `didvc/scripts/run-openid-conformance.sh` or run locally per `LOCAL-CONFORMANCE.md` (needs `X-Forwarded-Proto: https` on suite API calls) |
| **ES vs OpenSearch dual-backend note** | All persistence goes through the `PersistenceService` SPI, so both backends work unchanged | Any *backend-specific query builders* must be implemented and tested **twice** (module invariant); when adding queries, verify against both engines |

## 12. Security hardening checklist

- [ ] `didvc.edge.issuer-base-url` is HTTPS on the public domain (TLS
  terminated at the edge ingress).
- [ ] `didvc.edge.platform-base-url` reachable only over a private network
  or mTLS; platform REST not exposed publicly.
- [ ] `platform-api-key` and `internal-api-key` provisioned from
  vault/environment — **never literals in config files or command lines
  visible in process listings**; rotate on schedule.
- [ ] `internal-api-key` non-empty (empty disables the check on
  `POST /{tenant}/internal/offers`).
- [ ] `request-signing-secret` set explicitly on load-balanced fleets and
  rotated; never the per-boot random in production multi-instance setups.
- [ ] `didvc.edge.redis-enabled=true` on scaled fleets; Redis requires
  AUTH (`spring.data.redis.password`), bound to the edge network only.
- [ ] Issuer private keys only in the key-material provider; confirm no
  private JWK parameters (`d`) appear in `didvc-key-descriptor` items or
  logs. HSM/KMS custody is planned (T-7.3).
- [ ] Restrict CORS: the didvc-rest CXF endpoints currently use
  `allowAllOrigins=true` — constrain at the gateway/proxy until admin RBAC
  (T-7.1, planned) lands.
- [ ] Audit store is append-only (no UPDATE/DELETE grants on
  `didvc_audit_log`); schedule `verifyChain()` jobs.
- [ ] Edge instances are stateless and disposable; no secrets in edge
  container images beyond injected env/vault values.
- [ ] Kafka metering topic access restricted (billing data); TLS/SASL on
  the broker in production.

---

Review status: pending ops + legal sign-off (T-8.4 acceptance criteria)
