# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

################################################################################
#
#  Apache Unomi build script for Windows (PowerShell equivalent of build.sh)
#
################################################################################

<#
.SYNOPSIS
    Builds, tests, deploys, and runs Apache Unomi on Windows.

.DESCRIPTION
    Windows counterpart to ./build.sh. Supports the same build modes: unit/integration
    tests, OpenSearch/ElasticSearch IT profiles, Karaf debug, deployment, CI mode,
    Javadoc validation, and integration-test tracing. Run with -Help for all options.

    Requires: Java 11+, Maven 3.6+, GraphViz (dot), tar (Windows 10+ or Git for Windows).
    Integration tests additionally require Docker Desktop.

.EXAMPLE
    .\build.ps1 -Help

.EXAMPLE
    .\build.ps1 -IntegrationTests -UseOpenSearch

.EXAMPLE
    .\build.ps1 -NoKaraf -SkipTests

.NOTES
    Pair with optional setenv.ps1 in the repo root (same role as setenv.sh).
    See manual/src/main/asciidoc/building-and-deploying.adoc (build.sh section).
#>

param(
    [switch]$Help,
    [switch]$MavenDebug,
    [switch]$MavenQuiet,
    [switch]$Offline,
    [switch]$SkipTests,
    [switch]$SkipUnitTests,
    [switch]$IntegrationTests,
    [switch]$Deploy,
    [switch]$Debug,
    [int]$DebugPort = 5005,
    [switch]$DebugSuspend,
    [switch]$NoMavenCache,
    [switch]$PurgeMavenCache,
    [string]$KarafHome,
    [switch]$UseOpenSearch,
    [string]$Distribution,
    [string]$SearchHeap,
    [string]$KarafHeap,
    [switch]$NoKaraf,
    [string]$AutoStart,
    [string]$SingleTest,
    [switch]$ItDebug,
    [int]$ItDebugPort = 5006,
    [switch]$ItDebugSuspend,
    [switch]$SkipMigrationTests,
    [switch]$ResolverDebug,
    [switch]$KeepContainer,
    [switch]$NoMemorySampler,
    [int]$MemoryInterval = 30,
    [switch]$Javadoc,
    [string]$LogFile,
    [switch]$LogFileOnly,
    [switch]$Ci
)

$ErrorActionPreference = 'Stop'
$PSDefaultParameterValues['*:ErrorAction'] = 'Stop'

# Preserve invocation for IT run tracing (archive-it-run.sh reads this on Unix)
$script:BuildScriptInvocation = @($MyInvocation.Line)

# Defaults aligned with build.sh
$script:UseMavenCache = -not $NoMavenCache
$script:ItMemorySampler = -not $NoMemorySampler
$script:KarafDebugSuspend = if ($DebugSuspend) { 'y' } else { 'n' }

if ($Ci) {
    $NoKaraf = $true
    $script:UseMavenCache = $false
    $env:BUILD_NON_INTERACTIVE = 'true'
    $MavenQuiet = $true
    $Javadoc = $true
}

if ($PurgeMavenCache) {
    $script:UseMavenCache = $false
}

if ($UseOpenSearch -and [string]::IsNullOrWhiteSpace($Distribution)) {
    $Distribution = 'unomi-distribution-opensearch'
}
if (-not [string]::IsNullOrWhiteSpace($Distribution) -and $Distribution -like '*opensearch*') {
    $UseOpenSearch = $true
}

if ($LogFileOnly -and [string]::IsNullOrWhiteSpace($LogFile)) {
    Write-Error '--log-file-only requires --log-file PATH'
    exit 1
}

# -LogFileOnly must suppress console output entirely (matching build.sh's
# `exec > "$LOG_FILE" 2>&1`). Start-Transcript alone only tees to console + file,
# so re-invoke this script once with all output streams redirected to the file.
if ($LogFile -and $LogFileOnly -and -not $env:UNOMI_BUILD_PS1_LOG_REDIRECTED) {
    $logDir = Split-Path -Parent $LogFile
    if ($logDir -and -not (Test-Path $logDir)) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }
    $env:UNOMI_BUILD_PS1_LOG_REDIRECTED = '1'
    try {
        & $PSCommandPath @PSBoundParameters *> $LogFile
        exit $LASTEXITCODE
    } finally {
        Remove-Item Env:\UNOMI_BUILD_PS1_LOG_REDIRECTED -ErrorAction SilentlyContinue
    }
}

function Test-NonInteractive {
    return [bool]($env:CI -or $env:GITHUB_ACTIONS -or $env:BUILD_NON_INTERACTIVE -eq 'true')
}

