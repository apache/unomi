#!/bin/sh
echo Building...
mvn clean install -P generate-package
pushd package/target
echo Uncompressing Unomi package...
tar zxvf context-server-package-1.0-SNAPSHOT.tar.gz
cd context-server-package-1.0-SNAPSHOT/bin
echo Starting Unomi...
./karaf debug
popd

