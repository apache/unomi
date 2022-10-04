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
    echo "Illegal number of arguments supplied. Syntax should be generate-site-and-upload.sh SVNusername SVNpassword"
    exit 1
fi
echo Generating manual...
mvn clean
cd manual
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.1/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_1_x -Ddoc.output.html=target/generated-docs/html/1_1_x -Ddoc.version=1_1_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.2/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_2_x -Ddoc.output.html=target/generated-docs/html/1_2_x -Ddoc.version=1_2_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.3/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_3_x -Ddoc.output.html=target/generated-docs/html/1_3_x -Ddoc.version=1_3_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.4/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_4_x -Ddoc.output.html=target/generated-docs/html/1_4_x -Ddoc.version=1_4_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.5/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_5_x -Ddoc.output.html=target/generated-docs/html/1_5_x -Ddoc.version=1_5_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.6/asciidoc -Ddoc.output.pdf=target/generated-docs/pdf/1_6_x -Ddoc.output.html=target/generated-docs/html/1_6_x -Ddoc.version=1_6_x -P sign verify
mvn -Ddoc.archive=true -Ddoc.output.pdf=target/generated-docs/pdf/2_0_x -Ddoc.output.html=target/generated-docs/html/2_0_x -Ddoc.version=2_0_x -P sign install
mvn  -P sign install
cd ..
echo Generating Javadoc...
mvn javadoc:aggregate -P integration-tests
echo Generating REST API...
cd rest
mvn package
cd ..
mkdir -p target/staging/unomi-api
mkdir -p target/staging/manual
cp -R target/site/apidocs target/staging/unomi-api
cp -Rf manual/target/generated-docs/html/* target/staging/manual
echo Committing documentation to Apache SVN...
mvn scm-publish:publish-scm -Dscmpublish.pubScmUrl=scm:svn:https://svn.apache.org/repos/asf/unomi/website/manual -Dscmpublish.content=target/staging/manual -Dusername=$1 -Dpassword=$2
mvn scm-publish:publish-scm -Dscmpublish.pubScmUrl=scm:svn:https://svn.apache.org/repos/asf/unomi/website/unomi-api -Dscmpublish.content=target/staging/unomi-api -Dusername=$1 -Dpassword=$2
# mvn scm-publish:publish-scm -Dscmpublish.pubScmUrl=scm:svn:https://svn.apache.org/repos/asf/unomi/website/rest-api-doc -Dscmpublish.content=target/staging/rest-api-doc -Dusername=$1 -Dpassword=$2
echo Documentation generation and upload completed.