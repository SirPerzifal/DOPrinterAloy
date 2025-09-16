@echo off
echo Setting up JAVA_HOME...

REM Find Java installation
echo Searching for Java installations...

REM Check common Java locations
if exist "C:\Program Files\Java\jdk-17" (
    set "JAVA_HOME_PATH=C:\Program Files\Java\jdk-17"
    echo Found JDK 17 at: C:\Program Files\Java\jdk-17
    goto :found
)

if exist "C:\Program Files\OpenJDK\jdk-11.0.8.10-hotspot" (
    set "JAVA_HOME_PATH=C:\Program Files\OpenJDK\jdk-11.0.8.10-hotspot"
    echo Found OpenJDK 11 at: C:\Program Files\OpenJDK\jdk-11.0.8.10-hotspot
    goto :found
)

if exist "C:\Program Files\Java\jdk-11" (
    set "JAVA_HOME_PATH=C:\Program Files\Java\jdk-11"
    echo Found JDK 11 at: C:\Program Files\Java\jdk-11
    goto :found
)

echo ERROR: No JDK found in standard locations.
echo Please install JDK 11+ or set JAVA_HOME manually.
pause
exit /b 1

:found
echo.
echo Setting JAVA_HOME to: %JAVA_HOME_PATH%

REM Set for current session
set "JAVA_HOME=%JAVA_HOME_PATH%"

REM Set permanently for user
setx JAVA_HOME "%JAVA_HOME_PATH%"

REM Verify
echo.
echo Verification:
echo JAVA_HOME = %JAVA_HOME%
"%JAVA_HOME%\bin\java" -version

REM Check if jmods exist
if exist "%JAVA_HOME%\jmods\java.base.jmod" (
    echo ✓ JDK with jmods found - ready for jlink!
) else (
    echo ✗ jmods not found - this might be a JRE, not JDK
    echo You need a full JDK installation for jpackage to work.
)

echo.
echo JAVA_HOME has been set. Please restart your command prompt and try again.
pause