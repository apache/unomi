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

# Tenant Onboarding Guide

Onboarding a new **issuer** or **verifier** tenant onto the DID-VC stack.
Examples use a local stack: platform REST at `http://localhost:8181`,
edge at `http://localhost:8081` (see
[operator-runbook.md](operator-runbook.md) for stack bring-up). Schema
design guidance lives in
[credential-schema-cookbook.md](credential-schema-cookbook.md).

## 1. Concepts

- **Tenant** — every issuer business unit and every relying party is a
  Unomi tenant with its own API keys and isolated items. On the edge, the
  tenant is the **URL segment**: `/{tenantId}/.well-known/openid-credential-issuer`,
  `/{tenantId}/token`, `/{tenantId}/vp/authorize`, etc.
- **Trust registry entries** — a verifier tenant accepts credentials of a
  given `vct` from a given issuer DID at an accreditation level within a
  validity window (`didvc-trust-entry` items). Checked on **every**
  verification: entries count only when `status=active` and
  `validFrom ≤ now < validUntil`.
- **Pairwise bindings** — subjects are referenced outside the platform by
  a **verifier-scoped opaque reference**
  (`didvc:pairwise:<random>`, `didvc-pairwise-binding` items). Two
  verifiers get different references for the same subject; profile
  resolution (reference → profile id) is deliberately **not exposed over
  REST** — the identity half stays inside the platform.

## 2. Step-by-step (issuer tenants)

### Step 1 — Create the tenant DID (did:web)

`POST /didvc/dids` creates a did:web DID and generates its first signing
key in one call (`DidService.createDid` → `IssuerKeyService.generateKey`).

Choose the domain carefully: the DID identifier is derived from it
(`did:web:<domain>[:<path segments>]`) and must match the web origin that
will serve the DID document. `algorithm` is `EdDSA` (default) or `ES256`.

```bash
curl -s -X POST http://localhost:8181/didvc/dids \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"hkt","domain":"id.example.hkt","path":"issuers/kyc","algorithm":"EdDSA"}'
```

The response is the DID document data, including the new verification
method. With a `path` of `issuers/kyc`, the resulting identifier is
`did:web:id.example.hkt:issuers:kyc` (did:web encodes `/` as `:`).
Related lifecycle calls:

```bash
# resolve (also usable to confirm creation)
curl -s http://localhost:8181/didvc/dids/did:web:id.example.hkt:issuers:kyc
# list DIDs for a tenant
curl -s 'http://localhost:8181/didvc/dids?tenantId=hkt'
# rotate the signing key (generates a new key, updates the document)
curl -s -X POST http://localhost:8181/didvc/dids/did:web:id.example.hkt:issuers:kyc/rotate \
  -H 'Content-Type: application/json' -d '{"algorithm":"EdDSA"}'
# deactivate
curl -s -X DELETE http://localhost:8181/didvc/dids/did:web:id.example.hkt:issuers:kyc
```

### Step 2 — Generate / rotate issuer keys

Key generation for a tenant DID happens via DID creation or rotation
(Step 1). Key facts for onboarding:

- `kid` = **JWK thumbprint** (RFC 7638) of the key — used verbatim as the
  signing key reference in issuance requests and status-list publishing.
- Only the **public JWK** is persisted (`didvc-key-descriptor` item);
  private material lives in the in-process provider (the HSM/KMS
  replacement point — see runbook §6). After a platform restart, keys
  must be re-loaded from the provider before signing works again.
- Default rotation window: **180 days** (`rotationDueDate`). Schedule
  rotation via the `rotate` endpoint above.

If you need the `kid` for later steps, resolve the DID document and read
the verification method's key id.

### Step 3 — Publish the DID document (/.well-known/did.json)

The platform serves the document at:

```bash
curl -s 'http://localhost:8181/.well-known/did.json?did=did:web:id.example.hkt:issuers:kyc'
```

Until Host-based did:web resolution lands, the DID is supplied as a query
parameter. For production did:web compliance, front this endpoint with
the domain's own web server at the path the DID implies
(`https://id.example.hkt/issuers/kyc/did.json` for the path-qualified
form, `https://id.example.hkt/.well-known/did.json` for a bare-domain
DID) so third-party resolvers can fetch it.

### Step 4 — Register the credential schema

`POST /didvc/dids`-side registries: schemas are `didvc-schema` items with
a claim whitelist (see the cookbook for design rules and worked
examples).

```bash
curl -s -X POST http://localhost:8181/didvc/schemas \
  -H 'Content-Type: application/json' \
  -d '{
    "itemId": "hkt-kyc-v1",
    "name": "Reusable KYC",
    "vct": "hkt_kyc_v1",
    "description": "Minimal reusable-KYC attestation",
    "allowedClaims": ["kycLevel","sanctionsClear","givenName","nationality"],
    "requiredClaims": ["kycLevel","sanctionsClear"],
    "claimTypes": {"kycLevel":"string","sanctionsClear":"boolean",
                   "givenName":"string","nationality":"string"},
    "tenantId": "hkt"
  }'

curl -s http://localhost:8181/didvc/schemas/hkt-kyc-v1
curl -s 'http://localhost:8181/didvc/schemas?tenantId=hkt'
curl -s -X DELETE http://localhost:8181/didvc/schemas/hkt-kyc-v1
```

