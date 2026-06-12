"""Generate Google Play store graphic assets for FitToGym.

Renders two PNGs from the app's real brand palette and the four-bar
"workout timeline" icon motif (warmup / build / interval / cooldown):

  * icon-512.png                 512x512, 32-bit PNG  (hi-res Play icon)
  * feature-graphic-1024x500.png 1024x500, 24-bit PNG (no alpha per Play rules)

Pure-Pillow drawing (no SVG rasteriser required). Re-run after any palette
or wording change:  python play-assets/generate_assets.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

# --- Brand palette (from app/src/main/res + ui/theme/Color.kt) -------------
BLUE_DEEP = (0x15, 0x65, 0xC0)     # #1565C0 ACTIVE / brand
BLUE_BRIGHT = (0x1E, 0x88, 0xE5)   # #1E88E5 WARMUP
BLUE_LIGHT = (0x64, 0xB5, 0xF6)    # #64B5F6 REST
BLUE_MUTED = (0x90, 0xCA, 0xF9)    # #90CAF9 COOLDOWN
BLUE_900 = (0x0D, 0x47, 0xA1)      # darker blue for gradient depth
WHITE = (0xFF, 0xFF, 0xFF)

OUT_DIR = os.path.dirname(os.path.abspath(__file__))


def lerp(a: tuple[int, int, int], b: tuple[int, int, int], t: float) -> tuple[int, int, int]:
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    for y in range(h):
        c = lerp(top, bottom, y / max(1, h - 1))
        for x in range(w):
            px[x, y] = c
    return img


def diagonal_gradient(size, c0, c1):
    """Top-left c0 -> bottom-right c1."""
    w, h = size
    img = Image.new("RGB", size)
    px = img.load()
    maxd = (w - 1) + (h - 1)
    for y in range(h):
        for x in range(w):
            px[x, y] = lerp(c0, c1, (x + y) / maxd)
    return img


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    bold_candidates = [
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\seguisb.ttf",
    ]
    reg_candidates = [
        r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ]
    for path in (bold_candidates if bold else reg_candidates):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def make_icon(path: str) -> None:
    """512x512 hi-res icon: white timeline bars on a brand-blue gradient.

    Geometry is taken verbatim from ic_launcher_foreground.xml (108 viewport)
    and scaled up, so the Play listing icon matches the installed launcher.
    """
    S = 512
    scale = S / 108.0
    img = vertical_gradient((S, S), lerp(BLUE_BRIGHT, BLUE_DEEP, 0.35), BLUE_DEEP).convert("RGBA")
    draw = ImageDraw.Draw(img)

    # (x, y, w, h) in 108-viewport units — four bars + baseline.
    bars = [
        (28, 46, 12, 22),
        (44, 36, 12, 32),
        (60, 28, 12, 40),
        (76, 42, 12, 26),
    ]
    radius = 3 * scale
    for (x, y, w, h) in bars:
        x0, y0 = x * scale, y * scale
        x1, y1 = (x + w) * scale, (y + h) * scale
        draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=WHITE)

    bx, by, bw, bh = 22, 72, 70, 4
    draw.rounded_rectangle(
        [bx * scale, by * scale, (bx + bw) * scale, (by + bh) * scale],
        radius=1.5 * scale,
        fill=WHITE,
    )

    img.save(path)
    print(f"wrote {path} ({S}x{S}, 32-bit)")


def make_feature_graphic(path: str) -> None:
    """1024x500 feature graphic: wordmark + tagline (left) + timeline motif (right)."""
    W, H = 1024, 500
    img = diagonal_gradient((W, H), BLUE_BRIGHT, BLUE_900).convert("RGBA")

    # Right-third workout-timeline motif (equaliser-like bars on a baseline).
    # Confined to x >= 700 so it never collides with the wordmark/tagline.
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    baseline_y = 330
    bar_w = 30
    gap = 22
    heights = [70, 120, 95, 180, 130, 155]
    alphas = [130, 170, 140, 235, 175, 200]
    right_margin = 40
    start_x = W - right_margin - (len(heights) * bar_w + (len(heights) - 1) * gap)
    for i, (bh, a) in enumerate(zip(heights, alphas)):
        x0 = start_x + i * (bar_w + gap)
        od.rounded_rectangle(
            [x0, baseline_y - bh, x0 + bar_w, baseline_y],
            radius=8,
            fill=(255, 255, 255, a),
        )
    od.rounded_rectangle(
        [start_x - 10, baseline_y, W - right_margin, baseline_y + 6],
        radius=3,
        fill=(255, 255, 255, 230),
    )
    img = Image.alpha_composite(img, overlay)
    draw = ImageDraw.Draw(img)

    # Text zone is everything left of the bars motif (with padding).
    text_x = 72
    text_max_w = (start_x - 24) - text_x

    def fit_font(text: str, target: int, bold: bool) -> ImageFont.FreeTypeFont:
        size = target
        while size > 14:
            f = load_font(size, bold=bold)
            if draw.textlength(text, font=f) <= text_max_w:
                return f
            size -= 2
        return load_font(14, bold=bold)

    title = "FitToGym"
    tag1 = "Structured .fit workouts \u2014 fully offline."
    tag2 = "Beeps \u00b7 BLE heart rate \u00b7 floating mini view."

    title_font = fit_font(title, 104, bold=True)
    tag_font = fit_font(max(tag1, tag2, key=len), 31, bold=False)

    draw.text((text_x, 150), title, font=title_font, fill=WHITE)
    draw.text((text_x + 4, 286), tag1, font=tag_font, fill=WHITE)
    draw.text((text_x + 4, 328), tag2, font=tag_font, fill=BLUE_MUTED)

    # Feature graphic must be 24-bit (no alpha).
    img.convert("RGB").save(path)
    print(f"wrote {path} ({W}x{H}, 24-bit, no alpha)")


def main() -> None:
    make_icon(os.path.join(OUT_DIR, "icon-512.png"))
    make_feature_graphic(os.path.join(OUT_DIR, "feature-graphic-1024x500.png"))


if __name__ == "__main__":
    main()
