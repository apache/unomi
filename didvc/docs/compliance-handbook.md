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

# Compliance Handbook — Hong Kong Context

Compliance notes per credential type for the DID-VC module, mapped to the
module's privacy architecture and the build plan's requirement IDs
(`FR-*`, `G*`, `D*` — see `.local-notes/hkt-did-vc/TODO.md` and
`ARCHITECTURE.md`). Items from phases 4–7 are marked **planned**; nothing
here is legal advice — W-1 (legal perimeter review) is the governing
workstream and must sign off before GA.

## 1. Privacy architecture overview (what compliance builds on)

- **Selective disclosure (SD-JWT, RFC 9901)** — credentials are issued as
  `dc+sd-jwt`: sensitive claims are salted digests until the holder
  discloses them; verification responses return **disclosed claims only**.
- **Pairwise pseudonyms per verifier** — `PairwiseBindingService` gives
  each verifier a different opaque reference (`didvc:pairwise:<random>`)
  for the same subject. Two verifiers cannot correlate subjects through
  the credential layer (implemented, T-3.3; FR-C4/G4).
- **Verifier-scoped opaque references; profile resolution never exposed
  over the edge** — the reverse mapping (reference → profile id) exists
  only inside the platform (`PairwiseBindingService.resolveProfileId`);
  there is deliberately **no REST endpoint** for it. Re-identification is
  designed as a two-custodian compliance workflow (split-knowledge,
  FR-G4).
- **Claim whitelists at issuance** — schemas enumerate the only claims a
  credential type may carry; raw PII is rejected when not whitelisted
  (implemented, T-1.5; FR-D1/C1).
- **Boolean/claim-level responses** — verifiers receive validity and the
  disclosed claims they asked for, never the underlying dataset
  (DCQL-scoped, implemented; zero-PII GBA variant planned T-5.2/FR-D3).

## 2. PDPO data-minimisation principles mapping

Hong Kong Personal Data (Privacy) Ordinance, six Data Protection
Principles (DPP1–DPP6):

| DPP | Principle | Module control | Status |
|---|---|---|---|
| DPP1 | Purpose and manner of collection — lawful, fair, purpose-notified | Purposes embodied in per-credential-type schemas and consent grants scoped to verifier **categories** (`ConsentGrantRecord.subjectId × schemaId × verifierCategory`); holders grant per claim set | Implemented (T-2.2 consent gating, FR-CS1) |
| DPP2 | Accuracy and retention | Credential `exp` (bounded `validityDays`, default 365); `CredentialRefreshService` marks refresh-due 90 days before expiry and on identity change (SIM re-registration); revocation removes reliance | Implemented (T-2.4); scheduled sweeping on the Unomi scheduler is deferred to runtime (T-2.4 note) |
| DPP3 | Use limitation — no new purposes without consent | Disclosure checked against consent grants at issuance, and disclosure at verification never exceeds the granted claim set (SD-JWT selective disclosure + DCQL scope) | Implemented (FR-CS1/CS2) |
| DPP4 | Security | Private keys never persisted/logged (public JWKs only; HSM/KMS custody planned T-7.3); TLS edge; nonce/replay protection (Redis-backed fleet-wide single-use); hash-chained audit | Partially implemented — HSM, admin RBAC (T-7.1) planned |
| DPP5 | Transparency | Per-tenant issuer metadata well-known documents; audit log records issuance/verification with actor and verifier-scoped subject reference | Implemented (metadata, audit); transparency reporting is a governance-charter item (W-3, planned) |
| DPP6 | Access and correction | Subject access flows through the KYC-evidence custodian; re-identification requires the split-knowledge workflow | **Implemented** (T-7.2: two-custodian `SplitKnowledgeService`, single-use resolution, full step audit) |

**Data minimisation specifically** (FR-D1/D2): the schema whitelist is
the gate — e.g. the real-name credential (`hkt_realname_v1`, planned
T-5.1) is a single boolean claim; the KYC schema carries two mandatory
attestation claims and only optional selective-disclosure identity
attributes.

## 3. HKMA / AMLO considerations for the reusable-KYC credential

For `hkt_kyc_v1` used by HK banks / VATPs (Capital flow, FR-C1–C5):

