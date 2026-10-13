#!/bin/sh
# Gradle wrapper 启动脚本（精简版）
# 需要 gradle/wrapper/gradle-wrapper.jar 存在，见 README 说明。

APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$CLASSPATH" ]; then
  echo "缺少 gradle/wrapper/gradle-wrapper.jar" >&2
  echo "请用 Android Studio 打开本项目（会自动补全），或执行 gradle wrapper 生成。" >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain "$@"
