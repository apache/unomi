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

set -e  # Exit on error
trap 'handle_error $? $LINENO $BASH_LINENO "$BASH_COMMAND" $(printf "::%s" ${FUNCNAME[@]:-})' ERR

# Error handling function
handle_error() {
    local exit_code=$1
    local line_no=$2
    local bash_lineno=$3
    local last_command=$4
    local func_trace=$5

    cat << "EOF"
     _____ ____  ____   ___  ____
    | ____|  _ \|  _ \ / _ \|  _ \
    |  _| | |_) | |_) | | | | |_) |
    | |___|  _ <|  _ <| |_| |  _ <
    |_____|_| \_\_| \_\\___/|_| \_\

EOF
    echo "Error occurred in:"
    echo "  Command: $last_command"
    echo "  Line: $line_no"
    echo "  Exit code: $exit_code"
    if [ ! -z "$func_trace" ]; then
        echo "  Function trace: $func_trace"
    fi
    exit $exit_code
}

# Function to detect color support
setup_colors() {
    # Only use colors if connected to a terminal
    if [ -t 1 ]; then
        # Check if NO_COLOR is set (https://no-color.org/)
        if [ -z "${NO_COLOR+x}" ]; then
            # Check if terminal supports colors
            if [ -n "$TERM" ] && [ "$TERM" != "dumb" ]; then
                # Check if tput is available
                if command -v tput > /dev/null && tput setaf 1 >&/dev/null; then
                    RED='\033[0;31m'
                    GREEN='\033[0;32m'
                    YELLOW='\033[1;33m'
                    BLUE='\033[0;34m'
                    MAGENTA='\033[0;35m'
                    CYAN='\033[0;36m'
                    GRAY='\033[0;90m'
                    BOLD='\033[1m'
                    NC='\033[0m' # No Color

                    # Unicode symbols (only if terminal likely supports UTF-8)
                    if [ "$TERM" != "linux" ] && [ -n "$LANG" ] && [ "$LANG" != "C" ]; then
                        CHECK_MARK="✔"
                        CROSS_MARK="✘"
                        ARROW="➜"
                        WARNING="⚠"
                        INFO="ℹ"
                    else
                        CHECK_MARK="(+)"
                        CROSS_MARK="(x)"
                        ARROW="(>)"
                        WARNING="(!)"
                        INFO="(i)"
                    fi

                    HAS_COLORS=1
                fi
            fi
        fi
    fi

    # If colors are not supported or disabled, use plain text without escape sequences
    if [ -z "${HAS_COLORS+x}" ]; then
        RED=''
        GREEN=''
        YELLOW=''
        BLUE=''
        MAGENTA=''
        CYAN=''
        GRAY=''
        BOLD=''
        NC=''

        # Use ASCII alternatives without escape sequences
        CHECK_MARK="(+)"
        CROSS_MARK="(x)"
        ARROW="(>)"
        WARNING="(!)"
        INFO="(i)"

        HAS_COLORS=0
    fi
}

# Initialize colors early
setup_colors

# Function to print section headers
print_section() {
    local text="$1"
    local text_length=${#text}
    local total_width=80  # Standard terminal width
    local padding_length=$(( (total_width - text_length - 4) / 2 ))  # -4 for the borders and spaces
    local left_padding=""
    local right_padding=""

    # Create padding strings of spaces for the text line
    for ((i=0; i<padding_length; i++)); do
        left_padding+=" "
        right_padding+=" "
    done

    # Adjust right padding for odd lengths
    if [ $(( (total_width - text_length) % 2 )) -eq 1 ]; then
        right_padding+=" "
    fi

    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "\n${BLUE}╔════════════════════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${BLUE}║${NC}${left_padding}${BOLD}${text}${NC}${right_padding}${BLUE}║${NC}"
        echo -e "${BLUE}╚════════════════════════════════════════════════════════════════════════════╝${NC}"
    else
        echo -e "\n+------------------------------------------------------------------------------+"
        echo -e "| ${left_padding}${text}${right_padding} |"
        echo "+------------------------------------------------------------------------------+"
    fi
}

# Function to print status messages
print_status() {
    local status=$1
    local message=$2
    
    if [ "$HAS_COLORS" -eq 1 ]; then
        case $status in
            "success")
                echo -e " ${GREEN}${CHECK_MARK}${NC} ${GREEN}${message}${NC}"
                ;;
            "error")
                echo -e " ${RED}${CROSS_MARK}${NC} ${RED}${message}${NC}"
                ;;
            "warning")
                echo -e " ${YELLOW}${WARNING}${NC} ${YELLOW}${message}${NC}"
                ;;
            "info")
                echo -e " ${CYAN}${INFO}${NC} ${CYAN}${message}${NC}"
                ;;
            *)
                echo -e " ${BLUE}${ARROW}${NC} ${message}"
                ;;
        esac
    else
        # No colors - use plain text without escape sequences
        local symbol
        case $status in
            "success")
                symbol="${CHECK_MARK}"
                ;;
            "error")
                symbol="${CROSS_MARK}"
                ;;
            "warning")
                symbol="${WARNING}"
                ;;
            "info")
                symbol="${INFO}"
                ;;
            *)
                symbol="${ARROW}"
                ;;
        esac
        echo " ${symbol} ${message}"
    fi
}