- **Reusable KYC ≠ transferred CDD responsibility.** The accepting
  institution relies on the credential as *evidence* for onboarding;
  under AMLO (Cap. 615) the accepting FI remains responsible for its own
  CDD measures. Position the credential as an input to, not a
  replacement of, the FI's KYC process; final legal position is W-1's
  to confirm.
- **What the audit log records** (implemented, T-3.5): each issuance
  appends `didvcIssued` and each accepted verification appends
  `didvpVerified` with actor (verifier tenant), a **verifier-scoped
  subject reference**, and a payload of issuer/vct/disclosed claims —
  timestamped and hash-chained. Revocations append `didvcRevoked`.
  This supports "reasonable steps" records of reliance without
  stockpiling identity data.
- **Validity and re-verification** (FR-C5): bounded `exp` plus the
  90-day refresh window and identity-change triggers model the
  annual-refresh / event-driven re-verification pattern AMLO-examiners
  expect.
- **Split-knowledge workflow (T-7.2, designed but not built):** the
  design separates the KYC-evidence custodian (identity half) from the
  credential operator holding pairwise bindings (linkage half);
  re-identification requires a two-custodian, two-step approval flow,
  with both steps audited. Today the linkage half exists inside the
  platform (`PairwiseBindingService`, not REST-exposed) but the approval
  workflow is not implemented — treat manual resolution accordingly and
  log it out-of-band until T-7.2 lands (blocked by W-3 governance
  charter).

## 4. GBA cross-boundary notes (Data flow, planned)

- **StatusList revocation recognition across counterparties** — the
  signed status-list JWT (`POST /didvc/statuslists/{id}/publish`) is the
  interoperability artifact: mainland counterparties that fetch and
  verify the Bitstring Status List themselves recognize revocations at
  their next check. Keep publishing cadence in SLAs with counterparties
  (runbook §7). (Implemented; FR-D5.)
- **Claim-level VP responses (T-5.2, planned)** — boolean/claim-level
  response contract for GBA counterparties: valid/invalid, claim type,
  expiry — response contains **zero PII** (FR-D3). Golden-path +
  negative ITs are the acceptance criteria.
