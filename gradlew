#!/bin/sh
# Minimal Gradle wrapper launcher. Generate gradle-wrapper.jar with Android Studio or `gradle wrapper` if absent.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "Missing gradle/wrapper/gradle-wrapper.jar. Open the project in Android Studio and run Gradle sync, or run: gradle wrapper --gradle-version 8.13" >&2
  exit 1
fi
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
