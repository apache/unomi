#!/usr/bin/env bash
set -ev

git reset --hard 1.5.2-SNAPSHOT
cp .travis.settings.xml $HOME/.m2/settings.xml
mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean install deploy -DskipTests -Drat.skip=true -DaltDeploymentRepository=snapshots::default::https://yotpo.jfrog.io/artifactory/maven