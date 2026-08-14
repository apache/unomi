#!/bin/sh
#
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
#
# Provisions and configures the Apache Unomi login sample on a LOCAL instance.
#
# This is a demo convenience, not a deployment tool. It uses the system administrator
# credential because provisioning a tenant is an operator action - the sample servlet itself
# only ever receives the scoped tenant private key this script creates for it.
#
# Idempotent: re-running reuses an existing tenant and scope, and issues a fresh private key.
#
# Usage:
#   export UNOMI_ROOT_PASSWORD='your-admin-password'
#   ./setup.sh [--version <sample-version>]
#
# Optional environment overrides:
#   UNOMI_URL        base URL of the running Unomi   (default http://localhost:8181)
#   UNOMI_TENANT_ID  tenant to create/use            (default default)
#   UNOMI_SCOPE      scope to create/use             (default default)
#   KARAF_HOME       Unomi install dir (auto-detected in the source tree)
#   DEMO_PASSWORD    login-form password             (default: randomly generated)

set -eu

UNOMI_URL="${UNOMI_URL:-http://localhost:8181}"
TENANT_ID="${UNOMI_TENANT_ID:-default}"
SCOPE="${UNOMI_SCOPE:-default}"
PID="org.apache.unomi.samples.login"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SAMPLE_VERSION=""

usage() {
    cat <<'USAGE'
Provisions and configures the Apache Unomi login sample on a local instance.

  export UNOMI_ROOT_PASSWORD='your-admin-password'
  ./setup.sh [--version <sample-version>]

Environment overrides: UNOMI_URL, UNOMI_TENANT_ID, UNOMI_SCOPE, KARAF_HOME, DEMO_PASSWORD.
USAGE
}

fail() { echo "ERROR: $*" >&2; exit 1; }

# The cfg is parsed as a Java .properties file, which treats backslash as an escape character and
# strips whitespace between the separator and the value. Writing a value verbatim would therefore
# store something different from what the operator typed - "p@ss\\word" silently becomes
# "p@ssword" - and the resulting login failure gives no clue why. Escape backslashes, then escape a
# leading space or tab so it survives.
properties_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/^\([ \t]\)/\\\1/'
}

while [ $# -gt 0 ]; do
    case "$1" in
        --version) SAMPLE_VERSION="${2:?--version needs a value}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown argument: $1 (try --help)" >&2; exit 2 ;;
    esac
done

for cmd in curl jq; do
    command -v "$cmd" >/dev/null 2>&1 || fail "$cmd is required but not on PATH"
done

[ -n "${UNOMI_ROOT_PASSWORD:-}" ] || fail "UNOMI_ROOT_PASSWORD is not set. Export the administrator
       password you started Unomi with, then run this script again."

# Configuration and deployment go through the directories Karaf already watches
# (felix.fileinstall polls etc/ for *.cfg and deploy/ for bundles), so this script needs no Karaf
# console: no SSH, no host keys, and no console credential on the command line.
#
# That also means everything is written to a directory rather than to the running process, so the
# directory has to be validated properly: pointing at a freshly built distribution while a different
# one is actually serving UNOMI_URL would write the config into an install nobody is reading.
if [ -n "${KARAF_HOME:-}" ]; then
    KARAF_DIR="$KARAF_HOME"
else
    KARAF_DIR=""
    for candidate in "${SCRIPT_DIR}"/../../package/target/unomi-*/; do
        [ -f "${candidate}etc/config.properties" ] || continue
        [ -z "$KARAF_DIR" ] || fail "Found more than one built distribution under package/target.
       Set KARAF_HOME to the one that is running."
        KARAF_DIR="$candidate"
    done
    [ -n "$KARAF_DIR" ] || fail "Could not find a Unomi install. Set KARAF_HOME to the directory of the
       running instance, for example:
         KARAF_HOME=../../package/target/unomi-3.1.0-SNAPSHOT ./setup.sh"
fi

[ -d "$KARAF_DIR" ] || fail "KARAF_HOME does not exist: ${KARAF_DIR}"
KARAF_DIR=$(CDPATH= cd -- "$KARAF_DIR" && pwd)

# Looks like a Karaf install at all?
for marker in etc/config.properties bin/karaf deploy; do
    [ -e "${KARAF_DIR}/${marker}" ] \
        || fail "${KARAF_DIR} does not look like a Unomi install (missing ${marker}). Set KARAF_HOME."
done

# Writable? Failing here beats a half-applied setup.
for dir in etc deploy; do
    [ -w "${KARAF_DIR}/${dir}" ] || fail "${KARAF_DIR}/${dir} is not writable by $(id -un)."
done

# Actually running? Karaf writes karaf.pid at startup; a stale file from a previous run is common,
# so the process is checked rather than just the file. Without this the script would happily
# configure a stopped install and only fail later, at the readiness poll, with a confusing message.
KARAF_PID_FILE="${KARAF_DIR}/karaf.pid"
[ -f "$KARAF_PID_FILE" ] || fail "No karaf.pid in ${KARAF_DIR} - that instance has never been started.
       Start Unomi there, or point KARAF_HOME at the instance serving ${UNOMI_URL}."
