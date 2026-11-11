#!/usr/bin/env bash
################################################################################
#
#    Licensed to the Apache Software Foundation (ASF) under one or more
#    contributor license agreements.  See the NOTICE file distributed with
#    this work for additional information regarding copyright ownership.
#    The ASF licenses this file to You under the Apache License, Version 2.0
#    (the "License"); you may not use this file except in compliance with
#    the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
################################################################################
set -euo pipefail

mvn clean install -P integration-tests,rat

# UNOMI-1011: the package module must publish the distribution archives, and no longer
# builds or publishes the empty buildcache jar it once had for the (now disabled) build cache.
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
mvn -pl package deploy -DskipTests -DaltDeploymentRepository="local::default::file://${STAGE}"
if find "$STAGE" -name '*buildcache*' | grep -q .; then
  echo "error: staged artifacts must not include a buildcache jar (UNOMI-1011)" >&2
  find "$STAGE" -name '*buildcache*' >&2
  exit 1
fi
if ! find "$STAGE" \( -name 'unomi-*.zip' -o -name 'unomi-*.tar.gz' \) | grep -q .; then
  echo "error: staged artifacts must include the binary distribution archives" >&2
  find "$STAGE" -type f >&2
  exit 1
fi
echo "UNOMI-1011 check ok: distribution present, no buildcache artifact in ${STAGE}"
