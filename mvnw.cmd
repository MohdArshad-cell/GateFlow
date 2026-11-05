@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch File for GateFlow
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
set "WRAPPER_JAR=%DIR%.mvn\wrapper\maven-wrapper.jar"

if not exist "%WRAPPER_JAR%" (
    echo Error: Could not find %WRAPPER_JAR%
    exit /b 1
)

java -Dmaven.multiModuleProjectDirectory="%DIR%." -Dfile.encoding=UTF-8 -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
