@echo off
setlocal
cd /d "%~dp0"
set "JAVA_HOME=D:\develop\Java\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "D:\develop\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" -Dmaven.repo.local="%~dp0.m2-repository" javafx:run
if errorlevel 1 pause
