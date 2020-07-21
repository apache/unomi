#!/usr/bin/env bash
set -ev

mvn -B -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn clean install -Drat.skip=true