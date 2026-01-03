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

##############################################################################
# Elasticsearch Reindex Script
# This script reindexes data from Elasticsearch 7 to Elasticsearch 9
# It handles both regular indices and rollover indices with their aliases
##############################################################################

# Ensure we're running with bash
if [ -z "$BASH_VERSION" ]; then
    echo "Error: This script requires bash. Please run with: bash $0"
    exit 1
fi

set -e  # Exit on error

##############################################################################
# CONFIGURATION
##############################################################################

# Environment prefix (configurable for multiple environments)
INDEX_PREFIX="${INDEX_PREFIX:-context-}"

# Elasticsearch source (ES7) configuration
ES7_HOST="${ES7_HOST:-http://localhost:9200}"
ES7_HOST_FROM_ES9="${ES7_HOST_FROM_ES9:-${ES7_HOST}}"
ES7_USER="${ES7_USER:-elastic}"
ES7_PASSWORD="${ES7_PASSWORD:-password}"

# Elasticsearch destination (ES9) configuration
ES9_HOST="${ES9_HOST:-http://localhost:9201}"
ES9_USER="${ES9_USER:-elastic}"
ES9_PASSWORD="${ES9_PASSWORD:-password}"

# Batch size for reindexing
BATCH_SIZE="${BATCH_SIZE:-1000}"

##############################################################################
# INDICES CONFIGURATION
##############################################################################

# Regular indices (no rollover)
REGULAR_INDICES=(
    "profile"
    "systemitems"
    "personasession"
    "profilealias"
    "geonameentry"
)

# Rollover indices patterns
# Format: "pattern:alias_name"
# The script will automatically discover all matching indices
ROLLOVER_PATTERNS=(
    "session-*:session"
    "event-*:event"
)

##############################################################################
# FUNCTIONS
##############################################################################

# Function to log messages with timestamp
log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

# Function to log errors
log_error() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] ERROR: $1" >&2
}

# Function to get index stats
get_index_stats() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    response=$(curl -s \
        -u "${user}:${password}" \
        "${host}/${index}/_stats")

    if [ $? -eq 0 ]; then
        echo "$response"
        return 0
    else
        return 1
    fi
}

# Function to extract key stats from index stats response
extract_index_stats() {
    local stats_json=$1
    local index=$2

    local doc_count=$(echo "$stats_json" | jq -r ".indices[\"${index}\"].primaries.docs.count // 0")
    local doc_deleted=$(echo "$stats_json" | jq -r ".indices[\"${index}\"].primaries.docs.deleted // 0")
    local store_size=$(echo "$stats_json" | jq -r ".indices[\"${index}\"].primaries.store.size_in_bytes // 0")
    local store_size_mb=$((store_size / 1024 / 1024))

    echo "${doc_count}|${doc_deleted}|${store_size_mb}"
}

# Temporary files to store source and destination stats
STATS_DIR=$(mktemp -d)
SOURCE_STATS_FILE="${STATS_DIR}/source_stats.txt"
DEST_STATS_FILE="${STATS_DIR}/dest_stats.txt"

# Cleanup function
cleanup() {
    rm -rf "$STATS_DIR"
}

# Register cleanup on exit
trap cleanup EXIT

# Function to discover indices matching a pattern
discover_indices() {
    local host=$1
    local user=$2
    local password=$3
    local pattern=$4

    response=$(curl -s \
        -u "${user}:${password}" \
        "${host}/_cat/indices/${pattern}?h=index&format=json")

    # Extract index names
    echo "$response" | jq -r '.[].index' 2>/dev/null || echo ""
}

# Function to check if index exists
check_index_exists() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -u "${user}:${password}" \
        "${host}/${index}")

    if [ "$response" == "200" ]; then
        return 0
    else
        return 1
    fi
}

# Function to get index settings
get_index_settings() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    curl -s -u "${user}:${password}" \
        "${host}/${index}/_settings" | jq .
}

# Function to get index mappings
get_index_mappings() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    curl -s -u "${user}:${password}" \
        "${host}/${index}/_mapping" | jq .
}

# Function to get index aliases
get_index_aliases() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    curl -s -u "${user}:${password}" \
        "${host}/${index}/_alias" | jq .
}

