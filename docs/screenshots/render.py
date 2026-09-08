#!/usr/bin/env python3
"""Render README screenshots that match the Compose UI (ink / paper / prompt)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
INK, PAPER, DIM, PROMPT, LINE = "#0B0B0B", "#E8E4D9", "#8A867C", "#00FF41", "#2A2A2A"
W, H = 780, 1688
PAD_X, PAD_T = 56, 108


def font(size: int):
    for path in (
        "/System/Library/Fonts/Supplemental/Courier New.ttf",
        "/Library/Fonts/Courier New.ttf",
        "/System/Library/Fonts/Menlo.ttc",
        "/System/Library/Fonts/SFNSMono.ttf",
    ):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


F_TIME = font(112)
F_BODY = font(32)
F_SMALL = font(24)
F_MED = font(28)


def phone() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    im = Image.new("RGB", (W, H), INK)
    return im, ImageDraw.Draw(im)


def save(im: Image.Image, name: str) -> None:
    path = OUT / name
    im.save(path, "PNG")
    print(path, im.size)


def home() -> None:
    im, d = phone()
    d.text((PAD_X, PAD_T), "15:42", font=F_TIME, fill=PAPER)
    d.text((PAD_X, PAD_T + 128), "Mon 7 Sep", font=F_MED, fill=DIM)
    d.text((PAD_X, PAD_T + 168), "18° cloudy", font=F_MED, fill=DIM)
    bubble_x, bubble_y = W - PAD_X - 36, PAD_T + 12
    d.rounded_rectangle((bubble_x, bubble_y, bubble_x + 36, bubble_y + 24), radius=4, outline=PROMPT, width=2)
    d.line((bubble_x + 10, bubble_y + 24, bubble_x + 4, bubble_y + 34), fill=PROMPT, width=2)
    d.line((bubble_x + 18, bubble_y + 24, bubble_x + 4, bubble_y + 34), fill=PROMPT, width=2)
    y = PAD_T + 230
    for todo in ("buy milk", "ship builder-launcher CI", "call dentist"):
        d.text((PAD_X, y), todo, font=F_BODY, fill=PAPER)
        y += 48
    d.text((PAD_X, y), "… more tasks >", font=F_BODY, fill=PROMPT)
    y = H - 160
    d.text((PAD_X, y), ">", font=F_BODY, fill=PROMPT)
    d.text((PAD_X + 36, y), "?summarize this PR", font=F_BODY, fill=PAPER)
    d.rectangle((PAD_X + 380, y + 6, PAD_X + 388, y + 32), fill=PROMPT)
    d.line((PAD_X, y + 48, W - PAD_X, y + 48), fill=LINE, width=2)
    save(im, "home.png")


def hub() -> None:
    im, d = phone()
    d.text((PAD_X, PAD_T), "hub", font=F_BODY, fill=PROMPT)
    d.text((W - PAD_X - 80, PAD_T), "home", font=F_SMALL, fill=DIM)
    items = [
        ("Messages", "Jason", "on my way"),
        ("Signal", "Lauren", "running late"),
    ]
    y = PAD_T + 80
    for src, title, body in items:
        d.text((PAD_X, y), src, font=F_SMALL, fill=DIM)
        d.text((PAD_X, y + 32), title, font=F_BODY, fill=PAPER)
        if body:
            d.text((PAD_X, y + 70), body, font=F_MED, fill=DIM)
        ix, iy = W - PAD_X - 78, y + 26
        stem_x, top_y, tip_x = ix + 4, iy + 2, ix + 22
        d.line((stem_x, iy + 22, stem_x, top_y), fill=PROMPT, width=2)
        d.line((stem_x, top_y, tip_x, top_y), fill=PROMPT, width=2)
        d.line((tip_x, top_y, tip_x - 7, top_y - 5), fill=PROMPT, width=2)
        d.line((tip_x, top_y, tip_x - 7, top_y + 5), fill=PROMPT, width=2)
        xx, xy = W - PAD_X - 28, y + 30
        d.line((xx, xy, xx + 16, xy + 16), fill=DIM, width=2)
        d.line((xx + 16, xy, xx, xy + 16), fill=DIM, width=2)
        y += 128
    save(im, "hub.png")


def settings() -> None:
    im, d = phone()
    d.text((PAD_X, PAD_T), "settings", font=F_BODY, fill=PROMPT)
    d.text((W - PAD_X - 80, PAD_T), "home", font=F_SMALL, fill=DIM)
    y = PAD_T + 80
    d.text((PAD_X, y), "Accent", font=F_SMALL, fill=DIM)
    y += 40
    swatches = ["#00FF41", "#B7C9A8", "#FFB000", "#00E5FF", "#FF2BD6", "#E8E4D9"]
    x = PAD_X
    for i, color in enumerate(swatches):
        d.rectangle((x, y, x + 28, y + 28), fill=color, outline="#E8E4D9" if i == 0 else LINE)
        x += 40
    y += 44
    d.text((PAD_X, y), "cyberpunk green", font=F_MED, fill=PROMPT)
    y += 36
    d.text((PAD_X, y), "Cursor, the > prompt, and links like all notes.", font=F_MED, fill=DIM)
    y += 48
    d.text((PAD_X, y), "Hex", font=F_SMALL, fill=DIM)
    y += 36
    d.text((PAD_X, y), "#00FF41", font=F_MED, fill=PAPER)
    d.line((PAD_X, y + 44, W - PAD_X, y + 44), fill=LINE, width=2)
    y += 70
    d.text((PAD_X, y), "AI provider", font=F_SMALL, fill=DIM)
    y += 40
    d.text((PAD_X, y), "Hermes", font=F_MED, fill=PROMPT)
    d.text((PAD_X + 180, y), "xAI", font=F_MED, fill=DIM)
    y += 44
    d.text((PAD_X, y), "OpenAI", font=F_MED, fill=DIM)
    d.text((PAD_X + 180, y), "Anthropic", font=F_MED, fill=DIM)
    y += 64
    d.text((PAD_X, y), "Hermes base URL", font=F_SMALL, fill=DIM)
    y += 36
    d.text((PAD_X, y), "http://192.168.1.10:8642", font=F_MED, fill=PAPER)
    d.line((PAD_X, y + 44, W - PAD_X, y + 44), fill=LINE, width=2)
    y += 70
    d.text((PAD_X, y), "API key (stored on device)", font=F_SMALL, fill=DIM)
    y += 36
    d.text((PAD_X, y), "optional for local Hermes", font=F_MED, fill=DIM)
    d.line((PAD_X, y + 44, W - PAD_X, y + 44), fill=LINE, width=2)
    y += 70
    d.text((PAD_X, y), "Model", font=F_SMALL, fill=DIM)
    y += 36
    d.text((PAD_X, y), "default", font=F_MED, fill=DIM)
    d.line((PAD_X, y + 44, W - PAD_X, y + 44), fill=LINE, width=2)
    y += 70
    d.text((PAD_X, y), "Keyboard", font=F_SMALL, fill=DIM)
    y += 40
    d.text((PAD_X, y), "auto", font=F_MED, fill=PROMPT)
    d.text((PAD_X + 120, y), "hardware", font=F_MED, fill=DIM)
    d.text((PAD_X + 320, y), "software", font=F_MED, fill=DIM)
    y += 56
    d.text((PAD_X, y), "Hardware keyboard detected —", font=F_MED, fill=DIM)
    d.text((PAD_X, y + 36), "command bar sits at the bottom,", font=F_MED, fill=DIM)
    d.text((PAD_X, y + 72), "above the keys.", font=F_MED, fill=DIM)
    y += 140
    d.text((PAD_X, y), "Weather location", font=F_SMALL, fill=DIM)
    y += 36
    d.text((PAD_X, y), "Kitchener, Ontario, Canada", font=F_MED, fill=PAPER)
    d.line((PAD_X, y + 44, W - PAD_X, y + 44), fill=LINE, width=2)
    y += 70
    d.text((PAD_X, y), "Weather uses this city. No GPS.", font=F_MED, fill=DIM)
    y += 56
    d.text((PAD_X, y), "Notification access (hub)", font=F_MED, fill=PAPER)
    y += 48
    d.text((PAD_X, y), "Set as default home app", font=F_MED, fill=PAPER)
    save(im, "settings.png")


if __name__ == "__main__":
    home()
    hub()
    settings()
