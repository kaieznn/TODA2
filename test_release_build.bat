@echo off

REM Change to the directory where this batch file is located
cd /d "%~dp0"

echo Testing TODA Release APK Build (passenger/driver/admin)...
echo.
echo Current directory: %CD%
echo.

REM Check if gradlew exists
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found in current directory!
    echo Please make sure you're running this from the project root directory.
    echo Current directory: %CD%
    echo.
    echo Press any key to exit...
    pause >nul
    exit /b 1
)

echo Attempting to unlock build folder...
REM Kill any running gradle/java processes to avoid file locks
taskkill /f /im java.exe 2>nul
taskkill /f /im gradle.exe 2>nul
timeout /t 2 /nobreak >nul

REM Try to manually delete the build folder if it exists
if exist "build" (
    echo Removing existing build folder...
    rmdir /s /q "build" 2>nul
)
if exist "app\build" (
    echo Removing existing app build folder...
    rmdir /s /q "app\build" 2>nul
)

echo Waiting for file handles to release...
timeout /t 3 /nobreak >nul

echo Starting Gradle daemon cleanup...
call gradlew.bat --stop
timeout /t 2 /nobreak >nul

setlocal enabledelayedexpansion

REM Do a single clean before all builds
echo Performing initial clean...
call gradlew.bat clean --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo Initial clean failed. Check output above.
    exit /b 1
)

REM Build Passenger release
echo.
echo Building Passenger release APK...
call gradlew.bat assemblePassengerRelease --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED for passenger. Check the Gradle output above.
    exit /b 1
)
set APK_PASSENGER=app\build\outputs\apk\passenger\release\app-passenger-release.apk
echo Checking for APK at %APK_PASSENGER%
if exist "%APK_PASSENGER%" (
    echo ✓ SUCCESS: %APK_PASSENGER% created!
) else (
    echo ✗ WARNING: Expected APK not found at %APK_PASSENGER%
    dir "app\build\outputs\apk\passenger\release\" || dir "app\build\outputs\apk\passenger\"
)

REM Build Driver release
echo.
echo Building Driver release APK...
call gradlew.bat assembleDriverRelease --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED for driver. Check the Gradle output above.
    exit /b 1
)
set APK_DRIVER=app\build\outputs\apk\driver\release\app-driver-release.apk
echo Checking for APK at %APK_DRIVER%
if exist "%APK_DRIVER%" (
    echo ✓ SUCCESS: %APK_DRIVER% created!
) else (
    echo ✗ WARNING: Expected APK not found at %APK_DRIVER%
    dir "app\build\outputs\apk\driver\release\" || dir "app\build\outputs\apk\driver\"
)

REM Build Admin release
echo.
echo Building Admin release APK...
call gradlew.bat assembleAdminRelease --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED for admin. Check the Gradle output above.
    exit /b 1
)
set APK_ADMIN=app\build\outputs\apk\admin\release\app-admin-release.apk
echo Checking for APK at %APK_ADMIN%
if exist "%APK_ADMIN%" (
    echo ✓ SUCCESS: %APK_ADMIN% created!
) else (
    echo ✗ WARNING: Expected APK not found at %APK_ADMIN%
    dir "app\build\outputs\apk\admin\release\" || dir "app\build\outputs\apk\admin\"
)

echo.
echo ========================================
echo ALL REQUESTED BUILDS COMPLETED
echo ========================================
echo.
echo Press any key to exit...
pause >nul
endlocal
