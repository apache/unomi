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

# Credential Schema Cookbook

Recipes for defining credential schemas on the DID-VC module. Schemas are
managed by `CredentialSchemaService` and stored as `didvc-schema` items;
they are the claim-minimization gate of the whole issuance pipeline.

Status markers: **implemented** = works in the current module (phases
1–3); **planned** = phases 4–7 work from `.local-notes/hkt-did-vc/TODO.md`.

## 1. The didvc-schema item model

| Field | Type | Meaning |
|---|---|---|
| `itemId` | string | Schema identifier (required) — the handle used in issuance requests and offers |
| `name` | string | Human-readable schema name |
| `vct` | string | SD-JWT Verifiable Credential Type — the value placed in the credential's `vct` claim and the key used by trust entries and DCQL queries |
| `description` | string | Free text |
| `allowedClaims` | set of strings | **Claim whitelist** — the only claims a credential of this schema may carry |
| `requiredClaims` | set of strings | Claims that must be present and non-null at issuance |
| `claimTypes` | map claim → JSON type | One of `string`, `number`, `boolean`, `array`, `object` |
| `tenantId` | string | Owning issuer tenant |

CRUD via `POST /didvc/schemas`, `GET /didvc/schemas[/{schemaId}?tenantId=]`,
`DELETE /didvc/schemas/{schemaId}` (see onboarding guide Step 4 for curl).

## 2. Claim whitelisting and data minimization

The whitelist is the structural privacy control (FR-D1/D2, FR-C1):

- At issuance, `CredentialSchemaService.validateClaims` runs over the
  merged claim set (always-disclosed + selectively-disclosed). **Any
  claim not in `allowedClaims` is rejected** with
  *"Claim 'x' is not in the allowed claim set of schema … — raw
  attributes must be mapped to whitelisted claims before issuance"*.
  Raw PII therefore never enters a credential payload by accident.
- `requiredClaims` must be present and non-null, else issuance fails.
- `claimTypes` mismatches fail issuance
  (*"Claim 'x' must be of type …"*); an unknown type declared in the
  schema itself is an error.
- Whitelisting runs **before** consent checking: first the schema bounds
  what may ever be carried, then `ConsentBridgeService.verifyDisclosure`
  bounds what may be disclosed for this subject × schema × verifier
  category (disclosure never exceeds the granted claim set, FR-CS1).
- At verification, only **disclosed** claims are returned to the relying
  party (SD-JWT selective disclosure, FR-CS2); DCQL queries can further
  pin claim paths and expected values.

## 3. Worked example 1 — reusable KYC (`hkt-kyc-v1`, implemented)

The reference schema (used by the demo platform, interop round trip and
conformance runs): attestations first, identity attributes optional and
selectively disclosable.

```json
{
  "itemId": "hkt-kyc-v1",
  "name": "Reusable KYC",
  "vct": "hkt_kyc_v1",
  "description": "Reusable-KYC attestation for Capital-flow verification",
  "tenantId": "hkt",
  "allowedClaims": ["kycLevel", "sanctionsClear", "givenName", "nationality"],
  "requiredClaims": ["kycLevel", "sanctionsClear"],
  "claimTypes": {
    "kycLevel": "string",
    "sanctionsClear": "boolean",
    "givenName": "string",
    "nationality": "string"
  }
}
```

Design notes:

- `kycLevel` (e.g. `REMOTE_FULL`) and `sanctionsClear` are the
  **decision claims** verifiers actually query — mandatory, always
  disclosed (in the demo profile they are the always-disclosed set).
- `givenName` / `nationality` are optional **selective-disclosure**
  claims: issued as salted SD-JWT digests, revealed only when the holder
  consents and the DCQL query asks for them.
- No document numbers, addresses, birth dates or any raw KYC evidence —
  those stay in the KYC-evidence custodian's store (see compliance
  handbook, split-knowledge).
