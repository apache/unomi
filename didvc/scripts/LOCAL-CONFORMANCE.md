# Running the OpenID conformance suite locally against the credential edge

The OpenID Foundation's conformance suite (the migrated Java service) can be
built and run locally — no hosted account or public URL required. This is how
the DID-VC edge was conformance-tested in development; the same steps work in
CI.

## 1. Build the suite

```bash
git clone --depth 1 https://gitlab.com/openid/conformance-suite.git cs
# Local plain-HTTP development: disable the suite's RejectPlainHttpTrafficFilter
# (edit WebSecurityOidcLoginConfig.java + WebSecurityResourceServerConfig.java,
# wrap the addFilterAfter(...) line in: if (!Boolean.getBoolean("fintechlabs.allowPlainHttp")) { ... })
docker run --rm -u root -v $PWD/cs:/cs -v /tmp/m2-cs:/root/.m2 -w /cs \
  maven:3.9-eclipse-temurin-21 mvn -B package -DskipTests
```

## 2. Run the suite + MongoDB + the edge

```bash
docker run -d --name cs-mongo --network host mongo:6.0.13 --quiet
docker run --rm --network host --name conformance-suite -v $PWD/cs:/cs -w /cs eclipse-temurin:21 \
  java -Dfintechlabs.devmode=true -Dfintechlabs.allowPlainHttp=true \
       -Dspring.mongodb.uri=mongodb://localhost:27017/test_suite \
       -Dfintechlabs.base_url=http://localhost:8080 -jar target/fapi-test-suite.jar

java -jar didvc/didvc-edge/target/unomi-did-vc-edge-*.jar \
  --spring.profiles.active=demo --server.port=8081 \
  --didvc.edge.internal-api-key=test-key --didvc.edge.issuer-base-url=http://localhost:8081
```

All suite API calls need the `X-Forwarded-Proto: https` header (the suite's
forwarded-header strategy derives the request scheme from it).

## 3. Create and drive a plan

Plan: `oid4vci-1_0-issuer-test-plan` (VCI 1.0 Final issuer modules).

Variant selection (query param `variant`, JSON-encoded):

```json
{"fapi_profile":"vci","client_auth_type":"private_key_jwt",
 "credential_format":"sd_jwt_vc","fapi_request_method":"unsigned",
 "sender_constrain":"dpop","authorization_request_type":"simple",
 "openid":"plain_oauth","fapi_response_mode":"plain_response",
 "vci_grant_type":"authorization_code",
 "vci_authorization_code_flow_variant":"issuer_initiated",
 "vci_credential_encryption":"plain"}
```

Configuration body:

```json
{"description":"hkt-didvc issuer",
 "vci":{"credential_issuer_url":"http://localhost:8081/hkt",
        "credential_configuration_id":"hkt_kyc_v1",
        "credential_offer_endpoint":"http://localhost:8081/hkt/credential-offer"},
 "client":{"client_id":"hkt-didvc-wallet","dpop_signing_alg":"ES256"},
 "client2":{"client_id":"hkt-didvc-wallet-2","dpop_signing_alg":"ES256"}}
```

Per module: `POST /api/runner?test=<name>&plan=<id>` → `POST /api/runner/<module-id>`
(start) → out-of-band step: when the module exposes `credential_offer_endpoint`
(`GET /api/runner/<module-id>` → `exposed`), GET the offer from
`http://localhost:8081/hkt/credential-offer` and POST it to the exposed URL as
`?credential_offer=<urlencoded json>` → long-poll
`GET /api/runner/<module-id>/wait-state?states=FINISHED,INTERRUPTED&timeoutMs=30000`
→ `GET /api/log/<module-id>` for the result.

## Verified results (local run, suite 5.2.4)

`oid4vci-1_0-issuer-test-plan`, 15 modules, 5 FINISHED:

| Module | Result |
|---|---|
| oid4vci-1_0-issuer-metadata-test | FINISHED |
| oid4vci-1_0-issuer-metadata-test-signed | FINISHED (skip: unsigned metadata) |
| oid4vci-1_0-issuer-batch-issuance | FINISHED |
| oid4vci-1_0-issuer-fail-invalid-key-attestation-signature | FINISHED |
| oid4vci-1_0-issuer-fail-unsupported-encryption-algorithm | FINISHED |

The remaining 10 flow modules reach PAR + the authorization redirect but
stall inside the suite's HtmlUnit-driven redirect wait (suite-internal
browser step); they are documented here rather than claimed as passed. The
plan itself is marked alpha by the OpenID Foundation
("alpha version - may be incomplete or incorrect").
