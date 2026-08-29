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

# unomi-did-vc — DID/VC module for Apache Unomi

DID/VC identity and verifiable-credential extension for the Apache Unomi
Context Server (HKT Trusted-Identity CDP). Adds a W3C DID Core /
Verifiable Credentials-aligned identity layer as additional OSGi bundles —
no Unomi core source is modified.

See the design and build plan in `.local-notes/hkt-did-vc/`
(ARCHITECTURE.md, DIAGRAMS.md, TODO.md).

## Modules

| Module | Packaging | Status | Purpose |
|---|---|---|---|
| `didvc-api` | OSGi bundle | **Phase 1–4** | Domain model (8 item types, event types, DID document) + service interfaces (incl. the `DidMethodResolver` SPI and `UniversalDidResolverService`) |
| `didvc-sd-jwt` | jar | **Phase 2** | SD-JWT (RFC 9901 / SD-JWT VC) builder, parser, key-binding JWT and selective-disclosure verification — shared by services and edge |
| `didvc-services` | OSGi bundle | **Phase 1–7** | DS components: `DidService` (did:web), `IssuerKeyService` (EdDSA/ES256 JWS) over the `KeyMaterialProvider` seam (in-process default; `Pkcs11KeyMaterialProvider` for HSM-held keys — private material never enters app memory), `StatusService` (Bitstring Status List + StatusList2021), `CredentialSchemaService` (claim whitelist), `SdJwtVcFormatter` (vc+sd-jwt), `JsonLdVcFormatter` (ldp_vc, VC DM 2.0), `IssuanceService` (orchestration + consent gating + revocation, format selection), `TrustRegistryService`, `PairwiseBindingService`, `ConsentBridgeService`, `CredentialRefreshService`, `UniversalDidResolverServiceImpl` + did:key/HTTP method resolvers, `SplitKnowledgeService` (two-custodian re-identification), the phase 4–7 schema bootstraps (`hkt_profcred_v1` … `hkt_agent_binding_v1`), plus the `issueCredential` rule action (`didvcIssueCredentialAction`) |
| `didvc-rest` | OSGi bundle | **Phase 2–7** | CXF endpoints (mutations carry `@RequiresRole(ADMINISTRATOR)`): `/didvc/dids`, `/.well-known/did.json`, `/didvc/credentials`, `/didvc/schemas`, `/didvc/statuslists`, `/didvc/trust-entries`, `/didvc/trust-check`, `/didvc/pairwise-bindings`, `/didvc/consent-grants`, `/didvc/resolver/{did}` (universal DID resolution) |
| `didvc-metering` | jar | **Phase 3, 5, 6** | Verification metering (billable records, idempotent billing, Kafka sink), the immutable hash-chained audit log (in-memory and JDBC stores), the GBA SCC filing exporter (audit → filing-template field set, zero PII) and the manifest batch processor (per-record audit + Kafka result publishing) |
| `didvc-edge` | Spring Boot jar | **Phase 2–7** | Credential Edge: … the **GB/Z 185 interop bridge** (`/{tenant}/gbz185/verify`) and the **agent admission gate** (`/{tenant}/agents/admit` + per-call `/{tenant}/agents/admission/{keyHash}` with kill-switch semantics) | OID4VCI issuer (metadata, offers, **pre-authorized-code and authorization-code grants with PKCE**, credential/batch/deferred, nonce), OID4VP verifier (signed authorization requests with **DCQL queries**, `direct_post`, SD-JWT + key-binding validation, **nonce-store-backed replay protection (in-memory or Redis)**, revocation and trust checks, **claim-level zero-PII responses**, audit + metering), the **wallet backend API** (`/wallet/...`: offer redemption, credential storage listing, presentation builder), the **GBA SCC filing-export API** (`/{tenant}/scc/filing-export`), the **M2M verification API** (`/{tenant}/m2m/verify[-batch]`, stateless, API-key auth, sub-second p95) and the **Single Window customs endpoint** (`/{tenant}/customs/declarations`, EDI declaration → manifest batch → verification response) |
| `didvc-openid-gateway` | jar | **Phase 7** | OpenDID Web2↔Web3 gateway: DID anchor/resolve through chain adapters — EVM (`EvmChainAdapter`, hand-encoded DidAnchorRegistry ABI + simulated contract for demos/tests, RPC connection for testnet from env) with Tron/Solana/Aptos stubs |

## Build

From the repository root:

```bash
./build.sh                                  # full canonical build
mvn -pl bom,didvc/didvc-sd-jwt,didvc/didvc-metering,didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest,didvc/didvc-edge -am test
```

The integration-test scaffold (`DidvcSmokeIT`) runs under the usual IT
profile: `./build.sh -P integration-tests -Dit.test=DidvcSmokeIT`.

## Local development stack

Kafka (audit/metering bus), PostgreSQL (metadata/audit store) and Redis
(edge protocol state) in one command:

```bash
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml up -d
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml down
```

