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
#!/bin/sh

# Wait for heathy ElasticSearch
# next wait for ES status to turn to Green
health_check="$(curl -fsSL "$ELASTICSEARCH_HOST:9200/_cat/health?h=status")"

until ([ "$health_check" = 'yellow' ] || [ "$health_check" = 'green' ]); do
    health_check="$(curl -fsSL "$ELASTICSEARCH_HOST:9200/_cat/health?h=status")"
    >&2 echo "Elastic Search is unavailable - waiting"
    sleep 1
done

sed -i "s/elasticSearchAddresses=localhost:9300/elasticSearchAddresses=${ELASTICSEARCH_HOST}:${ELASTICSEARCH_PORT}/g" /opt/apache-unomi/etc/org.apache.unomi.persistence.elasticsearch.cfg
$KARAF_HOME/bin/start
$KARAF_HOME/bin/status # Call to status delays while Karaf creates karaf.log
tail -f $KARAF_HOME/data/log/karaf.log
