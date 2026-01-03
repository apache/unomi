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

# Shell Utility Functions
# This file contains reusable shell functions that can be used by other scripts
# Part of the Apache Unomi Manual Generator toolkit

# Color and formatting constants
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[0;37m'
BOLD='\033[1m'
DIM='\033[2m'
UNDERLINE='\033[4m'
BLINK='\033[5m'
REVERSE='\033[7m'
NC='\033[0m'

# Logging functions
log_info() {
    echo -e "${BLUE}ℹ${NC} $1"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [INFO] $1" >> "$MAIN_LOG"
    fi
}

log_success() {
    echo -e "${GREEN}✅${NC} $1"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [SUCCESS] $1" >> "$MAIN_LOG"
    fi
}

log_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [WARNING] $1" >> "$MAIN_LOG"
    fi
}

log_error() {
    echo -e "${RED}❌${NC} $1"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [ERROR] $1" >> "$MAIN_LOG"
    fi
}

log_step() {
    echo -e "${PURPLE}🔸${NC} ${BOLD}$1${NC}"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [STEP] $1" >> "$MAIN_LOG"
    fi
}

log_simulate() {
    echo -e "${CYAN}🎭${NC} $1"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [SIMULATE] $1" >> "$MAIN_LOG"
    fi
}

log_command() {
    echo -e "${YELLOW}\$${NC} ${DIM}$1${NC}"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [COMMAND] $1" >> "$MAIN_LOG"
    fi
}

log_debug() {
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [DEBUG] $1" >> "$MAIN_LOG"
    fi
}

