; Builds JordanMind.exe — a small Windows launcher that starts the app with the bundled runtime.
;
; Compiled with makensis on Linux, which produces a genuine Windows PE executable. Pass the output
; directory in with -DOUTDIR=<dir>.
Unicode true
Name "JordanMind"

!ifndef OUTDIR
  !define OUTDIR "."
!endif
OutFile "${OUTDIR}/JordanMind.exe"

RequestExecutionLevel user
SilentInstall silent
Icon "jordanmind.ico"

Section
  SetOutPath "$EXEDIR"
  ; Run the bundled runtime directly so no system-wide Java install is required.
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -Xmx2g -Dcompose.application.resources.dir="$EXEDIR\app\resources" -Dskiko.library.path="$EXEDIR\app" -cp "$EXEDIR\app\*" com.jordanmind.desktop.MainKt'
SectionEnd
