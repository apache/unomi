<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache Unomi — Threat Model (v0 draft)

## §1 Header

- **Project:** Apache Unomi (`apache/unomi`), `master` / 2.x line, against which this draft was written. This model covers the **apache/unomi** server; `unomi-tracker` (browser tracking client) and `unomi-site` (website) are in the engagement scope but are treated here as satellites (see §2/§3).
- **Date:** 2026-06-02. **Status:** draft — for Apache Unomi PMC review. **Author:** ASF Security team (drafted via the Scovetta threat-model rubric), for PMC ratification.
- **Version binding:** versioned with the project; a report against version *N* is triaged against the model as it stood at *N*.
- **Reporting cross-reference:** §8-property violations → report privately per ASF process (`security@apache.org` → `private@unomi.apache.org`); §3/§9 findings are closed citing this document.
- **Provenance legend:** *(documented)* = Unomi's own docs/repo/CVE advisories; *(maintainer)* = confirmed by an Unomi PMC member through this process; *(inferred)* = reasoned from architecture/history, not yet confirmed — each has a matching §14 open question.
- **Draft confidence:** ~16 documented / 0 maintainer / ~30 inferred.
- **What Unomi is:** Apache Unomi is a Java reference implementation of the OASIS Context Server (CXS) spec — a Customer Data Platform. It collects behavioural events about visitors (typically from a browser via the `unomi-tracker` JavaScript over a public **context** endpoint), builds and stores profiles + segments, evaluates rules/conditions, and exposes data via REST and GraphQL APIs. It persists to Elasticsearch/OpenSearch. *(documented — README, manual)*

## §2 Scope and intended use

- **Primary use:** an operator-deployed **context server** that ingests visitor events over the network and serves profile/segmentation data to web properties and back-office tools. *(documented — manual)*
- **Caller roles** (network service — the role splits):
  - **public web client** — a browser running `unomi-tracker`, hitting the **public context endpoint** (`/context.json`-style) **unauthenticated**, from the open internet. The highest-value untrusted surface. *(inferred — confirm the public-endpoint exposure model)*
  - **integrator / API client** — calls the REST / GraphQL APIs, authenticated; may author conditions, rules, segments, scopes. **Trusted to its credential's authority.** *(inferred)*
  - **operator/admin** — controls config, the Karaf container, plugins, and the Elasticsearch/OpenSearch backend. **Trusted.** *(inferred)*
  - **cluster peer** — another Unomi node. *(inferred)*

**Component-family table:**

