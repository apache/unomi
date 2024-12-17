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

echo "-----------------------------------------------------------------------------------------------"
echo "Apache Unomi Build Script"
echo "-----------------------------------------------------------------------------------------------"
echo

# Default values
SKIP_TESTS=false
RUN_INTEGRATION_TESTS=false
DEPLOY=false
DEBUG=false
USE_MAVEN_CACHE=true
PURGE_MAVEN_CACHE=false
MAVEN_DEBUG=false
MAVEN_OFFLINE=false
SEARCH_ENGINE="elasticsearch"
KARAF_DEBUG_PORT=5005
KARAF_DEBUG_SUSPEND=n

# Function to display usage
usage() {
    cat << "EOF"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|

EOF
    echo "Usage: $0 [options]"
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
    echo "  --search-engine ENGINE     Set search engine (elasticsearch|opensearch)"
    echo "  --karaf-home PATH          Set Karaf home directory for deployment"
    echo ""
    echo "Examples:"
    echo "  $0 --integration-tests --search-engine opensearch"
    echo "  $0 --debug --debug-port 5006 --debug-suspend"
    echo "  $0 --deploy --karaf-home ~/apache-karaf"
    echo "  $0 --purge-maven-cache --no-maven-cache"
    echo "  $0 -X --integration-tests    Run tests with Maven debug output"
    echo "  $0 -o -X                    Run offline with Maven debug output"
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
        --search-engine)
            shift
            SEARCH_ENGINE=$1
            ;;
        --karaf-home)
            shift
            CONTEXT_SERVER_KARAF_HOME=$1
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

# Validate search engine value
if [ "$SEARCH_ENGINE" != "elasticsearch" ] && [ "$SEARCH_ENGINE" != "opensearch" ]; then
    echo "Invalid search engine: $SEARCH_ENGINE. Must be 'elasticsearch' or 'opensearch'"
    exit 1
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
    PROFILES="$PROFILES,integration-tests,$SEARCH_ENGINE"
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

print_progress() {
    local step=$1
    local total=$2
    local msg=$3
    local elapsed=$(get_elapsed_time)
    local width=50
    local progress=$((width * step / total))
    printf "\r["
    printf "%${progress}s" | tr ' ' '#'
    printf "%$((width-progress))s" | tr ' ' '-'
    printf "] %d%% %s (%s)" $((100 * step / total)) "$msg" "$elapsed"
}

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

# Build phases
total_steps=2
current_step=0

print_progress $((++current_step)) $total_steps "Cleaning previous build..."
$MVN_CMD clean $MVN_OPTS

print_progress $((++current_step)) $total_steps "Compiling, testing and installing artifacts..."
$MVN_CMD install $MVN_OPTS

echo -e "\nBuild completed in $(get_elapsed_time)"

if [ "$DEPLOY" = true ]; then
    # Validate Karaf home directory
    if [ -z "$CONTEXT_SERVER_KARAF_HOME" ]; then
        echo "Error: Karaf home directory not set. Use --karaf-home option."
        exit 1
    fi
    if [ ! -d "$CONTEXT_SERVER_KARAF_HOME" ]; then
        echo "Error: Karaf home directory does not exist: $CONTEXT_SERVER_KARAF_HOME"
        exit 1
    fi
    if [ ! -w "$CONTEXT_SERVER_KARAF_HOME/deploy" ]; then
        echo "Error: No write permission to Karaf deploy directory: $CONTEXT_SERVER_KARAF_HOME/deploy"
        exit 1
    fi

    cat << "EOF"
     ____  _____ ____  _     _____   __
    |  _ \| ____|  _ \| |   / _ \ \ / /
    | | | |  _| | |_) | |  | | | \ V /
    | |_| | |___|  __/| |__| |_| || |
    |____/|_____|_|   |_____\___/ |_|