# Progress animation functions
animate_loading() {
    local message="$1"
    local duration="${2:-3}"
    local frames="⣾⣽⣻⢿⡿⣟⣯⣷"
    local frame_count=${#frames}
    local delay=0.1

    # Log the start of the loading animation
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [PROGRESS] Starting: $message" >> "$MAIN_LOG"
    fi

    if ! command -v bc >/dev/null 2>&1; then
        echo -e "${CYAN}⏳${NC} $message"
        sleep "$duration"
        echo -e "\r${GREEN}✅${NC} $message"
        if [ -n "${MAIN_LOG:-}" ]; then
            echo "$(date '+%Y-%m-%d %H:%M:%S') [PROGRESS] Completed: $message" >> "$MAIN_LOG"
        fi
        return
    fi

    local total_iterations=$(echo "$duration / $delay" | bc -l)
    total_iterations=${total_iterations%.*}

    for (( i=0; i<total_iterations; i++ )); do
        local frame_index=$((i % frame_count))
        local frame=${frames:$frame_index:1}
        echo -ne "\r${CYAN}$frame${NC} $message"
        sleep "$delay"
    done

    echo -e "\r${GREEN}✅${NC} $message"
    if [ -n "${MAIN_LOG:-}" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [PROGRESS] Completed: $message" >> "$MAIN_LOG"
    fi
}

# Rainbow text animation
rainbow_text() {
    local text="$1"
    local colors=("$RED" "$YELLOW" "$GREEN" "$CYAN" "$BLUE" "$PURPLE")
    local color_count=${#colors[@]}

    for (( i=0; i<${#text}; i++ )); do
        local char="${text:$i:1}"
        local color_index=$((i % color_count))
        echo -ne "${colors[$color_index]}$char"
    done
    echo -e "${NC}"
}

# Typewriter effect animation with ANSI code support
typewriter_effect() {
    local text="$1"
    local delay="${2:-0.01}"

    # Use echo -e to interpret escape sequences, then process character by character
    local processed_text=$(echo -e "$text")

    local i=0
    while [ $i -lt ${#processed_text} ]; do
        # Check if we're at the start of an ANSI escape sequence (\033[)
        if [[ "${processed_text:$i:1}" == $'\033' ]]; then
            # Find the end of the ANSI sequence (ends with a letter, usually 'm')
            local ansi_seq=""
            local j=$i
            while [ $j -lt ${#processed_text} ]; do
                ansi_seq+="${processed_text:$j:1}"
                if [[ "${processed_text:$j:1}" =~ [a-zA-Z] ]]; then
                    break
                fi
                ((j++))
            done
            # Print the entire ANSI sequence at once (no delay)
            echo -n "$ansi_seq"
            i=$((j + 1))
        else
            # Regular character - print with delay
            echo -n "${processed_text:$i:1}"
            sleep "$delay"
            ((i++))
        fi
    done
    echo -e "${NC}"
}

# System requirements checking
check_command() {
    local cmd="$1"
    local description="$2"
    local required="${3:-true}"

    if command -v "$cmd" >/dev/null 2>&1; then
        local version=""
        case "$cmd" in
            "java")
                version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1-2)
                ;;
            "mvn")
                version=$(mvn --version 2>/dev/null | head -n1 | cut -d' ' -f3)
                ;;
            "git")
                version=$(git --version | cut -d' ' -f3)
                ;;
            "svn")
                version=$(svn --version --quiet 2>/dev/null)
                ;;
            *)
                version="found"
                ;;
        esac
        log_success "$description $version found"
        return 0
    else
        if [ "$required" = "true" ]; then
            log_error "$description not found"
            return 1
        else
            log_info "$description not found (not required for local mode)"
            return 0
        fi
    fi
}

# Git repository validation
check_git_repository() {
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        log_error "Not in a git repository"
        return 1
    fi
    log_success "Git repository detected"
    return 0
}

# Disk space checking
check_disk_space() {
    local required_mb="${1:-100}"

    if command -v df >/dev/null 2>&1; then
        local available_kb
        available_kb=$(df . | tail -1 | awk '{print $4}')
        local available_mb=$((available_kb / 1024))

        if [ "$available_mb" -lt "$required_mb" ]; then
            log_warning "Low disk space: ${available_mb}MB available, ${required_mb}MB recommended"
            return 1
        else
            log_success "Sufficient disk space available"
            return 0
        fi
    else
        log_info "Cannot check disk space (df command not available)"
        return 0
    fi
}

# Memory checking
check_memory() {
    local required_mb="${1:-512}"

    if command -v free >/dev/null 2>&1; then
        # Linux
        local available_mb
        available_mb=$(free -m | awk '/^Mem:/{print $7}')
        if [ -z "$available_mb" ]; then
            available_mb=$(free -m | awk '/^Mem:/{print $4}')
        fi
    elif command -v vm_stat >/dev/null 2>&1; then
        # macOS
        local page_size=4096
        local free_pages
        free_pages=$(vm_stat | grep "Pages free" | awk '{print $3}' | sed 's/\.//')
        local available_mb=$((free_pages * page_size / 1024 / 1024))
    else
        log_info "Cannot check memory (free/vm_stat command not available)"
        return 0
    fi

    if [ -n "$available_mb" ] && [ "$available_mb" -lt "$required_mb" ]; then
        log_warning "Low available memory - at least ${required_mb}MB recommended"
        return 1
    else
        log_success "Sufficient memory available"
        return 0
    fi
}

# Directory validation
check_directory_writable() {
    local dir="${1:-.}"

    if [ -w "$dir" ]; then
        log_success "Current directory is writable"
        return 0
    else
        log_error "Current directory is not writable"
        return 1
    fi
}

# Logging helper functions for command execution
log_to_file() {
    local log_file="$1"
    local command="$2"
    shift 2

    echo "===============================================================================" >> "$log_file"
    echo "$(date '+%Y-%m-%d %H:%M:%S') - Executing: $command" >> "$log_file"
    echo "Working directory: $(pwd)" >> "$log_file"
    echo "===============================================================================" >> "$log_file"
}

run_git_command() {
    local cmd="$*"
    log_to_file "${MAIN_LOG:-/dev/null}" "$cmd"
    echo "Command: $cmd" >> "${MAIN_LOG:-/dev/null}"
    echo "Output:" >> "${MAIN_LOG:-/dev/null}"

    if eval "$cmd" >> "${MAIN_LOG:-/dev/null}" 2>&1; then
        echo "Status: SUCCESS" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return 0
    else
        local exit_code=$?
        echo "Status: FAILED (exit code: $exit_code)" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return $exit_code
    fi
}

run_maven_command() {
    local cmd="$*"
    log_to_file "${MAIN_LOG:-/dev/null}" "$cmd"
    echo "Command: $cmd" >> "${MAIN_LOG:-/dev/null}"
    echo "Output:" >> "${MAIN_LOG:-/dev/null}"

    if eval "$cmd" >> "${MAIN_LOG:-/dev/null}" 2>&1; then
        echo "Status: SUCCESS" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return 0
    else
        local exit_code=$?
        echo "Status: FAILED (exit code: $exit_code)" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return $exit_code
    fi
}

run_svn_command() {
    local cmd="$*"
    log_to_file "${MAIN_LOG:-/dev/null}" "$cmd"
    echo "Command: $cmd" >> "${MAIN_LOG:-/dev/null}"
    echo "Output:" >> "${MAIN_LOG:-/dev/null}"

    if eval "$cmd" >> "${MAIN_LOG:-/dev/null}" 2>&1; then
        echo "Status: SUCCESS" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return 0
    else
        local exit_code=$?
        echo "Status: FAILED (exit code: $exit_code)" >> "${MAIN_LOG:-/dev/null}"
        echo "" >> "${MAIN_LOG:-/dev/null}"
        return $exit_code
    fi
}

# Export functions for use in other scripts
export -f log_info log_success log_warning log_error log_step log_simulate log_command log_debug
export -f animate_loading rainbow_text typewriter_effect
export -f check_command check_git_repository check_disk_space check_memory check_directory_writable
export -f log_to_file run_git_command run_maven_command run_svn_command
