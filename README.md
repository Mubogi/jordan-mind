# Jordan Mind

An offline AI agent for Android and desktop. Everything — the language model, OCR, document
writing, search — runs on the device. The app declares no network permission, so it cannot reach
the internet even if it wanted to.

## What it does

| Tab | Capability |
|---|---|
| **Chat** | Streaming conversation with a local Qwen model, rendered as Markdown. Dictation and spoken replies. |
| **Build** | Generates complete, compilable Android projects from six templates, exportable as a ZIP. |
| **Docs** | Writes PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx) and text files. |
| **Files** | Browse, search, rename, delete, zip. All inside the app's own storage. |
| **Research** | Index your own documents and ask questions against them (local RAG). |
| **Exam** | Photograph a question, OCR it, get a step-by-step worked solution saved as PDF/Word. |
| **Agent** | Run instructions against the device — launch apps, compose messages, set alarms, schedule reminders — and see what it has learned. |

A desktop app shares the same agent core (`:core`), so planning, memory and slot filling behave
identically on both targets.

## Screenshots

Every image below is a real capture of the app running, not a mockup. The desktop shots come from
the Linux build under Xvfb; the phone shots are rendered by the Compose test suite on the JVM
(`ScreenshotTest`), because this machine has no KVM for an emulator.

### Desktop

Dark is the default; the light theme is one click away in the sidebar.

![Desktop, dark theme](docs/screenshots/pc-dark.png)

![Desktop, light theme](docs/screenshots/pc-light.png)

The model runs offline. This exchange was generated locally by `qwen2.5-0.5b-instruct-q4_k_m`:

![Offline chat answer](docs/screenshots/03-chat-answer-dark.png)

The agent plans an instruction with a learned template, then executes it:

![Agent runs a reminder](docs/screenshots/04-agent-task-dark.png)

### Android

![Android, all tabs](docs/screenshots/android-overview.png)

Android is dark-only by design. Full-size images are in `docs/screenshots/android/`.

## Install

Two APKs are built from this source:

| APK | Size | Works offline on first launch? |
|---|---|---|
| `JordanMind-1.2-bundled.apk` | ~595 MB | Yes. Qwen2.5-0.5B q8 is inside; the app unpacks and loads it on first start. |
| `JordanMind-1.2-lite.apk` | ~73 MB | No. It has no model, so import a `.task` from **Chat → Import file** before chatting. |

Download the bundled one if you want to install and immediately talk to it. Download the lite one
only if you already have a `.task` file and would rather not pull 595 MB. Both are version 1.2
(versionCode 3).

> Version 1.0 had a real defect: it never loaded a model, so the chat input stayed disabled and the
> app looked broken. 1.1 loads a model at startup and says what to do when it cannot find one.
> 1.2 tunes itself to the phone it is installed on and adds Work mode.

### Tuned for the phone in your hand

Version 1.2 reads the device's RAM at startup and picks a matching working set, so the same build
behaves on a budget phone and a flagship:

| RAM | Tier | Context | Max reply | Background drift |
|---|---|---|---|---|
| up to 4 GB | lean | 1024 tokens | 384 tokens | off |
| up to 6 GB | balanced | 1536 tokens | 512 tokens | on |
| above 6 GB | capable | 2048 tokens | 768 tokens | on |

A Redmi 15C (Helio G81-Ultra) sits in the lean or balanced tier. The ceiling is enforced in
`LlmEngine.load`, so an oversized context cannot be allocated even if a settings file asks for one —
that allocation is what makes the app look like it "keeps stopping" on a 4 GB phone. Thread count is
not a setting: MediaPipe's own thread pool already sizes itself to the fast CPU cluster, and the API
exposes no override, so a knob here would be a lie.

The background gradient stops animating whenever the app is not on screen. An infinite animation is
never idle, so leaving it running kept the whole UI recomposing and the GPU busy in the background.

### Markdown replies

Answers are rendered, not dumped. Headings, bulleted and numbered lists, block quotes, dividers and
fenced code all get their own treatment, and inline `code` and **bold** keep their formatting.

Code blocks are the part that matters: they scroll horizontally instead of wrapping, so a command
stays readable, and each one carries a copy button. Every finished reply also has a copy button, and
the composer offers **regenerate** to re-run the last question.

The parser is a small hand-written reader over the subset a phone-sized model actually emits rather
than a full Markdown library, because tables, embedded HTML and reference links would add a parser
and an HTML renderer for output this model never produces. Anything it does not recognise falls
through to a plain paragraph, so malformed output still renders as text instead of disappearing.

### Work mode

A toggle above the chat switches the assistant to a work persona: it leads with the finished draft,
produces copy-ready emails and status updates, plans in short numbered steps, and refuses to invent
facts, figures or dates. It is a system prompt, not a second model, so it adds nothing to the RAM
footprint.

### Share to Jordan Mind

