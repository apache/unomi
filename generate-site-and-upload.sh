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
echo Generating site...
mvn clean install site site:stage -P integration-tests,performance-tests
cd rest
mvn package
cd -
echo Committing site to Apache SVN...
mvn scm-publish:publish-scm -Dusername=$1 -Dpassword=$2
echo Site generation and upload completed.