function Initialize-Colors {
    $script:HasColors = $false
    if ($env:NO_COLOR) { return }
    if (-not [Console]::IsOutputRedirected) {
        $script:HasColors = $Host.UI.SupportsVirtualTerminal
    }

    if ($script:HasColors) {
        $script:RED = "`e[0;31m"
        $script:GREEN = "`e[0;32m"
        $script:YELLOW = "`e[1;33m"
        $script:BLUE = "`e[0;34m"
        $script:MAGENTA = "`e[0;35m"
        $script:CYAN = "`e[0;36m"
        $script:GRAY = "`e[0;90m"
        $script:BOLD = "`e[1m"
        $script:NC = "`e[0m"
        $script:CHECK_MARK = '✔'
        $script:CROSS_MARK = '✘'
        $script:ARROW = '➜'
        $script:WARNING = '⚠'
        $script:INFO = 'ℹ'
    } else {
        $script:RED = $script:GREEN = $script:YELLOW = $script:BLUE = ''
        $script:MAGENTA = $script:CYAN = $script:GRAY = $script:BOLD = $script:NC = ''
        $script:CHECK_MARK = '(+)'
        $script:CROSS_MARK = '(x)'
        $script:ARROW = '(>)'
        $script:WARNING = '(!)'
        $script:INFO = '(i)'
    }
}

Initialize-Colors

function Write-Section {
    param([string]$Text)
    $totalWidth = 80
    $textLength = $Text.Length
    $paddingLength = [math]::Floor(($totalWidth - $textLength - 4) / 2)
    $leftPadding = ' ' * $paddingLength
    $rightPadding = ' ' * ($paddingLength + (($totalWidth - $textLength - 4) % 2))
    Write-Host ''
    if ($script:HasColors) {
        Write-Host "$($script:BLUE)╔════════════════════════════════════════════════════════════════════════════╗$($script:NC)"
        Write-Host "$($script:BLUE)║$($script:NC)$leftPadding$($script:BOLD)$Text$($script:NC)$rightPadding$($script:BLUE)║$($script:NC)"
        Write-Host "$($script:BLUE)╚════════════════════════════════════════════════════════════════════════════╝$($script:NC)"
    } else {
        Write-Host '+------------------------------------------------------------------------------+'
        Write-Host "| $leftPadding$Text$rightPadding |"
        Write-Host '+------------------------------------------------------------------------------+'
    }
}

function Write-Status {
    param([string]$Status, [string]$Message)
    $symbol = switch ($Status) {
        'success' { $script:CHECK_MARK }
        'error'   { $script:CROSS_MARK }
        'warning' { $script:WARNING }
        'info'    { $script:INFO }
        default   { $script:ARROW }
    }
    $color = switch ($Status) {
        'success' { $script:GREEN }
        'error'   { $script:RED }
        'warning' { $script:YELLOW }
        'info'    { $script:CYAN }
        default   { '' }
    }
    if ($script:HasColors -and $color) {
        Write-Host " $symbol ${color}${Message}$($script:NC)"
    } else {
        Write-Host " $symbol $Message"
    }
}

function Write-BuildProgress {
    param([int]$Current, [int]$Total, [string]$Message)
    $percentage = [math]::Floor(($Current * 100) / $Total)
    if ($script:HasColors) {
        $filled = [math]::Floor($percentage / 2)
        $empty = 50 - $filled
        $bar = '[' + ('█' * $filled) + ('░' * $empty) + ']'
        Write-Host "`r$($script:CYAN)$bar$($script:NC) $percentage% $($script:GRAY)$Message$($script:NC)" -NoNewline
    } else {
        $filled = [math]::Floor($percentage / 4)
        $empty = 25 - $filled
        $bar = '[' + ('#' * $filled) + ('-' * $empty) + ']'
        Write-Host "`r$bar $percentage% $Message" -NoNewline
    }
}

function Test-CommandExists {
    param([string]$Name)
    return [bool](Get-Command -Name $Name -ErrorAction SilentlyContinue)
}

function Invoke-PromptContinue {
    param([string]$PromptText = 'Continue?')
    if (Test-NonInteractive) {
        Write-Status 'info' "Non-interactive mode: continuing ($PromptText)"
        return
    }
    $reply = Read-Host "$PromptText (y/N)"
    if ($reply -notmatch '^[Yy]$') { exit 1 }
}

function Show-Usage {
    Write-Section 'Apache Unomi Build Script'
    if ($script:HasColors) { Write-Host $script:CYAN }
    Write-Host @'
     _    _ _____ _      ____
    | |  | |  ___| |    |  _ \
    | |__| | |__ | |    | |_) |
    |  __  |  __|| |    |  __/
    | |  | | |___| |____| |
    |_|  |_\_____|______|_|
