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
Source: "lib\javafx-sdk-17.0.16\bin\*.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "favicon.ico"; DestDir: "{app}"; Flags: ignoreversion
Source: "lib\*.jar"; DestDir: "{app}\lib"; Flags: ignoreversion

[Icons]
Name: "{group}\AloyDesktop"; Filename: "{app}\runtime\bin\javaw.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; IconFilename: "{app}\favicon.ico"; WorkingDir: "{app}"
Name: "{group}\AloyDesktop (Debug)"; Filename: "{app}\runtime\bin\java.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; IconFilename: "{app}\favicon.ico"; WorkingDir: "{app}"

[Run]
Filename: "{app}\runtime\bin\java.exe"; Parameters: "-cp ""{app}\DOPrinterAloy.jar;{app}\lib\*"" -Djava.library.path=""{app}"" -Dprism.order=sw Main"; Description: "{cm:LaunchProgram,AloyDesktop}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"