@echo off
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d out @sources.txt || exit /b 1
java -cp "out;lib/*" kalvi.transport.TransportApp %*
