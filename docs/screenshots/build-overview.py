#!/usr/bin/env python3
"""Builds docs/screenshots/android-overview.png from the per-tab captures.

The individual screenshots come from ScreenshotTest; this stitches them into the
one image the README and the release notes embed. Run it after regenerating
screenshots:

    ./gradlew :app:testDebugUnitTest --tests "com.jordanmind.agent.ScreenshotTest"
    python3 docs/screenshots/build-overview.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ANDROID = HERE / "android"
OUT = HERE / "android-overview.png"

# Order matters: this is the order a reader scans the grid in.
SHOTS = [
    ("01-chat", "Chat"),
    ("02-agent", "Agent"),
    ("03-build", "Build"),
    ("04-docs", "Docs"),
    ("05-files", "Files"),
    ("06-research", "Research"),
    ("07-exam", "Exam"),
    ("08-markdown", "Markdown"),
]

COLUMNS = 4
CELL_W = 213
PAD = 4


def main() -> None:
    tiles = []
    for stem, _ in SHOTS:
        path = ANDROID / f"{stem}.png"
        if not path.exists():
            raise SystemExit(f"missing {path}; run ScreenshotTest first")
        image = Image.open(path).convert("RGB")
        ratio = CELL_W / image.width
        tiles.append(image.resize((CELL_W, max(1, int(image.height * ratio))), Image.LANCZOS))

    cell_h = max(tile.height for tile in tiles)
    rows = (len(tiles) + COLUMNS - 1) // COLUMNS
    width = COLUMNS * CELL_W + (COLUMNS + 1) * PAD
    height = rows * cell_h + (rows + 1) * PAD

    canvas = Image.new("RGB", (width, height), (12, 10, 5))
    for index, tile in enumerate(tiles):
        column, row = index % COLUMNS, index // COLUMNS
        canvas.paste(
            tile,
            (PAD + column * (CELL_W + PAD), PAD + row * (cell_h + PAD)),
        )

    canvas.save(OUT, optimize=True)
    print(f"wrote {OUT} ({canvas.width}x{canvas.height}, {len(tiles)} tiles)")


if __name__ == "__main__":
    main()
