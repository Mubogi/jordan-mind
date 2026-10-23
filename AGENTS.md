# AGENTS.md

Repository knowledge for future sessions. Everything here was verified in this environment.

## What this project is

Jordan Mind — an offline AI agent that runs on an Android phone and on a desktop. Three Gradle
modules:

| Module | What it is |
| --- | --- |
| `:app` | Android app (Compose, Material 3, dark-only amber theme) |
| `:core` | Pure Kotlin/JVM agent state, no Android APIs, shared by both |
| `:desktop` | Compose Desktop app + headless `--selftest` mode |

`:core` is deliberately free of Android dependencies so the desktop app and the JVM tests can use
the same logic. Anything both targets need belongs there, not duplicated.

## Toolchain (this sandbox)

There is no JDK 17 installed, so **do not pin `jvmToolchain(17)`** — Gradle cannot resolve it and
fails with "Cannot find a Java installation". The modules compile with the JDK 21 that exists.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # 21.0.12.1
export ANDROID_HOME=/workspace/android-sdk
export PATH=$JAVA_HOME/bin:$PATH
GRADLE=/workspace/tools/gradle-8.14.6/bin/gradle      # or ./gradlew
```

Builds are slow (2–3 min) and run in the background with `nohup ... &`, then poll the log.

## Commands

```bash
# all tests (68 tests: core, planner, time parsing, UI smoke, model)
./gradlew testDebugUnitTest :core:test :desktop:test

# release APK, no model bundled
./gradlew assembleRelease

# release APK with a model baked into assets/models/qwen-bundled.task
./gradlew assembleRelease -PbundleModel=/path/to/model.task

# Android lint (fails the build on errors — keep it clean)
./gradlew lintRelease

# desktop: runnable image, .deb, Windows installers
./gradlew :desktop:createReleaseDistributable :desktop:packageReleaseDeb
./gradlew :desktop:packageReleaseExe :desktop:packageReleaseMsi   # Windows host only

