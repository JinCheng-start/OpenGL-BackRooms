@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.
@REM
@REM Licensed under the Apache License, Version 2.0;
@REM you may not use this file except in compliance with the License.

@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%~dp0"
set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"

set "MVNW_CMD="
set "MVNW_REPOURL="
set "MVNW_VERBOSE="

:strip
if not "%MAVEN_PROJECTBASEDIR:~-1%"=="\" goto endstrip
set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
goto strip
:endstrip

if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
    echo Downloading Maven Wrapper...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar'"
)

set "JAVA_HOME_SAVED=%JAVA_HOME%"
set "JAVA_HOME=C:\Program Files\Zulu\zulu-21"

for /f "tokens=*" %%i in ('"%JAVA_HOME%\bin\java" -cp "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*') do set MVNW_CMD=%%i

set "JAVA_HOME=%JAVA_HOME_SAVED%"
if not "%MVNW_CMD%"=="" goto exec

set "JAVA_HOME=%JAVA_HOME_SAVED%"
"%JAVA_HOME%\bin\java" -cp "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*

:exec
%MVNW_CMD%

endlocal
