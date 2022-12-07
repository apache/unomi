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
if [ $# -ne 4 ]
  then
    echo "Illegal number of arguments supplied. Syntax should be generate-site-and-upload.sh X_X_X X.X.X SVNusername SVNpassword "
    echo "Example: ./generate-site-and-upload.sh 2_0_x 2.0.1 user password"
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
BRANCH_NAME=$1
VERSION=$2
SVN_USERNAME=$3
SVN_PASSWORD=$4
LOCAL_BRANCH_NAME=`git rev-parse --abbrev-ref HEAD`
echo Git local branch: ${LOCAL_BRANCH_NAME}
bash generate-site.sh $BRANCH_NAME $VERSION
echo Committing documentation to Apache SVN...
mvn scm-publish:publish-scm -Dscmpublish.pubScmUrl=scm:svn:https://svn.apache.org/repos/asf/unomi/website/manual -Dscmpublish.content=target/staging/manual -Dusername=$SVN_USERNAME -Dpassword=$SVN_PASSWORD
if [ "$LOCAL_BRANCH_NAME" == "master" ]; then
  mvn scm-publish:publish-scm -Dscmpublish.pubScmUrl=scm:svn:https://svn.apache.org/repos/asf/unomi/website/unomi-api -Dscmpublish.content=target/staging/unomi-api -Dusername=$SVN_USERNAME -Dpassword=$SVN_PASSWORD
fi

echo "Committing manual to Apache Dist SVN..."
pushd manual/target
svn co https://dist.apache.org/repos/dist/release/unomi/$VERSION
mv unomi-manual-$BRANCH_NAME.pdf $VERSION
mv unomi-manual-$BRANCH_NAME.pdf.asc $VERSION
mv unomi-manual-$BRANCH_NAME.zip $VERSION
mv unomi-manual-$BRANCH_NAME.pdf.sha512 $VERSION
mv unomi-manual-$BRANCH_NAME.zip.asc $VERSION
mv unomi-manual-$BRANCH_NAME.zip.sha512 $VERSION
cd $VERSION
svn add unomi-manual*
svn commit -m "Update Unomi manual packages for version ${VERSION}"
popd
echo Documentation generation and upload completed.
