@echo off
rem
rem
rem    Licensed to the Apache Software Foundation (ASF) under one or more
rem    contributor license agreements.  See the NOTICE file distributed with
rem    this work for additional information regarding copyright ownership.
rem    The ASF licenses this file to You under the Apache License, Version 2.0
rem    (the "License"); you may not use this file except in compliance with
rem    the License.  You may obtain a copy of the License at
rem
rem       http://www.apache.org/licenses/LICENSE-2.0
rem
rem    Unless required by applicable law or agreed to in writing, software
rem    distributed under the License is distributed on an "AS IS" BASIS,
rem    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem    See the License for the specific language governing permissions and
rem    limitations under the License.
rem

rem
rem handle specific scripts; the SCRIPT_NAME is exactly the name of the Karaf
rem script; for example karaf.bat, start.bat, stop.bat, admin.bat, client.bat, ...
rem
rem if "%KARAF_SCRIPT%" == "SCRIPT_NAME" (
rem   Actions go here...
rem )

rem
rem general settings which should be applied for all scripts go here; please keep
rem in mind that it is possible that scripts might be executed more than once, e.g.
rem in example of the start script where the start script is executed first and the
rem karaf script afterwards.
rem

rem
rem The following section shows the possible configuration options for the default 
rem karaf scripts
rem
rem Window name of the windows console
rem SET KARAF_TITLE
rem Location of Java installation
rem SET JAVA_HOME
rem Minimum memory for the JVM
rem SET JAVA_MIN_MEM
rem Maximum memory for the JVM
REM SET JAVA_MAX_MEM
rem Minimum perm memory for the JVM
rem SET JAVA_PERM_MEM
rem Maximum perm memory for the JVM
rem SET JAVA_MAX_PERM_MEM
rem Karaf home folder
rem SET KARAF_HOME
rem Karaf data folder
rem SET KARAF_DATA
rem Karaf base folder
rem SET KARAF_BASE
rem Karaf etc folder
rem SET KARAF_ETC
rem Additional available Karaf options
rem SET KARAF_OPTS
rem Enable debug mode
rem SET KARAF_DEBUG

set MY_DIRNAME=%~dp0%
set MY_KARAF_HOME=%DIRNAME%..

rem Warn early when starting without admin/health passwords (no known defaults are shipped).
rem
rem An unset password does NOT mean "no account": it expands to the empty string, which Karaf's
rem PropertiesLoginModule accepts as a valid password for the admin account.
rem
rem KNOWN LIMITATION: karaf.bat invokes this file with "call" and does not test errorlevel
rem afterwards, so the "exit /b 1" below returns from this script but does NOT stop the launcher.
rem The error message is printed and startup continues. Windows operators must therefore act on the
rem message; there is no way to fail closed from here without also killing the operator's console
rem (plain "exit 1" would terminate the whole cmd.exe session, including an interactive prompt).
rem
rem karaf.bat sets KARAF_SCRIPT with the quotes included in the value (SET KARAF_SCRIPT="karaf.bat"),
rem so strip them before comparing - otherwise every comparison below silently fails to match.
set _UNOMI_KARAF_SCRIPT=%KARAF_SCRIPT:"=%
if /I "%_UNOMI_KARAF_SCRIPT%"=="karaf.bat" goto checkPasswords
if /I "%_UNOMI_KARAF_SCRIPT%"=="start.bat" goto checkPasswords
if /I "%_UNOMI_KARAF_SCRIPT%"=="karaf" goto checkPasswords
if /I "%_UNOMI_KARAF_SCRIPT%"=="start" goto checkPasswords
goto afterPasswordChecks

:checkPasswords
if not "%UNOMI_ROOT_PASSWORD%"=="" goto afterRootPasswordCheck
if /I "%UNOMI_SKIP_ROOT_PASSWORD_CHECK%"=="true" goto afterRootPasswordCheck
echo ERROR: UNOMI_ROOT_PASSWORD is not set.
echo.
echo Apache Unomi does not ship a known default admin password, and an unset value becomes
echo an EMPTY password that still authenticates. Set one before starting, for example:
echo.
echo   set UNOMI_ROOT_PASSWORD=choose-a-strong-password
echo   set UNOMI_HEALTHCHECK_PASSWORD=choose-a-strong-health-password
echo   bin\karaf.bat
echo.
echo Or set org.apache.unomi.security.root.password in etc\custom.system.properties
echo and set UNOMI_SKIP_ROOT_PASSWORD_CHECK=true.
echo.
echo WARNING: startup continues anyway on Windows - see the note at the top of this file.
set _UNOMI_KARAF_SCRIPT=
exit /b 1

:afterRootPasswordCheck
if not "%UNOMI_HEALTHCHECK_PASSWORD%"=="" goto afterPasswordChecks
if /I "%UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK%"=="true" goto afterPasswordChecks
echo ERROR: UNOMI_HEALTHCHECK_PASSWORD is not set.
echo.
echo Apache Unomi does not ship a known default health-check password, and an unset value
echo becomes an EMPTY password that still authenticates. Set one before starting, for example:
echo.
echo   set UNOMI_ROOT_PASSWORD=choose-a-strong-password
echo   set UNOMI_HEALTHCHECK_PASSWORD=choose-a-strong-health-password
echo   bin\karaf.bat
echo.
echo Or set org.apache.unomi.healthcheck.password in etc\custom.system.properties
echo and set UNOMI_SKIP_HEALTHCHECK_PASSWORD_CHECK=true.
echo.
echo WARNING: startup continues anyway on Windows - see the note at the top of this file.
set _UNOMI_KARAF_SCRIPT=
exit /b 1

:afterPasswordChecks
set _UNOMI_KARAF_SCRIPT=
