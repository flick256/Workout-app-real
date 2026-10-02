#!/usr/bin/env python3
"""
Downloads the free-exercise-db images and converts them to small WebP files that are
bundled in the app (app/src/main/assets/exercise_images/<id>/<n>.webp).

Run from the repo root:  pip install pillow && python3 tools/fetch_exercise_images.py
Safe to re-run: existing files are skipped.
"""
import io
import json
import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
DATASET = "app/src/main/assets/exercises.json"
OUT = "app/src/main/assets/exercise_images"
MAX_WIDTH = 480
QUALITY = 60


def convert(rel_path: str) -> str:
    target = os.path.join(OUT, os.path.splitext(rel_path)[0] + ".webp")
    if os.path.exists(target):
        return "skip"
    for attempt in range(4):
        try:
            with urllib.request.urlopen(BASE + urllib.request.quote(rel_path), timeout=30) as resp:
                data = resp.read()
            break
        except Exception as e:  # network hiccup: back off and retry
            if attempt == 3:
                return f"fail {rel_path}: {e}"
            time.sleep(2 ** attempt)
    image = Image.open(io.BytesIO(data)).convert("RGB")
    image.thumbnail((MAX_WIDTH, MAX_WIDTH * 2))
    os.makedirs(os.path.dirname(target), exist_ok=True)
    image.save(target, "WEBP", quality=QUALITY, method=6)
    return "ok"


def main() -> int:
    with open(DATASET) as f:
        paths = [p for e in json.load(f) for p in e.get("images", [])]
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(convert, paths))
    fails = [r for r in results if r.startswith("fail")]
    print(f"{len(paths)} images: {results.count('ok')} new, {results.count('skip')} existing, {len(fails)} failed")
    for f in fails:
        print(f)
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
