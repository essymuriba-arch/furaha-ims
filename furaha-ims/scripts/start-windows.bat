@echo off
title Furaha Mini Mart - Inventory & Sales Management System
cd /d %~dp0..

echo =======================================================================
echo   Starting Furaha Inventory & Sales Management System (ISMS)...
echo   Furaha Mini Mart · STU 3101 · KCA University
echo =======================================================================
echo.

REM Optional environment variables (defaults used if not set)
if "%PORT%"=="" set PORT=8080
if "%DB_URL%"=="" set DB_URL=jdbc:mysql://localhost:3306/furaha_ims?useSSL=false^&allowPublicKeyRetrieval=true^&serverTimezone=Africa/Nairobi^&characterEncoding=utf8
if "%DB_USER%"=="" set DB_USER=furaha_ims
if "%DB_PASSWORD%"=="" set DB_PASSWORD=furaha_ims

REM Check if target JAR exists
if not exist target\furaha-ims.jar (
    echo [NOTICE] target\furaha-ims.jar not found.
    echo Building application now with Maven...
    call mvn clean package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Build failed. Please ensure JDK 17 and Maven are installed.
        pause
        exit /b 1
    )
)

echo Opening browser at http://localhost:%PORT%...
start http://localhost:%PORT%

echo Starting Java Server on port %PORT%...
java -jar target\furaha-ims.jar
pause
