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
# Quiet evaluate: avoid capturing Maven download lines into the environment (breaks CI with ARG_MAX).
export UNOMI_VERSION="$(mvn -B -q -DforceStdout help:evaluate -Dexpression=project.version -DinteractiveMode=false 2>/dev/null)"
if [ -z "$UNOMI_VERSION" ]; then
    echo "Failed to detect project version from Maven" >&2
    exit 1
fi
echo "Detected project version=$UNOMI_VERSION"
export KARAF_VERSION=4.4.8
# Uncomment the following line if you need Apache Unomi to start automatically at the first start
# export KARAF_OPTS="-Dunomi.autoStart=true"
