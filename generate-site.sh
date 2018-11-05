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
echo Generating documentation...
mvn clean
cd manual
mvn -Phtml -Ddoc.source=src/archives/1.1/asciidoc -Ddoc.output.html=target/generated-html/1_1_x
mvn -Ppdf -Ddoc.source=src/archives/1.1/asciidoc -Ddoc.output.pdf=target/generated-pdf/1_1_x
mvn -Phtml -Ddoc.source=src/archives/1.2/asciidoc -Ddoc.output.html=target/generated-html/1_2_x
mvn -Ppdf -Ddoc.source=src/archives/1.2/asciidoc -Ddoc.output.pdf=target/generated-pdf/1_2_x
mvn -Phtml -Ddoc.source=src/archives/1.3/asciidoc -Ddoc.output.html=target/generated-html/1_3_x
mvn -Ppdf -Ddoc.source=src/archives/1.3/asciidoc -Ddoc.output.pdf=target/generated-pdf/1_3_x
mvn -Phtml
mvn -Ppdf
cd ..
echo Generating Javadoc...
mvn javadoc:aggregate -P integration-tests
cd rest
mvn package
cd ..
mkdir target/staging/unomi-api
mkdir target/staging/manual
cp -R target/site/apidocs target/staging/unomi-api
cp -Rf manual/target/generated-html/* target/staging/manual
echo Documentation generation completed!