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

# DID-VC security review — Phase 8 (T-8.3)

Scope: the externally reachable surface of `didvc-edge` (OID4VCI issuer,
OID4VP verifier, internal offer API), reviewed against the OWASP API
Security Top 10 (2023) and the OAuth/OIDF threat models (RFC 6749 §10,
RFC 9449 §11, RFC 9901 §10). The OSGi platform side (`didvc-rest`,
`didvc-services`) is assumed deployed inside the trusted network behind
Unomi's existing auth; its findings are noted where relevant.

Review type: source review during Phase 8 conformance hardening, plus the
protocol-level fixes the conformance suite forced. **No third-party
penetration test has been performed yet** — see "Open items" F-10.

## Findings fixed in this phase

| # | Finding | Fix |
|---|---|---|
| F-1 (fixed) | **DPoP proofs were never validated** (API1/BOLA-adjacent, RFC 9449 violation): any access token could be replayed with any key; sender-constraining was cosmetic. Consequence: the conformance multiple-clients module's cross-client DPoP check failed. | `DpopProofValidator` (RFC 9449): typ/jwk header, htm/htu binding with port normalization, iat window, jti single-use cache, `ath` binding to the access token, jkt recorded per token and enforced on `/credential`, `/batch-credential`, `/deferred-credential`; `token_type: DPoP` returned. |
| F-2 (fixed) | **Proof nonce was optional even when a c_nonce had been issued** (OID4VCI violation): a proof omitting the nonce was accepted. | `validateProof` now rejects missing/mismatched nonces with `invalid_nonce` and attaches a fresh registered `c_nonce` to the error; the nonce endpoint mints registered nonces (single-use, TTL 1 h, Redis-compatible). |
| F-3 (fixed) | **Authorization-code issuance never bound the holder key** (`cnf.jwk` missing) — the issued credential was not bound to the proof key. | The auth-code path now threads the proof's public JWK into the issue request. |
| F-4 (fixed) | **Status list was unfetchable** (credentials referenced a URN): wallets could not check revocation. | `GET /{tenant}/status-lists/{id}` serves an OTSL `statuslist+jwt` (zlib `lst`, embedded public JWK, `application/statuslist+jwt`); demo issuance embeds the fetchable URI. |
| F-5 (fixed) | **OID4VP request objects were HS256-signed with a boot-random secret** — no wallet could verify them; the browser flow was absent. | Request objects are signed EdDSA/ES256 with the verifier key (published at `/{tenant}/.well-known/jwks.json`); `GET /{tenant}/vp/authorize` implements the redirect-based web-wallet flow with server-generated nonce/state; KB-JWT `aud` is now the verifier client_id. |
| F-6 (fixed) | **sd_hash did not follow RFC 9901 §4.3.1** (missing JWT prefix and trailing tilde) — KB-JWTs were bound to less material than the spec requires. | Digest now covers `<JWS>~<d1>~…~<dn>~`; verified against the RFC 9701→9901 test vectors (see `Rfc9901VectorTest`). |

## Open findings (triaged)

| # | Severity | Finding | Recommendation |
|---|---|---|---|
| F-7 | Medium | `/authorize` and `/par` accept **any `redirect_uri` and `client_id`** — no client registry or allow-list (RFC 6749 §3.1.2.3). Open-redirect/code-interception risk when the edge is internet-facing. | Production deployments must front the issuer with a client-registration layer (didvc-rest admin surface) and enforce exact-match redirect URIs. Demo/conformance profile accepts this by design. Track as pre-GA hardening. |
| F-8 | Medium | Credential-request proofs: **`aud`/`iss` claims are not validated** (OID4VCI requires `aud` = credential issuer). A proof audience for another issuer is accepted. | Add strict `aud` check once wallet behaviour is confirmed against the conformance suite (kept out of this phase to avoid regressing the 15-module run). Low blast radius when added. |
| F-9 | Low | Internal API key (`didvc.edge.internal-api-key`) compared with non-constant-time equality; demo external-issuer JWK passed as a property. | Use constant-time compare; feed keys from env/vault only (see operator runbook — never config literals). |
| F-10 | Low | `accessTokens`, `preAuthCodes`, `parRequests` maps have no eviction — long-running instances accumulate expired entries (unbounded memory). | Add TTL sweeper; not reachable below internal network today. |
| F-11 | Low | `direct_post` failures return plain-text 400s, not RFC 9449 error JSON (`invalid_vp_token` etc.) — leaks no data but hampers client error handling. | Standardise error bodies. |
| F-12 | Informational | Verifier `GET /vp/authorize` redirects to a caller-supplied `wallet_authorization_endpoint` (validated only as a URL). As a browser-facing redirect this is an open-redirect vector. | Restrict to configured wallet endpoints per tenant before exposing the endpoint publicly. |
| F-13 | Informational | Demo-only trust surfaces (`--spring.profiles.active=demo`, `InMemoryPlatformApi`, `demoIssuerKid`) must never run in production. | Enforce profile allow-list at deployment (runbook checklist). |

## OWASP API Top-10 (2023) mapping

- **API1 BOLA** — mitigated by DPoP jkt enforcement (F-1 fix), per-tenant token scoping, nonce single-use.
- **API2 Broken authentication** — token endpoint is PKCE-only by design (public clients); client authentication (private_key_jwt/client attestation) is accepted-but-unverified (F-7). Internal API guarded by header key (F-9).
- **API3 Broken property-level authorization** — verification responses expose disclosed claims only; pairwise pseudonyms for subjects; profile resolution never leaves the platform.
- **API4 Unrestricted resource consumption** — load-test results (see `performance.md`) bound per-request cost; F-10 remains.
- **API5/6** — internal admin API is network-scoped + keyed; no mass-assignment surfaces (DTOs are explicit).
- **API7 SSRF** — the edge performs no server-side fetches of user-supplied URLs (DID resolution goes through the platform API); the *tooling* (`drive-openid-plan.py`, `load-test.ts`) applies strict origin allowlists with IP-class validation.
- **API8 Misconfiguration** — TLS/Redis AUTH/vault guidance in the operator runbook.
- **API9/10** — inventory: this document plus the runbook; audit log covers issuance/verification/revocation.

## Penetration testing

Not yet performed. Required before GA (per T-8.3 acceptance): third-party
pen test of the edge in a production-like profile, plus dependency
scanning in CI (OWASP dependency-check or Dependabot) for the
`nimbus-jose-jwt`/`spring-boot` chains.

Review status: engineering review complete; external pen test pending
(T-8.3 acceptance criteria partially met — see TODO.md).
