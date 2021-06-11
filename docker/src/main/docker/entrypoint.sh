#!/bin/bash

################################################################################
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
################################################################################
# Wait for heathy ElasticSearch
# next wait for ES status to turn to Green

if [ -v UNOMI_ELASTICSEARCH_USERNAME ] && [ -v UNOMI_ELASTICSEARCH_PASSWORD ]; then
  elasticsearch_addresses="$UNOMI_ELASTICSEARCH_USERNAME:$UNOMI_ELASTICSEARCH_PASSWORD@$UNOMI_ELASTICSEARCH_ADDRESSES/_cat/health?h=status"
else
  elasticsearch_addresses="$UNOMI_ELASTICSEARCH_ADDRESSES/_cat/health?h=status"
fi

health_check="$(curl -fsSL "$elasticsearch_addresses")"

until ([ "$health_check" = 'yellow' ] || [ "$health_check" = 'green' ]); do
    health_check="$(curl -fsSL $elasticsearch_addresses)"
    >&2 echo "Elastic Search is not yet available - waiting (health check=$health_check)..."
    sleep 1
done

$UNOMI_HOME/bin/start
$UNOMI_HOME/bin/status # Call to status delays while Karaf creates karaf.log

tail -f $UNOMI_HOME/data/log/karaf.log