# Function to get ILM policy
get_ilm_policy() {
    local host=$1
    local user=$2
    local password=$3
    local policy_name=$4

    response=$(curl -s -w "\n%{http_code}" \
        -u "${user}:${password}" \
        "${host}/_ilm/policy/${policy_name}")

    http_code=$(echo "$response" | tail -n 1)
    response_body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        echo "$response_body" | jq -r --arg policy "$policy_name" '.[$policy].policy'
        return 0
    else
        return 1
    fi
}

# Function to create ILM policy
create_ilm_policy() {
    local policy_name=$1
    local policy_body=$2

    log "Creating ILM policy ${policy_name} on ES9..."

    response=$(curl -s -w "\n%{http_code}" \
        -u "${ES9_USER}:${ES9_PASSWORD}" \
        -X PUT "${ES9_HOST}/_ilm/policy/${policy_name}" \
        -H 'Content-Type: application/json' \
        -d "{\"policy\": ${policy_body}}")

    http_code=$(echo "$response" | tail -n 1)
    response_body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        log "ILM policy ${policy_name} created successfully"
        return 0
    else
        log_error "Failed to create ILM policy ${policy_name}: ${response_body}"
        return 1
    fi
}

# Function to check if ILM policy exists
check_ilm_policy_exists() {
    local host=$1
    local user=$2
    local password=$3
    local policy_name=$4

    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -u "${user}:${password}" \
        "${host}/_ilm/policy/${policy_name}")

    if [ "$response" == "200" ]; then
        return 0
    else
        return 1
    fi
}

# Function to get ILM policy name from index settings
get_index_ilm_policy() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4

    settings=$(get_index_settings "$host" "$user" "$password" "$index")
    policy_name=$(echo "$settings" | jq -r ".[\"${index}\"].settings.index.lifecycle.name // \"\"")

    if [ -n "$policy_name" ] && [ "$policy_name" != "null" ] && [ "$policy_name" != "" ]; then
        echo "$policy_name"
        return 0
    else
        return 1
    fi
}

# Function to create index on destination
create_index() {
    local index=$1
    local settings=$2
    local mappings=$3
    local ilm_policy=${4:-""}

    log "Creating index ${index} on ES9..."

    # If ILM policy is provided, add it to settings
    if [ -n "$ilm_policy" ] && [ "$ilm_policy" != "null" ]; then
        settings=$(echo "$settings" | jq --arg policy "$ilm_policy" --arg rollover_alias "$(echo $index| sed -r "s/(.*)-[0-9]+/\1/")" '. + {"lifecycle": {"name": $policy,"rollover_alias": $rollover_alias}}')
        log "Index will be associated with ILM policy: ${ilm_policy}"
    fi

    # Prepare the request body
    local body=$(jq -n \
        --argjson settings "$settings" \
        --argjson mappings "$mappings" \
        '{settings: $settings, mappings: $mappings}')

    response=$(curl -s -w "\n%{http_code}" \
        -u "${ES9_USER}:${ES9_PASSWORD}" \
        -X PUT "${ES9_HOST}/${index}" \
        -H 'Content-Type: application/json' \
        -d "$body")

    http_code=$(echo "$response" | tail -n 1)
    response_body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ] || [ "$http_code" == "201" ]; then
        log "Index ${index} created successfully"
        return 0
    else
        log_error "Failed to create index ${index}: ${response_body}"
        return 1
    fi
}

# Function to create alias
create_alias() {
    local index=$1
    local alias=$2
    local is_write_index=${3:-false}

    log "Creating alias ${alias} for index ${index} (is_write_index=${is_write_index})..."

    local alias_config="{\"index\": \"${index}\", \"alias\": \"${alias}\""

    if [ "$is_write_index" == "true" ]; then
        alias_config="${alias_config}, \"is_write_index\": true"
    fi

    alias_config="${alias_config}}"

    response=$(curl -s -w "\n%{http_code}" \
        -u "${ES9_USER}:${ES9_PASSWORD}" \
        -X POST "${ES9_HOST}/_aliases" \
        -H 'Content-Type: application/json' \
        -d "{
            \"actions\": [
                {
                    \"add\": ${alias_config}
                }
            ]
        }")

    http_code=$(echo "$response" | tail -n 1)

    if [ "$http_code" == "200" ]; then
        log "Alias ${alias} created successfully"
        return 0
    else
        log_error "Failed to create alias ${alias}"
        return 1
    fi
}