'@
    if ($script:HasColors) { Write-Host $script:NC }
    Write-Host ''
    Write-Host 'Usage: .\build.ps1 [options]'
    Write-Host ''
    Write-Host 'Options:'
    Write-Host '  -Help                      Show this help message'
    Write-Host '  -SkipTests                 Skip all tests'
    Write-Host '  -SkipUnitTests             Skip unit tests (integration tests can still run)'
    Write-Host '  -IntegrationTests          Run integration tests'
    Write-Host '  -Deploy                    Deploy after build'
    Write-Host '  -MavenDebug                Enable Maven debug output'
    Write-Host '  -MavenQuiet                Disable Maven download progress (quiet mode)'
    Write-Host '  -Offline                   Run Maven in offline mode'
    Write-Host '  -Debug                     Run Karaf in debug mode'
    Write-Host '  -DebugPort PORT            Set debug port (default: 5005)'
    Write-Host '  -DebugSuspend              Suspend JVM until debugger connects'
    Write-Host '  -NoMavenCache              Disable Maven build cache'
    Write-Host '  -PurgeMavenCache           Purge local Maven cache before building'
    Write-Host '  -KarafHome PATH            Set Karaf home directory for deployment'
    Write-Host '  -UseOpenSearch             Use OpenSearch instead of ElasticSearch'
    Write-Host '  -Distribution DIST         Set Unomi distribution (e.g. unomi-distribution-opensearch)'
    Write-Host '  -SearchHeap SIZE           Search engine heap for integration tests'
    Write-Host '  -KarafHeap SIZE            Karaf JVM heap for integration tests'
    Write-Host '  -NoKaraf                   Build without starting Karaf'
    Write-Host '  -AutoStart ENGINE          Auto-start elasticsearch or opensearch'
    Write-Host '  -SingleTest TEST           Run a single integration test'
    Write-Host '  -ItDebug                   Enable integration test debug mode'
    Write-Host '  -ItDebugPort PORT          Set integration test debug port (default: 5006)'
    Write-Host '  -ItDebugSuspend            Suspend integration test until debugger connects'
    Write-Host '  -SkipMigrationTests        Skip migration-related tests'
    Write-Host '  -ResolverDebug             Enable Karaf Resolver debug logging for integration tests'
    Write-Host '  -KeepContainer             Keep search engine container running after tests'
    Write-Host '  -NoMemorySampler           Disable JVM/system memory sampling during integration tests'
    Write-Host '  -MemoryInterval SEC        Memory sample interval in seconds (default: 30)'
    Write-Host '  -Javadoc                   Build and validate Javadoc after install'
    Write-Host '  -Ci                        CI mode: no Karaf, no Maven cache, non-interactive, Javadoc'
    Write-Host '  -LogFile PATH              Tee all output to PATH (console + file)'
    Write-Host '  -LogFileOnly               With -LogFile: write to file only, suppress console'
    Write-Host ''
    Write-Host 'Examples:'
    Write-Host '  .\build.ps1 -IntegrationTests -UseOpenSearch'
    Write-Host '  .\build.ps1 -SkipUnitTests -IntegrationTests'
    Write-Host '  .\build.ps1 -Debug -DebugPort 5006 -DebugSuspend'
    Write-Host '  .\build.ps1 -Deploy -KarafHome C:\apache-karaf'
    Write-Host '  .\build.ps1 -NoKaraf -AutoStart opensearch'
    exit 0
}

if ($Help) { Show-Usage }

if ($AutoStart -and $AutoStart -notin @('elasticsearch', 'opensearch')) {
    Write-Error "AutoStart must be either 'elasticsearch' or 'opensearch'"
    exit 1
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SetEnvPath = Join-Path $ScriptDir 'setenv.ps1'
if (Test-Path $SetEnvPath) { . $SetEnvPath }

function Get-UserHome {
    if ($env:USERPROFILE) { return $env:USERPROFILE }
    if ($env:HOME) { return $env:HOME }
    return (Get-Location).Path
}

function Initialize-ProjectVersion {
    if ($env:UNOMI_VERSION) { return }
    $version = (& mvn -B -q '-DforceStdout' help:evaluate '-Dexpression=project.version' '-DinteractiveMode=false' 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($version)) {
        Write-Error 'Failed to detect project version from Maven'
        exit 1
    }
    $env:UNOMI_VERSION = $version
    Write-Host "Detected project version=$($env:UNOMI_VERSION)"
}

Initialize-ProjectVersion

if ($PurgeMavenCache) {
    Write-Host 'Purging Maven cache...'
    $m2 = Join-Path (Get-UserHome) '.m2'
    foreach ($dir in @('build-cache', 'dependency-cache', 'dependency-cache_v2')) {
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $m2 $dir)
    }
    Write-Host 'Maven cache purged.'
}

