"""Frame raw phone screenshots into branded, Play-Store-ready portrait images.

Usage:
    1. Save your raw phone screenshots (PNG) into  play-assets/screenshots/raw/
       using the names listed in MANIFEST below.
    2. Run:  python .\\play-assets\\frame_screenshots.py
    3. Framed output lands in  play-assets/screenshots/  as 01.png, 02.png, ...

Only the files that actually exist are processed, so you can start with the
two you already have and add the rest later. Re-running is safe (overwrites).

Output spec: 1080x1920 (9:16, well within Play's 2:1 limit), 24-bit PNG.
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RAW_DIR = os.path.join(HERE, "screenshots", "raw")
OUT_DIR = os.path.join(HERE, "screenshots")

# Brand palette (matches generate_assets.py / the feature graphic).
BLUE_BRIGHT = (30, 136, 229)   # #1E88E5
BLUE_900 = (13, 71, 161)       # #0D47A1
BLUE_MUTED = (144, 202, 246)   # #90CAF9
WHITE = (255, 255, 255)

# Canvas + layout.
CANVAS_W, CANVAS_H = 1080, 1920
MARGIN_X = 90
CAPTION_TOP = 82
CAPTION_MAX_W = CANVAS_W - 2 * 78
BOX_TOP = 250
BOX_BOTTOM = 1808
CORNER_RADIUS = 40
SHADOW_BLUR = 26
SHADOW_OFFSET = 16

# Ordered list of (raw filename, caption, crop_bottom_frac). crop_bottom_frac
# trims that fraction off the bottom of the raw shot before framing (used to
# remove the Android screenshot-preview / Lens toolbar that got captured in one
# shot). Output is numbered by position (01, 02, ...).
MANIFEST = [
    ("WhatsApp Image 2026-06-10 at 20.32.28 (1).jpeg",
     "All your structured workouts in one place", 0.17),
    ("WhatsApp Image 2026-06-12 at 13.34.07.jpeg",
     "See your current and next pace target", 0.405),
    ("WhatsApp Image 2026-06-10 at 20.32.29.jpeg",
     "Live heart rate from any Bluetooth strap", 0.0),
    ("WhatsApp Image 2026-06-10 at 20.32.28.jpeg",
     "Big, glanceable targets \u2014 no strap needed", 0.0),
]


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    """Load a system font, probing common bold/regular faces."""
    bold_faces = ["segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"]
    regular_faces = ["segoeui.ttf", "arial.ttf", "DejaVuSans.ttf"]
    for name in (bold_faces if bold else regular_faces):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def lerp(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def diagonal_gradient(size: tuple[int, int], c0, c1) -> Image.Image:
    """Top-left -> bottom-right diagonal gradient."""
    w, h = size
    base = Image.new("RGB", size, c0)
    grad = Image.new("L", size)
    px = grad.load()
    denom = float(w + h)
    for y in range(h):
        for x in range(w):
            px[x, y] = int((x + y) / denom * 255)
    top = Image.new("RGB", size, c1)
    return Image.composite(top, base, grad)


def round_corners(im: Image.Image, radius: int) -> Image.Image:
    im = im.convert("RGBA")
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, im.size[0], im.size[1]], radius=radius, fill=255)
    im.putalpha(mask)
    return im


def wrap_lines(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_w: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    cur = ""
    for word in words:
        trial = (cur + " " + word).strip()
        if draw.textlength(trial, font=font) <= max_w:
            cur = trial
        else:
            if cur:
                lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def frame_one(raw_path: str, caption: str, out_path: str, crop_bottom_frac: float = 0.0) -> None:
    canvas = diagonal_gradient((CANVAS_W, CANVAS_H), BLUE_BRIGHT, BLUE_900).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Caption (centred, wrapped, auto-shrink to two lines max).
    size = 54
    while size > 30:
        cap_font = load_font(size, bold=True)
        lines = wrap_lines(draw, caption, cap_font, CAPTION_MAX_W)
        if len(lines) <= 2:
            break
        size -= 3
    line_h = (cap_font.getbbox("Ag")[3] - cap_font.getbbox("Ag")[1]) + 12
    y = CAPTION_TOP
    for line in lines:
        w = draw.textlength(line, font=cap_font)
        draw.text(((CANVAS_W - w) / 2, y), line, font=cap_font, fill=WHITE)
        y += line_h

    # Load + scale screenshot to fit the content box (preserve aspect).
    shot = Image.open(raw_path).convert("RGB")
    if crop_bottom_frac > 0.0:
        keep_h = int(round(shot.height * (1.0 - crop_bottom_frac)))
        shot = shot.crop((0, 0, shot.width, keep_h))
    box_w = CANVAS_W - 2 * MARGIN_X
    box_h = BOX_BOTTOM - BOX_TOP
    scale = min(box_w / shot.width, box_h / shot.height)
    new_w = max(1, int(round(shot.width * scale)))
    new_h = max(1, int(round(shot.height * scale)))
    shot = shot.resize((new_w, new_h), Image.LANCZOS)
    shot = round_corners(shot, CORNER_RADIUS)

    pos_x = (CANVAS_W - new_w) // 2
    pos_y = BOX_TOP + (box_h - new_h) // 2

    # Drop shadow behind the screenshot.
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(
        [pos_x + SHADOW_OFFSET, pos_y + SHADOW_OFFSET,
         pos_x + new_w + SHADOW_OFFSET, pos_y + new_h + SHADOW_OFFSET],
        radius=CORNER_RADIUS, fill=(0, 0, 20, 150),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(SHADOW_BLUR))
    canvas = Image.alpha_composite(canvas, shadow)

    canvas.paste(shot, (pos_x, pos_y), shot)

    # Footer wordmark.
    draw = ImageDraw.Draw(canvas)
    foot_font = load_font(36, bold=True)
    fw = draw.textlength("FitToGym", font=foot_font)
    draw.text(((CANVAS_W - fw) / 2, CANVAS_H - 58), "FitToGym", font=foot_font, fill=BLUE_MUTED)

    canvas.convert("RGB").save(out_path)
    print(f"wrote {out_path}  (from {os.path.basename(raw_path)})")


def main() -> None:
    os.makedirs(RAW_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)

    made = 0
    for idx, (fname, caption, crop_bottom_frac) in enumerate(MANIFEST, start=1):
        raw_path = os.path.join(RAW_DIR, fname)
        if not os.path.exists(raw_path):
            print(f"skip   {fname:18s} (not found in screenshots/raw/)")
            continue
        out_path = os.path.join(OUT_DIR, f"{idx:02d}.png")
        frame_one(raw_path, caption, out_path, crop_bottom_frac)
        made += 1

    print()
    if made == 0:
        print("No raw screenshots found yet.")
        print(f"Drop PNGs into: {RAW_DIR}")
        print("Expected names:")
        for fname, caption, _ in MANIFEST:
            print(f"  {fname:18s} -> \"{caption}\"")
    else:
        print(f"Done: {made} framed screenshot(s) in {OUT_DIR}")


if __name__ == "__main__":
    main()
