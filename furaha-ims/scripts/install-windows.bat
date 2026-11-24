@echo off
setlocal enabledelayedexpansion
title Furaha Mini Mart ISMS - Windows Installer
echo =======================================================================
echo   FURAHA INVENTORY & SALES MANAGEMENT SYSTEM (ISMS)
echo   Windows Setup & Database Initialization Script
echo   STU 3101 · KCA University · Mitsanze Lee Kazungu
echo =======================================================================
echo.

REM 1. Verify Java Installation
echo [1/4] Checking Java Runtime (Java 17+)...
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Java not detected on PATH.
    echo Please ensure Java 17 or higher is installed and added to your PATH.
    echo Download: https://adoptium.net/temurin/releases/
) else (
    java -version
    echo [OK] Java detected.
)
echo.

REM 2. Verify Maven Installation
echo [2/4] Checking Apache Maven...
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Maven not detected on PATH.
    echo If building from source, please install Maven or use the prebuilt JAR.
) else (
    echo [OK] Maven detected.
)
echo.

REM 3. MySQL Database Setup
echo [3/4] Initializing MySQL Database (furaha_ims)...
set /p MYSQL_USER=Enter MySQL username (default root): 
if "%MYSQL_USER%"=="" set MYSQL_USER=root
set /p MYSQL_PASS=Enter MySQL password: 

echo Executing db\schema.sql...
mysql -u %MYSQL_USER% -p%MYSQL_PASS% < ..\db\schema.sql
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to execute schema.sql. Please verify your MySQL credentials and service.
    pause
    exit /b 1
)

echo Executing db\seed.sql...
mysql -u %MYSQL_USER% -p%MYSQL_PASS% < ..\db\seed.sql
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to execute seed.sql.
    pause
    exit /b 1
)

REM Grant user permissions
echo Configuring application database user 'furaha_ims'...
mysql -u %MYSQL_USER% -p%MYSQL_PASS% -e "CREATE USER IF NOT EXISTS 'furaha_ims'@'localhost' IDENTIFIED BY 'furaha_ims'; GRANT ALL PRIVILEGES ON furaha_ims.* TO 'furaha_ims'@'localhost'; FLUSH PRIVILEGES;"

echo [OK] MySQL database setup completed successfully.
echo.

REM 4. Build Java Application
echo [4/4] Building Furaha Java IMS Package...
cd /d %~dp0..
if exist pom.xml (
    call mvn clean package -DskipTests
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Maven build failed.
        pause
        exit /b 1
    )
    echo [OK] furaha-ims.jar built successfully in target\ directory.
)

echo.
echo =======================================================================
echo   Installation Complete!
echo   To launch the system:
echo     1. Run: scripts\start-windows.bat
echo     2. Open your browser at: http://localhost:8080
echo.
echo   Default Credentials:
echo     Administrator: admin / admin123
echo     Attendant:     amina / attendant123
echo =======================================================================
pause