# Function to check task status
check_task_status() {
    local task_id=$1

    response=$(curl -s -w "\n%{http_code}" \
        -u "${ES9_USER}:${ES9_PASSWORD}" \
        "${ES9_HOST}/_tasks/${task_id}")

    http_code=$(echo "$response" | tail -n 1)
    response_body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        echo "$response_body" | tr -d '\000-\037'
        return 0
    else
        log_error "Failed to get task status: ${response_body}"
        return 1
    fi
}

# Function to wait for task completion
wait_for_task() {
    local task_id=$1
    local check_interval=5  # Check every 5 seconds

    log "Waiting for task ${task_id} to complete..."

    while true; do
        sleep $check_interval

        task_status=$(check_task_status "$task_id")
        if [ $? -ne 0 ]; then
            log_error "Failed to check task status"
            return 1
        fi

        completed=$(echo "$task_status" | jq -r '.completed')

        if [ "$completed" == "true" ]; then
            log "Task completed successfully"

            # Get task response
            response_data=$(echo "$task_status" | jq -r '.response')

            # Parse and display reindex results
            total=$(echo "$response_data" | jq -r '.total // 0')
            created=$(echo "$response_data" | jq -r '.created // 0')
            updated=$(echo "$response_data" | jq -r '.updated // 0')
            failures=$(echo "$response_data" | jq -r '.failures | length')

            log "Reindex results: Total=${total}, Created=${created}, Updated=${updated}, Failures=${failures}"

            if [ "$failures" -gt 0 ]; then
                log_error "Some documents failed to reindex:"
                echo "$response_data" | jq '.failures'
            fi

            return 0
        fi

        # Display progress
        task=$(echo "$task_status" | jq -r '.task')
        status=$(echo "$task" | jq -r '.status')
        total=$(echo "$status" | jq -r '.total // 0')
        created=$(echo "$status" | jq -r '.created // 0')
        updated=$(echo "$status" | jq -r '.updated // 0')

        log "Progress: ${created}/${total} documents created, ${updated} updated"
    done
}

# Function to reindex data
reindex_data() {
    local source_index=$1
    local dest_index=$2

    log "Starting reindex from ${source_index} to ${dest_index}..."

    response=$(curl -s -w "\n%{http_code}" \
        -u "${ES9_USER}:${ES9_PASSWORD}" \
        -X POST "${ES9_HOST}/_reindex?wait_for_completion=false" \
        -H 'Content-Type: application/json' \
        -d "{
            \"source\": {
                \"remote\": {
                    \"host\": \"${ES7_HOST_FROM_ES9}\",
                    \"username\": \"${ES7_USER}\",
                    \"password\": \"${ES7_PASSWORD}\"
                },
                \"index\": \"${source_index}\",
                \"size\": ${BATCH_SIZE}
            },
            \"dest\": {
                \"index\": \"${dest_index}\"
            }
        }")

    http_code=$(echo "$response" | tail -n 1)
    response_body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "200" ]; then
        # Get task ID
        task_id=$(echo "$response_body" | jq -r '.task')
        log "Reindex task created with ID: ${task_id}"

        # Wait for task completion
        wait_for_task "$task_id"
        return $?
    else
        log_error "Failed to start reindex: ${response_body}"
        return 1
    fi
}

