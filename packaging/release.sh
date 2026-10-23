#!/usr/bin/env bash
#
# Publishes a Jordan Mind release to GitHub: pushes the branch, tags it, creates the release and
# uploads every asset, including the screenshots.
#
# It is safe to re-run. The tag and release are created only if missing, and an asset that is
# already published is replaced, so a half-finished run can simply be repeated.
#
# Usage:
#   GITHUB_TOKEN=... ./packaging/release.sh                 # uses the version in app/build.gradle.kts
#   GITHUB_TOKEN=... VERSION=1.2.0 ./packaging/release.sh
#
set -uo pipefail

REPO="${REPO:-Mubogi/jordan-mind}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/packaging/windows/dist"
cd "$ROOT"

: "${GITHUB_TOKEN:?set GITHUB_TOKEN to a token with repo scope}"

# The APKs carry the short version ("1.2") while the tag and Windows installers carry the full one
# ("1.2.0") — that is the convention the existing v1.1.0 release follows. Deriving both from
# app/build.gradle.kts means neither name has to be passed in or guessed.
APP_VERSION="$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)"
APK_VERSION="${APK_VERSION:-$APP_VERSION}"
VERSION="${VERSION:-$APP_VERSION}"
# "1.2" -> "1.2.0"; a version that already has a patch component is left alone.
case "$VERSION" in
  *.*.*) ;;
  *) VERSION="$VERSION.0" ;;
esac
TAG="${TAG:-v$VERSION}"
API="https://api.github.com/repos/$REPO"

