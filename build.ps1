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

# Error handling
$ErrorActionPreference = "Stop"
$PSDefaultParameterValues['*:ErrorAction'] = 'Stop'

# Script parameters
param(
    [switch]$Help,
    [switch]$MavenDebug,
    [switch]$Offline,
    [switch]$SkipTests,
    [switch]$IntegrationTests,
    [switch]$Deploy,
    [switch]$Debug,
    [int]$DebugPort = 5005,
    [switch]$DebugSuspend,
    [switch]$NoMavenCache,
    [switch]$PurgeMavenCache,
    [string]$KarafHome,
    [switch]$UseOpenSearch,
    [switch]$NoKaraf,
    [string]$AutoStart,
    [switch]$ResolverDebug
)

# Global variables
$script:HasColors = $Host.UI.SupportsVirtualTerminal
$script:StartTime = $null

# ANSI color codes (only used if terminal supports them)
if ($HasColors) {
    $script:RED = "`e[0;31m"
    $script:GREEN = "`e[0;32m"
    $script:YELLOW = "`e[1;33m"
    $script:BLUE = "`e[0;34m"
    $script:MAGENTA = "`e[0;35m"
    $script:CYAN = "`e[0;36m"
    $script:GRAY = "`e[0;90m"
    $script:BOLD = "`e[1m"
    $script:NC = "`e[0m"
    
    # Unicode symbols
    $script:CHECK_MARK = "✓"
    $script:CROSS_MARK = "✗"
    $script:ARROW = "➜"
    $script:WARNING = "⚠"
    $script:INFO = "ℹ"
} else {
    # Plain text alternatives
    $script:CHECK_MARK = "(+)"
    $script:CROSS_MARK = "(x)"
    $script:ARROW = "(>)"
    $script:WARNING = "(!)"
    $script:INFO = "(i)"
}

# Helper functions
function Write-Section {
    param([string]$Text)
    
    $totalWidth = 80
    $textLength = $Text.Length
    $paddingLength = [math]::Floor(($totalWidth - $textLength - 4) / 2)
    $leftPadding = " " * $paddingLength
    $rightPadding = " " * ($paddingLength + ($totalWidth - $textLength - 4) % 2)
    
    Write-Host ""
    if ($HasColors) {
        Write-Host "$BLUE╔════════════════════════════════════════════════════════════════════════════╗$NC"
        Write-Host "$BLUE║$NC$leftPadding$BOLD$Text$NC$rightPadding$BLUE║$NC"
        Write-Host "$BLUE╚════════════════════════════════════════════════════════════════════════════╝$NC"
    } else {
        Write-Host "+------------------------------------------------------------------------------+"
        Write-Host "| $leftPadding$Text$rightPadding |"
        Write-Host "+------------------------------------------------------------------------------+"
    }
}

function Write-Status {
    param(
        [string]$Status,
        [string]$Message
    )
    
    $symbol = switch ($Status) {
        "success" { if ($HasColors) { "$GREEN$CHECK_MARK$NC" } else { $CHECK_MARK } }
        "error"   { if ($HasColors) { "$RED$CROSS_MARK$NC" } else { $CROSS_MARK } }
        "warning" { if ($HasColors) { "$YELLOW$WARNING$NC" } else { $WARNING } }
        "info"    { if ($HasColors) { "$CYAN$INFO$NC" } else { $INFO } }
        default   { if ($HasColors) { "$BLUE$ARROW$NC" } else { $ARROW } }
    }
    
    $color = switch ($Status) {
        "success" { $GREEN }
        "error"   { $RED }
        "warning" { $YELLOW }
        "info"    { $CYAN }
        default   { "" }
    }
    
    if ($HasColors) {
        Write-Host " $symbol $color$Message$NC"
    } else {
        Write-Host " $symbol $Message"
    }
}

function Write-Progress {
    param(
        [int]$Current,
        [int]$Total,
        [string]$Message
    )
    
    $percentage = [math]::Floor(($Current * 100) / $Total)
    
    if ($HasColors) {
        $filled = [math]::Floor($percentage / 2)
        $empty = 50 - $filled
        
        $progress = "["
        $progress += "█" * $filled
        $progress += "░" * $empty
        $progress += "]"
        
        Write-Host "`r$CYAN$progress$NC $percentage% $GRAY$Message$NC" -NoNewline
    } else {
        $filled = [math]::Floor($percentage / 4)
        $empty = 25 - $filled
        
        $progress = "["
        $progress += "#" * $filled
        $progress += "-" * $empty
        $progress += "]"
        
        Write-Host "`r$progress $percentage% $Message" -NoNewline
    }
}