# Enhanced progress tracking
print_progress() {
    local current=$1
    local total=$2
    local message=$3
    local percentage=$((current * 100 / total))

    if [ "$HAS_COLORS" -eq 1 ]; then
        local filled=$((percentage / 2))
        local empty=$((50 - filled))

        local progress="["
        for ((i=0; i<filled; i++)); do progress+="█"; done
        for ((i=0; i<empty; i++)); do progress+="░"; done
        progress+="]"

        echo -e "\r${CYAN}${progress}${NC} ${percentage}% ${GRAY}${message}${NC}"
    else
        local filled=$((percentage / 4))
        local empty=$((25 - filled))

        local progress="["
        for ((i=0; i<filled; i++)); do progress+="#"; done
        for ((i=0; i<empty; i++)); do progress+="-"; done
        progress+="]"

        echo -e "\r${progress} ${percentage}% ${message}"
    fi
}

# Add the new section header
print_section "Apache Unomi Build Script"

# Default values
SKIP_TESTS=false
RUN_INTEGRATION_TESTS=false
DEPLOY=false
DEBUG=false
USE_MAVEN_CACHE=true
PURGE_MAVEN_CACHE=false
MAVEN_DEBUG=false
MAVEN_OFFLINE=false
KARAF_DEBUG_PORT=5005
KARAF_DEBUG_SUSPEND=n
USE_OPENSEARCH=false
NO_KARAF=false
AUTO_START=""

