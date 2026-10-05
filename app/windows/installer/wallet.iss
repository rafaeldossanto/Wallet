; The Windows installer, built with Inno Setup 6 from `flutter build windows --release`.
;
; The Desktop workflow runs it as:
;   ISCC.exe /DAppVersion=0.1.0 windows\installer\wallet.iss
; and the setup lands in build\installer\Wallet-Setup-<version>.exe.
;
; It installs for the current user only (%LOCALAPPDATA%\Programs\Wallet), like Discord, so it
; never asks for administrator rights. Updating is running a newer setup over the old one: the
; app does it by itself with /VERYSILENT /RELAUNCH=... (lib/core/desktop/installer_io.dart).

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef BuildDir
  #define BuildDir "..\..\build\windows\x64\runner\Release"
#endif

[Setup]
; Never change the AppId: it is how a newer setup finds the installed Wallet to update it.
AppId={{3597112C-202D-4C62-B750-535CEC75D3C6}
AppName=Wallet
AppVersion={#AppVersion}
AppVerName=Wallet {#AppVersion}
AppPublisher=Wallet
DefaultDirName={autopf}\Wallet
DisableProgramGroupPage=yes
DisableDirPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\..\build\installer
OutputBaseFilename=Wallet-Setup-{#AppVersion}
SetupIconFile=..\runner\resources\app_icon.ico
UninstallDisplayIcon={app}\Wallet.exe
UninstallDisplayName=Wallet
WizardStyle=modern
Compression=lzma2/max
SolidCompression=yes
; An open Wallet is closed before its files are replaced, and opened again afterwards.
CloseApplications=yes
RestartApplications=yes

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
; The executable, the Flutter engine, the app's data and the Visual C++ runtime the workflow
; copies next to them, so nothing else needs installing.
Source: "{#BuildDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Files of an older version that a newer one no longer ships.
Type: filesandordirs; Name: "{app}\data"

[Icons]
Name: "{autoprograms}\Wallet"; Filename: "{app}\Wallet.exe"
Name: "{autodesktop}\Wallet"; Filename: "{app}\Wallet.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Wallet.exe"; Description: "{cm:LaunchProgram,Wallet}"; Flags: nowait postinstall skipifsilent
; A silent setup is the app updating itself (/RELAUNCH=open|tray|none): it opens again as a
; window, back in the tray, or not at all when the user was quitting.
Filename: "{app}\Wallet.exe"; Flags: nowait; Check: WizardSilent and RelaunchIs('open')
Filename: "{app}\Wallet.exe"; Parameters: "--hidden"; Flags: nowait; Check: WizardSilent and RelaunchIs('tray')

[UninstallDelete]
; Installers the updater downloaded.
Type: filesandordirs; Name: "{localappdata}\Wallet\updates"

[Code]
function RelaunchIs(Value: String): Boolean;
begin
  Result := CompareText(ExpandConstant('{param:RELAUNCH|none}'), Value) = 0;
end;

// "Abrir com o Windows" is written by the app, so the uninstaller removes it by hand.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    RegDeleteValue(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Run', 'Wallet');
end;
