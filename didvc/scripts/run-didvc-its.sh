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

# Runs the DID-VC integration tests (DidvcSmokeIT by default, which
# covers the phase 2-4 smoke and cross-method resolution suites) inside
# the real Karaf container against a Dockerized Elasticsearch, the same
# path used for the phase 2/3 IT verification runs. Requires Docker (the
# itests module starts the itests-elasticsearch container itself) and the
# reactor artifacts already installed in the local Maven repository (a
# preceding `mvn install` / `./build.sh`).
#
# Usage: didvc/scripts/run-didvc-its.sh [extra maven args]
#   IT_TESTS="DidvcSmokeIT" didvc/scripts/run-didvc-its.sh

set -e

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
M2_CACHE="${M2_CACHE:-/tmp/m2-unomi}"

TESTS="${IT_TESTS:-DidvcSmokeIT}"
INCLUDES=""
IFS=',' read -ra TEST_ARRAY <<< "$TESTS"
for t in "${TEST_ARRAY[@]}"; do
    if [ -n "$INCLUDES" ]; then
        INCLUDES="$INCLUDES,"
    fi
    INCLUDES="${INCLUDES}**/${t}.java"
done

docker run --rm -u root --network host \
  -v "${REPO_ROOT}":/ws \
  -v "${M2_CACHE}":/root/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -w /ws maven:3.9-eclipse-temurin-17 \
  mvn -B -P integration-tests -pl itests install \
  -Dit.test="${TESTS}" \
  "-Dfailsafe.includes=${INCLUDES}" \
  "$@"