# Enhanced usage function with color support
usage() {
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${CYAN}"
        cat << "EOF"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
EOF
        echo -e "${NC}"

        echo -e "${BOLD}Usage:${NC} $0 [options]"
        echo
        echo -e "${BOLD}Options:${NC}"
        echo -e "  ${CYAN}-h, --help${NC}                 Show this help message"
        echo -e "  ${CYAN}-s, --skip-tests${NC}           Skip all tests"
        echo -e "  ${CYAN}-i, --integration-tests${NC}    Run integration tests"
        echo -e "  ${CYAN}-d, --deploy${NC}               Deploy after build"
        echo -e "  ${CYAN}-X, --maven-debug${NC}         Enable Maven debug output"
        echo -e "  ${CYAN}-o, --offline${NC}             Run Maven in offline mode"
        echo -e "  ${CYAN}--debug${NC}                    Run Karaf in debug mode"
        echo -e "  ${CYAN}--debug-port PORT${NC}          Set debug port (default: 5005)"
        echo -e "  ${CYAN}--debug-suspend${NC}           Suspend JVM until debugger connects"
        echo -e "  ${CYAN}--no-maven-cache${NC}           Disable Maven build cache"
        echo -e "  ${CYAN}--purge-maven-cache${NC}        Purge local Maven cache before building"
        echo -e "  ${CYAN}--karaf-home PATH${NC}          Set Karaf home directory for deployment"
        echo -e "  ${CYAN}--use-opensearch${NC}          Use OpenSearch instead of ElasticSearch"
        echo -e "  ${CYAN}--no-karaf${NC}               Build without starting Karaf"
        echo -e "  ${CYAN}--auto-start ENGINE${NC}      Auto-start with specified engine"
    else
        cat << "EOF"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
EOF
        echo
        echo "Usage: $0 [options]"
        echo
        echo "Options:"
        echo "  -h, --help                 Show this help message"
        echo "  -s, --skip-tests           Skip all tests"
        echo "  -i, --integration-tests    Run integration tests"
        echo "  -d, --deploy               Deploy after build"
        echo "  -X, --maven-debug         Enable Maven debug output"
        echo "  -o, --offline             Run Maven in offline mode"
        echo "  --debug                    Run Karaf in debug mode"
        echo "  --debug-port PORT          Set debug port (default: 5005)"
        echo "  --debug-suspend           Suspend JVM until debugger connects"
        echo "  --no-maven-cache           Disable Maven build cache"
        echo "  --purge-maven-cache        Purge local Maven cache before building"
        echo "  --karaf-home PATH          Set Karaf home directory for deployment"
        echo "  --use-opensearch          Use OpenSearch instead of ElasticSearch"
        echo "  --no-karaf               Build without starting Karaf"
        echo "  --auto-start ENGINE      Auto-start with specified engine"
    fi

    echo
    echo "Examples:"
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "  ${GRAY}# Build with integration tests using OpenSearch${NC}"
        echo -e "  ${GRAY}$0 --integration-tests --use-opensearch${NC}"
        echo -e
        echo -e "  ${GRAY}# Build in debug mode${NC}"
        echo -e "  ${GRAY}$0 --debug --debug-port 5006 --debug-suspend${NC}"
        echo -e
        echo -e "  ${GRAY}# Deploy to specific Karaf instance${NC}"
        echo -e "  ${GRAY}$0 --deploy --karaf-home ~/apache-karaf${NC}"
        echo -e
        echo -e "  ${GRAY}# Build without Karaf and auto-start OpenSearch${NC}"
        echo -e "  ${GRAY}$0 --no-karaf --auto-start opensearch${NC}"
        echo -e
        echo -e "  ${GRAY}# Run without colored output${NC}"
        echo -e "  ${GRAY}NO_COLOR=1 $0${NC}"
        echo -e "  ${GRAY}# or ${NC}"
        echo -e "  ${GRAY}export NO_COLOR=1${NC}"
        echo -e "  ${GRAY}$0${NC}"
    else
        echo "  # Build with integration tests using OpenSearch"
        echo "  $0 --integration-tests --use-opensearch"
        echo
        echo "  # Build in debug mode"
        echo "  $0 --debug --debug-port 5006 --debug-suspend"
        echo
        echo "  # Deploy to specific Karaf instance"
        echo "  $0 --deploy --karaf-home ~/apache-karaf"
        echo
        echo "  # Build without Karaf and auto-start OpenSearch"
        echo "  $0 --no-karaf --auto-start opensearch"
        echo
        echo "  # Run without colored output"
        echo "  NO_COLOR=1 $0"
        echo "  # or"
        echo "  export NO_COLOR=1"
        echo "  $0"
    fi
    exit 1
}

# Parse command line arguments
while [ "$1" != "" ]; do
    case $1 in
        -h | --help)
            usage
            ;;
        -X | --maven-debug)
            MAVEN_DEBUG=true
            ;;
        -o | --offline)
            MAVEN_OFFLINE=true
            ;;
        -s | --skip-tests)
            SKIP_TESTS=true
            ;;
        -i | --integration-tests)
            RUN_INTEGRATION_TESTS=true
            ;;
        -d | --deploy)
            DEPLOY=true
            ;;
        --debug)
            DEBUG=true
            ;;
        --debug-port)
            shift
            KARAF_DEBUG_PORT=$1
            ;;
        --debug-suspend)
            KARAF_DEBUG_SUSPEND=y
            ;;
        --no-maven-cache)
            USE_MAVEN_CACHE=false
            ;;
        --purge-maven-cache)
            PURGE_MAVEN_CACHE=true
            ;;
        --karaf-home)
            shift
            CONTEXT_SERVER_KARAF_HOME=$1
            ;;
        --use-opensearch)
            USE_OPENSEARCH=true
            ;;
        --no-karaf)
            NO_KARAF=true
            ;;
        --auto-start)
            shift
            if [[ "$1" != "elasticsearch" && "$1" != "opensearch" ]]; then
                echo "Error: --auto-start must be either 'elasticsearch' or 'opensearch'"
                exit 1
            fi
            AUTO_START="$1"
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
    shift