function Test-Command {
    param([string]$Name)
    return [bool](Get-Command -Name $Name -ErrorAction SilentlyContinue)
}

function Get-ElapsedTime {
    if ($null -eq $script:StartTime) { return "00:00" }
    $elapsed = [math]::Floor(((Get-Date) - $script:StartTime).TotalSeconds)
    return "{0:d2}:{1:d2}" -f ($elapsed / 60), ($elapsed % 60)
}

function Start-Timer {
    $script:StartTime = Get-Date
}

function Show-Usage {
    if ($HasColors) {
        Write-Host $CYAN
        Write-Host @"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
"@
        Write-Host $NC
    } else {
        Write-Host @"
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
"@
    }
    
    Write-Host "Usage: build.ps1 [options]"
    Write-Host ""
    Write-Host "Options:"
    if ($HasColors) {
        Write-Host "  $CYAN-Help$NC                    Show this help message"
        Write-Host "  $CYAN-MavenDebug$NC             Enable Maven debug output"
        Write-Host "  $CYAN-Offline$NC                Run Maven in offline mode"
        Write-Host "  $CYAN-SkipTests$NC              Skip all tests"
        Write-Host "  $CYAN-IntegrationTests$NC       Run integration tests"
        Write-Host "  $CYAN-Deploy$NC                 Deploy after build"
        Write-Host "  $CYAN-Debug$NC                  Run Karaf in debug mode"
        Write-Host "  $CYAN-DebugPort$NC <port>       Set debug port (default: 5005)"
        Write-Host "  $CYAN-DebugSuspend$NC          Suspend JVM until debugger connects"
        Write-Host "  $CYAN-NoMavenCache$NC           Disable Maven build cache"
        Write-Host "  $CYAN-PurgeMavenCache$NC        Purge local Maven cache before building"
        Write-Host "  $CYAN-KarafHome$NC <path>       Set Karaf home directory for deployment"
        Write-Host "  $CYAN-UseOpenSearch$NC         Use OpenSearch instead of ElasticSearch"
        Write-Host "  $CYAN-NoKaraf$NC              Build without starting Karaf"
        Write-Host "  $CYAN-AutoStart$NC <engine>    Auto-start with specified engine"
        Write-Host "  $CYAN-ResolverDebug$NC        Enable Karaf Resolver debug logging for integration tests"
    } else {
        Write-Host "  -Help                    Show this help message"
        Write-Host "  -MavenDebug             Enable Maven debug output"
        Write-Host "  -Offline                Run Maven in offline mode"
        Write-Host "  -SkipTests              Skip all tests"
        Write-Host "  -IntegrationTests       Run integration tests"
        Write-Host "  -Deploy                 Deploy after build"
        Write-Host "  -Debug                  Run Karaf in debug mode"
        Write-Host "  -DebugPort <port>       Set debug port (default: 5005)"
        Write-Host "  -DebugSuspend          Suspend JVM until debugger connects"
        Write-Host "  -NoMavenCache           Disable Maven build cache"
        Write-Host "  -PurgeMavenCache        Purge local Maven cache before building"
        Write-Host "  -KarafHome <path>       Set Karaf home directory for deployment"
        Write-Host "  -UseOpenSearch         Use OpenSearch instead of ElasticSearch"
        Write-Host "  -NoKaraf              Build without starting Karaf"
        Write-Host "  -AutoStart <engine>    Auto-start with specified engine"
        Write-Host "  -ResolverDebug         Enable Karaf Resolver debug logging for integration tests"
    }
    
    Write-Host ""
    Write-Host "Examples:"
    if ($HasColors) {
        Write-Host "$GRAY# Build with integration tests using OpenSearch$NC"
        Write-Host "$GRAY.\build.ps1 -IntegrationTests -UseOpenSearch$NC"
        Write-Host ""
        Write-Host "$GRAY# Build in debug mode$NC"
        Write-Host "$GRAY.\build.ps1 -Debug -DebugPort 5006 -DebugSuspend$NC"
        Write-Host ""
        Write-Host "$GRAY# Deploy to specific Karaf instance$NC"
        Write-Host "$GRAY.\build.ps1 -Deploy -KarafHome ~\apache-karaf$NC"
    } else {
        Write-Host "# Build with integration tests using OpenSearch"
        Write-Host ".\build.ps1 -IntegrationTests -UseOpenSearch"
        Write-Host ""
        Write-Host "# Build in debug mode"
        Write-Host ".\build.ps1 -Debug -DebugPort 5006 -DebugSuspend"
        Write-Host ""
        Write-Host "# Deploy to specific Karaf instance"
        Write-Host ".\build.ps1 -Deploy -KarafHome ~\apache-karaf"
    }
    
    exit 0
}

