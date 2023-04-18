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
if [ $# -ne 2 ]
  then
    echo "Illegal number of arguments supplied. Syntax should be generate-site.sh X_X_X X.X.X"
    echo "Where X_X_X is either the release branch name or master"
    echo "Example: ./generate-site.sh 2_0_x 2.0.1 or ./generate-site.sh master 2.3.0-SNAPSHOT for updating the master snapshot version"
    exit 1
fi
echo Setting up environment...
DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
if [ -f "$DIRNAME/setenv.sh" ]; then
  . "$DIRNAME/setenv.sh"
fi
set -e
# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?."' EXIT
RELEASE_BRANCH_NAME=$1
RELEASE_VERSION=$2
LOCAL_BRANCH_NAME=`git rev-parse --abbrev-ref HEAD`
echo Git local branch: ${LOCAL_BRANCH_NAME}
echo Generating manual for branch ${RELEASE_BRANCH_NAME} and version ${RELEASE_VERSION}...
mvn clean
pushd manual
if [ "$RELEASE_BRANCH_NAME" != "master" ]; then
  mvn -Ddoc.archive=true -Ddoc.output.pdf=target/generated-docs/pdf/$RELEASE_BRANCH_NAME -Ddoc.output.html=target/generated-docs/html/$RELEASE_BRANCH_NAME -Ddoc.version=$RELEASE_BRANCH_NAME -P sign install
fi
mvn -P sign install
# If not on master branch we remove the latest directories
if [ "$LOCAL_BRANCH_NAME" != "master" ]; then
  rm -rf target/generated-docs/html/latest
  rm -rf target/generated-docs/pdf/latest
fi
popd
if [ "$LOCAL_BRANCH_NAME" == "master" ]; then
  echo Generating Javadoc...
  mvn javadoc:aggregate -P integration-tests
else
  echo Not on master branch, skipping Javadoc generation
fi
mkdir -p target/staging/unomi-api
mkdir -p target/staging/manual
if [ "$LOCAL_BRANCH_NAME" == "master" ]; then
  cp -R target/site/apidocs target/staging/unomi-api
fi
cp -Rf manual/target/generated-docs/html/* target/staging/manual
echo Documentation generation completed!
