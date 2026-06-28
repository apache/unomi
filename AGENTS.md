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

# Agent Guide for Apache Unomi

This file is read by automated agents (security scanners, code
analyzers, AI assistants) operating on this repository.

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md` and the
threat model it links before reporting issues.

## Backporting from `unomi-3-dev` (UNOMI-875)

When porting Phase 2 work from `unomi-3-dev` to `master`:

1. **Master is often ahead** — merged review PRs (#771–#783) improved code beyond 3-dev. A file diff does not mean master is missing the feature.
2. **Never blind-checkout** existing files from `unomi-3-dev`. Start from `origin/master` and cherry-pick additive hunks only.
3. **Audit before commit** — compare `origin/master` vs your branch; any removed logic from master is likely a regression (see `.cursor/rules/unomi-3-dev-backport.mdc`).
4. **Safe wholesale adds**: new scripts, docs, Postman collection, new Java classes. **Surgical merge**: `build.sh`, `pom.xml`, CI workflows (keep master CI/Javadoc/IT memory sampling).
5. **Do not port** items in the plan exclusion register (REST mappers, migration scripts UNOMI-943, security WIP, wholesale test harness from 3-dev).

Detailed tracker: `.local-notes/unomi-3-dev-backport-plan-phase2.md` (local, git-ignored).
