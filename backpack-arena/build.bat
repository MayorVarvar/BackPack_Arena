@echo off
rem Builds the plugin. Gradle on Windows fails to run tests when the user home path
rem contains non-ASCII characters, so the Gradle cache lives in C:\Users\Public\gradle-home.
set GRADLE_USER_HOME=C:\Users\Public\gradle-home
call "%~dp0gradlew.bat" build %*
if %ERRORLEVEL% equ 0 echo. & echo Done: dist\BackpackArena-0.1.0.jar