KARAF_PID=$(cat "$KARAF_PID_FILE" 2>/dev/null || true)
{ [ -n "$KARAF_PID" ] && kill -0 "$KARAF_PID" 2>/dev/null; } \
    || fail "${KARAF_DIR} has a stale karaf.pid (process ${KARAF_PID:-unknown} is not running).
       Start that instance, or point KARAF_HOME at the one serving ${UNOMI_URL}."

echo "==> Using Unomi install ${KARAF_DIR} (running, pid ${KARAF_PID})"

# Credentials go into a mode-600 netrc rather than curl --user: --user places the password in the
# process arguments, where any local user can read it from ps for the lifetime of the request.
NETRC=$(mktemp) || fail "Could not create a temporary file"
chmod 600 "$NETRC"
trap 'rm -f "$NETRC"' EXIT INT TERM HUP
UNOMI_HOST=$(printf '%s' "$UNOMI_URL" | sed -e 's,^[A-Za-z][A-Za-z0-9+.-]*://,,' -e 's,[:/].*$,,')
[ -n "$UNOMI_HOST" ] || fail "Could not parse a host out of UNOMI_URL='${UNOMI_URL}'"
printf 'machine %s login karaf password %s\n' "$UNOMI_HOST" "$UNOMI_ROOT_PASSWORD" > "$NETRC"

# curl exits 0 for any completed HTTP transaction, including 401/403/500, so a bare "curl || fail"
# reports failed requests as successes. Every call therefore checks the status code explicitly and
# surfaces it, rather than relying on curl's exit status.
#   $1 = description used in the error message; remaining args go to curl; body goes to stdout.
admin_request() {
    _what="$1"; shift
    _out=$(mktemp) || fail "Could not create a temporary file"
    _code=$(curl -sS -o "$_out" -w '%{http_code}' --netrc-file "$NETRC" "$@" 2>/dev/null) || _code="000"
    case "$_code" in
        2*)
            cat "$_out"; rm -f "$_out"; return 0 ;;
        000)
            rm -f "$_out"
            fail "${_what}: could not connect to ${UNOMI_URL}. Is Unomi running?" ;;
        401|403)
            rm -f "$_out"
            fail "${_what}: HTTP ${_code}. Check that UNOMI_ROOT_PASSWORD is the administrator
       password of the instance at ${UNOMI_URL}." ;;
        *)
            _body=$(head -c 400 "$_out" 2>/dev/null || true); rm -f "$_out"
            fail "${_what}: HTTP ${_code}. ${_body}" ;;
    esac
}

# Existence checks must NOT fail on a non-2xx: "not found" is the normal create-it path, and the
# endpoints differ in how they say it (404, or an empty 2xx body). These two helpers therefore
# report what came back instead of treating it as an error; a genuine permission problem still
# surfaces loudly at the following create call, which goes through admin_request.
admin_status() { curl -sS -o /dev/null -w '%{http_code}' --netrc-file "$NETRC" "$@" 2>/dev/null || echo "000"; }
admin_body_or_empty() { curl -sS --netrc-file "$NETRC" "$@" 2>/dev/null || true; }

echo "==> Checking Unomi at ${UNOMI_URL}"
admin_request "Connecting to ${UNOMI_URL}" "${UNOMI_URL}/cxs/tenants" >/dev/null
echo "    reachable, administrator credentials accepted"

echo "==> Tenant '${TENANT_ID}'"
if [ "$(admin_status "${UNOMI_URL}/cxs/tenants/${TENANT_ID}")" = "200" ]; then
    echo "    already exists, reusing"
else
    admin_request "Creating tenant '${TENANT_ID}'" -X POST "${UNOMI_URL}/cxs/tenants" \
        -H "Content-Type: application/json" \
        -d "{\"requestedId\":\"${TENANT_ID}\",\"properties\":{\"name\":\"Login sample tenant\"}}" \
        >/dev/null
    echo "    created"
fi

echo "==> Scope '${SCOPE}'"
existing_scope=$(admin_body_or_empty -H "X-Unomi-Tenant-Id: ${TENANT_ID}" "${UNOMI_URL}/cxs/scopes/${SCOPE}")
if printf '%s' "$existing_scope" | jq -e '.itemId? // empty' >/dev/null 2>&1; then
    echo "    already exists, reusing"
else
    admin_request "Creating scope '${SCOPE}'" -X POST "${UNOMI_URL}/cxs/scopes" \
        -H "Content-Type: application/json" \
        -H "X-Unomi-Tenant-Id: ${TENANT_ID}" \
        -d "{\"itemId\":\"${SCOPE}\",\"metadata\":{\"id\":\"${SCOPE}\",\"name\":\"Login sample scope\"}}" \
        >/dev/null
    echo "    created"
fi