function Test-JavaRequirement {
    if (-not (Test-CommandExists 'java')) {
        Write-Status 'error' 'java not found'
        Write-Host 'Please install Java 11 or higher from https://adoptium.net'
        return $false
    }
    $javaVersion = & java -version 2>&1 | Out-String
    if ($javaVersion -match 'version "(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 11) {
            Write-Status 'success' "Java $major detected"
            return $true
        }
    }
    Write-Status 'error' "Java version 11 or higher required: $javaVersion"
    return $false
}

function Test-MavenRequirement {
    if (-not (Test-CommandExists 'mvn')) {
        Write-Status 'error' 'mvn not found'
        return $false
    }
    if ($Offline) {
        Write-Status 'success' 'Maven (offline mode enabled)'
        return $true
    }
    $mvnVersion = (& mvn --version | Select-Object -First 1)
    Write-Status 'success' $mvnVersion
    return $true
}

function Test-GraphVizRequirement {
    if (-not (Test-CommandExists 'dot')) {
        Write-Status 'error' 'GraphViz (dot) not found'
        return $false
    }
    $dotVersion = & dot -V 2>&1 | Out-String
    Write-Status 'success' "GraphViz: $($dotVersion.Trim())"
    if (-not $env:GRAPHVIZ_DOT) {
        $env:GRAPHVIZ_DOT = (Get-Command dot).Source
    }
    return $true
}

function Test-PortAvailable {
    param([int]$Port)
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        return $true
    } catch {
        return $false
    }
}

function Test-DockerForIntegrationTests {
    if (-not (Test-CommandExists 'docker')) {
        Write-Status 'error' 'Docker is not installed or not in PATH'
        Write-Host 'Integration tests require Docker. Install Docker Desktop for Windows.'
        return $false
    }
    $null = & docker info 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Status 'error' 'Docker is not accessible'
        return $false
    }
    Write-Status 'success' "Docker available: $((& docker --version 2>&1) -join ' ')"
    return $true
}

function Test-IntegrationTestEnvVars {
    param([bool]$UseOpenSearch, [string]$AutoStart)
    $detected = @()
    if ($env:UNOMI_ELASTICSEARCH_CLUSTERNAME -or $env:UNOMI_ELASTICSEARCH_USERNAME -or $env:UNOMI_ELASTICSEARCH_PASSWORD -or
        $env:UNOMI_ELASTICSEARCH_SSL_ENABLE -or $env:UNOMI_ELASTICSEARCH_SSL_TRUST_ALL_CERTIFICATES) {
        $detected += 'Elasticsearch'
    }
    # UNOMI_OPENSEARCH_PASSWORD is required (and checked) by Test-Requirements when
    # -UseOpenSearch or -AutoStart opensearch is selected, so it must not be treated as a
    # conflicting leftover variable in that case, or the build would always fail.
    $openSearchSelected = $UseOpenSearch -or ($AutoStart -eq 'opensearch')
    if ($env:UNOMI_OPENSEARCH_CLUSTERNAME -or $env:UNOMI_OPENSEARCH_ADDRESSES -or $env:UNOMI_OPENSEARCH_USERNAME -or
        (-not $openSearchSelected -and $env:UNOMI_OPENSEARCH_PASSWORD) -or $env:UNOMI_OPENSEARCH_SSL_ENABLE -or $env:UNOMI_OPENSEARCH_SSL_TRUST_ALL_CERTIFICATES) {
        $detected += 'OpenSearch'
    }
    if ($detected.Count -eq 0) { return }
    Write-Status 'error' "Environment variables for $($detected -join ', ') are set and will interfere with integration tests"
    Write-Host ''
    Write-Host 'Clear them with clear-elasticsearch.sh / clear-opensearch.sh (Git Bash) or unset in PowerShell.'
    exit 1
}

