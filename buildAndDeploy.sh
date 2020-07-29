#!/usr/bin/env bash
set -ev

cp .travis.settings.xml $HOME/.m2/settings.xml
mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean install deploy -DskipTests -Drat.skip=true --update-snapshots -DaltDeploymentRepository=snapshots::default::https://yotpo.jfrog.io/artifactory/maven