Claims outside `allowedClaims` are **rejected at issuance** — the
data-minimization gate.

### Step 5 — Configure credential issuance

Two paths:

**(a) Rule-driven (event-driven issuance, the CDP pattern).** Deploy an
Unomi rule that fires the `issueCredential` action executor
(`didvcIssueCredentialAction`) on a source event (e.g.
`kycVerificationSucceeded`). Action parameters:

| Parameter | Meaning |
|---|---|
| `schemaId` | required — schema item id |
| `kid` | required — issuer signing key id |
| `verifierCategory` | consent-grant scope, e.g. `financial-institution` |
| `validityDays` | credential validity (default 365) |
| `claimsJson` | JSON object of claim values sourced from the event/profile |
| `selectiveClaims` | claim names that remain selectively disclosable (SD-JWT); the rest are always-disclosed |

**(b) Direct issuance via REST** (also what the edge calls):

```bash
curl -s -X POST http://localhost:8181/didvc/credentials \
  -H 'Content-Type: application/json' \
  -d '{
    "tenantId": "hkt",
    "schemaId": "hkt-kyc-v1",
    "subjectId": "didvc:pairwise:abc123...",
    "subjectType": "profile",
    "kid": "<kid>",
    "verifierCategory": "financial-institution",
    "validityDays": 365,
    "alwaysDisclosedClaims": {"kycLevel":"REMOTE_FULL","sanctionsClear":true},
    "selectivelyDisclosedClaims": {"givenName":"DAI SIU MING","nationality":"HK"}
  }'
```

`subjectId` may be a profile id or an opaque pairwise reference (see
Step 8). Issuance runs the full pipeline: schema whitelist → consent
check → status-index allocation → SD-JWT formatting → persistence →
`didvcIssued` event.

**Consent grants** gate disclosure per subject × schema × verifier
category. Without a covering grant, issuance of claims beyond the granted
set fails:

```bash
curl -s -X POST http://localhost:8181/didvc/consent-grants \
  -H 'Content-Type: application/json' \
  -d '{
    "itemId": "grant-hkt-kyc-fi-001",
    "subjectId": "didvc:pairwise:abc123...",
    "schemaId": "hkt-kyc-v1",
    "verifierCategory": "financial-institution",
    "claims": ["kycLevel","sanctionsClear","givenName","nationality"],
    "grantedAt": "2026-08-28T00:00:00Z[UTC]"
  }'
```

### Step 6 — Status list setup

Optional: issuance auto-creates a default `revocation` list (size 1024)
per tenant/issuer. Create one explicitly (e.g. a separate suspension
list) and publish the signed list for external verifiers:

```bash
curl -s -X POST http://localhost:8181/didvc/statuslists \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"hkt","issuerDid":"did:web:id.example.hkt","statusPurpose":"revocation","size":1024}'

curl -s http://localhost:8181/didvc/statuslists/<statusListId>

curl -s -X POST http://localhost:8181/didvc/statuslists/<statusListId>/publish \
  -H 'Content-Type: application/json' -d '{"kid":"<kid>"}'

curl -s 'http://localhost:8181/didvc/statuslists/<statusListId>/revoked?index=7'
```

### Step 7 — Trust entries (verifier tenants)

A verifier tenant registers which issuer/vct pairs it accepts. Entry
fields: `issuerDid`, `vct`, `accreditationLevel`, `validFrom`,
`validUntil`, `status` (`active`/`revoked`); the entry's `tenantId` is
the **verifier** tenant.

```bash
# register acceptance
curl -s -X POST http://localhost:8181/didvc/trust-entries \
  -H 'Content-Type: application/json' \
  -d '{
    "itemId": "trust-bank01-kyc-001",
    "tenantId": "bank01",
    "issuerDid": "did:web:id.example.hkt",
    "vct": "hkt_kyc_v1",
    "accreditationLevel": "primary",
    "validFrom": "2026-08-28T00:00:00Z[UTC]",
    "validUntil": "2027-08-28T00:00:00Z[UTC]",
    "status": "active"
  }'

# list and check
curl -s 'http://localhost:8181/didvc/trust-entries?verifierTenantId=bank01'
curl -s 'http://localhost:8181/didvc/trust-check?verifierTenantId=bank01&issuerDid=did:web:id.example.hkt&vct=hkt_kyc_v1'
# → {"trusted": true}
curl -s -X DELETE http://localhost:8181/didvc/trust-entries/trust-bank01-kyc-001
```

### Step 8 — Pairwise bindings

Create the verifier-scoped opaque reference for a subject (used as the
credential `subjectId` / `sub`):

```bash
curl -s -X POST http://localhost:8181/didvc/pairwise-bindings \
  -H 'Content-Type: application/json' \
  -d '{"profileId":"profile-42","verifierTenantId":"bank01"}'
# → {"opaqueReference": "didvc:pairwise:..."}
```

