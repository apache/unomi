#!/bin/bash
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
# Quick setup script to restore Elasticsearch snapshot and analyze with Kibana

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SNAPSHOT_ZIP="$SCRIPT_DIR/src/test/resources/migration/snapshots_repository.zip"
EXTRACT_DIR="$SCRIPT_DIR/snapshots_repository"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-snapshot-analysis.yml"

echo "=========================================="
echo "Elasticsearch Snapshot Analysis Setup"
echo "=========================================="

# Step 1: Extract snapshot repository (matching Maven build process from pom.xml line 235)
if [ ! -d "$EXTRACT_DIR" ]; then
    echo "Extracting snapshot repository..."
    echo "  (Matching Maven build: unzip to ${project.build.directory}/snapshots_repository)"
    unzip -q "$SNAPSHOT_ZIP" -d "$SCRIPT_DIR"
    echo "✓ Extracted to $EXTRACT_DIR"
else
    echo "✓ Snapshot repository already extracted"
fi

# Step 2: Start Elasticsearch and Kibana
echo ""
echo "Starting Elasticsearch and Kibana..."
cd "$SCRIPT_DIR"
docker-compose -f "$COMPOSE_FILE" up -d

# Step 3: Wait for Elasticsearch to be ready
echo ""
echo "Waiting for Elasticsearch to be ready..."
max_attempts=60
attempt=0
while [ $attempt -lt $max_attempts ]; do
    if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
        echo "✓ Elasticsearch is ready!"
        break
    fi
    attempt=$((attempt + 1))
    echo "  Attempt $attempt/$max_attempts..."
    sleep 2
done

if [ $attempt -eq $max_attempts ]; then
    echo "✗ Elasticsearch failed to start"
    exit 1
fi

# Step 4: Register snapshot repository (matching test configuration)
echo ""
echo "Registering snapshot repository..."
REPO_RESPONSE=$(curl -s -X PUT "http://localhost:9200/_snapshot/snapshots_repository" \
  -H 'Content-Type: application/json' \
  -d '{
    "type": "fs",
    "settings": {
      "location": "snapshots"
    }
  }')

if echo "$REPO_RESPONSE" | grep -q '"acknowledged":true'; then
    echo "✓ Snapshot repository registered"
else
    echo "✗ Failed to register repository: $REPO_RESPONSE"
    exit 1
fi

# Step 5: Verify snapshot exists (matching test configuration)
echo ""
echo "Verifying snapshot exists..."
SNAPSHOT_NAME="snapshot_2"  # From Migrate16xToCurrentVersionIT.java line 55
SNAPSHOT_CHECK=$(curl -s "http://localhost:9200/_snapshot/snapshots_repository/$SNAPSHOT_NAME")
if [ -z "$SNAPSHOT_CHECK" ] || echo "$SNAPSHOT_CHECK" | grep -q '"error"'; then
    echo "✗ Snapshot $SNAPSHOT_NAME not found"
    echo "Response: $SNAPSHOT_CHECK"
    echo ""
    echo "Available snapshots:"
    curl -s "http://localhost:9200/_snapshot/snapshots_repository/_all" | python3 -m json.tool 2>/dev/null || curl -s "http://localhost:9200/_snapshot/snapshots_repository/_all"
    exit 1
fi
echo "✓ Snapshot $SNAPSHOT_NAME found"

# Step 6: Restore snapshot (matching test configuration)
echo ""
echo "Restoring snapshot: $SNAPSHOT_NAME"
RESTORE_RESPONSE=$(curl -s -X POST "http://localhost:9200/_snapshot/snapshots_repository/$SNAPSHOT_NAME/_restore?wait_for_completion=true" \
  -H 'Content-Type: application/json' \
  -d '{}')

if echo "$RESTORE_RESPONSE" | grep -q '"snapshot"'; then
    echo "✓ Snapshot restored successfully!"
else
    echo "⚠ Restore response: $RESTORE_RESPONSE"
    echo "Snapshot may still be restoring. Check status with:"
    echo "  curl http://localhost:9200/_snapshot/snapshots_repository/$SNAPSHOT_NAME/_status"
fi

# Step 7: List restored indices
echo ""
echo "Restored indices:"
curl -s "http://localhost:9200/_cat/indices?v"

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Access Kibana at: http://localhost:5601"
echo ""
echo "Useful commands:"
echo "  - List indices: curl http://localhost:9200/_cat/indices?v"
echo "  - Search an index: curl http://localhost:9200/INDEX_NAME/_search?pretty"
echo "  - Stop services: docker-compose -f $COMPOSE_FILE down"
echo "  - View logs: docker-compose -f $COMPOSE_FILE logs -f"
echo ""
echo "Configuration details (matching test setup):"
echo "  - Repository name: snapshots_repository (from Migrate16xToCurrentVersionIT.java:53)"
echo "  - Snapshot name: snapshot_2 (from Migrate16xToCurrentVersionIT.java:55)"
echo "  - Repository location: snapshots (relative to path.repo, from create_snapshots_repository.json)"
echo "  - path.repo: /usr/share/elasticsearch/snapshots_repository (matching pom.xml:356)"
echo "  - Elasticsearch version: 9.1.3 (matching pom.xml:347)"
echo "  - Snapshot extraction: matches pom.xml:235 (unzip to target/snapshots_repository)"
echo ""

