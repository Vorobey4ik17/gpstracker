@echo off
chcp 65001 >nul
cd /d %~dp0
echo ============================================
echo   GPS Tracker - запуск системы
echo ============================================
echo.
start "GPS SERVER (не закрывать!)" python server.py
timeout /t 2 >nul
start "NGROK TUNNEL (не закрывать!)" ngrok http --url=punctured-detail-expansive.ngrok-free.dev 5000
echo.
echo  Карта на ноутбуке:   http://localhost:5000
echo  Адрес для телефонов: https://punctured-detail-expansive.ngrok-free.dev
echo.
echo  Два новых окна (SERVER и TUNNEL) должны остаться открытыми!
echo  Это окно можно закрыть.
echo.
pause
