#!/bin/sh
#
# Gradle startup script for UN*X
#
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD="maximum"
WARN () { echo "$*"; }
die () { echo; echo "$*"; echo; exit 1; }
OS="`uname`"
case "$OS" in
  Cygwin* ) cygwin=true ;;
  Darwin* ) darwin=true ;;
  MSYS* | MINGW* ) msys=true ;;
  NONSTOP* ) nonstop=true ;;
esac
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
case "$OS" in
  Cygwin* | MSYS* | MINGW* )
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    ;;
esac
set_term_title() { true; }
APP_HOME=`pwd -P`
for i in "$APP_HOME" "$APP_HOME/.." "$APP_HOME/../.." "$APP_HOME/../../.."; do
  if [ -f "$i/settings.gradle" ] || [ -f "$i/settings.gradle.kts" ]; then
    APP_HOME=`cd "$i" && pwd -P`
    break
  fi
done
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and 'java' could not be found in your PATH."
fi

exec "$JAVACMD" \
  $DEFAULT_JVM_OPTS \
  $JAVA_OPTS \
  $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
