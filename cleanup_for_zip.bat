@echo off
echo Cleaning up Android project build artifacts to avoid path length issues...

REM Remove root build directory
if exist "build" (
    echo Removing root build directory...
    rmdir /s /q "build" 2>nul
)

REM Remove app build directory
if exist "app\build" (
    echo Removing app build directory...
    rmdir /s /q "app\build" 2>nul
)

REM Remove .gradle directory (contains cache and temporary files)
if exist ".gradle" (
    echo Removing .gradle directory...
    rmdir /s /q ".gradle" 2>nul
)

REM Remove .idea directory (IDE-specific files)
if exist ".idea" (
    echo Removing .idea directory...
    rmdir /s /q ".idea" 2>nul
)

REM Remove .kotlin directory (Kotlin compiler cache)
if exist ".kotlin" (
    echo Removing .kotlin directory...
    rmdir /s /q ".kotlin" 2>nul
)

echo.
echo Cleanup completed! The following directories have been removed:
echo - build/ (root build artifacts)
echo - app/build/ (app build artifacts)
echo - .gradle/ (Gradle cache)
echo - .idea/ (IDE files)
echo - .kotlin/ (Kotlin cache)
echo.
echo Your project can now be safely zipped without path length issues.
echo To rebuild the project, simply open it in Android Studio or run './gradlew build'
echo.
pause
