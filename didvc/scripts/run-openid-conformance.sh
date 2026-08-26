#!/usr/bin/env bash
################################################################################
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
#
# OpenID OID4VCI/OID4VP conformance runner (CI).
#
# Runs the OpenID Foundation conformance test modules for the DID-VC
# credential edge. The hosted suite (https://www.certification.openid.net,
# API: https://api.certification.openid.net) must be able to reach the
# edge's issuer/verifier endpoints over HTTPS, which this script provides
# through a cloudflared quick tunnel when available.
#
# The API calls mirror the official suite automation wrapper:
# https://gitlab.com/openid/conformance-suite/-/blob/master/scripts/conformance.py
#   GET  api/runner/available                         list test modules
#   POST api/plan?planName=..&variant=<json-string>   create test plan (body = config)
#   POST api/runner?test=<name>&plan=<id>             create module instance
#   POST api/runner/<module-id>                       start the module
#   GET  api/runner/<module-id>/wait-state?states=..  long-poll until FINISHED
#   GET  api/log/<module-id>                          module log
#
# Usage: run-openid-conformance.sh [issuer-url] [verifier-url]
# Plan names are overridable:
#   OPENID_VCI_PLAN_NAME (default oid4vci-issuer-test-plan)
#   OPENID_VP_PLAN_NAME  (default oid4vp-verifier-test-plan)
#
set -euo pipefail

ISSUER_URL="${1:-}"
VERIFIER_URL="${2:-}"
SUITE_API="${OPENID_SUITE_API:-https://api.certification.openid.net}"
VCI_PLAN_NAME="${OPENID_VCI_PLAN_NAME:-oid4vci-issuer-test-plan}"
VP_PLAN_NAME="${OPENID_VP_PLAN_NAME:-oid4vp-verifier-test-plan}"
CLIENT_NAME="hkt-didvc-${GITHUB_RUN_ID:-local}"

log() { echo "[conformance] $*"; }

# The hosted suite requires a Bearer token for API access. Supply it via
# the OPENID_SUITE_TOKEN secret (workflow: actions secrets) or env var.
suite_curl() {
  local args=()
  if [ -n "${OPENID_SUITE_TOKEN:-}" ]; then
    args+=(-H "Authorization: Bearer ${OPENID_SUITE_TOKEN}")
  fi
  curl -fsS "${args[@]}" "$@"
}

# Expose the local edge through a cloudflared quick tunnel when cloudflared
# is installed (GitHub Actions has public egress for trycloudflare.com).
start_cloudflared_tunnel() {
  if command -v cloudflared >/dev/null 2>&1; then
    log "Starting cloudflared quick tunnel to http://localhost:8081"
    nohup cloudflared tunnel --url http://localhost:8081 --no-autoupdate >/tmp/cloudflared.log 2>&1 &
    TUNNEL_URL=""
    for _ in $(seq 1 60); do
      TUNNEL_URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' /tmp/cloudflared.log | head -1 || true)
      [ -n "${TUNNEL_URL}" ] && break
      sleep 2
    done
    if [ -z "${TUNNEL_URL}" ]; then
      log "ERROR: cloudflared tunnel did not come up; log follows"
      cat /tmp/cloudflared.log || true
      exit 1
    fi
    ISSUER_URL="${TUNNEL_URL}/hkt"
    VERIFIER_URL="${TUNNEL_URL}/bank-a"
    log "Tunnel ready: ${TUNNEL_URL}"
  fi
}

list_available_modules() {
  suite_curl "${SUITE_API}/api/runner/available" \
    | jq -r '.[].testModuleIdentifier // empty' 2>/dev/null | sort | head -50 || true
}

# Runs one test plan. $1 = plan name, $2 = configuration JSON, $3 = variant JSON or ""
run_test_plan() {
  local plan_name="$1"
  local configuration="$2"
  local variant="$3"

  log "Creating test plan '${plan_name}'"
  local create_args=("${SUITE_API}/api/plan?planName=${plan_name}")
  if [ -n "${variant}" ]; then
    create_args+=("&variant=$(python3 -c "import json,sys;print(__import__('urllib.parse',fromlist=['quote']).quote(json.dumps(${variant})))")")
  fi
  local plan_response
  plan_response=$(suite_curl -X POST "${create_args[*]}" \
    -H 'Content-Type: application/json' --data "${configuration}")
  local plan_id
  plan_id=$(echo "${plan_response}" | jq -r '.id // .planId // empty')
  if [ -z "${plan_id}" ]; then
    log "ERROR: could not create test plan '${plan_name}': ${plan_response}"
    return 1
  fi
  log "Plan '${plan_name}' created with id ${plan_id}"

  # The plan response carries the test module identifiers; tolerate both
  # 'modules[].testModule.testModuleIdentifier' and 'modules[].id' shapes.
  local test_names
  test_names=$(echo "${plan_response}" \
    | jq -r '.modules[]? | (.testModule.testModuleIdentifier // .testModuleIdentifier // .id // empty)' 2>/dev/null || true)
  if [ -z "${test_names}" ]; then
    log "WARN: no test module identifiers found in plan response; dumping response for diagnosis"
    echo "${plan_response}" | jq . || true
  fi

  local failed=0
  for test_name in ${test_names}; do
    log "Creating test module instance for '${test_name}'"
    local module_response
    module_response=$(suite_curl -X POST \
      "${SUITE_API}/api/runner?test=${test_name}&plan=${plan_id}")
    local module_id
    module_id=$(echo "${module_response}" | jq -r '.id // .moduleId // empty')
    if [ -z "${module_id}" ]; then
      log "ERROR: no module id in create response: ${module_response}"
      failed=1
      continue
    fi

    log "Starting module ${module_id} (${test_name})"
    suite_curl -X POST "${SUITE_API}/api/runner/${module_id}" >/dev/null

    log "Waiting for module ${module_id} to finish"
    local state=""
    for _ in $(seq 1 40); do
      state=$(suite_curl \
        "${SUITE_API}/api/runner/${module_id}/wait-state?states=FINISHED,INTERRUPTED&timeoutMs=30000" \
        | jq -r '.state // .status // empty')
      case "${state}" in
        FINISHED) break ;;
        INTERRUPTED) break ;;
        *) sleep 5 ;;
      esac
    done
    log "Module ${module_id} final state: ${state:-unknown}"
    if [ "${state}" != "FINISHED" ]; then
      failed=1
    fi
    log "Module ${module_id} log:"
    suite_curl "${SUITE_API}/api/log/${module_id}" | jq . || true
  done
  return "${failed}"
}

start_cloudflared_tunnel

log "Available test modules (informational):"
list_available_modules

if [ -n "${ISSUER_URL}" ]; then
  log "Issuer under test: ${ISSUER_URL}"
  run_test_plan "${VCI_PLAN_NAME}" \
    "$(jq -nc --arg issuer "${ISSUER_URL}" '{issuer: $issuer}')" \
    '{"client_auth_type":"none"}' || exit 1
fi

if [ -n "${VERIFIER_URL}" ]; then
  log "Verifier under test: ${VERIFIER_URL}"
  run_test_plan "${VP_PLAN_NAME}" \
    "$(jq -nc --arg verifier "${VERIFIER_URL}" '{verifier: $verifier}')" \
    '{"client_auth_type":"none"}' || exit 1
fi

log "CONFORMANCE COMPLETE"
