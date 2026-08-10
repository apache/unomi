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

# Security Policy

## Reporting a Vulnerability

`apache/unomi` follows the [Apache Software Foundation security process](https://www.apache.org/security/). Please report suspected
vulnerabilities privately to `security@apache.org` (the ASF Security Team routes Unomi reports to the project's private PMC list,
`private@unomi.apache.org`); do not open public GitHub issues or pull requests for security reports.

## How the PMC handles reports

Unomi follows the default ASF process in
[ASF Project Security for Committers — Handling a possible vulnerability](https://apache.org/security/committers.html#vulnerability-handling).
Unomi does not currently maintain a dedicated `security@unomi.apache.org` list, so further private mail about an undisclosed issue
should be copied to `security@apache.org` as that guide requires.

Summary of the steps the PMC applies:

1. **Work in private** — no public Jira/GitHub issues; commit messages must not call out the security nature of the fix until announcement.
2. **Acknowledge** — email the reporter (cc `security@apache.org` / `private@unomi.apache.org`).
3. **Investigate** — triage against [THREAT_MODEL.md](./THREAT_MODEL.md); **accept** or **reject** each distinct finding (a multi-issue report may be split).
4. **If rejected** — write to the reporter explaining why (cc security lists). Rejection reasons include out-of-model / by-design findings and issues that affect **only unreleased** development code with no released-line impact (still fix before the next GA when appropriate).
5. **If accepted** — tell the reporter we are working on a fix; request CVE ID(s) via [cveprocess.apache.org](https://cveprocess.apache.org) or `security@apache.org` (ASF Security can advise on splitting/merging CVEs).
6. **Resolve** — agree the fix privately; document on the ASF CVE portal; share fix + draft announcement with the reporter; commit without security references; ship a release that includes the fix.
7. **Announce** — with or after the release announcement (reporter, project lists, `security@apache.org`, `oss-security@lists.openwall.com`).
8. **Complete** — update [unomi.apache.org/security/](https://unomi.apache.org/security/) and CVE references.

## Threat Model

What the project treats as in scope and out of scope, the security
properties it provides and disclaims, the adversary model, and how
findings are triaged are documented in [THREAT_MODEL.md](./THREAT_MODEL.md).
