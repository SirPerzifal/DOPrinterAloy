[Setup]
AppId=AloyDesktop
AppName=AloyDesktop
AppVersion=1.0
AppPublisher=SGEEDE
DefaultDirName={pf}\AloyDesktop
DefaultGroupName=AloyDesktop
OutputDir=installer
OutputBaseFilename=AloyDesktopSetup
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
SetupIconFile=favicon.ico
[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
[Files]
Source: "DOPrinterAloy.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "runtime\*"; DestDir: "{app}\runtime"; Flags: recursesubdirs ignoreversion
Source: "lib\javafx-sdk-17.0.16\bin\prism_sw.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "lib\javafx-sdk-17.0.16\bin\glass.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "lib\javafx-sdk-17.0.16\bin\javafx_font.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "lib\javafx-sdk-17.0.16\bin\javafx_iio.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "lib\javafx-sdk-17.0.16\bin\prism_d3d.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "favicon.ico"; DestDir: "{app}"; Flags: ignoreversion
[Icons]
Name: "{group}\AloyDesktop"; Filename: "{app}\runtime\bin\java.exe"; Parameters: "-jar ""{app}\DOPrinterAloy.jar"" -Djava.library.path=""{app}"" -Dprism.order=sw"; IconFilename: "{app}\favicon.ico"; WorkingDir: "{app}"
Name: "{commondesktop}\AloyDesktop"; Filename: "{app}\runtime\bin\java.exe"; Parameters: "-jar ""{app}\DOPrinterAloy.jar"" -Djava.library.path=""{app}"" -Dprism.order=sw"; IconFilename: "{app}\favicon.ico"; Tasks: desktopicon; WorkingDir: "{app}"
[Run]
Filename: "{app}\runtime\bin\java.exe"; Parameters: "-jar ""{app}\DOPrinterAloy.jar"" -Djava.library.path=""{app}"" -Dprism.order=sw"; Description: "{cm:LaunchProgram,AloyDesktop}"; Flags: nowait postinstall skipifsilent
[UninstallDelete]
Type: filesandordirs; Name: "{app}"
