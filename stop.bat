@echo off
rem Stops any OLD CampusTransit server that is still running (it keeps its port busy and shows the old page).
echo Looking for Java programs listening on ports 8080-8110 and 9090 ...
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr LISTENING ^| findstr /R ":80[89][0-9] :81[01][0-9] :9090"') do (
  tasklist /FI "PID eq %%a" 2^>nul | find /I "java" >nul && (
    echo Stopping Java process %%a
    taskkill /PID %%a /F >nul
    set FOUND=1
  )
)
if "%FOUND%"=="0" echo No old CampusTransit server found.
pause
