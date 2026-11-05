#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Shell Script for GateFlow
# ----------------------------------------------------------------------------
DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$DIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Error: Could not find $WRAPPER_JAR"
    exit 1
fi

exec java -Dmaven.multiModuleProjectDirectory="$DIR" -Dfile.encoding=UTF-8 -cp "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
