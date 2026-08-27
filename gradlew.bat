@rem BridgeKit Gradle launcher. Generated-compatible wrapper script for Windows.
@if "%DEBUG%"=="" @echo off
@setlocal
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" goto noJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute
:noJavaHome
echo ERROR: JAVA_HOME is not set or points to an invalid JDK. 1>&2
exit /b 1
:execute
"%JAVA_EXE%" -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
@endlocal
