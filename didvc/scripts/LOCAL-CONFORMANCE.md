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

## Driving a plan (automated)

`didvc/scripts/drive-openid-plan.py` performs the full out-of-band loop per
module — credential-offer delivery (`exposed.credential_offer_endpoint` +
`?credential_offer=<urlencoded>`), front-channel browser visits
(`urlsWithMethod` → fetch with redirect → `POST /api/runner/browser/{id}/visit`),
implicit submit (`implicit_submit.fullUrl` from `GET /api/log/{id}`), and
verifier-style URI inputs (`uriInputRequests` + `--verifier-start-url`: the
redirect produced by the edge's `GET /{tenant}/vp/authorize` is delivered to
the module's submit URL). Out-of-band fetches are restricted to an origin
allowlist (suite API + offer URL + `--allow-host`) with per-hop redirect
revalidation and resolved-IP class checks.

Local run:

```bash
python3 didvc/scripts/drive-openid-plan.py \
  --plan-name oid4vci-1_0-issuer-test-plan \
  --variant '{"fapi_profile":"vci","client_auth_type":"private_key_jwt","credential_format":"sd_jwt_vc","fapi_request_method":"unsigned","sender_constrain":"dpop","authorization_request_type":"simple","openid":"plain_oauth","fapi_response_mode":"plain_response","vci_grant_type":"authorization_code","vci_authorization_code_flow_variant":"issuer_initiated","vci_credential_encryption":"plain"}' \
  --config-file <(echo '{"description":"hkt-didvc issuer","vci":{"credential_issuer_url":"http://localhost:8081/hkt","credential_configuration_id":"hkt_kyc_v1","credential_offer_endpoint":"http://localhost:8081/hkt/credential-offer"},"client":{"client_id":"hkt-didvc-wallet","dpop_signing_alg":"ES256"},"client2":{"client_id":"hkt-didvc-wallet-2","dpop_signing_alg":"ES256"}}')
```

Manual per-module API (what the driver automates): `POST
api/runner?test=<name>&plan=<id>` → `POST api/runner/<module-id>` (start) →
drive the out-of-band duties above → long-poll
`GET api/runner/<module-id>/wait-state?states=FINISHED,INTERRUPTED` →
`GET api/log/<module-id>` for the result (a module is *clean* when it is
FINISHED **and** its log contains no `result: FAILURE/ERROR` events).

## Verified results (local run, suite 5.2.4)

`oid4vci-1_0-issuer-test-plan`, 15 modules, **15/15 FINISHED** (2026-08-28,
after the DPoP sender-constraining, nonce-endpoint and cnf-binding fixes):

| Module | Result |
|---|---|
| oid4vci-1_0-issuer-metadata-test | FINISHED (5 tolerated errors: plain-HTTP https/TLS checks only) |
| oid4vci-1_0-issuer-metadata-test-signed | FINISHED (skip: unsigned metadata) |
| oid4vci-1_0-issuer-happy-flow | FINISHED |
| oid4vci-1_0-issuer-happy-flow-additional-requests | FINISHED (3 tolerated errors: TLS 1.0/1.1 + BCP195 checks) |
| oid4vci-1_0-issuer-happy-flow-multiple-clients | FINISHED |
| oid4vci-1_0-issuer-happy-flow-skip-notification | FINISHED |
| oid4vci-1_0-issuer-batch-issuance | FINISHED |
| oid4vci-1_0-issuer-fail-invalid-nonce | FINISHED |
| oid4vci-1_0-issuer-fail-invalid-jwt-proof-signature | FINISHED |
| oid4vci-1_0-issuer-fail-invalid-key-attestation-signature | FINISHED |
| oid4vci-1_0-issuer-fail-missing-proof | FINISHED |
| oid4vci-1_0-issuer-fail-unsupported-encryption-algorithm | FINISHED |
| oid4vci-1_0-issuer-fail-unknown-credential-configuration | FINISHED |
| oid4vci-1_0-issuer-fail-unknown-credential-identifier | FINISHED |
| oid4vci-1_0-issuer-fail-on-access-token-in-query | FINISHED |

Every remaining log error is a plain-HTTP artifact of the local run
(`VCIEnsureHttpsUrlsMetadata`, `VCIValidateAuthorizationServersAreHttps`,
`VCIValidateCredentialIssuerUri`, `CheckDiscEndpointAllEndpointsAreHttps`,
`EnsureTLS12RequireBCP195Ciphers`, `DisallowTLS10/11`) — these cannot pass
against `http://localhost` and are exercised over the https cloudflared
tunnel in CI. The driver exits non-zero on any FAILURE/ERROR event unless
`--tolerate-test-errors` is given (the runner script adds it automatically
for `://localhost` suites).

Two fixes were needed to reach a clean 15/15: the credential endpoint now
extracts the holder key from both the singular `proof.jwt` and the
1.0-final plural `proofs.jwt[]` request shapes (the issued SD-JWT carries
`cnf.jwk` bound to the proof key), and the AS metadata advertises
`authorization_details_types_supported: ["openid_credential"]`.

The driver must perform the suite's out-of-band steps per module:
credential-offer delivery (query param), the browser step
(`GET /api/runner/browser/{id}` → follow each pending URL → `POST
.../visit`), and the fragment submission (POST the empty fragment to the
`implicit_submit.fullUrl` from the module log). The plan itself is marked
alpha by the OpenID Foundation.

The `oid4vp-1final-verifier-test-plan` was created (11 modules) but the
suite's verifier flow requires a browser-redirect authorize flow that the
edge's API-only verifier does not yet implement; the modules stall at the
verifier initiation step.