# The plaintext of a private key is returned once, at creation, so it is captured here and never
# echoed. An empty value means the request failed; configuring the sample with it would leave the
# servlet with a blank credential.
echo "==> Issuing a tenant private key"
PRIVATE_KEY=$(admin_request "Issuing a private key for '${TENANT_ID}'" \
    -X POST "${UNOMI_URL}/cxs/tenants/${TENANT_ID}/apikeys?type=PRIVATE" \
    | jq -r '.plainTextKey // empty')
[ -n "$PRIVATE_KEY" ] || fail "No plainTextKey was returned. Check that tenant '${TENANT_ID}' exists
       and that the administrator credentials are correct."
echo "    issued (not printed)"

# No demo password ships with the sample, for the same reason Unomi ships no default admin password.
if [ -n "${DEMO_PASSWORD:-}" ]; then
    case "$DEMO_PASSWORD" in
        *"$(printf '\n')"*) fail "DEMO_PASSWORD must not contain a newline." ;;
    esac
    LOGIN_PASSWORD="$DEMO_PASSWORD"
else
    LOGIN_PASSWORD=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 20) \
        || fail "Could not generate a demo password"
fi

# Written before the bundle is deployed so the component sees its configuration on first activation.
# Holds the private key and the demo password, hence the restrictive mode.
echo "==> Writing ${KARAF_DIR}/etc/${PID}.cfg"
CFG="${KARAF_DIR}/etc/${PID}.cfg"
# Scoped so the restrictive mode applies to the cfg only: leaving umask 077 set would also strip
# group/other bits from the bundle jar copied into deploy/ further down.
_previous_umask=$(umask)
umask 077
cat > "$CFG" <<EOF
# Generated by samples/login-integration/setup.sh - safe to edit or delete.
unomiBaseUrl=${UNOMI_URL}
tenantId=${TENANT_ID}
scope=${SCOPE}
privateKey=$(properties_escape "$PRIVATE_KEY")
demoPassword=$(properties_escape "$LOGIN_PASSWORD")
EOF
chmod 600 "$CFG"
umask "$_previous_umask"
echo "    written (mode 600)"

echo "==> Deploying the sample bundle"
SAMPLE_JAR=$(ls "${SCRIPT_DIR}"/target/login-integration-sample-*.jar 2>/dev/null | head -1)
[ -n "$SAMPLE_JAR" ] || fail "No built bundle in ${SCRIPT_DIR}/target.
       Build it first: mvn -pl samples/login-integration -am install -DskipTests"
cp "$SAMPLE_JAR" "${KARAF_DIR}/deploy/" || fail "Could not copy the bundle into ${KARAF_DIR}/deploy"
echo "    copied $(basename "$SAMPLE_JAR") into deploy/"

# Karaf polls deploy/ once a second, so confirm the sample really came up rather than reporting
# success on the basis of having copied a file.
#
# Probe the servlet, not the static page: /login/index.html is published by LoginSampleResources,
# a separate component with no configuration dependency, so it answers 200 as soon as the bundle
# resolves - even while LoginServlet is still unconfigured and every login returns 503. That window
# is real, not theoretical: the two components activate independently as fileinstall picks up the
# cfg and the jar. Posting a deliberately wrong password distinguishes the states:
#   401 = servlet up AND configured (it got as far as checking the password)
#   503 = deployed but not configured yet
#   404/000 = not deployed yet
echo "==> Waiting for the sample to answer"
DEPLOY_STATUS=""
i=0
while [ $i -lt 30 ]; do
    DEPLOY_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST \
        --data "email=setup-probe@example.invalid&password=setup-probe-wrong-password" \
        "${UNOMI_URL}/login/authenticate" 2>/dev/null || echo "000")
    [ "$DEPLOY_STATUS" = "401" ] && break
    sleep 1
    i=$((i + 1))
done
if [ "$DEPLOY_STATUS" != "401" ]; then
    case "$DEPLOY_STATUS" in
        503) fail "The sample deployed but never picked up its configuration (still HTTP 503 after ${i}s).
       Check ${CFG} and ${KARAF_DIR}/data/log/karaf.log for a line starting 'Login sample'." ;;
        *)   fail "The sample did not come up at ${UNOMI_URL}/login/authenticate after ${i}s (last status
       ${DEPLOY_STATUS}). Check ${KARAF_DIR}/data/log/karaf.log for a line starting 'Login sample'." ;;
    esac
fi
echo "    up and configured after ${i}s"

cat <<EOF

========================================================================
 Login sample is ready.

   Page:           ${UNOMI_URL}/login/index.html
   Demo password:  ${LOGIN_PASSWORD}

 Log in with any email address and the password above. The password was
 generated for this run - it is not stored anywhere else, so copy it now.
========================================================================

To remove the sample, run:

  rm -f "${KARAF_DIR}/deploy/$(basename "$SAMPLE_JAR")"
  rm -f "${CFG}"

Karaf uninstalls the bundle as soon as the jar disappears from deploy/.
EOF