function Test-Requirements {
    Write-Section 'System Requirements Check'
    $hasErrors = $false
    $hasWarnings = $false

    Write-Status 'info' 'Checking required tools...'
    if (-not (Test-JavaRequirement)) { $hasErrors = $true }
    if (-not (Test-MavenRequirement)) { $hasErrors = $true }
    if (Test-CommandExists 'tar') { Write-Status 'success' 'tar' }
    else { Write-Status 'error' 'tar not found (required on Windows 10+ or via Git for Windows)'; $hasErrors = $true }
    if (-not (Test-GraphVizRequirement)) { $hasErrors = $true }

    if ($IsWindows) {
        $os = Get-CimInstance Win32_OperatingSystem -ErrorAction SilentlyContinue
        if ($os) {
            $availableMemoryMB = [math]::Round($os.FreePhysicalMemory / 1024)
            if ($availableMemoryMB -lt 2048) {
                Write-Status 'warning' "Memory: ${availableMemoryMB}MB available (2048MB recommended)"
                $hasWarnings = $true
            } else {
                Write-Status 'success' "Memory: ${availableMemoryMB}MB available"
            }
        }

        $driveName = (Get-Location).Drive.Name
        if ($driveName) {
            $drive = Get-PSDrive -Name $driveName -ErrorAction SilentlyContinue
            if ($drive) {
                $freeMB = [math]::Round($drive.Free / 1MB)
                if ($freeMB -lt 1024) {
                    Write-Status 'warning' "Disk space: ${freeMB}MB available (1024MB recommended)"
                    $hasWarnings = $true
                } else {
                    Write-Status 'success' "Disk space: ${freeMB}MB available"
                }
            }
        }
    }

    $settings = Join-Path (Get-UserHome) '.m2/settings.xml'
    if (-not (Test-Path $settings)) {
        Write-Status 'warning' 'Maven settings.xml not found'
        $hasWarnings = $true
    } else {
        Write-Status 'success' 'Maven settings.xml found'
    }

    if ($Debug) {
        if ($DebugPort -lt 1024 -or $DebugPort -gt 65535) {
            Write-Status 'error' "Debug port: $DebugPort (invalid)"
            $hasErrors = $true
        } elseif (-not (Test-PortAvailable $DebugPort)) {
            Write-Status 'error' "Debug port: $DebugPort (already in use)"
            $hasErrors = $true
        } else {
            Write-Status 'success' "Debug port: $DebugPort available"
        }
    }

    if ($Deploy) {
        if ([string]::IsNullOrWhiteSpace($KarafHome)) {
            Write-Status 'error' 'Karaf home directory not set for deployment'
            $hasErrors = $true
        } elseif (-not (Test-Path $KarafHome)) {
            Write-Status 'error' "Karaf home directory does not exist: $KarafHome"
            $hasErrors = $true
        } elseif (-not (Test-Path (Join-Path $KarafHome 'deploy'))) {
            Write-Status 'error' "Karaf deploy directory not found: $KarafHome\deploy"
            $hasErrors = $true
        } else {
            Write-Status 'success' "Karaf home directory validated: $KarafHome"
        }
    }

    if ($SkipTests -and $IntegrationTests) {
        Write-Status 'error' 'Cannot use -SkipTests and -IntegrationTests together'
        $hasErrors = $true
    }
    if ($SkipTests -and $SkipUnitTests) {
        Write-Status 'error' 'Cannot use -SkipTests and -SkipUnitTests together'
        $hasErrors = $true
    }

    if ($IntegrationTests -and -not (Test-DockerForIntegrationTests)) {
        $hasErrors = $true
    }

    if (($UseOpenSearch -or $AutoStart -eq 'opensearch') -and [string]::IsNullOrWhiteSpace($env:UNOMI_OPENSEARCH_PASSWORD)) {
        Write-Status 'error' 'UNOMI_OPENSEARCH_PASSWORD is not set for OpenSearch'
        $hasErrors = $true
    }

    if (-not [string]::IsNullOrWhiteSpace($SingleTest) -and -not $IntegrationTests) {
        Write-Status 'error' 'SingleTest specified but integration tests are not enabled. Use -IntegrationTests.'
        $hasErrors = $true
    }
    if ($ItDebug -and -not $IntegrationTests) {
        Write-Status 'error' 'ItDebug enabled but integration tests are not enabled. Use -IntegrationTests.'
        $hasErrors = $true
    }
    if ($ItDebug) {
        if ($ItDebugPort -lt 1024 -or $ItDebugPort -gt 65535) {
            Write-Status 'error' "Integration test debug port: $ItDebugPort (invalid)"
            $hasErrors = $true
        } elseif (-not (Test-PortAvailable $ItDebugPort)) {
            Write-Status 'error' "Integration test debug port: $ItDebugPort (already in use)"
            $hasErrors = $true
        } else {
            Write-Status 'success' "Integration test debug port: $ItDebugPort available"
        }
    }

    if ($Offline -and $PurgeMavenCache) {
        Write-Status 'error' 'Cannot use -PurgeMavenCache in offline mode'
        $hasErrors = $true
    }

    Write-Host ''
    if ($hasErrors) {
        Write-Status 'error' 'Critical requirements not met. Please fix the errors above.'
        exit 1
    }
    if ($hasWarnings) {
        Write-Status 'warning' 'Some non-critical requirements not met'
        Invoke-PromptContinue 'Continue despite warnings?'
    } else {
        Write-Status 'success' 'All requirements checked successfully'
    }
}

