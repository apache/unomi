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

# Near the top of the file, add these default debug settings
KARAF_DEBUG=${KARAF_DEBUG:-false}
KARAF_DEBUG_PORT=${KARAF_DEBUG_PORT:-5005}
KARAF_DEBUG_SUSPEND=${KARAF_DEBUG_SUSPEND:-n}

# Before starting Karaf, add debug configuration
if [ "$KARAF_DEBUG" = "true" ]; then
    echo "Enabling Karaf debug mode on port $KARAF_DEBUG_PORT (suspend=$KARAF_DEBUG_SUSPEND)"
    export JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$KARAF_DEBUG_SUSPEND,address=*:$KARAF_DEBUG_PORT"
    export KARAF_DEBUG=true
fi

UNOMI_DISTRIBUTION="${UNOMI_DISTRIBUTION:-unomi-distribution-elasticsearch}"
export KARAF_OPTS="-Dunomi.autoStart=${UNOMI_AUTO_START} -Dunomi.distribution=${UNOMI_DISTRIBUTION}"

echo "KARAF_OPTS: $KARAF_OPTS"

# Function to check cluster health for a specific node
check_node_health() {
    local node_url="$1"
    local curl_opts="$2"
    response=$(eval curl -v -fsSL ${curl_opts} "${node_url}" 2>&1)
    if [ $? -eq 0 ]; then
        echo "$response" | grep -o '"status"[ ]*:[ ]*"[^"]*"' | cut -d'"' -f4
    else
        echo ""
    fi
}

# Configure connection parameters based on persistence config
if [[ "$UNOMI_DISTRIBUTION" == *opensearch* ]]; then
    # OpenSearch configuration
    PERSISTENCE_TYPE="opensearch"
    if [ -z "$UNOMI_OPENSEARCH_PASSWORD" ]; then
        echo "Error: UNOMI_OPENSEARCH_PASSWORD must be set when using OpenSearch"
        exit 1
    fi

    schema='https'
    auth_header="Authorization: Basic $(echo -n "admin:${UNOMI_OPENSEARCH_PASSWORD}" | base64)"
    health_endpoint="_cluster/health"
    curl_opts="-k -H \"${auth_header}\" -H \"Content-Type: application/json\""
    # Build array of node URLs
    IFS=',' read -ra NODES <<< "${UNOMI_OPENSEARCH_ADDRESSES}"
elif [[ "$UNOMI_DISTRIBUTION" == *elasticsearch* ]]; then
    # Elasticsearch configuration
    PERSISTENCE_TYPE="elasticsearch"
    if [ "$UNOMI_ELASTICSEARCH_SSL_ENABLE" = 'true' ]; then
        schema='https'
    else
        schema='http'
    fi

    if [ -v UNOMI_ELASTICSEARCH_USERNAME ] && [ -v UNOMI_ELASTICSEARCH_PASSWORD ]; then
        auth_header="Authorization: Basic $(echo -n "${UNOMI_ELASTICSEARCH_USERNAME}:${UNOMI_ELASTICSEARCH_PASSWORD}" | base64)"
        curl_opts="-H \"${auth_header}\""
    fi
    health_endpoint="_cluster/health"
    # Build array of node URLs
    IFS=',' read -ra NODES <<< "${UNOMI_ELASTICSEARCH_ADDRESSES}"
else
    echo "Error: unable to determine persistence type from distribution name: $UNOMI_DISTRIBUTION"
    exit 1
fi

# Wait for search engine to be ready
echo "Waiting for ${PERSISTENCE_TYPE} to be ready..."
echo "Checking nodes: ${NODES[@]}"
health_check=""

while ([ -z "$health_check" ] || ([ "$health_check" != 'yellow' ] && [ "$health_check" != 'green' ])); do
    # Try each node until we get a successful response
    for node in "${NODES[@]}"; do
        node_url="${schema}://${node}/${health_endpoint}"
        echo "Checking health at: ${node_url}"
        health_check=$(check_node_health "$node_url" "$curl_opts")

        if [ ! -z "$health_check" ]; then
            echo "Successfully connected to node: $node (status: ${health_check})"
            break
        else
            >&2 echo "Connection failed to node: $node"
        fi
    done

    if [ -z "$health_check" ]; then
        >&2 echo "${PERSISTENCE_TYPE^} is not yet available - all nodes unreachable"
        sleep 3
        continue
    fi

    if [ "$health_check" != 'yellow' ] && [ "$health_check" != 'green' ]; then
        >&2 echo "${PERSISTENCE_TYPE^} health status: ${health_check} (waiting for yellow or green)"
        sleep 3
    else
        >&2 echo "${PERSISTENCE_TYPE^} health status: ${health_check}"
    fi
done

echo "${PERSISTENCE_TYPE^} is ready with health status: ${health_check}"

# Run Unomi in current bash session
exec "$UNOMI_HOME/bin/karaf" run