done

# Set environment
DIRNAME=`dirname "$0"`
PROGNAME=`basename "$0"`
if [ -f "$DIRNAME/setenv.sh" ]; then
    . "$DIRNAME/setenv.sh"
fi

# Purge Maven cache if requested
if [ "$PURGE_MAVEN_CACHE" = true ]; then
    echo "Purging Maven cache..."
    rm -rf ~/.m2/build-cache ~/.m2/dependency-cache ~/.m2/dependency-cache_v2
    echo "Maven cache purged."
fi

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to check required tools
check_requirements() {
    local missing_tools=()

    for tool in mvn java tar gzip; do
        if ! command_exists "$tool"; then
            missing_tools+=("$tool")
        fi
    done

    if [ ${#missing_tools[@]} -ne 0 ]; then
        echo "Error: Required tools are missing:"
        printf '  - %s\n' "${missing_tools[@]}"
        exit 1
    fi
}

# Check requirements early
check_requirements

# Construct Maven command
MVN_CMD="mvn"
MVN_OPTS=""

# Add Maven debug option
if [ "$MAVEN_DEBUG" = true ]; then
    MVN_OPTS="$MVN_OPTS -X"
    echo "Maven debug output enabled"
fi

# Add Maven offline option
if [ "$MAVEN_OFFLINE" = true ]; then
    MVN_OPTS="$MVN_OPTS -o"
    echo "Maven offline mode enabled"

    # Warn if purge cache is enabled with offline mode
    if [ "$PURGE_MAVEN_CACHE" = true ]; then
        echo "Warning: Purging Maven cache while in offline mode may cause build failures"
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Add Maven cache option
if [ "$USE_MAVEN_CACHE" = false ]; then
    MVN_OPTS="$MVN_OPTS -Dmaven.build.cache.enabled=false"
fi

# Verify Maven settings
if [ ! -f ~/.m2/settings.xml ]; then
    echo "Warning: Maven settings.xml not found at ~/.m2/settings.xml"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Add profile options
PROFILES=""
if [ "$RUN_INTEGRATION_TESTS" = true ]; then
    if [ "$USE_OPENSEARCH" = true ]; then
        MVN_OPTS="$MVN_OPTS -Duse.opensearch=true -P opensearch"
        echo "Running integration tests with OpenSearch"
    else
        echo "Running integration tests with ElasticSearch"
    fi
    MVN_OPTS="$MVN_OPTS -P integration-tests"
else
    if [ "$SKIP_TESTS" = true ]; then
        PROFILES="$PROFILES,!integration-tests,!run-tests"
        MVN_OPTS="$MVN_OPTS -DskipTests"
    fi
fi

if [ ! -z "$PROFILES" ]; then
    # Remove leading comma if present
    PROFILES=${PROFILES#,}
    MVN_OPTS="$MVN_OPTS -P$PROFILES"
fi

# Progress tracking functions
start_timer() {
    start_time=$(date +%s)
}

get_elapsed_time() {
    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))
    printf "%02d:%02d" $((elapsed/60)) $((elapsed%60))
}

# Function to validate combinations of options
validate_options() {
    # Check for mutually exclusive options
    if [ "$SKIP_TESTS" = true ] && [ "$RUN_INTEGRATION_TESTS" = true ]; then
        echo "Error: Cannot use --skip-tests and --integration-tests together"
        exit 1
    fi

    # Check for offline mode conflicts
    if [ "$MAVEN_OFFLINE" = true ]; then
        if [ "$PURGE_MAVEN_CACHE" = true ]; then
            echo "Error: Cannot use --purge-maven-cache in offline mode (--offline)"
            exit 1
        fi
        if [ "$USE_MAVEN_CACHE" = false ]; then
            echo "Warning: Using --no-maven-cache with offline mode may cause build failures"
            prompt_continue
        fi
    fi

    # Validate debug-related options
    if [ "$DEBUG" = true ]; then
        if ! [[ "$KARAF_DEBUG_PORT" =~ ^[0-9]+$ ]] || [ "$KARAF_DEBUG_PORT" -lt 1024 ] || [ "$KARAF_DEBUG_PORT" -gt 65535 ]; then
            echo "Error: Debug port must be a valid port number (1024-65535)"
            exit 1
        fi
        # Check if debug port is already in use
        if command -v nc >/dev/null 2>&1; then
            if nc -z localhost "$KARAF_DEBUG_PORT" 2>/dev/null; then
                echo "Error: Port $KARAF_DEBUG_PORT is already in use"
                exit 1
            fi
        fi
    fi

    # Validate Karaf home if specified
    if [ ! -z "$CONTEXT_SERVER_KARAF_HOME" ]; then
        if [ ! -d "$CONTEXT_SERVER_KARAF_HOME" ]; then
            echo "Error: Specified Karaf home directory does not exist: $CONTEXT_SERVER_KARAF_HOME"
            exit 1
        fi
        if [ ! -w "$CONTEXT_SERVER_KARAF_HOME" ]; then
            echo "Error: Specified Karaf home directory is not writable: $CONTEXT_SERVER_KARAF_HOME"
            exit 1
        fi
    fi

    # Check system requirements
    check_system_requirements
}

# Function to check system requirements
check_system_requirements() {
    print_section "System Requirements Check"
    local has_warnings=false

    # Java check
    if command -v java >/dev/null 2>&1; then
        java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        if [[ "$java_version" =~ ^1[1-9]\. || "$java_version" =~ ^[2-9][0-9]\. ]]; then
            print_status "success" "Java ${java_version} detected"
        else
            print_status "error" "Java 11 or higher required (found: ${java_version})"
            exit 1
        fi
    else
        print_status "error" "Java not found"
        exit 1
    fi

    # Maven check
    if [ "$MAVEN_OFFLINE" = false ]; then
        if command -v mvn >/dev/null 2>&1; then
            mvn_version=$(mvn --version | head -n 1)
            print_status "success" "${mvn_version}"
        else
            print_status "error" "Maven not found"
            exit 1
        fi
    fi

    # Memory check
    if command -v free >/dev/null 2>&1; then
        available_memory=$(free -m | awk '/^Mem:/{print $2}')
        if [ "$available_memory" -lt 2048 ]; then
            print_status "warning" "Low memory: ${available_memory}MB available (2048MB recommended)"
            has_warnings=true
        else
            print_status "success" "Memory: ${available_memory}MB available"
        fi
    fi

    # Disk space check
    if command -v df >/dev/null 2>&1; then
        available_disk=$(df -m . | awk 'NR==2 {print $4}')
        if [ "$available_disk" -lt 1024 ]; then
            print_status "warning" "Low disk space: ${available_disk}MB available (1024MB recommended)"
            has_warnings=true
        else
            print_status "success" "Disk space: ${available_disk}MB available"
        fi
    fi

    if [ "$has_warnings" = true ]; then
        echo
        prompt_continue "Continue despite warnings?"
    fi
}

# Enhanced prompt_continue with color support
prompt_continue() {
    local message=${1:-"Continue?"}
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -en "${YELLOW}${WARNING} ${message} (y/N) ${NC}"
    else
        echo -en "${WARNING} ${message} (y/N) "
    fi
    read -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_status "error" "Operation cancelled by user"
        exit 1
    fi
}

# Add this after parsing arguments
validate_options

# Build command
cat << "EOF"
     ____  _    _ _____ _      ____
    |  _ \| |  | |_   _| |    |  _ \
    | |_) | |  | | | | | |    | | | |
    |  _ <| |  | | | | | |    | | | |
    | |_) | |__| |_| |_| |____| |_| |
    |____/ \____/|_____|______|____/

