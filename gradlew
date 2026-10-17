#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
DEFAULT_JVM_OPTS=""
CLASSPATH=""

# Determine JAVA_HOME
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Fallback to system gradle if wrapper jar is missing
if [ ! -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    if which gradle >/dev/null 2>&1; then
        exec gradle "$@"
    fi
fi

exec gradle "$@"
