#!/bin/sh
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
echo Building...
DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
if [ -f "$DIRNAME/setenv.sh" ]; then
  . "$DIRNAME/setenv.sh"
fi
mvn clean install -P integration-tests,performance-tests,rat
if [ $? -ne 0 ]
then
    exit 1;
fi
pushd package/target
echo Uncompressing Unomi package...
tar zxvf unomi-$UNOMI_VERSION.tar.gz
cd unomi-$UNOMI_VERSION/bin
echo Apache Unomi features installed, use [unomi:start] to start it.
./karaf debug
popd