EOF
echo "Building..."
echo "Estimated time: 3-5 minutes for build, 50-60 minutes with integration tests"
start_timer

# Build phases with enhanced output
total_steps=2
current_step=0

print_progress $((++current_step)) $total_steps "Cleaning previous build..."
if [ "$HAS_COLORS" -eq 1 ]; then
    echo -e "${GRAY}Running: $MVN_CMD clean $MVN_OPTS${NC}"
else
    echo "Running: $MVN_CMD clean $MVN_OPTS"
fi
$MVN_CMD clean $MVN_OPTS || {
    print_status "error" "Maven clean failed"
    exit 1
}

print_progress $((++current_step)) $total_steps "Compiling and installing artifacts..."
if [ "$HAS_COLORS" -eq 1 ]; then
    echo -e "${GRAY}Running: $MVN_CMD install $MVN_OPTS${NC}"
else
    echo "Running: $MVN_CMD install $MVN_OPTS"
fi
$MVN_CMD install $MVN_OPTS || {
    print_status "error" "Maven install failed"
    exit 1
}

print_status "success" "Build completed in $(get_elapsed_time)"

# Deployment section with enhanced output
if [ "$DEPLOY" = true ]; then
    # Validate Karaf home directory
    if [ -z "$CONTEXT_SERVER_KARAF_HOME" ]; then
        print_status "error" "Karaf home directory not set. Use --karaf-home option."
        exit 1
    fi
    if [ ! -d "$CONTEXT_SERVER_KARAF_HOME" ]; then
        print_status "error" "Karaf home directory does not exist: $CONTEXT_SERVER_KARAF_HOME"
        exit 1
    fi
    if [ ! -w "$CONTEXT_SERVER_KARAF_HOME/deploy" ]; then
        print_status "error" "No write permission to Karaf deploy directory: $CONTEXT_SERVER_KARAF_HOME/deploy"
        exit 1
    fi

    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${MAGENTA}"
        cat << "EOF"
     ____  _____ ____  _     _____   __
    |  _ \| ____|  _ \| |   / _ \ \ / /
    | | | |  _| | |_) | |  | | | \ V /
    | |_| | |___|  __/| |__| |_| || |
    |____/|_____|_|   |_____\___/ |_|

