# Jordan Mind

An offline AI agent for Android. Everything — the language model, OCR, document writing,
search — runs on the phone. The app declares no network permission, so it cannot reach the
internet even if it wanted to.

## What it does

| Tab | Capability |
|---|---|
| **Chat** | Streaming conversation with a local Qwen model. Dictation and spoken replies. |
| **Build** | Generates complete, compilable Android projects from six templates, exportable as a ZIP. |
| **Docs** | Writes PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx) and text files. |
| **Files** | Browse, search, rename, delete, zip. All inside the app's own storage. |
| **Research** | Index your own documents and ask questions against them (local RAG). |
| **Exam** | Photograph a question, OCR it, get a step-by-step worked solution saved as PDF/Word. |

## Install

Two builds are produced:

- `JordanMind-1.0-bundled.apk` (~665 MB) — Qwen2.5-0.5B-Instruct q8 is baked in. Install and
  it works immediately, no model download, no setup.
- `JordanMind-1.0-lite.apk` (~143 MB) — no model. Install, then either press **Install bundled**
  (needs the model pushed to the device) or **Import file** to pick a `.task` / `.litertlm` from
  storage. Use this if you want the 1.5B model instead, or want a smaller download.

Requires Android 10 (API 29) or newer. 4 GB RAM minimum; 6 GB+ recommended for the 1.5B model.

Because these are debug-signed, Android will ask you to allow installation from unknown sources.

## Models

Only LiteRT/MediaPipe `.task` bundles are supported, because that is what the on-device
inference engine loads. Two Qwen options exist in that format:

| Model | Size (q8) | Notes |
|---|---|---|
| Qwen2.5-0.5B-Instruct | ~521 MB | Fast, fits low-end phones. Good for short answers, arithmetic, summaries. |
| Qwen2.5-1.5B-Instruct | ~1.6 GB | Noticeably better reasoning and longer coherent answers. Needs 6 GB+ RAM. |

There is no 3B Qwen in the LiteRT lineup; 1.5B is the practical ceiling for this format.
Larger models would need a different runtime (llama.cpp / GGUF), which is not wired up here.

## Building

```bash
./build.sh                      # APK with no bundled model
./build.sh /path/to/model.task  # APK with the model baked into assets/models/
```

Requires JDK 21 and an Android SDK with platform 36 and build-tools 36.0.0. Point
`local.properties` at your SDK, or set `ANDROID_HOME`.

```bash
gradle testDebugUnitTest        # unit tests for the embedder and project generator
```

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
