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
if [ $# -eq 0 ]
  then
    echo "Please use arguments RELEASE_VERSION RELEASE_BRANCH. Ex: ./release-stage-1.sh 2.1.0 master"
    exit 1
fi
set -e
# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?."' EXIT
export RELEASE_VERSION=$1
export RELEASE_BRANCH=$2
echo Prepare staging 1 of release process for version $RELEASE_VERSION in branch $RELEASE_BRANCH...
mkdir -p target/release/$RELEASE_VERSION
pushd target/release/$RELEASE_VERSION
git clone https://gitbox.apache.org/repos/asf/unomi.git unomi-$RELEASE_VERSION
cd unomi-$RELEASE_VERSION
git checkout $RELEASE_BRANCH
mvn clean install -P apache-release,integration-tests,docker
mvn clean install -DskipITs=true -DskipTests=true -P integration-tests,rat,apache-release,docker,\!run-tests

pushd target
gpg --verify unomi-root-$RELEASE_VERSION-SNAPSHOT-source-release.zip.asc unomi-root-$RELEASE_VERSION-SNAPSHOT-source-release.zip
shasum -a 512 unomi-root-$RELEASE_VERSION-SNAPSHOT-source-release.zip
cat unomi-root-$RELEASE_VERSION-SNAPSHOT-source-release.zip.sha512
unzip unomi-root-$RELEASE_VERSION-SNAPSHOT-source-release.zip
cd unomi-root-$RELEASE_VERSION-SNAPSHOT
mvn clean install
popd

mvn release:prepare -DskipITs=true -DskipTests=true -Drelease.arguments="-Papache-release,integration-tests,\!run-tests -DskipITs=true -DskipTests=true" -DdryRun=true -P apache-release,integration-tests,docker,\!run-tests
mvn deploy -DskipITs=true -DskipTests=true -Drelease.arguments="-Papache-release,integration-tests,\!run-tests -DskipITs=true -DskipTests=true" -P \!run-tests
rm release.properties
mvn -DskipITs=true -DskipTests=true -Drelease.arguments="-Papache-release,integration-tests,\!run-tests -DskipITs=true -DskipTests=true" -P apache-release,integration-tests,docker,\!run-tests release:prepare
mvn -DskipITs=true -DskipTests=true -Drelease.arguments="-Papache-release,integration-tests,\!run-tests -DskipITs=true -DskipTests=true" -P integration-tests,docker,\!run-tests release:perform
popd
