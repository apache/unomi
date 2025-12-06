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

# Detect if script is being sourced or executed directly
# Both setup and clear scripts must be sourced to modify the current shell's environment
_IS_SOURCED=false
if [ -n "${ZSH_VERSION}" ]; then
    # In zsh, use funcstack - it has entries when sourced, empty when executed
    # This works even when nested (sourced from another sourced script)
    if [ ${#funcstack[@]} -gt 0 ]; then
        _IS_SOURCED=true
    fi
else
    # In bash, check BASH_SOURCE
    if [ -n "${BASH_SOURCE[0]}" ] && [ "${BASH_SOURCE[0]}" != "${0}" ]; then
        _IS_SOURCED=true
    fi
fi

if [ "${_IS_SOURCED}" = false ]; then
    echo "WARNING: This script must be sourced to clear environment variables in your shell." >&2
    echo "" >&2
    echo "Usage:" >&2
    echo "  source ${0}" >&2
    echo "  # or" >&2
    echo "  . ${0}" >&2
    echo "" >&2
    echo "Note: Both setup and clear scripts must be sourced because they modify" >&2
    echo "environment variables (export/unset) which only work in the current shell." >&2
    # Only exit if executed directly, not if sourced
    exit 1
fi

# Clear OpenSearch environment variables
unset UNOMI_OPENSEARCH_CLUSTERNAME
unset UNOMI_OPENSEARCH_ADDRESSES
unset UNOMI_OPENSEARCH_USERNAME
unset UNOMI_OPENSEARCH_PASSWORD
unset UNOMI_OPENSEARCH_SSL_ENABLE
unset UNOMI_OPENSEARCH_SSL_TRUST_ALL_CERTIFICATES

echo "OpenSearch environment variables cleared."