The reverse mapping (reference → profile) exists only inside the platform
service; there is deliberately **no REST endpoint** for it
(split-knowledge design, FR-G4 — see compliance handbook).

## 3. Edge integration (issuer side)

Per-tenant well-known metadata is served by the edge — point wallets at
it:

```bash
curl -s http://localhost:8081/hkt/.well-known/openid-credential-issuer
curl -s http://localhost:8081/hkt/.well-known/oauth-authorization-server
```

Create a credential offer from your backend (internal endpoint, guarded
by `X-Api-Key` = `didvc.edge.internal-api-key`; provision the key out of
band and export it — never a committed literal):

```bash
export DIDVC_INTERNAL_API_KEY="$(openssl rand -hex 24)"
curl -s -X POST http://localhost:8081/hkt/internal/offers \
  -H "X-Api-Key: $DIDVC_INTERNAL_API_KEY" -H 'Content-Type: application/json' \
  -d '{
    "schemaId": "hkt-kyc-v1",
    "vct": "hkt_kyc_v1",
    "subjectId": "didvc:pairwise:abc123...",
    "kid": "<kid>",
    "verifierCategory": "financial-institution",
    "alwaysDisclosedClaims": {"kycLevel":"REMOTE_FULL","sanctionsClear":true},
    "selectivelyDisclosedClaims": {"givenName":"DAI SIU MING"}
  }'
```

The response is a credential offer (pre-authorized code grant) to deliver
to the wallet (QR / deep link). The wallet then completes the OID4VCI
flow: `POST /{tenant}/token` (pre-authorized-code or authorization-code
grant with PKCE) → `POST /{tenant}/credential` (or `/batch-credential`,
`/deferred-credential`, `/nonce`). Codes and tokens are ephemeral
(10-minute TTL, held in edge memory).

## 4. Verification flow setup (verifier tenants)

1. **Register trust entries** for the issuer/vct pairs you accept
   (Step 7) — verification enforces this on every presentation.
2. **Create an authorization request** (plain claims map **or** DCQL
   query — the preferred format):

```bash
curl -s -X POST http://localhost:8081/bank01/vp/authorize \
  -H 'Content-Type: application/json' \
  -d '{
    "clientId": "bank01-portal",
    "responseUri": "https://bank01.example.hk/vp/callback",
    "nonce": "n-4711",
    "dcql_query": {
      "credentials": [
        {"id": "kyc", "format": "dc+sd-jwt", "vct": "hkt_kyc_v1",
         "claims": [{"path": ["kycLevel"], "values": ["REMOTE_FULL"]},
                    {"path": ["sanctionsClear"]}]}
      ]
    }
  }'
# → {"request_uri": "http://localhost:8081/bank01/vp/request/<id>"}
```

3. **Hand the signed request object to the wallet** (it is an
   HS256-signed JAR at the returned `request_uri`, 10-minute TTL).
4. **Receive the presentation** at your `response_uri` or relay it:

```bash
curl -s -X POST http://localhost:8081/bank01/vp/direct_post \
  -H 'Content-Type: application/json' \
  -d '{"state":"<request id>","nonce":"n-4711","vp_token":"<SD-JWT presentation>"}'
```

A valid response is `{"valid": true, "issuer": ..., "vct": ...,
"subject": ..., "claims": {disclosed claims only}, "alwaysDisclosed":
{...}}` — never the underlying dataset. The edge checks signature (issuer
DID key), time validity, revocation (status list), trust registry, and
key binding (holder possession, nonce/audience/replay, fleet-wide
single-use nonce). Rejections return HTTP 400 with the specific reason
(see runbook §11).

Every accepted verification appends a `didvpVerified` audit record and a
billable metering record.

## 5. Go-live checklist

- [ ] Tenant exists in Unomi with API keys (per-tenant isolation).
- [ ] DID created on the production domain; document reachable at the
      did:web location by third-party resolvers.
- [ ] Signing key `kid` recorded; rotation scheduled (180-day window);
      key-material re-provisioning procedure agreed (HSM/KMS custody,
      T-7.3 planned).
- [ ] Schema(s) registered with minimal `allowedClaims`; required claims
      and types tested (issuance rejects non-whitelisted claims).
- [ ] Consent-grant flow exercised for each verifier category in scope.
- [ ] Status list created; publish cadence and revocation runbook agreed
      (runbook §7–8).
- [ ] Trust entries active with sane validity windows; `trust-check`
      returns true for every issuer/vct you will accept.
- [ ] Edge `didvc.edge.issuer-base-url` set to the public HTTPS URL;
      internal/platform API keys provisioned from vault.
- [ ] End-to-end test: offer → token → credential → wallet →
      `vp/authorize` → `direct_post` accepted; audit + metering records
      observed (JDBC audit store and `didvc-metering` topic in
      production wiring).
- [ ] Redis nonce store enabled if the edge fleet scales beyond one
      instance.
- [ ] Ops + legal sign-off recorded (compliance handbook mapping per
      credential type).

---

Review status: pending ops + legal sign-off (T-8.4 acceptance criteria)