| Family | Entry point | Touches outside process | In model? |
| --- | --- | --- | --- |
| Public context ingestion | `/context.json` / event collector (`wab`, `rest`) | network (public listen) | **In — primary boundary** *(inferred)* |
| Rule / condition / segment engine + scripting | `services`, `scripting` (MVEL/OGNL expression eval) | evaluates expressions | **In — historically the RCE surface (§11)** *(documented: CVEs)* |
| JSON-Schema event validation | schema validation of incoming events | — | **In — the input-validation defense** *(documented: manual `jsonSchema`)* |
| Admin REST + GraphQL APIs | `rest`, `graphql` | network (authenticated) | **In** *(documented: modules)* |
| Persistence | `persistence-elasticsearch` / `persistence-opensearch` | network → ES/OS backend | **In (Unomi's use of it); the backend's own security is operator's** *(inferred)* |
| Plugins / extensions / connectors | `plugins`, `extensions`, `connectors` | varies | **In core ones; third-party/`samples` out** *(inferred)* |
| `unomi-tracker` (JS client) | browser | — | **Satellite — discoverability pointer; client-side, lower trust surface** *(inferred)* |
| `unomi-site`, `samples`, `itests` | website / demos / tests | — | **Out** *(see §3)* |

## §3 Out of scope (explicit non-goals)

- **Attackers who already control the host, the Karaf container, the config, the plugins, or the Elasticsearch/OpenSearch backend.** Operator-trusted. *(inferred)*
- **`unomi-site`, `samples/`, `itests/`** — website + demo + test code, not production trust surface. *(inferred)*
- **Confidentiality of profile data at rest / in the search backend** when the operator has not secured Elasticsearch/OpenSearch and the network — that is deployment hardening, not an Unomi code property, unless Unomi claims otherwise. *(inferred)*
- **Arbitrary expression evaluation by a *trusted admin*** who authors a malicious condition/rule — an authenticated privileged user defining server-side logic is the intended (if powerful) feature, not an attack on Unomi. The boundary is whether *public/untrusted* input can reach expression evaluation (see §8/§11). *(inferred — confirm)*

## §4 Trust boundaries and data flow

- **Primary boundary: the public context endpoint.** Event payloads arriving unauthenticated from browsers are **untrusted**. They flow → JSON-Schema validation → event/condition processing → profile update → persistence. The schema-validation step is the gate. *(inferred; schema validation documented)*
- **Secondary boundary: the authenticated REST/GraphQL admin surface**, where conditions/rules/scopes are defined — trusted to the credential. *(inferred)*
- **The historical break (load-bearing):** the public surface must **not** allow attacker-controlled input to reach OGNL/MVEL expression evaluation that can instantiate/call arbitrary Java — that was CVE-2020-11975 / CVE-2020-13942 / CVE-2021-31164, fixed by constraining the public surface. The model treats a regression of this kind as `VALID`/critical. *(documented — CVE advisories)*
- **Reachability precondition:** a finding in `scripting`/condition-evaluation is **in-model** only if reachable from **public/unauthenticated** input (or from a lower privilege than the operation requires). Expression power available only to a trusted authenticated author is `OUT-OF-MODEL: trusted-input`. A finding on the ES/OS backend is in-model only if reachable through Unomi's API, not by directly attacking an exposed backend. *(inferred)*

## §5 Assumptions about the environment

- **Runtime:** JVM; runs in an Apache Karaf / OSGi container. *(documented — kar/package/manual)*
- **Backend:** Elasticsearch or OpenSearch, assumed deployed on a trusted network and secured by the operator. *(inferred)*
- **The public endpoint is internet-facing by design** (browsers post events directly); the admin APIs are assumed *not* public. Confirm. *(inferred)*
- **Negative side-effects inventory** (predominantly inferred — wave-1/2 target): Unomi listens on HTTP; reads config from the Karaf container; talks to the search backend; loads OSGi plugins; evaluates conditions/expressions; the scripting engine executes expression logic authored through the (trusted) admin path. *(inferred)*

## §5a Build-time and configuration variants

Security-relevant configuration knobs *(all inferred — confirm names/defaults against `configuration.adoc`):*

- **Public-endpoint protection / third-party server allow-list + secured events** — the mechanism that distinguishes events a public client may send from those that require a trusted key. Default posture? *(inferred — Unomi has a "protected events" / third-party-server key concept)*
- **JSON-Schema validation** of incoming events — on by default? Reject-unknown by default? *(inferred; feature documented)*
- **Expression/scripting allow-list** (post-CVE) — what restricts which classes/methods conditions may reference, and is it on by default? *(inferred; the CVE fixes introduced restrictions)*
- **Authentication on the admin REST/GraphQL APIs** — default credentials? bound to localhost vs all interfaces by default? *(inferred — the insecure-default question; wave 1)*

**Insecure-default check:** if any of (public-endpoint protection, schema validation, scripting allow-list, admin auth) ships *off* or with a default credential, a report against that default is `VALID` unless the PMC designates it a documented must-configure (`OUT-OF-MODEL: non-default-build`). This is a wave-1 ruling (§14).

## §6 Assumptions about inputs

Per-surface trust table *(all inferred unless noted):*

| Surface | Input | Attacker-controllable? | Caller/operator must enforce |
| --- | --- | --- | --- |
| Public context endpoint | event JSON, profile/session refs, scope | **yes (unauthenticated, public)** | JSON-schema validation on; public-event allow-list; no expression reach |
| REST / GraphQL admin | conditions, rules, segments, queries | **yes, within the authenticated credential's authority** | authn + authz; restrict who may author expressions |
| Condition / rule definitions | MVEL/OGNL expressions | **public: must be no; admin: yes-but-trusted** | keep expression authoring on the trusted side |
| Persistence queries | derived from the above | indirectly | backend hardening; query/scope isolation |
| Plugins / connectors config | operator-supplied | no — operator-trusted | vet third-party plugins |

- **Size/shape/rate:** whether the public endpoint bounds event size / batch count / request rate against a flood is open (see §8 resource line). *(inferred)*

## §7 Adversary model

- **Primary adversary:** an unauthenticated party who can reach the **public context endpoint** from the internet — trying to achieve code execution (the CVE history), read/modify other visitors' profiles, inject events to corrupt segmentation, or exhaust resources. *(documented threat history; framing inferred)*
- **Secondary:** an authenticated API client trying to exceed its authority (read other scopes' data, escalate). *(inferred)*
- **Capabilities:** craft arbitrary event/condition JSON to the public endpoint; replay; send large/malformed payloads. **Not** assumed: control of the admin credential, the container, or the backend. *(inferred)*
- **Out of scope:** trusted admins authoring powerful (even dangerous) conditions; attackers with host/backend control. *(inferred)*

## §8 Security properties the project provides

*(All inferred pending PMC confirmation; the CVE-fix posture is documented history.)*

- **No code execution from public/untrusted input.** Public-endpoint input cannot reach OGNL/MVEL evaluation that instantiates or calls arbitrary Java — the post-CVE invariant. *Violation symptom:* RCE / arbitrary-class invocation from an unauthenticated request. *Severity:* security-critical. *(documented that this class was fixed; the standing guarantee is the claim to confirm)*
- **Input validation at the public boundary.** Incoming events are validated against registered JSON Schemas; non-conforming input is rejected, not processed. *Violation symptom:* unvalidated/unknown event shape reaching processing. *Severity:* security-critical → moderate. *(documented feature; default/strictness to confirm)*
- **Profile/scope access control.** A public client cannot read or modify profile data outside what the context/scope model permits; an API client is bounded by its authority. *Violation symptom:* cross-profile / cross-scope data access. *Severity:* security-critical (data exposure — PII). *(inferred)*
- **Resource bounds — UNSPECIFIED.** Whether a public event flood or an expensive segment/condition is a bug or expected-and-operator-managed is open. *(inferred)*

## §9 Security properties the project does *not* provide

- **No protection if the admin REST/GraphQL APIs are exposed unauthenticated / with default creds** — keeping the admin surface off the public network + authenticated is the operator's job (pending §5a ruling). *(inferred)*
- **No confidentiality/integrity for the ES/OS backend or its network** — Unomi assumes a secured backend; it does not defend an exposed Elasticsearch. *(inferred)*
- **Not a sandbox for admin-authored expressions/plugins** — a trusted author with condition/scripting authority can run server-side logic by design; that power is not contained. *(inferred)* **False friend:** the presence of the scripting/expression allow-list protects the *public* surface; it is not a sandbox that makes arbitrary admin-authored expressions safe.
- **No guarantee of correctness of analytics/segmentation under adversarial event injection** beyond the access-control boundary. *(inferred)*
- **Well-known classes left to the caller/operator:** expression-injection (the CVE class — defended by constraining the public surface), event/PII-exposure via a misconfigured public endpoint, and DoS via event floods. *(documented history; framing inferred)*

## §10 Downstream responsibilities (operator/deployer)

*(All inferred — confirm.)*

- Keep the admin REST/GraphQL APIs **off the public network** and authenticated; change any default credentials. *(inferred)*
- Keep JSON-Schema validation and the public-event allow-list **enabled**; register schemas for the events you accept. *(inferred)*
- Secure the Elasticsearch/OpenSearch backend + its network. *(inferred)*
- Restrict who holds condition/rule/scripting authoring authority — it is equivalent to server-side code definition. *(inferred)*
- Put the public endpoint behind rate-limiting / a CDN/WAF appropriate to public exposure. *(inferred)*

## §11 Known misuse patterns

*(Draft one-liners — expand before publishing.)*

- Exposing the admin REST/GraphQL APIs to the internet (or leaving default creds). *(inferred)*
- Disabling JSON-Schema validation or the public-event allow-list "to make integration easier", re-opening the public surface. *(inferred)*
- Treating the scripting/expression allow-list as a sandbox for admin-authored conditions. *(inferred)*
- Exposing Elasticsearch/OpenSearch alongside Unomi without backend auth. *(inferred)*

## §11a Known non-findings (recurring false positives)

*(Seed list — PMC confirmation here is the highest-leverage scan-suppression input.)*

- "Unomi evaluates OGNL/MVEL expressions → RCE" — by-design for **trusted admin-authored** conditions; the public surface is constrained (post-CVE). A report is `VALID` only if it shows **public/unauthenticated** input reaching expression evaluation; otherwise `OUT-OF-MODEL: trusted-input` / `BY-DESIGN`. *(documented — CVE fixes)*
- "Scripting / reflection present in `scripting` module" — needs the public-reachability test (§4) before it is a finding. *(inferred)*
- "No auth on the context endpoint" — the public ingestion endpoint is unauthenticated **by design**; the protection is schema validation + the event allow-list, not authentication. *(inferred)*
- "Elasticsearch reachable / no TLS" — operator deployment responsibility (§9/§10). *(inferred)*
- "Admin can run dangerous operation X" — out-of-model: admin is trusted (§7). *(inferred)*

## §12 Conditions that would change this model

- A change to the public-endpoint protection model, the JSON-Schema-validation default, the scripting allow-list, or admin-auth defaults. *(inferred)*
- A new public surface or a new expression/scripting capability reachable from untrusted input. *(inferred)*
- Promoting a `samples/` or third-party connector into core. *(inferred)*
- A report that cannot be routed to one §13 disposition → revise the model.

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope adversary/input (public-input code execution; schema-validation bypass; cross-profile/scope access; pre-auth crash). | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property broken, but a §11 misuse is easy enough to harden. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires admin/authenticated authority (e.g. an admin-authored malicious condition) or operator-controlled config/backend. | §6, §7 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires host/container/backend control or another excluded capability. | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in `unomi-site`, `samples/`, `itests/`, or third-party connectors. | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a discouraged/non-default §5a setting. | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9-disclaimed property (no admin-expression sandbox, unauthenticated-by-design context endpoint, backend security). | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a entry. | §11a |
| `MODEL-GAP` | Cannot be cleanly routed — triggers §12. | §12 |

## §14 Open questions for the maintainers

**Wave 1 — scope & default posture:**
1. Confirm the trust split: the **context endpoint is public/unauthenticated by design**, while the **REST/GraphQL admin APIs are not public and are authenticated**. What are the defaults (bind address, default credentials)? Is exposing the admin API or leaving a default credential a `VALID` report or a documented must-configure? → §2/§5a/§7.
2. Confirm `unomi-tracker` is modeled as a client-side satellite and `unomi-site`/`samples`/`itests` are out of scope. → §2/§3.
3. The model covers the apache/unomi server; should `unomi-tracker` get its own (lighter) model later, or a discoverability pointer to this one? → §1.

**Wave 2 — the public boundary & its defenses:**
4. Is **JSON-Schema validation** of incoming events on by default, and does it reject non-conforming/unknown events? Is it the intended primary input defense at the public boundary? → §8.
5. What exactly restricts the **public** surface from reaching OGNL/MVEL expression evaluation today (the post-CVE allow-list / protected-events / third-party-key mechanism) — and is it on by default? → §4/§5a/§8.
6. Are there bounds on public event size / batch / rate, or is flood protection the operator's (WAF/rate-limit) concern? → §8/§11a.

**Wave 3 — expressions, scopes, backend:**
7. Confirm that OGNL/MVEL expression power is **by-design for trusted admin-authored** conditions and is **not** a sandbox — so a finding is `VALID` only when *public/untrusted* input reaches it. → §9/§11a.
8. What is the profile/**scope** isolation model — can an authenticated API client read/modify data outside its scope, and is that boundary something Unomi enforces or the integrator's concern? → §8.
9. Is the Elasticsearch/OpenSearch backend assumed trusted/secured-by-operator (so backend-exposure reports are out-of-model)? → §3/§9.

**Wave 4 — meta & non-findings:**
10. Any other recurring scanner/fuzzer false positives to seed §11a (e.g. the `scripting` module, reflection, OSGi dynamic loading)? → §11a.
11. **Meta:** Unomi has no in-repo `SECURITY.md`/`AGENTS.md` today; this engagement adds `SECURITY.md` + `THREAT_MODEL.md` and wires `AGENTS.md → SECURITY.md → THREAT_MODEL.md`. The website publishes CVE advisories at `unomi.apache.org/security/`. Confirm the in-repo model is canonical and how it should reference the website advisories; confirm revision ownership. → §1.