# Show help if requested
if ($Help) {
    Show-Usage
}

# Validate AutoStart parameter
if ($AutoStart -and $AutoStart -notin @('elasticsearch', 'opensearch')) {
    Write-Status "error" "AutoStart must be either 'elasticsearch' or 'opensearch'"
    exit 1
}

# Set environment
$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition
$SetEnvPath = Join-Path $ScriptPath "setenv.ps1"
if (Test-Path $SetEnvPath) {
    . $SetEnvPath
}

# Purge Maven cache if requested
if ($PurgeMavenCache) {
    Write-Host "Purging Maven cache..."
    Remove-Item -Force -Recurse -ErrorAction SilentlyContinue `
        "$env:USERPROFILE\.m2\build-cache",
        "$env:USERPROFILE\.m2\dependency-cache",
        "$env:USERPROFILE\.m2\dependency-cache_v2"
    Write-Host "Maven cache purged."
}

function Test-JavaVersion {
    try {
        $javaVersion = & java -version 2>&1
        if ($javaVersion -match 'version "(\d+)') {
            $version = [int]$Matches[1]
            if ($version -ge 11) {
                Write-Status "success" "Java $version detected"
                return $true
            } else {
                Write-Status "error" "Java $version detected, but version 11 or higher is required"
                Write-Status "info" "To install Java 11 or higher:"
                Write-Status "info" "  - Windows (Chocolatey): choco install temurin11"
                Write-Status "info" "  - Windows (Manual): Download from https://adoptium.net"
                Write-Status "info" "  - Windows (Scoop): scoop install temurin11-jdk"
                Write-Status "info" "For more details, see BUILDING.md in the project root"
                return $false
            }
        }
    } catch {
        Write-Status "error" "Java not found"
        Write-Status "info" "To install Java 11 or higher:"
        Write-Status "info" "  - Windows (Chocolatey): choco install temurin11"
        Write-Status "info" "  - Windows (Manual): Download from https://adoptium.net"
        Write-Status "info" "  - Windows (Scoop): scoop install temurin11-jdk"
        Write-Status "info" "For more details, see BUILDING.md in the project root"
        return $false
    }
    return $false
}

function Test-MavenVersion {
    try {
        $mvnVersion = & mvn -version 2>&1
        if ($mvnVersion -match 'Apache Maven (\d+)\.(\d+)') {
            $major = [int]$Matches[1]
            $minor = [int]$Matches[2]
            if ($major -gt 3 -or ($major -eq 3 -and $minor -ge 6)) {
                Write-Status "success" "Maven $major.$minor detected"
                return $true
            } else {
                Write-Status "error" "Maven $major.$minor detected, but version 3.6 or higher is required"
                Write-Status "info" "To install Maven 3.6 or higher:"
                Write-Status "info" "  - Windows (Chocolatey): choco install maven"
                Write-Status "info" "  - Windows (Manual): Download from https://maven.apache.org"
                Write-Status "info" "  - Windows (Scoop): scoop install maven"
                Write-Status "info" "For more details, see BUILDING.md in the project root"
                return $false
            }
        }
    } catch {
        Write-Status "error" "Maven not found"
        Write-Status "info" "To install Maven 3.6 or higher:"
        Write-Status "info" "  - Windows (Chocolatey): choco install maven"
        Write-Status "info" "  - Windows (Manual): Download from https://maven.apache.org"
        Write-Status "info" "  - Windows (Scoop): scoop install maven"
        Write-Status "info" "For more details, see BUILDING.md in the project root"
        return $false
    }
    return $false
}

function Test-GraphViz {
    try {
        $dotVersion = & dot -V 2>&1
        if ($dotVersion -match 'graphviz version') {
            Write-Status "success" "GraphViz detected"
            return $true
        }
    } catch {
        Write-Status "error" "GraphViz (dot) not found - required for documentation generation"
        Write-Status "info" "To install GraphViz:"
        Write-Status "info" "  - Windows (Chocolatey): choco install graphviz"
        Write-Status "info" "  - Windows (Manual): Download from https://graphviz.org"
        Write-Status "info" "  - Windows (Scoop): scoop install graphviz"
        Write-Status "info" "For more details, see BUILDING.md in the project root"
        return $false
    }
    return $false
}

function Test-SystemRequirements {
    $hasErrors = $false
    $hasWarnings = $false
    
    Write-Section "Checking System Requirements"
    
    # Check available memory
    $memory = Get-CimInstance Win32_OperatingSystem
    $availableMemoryGB = [math]::Round($memory.FreePhysicalMemory / 1MB, 2)
    $totalMemoryGB = [math]::Round($memory.TotalVisibleMemorySize / 1MB, 2)
    
    if ($availableMemoryGB -lt 2) {
        Write-Status "error" "Insufficient memory: $availableMemoryGB GB available, minimum 2 GB required"
        $hasErrors = $true
    } elseif ($availableMemoryGB -lt 4) {
        Write-Status "warning" "Low memory: $availableMemoryGB GB available, recommended 4 GB or more"
        $hasWarnings = $true
    } else {
        Write-Status "success" "Memory: $availableMemoryGB GB available of $totalMemoryGB GB total"
    }
    
    # Check available disk space
    $drive = Get-PSDrive -Name (Split-Path -Qualifier $PWD.Path)
    $freeSpaceGB = [math]::Round($drive.Free / 1GB, 2)
    $totalSpaceGB = [math]::Round(($drive.Free + $drive.Used) / 1GB, 2)
    
    if ($freeSpaceGB -lt 2) {
        Write-Status "error" "Insufficient disk space: $freeSpaceGB GB available, minimum 2 GB required"
        $hasErrors = $true
    } elseif ($freeSpaceGB -lt 10) {
        Write-Status "warning" "Low disk space: $freeSpaceGB GB available, recommended 10 GB or more"
        $hasWarnings = $true
    } else {
        Write-Status "success" "Disk space: $freeSpaceGB GB available of $totalSpaceGB GB total"
    }
    
    return @{
        HasErrors = $hasErrors
        HasWarnings = $hasWarnings
    }
}

function Test-Requirements {
    $hasErrors = $false
    $hasWarnings = $false
    
    Write-Section "Checking Required Tools"
    
    # Check Java
    if (-not (Test-JavaVersion)) {
        $hasErrors = $true
    }
    
    # Check Maven
    if (-not (Test-MavenVersion)) {
        $hasErrors = $true
    }
    
    # Check GraphViz
    if (-not (Test-GraphViz)) {
        $hasErrors = $true
    }
    
    # Check system requirements
    $sysReq = Test-SystemRequirements
    $hasErrors = $hasErrors -or $sysReq.HasErrors
    $hasWarnings = $hasWarnings -or $sysReq.HasWarnings
    
    # Final status
    Write-Host ""
    if ($hasErrors) {
        Write-Status "error" "One or more requirements not met. Please fix the errors above and try again."
        Write-Status "info" "For more information, see BUILDING.md in the project root"
        Write-Status "info" "or visit https://unomi.apache.org/contribute/building-and-deploying.html"
        exit 1
    } elseif ($hasWarnings) {
        Write-Status "warning" "Requirements met with warnings. You may proceed, but performance might be affected."
    } else {
        Write-Status "success" "All requirements met successfully!"
    }
    
    Write-Host ""
}

# Check requirements before proceeding
Test-Requirements 

# Validate OpenSearch password requirement
if ($UseOpenSearch -or ($AutoStart -and $AutoStart -eq 'opensearch')) {
    if (-not $env:UNOMI_OPENSEARCH_PASSWORD -or [string]::IsNullOrWhiteSpace($env:UNOMI_OPENSEARCH_PASSWORD)) {
        Write-Status "error" "UNOMI_OPENSEARCH_PASSWORD is not set for OpenSearch"
        Write-Status "info" "When using OpenSearch, you must set the UNOMI_OPENSEARCH_PASSWORD environment variable before building/starting."
        Write-Host "Examples:"
        Write-Host "  setx UNOMI_OPENSEARCH_PASSWORD yourStrongPassword (PowerShell - current user)"
        Write-Host "  $env:UNOMI_OPENSEARCH_PASSWORD='yourStrongPassword'; .\build.ps1 -IntegrationTests -UseOpenSearch"
        exit 1
    }
}

# Function to check for conflicting environment variables before integration tests
function Test-IntegrationTestEnvVars {
    $detectedVars = @()
    $scriptDir = Split-Path -Parent $MyInvocation.PSCommandPath
    
    # Check for Elasticsearch environment variables
    if ($env:UNOMI_ELASTICSEARCH_CLUSTERNAME -or 
        $env:UNOMI_ELASTICSEARCH_USERNAME -or 
        $env:UNOMI_ELASTICSEARCH_PASSWORD -or 
        $env:UNOMI_ELASTICSEARCH_SSL_ENABLE -or 
        $env:UNOMI_ELASTICSEARCH_SSL_TRUST_ALL_CERTIFICATES) {
        $detectedVars += "Elasticsearch"
    }
    
    # Check for OpenSearch environment variables
    if ($env:UNOMI_OPENSEARCH_CLUSTERNAME -or 
        $env:UNOMI_OPENSEARCH_ADDRESSES -or 
        $env:UNOMI_OPENSEARCH_USERNAME -or 
        $env:UNOMI_OPENSEARCH_PASSWORD -or 
        $env:UNOMI_OPENSEARCH_SSL_ENABLE -or 
        $env:UNOMI_OPENSEARCH_SSL_TRUST_ALL_CERTIFICATES) {
        $detectedVars += "OpenSearch"
    }
    
    if ($detectedVars.Count -gt 0) {
        Write-Status "error" "Environment variables for $($detectedVars -join ' and ') are set and will interfere with integration tests"
        Write-Host ""
        Write-Host "Integration tests manage their own search engine configuration and should not"
        Write-Host "be run with these environment variables set."
        Write-Host ""
        Write-Host "To clear the environment variables, run one of the following:"
        Write-Host ""
        if ($detectedVars -contains "Elasticsearch") {
            Write-Host "  . $scriptDir\clear-elasticsearch.sh"
        }
        if ($detectedVars -contains "OpenSearch") {
            Write-Host "  . $scriptDir\clear-opensearch.sh"
        }
        Write-Host ""
        Write-Host "Or manually unset the variables in PowerShell:"
        if ($detectedVars -contains "Elasticsearch") {
            Write-Host "  Remove-Item Env:\UNOMI_ELASTICSEARCH_CLUSTERNAME"
            Write-Host "  Remove-Item Env:\UNOMI_ELASTICSEARCH_USERNAME"
            Write-Host "  Remove-Item Env:\UNOMI_ELASTICSEARCH_PASSWORD"
            Write-Host "  Remove-Item Env:\UNOMI_ELASTICSEARCH_SSL_ENABLE"
            Write-Host "  Remove-Item Env:\UNOMI_ELASTICSEARCH_SSL_TRUST_ALL_CERTIFICATES"
        }
        if ($detectedVars -contains "OpenSearch") {
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_CLUSTERNAME"
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_ADDRESSES"
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_USERNAME"
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_PASSWORD"
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_SSL_ENABLE"
            Write-Host "  Remove-Item Env:\UNOMI_OPENSEARCH_SSL_TRUST_ALL_CERTIFICATES"
        }
        Write-Host ""
        Write-Host "After clearing the variables, you can run the integration tests again."
        exit 1
    }
}

function Get-MavenOptions {
    $options = @()
    
    # Basic options
    if ($MavenDebug) {
        $options += "-X"
    }
    if ($Offline) {
        $options += "-o"
    }
    if ($SkipTests) {
        $options += "-DskipTests"
    }
    if ($IntegrationTests) {
        # Check for conflicting environment variables before running integration tests
        Test-IntegrationTestEnvVars
        $options += "-Pintegration-tests"
    }
    if ($ResolverDebug) {
        $options += "-Dit.unomi.resolver.debug=true"
    }
    if ($NoMavenCache) {
        $options += "-Dmaven.buildcache.enabled=false"
    }
    if ($UseOpenSearch) {
        $options += "-Popensearch"
    }
    
    return $options -join " "
}

function Invoke-MavenBuild {
    Write-Section "Building Apache Unomi"
    
    Start-Timer
    
    $mvnOptions = Get-MavenOptions
    $buildCommand = "mvn clean install $mvnOptions"
    
    if ($ResolverDebug) {
        Write-Status "info" "Karaf Resolver debug logging enabled for integration tests"
    }
    
    Write-Status "info" "Running: $buildCommand"
    Write-Host ""
    
    try {
        Invoke-Expression $buildCommand
        if ($LASTEXITCODE -ne 0) {
            Write-Status "error" "Maven build failed with exit code $LASTEXITCODE"
            Write-Status "info" "Check the build output above for errors"
            Write-Status "info" "For more information, see BUILDING.md in the project root"
            exit 1
        }
        Write-Status "success" "Build completed successfully in $(Get-ElapsedTime)"
    } catch {
        Write-Status "error" "Maven build failed: $_"
        Write-Status "info" "Check the build output above for errors"
        Write-Status "info" "For more information, see BUILDING.md in the project root"
        exit 1
    }
}

function Deploy-ToKaraf {
    param(
        [string]$KarafPath
    )
    
    Write-Section "Deploying to Apache Karaf"
    
    if (-not $KarafPath) {
        Write-Status "error" "Karaf home directory not specified"
        Write-Status "info" "Please specify the Karaf home directory using -KarafHome"
        exit 1
    }
    
    if (-not (Test-Path $KarafPath)) {
        Write-Status "error" "Karaf home directory not found: $KarafPath"
        Write-Status "info" "Please ensure the directory exists and try again"
        exit 1
    }
    
    # Stop Karaf if it's running
    $karafPid = $null
    if (Test-Path "$KarafPath\data\karaf.pid") {
        $karafPid = Get-Content "$KarafPath\data\karaf.pid"
        $process = Get-Process -Id $karafPid -ErrorAction SilentlyContinue
        if ($process) {
            Write-Status "info" "Stopping Karaf instance (PID: $karafPid)"
            Stop-Process -Id $karafPid -Force
            Start-Sleep -Seconds 5
        }
    }
    
    # Clean Karaf directories
    Write-Status "info" "Cleaning Karaf directories"
    Remove-Item -Force -Recurse -ErrorAction SilentlyContinue `
        "$KarafPath\data",
        "$KarafPath\cache",
        "$KarafPath\log"
    
    # Copy artifacts
    Write-Status "info" "Copying artifacts to Karaf deploy directory"
    Copy-Item -Force -Recurse "package\target\unomi-*\*" $KarafPath
    
    # Configure debug options if needed
    if ($Debug) {
        $debugOpts = "debug"
        if ($DebugSuspend) {
            $debugOpts += "=suspend"
        }
        $debugOpts += "=$DebugPort"
        
        $envContent = @"
EXTRA_JAVA_OPTS=`"-agentlib:jdwp=transport=dt_socket,server=y,$debugOpts`"
"@
        Set-Content -Path "$KarafPath\bin\setenv.bat" -Value $envContent
    }
    
    # Start Karaf
    if (-not $NoKaraf) {
        Write-Status "info" "Starting Karaf"
        $karafCmd = "$KarafPath\bin\karaf.bat"
        if ($Debug) {
            Write-Status "info" "Debug port: $DebugPort (suspend: $DebugSuspend)"
        }
        
        if ($AutoStart) {
            Write-Status "info" "Auto-starting with $AutoStart"
            Start-Process $karafCmd -ArgumentList "clean" -NoNewWindow
        } else {
            Start-Process $karafCmd -NoNewWindow
        }
        
        Write-Status "success" "Karaf started successfully"
        Write-Status "info" "Use bin\client.bat to connect to the console"
        Write-Status "info" "Default credentials: karaf/karaf"
    }
}

# Build and deploy
Invoke-MavenBuild

if ($Deploy) {
    Deploy-ToKaraf -KarafPath $KarafHome
}

Write-Status "success" "Build process completed successfully!" 