The app registers for `ACTION_SEND` text. Selecting text in any app and choosing *Jordan Mind* opens
the chat with that text already sent, which is the fastest path from a message or a note to a draft
reply. There is still no network permission.

The APK is signed with a self-signed key, so Android will ask you to allow installation from
unknown sources before it will sideload.

Requires Android 10 (API 29) or newer. 4 GB RAM minimum.

### Desktop

CI publishes `JordanMind-windows` (a `.exe` and `.msi` built on `windows-latest`) and
`JordanMind-linux` (a `.deb` plus a runnable app image). The desktop app loads GGUF models from
`~/.jordanmind/models/` through an external `llama-cli`.

To get a Windows installer that works offline from first launch, run the cross-build from Linux:

```bash
packaging/windows/build-windows-installer.sh                 # ~100 MB, no model
BUNDLE_MODEL=1 packaging/windows/build-windows-installer.sh  # ~570 MB, model + llama.cpp runtime
```

`BUNDLE_MODEL=1` stages Qwen2.5-0.5B q4_k_m and the llama.cpp Windows build, so the installed app
answers with nothing else installed. It is not wired into CI because it downloads ~1 GB of model
weights per run.

Release builds are signed and minified with R8.

## Models

On Android, only LiteRT/MediaPipe `.task` bundles are supported, because that is what the
on-device inference engine loads. Two Qwen options exist in that format:

| Model | Size (q8) | Notes |
|---|---|---|
| Qwen2.5-0.5B-Instruct | ~521 MB | Fast, fits low-end phones. Good for short answers, arithmetic, summaries. |
| Qwen2.5-1.5B-Instruct | ~1.6 GB | Noticeably better reasoning and longer coherent answers. Needs 6 GB+ RAM. |

There is no 3B Qwen in the LiteRT lineup; 1.5B is the practical ceiling on Android.

The desktop app takes a different route: it drives `llama-cli` and loads GGUF files, which is why
the bundled installer ships `qwen2.5-0.5b-instruct-q4_k_m.gguf` rather than a `.task`. Any GGUF
`llama.cpp` supports can be dropped into `~/.jordanmind/models/` and used there. Android cannot do
this without shipping the native runtime for every ABI.

## Building

```bash
./gradlew assembleRelease                                     # APK, no bundled model
./gradlew assembleRelease -PbundleModel=/path/to/model.task   # model baked into assets/models/
./gradlew :desktop:createReleaseDistributable                 # desktop app
./gradlew :desktop:packageReleaseDeb                          # Linux .deb (needs fakeroot)
```

On Windows, `./gradlew :desktop:packageReleaseExe` produces the `.exe` and `.msi`. This cannot be
cross-compiled with `jpackage` from Linux, because it links a native launcher and runtime for the
target OS. `packaging/windows/build-windows-installer.sh` sidesteps that by assembling the image
by hand — see the script for the steps.

Requires JDK 21 and an Android SDK with platform 36 and build-tools 36.0.0. Point
`local.properties` at your SDK, or set `ANDROID_HOME`.

```bash
./gradlew testDebugUnitTest :core:test :desktop:test   # 68 tests
```

The packaged desktop app can verify itself without a display:

```bash
"desktop/build/compose/binaries/main-release/app/Jordan Mind/bin/Jordan Mind" --selftest
```

It exercises planning, learning, memory, article export, persistence across a restart, and the
path-traversal guards.

## Privacy

There is no networking code in this project. The Android manifest removes `INTERNET` and
`ACCESS_NETWORK_STATE` outright, verifiable on any build:

```bash
aapt2 dump badging app-release.apk | grep -i internet   # prints nothing
```

Conversations, reminders, facts and drafts are stored on the device only.

## Honest limitations

- **APK compilation happens off-device.** Tab 2 writes real Gradle projects and they are verified
  to build, but the phone does not run AGP/AAPT2 itself — that toolchain does not fit alongside
  the model. Open the exported folder in Android Studio, or run `./gradlew assembleDebug`.
- **The 0.5B model is small.** It handles everyday questions well and will stumble on genuinely
  hard multi-step reasoning. Load 1.5B when quality matters more than speed.
- **Dictation may need the network.** Speech recognition uses the system recogniser; with no
  INTERNET permission it fails on devices whose recogniser is cloud-backed. Typing always works.
  Fully offline dictation would need bundled Vosk weights (~50 MB), deliberately left out.
- **Retrieval is lexical, not neural.** The RAG embedder is a hashed bag-of-words with cosine
  similarity — fast and dependency-free, but it matches wording, not meaning.
- **"Learning" means remembering instruction-to-action mappings**, not training the model. No
  weights are ever modified; the model is fixed and read-only.
- **Desktop actions are sandboxed** to the user's home directory, and links are restricted to
  `http(s)`.
- **Gemma 3 1B cannot be bundled.** It is gated behind a signed licence and returns HTTP 401 to
  unattended downloads.
