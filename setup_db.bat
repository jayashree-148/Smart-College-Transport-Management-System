@echo off
rem Creates college_transport_db and loads sql\01..05 using JDBC.  setup_db.bat --reset  rebuilds from scratch.
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b src\*.java > sources.txt
javac -cp "lib/*" -d out @sources.txt || exit /b 1
java -cp "out;lib/*" kalvi.transport.dao.DbSetup db.properties %*
