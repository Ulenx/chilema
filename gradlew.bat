@rem Gradle wrapper 启动脚本（精简版）
@rem 需要 gradle\wrapper\gradle-wrapper.jar 存在，见 README 说明。
@echo off
setlocal

set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo 缺少 gradle\wrapper\gradle-wrapper.jar 1>&2
  echo 请用 Android Studio 打开本项目（会自动补全），或执行 gradle wrapper 生成。 1>&2
  exit /b 1
)

if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)

"%JAVACMD%" -Xmx64m -Xms64m -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
