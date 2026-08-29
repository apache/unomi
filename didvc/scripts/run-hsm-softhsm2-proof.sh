#!/bin/bash
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

# FR-G2 acceptance: signing with SoftHSM2. Installs SoftHSM2 in a
# throwaway JDK-17 container, initializes a per-run token (random PIN
# generated inside the container — no credential literals anywhere),
# generates the signing key ON the token with keytool (SunPKCS11), then
# runs the Pkcs11Softhsm2Proof main: sign inside the token, verify the
# JWS against the token's public key. Prints PROOF-OK on success.
#
# Usage: didvc/scripts/run-hsm-softhsm2-proof.sh

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M2_CACHE="${M2_CACHE:-/tmp/m2-unomi}"

# Mask .mvn (develocity core extensions cannot resolve offline)
EMPTY_MVN_DIR="$(mktemp -d)"

docker run --rm -u root \
  -v "${REPO_ROOT}":/ws \
  -v "${M2_CACHE}":/home/build-user/.m2 \
  -v "${EMPTY_MVN_DIR}":/ws/.mvn \
  -w /ws maven:3.9-eclipse-temurin-17 \
  bash -c '
    set -e
    groupadd -g 1001 build-group >/dev/null 2>&1 || true
    useradd -u 1001 -g 1001 -d /home/build-user build-user >/dev/null 2>&1 || true
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq && apt-get install -y -qq softhsm2 >/dev/null
    LIBRARY=$(ls /usr/lib/*/softhsm/libsofthsm2.so /usr/lib/softhsm/libsofthsm2.so 2>/dev/null | head -1)
    echo "SoftHSM2 library: ${LIBRARY}"
    TOKEN_DIR=$(mktemp -d)
    PIN=$(tr -dc A-Za-z0-9 </dev/urandom | head -c 24)
    KID=hsm-proof-$(tr -dc a-z0-9 </dev/urandom | head -c 8)
    printf "directories.tokendir = %s\nobjectstore.backend = file\n" "${TOKEN_DIR}" > "${TOKEN_DIR}/softhsm2.conf"
    export SOFTHSM2_CONF="${TOKEN_DIR}/softhsm2.conf"
    softhsm2-util --init-token --free --label "${KID}" --so-pin "${PIN}" --pin "${PIN}"
    CFG=$(mktemp)
    printf "name = didvc-softhsm\nlibrary = %s\nslotListIndex = 0\n" "${LIBRARY}" > "${CFG}"
    keytool -keystore NONE -storetype PKCS11 \
      -providerclass sun.security.pkcs11.SunPKCS11 -providerarg "${CFG}" \
      -genkeypair -alias "${KID}" -keyalg EC -groupname secp256r1 \
      -dname "CN=didvc-hsm-proof" -storepass "${PIN}" -noprompt >/dev/null
    echo "Token key generated (alias ${KID}); private material stays on the token"
    # Offline maven against the build cache (.mvn masked on the host
    # mount; remote-snapshot chasing disabled); -am builds the didvc
    # reactor from source so no installed snapshots are needed
    chown -R 1001:1001 /home/build-user
    # The proof main runs as build-user: the root-created token dir and
    # PKCS#11 config must be readable for it
    chmod -R a+rx "${TOKEN_DIR}" || true
    chmod a+r "${CFG}" || true
    mvnOffline() {
      runuser -u build-user -- env HOME=/home/build-user mvn -o -B "$@"
    }
    mvnOffline -pl didvc/didvc-api,didvc/didvc-sd-jwt,didvc/didvc-services -am compile test-compile -DskipTests
    mvnOffline dependency:build-classpath -pl didvc/didvc-services -Dmdep.outputFile=/tmp/cp.txt
    CP="didvc/didvc-services/target/classes:didvc/didvc-services/target/test-classes:didvc/didvc-sd-jwt/target/classes:$(cat /tmp/cp.txt)"
    runuser -u build-user -- java -cp "${CP}" org.apache.unomi.didvc.services.impl.Pkcs11Softhsm2Proof "${CFG}" "${PIN}" "${KID}"
    softhsm2-util --delete-token --token "${KID}" >/dev/null || true
  '
STATUS=$?
rm -rf "${EMPTY_MVN_DIR}"
exit ${STATUS}
