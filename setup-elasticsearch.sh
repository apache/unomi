#!/usr/bin/env bash
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

# Main function - wraps all logic so we can use 'return' when sourced
_setup_elasticsearch() {
    local exit_code=0
    
    # Get the directory where this script is located
    # Support both bash and zsh
    if [ -n "${ZSH_VERSION}" ]; then
        SCRIPT_DIR="$(cd "$(dirname "${(%):-%x}")" && pwd)"
    else
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    fi

    # Source shared utilities
    if [ -f "${SCRIPT_DIR}/setup-utils.sh" ]; then
        source "${SCRIPT_DIR}/setup-utils.sh" || { echo "ERROR: Failed to source setup-utils.sh" >&2; return 1; }
    else
        echo "ERROR: setup-utils.sh not found in ${SCRIPT_DIR}" >&2
        return 1
    fi

    # Check if script is being sourced
    # Support both bash and zsh
    if [ -n "${ZSH_VERSION}" ]; then
        if ! check_sourced "${(%):-%x}"; then
            return 1
        fi
    else
        if ! check_sourced "${BASH_SOURCE[0]}"; then
            return 1
        fi
    fi

    # Clear OpenSearch environment variables first
    clear_opposite "${SCRIPT_DIR}" "opensearch" || { echo "WARNING: Failed to clear OpenSearch variables" >&2; }

    # Load .env.local file if it exists
    load_env_local "${SCRIPT_DIR}"

    # Check if password is set
    if ! check_password "UNOMI_ELASTICSEARCH_PASSWORD"; then
        return 1
    fi

    # Set Elasticsearch 9 environment variables
    # Note: Unomi defaults are not appropriate for ES 9, so we override multiple settings
    export UNOMI_ELASTICSEARCH_CLUSTERNAME=docker-cluster
    export UNOMI_ELASTICSEARCH_USERNAME=elastic
    export UNOMI_ELASTICSEARCH_SSL_ENABLE=true
    export UNOMI_ELASTICSEARCH_SSL_TRUST_ALL_CERTIFICATES=true
    # Password is already set from .env.local or environment

    echo "Elasticsearch 9 environment variables configured."
    echo "  Cluster: ${UNOMI_ELASTICSEARCH_CLUSTERNAME} (overridden from default: contextElasticSearch)"
    echo "  Username: ${UNOMI_ELASTICSEARCH_USERNAME} (overridden from default: empty)"
    echo "  SSL Enabled: ${UNOMI_ELASTICSEARCH_SSL_ENABLE} (overridden from default: false)"
    echo "  Trust All Certificates: ${UNOMI_ELASTICSEARCH_SSL_TRUST_ALL_CERTIFICATES} (overridden from default: false)"
    
    return 0
}

# Determine if script is being sourced
# This MUST be done at the top level before any function calls
_IS_SOURCED=false
if [ -n "${ZSH_VERSION}" ]; then
    # In zsh, use funcstack - it has entries when sourced, empty when executed
    # This is the most reliable method and works even when nested
    if [ ${#funcstack[@]} -gt 0 ]; then
        _IS_SOURCED=true
    fi
else
    # In bash, check BASH_SOURCE
    # When sourced: BASH_SOURCE[0] != $0
    # When executed: BASH_SOURCE[0] == $0
    if [ -n "${BASH_SOURCE[0]}" ] && [ "${BASH_SOURCE[0]}" != "${0}" ]; then
        _IS_SOURCED=true
    fi
fi

# Call the main function
# CRITICAL: NEVER use exit when sourced - it will close the shell
if [ "${_IS_SOURCED}" = "true" ]; then
    # Script is being sourced - call function and handle errors gracefully
    # Do NOT use || exit here - it will close the shell!
    # Just call the function - if it fails, it will return 1 but won't exit
    _setup_elasticsearch
    _SETUP_EXIT_CODE=$?
    # Clean up temporary variables
    unset _IS_SOURCED _SETUP_EXIT_CODE
    # Don't do anything with the exit code - just let it be
    # The function already printed error messages if it failed
else
    # Script is being executed directly - safe to use exit
    _setup_elasticsearch || exit 1
fi