EOF
        echo -e "${NC}"
    else
        cat << "EOF"
     ____  _____ ____  _     _____   __
    |  _ \| ____|  _ \| |   / _ \ \ / /
    | | | |  _| | |_) | |  | | | \ V /
    | |_| | |___|  __/| |__| |_| || |
    |____/|_____|_|   |_____\___/ |_|

EOF
    fi

    start_timer
    total_deploy_steps=3
    current_step=0

    print_progress $((++current_step)) $total_deploy_steps "Copying KAR package..."
    if [ ! -f "kar/target/unomi-kar-$UNOMI_VERSION.kar" ]; then
        print_status "error" "KAR file not found: kar/target/unomi-kar-$UNOMI_VERSION.kar"
        exit 1
    fi
    cp -v kar/target/unomi-kar-$UNOMI_VERSION.kar $CONTEXT_SERVER_KARAF_HOME/deploy/ || {
        print_status "error" "Failed to copy KAR file to deploy directory"
        exit 1
    }
    print_status "success" "KAR package copied successfully"

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf Maven repository..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository/"* || {
            print_status "error" "Failed to purge Karaf Maven repository"
            exit 1
        }
        print_status "success" "Karaf Maven repository purged"
    else
        print_status "info" "Karaf Maven repository not found, skipping purge"
    fi

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf temporary files..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/tmp" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/tmp/"* || {
            print_status "error" "Failed to purge Karaf temporary directory"
            exit 1
        }
        print_status "success" "Karaf temporary files purged"
    else
        print_status "info" "Karaf temporary directory not found, skipping purge"
    fi

    print_status "success" "Deployment completed in $(get_elapsed_time)"
fi

# Karaf startup section
if [ "$NO_KARAF" = false ]; then
    if [ "$HAS_COLORS" -eq 1 ]; then
        echo -e "${GREEN}"
        cat << "EOF"
     _  __    _    ____      _    _____
    | |/ /   / \  |  _ \    / \  |  ___|
    | ' /   / _ \ | |_) |  / _ \ | |_
    | . \  / ___ \|  _ <  / ___ \|  _|
    |_|\_\/_/   \_\_| \_\/_/   \_\_|
