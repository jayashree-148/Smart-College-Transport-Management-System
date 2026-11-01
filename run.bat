@echo off
title CampusTransit
rem Starts the Java backend + dashboard and opens it in your browser (loading page -> sign in).
rem   run.bat            MySQL (downloads the driver + builds the database the first time; falls back to demo files and says why)
rem   run.bat --demo     demo files only, no MySQL needed
rem   run.bat --no-browser
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo Java JDK 17 or newer is required ^(javac not found^). Install it from https://adoptium.net and run again.
  pause
  exit /b 1
)
if exist out rmdir /s /q out
mkdir out
if not exist lib mkdir lib
dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d out @sources.txt
if errorlevel 1 (
  echo.
  echo Compile failed. You need JDK 17 or newer. Your Java is:
  java -version
  pause
  exit /b 1
)
del sources.txt
java -cp "out;lib/*" kalvi.transport.web.WebApp %*
pause
