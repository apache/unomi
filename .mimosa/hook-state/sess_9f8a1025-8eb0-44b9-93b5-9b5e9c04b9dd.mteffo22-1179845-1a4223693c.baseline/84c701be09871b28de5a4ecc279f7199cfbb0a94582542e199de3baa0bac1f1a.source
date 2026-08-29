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
echo Prepare staging 2 of release process...
if [ $# -eq 0 ]
  then
    echo "Please use arguments RELEASE_VERSION NEXUS_REPOSITORY_ID. Ex: ./release-stage-2.sh 2.1.0 orgapacheunomi-1033"
    exit 1
fi
set -e
# keep track of the last executed command
trap 'last_command=$current_command; current_command=$BASH_COMMAND' DEBUG
# echo an error message before exiting
trap 'echo "\"${last_command}\" command filed with exit code $?."' EXIT

export RELEASE_VERSION=$1
export REPO_ID=$2
pushd target/release/$RELEASE_VERSION
svn checkout https://dist.apache.org/repos/dist/dev/unomi unomi-dev
cd unomi-dev
mkdir $RELEASE_VERSION
cd $RELEASE_VERSION

wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi-root/$RELEASE_VERSION/unomi-root-$RELEASE_VERSION-source-release.zip
wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi-root/$RELEASE_VERSION/unomi-root-$RELEASE_VERSION-source-release.zip.asc
wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi/$RELEASE_VERSION/unomi-$RELEASE_VERSION.tar.gz
wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi/$RELEASE_VERSION/unomi-$RELEASE_VERSION.tar.gz.asc
wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi/$RELEASE_VERSION/unomi-$RELEASE_VERSION.zip
wget https://repository.apache.org/content/repositories/$REPO_ID/org/apache/unomi/unomi/$RELEASE_VERSION/unomi-$RELEASE_VERSION.zip.asc

mv unomi-root-$RELEASE_VERSION-source-release.zip unomi-$RELEASE_VERSION-src.zip
mv unomi-root-$RELEASE_VERSION-source-release.zip.asc unomi-$RELEASE_VERSION-src.zip.asc
shasum -a 512 unomi-$RELEASE_VERSION-src.zip > unomi-$RELEASE_VERSION-src.zip.sha512

mv unomi-$RELEASE_VERSION.zip unomi-$RELEASE_VERSION-bin.zip
mv unomi-$RELEASE_VERSION.zip.asc unomi-$RELEASE_VERSION-bin.zip.asc
shasum -a 512 unomi-$RELEASE_VERSION-bin.zip > unomi-$RELEASE_VERSION-bin.zip.sha512

mv unomi-$RELEASE_VERSION.tar.gz unomi-$RELEASE_VERSION-bin.tar.gz
mv unomi-$RELEASE_VERSION.tar.gz.asc unomi-$RELEASE_VERSION-bin.tar.gz.asc
shasum -a 512 unomi-$RELEASE_VERSION-bin.tar.gz > unomi-$RELEASE_VERSION-bin.tar.gz.sha512

cd ..
svn add $RELEASE_VERSION

svn commit -m "Apache $RELEASE_VERSION Release (for PMC voting)"
popd