- **SCC filing audit exports (T-5.3, implemented)** — audit exports for
  continuing bilateral-filing obligations under the GBA SCC
  (Standard Contract) regime; export format must match the filing
  template fields (FR-D4). GBA SCC remains voluntary and excludes
  "important data" — the design operates within these boundaries
  (architecture risk register #5); `hkt_gbascc_v1` boundary check is a
  W-1 deliverable.
- **GB/Z 185 interop bridge (T-7.4, planned)** — dual verification
  pipeline for linkage VPs with trust mapping to per-tenant policies;
  every bridge call appends an audit record (accountability surface,
  FR-ID6). Red Date partnership (W-2) gates the mainland adapters.

## 5. Revocation policy duties

- Revocation is the "kill switch" (FR-D5): `DELETE
  /didvc/credentials/{id}` flips the status bit — effective at the next
  verification; republish the signed list for direct-fetching
  counterparties (runbook §8).
- Approval authority: the revocation-authority matrix is a governance
  charter deliverable (W-3); admin RBAC on revocation APIs is planned
  (T-7.1, FR-G3). Until then restrict admin REST at the gateway and log
  operator actions out-of-band.
- Duties to expect: subject withdrawal (DPP3/consent withdrawal →
  revoke + refresh), evidence invalidation (SIM re-registration trigger,
  implemented), regulatory instruction, fraud. Each path should be
  rehearsed via the revocation runbook.

## 6. Audit evidence — hash-chained tamper detection

- Every lifecycle event appends to the audit log
  (`didvcIssued`/`didvcRevoked`/`didvpVerified`/`didvcOfferSent`).
  Each record's SHA-256 hash covers
  `seq | prev_hash | event_type | actor | subject_ref | payload | created_at`,
  chained from `genesis` (implemented and verified against live
  PostgreSQL, T-3.5; FR-G1/D4/L4).
- **How to run verification:** call
  `AuditLogService.verifyChain()` against the store — it recomputes
  sequence numbers, predecessor links and per-row hashes and reports any
  mutation. Schedule it as an ops job on the JDBC store
  (`didvc_audit_log`). A SQL equivalent recomputation is described in
  the runbook §9. Production stores must be append-only (no
  UPDATE/DELETE grants) — the hash chain detects mutation regardless,
  providing the evidence trail for regulators.

## 7. Metering / billing records privacy

`VerificationMeteringRecord` carries: `eventId`, `verifierTenantId`
(billed partner), `issuerDid`, `vct`, `subjectRef`, `occurredAt`,
`amountMinorUnits`, `currency`. `subjectRef` is the **verifier-scoped
pairwise pseudonym — never the subject's profile identifier** — so the
billing stream (Kafka topic `didvc-metering`, keyed by `eventId` for
idempotency) contains **no PII** (implemented, T-3.4; FR-C3). Retention
of billing records is a commercial/finance matter and can follow normal
financial-records retention without creating a parallel identity
dataset.

## 8. Credential-type compliance matrix

| Credential (vct) | Flow / regimes | Required controls | Req IDs | Status |
|---|---|---|---|---|
| `hkt_kyc_v1` — reusable KYC | Capital: HK banks/fintechs/VATPs; AMLO CDD-reliance, HKMA expectations, PDPO | Claim whitelist + selective disclosure; consent grants per verifier category; pairwise pseudonyms; bounded validity + refresh triggers; per-verification audit + metering; split-knowledge re-identification | FR-C1–C5, G1, G4, D5, CS1/CS2 | **Implemented** (T-2.4, T-3.3–3.5); split-knowledge workflow implemented (T-7.2) |
| `hkt_profcred_v1` — professional credential | People: employers/universities; PDPO (minimal personal data) | Coded qualification claims, no transcripts; SD-JWT selective disclosure; trust entries per verifier | FR-P1 | **Planned** (T-4.3) |
| `hkt_residency_v1` — residency | People: cross-border talent/residency checks; PDPO, immigration-data sensitivity | Status-not-document design (no visa numbers); short claim set; expiry-driven refresh | FR-P1 | **Planned** (T-4.3) |
| `hkt_licensed_institution_v1` / `hkt_realname_v1` — KYB / real-name | Data: GBA counterparties; GBA SCC boundaries, "important data" exclusion | Strict claim minimization (boolean real-name); **schema validation rejects embedded registry data**; zero-PII claim-level responses; SCC filing audit exports | FR-D1–D4 | **Implemented** (T-5.1–5.3: schema bootstraps with registry-data rejection, zero-PII claim-level VP responses, `GET /{tenant}/scc/filing-export`); status-list revocation recognition already implemented |
| `hkt_agent_binding_v1` — agent binding | Agentic-ID: HK/mainland service gateways, GB/Z 185 interop | Key-to-principal binding at issuance; per-call VP verification; audit record per call (accountability surface) | FR-ID6 | **Planned** (T-7.6, with T-7.4 bridge) |

Cross-cutting: PDPO DPP1–DPP6 mapping (§2), governance (W-3 charter:
trust-registry accreditation, revocation authority, split-knowledge
oversight, transparency reporting), legal perimeter review W-1 blocks
T-2.4/T-5.3/T-7.2 GA.

## 9. Evidence inventory for audits

| Evidence | Where | Produced by |
|---|---|---|
| Issuance/verification/revocation records (hash-chained) | `didvc_audit_log` (PostgreSQL) | Every lifecycle event (implemented) |
| Verification billing records (no PII) | Kafka `didvc-metering` | Per accepted verification (implemented) |
| Trust-registry decisions | `didvc-trust-entry` items (platform persistence) | Trust admin (implemented; accreditation process = W-3) |
| Consent grants | `didvc-consent-grant` items | ConsentBridge (implemented) |
| Key custody records | `didvc-key-descriptor` (public JWKs, rotation windows) | IssuerKeyService (implemented); HSM/KMS logs planned (T-7.3) |
| Schema registry (minimization evidence) | `didvc-schema` items | CredentialSchemaService (implemented) |
| SCC filing exports | implemented (T-5.3) | `GET /{tenant}/scc/filing-export` from the immutable audit log |

---

Review status: pending ops + legal sign-off (T-8.4 acceptance criteria)
