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

# Runs the canonical repository build script (./build.sh) inside the
# JDK 17 Maven image. The host only has JDK 25, whose removal of the
# security manager breaks the legacy
# services-common/KarafSecurityServiceTest (javax.security.auth.Subject
# getSubject is unsupported) — the project builds and tests on JDK 17.
# Graphviz is installed in the container for build.sh's preflight, and
# the build itself runs as the workspace owner (uid 1001) so
# file-permission tests behave and no root-owned target/ directories are
# left behind. The Maven cache is mounted at the build user's home.
# build.sh's final Karaf startup check requires root passwords from the
# environment (no defaults are shipped); throwaway values are generated
# per run when UNOMI_ROOT_PASSWORD/UNOMI_HEALTHCHECK_PASSWORD are unset.
# MAVEN_EXTRA_OPTS is passed through to build.sh (e.g.
# -Dsurefire.rerunFailingTestsCount=2 to retry the load-sensitive
# scheduler test in place).
#
# Usage: didvc/scripts/run-build-sh-jdk17.sh [build.sh args]

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M2_CACHE="${M2_CACHE:-/tmp/m2-unomi}"
WORKSPACE_UID="$(stat -c '%u' "${REPO_ROOT}")"
WORKSPACE_GID="$(stat -c '%g' "${REPO_ROOT}")"

generate_password() {
    head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20
}

UNOMI_ROOT_PASSWORD="${UNOMI_ROOT_PASSWORD:-$(generate_password)}"
UNOMI_HEALTHCHECK_PASSWORD="${UNOMI_HEALTHCHECK_PASSWORD:-$(generate_password)}"

docker run --rm -u root \
  -e UNOMI_ROOT_PASSWORD="${UNOMI_ROOT_PASSWORD}" \
  -e UNOMI_HEALTHCHECK_PASSWORD="${UNOMI_HEALTHCHECK_PASSWORD}" \
  -e MAVEN_EXTRA_OPTS="${MAVEN_EXTRA_OPTS:-}" \
  -v "${REPO_ROOT}":/ws \
  -v "${M2_CACHE}":/home/build-user/.m2 \
  -w /ws maven:3.9-eclipse-temurin-17 \
  bash -c "apt-get update -qq && apt-get install -y -qq graphviz \
    && groupadd -g ${WORKSPACE_GID} build-group >/dev/null 2>&1 || true \
    && useradd -u ${WORKSPACE_UID} -g ${WORKSPACE_GID} -d /home/build-user build-user >/dev/null 2>&1 || true \
    && ([ -f /home/build-user/.m2/settings.xml ] || printf '<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\"\n  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n  xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd\">\n  <localRepository>/home/build-user/.m2/repository</localRepository>\n</settings>\n' > /home/build-user/.m2/settings.xml) \
    && chown -R ${WORKSPACE_UID}:${WORKSPACE_GID} /home/build-user \
    && runuser -u build-user -- env HOME=/home/build-user ./build.sh $*"
