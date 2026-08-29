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
| `didvc-services` | OSGi bundle | **Phase 1–4** | DS components: `DidService` (did:web), `IssuerKeyService` (EdDSA/ES256 JWS), `StatusService` (Bitstring Status List + StatusList2021), `CredentialSchemaService` (claim whitelist), `SdJwtVcFormatter` (vc+sd-jwt), `JsonLdVcFormatter` (ldp_vc, VC DM 2.0), `IssuanceService` (orchestration + consent gating + revocation, format selection), `TrustRegistryService`, `PairwiseBindingService`, `ConsentBridgeService`, `CredentialRefreshService`, `UniversalDidResolverServiceImpl` + did:key/HTTP method resolvers, `Phase4SchemaBootstrap` (hkt_profcred_v1/hkt_residency_v1), plus the `issueCredential` rule action (`didvcIssueCredentialAction`) |
| `didvc-rest` | OSGi bundle | **Phase 2–4** | CXF endpoints: `/didvc/dids`, `/.well-known/did.json`, `/didvc/credentials`, `/didvc/schemas`, `/didvc/statuslists`, `/didvc/trust-entries`, `/didvc/trust-check`, `/didvc/pairwise-bindings`, `/didvc/consent-grants`, `/didvc/resolver/{did}` (universal DID resolution) |
| `didvc-metering` | jar | **Phase 3** | Verification metering (billable records, idempotent billing, Kafka sink) and the immutable hash-chained audit log (in-memory and JDBC stores) |
| `didvc-edge` | Spring Boot jar | **Phase 2–4** | Credential Edge: OID4VCI issuer (metadata, offers, **pre-authorized-code and authorization-code grants with PKCE**, credential/batch/deferred, nonce), OID4VP verifier (signed authorization requests with **DCQL queries**, `direct_post`, SD-JWT + key-binding validation, **nonce-store-backed replay protection (in-memory or Redis)**, revocation and trust checks, audit + metering) and the **wallet backend API** (`/wallet/...`: offer redemption, credential storage listing, presentation builder) |
| `didvc-openid-gateway` | jar | Phase 7 | OpenDID Web2/Web3 gateway placeholder (oracle-contract bridge) |

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
- OID4VP: `POST /{tenant}/vp/authorize` (claims map or `dcql_query`),
  `GET /{tenant}/vp/request/{id}`, `POST /{tenant}/vp/direct_post`

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

- Unit/integration tests: `mvn -pl bom,didvc/didvc-sd-jwt,didvc/didvc-metering,didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest,didvc/didvc-edge -am test`
  (137 tests: api 8, sd-jwt 9, metering 6, services 89, edge 25).
- Live ITs against a real Karaf + Elasticsearch container (verified 7/7 —
  the phase 1-3 smoke suite plus phase 4's cross-method resolution tests,
  all in `DidvcSmokeIT`):

```bash
mvn -P integration-tests -pl itests install \
  -Dit.test=DidvcSmokeIT -Dfailsafe.includes=**/DidvcSmokeIT.java
```

Run the didvc suite with `didvc/scripts/run-didvc-its.sh` (docker, host
network, Dockerized Elasticsearch). The IT backends install the
`unomi-did-vc` feature explicitly so the DID-VC bundles are deployed
before the PaxExam probe starts.