# Function to process regular index
process_regular_index() {
    local index_name=$1
    local full_index_name="${INDEX_PREFIX}${index_name}"

    log "=========================================="
    log "Processing regular index: ${full_index_name}"
    log "=========================================="

    # Check if source index exists
    if ! check_index_exists "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name"; then
        log_error "Source index ${full_index_name} does not exist on ES7. Skipping..."
        return 1
    fi

    # Collect source stats
    collect_index_stats "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" "SOURCE"

    # Get settings and mappings from source
    log "Retrieving settings and mappings from source..."
    settings=$(get_index_settings "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" | \
        jq ".[\"${full_index_name}\"].settings.index |
            del(.creation_date, .uuid, .version, .provided_name, .lifecycle)")
    mappings=$(get_index_mappings "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" | \
        jq ".[\"${full_index_name}\"].mappings")

    # Check if index has an ILM policy
    ilm_policy_name=""
    if ilm_policy_name=$(get_index_ilm_policy "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name"); then
        log "Index has ILM policy: ${ilm_policy_name}"

        # Check if policy exists on ES9, if not, migrate it
        if ! check_ilm_policy_exists "$ES9_HOST" "$ES9_USER" "$ES9_PASSWORD" "$ilm_policy_name"; then
            log "ILM policy ${ilm_policy_name} does not exist on ES9, migrating it..."

            if ilm_policy_body=$(get_ilm_policy "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$ilm_policy_name"); then
                create_ilm_policy "$ilm_policy_name" "$ilm_policy_body"
            else
                log_error "Failed to retrieve ILM policy ${ilm_policy_name} from ES7"
                ilm_policy_name=""
            fi
        else
            log "ILM policy ${ilm_policy_name} already exists on ES9"
        fi
    else
        log "Index does not have an ILM policy"
    fi

    # Create index on destination if it doesn't exist
    if ! check_index_exists "$ES9_HOST" "$ES9_USER" "$ES9_PASSWORD" "$full_index_name"; then
        create_index "$full_index_name" "$settings" "$mappings" "$ilm_policy_name"
    else
        log "Index ${full_index_name} already exists on ES9. Skipping creation..."
    fi

    # Reindex data
    reindex_data "$full_index_name" "$full_index_name"
}

# Function to collect stats for an index
collect_index_stats() {
    local host=$1
    local user=$2
    local password=$3
    local index=$4
    local array_name=$5

    if check_index_exists "$host" "$user" "$password" "$index"; then
        local stats_json=$(get_index_stats "$host" "$user" "$password" "$index")
        local stats=$(extract_index_stats "$stats_json" "$index")

        if [ "$array_name" == "SOURCE" ]; then
            echo "${index}:${stats}" >> "$SOURCE_STATS_FILE"
        else
            echo "${index}:${stats}" >> "$DEST_STATS_FILE"
        fi
    fi
}

# Function to display comparison report
display_comparison_report() {
    log "=========================================="
    log "MIGRATION COMPARISON REPORT"
    log "=========================================="

    printf "%-40s | %15s | %15s | %15s | %10s\n" "Index" "Source Docs" "Dest Docs" "Difference" "Status"
    printf "%-40s-+-%15s-+-%15s-+-%15s-+-%10s\n" "----------------------------------------" "---------------" "---------------" "---------------" "----------"

    local total_issues=0

    # Read source stats file
    if [ -f "$SOURCE_STATS_FILE" ]; then
        while IFS=':' read -r index source_stats; do
            # Find corresponding dest stats
            dest_stats="0|0|0"
            if [ -f "$DEST_STATS_FILE" ]; then
                dest_line=$(grep "^${index}:" "$DEST_STATS_FILE" 2>/dev/null || echo "")
                if [ -n "$dest_line" ]; then
                    dest_stats="${dest_line#*:}"
                fi
            fi

            IFS='|' read -r source_docs source_deleted source_size <<< "$source_stats"
            IFS='|' read -r dest_docs dest_deleted dest_size <<< "$dest_stats"

            local diff=$((dest_docs - source_docs))
            local status="✓ OK"

            if [ "$dest_docs" -ne "$source_docs" ]; then
                status="✗ MISMATCH"
                ((total_issues++))
            fi

            printf "%-40s | %15s | %15s | %+15s | %10s\n" "$index" "$source_docs" "$dest_docs" "$diff" "$status"
        done < "$SOURCE_STATS_FILE"
    fi

    log "=========================================="
    if [ $total_issues -eq 0 ]; then
        log "✓ All indices migrated successfully!"
    else
        log_error "✗ ${total_issues} index(es) have mismatched document counts"
    fi
    log "=========================================="
}

# Function to process rollover index
process_rollover_index() {
    local index_name=$1
    local alias_name=$2

    local full_index_name="${INDEX_PREFIX}${index_name}"
    local full_alias_name="${INDEX_PREFIX}${alias_name}"

    log "=========================================="
    log "Processing rollover index: ${full_index_name}"
    log "Alias: ${full_alias_name}"
    log "=========================================="

    # Check if source index exists
    if ! check_index_exists "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name"; then
        log_error "Source index ${full_index_name} does not exist on ES7. Skipping..."
        return 1
    fi

    # Collect source stats
    collect_index_stats "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" "SOURCE"

    # Get settings, mappings and aliases from source
    log "Retrieving settings, mappings and aliases from source..."
    settings=$(get_index_settings "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" | \
        jq ".[\"${full_index_name}\"].settings.index |
            del(.creation_date, .uuid, .version, .provided_name, .lifecycle)")
    mappings=$(get_index_mappings "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name" | \
        jq ".[\"${full_index_name}\"].mappings")

    # Get alias information from source to check if it's a write index
    aliases_info=$(get_index_aliases "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name")
    is_write_index=$(echo "$aliases_info" | jq -r ".[\"${full_index_name}\"].aliases[\"${full_alias_name}\"].is_write_index // false")

    log "Index ${full_index_name} is_write_index: ${is_write_index}"

    # Check if index has an ILM policy
    ilm_policy_name=""
    if ilm_policy_name=$(get_index_ilm_policy "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_index_name"); then
        log "Index has ILM policy: ${ilm_policy_name}"

        # Check if policy exists on ES9, if not, migrate it
        if ! check_ilm_policy_exists "$ES9_HOST" "$ES9_USER" "$ES9_PASSWORD" "$ilm_policy_name"; then
            log "ILM policy ${ilm_policy_name} does not exist on ES9, migrating it..."

            if ilm_policy_body=$(get_ilm_policy "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$ilm_policy_name"); then
                create_ilm_policy "$ilm_policy_name" "$ilm_policy_body"
            else
                log_error "Failed to retrieve ILM policy ${ilm_policy_name} from ES7"
                ilm_policy_name=""
            fi
        else
            log "ILM policy ${ilm_policy_name} already exists on ES9"
        fi
    else
        log "Index does not have an ILM policy"
    fi

    # Create index on destination if it doesn't exist
    if ! check_index_exists "$ES9_HOST" "$ES9_USER" "$ES9_PASSWORD" "$full_index_name"; then
        create_index "$full_index_name" "$settings" "$mappings" "$ilm_policy_name"
    else
        log "Index ${full_index_name} already exists on ES9. Skipping creation..."
    fi

    # Create alias with is_write_index parameter
    create_alias "$full_index_name" "$full_alias_name" "$is_write_index"

    # Reindex data
    reindex_data "$full_index_name" "$full_index_name"
}

##############################################################################
# MAIN EXECUTION
##############################################################################

main() {
    log "=========================================="
    log "Elasticsearch Reindex Script Started"
    log "=========================================="
    log "Configuration:"
    log "  Index Prefix: ${INDEX_PREFIX}"
    log "  ES7 Host: ${ES7_HOST}"
    log "  ES7 Host From ES9: ${ES7_HOST_FROM_ES9}"
    log "  ES9 Host: ${ES9_HOST}"
    log "  Batch Size: ${BATCH_SIZE}"
    log "=========================================="

    # Check if jq is installed
    if ! command -v jq &> /dev/null; then
        log_error "jq is required but not installed. Please install jq."
        exit 1
    fi

    # Process regular indices
    log "Processing regular indices..."
    for index_name in "${REGULAR_INDICES[@]}"; do
        process_regular_index "$index_name"
        echo ""
    done

    # Process rollover indices
    log "Processing rollover indices..."
    for pattern_config in "${ROLLOVER_PATTERNS[@]}"; do
        IFS=':' read -r pattern alias_name <<< "$pattern_config"

        # Add prefix to pattern
        full_pattern="${INDEX_PREFIX}${pattern}"

        log "Discovering indices matching pattern: ${full_pattern}"
        discovered_indices=$(discover_indices "$ES7_HOST" "$ES7_USER" "$ES7_PASSWORD" "$full_pattern")

        if [ -z "$discovered_indices" ]; then
            log "No indices found matching pattern: ${full_pattern}"
            continue
        fi

        # Process each discovered index
        while IFS= read -r full_index_name; do
            # Remove prefix to get the index name without prefix
            index_name="${full_index_name#$INDEX_PREFIX}"
            process_rollover_index "$index_name" "$alias_name"
            echo ""
        done <<< "$discovered_indices"
    done

    log "=========================================="
    log "Collecting destination statistics..."
    log "=========================================="

    # Collect destination stats for all migrated indices
    if [ -f "$SOURCE_STATS_FILE" ]; then
        while IFS=':' read -r index stats; do
            collect_index_stats "$ES9_HOST" "$ES9_USER" "$ES9_PASSWORD" "$index" "DEST"
        done < "$SOURCE_STATS_FILE"
    fi

    # Display comparison report
    display_comparison_report

    log "=========================================="
    log "Elasticsearch Reindex Script Completed"
    log "=========================================="
}

# Run main function
main