Test-Requirements

function Get-MavenArgumentList {
    $args = @()
    if ($MavenDebug) { $args += '-X' }
    if ($Offline) { $args += '-o' }
    if (-not $script:UseMavenCache) { $args += '-Dmaven.build.cache.enabled=false' }
    if ($MavenQuiet) { $args += '-ntp' }
    if ($env:MAVEN_EXTRA_OPTS) {
        $args += $env:MAVEN_EXTRA_OPTS.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
    }
    if (-not [string]::IsNullOrWhiteSpace($Distribution)) {
        $args += "-Dunomi.distribution=$Distribution"
        Write-Host "Using Unomi distribution: $Distribution"
    }
    if ($env:GRAPHVIZ_DOT) {
        $args += "-Dgraphviz.dot.path=$($env:GRAPHVIZ_DOT)"
    }

    $profiles = @()
    if ($IntegrationTests) {
        Test-IntegrationTestEnvVars -UseOpenSearch:$UseOpenSearch -AutoStart $AutoStart
        if ($UseOpenSearch) {
            $args += '-Duse.opensearch=true'
            $args += '-Popensearch'
            Write-Host 'Running integration tests with OpenSearch'
        } else {
            Write-Host 'Running integration tests with ElasticSearch'
        }
        $args += '-Pintegration-tests'
        if ($SearchHeap) {
            if ($UseOpenSearch) { $args += "-Dopensearch.heap=$SearchHeap" }
            else { $args += "-Delasticsearch.heap=$SearchHeap" }
        }
        if ($KarafHeap) { $args += "-Dit.karaf.heap=$KarafHeap" }
        if ($SingleTest) { $args += "-Dit.test=$SingleTest"; Write-Host "Running single integration test: $SingleTest" }
        if ($ItDebug) {
            $debugOpts = "port=$ItDebugPort"
            if ($ItDebugSuspend) { $debugOpts += ',hold:true' } else { $debugOpts += ',hold:false' }
            $args += "-Dit.karaf.debug=$debugOpts"
        }
        if ($SkipMigrationTests) { $args += '-Dit.test.exclude.pattern=**/migration/**/*IT.java' }
        if ($KeepContainer) { $args += '-Dit.keepContainer=true' }
        if ($ResolverDebug) { $args += '-Dit.unomi.resolver.debug=true' }
        if ($SkipUnitTests) { $args += '-Pskip-unit-tests' }
    } else {
        if ($SkipTests) {
            $profiles += '!integration-tests', '!run-tests'
            $args += '-DskipTests'
        } elseif ($SkipUnitTests) {
            $args += '-Pskip-unit-tests'
        }
        if (-not [string]::IsNullOrWhiteSpace($SingleTest)) {
            Write-Status 'warning' 'SingleTest specified but integration tests are not enabled. Use -IntegrationTests.'
        }
    }

    if ($profiles.Count -gt 0) {
        $args += "-P$($profiles -join ',')"
    }
    return $args
}

function Invoke-MavenPhase {
    param(
        [string]$Phase,
        [string[]]$ExtraArgs,
        [switch]$AllowFailure
    )
    $mvnArgs = @($Phase) + $ExtraArgs
    Write-Host "Running: mvn $($mvnArgs -join ' ')"
    & mvn @mvnArgs
    $code = $LASTEXITCODE
    if ($code -ne 0) {
        Write-Status 'error' "Maven $Phase failed"
        if ($AllowFailure) { return $code }
        exit $code
    }
    return 0
}

function Write-ItRunTraceStart {
    param([string[]]$MvnArgs)
    $traceDir = Join-Path $ScriptDir 'itests\target'
    New-Item -ItemType Directory -Force -Path $traceDir | Out-Null
    $traceFile = Join-Path $traceDir 'it-run-trace.properties'
    $engine = if ($UseOpenSearch) { 'opensearch' } else { 'elasticsearch' }
    @(
        '# IT run trace (written by build.ps1 after clean, before install)'
        'trace.phase=started'
        "trace.started=$((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
        "build.invocation=$($script:BuildScriptInvocation -join ' ')"
        "maven.clean.command=mvn clean $($MvnArgs -join ' ')"
        "maven.install.command=mvn install $($MvnArgs -join ' ')"
        "use.opensearch=$UseOpenSearch"
        "search.engine=$engine"
        "search.heap=$SearchHeap"
        "karaf.heap=$KarafHeap"
        "single.test=$SingleTest"
        "it.debug=$ItDebug"
        "it.debug.port=$ItDebugPort"
        "it.debug.suspend=$ItDebugSuspend"
        "skip.migration.tests=$SkipMigrationTests"
        "it.keep.container=$KeepContainer"
        "it.memory.sampler=$($script:ItMemorySampler)"
        "it.memory.interval=$MemoryInterval"
        "maven.debug=$MavenDebug"
        "maven.offline=$Offline"
        "maven.quiet=$MavenQuiet"
        "host=$env:COMPUTERNAME"
    ) | Set-Content -Path $traceFile -Encoding UTF8
}