The search engine is provisioned by the existing `setup-elasticsearch.sh`
or `setup-opensearch.sh` scripts. The audit JDBC store and Kafka metering
sink are exercised against this stack by the module tests' H2 equivalents
and the live smoke (see `.local-notes/hkt-did-vc/TODO.md` phase 3 notes).

## Edge (Spring Boot)

Run: `java -jar didvc/didvc-edge/target/unomi-did-vc-edge-*.jar` with
`didvc.edge.*` properties (see `EdgeProperties`). The edge is stateless:
credentials, status lists and trust state live in the Unomi platform
behind the `PlatformApi` (REST); only ephemeral nonces, pre-authorized
codes and access tokens are held locally. In production, swap the
in-memory audit/metering stores for JDBC (PostgreSQL) and Kafka via
configuration beans.

## REST API (Phase 2–4)

- `POST /didvc/credentials` — issue (schema whitelist + consent-gated;
  `format` selects the credential formatter, default `dc+sd-jwt`, `ldp_vc` for JSON-LD)
- `GET /didvc/credentials/{recordId}`, `DELETE` (revoke), `GET .../revoked`
- `POST /didvc/schemas`, `GET /didvc/schemas[/{schemaId}]`, `DELETE`
- `POST /didvc/statuslists`, `GET /didvc/statuslists/{id}`,
  `POST /didvc/statuslists/{id}/publish`, `GET .../revoked?index=N`
- `POST /didvc/trust-entries`, `GET /didvc/trust-entries`,
  `GET /didvc/trust-check?verifierTenantId=&issuerDid=&vct=`
- `POST /didvc/pairwise-bindings` (returns the verifier-scoped opaque
  reference; profile resolution is never exposed)
- `POST /didvc/consent-grants`
- `GET /didvc/resolver/{did}` — universal DID resolution (did:web, did:key,
  iAM Smart / RealDID drivers, registry stubs)
- OID4VCI: `GET /{tenant}/.well-known/openid-credential-issuer`,
  `GET /{tenant}/authorize` (authorization-code grant, PKCE),
  `POST /{tenant}/internal/offers`, `POST /{tenant}/token` (both grant
  types), `POST /{tenant}/credential`, `/batch-credential`,
  `/deferred-credential`
- OID4VP: `POST /{tenant}/vp/authorize` (claims map or `dcql_query`;
  `claim_level_response: true` switches the result to the phase-5
  zero-PII boolean contract — `valid`, `vct`, `expiresAt`, per-claim
  `satisfied` flags), `GET /{tenant}/vp/request/{id}`,
  `POST /{tenant}/vp/direct_post`

## GBA SCC filing export (Phase 5, on the edge)

- `GET /{tenant}/scc/filing-export?contract_reference=&purpose=&from=&to=`
  — renders the immutable audit log's verification records for the
  counterparty tenant into the filing-template field set (filingDate,
  exporter, importer, contractReference, purpose, dataElements as
  claim-type categories only, verificationRecords). Zero PII by
  construction — claim values never leave the audit log.

## M2M verification + Single Window customs (Phase 6, on the edge)

- `POST /{tenant}/m2m/verify` — stateless bearer-credential verification
  for logistics counterparties (API-key auth via `X-Api-Key`; keys read
  from `didvc.edge.m2m-api-keys` / environment, never committed; mTLS
  terminates at the ingress in production). Claim-level response
  (`valid`, `vct`, `expiresAt`); `includeClaims: true` adds disclosed
  values. Every check appends `didvcM2mVerified` to the audit log.
- `POST /{tenant}/m2m/verify-batch` — N records in one call, per-record
  outcomes keyed by the caller's correlation id.
- `POST /{tenant}/customs/declarations` — Single Window EDI adapter: a
  `DECLARATION` message (declarationNumber + lineItems, each with itemId
  and credential references) is verified through the manifest batch
  pipeline (per-record audit; Kafka result publishing when
  `didvc.edge.manifest-kafka-bootstrap-servers` is set, topic
  `didvc-manifest-verification` keyed by manifest id) and answered with
  a `VERIFICATION` response (status 1 accepted / 2 rejected per line
  item, correlation ids preserved).

## Governance & cross-jurisdiction (Phase 7, on the edge)

- `POST /{tenant}/gbz185/verify` — GB/Z 185 interop bridge: verifies a
  linkage VP (signed JWT: agent identity code, agent public key hash,
  policy scope) against the per-tenant trusted-issuer key set
  (`didvc.edge.gbz185-issuer-jwks`) and policy mapping
  (`didvc.edge.gbz185-policies`, entries `tenantId|issuerId=scopes`).
  Every call — accepted or rejected — appends a `didvcGbz185Verified`
  audit record.
- `POST /{tenant}/agents/admit` — registers a bound agent from a valid
  `hkt_agent_binding_v1` credential (non-binding vcts refused).
- `GET /{tenant}/agents/admission/{agentPubKeyHash}` — the gateway's
  per-call admission gate: re-verifies the registered binding live, so
  revocation takes effect at the next verified call (kill-switch
  semantics); unbound agents are never admitted.

