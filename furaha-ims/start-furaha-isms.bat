@echo off
title Furaha Inventory & Sales Management System (ISMS)
cd /d %~dp0

echo =======================================================================
echo   FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
echo   Furaha Mini Mart · STU 3101 · KCA University
echo =======================================================================
echo.
echo [1/2] Opening Web Browser at http://localhost:8080 ...
start http://localhost:8080
echo.
echo [2/2] Launching Furaha Application Server Engine...
powershell -ExecutionPolicy Bypass -File scripts\server.ps1 -Port 8080
pause