function Start-ItMemorySampler {
    $sampler = Join-Path $ScriptDir 'itests\sample-it-memory.sh'
    if (-not $script:ItMemorySampler -or -not (Test-Path $sampler)) { return }
    if (-not (Test-CommandExists 'bash')) {
        Write-Status 'warning' 'bash not found; skipping IT memory sampler'
        return
    }
    & bash $sampler start --target-dir (Join-Path $ScriptDir 'itests\target') --interval $MemoryInterval
    if ($LASTEXITCODE -ne 0) { Write-Status 'warning' 'Could not start IT memory sampler' }
}

function Stop-ItMemorySampler {
    $sampler = Join-Path $ScriptDir 'itests\sample-it-memory.sh'
    if (-not (Test-Path $sampler) -or -not (Test-CommandExists 'bash')) { return }
    & bash $sampler stop --target-dir (Join-Path $ScriptDir 'itests\target') 2>$null
}

function Complete-ItRunTrace {
    param([int]$ExitCode)
    $traceFile = Join-Path $ScriptDir 'itests\target\it-run-trace.properties'
    if (-not (Test-Path $traceFile)) { return }
    Add-Content -Path $traceFile -Value "trace.phase=completed"
    Add-Content -Path $traceFile -Value "trace.completed=$((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))"
    Add-Content -Path $traceFile -Value "maven.exit.code=$ExitCode"
}

function Write-ItRunOperatorNote {
    $sampler = Join-Path $ScriptDir 'itests\sample-it-memory.sh'
    if (-not (Test-Path $sampler) -or -not (Test-CommandExists 'bash')) { return }
    & bash $sampler operator-note --target-dir (Join-Path $ScriptDir 'itests\target') 2>$null
}

$script:StartTime = $null
function Start-BuildTimer { $script:StartTime = Get-Date }
function Get-BuildElapsed {
    if (-not $script:StartTime) { return '00:00' }
    $elapsed = [math]::Floor(((Get-Date) - $script:StartTime).TotalSeconds)
    return '{0:d2}:{1:d2}' -f [math]::Floor($elapsed / 60), ($elapsed % 60)
}

if ($LogFile -and -not $LogFileOnly) {
    $logDir = Split-Path -Parent $LogFile
    if ($logDir -and -not (Test-Path $logDir)) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }
    Start-Transcript -Path $LogFile -Append | Out-Null
}

Write-Section 'Apache Unomi Build Script'
$mvnArgs = Get-MavenArgumentList

Write-Host @'

     ____  _    _ _____ _      ____
    |  _ \| |  | |_   _| |    |  _ \
    | |_) | |  | | | | | |    | | | |
    |  _ <| |  | | | | | |    | | | |
    | |_) | |__| |_| |_| |____| |_| |
    |____/ \____/|_____|______|____/

'@
Write-Host 'Building...'
Write-Host 'Estimated time: 3-5 minutes for build, 50-60 minutes with integration tests'
Start-BuildTimer

$totalSteps = if ($Javadoc) { 3 } else { 2 }
$currentStep = 0

Write-BuildProgress (++$currentStep) $totalSteps 'Cleaning previous build...'
Write-Host ''
Invoke-MavenPhase -Phase 'clean' -ExtraArgs $mvnArgs | Out-Null

if ($IntegrationTests) {
    Write-ItRunTraceStart -MvnArgs $mvnArgs
    Start-ItMemorySampler
}

Write-BuildProgress (++$currentStep) $totalSteps 'Compiling and installing artifacts...'
Write-Host ''
$installExit = Invoke-MavenPhase -Phase 'install' -ExtraArgs $mvnArgs -AllowFailure
if ($IntegrationTests) {
    Stop-ItMemorySampler
    Complete-ItRunTrace -ExitCode $installExit
    Write-ItRunOperatorNote
}
if ($installExit -ne 0) { exit $installExit }

Write-Status 'success' "Build completed in $(Get-BuildElapsed)"

if ($Javadoc) {
    Write-Section 'Javadoc Validation'
    Write-BuildProgress (++$currentStep) $totalSteps 'Generating and validating Javadoc...'
    Write-Host ''
    $javadocArgs = @('javadoc:javadoc', '-DskipTests') + $mvnArgs
    Write-Host "Running: mvn $($javadocArgs -join ' ')"
    & mvn @javadocArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Status 'error' 'Javadoc validation failed — fix doclint errors above before pushing'
        exit $LASTEXITCODE
    }
    Write-Status 'success' 'Javadoc validated successfully'
}

