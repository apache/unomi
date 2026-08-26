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
# credential edge. The conformance suite is hosted at
# https://www.certification.openid.net (API: https://api.certification.openid.net)
# and must be able to reach the edge's issuer/verifier endpoints over HTTPS,
# which this script provides through a cloudflared quick tunnel.
#
# Prerequisites (in CI): mvn + java 17, curl, jq, and cloudflared on PATH.
# The workflow (.github/workflows/didvc-conformance.yml) wires this together.
#
# Usage: run-openid-conformance.sh <issuer-url> <verifier-url>
#   e.g. run-openid-conformance.sh https://<tunnel>/hkt https://<tunnel>/bank-a
#
set -euo pipefail

ISSUER_URL="${1:?issuer-url required}"
VERIFIER_URL="${2:?verifier-url required}"
SUITE_API="${OPENID_SUITE_API:-https://api.certification.openid.net}"
CLIENT_NAME="hkt-didvc-${GITHUB_RUN_ID:-local}"

log() { echo "[conformance] $*"; }

start_cloudflared_tunnel() {
  if command -v cloudflared >/dev/null 2>&1; then
    log "Starting cloudflared quick tunnel to http://localhost:8081"
    nohup cloudflared tunnel --url http://localhost:8081 --no-autoupdate >/tmp/cloudflared.log 2>&1 &
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

run_test_plan() {
  local plan_name="$1"  # e.g. oid4vci-issuer or oid4vp-verifier
  local test_plan="$2"  # test plan JSON
  log "Creating test plan ${plan_name}"
  local plan_id
  plan_id=$(curl -fsS -X POST "${SUITE_API}/plan" \
    -H 'Content-Type: application/json' \
    -d "${test_plan}" | jq -r '.id // .planId // empty')
  if [ -z "${plan_id}" ]; then
    log "ERROR: could not create test plan ${plan_name} (API response above)"
    return 1
  fi
  log "Polling plan ${plan_id}"
  local status=""
  for _ in $(seq 1 120); do
    status=$(curl -fsS "${SUITE_API}/plan/${plan_id}" | jq -r '.status // empty')
    log "plan ${plan_id}: ${status}"
    case "${status}" in
      FINISHED|finished) return 0 ;;
      INTERRUPTED|interrupted|CANCELLED|cancelled)
        curl -fsS "${SUITE_API}/plan/${plan_id}/log" || true
        return 1 ;;
    esac
    sleep 15
  done
  log "ERROR: test plan ${plan_id} timed out"
  curl -fsS "${SUITE_API}/plan/${plan_id}/log" || true
  return 1
}

start_cloudflared_tunnel

log "Issuer under test: ${ISSUER_URL}"
log "Verifier under test: ${VERIFIER_URL}"

# OID4VCI issuer plan: the suite acts as the wallet and exercises the
# issuer metadata, pre-authorized code and authorization-code flows.
log "Running OID4VCI issuer conformance"
run_test_plan "oid4vci-issuer" "$(jq -nc --arg issuer "${ISSUER_URL}" --arg client "${CLIENT_NAME}" '{
  testPlan: "oid4vci-issuer",
  clientName: $client,
  variant: { client_auth_type: "none" },
  config: { issuer: $issuer }
}')"

# OID4VP verifier plan: the suite acts as the holder and presents
# SD-JWT VC credentials against the verifier endpoints.
log "Running OID4VP verifier conformance"
run_test_plan "oid4vp-verifier" "$(jq -nc --arg verifier "${VERIFIER_URL}" --arg client "${CLIENT_NAME}" '{
  testPlan: "oid4vp-verifier",
  clientName: $client,
  variant: { credential_format: "vc+sd-jwt", client_auth_type: "none" },
  config: { verifier: $verifier }
}')"

log "CONFORMANCE COMPLETE"