- Refresh lifecycle: issue with a bounded `validityDays` (default 365);
  `CredentialRefreshService` marks credentials refresh-due 90 days
  before expiry or on identity change (e.g. SIM re-registration) —
  FR-C5.

## 4. Worked example 2 — professional credential (`hkt_profcred_v1`, T-4.3 done)

People-flow qualification checks by employers/universities (FR-P1).
Follows the KYC pattern — category over document. This schema is
bootstrapped automatically by the platform (`Phase4SchemaBootstrap`),
and the professional-body issuer tenant settings are documented in
[`professional-body-tenant-config.json`](professional-body-tenant-config.json):

```json
{
  "itemId": "hkt-profcred-v1",
  "vct": "hkt_profcred_v1",
  "allowedClaims": ["qualificationCode", "issuingBody", "gradeLevel",
                    "validUntilYear", "registrationRegion"],
  "requiredClaims": ["qualificationCode", "issuingBody", "validUntilYear"],
  "claimTypes": {
    "qualificationCode": "string",
    "issuingBody": "string",
    "gradeLevel": "string",
    "validUntilYear": "number",
    "registrationRegion": "string"
  }
}
```

Coded reference (`qualificationCode`, `issuingBody`) instead of free-text
qualification transcripts; no holder PII beyond what the pairwise
`sub` already provides.

## 5. Worked example 3 — residency (`hkt_residency_v1`, T-4.3 done)

```json
{
  "itemId": "hkt-residency-v1",
  "vct": "hkt_residency_v1",
  "allowedClaims": ["residencyStatus", "jurisdiction", "validUntil"],
  "requiredClaims": ["residencyStatus", "jurisdiction", "validUntil"],
  "claimTypes": {"residencyStatus": "string", "jurisdiction": "string",
                 "validUntil": "string"}
}
```

Statuses as controlled vocabulary (e.g. `permanent-resident`,
`valid-work-visa`) — never visa numbers or identity-document copies.

## 6. Worked example 4 — KYB / real-name (T-5.1 done, strict minimization)

GBA data-flow attestations (FR-D1/D2). Acceptance criterion for the
phase: **schema validation rejects embedded registry data** — the
whitelist must be tight enough that a Companies-Registry-style dump
cannot be smuggled through:

```json
{
  "itemId": "hkt-licensed-institution-v1",
  "vct": "hkt_licensed_institution_v1",
  "allowedClaims": ["licenseClass", "regulated", "licenseValidUntil"],
  "requiredClaims": ["licenseClass", "regulated", "licenseValidUntil"],
  "claimTypes": {"licenseClass": "string", "regulated": "boolean",
                 "licenseValidUntil": "string"}
}
```

```json
{
  "itemId": "hkt-realname-v1",
  "vct": "hkt_realname_v1",
  "allowedClaims": ["realNameVerified"],
  "requiredClaims": ["realNameVerified"],
  "claimTypes": {"realNameVerified": "boolean"}
}
```

The real-name credential is a single boolean claim — the strongest
minimization available. KYB counterparts verify license class and
validity, not registry extracts.

## 7. Status purpose choices

Each credential's status points at a bitstring status list with a
`statusPurpose` (FR-D5):

| Purpose | Semantics | Use when |
|---|---|---|
| `revocation` (default) | Bit set = credential permanently invalid | Kill-switch semantics: fraud, subject request, evidence withdrawn — the module default; every verification consults it |
| `suspension` | Bit set = temporarily invalid (conceptually reversible) | Ongoing investigations, temporary holds — create a separate list with `statusPurpose: "suspension"` |

Notes: issuance auto-allocates on the tenant/issuer default `revocation`
list (created on first use, size 1024); `revoke` flips a bit and takes
effect at the next verification; republishing the signed list
(`POST /didvc/statuslists/{id}/publish`) propagates to counterparties
that fetch the list themselves. A StatusList2021 JWT adapter exists for
older verifiers.

