#!/usr/bin/env python3
"""
One-time placeholder toolbar icon generator for the RuneLite sidebar
NavigationButton. Not part of the plugin runtime. Replace
src/main/resources/com/leaguestasks/icon.png with real artwork later.
"""
from pathlib import Path
from PIL import Image, ImageDraw

OUTPUT_PATH = (
    Path(__file__).parent.parent
    / "src" / "main" / "resources" / "com" / "leaguestasks" / "icon.png"
)

SIZE = 32
BG = (0, 0, 0, 0)
SCROLL = (222, 193, 140, 255)
SCROLL_EDGE = (140, 100, 60, 255)
DIE_BG = (160, 30, 30, 255)
DIE_PIP = (255, 255, 255, 255)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), BG)
    draw = ImageDraw.Draw(img)

    # Scroll body (represents the task list)
    draw.rounded_rectangle([2, 4, 22, 28], radius=3, fill=SCROLL, outline=SCROLL_EDGE, width=1)
    for y in (9, 14, 19, 23):
        draw.line([(5, y), (19, y)], fill=SCROLL_EDGE, width=1)

    # Die (represents the random pick)
    draw.rounded_rectangle([15, 15, 30, 30], radius=3, fill=DIE_BG, outline=(80, 10, 10, 255), width=1)
    for dx, dy in [(19, 19), (26, 19), (19, 26), (26, 26), (22.5, 22.5)]:
        draw.ellipse([dx - 1, dy - 1, dx + 1, dy + 1], fill=DIE_PIP)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUTPUT_PATH)
    print(f"Wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
