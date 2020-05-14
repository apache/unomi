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
echo Generating manual...
mvn clean
cd manual
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.1/asciidoc -Ddoc.output.pdf=target/generated-docs/1_1_x/pdf -Ddoc.output.html=target/generated-docs/1_1_x/html -Ddoc.version=1.1 -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.2/asciidoc -Ddoc.output.pdf=target/generated-docs/1_2_x/pdf -Ddoc.output.html=target/generated-docs/1_2_x/html -Ddoc.version=1.2 -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.3/asciidoc -Ddoc.output.pdf=target/generated-docs/1_3_x/pdf -Ddoc.output.html=target/generated-docs/1_3_x/html -Ddoc.version=1.3 -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.4/asciidoc -Ddoc.output.pdf=target/generated-docs/1_4_x/pdf -Ddoc.output.html=target/generated-docs/1_4_x/html -Ddoc.version=1.4 -P sign verify
mvn -Ddoc.archive=true -Ddoc.source=src/archives/1.5/asciidoc -Ddoc.output.pdf=target/generated-docs/1_5_x/pdf -Ddoc.output.html=target/generated-docs/1_4_x/html -Ddoc.version=1.5 -P sign verify
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
echo Documentation generation completed!