# verify a packaged desktop binary end to end, no display needed
"desktop/build/compose/binaries/main-release/app/Jordan Mind/bin/Jordan Mind" --selftest
```

## Screenshots

There is no emulator here (no `/dev/kvm`), so the two targets are captured differently.

- Android: `./gradlew :app:testDebugUnitTest --tests "com.jordanmind.agent.ScreenshotTest"`.
  Robolectric renders the real Compose UI and writes PNGs to `docs/screenshots/android/`. It uses
  `@GraphicsMode(NATIVE)` and a `w411dp-h891dp-xxhdpi` qualifier, so the images are phone-sized.
  The tab rail is a `LazyRow`, so off-screen chips are not composed; scroll it by
  `TabRailTestTag` before clicking a trailing tab.
- Desktop: run the app under `Xvfb :99`, then screenshot the largest window matching `Jordan Mind`.
  The app also registers 10x10 and 1x1 helper windows, so pick the biggest, not the first.
  Compare with `-colorspace Gray -format "%[fx:mean*255]"` to confirm which theme is on screen —
  the toggle's label names the theme you would switch *to*, so it reads opposite to the pixels.

## Things that will bite you

- **Bundling a model needs a task dependency on lint.** `stageBundledModel` writes into
  `app/build/bundled-assets`, which lint reads. Without an explicit `dependsOn`, Gradle fails with
  an implicit-dependency error that never mentions the model. The fix matches task names
  case-insensitively (`lintVitalAnalyzeRelease` is lowercase `lint`).
- **Model assets must stay uncompressed.** MediaPipe mmaps the `.task` file, so `noCompress` must
  include `task`/`litertlm`/`bin`/`gguf`. Verify with a zipfile check: the entry should be `STORED`
  and byte-identical to the source.
- **Compose Desktop ProGuard is disabled.** The Compose plugin bundles ProGuard 7.2.2, which cannot
  read JDK 21 class files (`proguardReleaseJars` fails with an `IOException`). See the
  `buildTypes.release.proguard` block in `desktop/build.gradle.kts`.
- **`jpackage` needs `fakeroot`** for `.deb` output. `sudo apt-get install -y fakeroot`.
- **Do not read `StateFlow.value` in composition.** Lint fails the build with
  `StateFlowValueCalledInComposition`, and the real consequence is a button that never
  re-enables. Use `collectAsStateWithLifecycle()`.
- **`libs.versions.toml` needs a blank line before `[plugins]`** or the TOML parser merges the
  last library entry into the section header.
- **Plugin versions must be declared once in the root `build.gradle.kts`** with `apply false`, then
  applied without a version in modules. Applying a versioned plugin in two modules fails with
  "already on the classpath with an unknown version".

## Agent design

Instruction handling is two-stage, cheapest first (`Planner`):

1. Memory: an exact or ≥0.75-similarity match on a stored pattern. No model call.
2. Templates: regex rules for common phrasings. No model call.
3. Model: asked for strict JSON. The resulting mapping is written back to memory.

This ordering is what makes the agent work with **no model loaded at all**, and why learned
instructions get faster over time. "Learning" is template memorisation, not weight training —
do not describe it as training the model.

Slot filling compiles the stored template into a regex (`AgentState.extractSlots`). Do not go back
to guessing word boundaries: it silently mangles multi-slot patterns like
`remind me to {task} at {time}`.

## Honest limits (do not claim otherwise)

- The app declares **no `INTERNET` permission** and there is no network code. Anything that needs
  the network cannot work.
- SMS opens a pre-filled composer; it never sends silently.
- Scheduled posts fire a notification. An offline app cannot publish to a blog.
- Desktop actions refuse paths outside `user.home` and non-`http(s)` links.
- The bundled model is Qwen2.5-0.5B-Instruct q8 (~547 MB). Qwen2.5-1.5B q8 (~1.6 GB) is a
  drop-in upgrade via `-PbundleModel`. Gemma3-1B is gated and returns HTTP 401 without a signed
  license, so it cannot be fetched unattended.

## Verified state

- 68 unit tests pass; `lintRelease` is clean.
- Release APK: 77 MB (no model) / 624 MB (bundled), signed, `apksigner verify` OK.
- Desktop `.deb` installs and the installed binary passes all 19 `--selftest` checks.
- Windows installer: built on Linux by `packaging/windows/build-windows-installer.sh`, and
  independently by the `windows-latest` CI job via `jpackage`.

## Packaging a Windows installer from Linux

`jpackage` links a native launcher and a runtime image for the target OS, so it genuinely cannot
cross-compile. That does not block Windows packaging: assemble the distribution by hand and wrap
it in NSIS, which does run on Linux.

`packaging/windows/build-windows-installer.sh` does exactly that:

1. `:desktop:createReleaseDistributable` for the jars.
2. Swap `skiko-awt-runtime-linux-x64` for `skiko-awt-runtime-windows-x64` from Maven Central.
   Skip this and the app dies on startup with an `UnsatisfiedLinkError`.
3. Drop in a Temurin 21 Windows JRE so the user needs no system Java.
4. Compile `launcher.nsi` into `JordanMind.exe`, then `installer.nsi` into the setup executable.

Gotchas:

- Skiko ships the `.dll` inside the runtime jar, so extract it as well as copying the jar.
- The launcher must set `-Dskiko.library.path` to the directory holding the DLL.
- Verify without Windows: `7z x` the installer and run the payload's `java.exe` under Wine with
  `--selftest`. Wine 10 runs the Temurin JRE and the app's headless self-test fine.
- `BUNDLE_MODEL=1` stages a Qwen2.5-0.5B GGUF and the llama.cpp Windows build into `app/models/`,
  producing a `-bundled` installer that works offline with nothing installed. `LocalModel` looks
  there first, then `~/.jordanmind/models`. Downloads are cached in `packaging/windows/.cache/`
  so repeat builds skip ~1 GB of fetching.
- Wine limit: `llama-cli.exe` under Wine was reported as never finishing generation. The real
  cause was an app bug, not Wine: `LocalModel` did not pass `-st`, so `llama-cli` stayed in
  interactive mode waiting on stdin and the app blocked until the timeout. That is fixed
  (`-st` plus a closed stdin) and covered by `LocalModelTest`. Re-verify end to end under Wine
  before claiming the bundled model generates text on Windows.
- `apt-get install wine64` removed the system JDK in this environment once. If `java` vanishes,
  reinstall `openjdk-21-jdk-headless` (the package needs a fresh `apt-get update` first).
- The container image is rebuilt between sessions and drops tools that a previous session relied
  on. Seen so far: `openjdk-21-jdk-headless`, `imagemagick`, `unzip`. Reinstall before assuming a
  script failure is a code failure.
- `llama-cli` lives at `/workspace/llm/llama-b11829/llama-cli` (not the `/workspace/lim/...` path
  recorded earlier). Set `JORDANMIND_LLAMA` to it when testing the desktop model path, and put a
  GGUF in `~/.jordanmind/models/`.

## First-launch model loading

A chat UI whose send button is gated on `LlmState.Ready` is dead on arrival if nothing loads a
model. The 1.0 APK shipped exactly that: `AppViewModel.init` only listed models on disk, and the
lite APK contains no model to list. `autoLoadModel()` now loads an installed model or unpacks the
bundled one, and `ModelAutoLoadTest` fails if that regresses. When changing startup, run that test
with the fix reverted to confirm it still bites.

## .gitignore trap

Build-output rules must be anchored. An unanchored `build/` also matches the source package
`app/src/main/java/com/jordanmind/agent/build/`, which silently dropped `AppProjectBuilder.kt`
from the repository — local builds passed because the file was on disk, and only CI (a fresh
clone) failed. Use `/build/`, `/app/build/`, and so on. Run
`git ls-files --others --exclude-standard` before trusting a push.
