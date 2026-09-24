@echo off
cd /d %~dp0
start "GPS SERVER - do not close" python server.py
timeout /t 3 >nul
start "NGROK TUNNEL - do not close" ngrok http --url=punctured-detail-expansive.ngrok-free.dev 5000
echo ==========================================
echo   GPS TRACKER STARTED
echo ==========================================
echo.
echo  Map on laptop:  http://localhost:5000
echo  Phone address:  https://punctured-detail-expansive.ngrok-free.dev
echo.
echo  Two black windows opened - KEEP THEM OPEN!
echo  This window can be closed.
echo.
pause
