@echo off
echo Building AloyDesktop with Inno Setup...
echo.

REM Step 1: Create optimized runtime dengan modul lengkap
echo Creating runtime...
"%JAVA_HOME%\bin\jlink" ^
--module-path "lib\javafx-sdk-17.0.16\lib;%JAVA_HOME%\jmods" ^
--add-modules java.base,java.desktop,java.logging,java.net.http,java.sql,java.xml,java.naming,javafx.controls,javafx.fxml,javafx.graphics,javafx.base ^
--output runtime ^
--compress=2 ^
--no-header-files ^
--no-man-pages ^
--strip-debug ^
--vm=server

if %ERRORLEVEL% neq 0 (
    echo Error creating runtime
    pause
    exit /b 1
)

REM Step 2: Create proper Inno Setup script
echo Creating Inno Setup script...
(
echo [Setup]
echo AppId=AloyDesktop
echo AppName=AloyDesktop
echo AppVersion=1.0
echo AppPublisher=SGEEDE
echo DefaultDirName={pf}\AloyDesktop
echo DefaultGroupName=AloyDesktop
echo OutputDir=installer
echo OutputBaseFilename=AloyDesktopSetup
echo Compression=lzma2
echo SolidCompression=yes
echo ArchitecturesAllowed=x64
echo ArchitecturesInstallIn64BitMode=x64
echo SetupIconFile=favicon.ico
echo 
echo [Languages]
echo Name: "english"; MessagesFile: "compiler:Default.isl"
echo 
echo [Tasks]
echo Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
echo 
echo [Files]
echo Source: "DOPrinterAloy.jar"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs ignoreversion
echo Source: "lib\javafx-sdk-17.0.16\bin\*.dll"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "favicon.ico"; DestDir: "{app}"; Flags: ignoreversion
echo Source: "lib\*.jar"; DestDir: "{app}\lib"; Flags: ignoreversion
echo 
echo [Icons]
echo Name: "{group}\AloyDesktop"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; IconFilename: "{app}\favicon.ico"; WorkingDir: "{app}"
echo Name: "{commondesktop}\AloyDesktop"; Filename: "{app}\runtime\bin\java.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; IconFilename: "{app}\favicon.ico"; Tasks: desktopicon; WorkingDir: "{app}"
echo 
echo [Run]
echo Filename: "{app}\runtime\bin\java.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; Description: "{cm:LaunchProgram,AloyDesktop}"; Flags: nowait postinstall skipifsilent
echo 
echo [UninstallDelete]
echo Type: filesandordirs; Name: "{app}"
) > setup.iss

echo.
echo Inno Setup script created!
echo.
echo Download Inno Setup from: https://jrsoftware.org/isdl.php
echo After installation, run: "iscc setup.iss"
echo.
pause