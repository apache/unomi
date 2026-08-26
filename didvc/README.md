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
| `didvc-api` | OSGi bundle | **Phase 1** | Domain model (7 item types, event types, DID document) + service interfaces |
| `didvc-services` | OSGi bundle | **Phase 1** | DS components: `DidService` (did:web), `IssuerKeyService` (EdDSA/ES256, JWS), `StatusService` (Bitstring Status List + StatusList2021), `CredentialSchemaService` (claim whitelist) |
| `didvc-rest` | OSGi bundle | **Phase 1** | CXF endpoints: `/didvc/dids` admin API, `/.well-known/did.json` |
| `didvc-edge` | jar | Phase 2+ | Credential Edge placeholder (OID4VCI/OID4VP/wallet/M2M, Spring Boot) |
| `didvc-openid-gateway` | jar | Phase 3 | OpenDID Web2/Web3 gateway placeholder (oracle-contract bridge) |
| `didvc-metering` | jar | Phase 2+ | Verification-metering placeholder (Kafka → billing) |

## Build

From the repository root:

```bash
./build.sh                                  # full canonical build
mvn -pl didvc/didvc-api,didvc/didvc-services,didvc/didvc-rest -am test   # just the didvc modules + tests
```

The integration-test scaffold (`DidvcSmokeIT`) runs under the usual IT
profile: `./build.sh -P integration-tests -Dit.test=DidvcSmokeIT`
(see `.local-notes/hkt-did-vc/TODO.md` for the full plan).

## Local development stack

Kafka (audit/metering bus), PostgreSQL (metadata/audit store) and Redis
(edge protocol state) in one command:

```bash
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml up -d
docker compose -f docker/src/main/docker/docker-compose-didvc-dev.yml down
```

The search engine is provisioned by the existing `setup-elasticsearch.sh`
or `setup-opensearch.sh` scripts.

## REST API (Phase 1)

- `POST /didvc/dids` — create a did:web DID
  `{"tenantId":"hkt","domain":"id.example.hkt","path":null,"algorithm":"EdDSA"}`
- `GET /didvc/dids/{did}` — resolve (returns the DID document JSON)
- `GET /didvc/dids?tenantId=hkt` — list a tenant's DIDs
- `POST /didvc/dids/{did}/rotate` — add a verification method (`{"algorithm":"ES256"}`)
- `DELETE /didvc/dids/{did}` — deactivate
- `GET /.well-known/did.json?did=did:web:...` — DID-document endpoint
  (Host-based did:web resolution is a follow-up)

## Conventions

- New code uses OSGi Declarative Services (`@Component`/`@Reference`) — no
  Blueprint XML.
- All persistence goes through the `PersistenceService` SPI, so both the
  Elasticsearch and OpenSearch backends work unchanged; any future
  backend-specific query builders must be implemented twice (see CLAUDE.md).
- Issuer private keys never touch persistence or logs; only public JWKs are
  stored (`didvc:key-descriptor`). The in-process key-material provider is
  the HSM/KMS replacement point.
