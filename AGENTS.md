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

## Branch backporting (generic)

When porting changes **from a source branch to a target branch**, follow
`.cursor/rules/branch-backport.mdc`. Summary:

1. **Target is canonical** for files that already exist there — branch from
   `origin/<target>`, not from source.
2. **Target may be ahead** — merged review PRs on the target often improve
   code beyond the source. A diff ≠ missing feature.
3. **Commit history on the target** — for each existing file:
   `git log origin/<source>..origin/<target> -- <path>`. Non-empty output
   means target-only fixes you must not drop.
4. **Never blind-checkout** existing files from source
   (`git checkout origin/<source> -- <path>`). Port additive hunks only.
5. **Audit before commit** — diff regression scan plus history cross-check;
   use `git blame origin/<target>` on any line you remove.
6. **Classify paths:** ADD (new on target) · MODIFY (hand-merge from
   target) · MERGE (build/CI/deps — target wins on target-only lines).
7. **Do not** bulk-cherry-pick from source branch history without
   per-commit review.

Cursor rule: `.cursor/rules/branch-backport.mdc`

## Backporting `unomi-3-dev` → `master` (UNOMI-875)

Active Phase 2 epic. **Apply generic rules above first**, then
`.cursor/rules/unomi-3-dev-backport.mdc` for Unomi-specific exclusions and
mega-PR risks.

| | |
|---|---|
| Source | `unomi-3-dev` |
| Target | `master` |
| Local plan | `.local-notes/unomi-3-dev-backport-plan-phase2.md` (git-ignored) |

Unomi-specific reminders:

- Master is ahead on REST mappers, tracing, shell CRUD (#755, #763), CI
  (#780, #957), docker image pins — do not revert.
- Safe wholesale adds: new scripts, Postman, docs, new Java classes.
- Do not port: migration scripts (UNOMI-943), 3-dev REST mappers, security
  WIP, wholesale test harness from 3-dev.