EOF
    start_timer
    total_deploy_steps=3
    current_step=0

    print_progress $((++current_step)) $total_deploy_steps "Copying KAR package..."
    if [ ! -f "kar/target/unomi-kar-$UNOMI_VERSION.kar" ]; then
        echo "Error: KAR file not found: kar/target/unomi-kar-$UNOMI_VERSION.kar"
        exit 1
    fi
    cp -v kar/target/unomi-kar-$UNOMI_VERSION.kar $CONTEXT_SERVER_KARAF_HOME/deploy/
    if [ $? -ne 0 ]; then
        echo "Error: Failed to copy KAR file to deploy directory"
        exit 1
    fi

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf Maven repository..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/maven/repository/"* || {
            echo "Error: Failed to purge Karaf Maven repository"
            exit 1
        }
    fi

    print_progress $((++current_step)) $total_deploy_steps "Purging Karaf temporary files..."
    if [ -d "$CONTEXT_SERVER_KARAF_HOME/data/tmp" ]; then
        rm -rf "$CONTEXT_SERVER_KARAF_HOME/data/tmp/"* || {
            echo "Error: Failed to purge Karaf temporary directory"
            exit 1
        }
    fi

    echo -e "\nDeployment completed in $(get_elapsed_time)"
fi

if [ "$DEPLOY" = false ]; then
    cat << "EOF"
     _  __    _    ____      _    _____
    | |/ /   / \  |  _ \    / \  |  ___|
    | ' /   / _ \ | |_) |  / _ \ | |_
    | . \  / ___ \|  _ <  / ___ \|  _|
    |_|\_\/_/   \_\_| \_\/_/   \_\_|

EOF
    start_timer
    total_karaf_steps=3
    current_step=0

    pushd package/target || {
        echo "Error: Failed to change directory to package/target"
        exit 1
    }

    print_progress $((++current_step)) $total_karaf_steps "Uncompressing Unomi package..."
    if [ ! -f "unomi-$UNOMI_VERSION.tar.gz" ]; then
        echo "Error: Unomi package not found: unomi-$UNOMI_VERSION.tar.gz"
        exit 1
    fi
    tar zxvf unomi-$UNOMI_VERSION.tar.gz

    print_progress $((++current_step)) $total_karaf_steps "Installing optional databases..."
    # Copy optional databases with error handling
    if [ -f "../../GeoLite2-City.mmdb" ]; then
        echo "Installing GeoLite2 City database..."
        cp -v ../../GeoLite2-City.mmdb unomi-$UNOMI_VERSION/etc || {
            echo "Error: Failed to copy GeoLite2 database"
            exit 1
        }
    fi

    if [ -f "../../allCountries.zip" ]; then
        echo "Installing Geonames countries database..."
        cp -v ../../allCountries.zip unomi-$UNOMI_VERSION/etc || {
            echo "Error: Failed to copy Geonames database"
            exit 1
        }
    fi

    cd unomi-$UNOMI_VERSION/bin || {
        echo "Error: Failed to change directory to Karaf bin directory"
        exit 1
    }

    print_progress $((++current_step)) $total_karaf_steps "Starting Karaf..."
    if [ "$DEBUG" = true ]; then
        echo "Starting Karaf in debug mode (port: $KARAF_DEBUG_PORT, suspend: $KARAF_DEBUG_SUSPEND)"
        # Check if debug port is already in use
        if command_exists nc; then
            if nc -z localhost $KARAF_DEBUG_PORT; then
                echo "Error: Debug port $KARAF_DEBUG_PORT is already in use"
                exit 1
            fi
        fi
        export KARAF_DEBUG=true
        export JAVA_DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$KARAF_DEBUG_SUSPEND,address=$KARAF_DEBUG_PORT"
    fi

    if [ ! -x "./karaf" ]; then
        echo "Error: Karaf executable not found or not executable"
        exit 1
    fi

    echo "Apache Unomi features installed, use [unomi:start] to start it."
    ./karaf || {
        echo "Error: Karaf failed to start"
        exit 1
    }
    popd || true
fi

cat << "EOF"
     ____   ___  _   _ _____ _
    |  _ \ / _ \| \ | | ____| |
    | | | | | | |  \| |  _| | |
    | |_| | |_| | |\  | |___|_|
    |____/ \___/|_| \_|_____(_)

EOF
echo "Operation completed successfully."