EOF
        echo -e "${NC}"
    else
        cat << "EOF"
     _  __    _    ____      _    _____
    | |/ /   / \  |  _ \    / \  |  ___|
    | ' /   / _ \ | |_) |  / _ \ | |_
    | . \  / ___ \|  _ <  / ___ \|  _|
    |_|\_\/_/   \_\_| \_\/_/   \_\_|
EOF
    fi

    total_karaf_steps=4  # Increased for additional checks
    current_step=0

    # Check Karaf environment
    print_progress $((++current_step)) $total_karaf_steps "Checking Karaf environment..."
    if [ ! -d "package/target" ]; then
        print_status "error" "Build directory not found. Did the build complete successfully?"
        exit 1
    fi

    pushd package/target || {
        print_status "error" "Failed to change directory to package/target"
        exit 1
    }

    # Verify Unomi version is set
    if [ -z "$UNOMI_VERSION" ]; then
        print_status "error" "UNOMI_VERSION is not set"
        exit 1
    fi

    print_progress $((++current_step)) $total_karaf_steps "Uncompressing Unomi package..."
    if [ ! -f "unomi-$UNOMI_VERSION.tar.gz" ]; then
        print_status "error" "Unomi package not found: unomi-$UNOMI_VERSION.tar.gz"
        exit 1
    fi
    tar zxvf unomi-$UNOMI_VERSION.tar.gz

    print_progress $((++current_step)) $total_karaf_steps "Installing optional databases..."
    if [ -f "../../GeoLite2-City.mmdb" ]; then
        print_status "info" "Installing GeoLite2 City database..."
        cp -v ../../GeoLite2-City.mmdb unomi-$UNOMI_VERSION/etc || {
            print_status "error" "Failed to copy GeoLite2 database"
            exit 1
        }
    else
        print_status "info" "GeoLite2 City database not found (optional)"
    fi

    if [ -f "../../allCountries.zip" ]; then
        print_status "info" "Installing Geonames countries database..."
        cp -v ../../allCountries.zip unomi-$UNOMI_VERSION/etc || {
            print_status "error" "Failed to copy Geonames database"
            exit 1
        }
    else
        print_status "info" "Geonames countries database not found (optional)"
    fi

    cd unomi-$UNOMI_VERSION/bin || {
        print_status "error" "Failed to change directory to Karaf bin directory"
        exit 1
    }

    print_progress $((++current_step)) $total_karaf_steps "Starting Karaf..."
    if [ "$DEBUG" = true ]; then
        print_status "info" "Debug mode enabled (port: $KARAF_DEBUG_PORT, suspend: $KARAF_DEBUG_SUSPEND)"
        if command -v nc >/dev/null 2>&1; then
            if nc -z localhost "$KARAF_DEBUG_PORT" 2>/dev/null; then
                print_status "error" "Port $KARAF_DEBUG_PORT is already in use"
                exit 1
            fi
        fi
        export KARAF_DEBUG=true
        export JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$KARAF_DEBUG_SUSPEND,address=$KARAF_DEBUG_PORT"
    fi

    if [ ! -x "./karaf" ]; then
        print_status "error" "Karaf executable not found or not executable"
        exit 1
    fi

    if [ ! -z "$AUTO_START" ]; then
        print_status "info" "Configuring auto-start for $AUTO_START"
        export KARAF_OPTS="-Dunomi.autoStart=$AUTO_START"
    else
        print_status "info" "Use [unomi:start] to start Unomi after Karaf initialization"
    fi

    ./karaf || {
        print_status "error" "Karaf failed to start"
        exit 1
    }
    popd || true
else
    print_status "info" "Skipping Karaf startup (--no-karaf specified)"
    if [ ! -z "$AUTO_START" ]; then
        print_status "info" "Note: auto-start ($AUTO_START) will be applied when Karaf is started manually"
    fi
fi

cat << "EOF"
     ____   ___  _   _ _____ _
    |  _ \ / _ \| \ | | ____| |
    | | | | | | |  \| |  _| | |
    | |_| | |_| | |\  | |___|_|
    |____/ \___/|_| \_|_____(_)

EOF
echo "Operation completed successfully."
