; Jordan Mind — Windows installer.
; Compiled on Linux with NSIS; produces a real Windows .exe installer containing the app,
; a bundled Java runtime, and the native Skiko library for Windows x64.
Unicode true

!ifndef VERSION
  !define VERSION "1.1.0"
!endif
!ifndef WORK
  !define WORK "."
!endif
!ifndef OUTDIR
  !define OUTDIR "."
!endif

!define APPNAME    "Jordan Mind"
!define COMPANY    "Jordan Mind"
!define APPEXE     "JordanMind.exe"
!define REGKEY     "Software\Microsoft\Windows\CurrentVersion\Uninstall\JordanMind"

Name "${APPNAME} ${VERSION}"
OutFile "${OUTDIR}/JordanMind-Setup-${VERSION}-win64${SUFFIX}.exe"
InstallDir "$LOCALAPPDATA\${APPNAME}"
InstallDirRegKey HKCU "Software\JordanMind" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
Icon "jordanmind.ico"
UninstallIcon "jordanmind.ico"

VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName"     "${APPNAME}"
VIAddVersionKey "CompanyName"     "${COMPANY}"
VIAddVersionKey "FileDescription" "${APPNAME} installer"
VIAddVersionKey "FileVersion"     "${VERSION}"
VIAddVersionKey "ProductVersion"  "${VERSION}"
VIAddVersionKey "LegalCopyright"  "Apache-2.0"

!include "MUI2.nsh"
!define MUI_ICON "jordanmind.ico"
!define MUI_UNICON "jordanmind.ico"
!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APPEXE}"
!define MUI_FINISHPAGE_RUN_TEXT "Start ${APPNAME}"
!define MUI_FINISHPAGE_SHOWREADME ""
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Create a desktop shortcut"
!define MUI_FINISHPAGE_SHOWREADME_FUNCTION CreateDesktopShortcut
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

Function CreateDesktopShortcut
  CreateShortcut "$DESKTOP\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\jordanmind.ico" 0
FunctionEnd

Function .onInit
  ; Refuse to install over a running copy, which would leave half-written jars behind.
  FindWindow $0 "SunAwtFrame" "${APPNAME}"
  StrCmp $0 0 done
    MessageBox MB_OK|MB_ICONEXCLAMATION "${APPNAME} is running. Close it and run the installer again."
    Abort
  done:
FunctionEnd

Section "Install"
  SetOutPath "$INSTDIR"
  SetOverwrite on

  ; The launcher, icon, and the entire application tree including the bundled runtime.
  File "${WORK}/JordanMind.exe"
  File "jordanmind.ico"
  File /r "${WORK}\stage\Jordan Mind\app"
  File /r "${WORK}\stage\Jordan Mind\runtime"

  WriteRegStr HKCU "Software\JordanMind" "InstallDir" "$INSTDIR"

  ; Add/Remove Programs entry, so it can be uninstalled the normal way.
  WriteRegStr   HKCU "${REGKEY}" "DisplayName"     "${APPNAME}"
  WriteRegStr   HKCU "${REGKEY}" "DisplayVersion"  "${VERSION}"
  WriteRegStr   HKCU "${REGKEY}" "Publisher"       "${COMPANY}"
  WriteRegStr   HKCU "${REGKEY}" "DisplayIcon"     "$INSTDIR\jordanmind.ico"
  WriteRegStr   HKCU "${REGKEY}" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr   HKCU "${REGKEY}" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKCU "${REGKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${REGKEY}" "NoRepair" 1
  ; Rough installed size in KB, as Windows expects.
  WriteRegDWORD HKCU "${REGKEY}" "EstimatedSize" 240640

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\${APPNAME}"
  CreateShortcut "$SMPROGRAMS\${APPNAME}\${APPNAME}.lnk" "$INSTDIR\${APPEXE}" "" "$INSTDIR\jordanmind.ico" 0
  CreateShortcut "$SMPROGRAMS\${APPNAME}\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Uninstall"
  Delete "$INSTDIR\JordanMind.exe"
  Delete "$INSTDIR\jordanmind.ico"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  RMDir "$INSTDIR"

  Delete "$DESKTOP\${APPNAME}.lnk"
  RMDir /r "$SMPROGRAMS\${APPNAME}"

  DeleteRegKey HKCU "${REGKEY}"
  DeleteRegKey HKCU "Software\JordanMind"

  ; Leave the user's own data alone — conversations, reminders and drafts live here.
  MessageBox MB_YESNO|MB_ICONQUESTION "Also delete your saved notes, reminders and conversations?" IDNO keep
    RMDir /r "$PROFILE\.jordanmind"
  keep:
SectionEnd
