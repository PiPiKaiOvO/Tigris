#!/usr/bin/env python3
"""Generate the web image set for the Tigris website.

Source of truth: ../docs/screenshots/*.jpg (real captures, 1280x720).
Outputs WebP at 1280 and 640 widths, plus a 1200x630 Open Graph card.
Run from anywhere: python scripts/build-assets.py
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT.parent / "docs" / "screenshots"
OUT = ROOT / "assets" / "img"
OUT.mkdir(parents=True, exist_ok=True)

SHOTS = ["home", "shelf", "grid", "cc", "frame"]

for name in SHOTS:
    src = SRC / f"{name}.jpg"
    if not src.exists():
        raise SystemExit(f"missing screenshot: {src}")
    im = Image.open(src).convert("RGB")
    if im.size != (1280, 720):
        raise SystemExit(f"unexpected size for {src}: {im.size}")
    im.save(OUT / f"{name}-1280.webp", "WEBP", quality=84, method=6)
    im.resize((640, 360), Image.LANCZOS).save(
        OUT / f"{name}-640.webp", "WEBP", quality=80, method=6
    )

# ---- Open Graph card -------------------------------------------------------
W, H = 1200, 630
card = Image.new("RGB", (W, H), "#070A10")

glow = Image.new("RGB", (W, H), "#070A10")
gd = ImageDraw.Draw(glow)
gd.ellipse((560, -220, 1420, 520), fill="#123A5A")
gd.ellipse((-260, 300, 520, 900), fill="#2A2450")
glow = glow.filter(ImageFilter.GaussianBlur(130))
card = Image.blend(card, glow, 0.85)

d = ImageDraw.Draw(card)
font_bold = ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc", 76)
font_mid = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 28)
font_small = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 22)
font_brand = ImageFont.truetype("C:/Windows/Fonts/Dengb.ttf", 30)

d.rounded_rectangle((72, 62, 112, 102), radius=12, fill="#B9D4FF")
d.text((72, 122), "TIGRIS", font=font_brand, fill="#B9D4FF")
d.text((72, 170), "让电视桌面，", font=font_bold, fill="#F5F7FB")
d.text((72, 258), "重新惊艳。", font=font_bold, fill="#F5F7FB")
d.multiline_text(
    (76, 372),
    "液态玻璃 Dock · 顶部内容\nAndroid TV 9+ · 无需 root · 开源",
    font=font_mid,
    fill="#A9B3C2",
    spacing=12,
)

tv = Image.open(SRC / "home.jpg").convert("RGB").resize((620, 349), Image.LANCZOS)
bezel = Image.new("RGB", (652, 381), "#050709")
bezel.paste(tv, (16, 16))
mask = Image.new("L", bezel.size, 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, 651, 380), radius=18, fill=255)
card.paste(bezel, (516, 130), mask)
d.rounded_rectangle((516, 130, 1167, 510), radius=18, outline="#3A4657", width=2)
d.text((76, 540), "github.com/lesnolie/Tigris", font=font_small, fill="#8C98A8")

card.save(OUT / "og.jpg", "JPEG", quality=88, optimize=True)
print("wrote", OUT)
for p in sorted(OUT.iterdir()):
    print(f"  {p.name:20s} {p.stat().st_size/1024:7.1f} KB")