Karaf-platform governance (phase 7): all mutating `/cxs/didvc/*` admin
endpoints (schemas, status lists, trust entries, DIDs, credentials)
require the `ROLE_UNOMI_ADMIN` role via `@RequiresRole`; the
split-knowledge re-identification workflow (`SplitKnowledgeService`)
requires two distinct custodian approvals before a pairwise reference
resolves, with every step on the persisted audit trail. The HSM path:
set `didvc.keyservice.pkcs11.config` to a SunPKCS11 config file and
`DIDVC_PKCS11_PIN` in the environment — signing then happens inside the
token (`Pkcs11KeyMaterialProvider`).

## Wallet backend API (Phase 4, on the edge)

- `POST /wallet/{walletId}/offers` — redeem a credential offer
  (pre-authorized-code grant): token exchange, holder-proof credential
  request, key binding; holds the delivered credential
- `GET /wallet/{walletId}/credentials` — storage listing (metadata only)
- `GET|DELETE /wallet/{walletId}/credentials/{credentialId}`
- `POST /wallet/{walletId}/presentations` — build a key-bound
  presentation for a verifier authorization request (`requestUri`) and
  submit it to the verifier's `direct_post` endpoint; returns the
  verification result
- `GET /wallet/{walletId}/jwks` — the wallet's public holder key

## Conventions

- New code uses OSGi Declarative Services (`@Component`/`@Reference`) — no
  Blueprint XML.
- All persistence goes through the `PersistenceService` SPI, so both the
  Elasticsearch and OpenSearch backends work unchanged; any future
  backend-specific query builders must be implemented twice (see CLAUDE.md).
- Issuer private keys never touch persistence or logs; only public JWKs are
  stored (`didvc-key-descriptor`). The in-process key-material provider is
  the HSM/KMS replacement point.
- Verification responses expose disclosed claims only; subjects are
  referenced by verifier-scoped pairwise pseudonyms everywhere outside the
  platform.
- Item types use hyphenated names (`didvc-schema`, `didvc-status-list`, …)
  — colons would break Elasticsearch index naming (`context-<type>` is
  parsed as a cross-cluster reference).

## Demo profile & wallet interop

Run the edge standalone against an in-memory platform that issues real
SD-JWT credentials (`--spring.profiles.active=demo`; adds
`/demo/issuer-kid` and `/demo/issuer-jwk`). The reference-wallet round
trip in `didvc/interop/` drives the full OID4VCI pre-authorized flow and
an OID4VP DCQL presentation through the OpenWallet Foundation
`@openid4vc` client libraries (a third-party implementation), verifying
the received credential with `jose` and the demo issuer key. Verified
end to end: offer → token exchange → credential (key-bound via the
request proof) → wallet-side signature check → presentation accepted with
disclosed claims.

```bash
java -jar didvc/didvc-edge/target/unomi-did-vc-edge-*.jar   --spring.profiles.active=demo --server.port=8081   --didvc.edge.internal-api-key=test-key
cd didvc/interop && npm install && npx tsx wallet-roundtrip.ts
```

## Conformance (CI)

The OpenID OID4VCI/OID4VP conformance suite is hosted by the OpenID
Foundation and must reach the edge over a public HTTPS URL.
`.github/workflows/didvc-conformance.yml` runs the wallet interop job
plus `didvc/scripts/run-openid-conformance.sh`, which exposes the edge
through a cloudflared quick tunnel and drives the hosted suite's test
plans for the issuer and verifier modules.

## Verification

- Unit/integration tests: `mvn -pl bom,didvc/didvc-sd-jwt,didvc/didvc-metering,didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest,didvc/didvc-edge,didvc/didvc-openid-gateway -am test`
  (212 tests: api 8, sd-jwt 22, metering 13, services 115, rest 3,
  edge 46, openid-gateway 5 — including the M2M sub-second-p95 load
  check, the Single Window EDI fixture round-trips, the admin RBAC
  matrix, split-knowledge workflow, GB/Z 185 bridge and agent
  admission).
- HSM signing proof (SoftHSM2): `didvc/scripts/run-hsm-softhsm2-proof.sh`
  — per-run token/PIN, key generated ON the token, JWS verified against
  the token's public key (prints PROOF-OK).
- Live ITs against a real Karaf + Elasticsearch container (verified 11/11 —
  the phase 1-6 suites plus phase 7's admin-RBAC live matrix (anonymous
  mutation → 401/403, JAAS admin → 2xx) and the split-knowledge
  two-custodian workflow, all in `DidvcSmokeIT`):

```bash
mvn -P integration-tests -pl itests install \
  -Dit.test=DidvcSmokeIT -Dfailsafe.includes=**/DidvcSmokeIT.java
```

Run the didvc suite with `didvc/scripts/run-didvc-its.sh` (docker, host
network, Dockerized Elasticsearch). The IT backends install the
`unomi-did-vc` feature explicitly so the DID-VC bundles are deployed
before the PaxExam probe starts.
