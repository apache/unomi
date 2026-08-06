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

- **Project:** Apache Unomi (`apache/unomi`), `master` branch, against which this draft was written. This model covers the **apache/unomi** server; `unomi-tracker` (browser tracking client) and `unomi-site` (website) are in the engagement scope but are treated here as satellites (see §2/§3).
- **Date:** 2026-06-02; **amended:** 2026-08-06 (PMC triage amendments — role split, GraphQL transports, tenant isolation, closed §14 Q1 / Q8a; default-password: documented today + 3.1 hardening commitment). **Status:** draft — for Apache Unomi PMC review. **Author:** ASF Security team (drafted via the Scovetta threat-model rubric), for PMC ratification; amendments by Unomi PMC.
- **Version binding:** versioned with the project; a report against version *N* is triaged against the model as it stood at *N*.
- **ASF vulnerability handling:** the PMC follows [Handling a possible vulnerability](https://apache.org/security/committers.html#vulnerability-handling) (see also `SECURITY.md`): work in private → acknowledge → investigate → **accept or reject** (with written reasons) → for accepted issues: CVE via `cveprocess.apache.org` / `security@apache.org` → private fix → release → announce → update project security pages. Unomi has no dedicated `security@unomi.apache.org`; copy private vulnerability mail to `security@apache.org`.
- **Accept vs reject (released vs unreleased):** **Accept** (and continue through CVE/fix/release/announce) findings that are in-model vulnerabilities in **released** Unomi lines (e.g. 2.x, 3.0.x). **Reject** as a security vulnerability in released Apache software — with a clear explanation to the reporter — findings that (a) are `OUT-OF-MODEL` / `BY-DESIGN` / `KNOWN-NON-FINDING` per this document, or (b) exist **only** on unreleased development code (e.g. `master` / `3.1.0-SNAPSHOT` before any 3.1 GA) and do not affect a released line. Rejection under (b) does **not** mean “ignore”: still fix in the development branch before GA, but do not open a CVE or run the embargoed announce path solely for never-shipped code. When one email mixes both, **split the reply**: accept the released-line slice; reject (with fix-before-GA note) the unreleased-only slice. Ask `security@apache.org` if a borderline case needs a CVE anyway.
- **Reporting cross-reference:** suspected §8 violations → `security@apache.org` → `private@unomi.apache.org`; dispositions cite this document.
- **Provenance legend:** *(documented)* = Unomi's own docs/repo/CVE advisories; *(maintainer)* = confirmed by an Unomi PMC member through this process; *(inferred)* = reasoned from architecture/history, not yet confirmed — each has a matching §14 open question.
- **Draft confidence:** ~16 documented / several maintainer (Q1, Q2, Q7, Q8a) / remaining inferred.
- **What Unomi is:** Apache Unomi is a Java reference implementation of the OASIS Context Server (CXS) spec — a Customer Data Platform. It collects behavioural events about visitors (typically from a browser via the `unomi-tracker` JavaScript over a public **context** endpoint), builds and stores profiles + segments, evaluates rules/conditions, and exposes data via REST and GraphQL APIs (HTTP and WebSocket). It persists to Elasticsearch/OpenSearch. *(documented — README, manual)*

## §2 Scope and intended use

- **Primary use:** an operator-deployed **context server** that ingests visitor events over the network and serves profile/segmentation data to web properties and back-office tools. *(documented — manual)*
- **Caller roles** (network service — the role splits):
  - **public web client** — a browser running `unomi-tracker`, hitting the **public context endpoint** (`/cxs/context.json` and `/cxs/eventcollector`) with a tenant **public** API key (`X-Unomi-Api-Key`), from the open internet. Highest-value untrusted surface. (Temporary exception: V2 compatibility mode may omit the public key while migrating.) *(documented — `security.adoc`)*
  - **tenant administrator** — authenticates with Basic `tenantId:privateApiKey`. Trusted **only within that tenant’s data plane** (profiles, rules, segments, schemas for that tenant). **Not** trusted for host-level side effects (unsandboxed script execution, arbitrary filesystem or unconstrained Camel endpoints) or for other tenants’ data. *(maintainer — §14 Q8a)*
  - **system administrator / operator** — JAAS (e.g. `karaf`) and control of the Karaf container, plugins, and the Elasticsearch/OpenSearch backend. Trusted for tenant CRUD, key minting, and host-impacting configuration. *(documented — manual; maintainer)*
  - **integrator / API client** — any authenticated REST/GraphQL caller; **trusted only to its credential’s authority** (public key, tenant private key, or system admin). *(maintainer)*
  - **cluster peer** — another Unomi node. *(inferred)*

**Component-family table:**

| Family | Entry point | Touches outside process | In model? |
| --- | --- | --- | --- |
| Public context ingestion | `/cxs/context.json` + `/cxs/eventcollector` | network (public listen) | **In — primary boundary** *(documented)* |
| Rule / condition / segment engine + scripting | `services`, `scripting` (MVEL/OGNL expression eval) | evaluates expressions | **In — historically the RCE surface (§11)** *(documented: CVEs)* |
| JSON-Schema event validation | schema validation of incoming events | — | **In — the input-validation defense** *(documented: manual `jsonSchema`)* |
| Admin REST APIs | `rest` | network (authenticated) | **In** *(documented: modules)* |
| GraphQL HTTP + WebSocket | `/graphql` (queries/mutations over HTTP; subscriptions over WebSocket) | network (authenticated) | **In — auth must apply to every transport, including WebSocket upgrade/subscribe** *(maintainer)* |
| Groovy actions extension | script upload/compile/dispatch | process (script exec) | **In — host-impacting ops require system administrator** *(maintainer — §14 Q8a)* |
| Router import/export | Camel source/destination URIs | filesystem / remote endpoints | **In — path/host confinement required for multi-tenant safety** *(maintainer — §14 Q8a)* |
| Persistence | `persistence-elasticsearch` / `persistence-opensearch` | network → ES/OS backend | **In (Unomi's use of it); the backend's own security is operator's** *(inferred)* |
| Plugins / extensions / connectors | `plugins`, `extensions`, `connectors` | varies | **In core ones; third-party/`samples` out** *(inferred)* |
| `unomi-tracker` (JS client) | browser | — | **Satellite — discoverability pointer; client-side, lower trust surface** *(maintainer — §14 Q2)* |
| `unomi-site`, `samples`, `itests` | website / demos / tests | — | **Out** *(see §3; maintainer — §14 Q2)* |

## §3 Out of scope (explicit non-goals)

- **Attackers who already control the host, the Karaf container, the config, the plugins, or the Elasticsearch/OpenSearch backend.** Operator-trusted. *(inferred)*
- **`unomi-site`, `samples/`, `itests/`** — website + demo + test code, not production trust surface. *(maintainer — §14 Q2)* A **core** action or endpoint that is unsafe when an operator follows a documented sample pattern can still be `VALID` / `VALID-HARDENING`; only the sample artifact itself is out of scope.
- **Confidentiality of profile data at rest / in the search backend** when the operator has not secured Elasticsearch/OpenSearch and the network — that is deployment hardening, not an Unomi code property, unless Unomi claims otherwise. *(inferred)*
- **Arbitrary expression evaluation by a *system administrator*** who authors a malicious condition/rule — an authenticated system-privileged user defining server-side logic is the intended (if powerful) feature, not an attack on Unomi. The boundary is whether *public/untrusted* input, or a *lower-privilege* credential (e.g. tenant administrator), can reach that power (see §8/§11). *(maintainer — §14 Q7 / Q8a)*

## §4 Trust boundaries and data flow

- **Primary boundary: the public context endpoint.** Event payloads arriving from browsers (public API key at most) are **untrusted**. They flow → JSON-Schema validation → event/condition processing → profile update → persistence. The schema-validation step and the public-event allow-list are the gates. *(documented; schema validation documented)*
- **Secondary boundary: the authenticated REST/GraphQL surface** (HTTP **and** WebSocket), where conditions/rules/scopes are defined and subscriptions are opened — trusted only to the credential’s authority. A transport that skips the auth gate that other transports enforce is an in-model break. *(maintainer)*
- **Tertiary boundary: tenant credential ↔ host / cross-tenant.** A tenant-administrator subject must not escape to host process powers (unsandboxed script compile/exec, absolute filesystem read/write, unconstrained Camel endpoints) or to another tenant’s data. *(maintainer — §14 Q8a)*
- **The historical break (load-bearing):** the public surface must **not** allow attacker-controlled input to reach OGNL/MVEL expression evaluation that can instantiate/call arbitrary Java — that was CVE-2020-11975 / CVE-2020-13942 / CVE-2021-31164, fixed by constraining the public surface. The model treats a regression of this kind as `VALID`/critical. *(documented — CVE advisories)*
- **Reachability precondition:** a finding in `scripting`/condition-evaluation is **in-model** only if reachable from **public/untrusted** input **or from a lower privilege than the operation requires** (e.g. tenant admin reaching system-admin-only host power). Expression power available only to a system administrator is `OUT-OF-MODEL: trusted-input`. A finding on the ES/OS backend is in-model only if reachable through Unomi's API, not by directly attacking an exposed backend. *(maintainer)*

## §5 Assumptions about the environment

- **Runtime:** JVM; runs in an Apache Karaf / OSGi container. *(documented — kar/package/manual)*
- **Backend:** Elasticsearch or OpenSearch, assumed deployed on a trusted network and secured by the operator. *(inferred)*
- **The public endpoint is internet-facing by design** (browsers post events directly); the admin REST/GraphQL APIs are assumed *not* public and are authenticated. *(maintainer — §14 Q1)*
- **Negative side-effects inventory** (predominantly inferred — wave-1/2 target): Unomi listens on HTTP; reads config from the Karaf container; talks to the search backend; loads OSGi plugins; evaluates conditions/expressions; the scripting engine executes expression logic authored through the (trusted) admin path; optional extensions (Groovy, router) can touch the filesystem and remote endpoints. *(inferred)*

## §5a Build-time and configuration variants

Security-relevant configuration knobs:

- **Public-endpoint protection / third-party server allow-list + secured events** — the mechanism that distinguishes events a public client may send from those that require a trusted key. Default posture? *(inferred — Unomi has a "protected events" / third-party-server key concept; see §14 Q5)*
- **JSON-Schema validation** of incoming events — on by default? Reject-unknown by default? *(inferred; feature documented; see §14 Q4)*
- **Expression/scripting allow-list** (post-CVE) — what restricts which classes/methods conditions may reference, and is it on by default? *(inferred; the CVE fixes introduced restrictions)*
- **Authentication on the admin REST/GraphQL APIs** — packaged default JAAS user `karaf` with password from `${org.apache.unomi.security.root.password:-karaf}`; health user defaults similarly. Manual documents the default and instructs operators to change it as soon as possible. *(documented — `users.properties`, `configuration.adoc`; maintainer — §14 Q1)*
- **Profile id cookie flags** — `contextserver.profileIdCookieHttpOnly` defaults to `false` unless overridden. *(documented — `org.apache.unomi.web.cfg`)*
- **Router allowed endpoint schemes** — default allowlist includes `file,ftp,sftp,ftps`. *(documented — `org.apache.unomi.router.cfg`)*

**Insecure-default check (PMC ruling) — two layers:**

1. **Triage of reports against current / pre-3.1 behaviour:** leaving the documented default JAAS password unchanged is a **documented must-configure** operator duty. A report that only shows “default `karaf:karaf` still works on a fresh install” is `OUT-OF-MODEL: non-default-build` / `BY-DESIGN: property-disclaimed` (§9). Auth bypasses or missing gates on an admin surface (independent of the password value) remain `VALID`. *(maintainer — §14 Q1)*
2. **Product commitment for the 3.1 release:** the PMC treats the shipped known default as **accepted technical debt to retire in 3.1**, not as the long-term desired posture. 3.1 should remove the working known default (for example: require an explicit password via config/env before admin API access; generate and persist a random password on first boot; and/or fail-fast when the API is bound non-loopback with an unset/default password). The health user should follow the same pattern. Until that ships, improvements are tracked as **`VALID-HARDENING`** (not a §8 break). **After 3.1 ships the stronger default**, this §5a ruling is revised: a regression that reintroduces a known working default password becomes `VALID`. *(maintainer — §12 / §14 Q1)*

## §6 Assumptions about inputs

Per-surface trust table:

| Surface | Input | Attacker-controllable? | Caller/operator must enforce |
| --- | --- | --- | --- |
| Public context endpoint | event JSON, profile/session refs, scope | **yes (public client)** | JSON-schema validation on; public-event allow-list; no expression reach; profile/session refs treated as **bearer identifiers** (see §8) |
| REST admin | conditions, rules, segments, queries, extension config | **yes, within credential authority** | authn + authz; system admin for host-impacting ops |
| GraphQL HTTP | queries / mutations | **yes, within credential authority** | same auth gate as documented for GraphQL |
| GraphQL WebSocket | subscription init + subscribe payloads | **yes** | **same auth as GraphQL HTTP**; no Subject → reject upgrade/subscribe |
| Condition / rule definitions | MVEL/OGNL expressions | **public: must be no; system admin: yes-but-trusted** | keep expression authoring on the system-trusted side |
| Public events → identity actions | merge/update target from event properties | **yes, when event type is public** | do not wire unverified identity claims to merge/update actions (§11) |
| Persistence queries | derived from the above | indirectly | backend hardening; tenant/scope isolation |
| Plugins / connectors config | operator-supplied | no — operator-trusted | vet third-party plugins |

- **Size/shape/rate:** whether the public endpoint bounds event size / batch count / request rate against a flood is open (see §8 resource line). *(inferred)*

## §7 Adversary model

- **Primary adversary:** an unauthenticated or public-API-key party who can reach the **public context endpoint** (and any other publicly reachable surface) — trying to achieve code execution (the CVE history), read/modify other visitors' profiles, open privileged GraphQL subscriptions without credentials, inject events to corrupt segmentation or rebind identity, or exhaust resources. *(documented threat history; maintainer framing)*
- **Secondary:** an authenticated **tenant** API client trying to exceed its authority — cross-tenant read/write, host RCE, arbitrary filesystem or internal-network reach via import/export, uploading unsandboxed scripts. *(maintainer — §14 Q8a)*
- **Capabilities:** craft arbitrary event/condition JSON to the public endpoint; replay; send large/malformed payloads; attempt WebSocket upgrade without credentials; use a stolen or issued tenant private key within (and beyond) its tenant. **Not** assumed: control of the system-admin credential, the container, or the backend. *(maintainer)*
- **Out of scope:** system administrators authoring powerful (even dangerous) conditions; attackers with host/backend control. *(maintainer)*

## §8 Security properties the project provides

*(CVE-fix posture is documented history; tenant and GraphQL transport lines are maintainer-confirmed.)*

- **No code execution from public/untrusted input.** Public-endpoint input cannot reach OGNL/MVEL evaluation that instantiates or calls arbitrary Java — the post-CVE invariant. *Violation symptom:* RCE / arbitrary-class invocation from an unauthenticated or public-key-only request. *Severity:* security-critical. *(documented)*
- **Input validation at the public boundary.** Incoming events are validated against registered JSON Schemas; non-conforming input is rejected, not processed. *Violation symptom:* unvalidated/unknown event shape reaching processing. *Severity:* security-critical → moderate. *(documented feature; default/strictness — §14 Q4)*
- **Profile/scope access control.** A public client cannot read or modify profile data outside what the context/scope model permits; an API client is bounded by its authority. On the public endpoints, `profileId` / `sessionId` are **bearer identifiers** (typically the `context-profile-id` cookie): possession of the identifier is the authorization model. *Violation symptom:* cross-profile / cross-scope data access **without** possessing that bearer (e.g. body `profileId` accepted when it does not match the cookie bearer; session→profile switch without ownership; unauthenticated GraphQL subscription receiving events). *Severity:* security-critical (data exposure — PII). *(maintainer)*
- **Authentication on all GraphQL transports.** HTTP queries/mutations and WebSocket upgrade/subscribe share the same authentication requirements; subscriptions are not a public operation. *Violation symptom:* unauthenticated client completes upgrade and reaches `graphQL.execute` for a subscription. *Severity:* security-critical → high. *(maintainer)*
- **Tenant isolation.** Credentials for tenant A cannot read or modify tenant B’s profiles, segments, rules, or keys. Unomi enforces this boundary in the data plane. *Violation symptom:* cross-tenant data access. *Severity:* security-critical. *(maintainer — §14 Q8a / UNOMI-139)*
- **No privilege escalation beyond credential class.** Host-impacting operations (unsandboxed script upload/compile/exec, absolute filesystem access, unconstrained remote Camel endpoints) require **system administrator**, not tenant administrator. *Violation symptom:* tenant private key achieves host RCE, arbitrary file read, or equivalent. *Severity:* security-critical. *(maintainer — §14 Q8a)*
- **Resource bounds — UNSPECIFIED.** Whether a public event flood or an expensive segment/condition is a bug or expected-and-operator-managed is open. *(inferred)*

## §9 Security properties the project does *not* provide

- **No protection if the admin REST/GraphQL APIs are exposed to the public network or left on the documented default JAAS password (pre-3.1 / until the 3.1 default hardening ships)** — keeping the admin surface off the public network and changing default credentials is the operator's job (§5a layer 1; §10). The project still commits to eliminating the known default in 3.1 (§5a layer 2). *(maintainer — §14 Q1)*
- **No confidentiality/integrity for the ES/OS backend or its network** — Unomi assumes a secured backend; it does not defend an exposed Elasticsearch. *(inferred)*
- **Not a sandbox for system-administrator-authored expressions/plugins** — a system admin with condition/scripting authority can run server-side logic by design; that power is not contained. *(maintainer — §14 Q7)* **False friend:** the presence of the scripting/expression allow-list protects the *public* surface; it is not a sandbox that makes arbitrary admin-authored expressions safe. **This disclaimer does not cover tenant-administrator script upload or Camel config that reaches host power** — that is a §8 privilege-escalation / tenant-isolation property.
- **No guarantee that possession of a visitor `profileId` UUID is hard** — the id is a bearer token; confidentiality of the cookie (and flags such as HttpOnly) is largely an operator/frontend concern, though unsafe defaults may be `VALID-HARDENING`. *(maintainer)*
- **No guarantee of correctness of analytics/segmentation under adversarial event injection** beyond the access-control boundary. *(inferred)*
- **Well-known classes left to the caller/operator:** expression-injection (the CVE class — defended by constraining the public surface), event/PII-exposure via a misconfigured public endpoint, DoS via event floods, and deploying sample identity-merge rules without verified identity (§11). *(documented history; maintainer framing)*

## §10 Downstream responsibilities (operator/deployer)

- Keep the admin REST/GraphQL APIs **off the public network** and authenticated; **change the documented default JAAS credentials** before production use (mandatory until 3.1 retires the known default). *(maintainer — §14 Q1)*
- Keep JSON-Schema validation and the public-event allow-list **enabled**; register schemas for the events you accept. *(inferred)*
- Secure the Elasticsearch/OpenSearch backend + its network. *(inferred)*
- Restrict who holds **system-administrator** condition/rule/scripting and extension-config authority — it is equivalent to server-side code definition. Treat **tenant private keys** as high privilege within a tenant, not as host-admin equivalents. *(maintainer)*
- Do not deploy sample login-merge (or similar) rules that bind public events to identity-merge/update actions without a **verified** identity step. *(maintainer)*
- Prefer `profileId` cookie `HttpOnly=true` (and Secure where appropriate) in production. *(maintainer)*
- Put the public endpoint behind rate-limiting / a CDN/WAF appropriate to public exposure. *(inferred)*

## §11 Known misuse patterns

- Exposing the admin REST/GraphQL APIs to the internet, or leaving the documented default JAAS credentials unchanged in production (especially on pre-3.1 builds). *(maintainer)*
- Disabling JSON-Schema validation or the public-event allow-list "to make integration easier", re-opening the public surface. *(inferred)*
- Treating the scripting/expression allow-list as a sandbox for admin-authored conditions. *(inferred)*
- Exposing Elasticsearch/OpenSearch alongside Unomi without backend auth. *(inferred)*
- Wiring `mergeProfilesOnPropertyAction` / `updatePropertiesAction` (or equivalents) to **public** event types using unverified `eventProperty::` identity claims (including following the login-integration sample pattern in production). *(maintainer)*
- Granting tenant administrators Groovy upload or router `file`/`ftp`/`sftp` configuration without path/host confinement. *(maintainer)*
- Assuming GraphQL WebSocket is covered by HTTP-only auth checks. *(maintainer)*

## §11a Known non-findings (recurring false positives)

*(PMC confirmation here is the highest-leverage scan-suppression input.)*

- "Unomi evaluates OGNL/MVEL expressions → RCE" — by-design for **system-administrator-authored** conditions; the public surface is constrained (post-CVE). A report is `VALID` only if it shows **public/untrusted** input, or a **lower privilege than required**, reaching expression evaluation / host script power; otherwise `OUT-OF-MODEL: trusted-input` / `BY-DESIGN`. *(documented — CVE fixes; maintainer — §14 Q7)*
- "Scripting / reflection present in `scripting` module" — needs the public-reachability (or privilege-escalation) test (§4) before it is a finding. *(inferred)*
- "No auth on the context endpoint" — the public ingestion endpoint is unauthenticated **by design** (aside from tenant public API key resolution); the protection is schema validation + the event allow-list, not visitor login. *(documented; maintainer)*
- "Public `/context.json` returns profile data for a supplied `profileId`" — **by design** when that id is the caller’s bearer (cookie / equivalent). Not automatically `VALID` as “IDOR” merely because the attacker knows the UUID. `VALID` / `VALID-HARDENING` when the body id is accepted **without** matching the bearer cookie, when session load switches profile without ownership, or when cookie flags make XSS→id theft trivial by unsafe default. *(maintainer)*
- "Elasticsearch reachable / no TLS" — operator deployment responsibility (§9/§10). *(inferred)*
- "System administrator can run dangerous operation X" — out-of-model: system admin is trusted (§7). **Does not apply** to tenant administrator achieving host RCE, arbitrary file read, or cross-tenant access — those are §8 violations. *(maintainer — §14 Q8a)*
- "Default `karaf:karaf` works on a fresh install" — against **current / pre-3.1** builds: documented must-configure (§5a layer 1); not `VALID` solely on that basis. The same class of report is accepted as **`VALID-HARDENING` motivation** for the 3.1 default-password retirement (§5a layer 2). After 3.1 ships that retirement, a regression reintroducing a known default is `VALID`. *(maintainer — §14 Q1)*

## §12 Conditions that would change this model

- A change to the public-endpoint protection model, the JSON-Schema-validation default, the scripting allow-list, or admin-auth defaults. *(inferred)*
- **Planned:** 3.1 retires the known default JAAS password (§5a layer 2). When that ships, revise §5a/§9/§11a so that a working known default is no longer `BY-DESIGN` / must-configure, and a regression is `VALID`. *(maintainer)*
- A new public surface or a new expression/scripting capability reachable from untrusted input or from tenant credentials. *(maintainer)*
- Promoting a `samples/` or third-party connector into core. *(inferred)*
- A change to the tenant-isolation or privilege-escalation guarantees (§8). *(maintainer)*
- A report that cannot be routed to one §13 disposition → revise the model.

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope adversary/input (public-input code execution; schema-validation bypass; cross-profile/scope access; GraphQL auth bypass on any transport; tenant isolation break; tenant→host privilege escalation; pre-auth crash). | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property broken, but a §11 misuse is easy enough to harden (e.g. HttpOnly default; **3.1 retirement of the known default JAAS password** per §5a layer 2). | §11, §5a |
| `OUT-OF-MODEL: trusted-input` | Requires **system-administrator** authority (e.g. a system-admin-authored malicious condition) or operator-controlled config/backend. | §6, §7 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires host/container/backend control or another excluded capability. | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in `unomi-site`, `samples/`, `itests/`, or third-party connectors (sample-only; core behaviour followed from samples may still be `VALID`). | §3 |
| `OUT-OF-MODEL: non-default-build` | Only manifests under a discouraged/non-default §5a setting, or solely under an unchanged documented default credential (§5a ruling). | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9-disclaimed property (no system-admin expression sandbox, unauthenticated-by-design context endpoint, backend security, documented default creds). | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a entry. | §11a |
| `MODEL-GAP` | Cannot be cleanly routed — triggers §12. | §12 |

## §14 Open questions for the maintainers

**Wave 1 — scope & default posture:**
1. ~~Confirm the trust split… default credentials?~~ **Answered (2026-08):** Context endpoint is public (public API key) by design; admin REST/GraphQL are authenticated and not intended to be public. **Triage (today / pre-3.1):** packaged default JAAS `karaf`/`karaf` is a **documented must-configure** → reports that only show the default still works are `OUT-OF-MODEL: non-default-build` / `BY-DESIGN`. **Product (3.1):** PMC commits to retire the known working default (explicit config, random first-boot password, and/or fail-fast when bound non-loopback); track that work as `VALID-HARDENING` until shipped, then treat regressions as `VALID`. Auth bypasses remain `VALID` in all versions. → §2/§5a/§7/§9/§12.
2. ~~Confirm `unomi-tracker`… samples/`itests` out of scope.~~ **Answered (2026-08):** Tracker is a client-side satellite; `unomi-site` / `samples` / `itests` are out of scope (§3). → §2/§3.
3. The model covers the apache/unomi server; should `unomi-tracker` get its own (lighter) model later, or a discoverability pointer to this one? → §1.

**Wave 2 — the public boundary & its defenses:**
4. Is **JSON-Schema validation** of incoming events on by default, and does it reject non-conforming/unknown events? Is it the intended primary input defense at the public boundary? → §8.
5. What exactly restricts the **public** surface from reaching OGNL/MVEL expression evaluation today (the post-CVE allow-list / protected-events / third-party-key mechanism — the `ThirdPartyServer` server-id / key / IP allow-list / allowed-event-types config in `configuration.adoc`) — and is it on by default? → §4/§5a/§8.
6. Are there bounds on public event size / batch / rate, or is flood protection the operator's (WAF/rate-limit) concern? → §8/§11a.

**Wave 3 — expressions, scopes, backend:**
7. ~~Confirm that OGNL/MVEL…~~ **Answered (2026-08):** Expression power is by-design for **system-administrator-authored** conditions and is not a sandbox. `VALID` when public/untrusted input reaches it, or when a lower privilege (tenant admin) reaches host script power that should require system admin. → §9/§11a.
8. What is the profile/**scope** isolation model — can an authenticated API client read/modify data outside its scope, and is that boundary something Unomi enforces or the integrator's concern? → §8. *(partially addressed by bearer-id clarification; scope semantics still open)*
8a. ~~**Tenant isolation (UNOMI-139):**…~~ **Answered (2026-08):** Unomi **enforces** tenant isolation in the data plane. Tenant A credentials must not read/modify tenant B. Tenant administrator must **not** obtain host RCE, arbitrary filesystem access, or unconstrained Camel reach — those require system administrator. Violations are `VALID`. → §8/§10. *(maintainer — sergehuber)*
9. Is the Elasticsearch/OpenSearch backend assumed trusted/secured-by-operator (so backend-exposure reports are out-of-model)? → §3/§9.

**Wave 4 — meta & non-findings:**
10. Any other recurring scanner/fuzzer false positives to seed §11a (e.g. the `scripting` module, reflection, OSGi dynamic loading)? → §11a.
11. **Meta:** Confirm the in-repo model is canonical and how it should reference website advisories at `unomi.apache.org/security/`; confirm revision ownership. (In-repo `SECURITY.md` / `AGENTS.md` wiring may already exist on current `master` — verify at ratification.) → §1.
