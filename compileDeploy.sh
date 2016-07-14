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
echo Setting up environment...
DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
if [ -f "$DIRNAME/setenv.sh" ]; then
  . "$DIRNAME/setenv.sh"
fi
if [ "x$CONTEXT_SERVER_KARAF_HOME" = "x" ]; then
    CONTEXT_SERVER_KARAF_HOME=~/java/deployments/context-server/apache-karaf-$KARAF_VERSION
    export CONTEXT_SERVER_KARAF_HOME
fi
echo Compiling...
mvn clean install
echo Deploying KAR package to $CONTEXT_SERVER_KARAF_HOME/deploy...
cp kar/target/unomi-kar-$UNOMI_VERSION.kar $CONTEXT_SERVER_KARAF_HOME/deploy/
echo Purging Karaf local Maven repository, exploded KAR directory and temporary directory... 
rm -rf $CONTEXT_SERVER_KARAF_HOME/data/maven/repository/*
rm -rf $CONTEXT_SERVER_KARAF_HOME/data/tmp/*
echo Compilation and deployment completed successfully.
