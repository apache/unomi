#!/bin/sh
echo Setting up environment...
if [ "x$CONTEXT_SERVER_KARAF_HOME" = "x" ]; then
    CONTEXT_SERVER_KARAF_HOME=~/java/deployments/context-server/apache-karaf-3.0.2
    export CONTEXT_SERVER_KARAF_HOME
fi
echo Compiling...
mvn clean install
echo Deploying KAR package to $CONTEXT_SERVER_KARAF_HOME/deploy...
cp wemi-context-server/kar/target/wemi-context-server-kar-1.0-SNAPSHOT.kar $CONTEXT_SERVER_KARAF_HOME/deploy/
echo Purging Karaf local Maven repository, exploded KAR directory and temporary directory... 
rm -rf $CONTEXT_SERVER_KARAF_HOME/data/maven/repository/*
rm -rf $CONTEXT_SERVER_KARAF_HOME/data/tmp/*
echo Compilation and deployment completed successfully.
