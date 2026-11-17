@echo off
setlocal
set ECHIPA=%1
if "%ECHIPA%"=="" set ECHIPA=1

call mvn -q package
if errorlevel 1 exit /b 1

start "n1" java -cp target\classes dfs.StorageNode --id n1 --port 7001 --dir data\n1 --echipa %ECHIPA% --coordinator
start "n2" java -cp target\classes dfs.StorageNode --id n2 --port 7002 --dir data\n2 --echipa %ECHIPA%
start "n3" java -cp target\classes dfs.StorageNode --id n3 --port 7003 --dir data\n3 --echipa %ECHIPA%

echo Nodurile n1, n2, n3 au fost pornite pentru echipa %ECHIPA%.