if ($Deploy) {
    Write-Section 'Deploying to Apache Karaf'
    $karFile = Join-Path $ScriptDir "kar\target\unomi-kar-$($env:UNOMI_VERSION).kar"
    if (-not (Test-Path $karFile)) {
        Write-Status 'error' "KAR file not found: $karFile"
        exit 1
    }
    Copy-Item -Force $karFile (Join-Path $KarafHome 'deploy\')
    Write-Status 'success' 'KAR package copied successfully'

    $repo = Join-Path $KarafHome 'data\maven\repository'
    if (Test-Path $repo) {
        Get-ChildItem $repo | Remove-Item -Recurse -Force
        Write-Status 'success' 'Karaf Maven repository purged'
    }
    $tmp = Join-Path $KarafHome 'data\tmp'
    if (Test-Path $tmp) {
        Get-ChildItem $tmp | Remove-Item -Recurse -Force
        Write-Status 'success' 'Karaf temporary files purged'
    }
    Write-Status 'success' 'Deployment completed'
}

if (-not $NoKaraf) {
    $packageTarget = Join-Path $ScriptDir 'package\target'
    if (-not (Test-Path $packageTarget)) {
        Write-Status 'error' 'Build directory not found. Did the build complete successfully?'
        exit 1
    }

    Push-Location $packageTarget
    try {
        $archive = Join-Path $packageTarget "unomi-$($env:UNOMI_VERSION).tar.gz"
        if (-not (Test-Path $archive)) {
            Write-Status 'error' "Unomi package not found: $archive"
            exit 1
        }
        & tar -xzf $archive
        if ($LASTEXITCODE -ne 0) {
            Write-Status 'error' "Failed to extract $archive"
            exit $LASTEXITCODE
        }
        $unomiHome = Join-Path $packageTarget "unomi-$($env:UNOMI_VERSION)"

        foreach ($pair in @(
            @{ Src = Join-Path $ScriptDir 'GeoLite2-City.mmdb'; Dest = Join-Path $unomiHome 'etc\GeoLite2-City.mmdb' }
            @{ Src = Join-Path $ScriptDir 'allCountries.zip'; Dest = Join-Path $unomiHome 'etc\allCountries.zip' }
        )) {
            if (Test-Path $pair.Src) {
                Copy-Item -Force $pair.Src $pair.Dest
            }
        }

        $karafOptsParts = @()
        if ($AutoStart) {
            $karafOptsParts += "-Dunomi.autoStart=$AutoStart"
            Write-Status 'info' "Configuring auto-start for $AutoStart"
        }
        if (-not [string]::IsNullOrWhiteSpace($Distribution)) {
            $karafOptsParts += "-Dunomi.distribution=$Distribution"
            Write-Status 'info' "Using Unomi distribution: $Distribution"
        }
        if ($karafOptsParts.Count -gt 0) {
            $env:KARAF_OPTS = $karafOptsParts -join ' '
        }

        if ($Debug) {
            if (-not (Test-PortAvailable $DebugPort)) {
                Write-Status 'error' "Port $DebugPort is already in use"
                exit 1
            }
            $env:KARAF_DEBUG = 'true'
            $env:JAVA_DEBUG_OPTS = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=$($script:KarafDebugSuspend),address=$DebugPort"
            Write-Status 'info' "Debug mode enabled (port: $DebugPort, suspend: $($script:KarafDebugSuspend))"
        }

        $karafBat = Join-Path $unomiHome 'bin\karaf.bat'
        if (-not (Test-Path $karafBat)) {
            Write-Status 'error' 'Karaf executable not found: karaf.bat'
            exit 1
        }
        Push-Location (Join-Path $unomiHome 'bin')
        try {
            & $karafBat
            if ($LASTEXITCODE -ne 0) {
                Write-Status 'error' 'Karaf failed to start'
                exit $LASTEXITCODE
            }
        } finally {
            Pop-Location
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Status 'info' 'Skipping Karaf startup (-NoKaraf specified)'
    if ($AutoStart) {
        Write-Status 'info' "Note: auto-start ($AutoStart) will be applied when Karaf is started manually"
    }
}

Write-Host @'

     ____   ___  _   _ _____ _
    |  _ \ / _ \| \ | | ____| |
    | | | | | | |  \| |  _| | |
    | |_| | |_| | |\  | |___|_|
    |____/ \___/|_| \_|_____(_)

'@
Write-Host 'Operation completed successfully.'

if ($LogFile -and -not $LogFileOnly) {
    Stop-Transcript | Out-Null
}
