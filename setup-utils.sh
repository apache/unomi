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

# Check if script is being sourced or executed directly
# Both setup and clear scripts must be sourced to modify the current shell's environment
# Usage: check_sourced CALLING_SCRIPT_PATH
# Returns: 0 if sourced, 1 if executed directly (and shows warning)
# Note: When called from a function in a sourced script, use 'return' not 'exit' to avoid closing the shell
check_sourced() {
    local calling_script="$1"
    local is_sourced=false
    
    # Support both bash and zsh
    if [ -n "${ZSH_VERSION}" ]; then
        # In zsh, use funcstack - it has entries when sourced, empty when executed
        # This is the most reliable method and works even when nested or inside functions
        if [ ${#funcstack[@]} -gt 0 ]; then
            is_sourced=true
        fi
    else
        # BASH_SOURCE[1] is the script that sourced this utils script (the calling script)
        # When the calling script is sourced: $0 is the parent shell (starts with "-" like "-zsh" or "-bash")
        # When the calling script is executed: $0 is the script path itself
        # So if BASH_SOURCE[1] != $0, the calling script was sourced
        if [ -n "${BASH_SOURCE[1]}" ] && [ "${BASH_SOURCE[1]}" != "${0}" ]; then
            is_sourced=true
        fi
    fi
    
    # If script was executed directly, show warning and return error (NEVER exit here)
    if [ "${is_sourced}" = false ]; then
        echo "WARNING: This script must be sourced to set environment variables in your shell." >&2
        echo "" >&2
        echo "Usage:" >&2
        echo "  source ${calling_script}" >&2
        echo "  # or" >&2
        echo "  . ${calling_script}" >&2
        echo "" >&2
        echo "Note: Both setup and clear scripts must be sourced because they modify" >&2
        echo "environment variables (export/unset) which only work in the current shell." >&2
        return 1
    fi
    return 0
}

# Load .env.local file if it exists
# Usage: load_env_local SCRIPT_DIR
load_env_local() {
    local script_dir="$1"
    local env_local="${script_dir}/.env.local"
    
    if [ -f "${env_local}" ]; then
        echo "Loading passwords from .env.local"
        source "${env_local}"
    fi
}

# Check if password environment variable is set
# Usage: check_password PASSWORD_VAR_NAME
# Returns: 0 if password is set, 1 if not set
# Note: When called from a sourced script, use 'return' not 'exit' to avoid closing the shell
check_password() {
    local password_var="$1"
    # Support both bash and zsh indirect variable expansion
    if [ -n "${ZSH_VERSION}" ]; then
        local password_value="${(P)password_var}"
    else
        local password_value="${!password_var}"
    fi
    
    if [ -z "${password_value}" ]; then
        echo "ERROR: ${password_var} is not set."
        echo ""
        echo "To set the password, create a .env.local file in the project root with:"
        echo "  export ${password_var}=your_password_here"
        echo ""
        echo "Example:"
        echo "  echo 'export ${password_var}=your_password' > .env.local"
        echo ""
        echo "Note: .env.local is gitignored and should not be committed."
        return 1
    fi
    return 0
}

# Clear the opposite search engine's environment variables
# Usage: clear_opposite SCRIPT_DIR OPPOSITE_TYPE
# OPPOSITE_TYPE should be "opensearch" or "elasticsearch"
clear_opposite() {
    local script_dir="$1"
    local opposite_type="$2"
    local clear_script="${script_dir}/clear-${opposite_type}.sh"
    
    if [ -f "${clear_script}" ]; then
        source "${clear_script}"
    else
        echo "Warning: clear-${opposite_type}.sh not found, skipping ${opposite_type} cleanup"
    fi
}

