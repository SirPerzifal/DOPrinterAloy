@echo off
echo Creating complete JavaFX application with runtime...

REM Auto-detect JAVA_HOME if not set
if "%JAVA_HOME%"=="" (
    echo JAVA_HOME not set, attempting auto-detection...
    
    if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
        echo Using JDK 17: %JAVA_HOME%
    ) else if exist "C:\Program Files\OpenJDK\jdk-11.0.8.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\OpenJDK\jdk-11.0.8.10-hotspot"
        echo Using OpenJDK 11: %JAVA_HOME%
    ) else (
        echo ERROR: Cannot find JDK. Please run setup-java-home.bat first
        pause
        exit /b 1
    )
)

REM Verify JavaFX SDK exists
if not exist "lib\javafx-sdk-17.0.16\bin\prism_sw.dll" (
    echo ERROR: JavaFX SDK not found or incomplete
    echo Please download JavaFX 17.0.16 SDK and extract to lib\javafx-sdk-17.0.16\
    pause
    exit /b 1
)

REM Clean up previous builds
rmdir /s /q runtime 2>nul
rmdir /s /q installer 2>nul

REM Step 1: Create custom runtime with jlink
echo Step 1: Creating custom runtime...
"%JAVA_HOME%\bin\jlink" ^
--module-path "lib\javafx-sdk-17.0.16\lib;%JAVA_HOME%\jmods" ^
--add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,java.base,java.desktop,java.logging ^
--output runtime ^
--compress=2 ^
--no-header-files ^
--no-man-pages ^
--bind-services

if %ERRORLEVEL% neq 0 (
    echo Error creating runtime.
    pause
    exit /b 1
)

REM Step 2: Package with custom runtime and include JavaFX native DLLs
echo Step 2: Creating installer...
"%JAVA_HOME%\bin\jpackage" ^
--input . ^
--name "AloyDesktop" ^
--main-jar DOPrinterAloy.jar ^
--main-class Main ^
--runtime-image runtime ^
--resource-dir "lib\javafx-sdk-17.0.16\bin" ^
--type exe ^
--dest installer ^
--app-version 1.0 ^
--description "AloyDesktop Application" ^
--vendor "SGEEDE" ^
--win-console ^
--win-dir-chooser ^
--win-menu ^
--win-shortcut ^
--java-options "-Dprism.order=sw" ^
--java-options "-Dprism.verbose=true" ^
--java-options "-Djava.library.path=./app"

if %ERRORLEVEL% neq 0 (
    echo Error creating installer.
    pause
    exit /b 1
)

REM Step 3: Verify DLLs are included
echo Step 3: Verifying DLL files...
if exist "installer\AloyDesktop\app\prism_sw.dll" (
    echo SUCCESS: prism_sw.dll found!
) else (
    echo WARNING: prism_sw.dll not found, manually copying...
    xcopy /Y "lib\javafx-sdk-17.0.16\bin\*.dll" "installer\AloyDesktop\app\"
)

echo.
echo SUCCESS: Installer created in 'installer' folder!
echo Press any key to exit...
pause > nul