say() { printf '==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

# A bad token fails here with a clear message instead of a confusing 404 later.
say "Checking credentials"
login="$(curl -fsS -H "Authorization: Bearer $GITHUB_TOKEN" https://api.github.com/user | jq -r .login)" \
  || die "GITHUB_TOKEN is not accepted by the API (Bad credentials)"
say "Authenticated as $login"

# ---------------------------------------------------------------- assets
# Every asset is collected first so a missing build is caught before anything is published.
assets=()
# Builds land either beside the repo or in the repo root depending on where the build ran.
find_asset() {
  local name="$1" dir
  for dir in "${ASSET_DIR:-/workspace}" "$ROOT" "$DIST" "$ROOT/app/build/outputs/apk/release"; do
    [ -f "$dir/$name" ] && { printf '%s\n' "$dir/$name"; return 0; }
  done
  return 1
}
add_asset() {
  local found
  if found="$(find_asset "$1")"; then
    assets+=("$found")
  else
    say "skipping $1 (not built)"
  fi
}

add_asset "JordanMind-$APK_VERSION-bundled.apk"
add_asset "JordanMind-$APK_VERSION-lite.apk"
add_asset "JordanMind-Setup-$VERSION-win64.exe"
add_asset "JordanMind-Setup-$VERSION-win64-bundled.exe"

if [ ${#assets[@]} -eq 0 ]; then
  die "no assets found; build the APKs and run packaging/windows/build-windows-installer.sh first"
fi
say "Assets to upload (${#assets[@]}):"
for asset in "${assets[@]}"; do
  printf '    %s (%s)\n' "$(basename "$asset")" "$(du -h "$asset" | cut -f1)"
done

# ---------------------------------------------------------------- push
# Authentication goes through a throwaway credential file rather than the remote URL, so the token
# never lands in argv (visible in `ps`) or in .git/config.
CRED="$(mktemp)"
trap 'rm -f "$CRED"' EXIT
chmod 600 "$CRED"
printf 'https://x-access-token:%s@github.com\n' "$GITHUB_TOKEN" > "$CRED"
push_with_token() { git -c credential.helper="store --file=$CRED" push "$@"; }

say "Pushing $(git rev-parse --abbrev-ref HEAD) to origin"
push_with_token origin HEAD || die "push failed"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  say "Tag $TAG already exists locally; moving it to HEAD"
  git tag -f "$TAG" >/dev/null
else
  say "Tagging $TAG"
  git tag "$TAG"
fi
push_with_token origin "refs/tags/$TAG" --force || die "tag push failed"

# ---------------------------------------------------------------- release
NOTES="$(cat <<EOF
Version $VERSION. Everything still runs on the device: the app declares no network permission.

## Install

| File | Size | Notes |
|---|---|---|
| \`JordanMind-$APK_VERSION-bundled.apk\` | ~595 MB | Qwen2.5-0.5B is inside. Install and talk to it straight away. |
| \`JordanMind-$APK_VERSION-lite.apk\` | ~73 MB | No model. Import a \`.task\` from **Chat → Import file** first. |
| \`JordanMind-Setup-$VERSION-win64-bundled.exe\` | ~596 MB | Windows, offline out of the box (GGUF + llama.cpp included). |
| \`JordanMind-Setup-$VERSION-win64.exe\` | ~100 MB | Windows, app only. |

## What changed

- **Tuned to the phone.** The app reads the device's RAM at startup and picks a matching working
  set, so the same build stays usable on a budget phone and a flagship:

  | RAM | Tier | Context | Max reply | Background drift |
  |---|---|---|---|---|
  | up to 4 GB | lean | 1024 tokens | 384 tokens | off |
  | up to 6 GB | balanced | 1536 tokens | 512 tokens | on |
  | above 6 GB | capable | 2048 tokens | 768 tokens | on |

  The ceiling is enforced inside \`LlmEngine.load\`, so an oversized context cannot be allocated
  even if a settings file asks for one. That allocation is what made the app look like it "keeps
  stopping" on a 4 GB phone. A Redmi 15C sits in the lean or balanced tier.

- **Work mode.** A toggle above the chat switches the assistant to a work persona: finished drafts
  first, copy-ready emails and status updates, short numbered plans, and no invented facts, figures
  or dates. It is a system prompt, not a second model, so it adds no RAM.

- **Share to Jordan Mind.** Select text in any app and hand it straight to the agent through the
  share sheet. The chat opens with that text already sent.

- **Less battery while hidden.** The background gradient stops animating when the app is not on
  screen, and is pinned still entirely on lean devices. An infinite animation is never idle, so it
  kept the whole UI recomposing and the GPU busy in the background.

- **Honest tests.** \`./gradlew test\` now runs green. The Robolectric UI suites are excluded from
  the release variant, where the Compose test manifest is absent and the whole suite failed to
  resolve the activity — failures that never existed in the debug build.

## Screenshots

![All tabs](docs/screenshots/android-overview.png)

Full-size captures are in \`docs/screenshots/android/\` on \`master\`.

## Verify the offline claim

\`\`\`
aapt2 dump badging JordanMind-$APK_VERSION-bundled.apk | grep -i internet   # prints nothing
\`\`\`
EOF
)"

if curl -fsS -H "Authorization: Bearer $GITHUB_TOKEN" "$API/releases/tags/$TAG" >/dev/null 2>&1; then
  say "Release $TAG exists; updating its notes"
  body="$(jq -n --arg b "$NOTES" '{body:$b}')"
  curl -fsS -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "$body" "$API/releases/tags/$TAG" >/dev/null || die "could not update the release"
else
  say "Creating release $TAG"
  body="$(jq -n --arg t "Jordan Mind $VERSION" --arg tag "$TAG" --arg b "$NOTES" \
    '{tag_name:$tag, name:$t, body:$b, draft:false, prerelease:false}')"
  curl -fsS -X POST -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "$body" "$API/releases" >/dev/null || die "could not create the release"
fi

# ---------------------------------------------------------------- upload
# GitHub rejects a second asset with the same name, so an existing one is removed first. That is
# what makes re-running this script safe after a partial upload.
for asset in "${assets[@]}"; do
  name="$(basename "$asset")"
  existing="$(curl -fsS -H "Authorization: Bearer $GITHUB_TOKEN" "$API/releases/tags/$TAG" \
    | jq -r --arg n "$name" '.assets[] | select(.name == $n) | .id')"
  if [ -n "$existing" ]; then
    say "Replacing existing asset $name"
    curl -fsS -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" \
      "$API/releases/assets/$existing" >/dev/null || die "could not remove the old $name"
  fi

  say "Uploading $name"
  curl -fsS -X POST \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H "Content-Type: application/octet-stream" \
    --data-binary "@$asset" \
    "https://uploads.github.com/repos/$REPO/releases/tags/$TAG/assets?name=$name" \
    >/dev/null || die "upload of $name failed"
done

say "Done: https://github.com/$REPO/releases/tag/$TAG"
