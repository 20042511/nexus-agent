#!/bin/sh
#
# Gradle wrapper script
#
APP_BASE_NAME=`basename "$0"`
APP_HOME=`pwd -P`
for i in "$APP_HOME" "$APP_HOME/.." "$APP_HOME/../.." ; do
  if [ -f "$i/settings.gradle" ] || [ -f "$i/settings.gradle.kts" ]; then
    APP_HOME=`cd "$i" && pwd -P`; break
  fi
done
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi
exec "$JAVACMD"   -classpath "$CLASSPATH"   org.gradle.wrapper.GradleWrapperMain   "$@"