## 8. vct naming conventions

- Pattern: `hkt_<domain>_<version>` — e.g. `hkt_kyc_v1`,
  `hkt_profcred_v1`, `hkt_licensed_institution_v1`,
  `hkt_agent_binding_v1` (T-7.6, planned).
- Lowercase snake_case; the `vct` is the trust-registry and DCQL join
  key, so it must be stable and globally unambiguous across tenants.
- Keep schema `itemId` aligned with the vct but hyphenated
  (`hkt-kyc-v1`) — item types and identifiers use hyphens (colons would
  break Elasticsearch index naming; `context-<type>` parses `:` as a
  cross-cluster reference).

## 9. Versioning and rotation of schemas

- Schemas are items keyed by `itemId`: `POST /didvc/schemas` with an
  existing id overwrites (save is an upsert); deletion is immediate.
- **Non-breaking change** (adding an optional claim, widening
  `claimTypes`) — edit in place; existing credentials are unaffected
  because validation happens at issuance time only.
- **Breaking change** (removing/renaming claims, tightening types) —
  create a **new schema id and vct with a bumped version**
  (`_v2`), register fresh trust entries for the new vct, migrate
  issuance rules/offers, and let existing credentials expire via their
  `exp` rather than re-issuing silently.
- Shrinking `allowedClaims` only affects new issuance; already-issued
  credentials keep verifying on their disclosed claims.
- Record schema changes in the platform audit trail via the normal
  issuance/revocation events; governance charter (W-3) will formalize
  schema-change approval (planned).

## 10. Validation semantics — what the schema service enforces at issuance

1. **Whitelist**: every submitted claim (both disclosure classes merged)
   ∈ `allowedClaims`, else reject.
2. **Required set**: each `requiredClaims` entry present and non-null,
   else reject.
3. **Types**: each claim with a declared `claimTypes` entry must match
   (`string`/`number`/`boolean`/`array`/`object`), else reject; an
   unknown declared type is itself an error.
4. **Consent (after schema checks)**: `verifyDisclosure(subjectId,
   schemaId, verifierCategory, claims)` — the requested claim set must be
   covered by an active consent grant for that subject × schema ×
   verifier category, else reject.
5. Downstream, the formatter (SD-JWT VC, `dc+sd-jwt`) adds
   `vct`/`iss`/`sub`/`iat`/`nbf`/`exp`, status (`status_list.idx`/`uri`)
   and optional `cnf.jwk` holder binding — those are structural claims,
   not schema-whitelisted ones.

The schema service does **not** enforce value vocabularies (e.g. which
`kycLevel` strings are legal) — constrain values with DCQL `values`
matching at verification, or in the issuance rule that builds
`claimsJson`.

## 11. Anti-patterns

- **Embedding registry data** — copying Companies Registry / immigration
  / sanctions-list extracts into claims. Rejected by design in T-5.1
  acceptance; carry coded booleans/classes instead.
- **Over-broad claims** — whitelisting `extraData`, `documents`,
  `metadata` or whole `object`-typed blobs. The whitelist must enumerate
  semantic claims, not pass-through containers.
- **Unique identifiers that defeat pairwise binding** — HKID numbers,
  passport numbers, full legal names or profile ids as claims. Subjects
  are addressed by the verifier-scoped pairwise `sub`; adding a globally
  stable identifier in the payload re-links verifiers and undoes FR-C4/
  G4. (Correlation *within* one verifier's scope is a product decision —
  make it deliberately, never by default.)
- **Free-text where a code suffices** — qualification transcripts,
  address strings. Prefer controlled vocabularies.
- **Optional-but-sensitive by default** — sensitive attributes belong in
  `selectivelyDisclosedClaims` at issuance, never in the always-disclosed
  set.
- **Version bumps in place** — changing `vct` or dropping claims on an
  existing schema id; see §9.

---

Review status: pending ops + legal sign-off (T-8.4 acceptance criteria)
