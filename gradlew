#!/bin/sh
APP_HOME=`pwd -P`
for i in "$APP_HOME" "$APP_HOME/.." "$APP_HOME/../.."; do
  if [ -f "$i/settings.gradle" ] || [ -f "$i/settings.gradle.kts" ]; then
    APP_HOME=`cd "$i" && pwd -P`; break
  fi
done
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
