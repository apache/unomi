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
_setup_opensearch() {
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

    # Clear Elasticsearch environment variables first. This must succeed: leaving both
    # engines' variables set is exactly the conflicting state build.sh's
    # check_integration_test_env_vars is designed to detect and reject.
    if ! clear_opposite "${SCRIPT_DIR}" "elasticsearch"; then
        echo "ERROR: Failed to clear Elasticsearch variables" >&2
        return 1
    fi

    # Load required passwords from .env.local (or existing environment).
    # Engine password is OpenSearch-only so we do not pull Elasticsearch's password.
    if ! require_password "${SCRIPT_DIR}" "UNOMI_OPENSEARCH_PASSWORD"; then
        return 1
    fi
    if ! require_password "${SCRIPT_DIR}" "UNOMI_ROOT_PASSWORD"; then
        return 1
    fi
    if ! require_password "${SCRIPT_DIR}" "UNOMI_HEALTHCHECK_PASSWORD"; then
        return 1
    fi

    # Set the distribution to use OpenSearch
    export UNOMI_DISTRIBUTION=unomi-distribution-opensearch

    echo "OpenSearch 3 environment variables configured."
    echo "  Distribution: ${UNOMI_DISTRIBUTION}"
    echo "  OpenSearch password: (set from .env.local or environment)"
    echo "  Root password: (set from .env.local or environment)"
    echo "  Healthcheck password: (set from .env.local or environment)"
    echo "  Note: Using Unomi defaults for other OpenSearch settings (cluster, addresses, username, SSL)"
    
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
    _setup_opensearch
    _SETUP_EXIT_CODE=$?
    # Clean up temporary variables
    unset _IS_SOURCED _SETUP_EXIT_CODE
    # Don't do anything with the exit code - just let it be
    # The function already printed error messages if it failed
else
    # Script is being executed directly - safe to use exit
    _setup_opensearch || exit 1
fi

