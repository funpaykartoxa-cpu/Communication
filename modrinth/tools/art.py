"""Shared drawing helpers for Communication promotional images."""

from __future__ import annotations

import math
import os
import time
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ICON = ROOT / "modrinth" / "icon.png"

FONT_REGULAR = "C:/Windows/Fonts/segoeui.ttf"
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_SEMIBOLD = "C:/Windows/Fonts/segoeuib.ttf"
FONT_CODE = "C:/Windows/Fonts/consolab.ttf"
FONT_DISPLAY = "C:/Windows/Fonts/bahnschrift.ttf"

BG_TOP = (8, 11, 20)
BG_BOTTOM = (5, 7, 14)
PANEL = (12, 17, 30, 245)
PANEL_RAISED = (17, 23, 40, 255)
PANEL_DARK = (9, 13, 24, 255)
BORDER = (37, 48, 74, 255)
BORDER_SOFT = (28, 37, 57, 255)
TEXT = (241, 244, 255, 255)
MUTED = (143, 152, 174, 255)
MUTED_DARK = (91, 99, 120, 255)
ACCENT = (133, 156, 255, 255)
ACCENT_LIGHT = (190, 205, 255, 255)
ACCENT_DEEP = (83, 105, 222, 255)
GREEN = (95, 220, 115, 255)
RED = (245, 90, 90, 255)
YELLOW = (245, 205, 75, 255)
WHITE = (255, 255, 255, 255)


def load_font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def lerp(a: tuple[int, ...], b: tuple[int, ...], t: float) -> tuple[int, ...]:
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def vertical_gradient(size: tuple[int, int], top, bottom) -> Image.Image:
    width, height = size
    image = Image.new("RGBA", size)
    draw = ImageDraw.Draw(image)
    for y in range(height):
        t = y / max(1, height - 1)
        eased = t * t * (3.0 - 2.0 * t)
        draw.line((0, y, width, y), fill=lerp(top, bottom, eased) + (255,))
    return image


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


class Art:
    def __init__(self, width: int = 1920, height: int = 1080):
        self.width = width
        self.height = height
        self.img = vertical_gradient((width, height), BG_TOP, BG_BOTTOM)
        self.draw = ImageDraw.Draw(self.img)
        self._fonts: dict[tuple[str, int], ImageFont.FreeTypeFont] = {}
        self._background()

    def _background(self) -> None:
        # Blue ambient light.
        glow = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((1050, -220, 2050, 780), fill=(45, 72, 200, 80))
        gd.ellipse((-260, 560, 720, 1500), fill=(55, 82, 210, 42))
        gd.ellipse((1280, 720, 1980, 1320), fill=(68, 88, 220, 32))
        glow = glow.filter(ImageFilter.GaussianBlur(150))
        self.img.alpha_composite(glow)

        # Technical grid, faded at the edges.
        grid = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        gd = ImageDraw.Draw(grid)
        for x in range(0, self.width, 80):
            gd.line((x, 0, x, self.height), fill=(120, 145, 220, 9), width=1)
        for y in range(0, self.height, 80):
            gd.line((0, y, self.width, y), fill=(120, 145, 220, 9), width=1)
        self.img.alpha_composite(grid)

        # Vignette.
        vignette = Image.new("L", (self.width, self.height), 0)
        vd = ImageDraw.Draw(vignette)
        vd.rectangle((0, 0, self.width, self.height), fill=210)
        vd.ellipse((-280, -240, self.width + 280, self.height + 240), fill=0)
        vignette = vignette.filter(ImageFilter.GaussianBlur(190))
        shade = Image.new("RGBA", (self.width, self.height), (0, 0, 0, 0))
        shade.putalpha(vignette.point(lambda value: int(value * 0.55)))
        self.img.alpha_composite(shade)

    def font(self, kind: str, size: int) -> ImageFont.FreeTypeFont:
        path = {
            "regular": FONT_REGULAR,
            "bold": FONT_BOLD,
            "semibold": FONT_SEMIBOLD,
            "code": FONT_CODE,
            "display": FONT_DISPLAY,
        }[kind]
        key = (path, size)
        if key not in self._fonts:
            self._fonts[key] = load_font(path, size)
        return self._fonts[key]

    def text(
        self,
        xy: tuple[float, float],
        content: str,
        size: int = 26,
        color=TEXT,
        kind: str = "regular",
        anchor: str = "la",
        spacing: int = 8,
    ) -> None:
        font = self.font(kind, size)
        if "\n" in content:
            self.draw.multiline_text(xy, content, font=font, fill=color, anchor=anchor, spacing=spacing)
        else:
            self.draw.text(xy, content, font=font, fill=color, anchor=anchor)

    def text_width(self, content: str, size: int, kind: str = "regular") -> float:
        return self.font(kind, size).getlength(content)

    def tracked(
        self,
        xy: tuple[float, float],
        content: str,
        size: int,
        color,
        tracking: float = 3.0,
        kind: str = "semibold",
        anchor: str = "la",
    ) -> None:
        font = self.font(kind, size)
        widths = [font.getlength(char) for char in content]
        total = sum(widths) + tracking * max(0, len(content) - 1)
        x, y = xy
        if anchor[0] == "m":
            x -= total / 2
        elif anchor[0] == "r":
            x -= total
        for char, width in zip(content, widths):
            self.draw.text((x, y), char, font=font, fill=color, anchor="l" + anchor[1])
            x += width + tracking

    def panel(
        self,
        x: float,
        y: float,
        width: float,
        height: float,
        radius: int = 24,
        fill=PANEL,
        outline=BORDER,
        border_width: int = 2,
        top_highlight: bool = True,
    ) -> None:
        self.draw.rounded_rectangle(
            (x, y, x + width, y + height), radius=radius, fill=fill, outline=outline, width=border_width
        )
        if top_highlight:
            self.draw.line(
                (x + radius, y + 1, x + width - radius, y + 1),
                fill=(255, 255, 255, 18),
                width=1,
            )

    def line(self, x1: float, y1: float, x2: float, y2: float, color=(255, 255, 255, 20), width: int = 1) -> None:
        self.draw.line((x1, y1, x2, y2), fill=color, width=width)

    def chip(
        self,
        x: float,
        y: float,
        label: str,
        icon: str | None = None,
        color=ACCENT,
        size: int = 22,
        height: int = 50,
        fill=(17, 24, 43, 235),
        outline=(47, 61, 92, 255),
    ) -> float:
        padding = 20 if icon is None else 16
        text_width = self.text_width(label, size, "semibold")
        icon_space = 26 if icon else 0
        width = padding * 2 + text_width + icon_space
        self.draw.rounded_rectangle((x, y, x + width, y + height), radius=height / 2, fill=fill, outline=outline, width=1)
        if icon:
            self.icon(icon, x + padding, y + height / 2 - 9, 18, color)
        self.text(
            (x + padding + icon_space, y + height / 2),
            label,
            size=size,
            color=TEXT,
            kind="semibold",
            anchor="lm",
        )
        return width

    def icon(self, name: str, x: float, y: float, size: float, color=ACCENT, width: int | None = None) -> None:
        stroke = max(2, int(size * 0.11)) if width is None else width
        d = self.draw
        if name == "chat":
            d.rounded_rectangle((x, y, x + size, y + size * 0.72), radius=size * 0.18, outline=color, width=stroke)
            d.polygon(
                [
                    (x + size * 0.22, y + size * 0.68),
                    (x + size * 0.16, y + size),
                    (x + size * 0.48, y + size * 0.71),
                ],
                fill=color,
            )
        elif name == "pin":
            cy = y + size * 0.38
            d.ellipse((x + size * 0.16, y, x + size * 0.84, y + size * 0.68), outline=color, width=stroke)
            d.polygon(
                [
                    (x + size * 0.24, y + size * 0.52),
                    (x + size * 0.76, y + size * 0.52),
                    (x + size * 0.5, y + size),
                ],
                outline=color,
                width=stroke,
            )
            d.ellipse(
                (x + size * 0.38, cy - size * 0.15, x + size * 0.62, cy + size * 0.09),
                outline=color,
                width=max(2, stroke - 1),
            )
        elif name == "grid":
            cell = size * 0.27
            gap = size * 0.095
            for row in range(3):
                for column in range(3):
                    d.rounded_rectangle(
                        (
                            x + column * (cell + gap),
                            y + row * (cell + gap),
                            x + column * (cell + gap) + cell,
                            y + row * (cell + gap) + cell,
                        ),
                        radius=2,
                        outline=color,
                        width=max(1, stroke - 1),
                    )
        elif name == "sound":
            d.polygon(
                [
                    (x, y + size * 0.35),
                    (x + size * 0.2, y + size * 0.35),
                    (x + size * 0.44, y + size * 0.08),
                    (x + size * 0.44, y + size * 0.92),
                    (x + size * 0.2, y + size * 0.65),
                    (x, y + size * 0.65),
                ],
                outline=color,
                width=stroke,
            )
            d.arc((x + size * 0.4, y + size * 0.18, x + size * 0.78, y + size * 0.82), start=-55, end=55, fill=color, width=stroke)
            d.arc((x + size * 0.52, y + size * 0.02, x + size, y + size * 0.98), start=-55, end=55, fill=color, width=stroke)
        elif name == "key":
            d.rounded_rectangle((x, y + size * 0.1, x + size, y + size * 0.9), radius=size * 0.16, outline=color, width=stroke)
            d.rounded_rectangle(
                (x + size * 0.22, y + size * 0.3, x + size * 0.78, y + size * 0.54),
                radius=size * 0.05,
                outline=color,
                width=max(1, stroke - 1),
            )
            d.line((x + size * 0.3, y + size * 0.7, x + size * 0.7, y + size * 0.7), fill=color, width=max(1, stroke - 1))
        elif name == "shield":
            d.polygon(
                [
                    (x + size * 0.5, y),
                    (x + size * 0.92, y + size * 0.16),
                    (x + size * 0.84, y + size * 0.66),
                    (x + size * 0.5, y + size),
                    (x + size * 0.16, y + size * 0.66),
                    (x + size * 0.08, y + size * 0.16),
                ],
                outline=color,
                width=stroke,
            )
            d.line(
                (x + size * 0.3, y + size * 0.5, x + size * 0.45, y + size * 0.65, x + size * 0.72, y + size * 0.33),
                fill=color,
                width=stroke,
            )
        elif name == "heart":
            r = size * 0.27
            cy = y + size * 0.34
            d.ellipse((x, cy - r, x + 2 * r, cy + r), fill=color)
            d.ellipse((x + size - 2 * r, cy - r, x + size, cy + r), fill=color)
            d.polygon(
                [
                    (x + size * 0.03, y + size * 0.38),
                    (x + size * 0.97, y + size * 0.38),
                    (x + size * 0.5, y + size),
                ],
                fill=color,
            )
        elif name == "users":
            d.ellipse((x + size * 0.08, y + size * 0.04, x + size * 0.38, y + size * 0.34), outline=color, width=stroke)
            d.arc((x - size * 0.02, y + size * 0.38, x + size * 0.48, y + size), start=180, end=360, fill=color, width=stroke)
            d.ellipse((x + size * 0.56, y + size * 0.12, x + size * 0.82, y + size * 0.38), outline=color, width=stroke)
            d.arc((x + size * 0.47, y + size * 0.45, x + size * 0.95, y + size), start=180, end=360, fill=color, width=stroke)
        elif name == "check":
            d.line(
                (x + size * 0.08, y + size * 0.52, x + size * 0.4, y + size * 0.84, x + size * 0.94, y + size * 0.16),
                fill=color,
                width=stroke,
            )
        elif name == "copy":
            d.rounded_rectangle(
                (x + size * 0.28, y, x + size, y + size * 0.72), radius=size * 0.1, outline=color, width=stroke
            )
            d.rounded_rectangle(
                (x, y + size * 0.28, x + size * 0.72, y + size),
                radius=size * 0.1,
                outline=color,
                width=stroke,
                fill=PANEL_DARK,
            )
        elif name == "lock":
            d.rounded_rectangle(
                (x + size * 0.08, y + size * 0.38, x + size * 0.92, y + size),
                radius=size * 0.12,
                outline=color,
                width=stroke,
            )
            d.arc((x + size * 0.22, y, x + size * 0.78, y + size * 0.7), start=180, end=360, fill=color, width=stroke)
        elif name == "bolt":
            d.polygon(
                [
                    (x + size * 0.56, y),
                    (x + size * 0.12, y + size * 0.58),
                    (x + size * 0.46, y + size * 0.58),
                    (x + size * 0.34, y + size),
                    (x + size * 0.88, y + size * 0.4),
                    (x + size * 0.54, y + size * 0.4),
                ],
                fill=color,
            )
        elif name == "monitor":
            d.rounded_rectangle((x, y, x + size, y + size * 0.7), radius=size * 0.1, outline=color, width=stroke)
            d.line((x + size * 0.5, y + size * 0.7, x + size * 0.5, y + size * 0.9), fill=color, width=stroke)
            d.line((x + size * 0.3, y + size * 0.92, x + size * 0.7, y + size * 0.92), fill=color, width=stroke)

    def art_icon(self, x: float, y: float, size: int, glow: bool = True) -> None:
        icon = Image.open(ICON).convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)
        if glow:
            halo = Image.new("RGBA", (size * 2, size * 2), (0, 0, 0, 0))
            halo.alpha_composite(icon, (size // 2, size // 2))
            halo = halo.filter(ImageFilter.GaussianBlur(38))
            halo.putalpha(halo.getchannel("A").point(lambda value: int(value * 0.55)))
            self.img.alpha_composite(halo, (int(x - size // 2), int(y - size // 2)))
        self.img.alpha_composite(icon, (int(x), int(y)))

    def avatar(self, x: float, y: float, size: int, hue: int = 0, initials: str = "") -> None:
        # Stylised player head: gradient disc with a simple face silhouette.
        colors = [(64, 92, 160), (96, 74, 150), (74, 116, 130), (138, 92, 92)]
        base = colors[hue % len(colors)]
        self.draw.ellipse((x, y, x + size, y + size), fill=base + (255,))
        self.draw.ellipse(
            (x + size * 0.2, y + size * 0.16, x + size * 0.8, y + size * 0.68),
            fill=(235, 205, 180, 255),
        )
        self.draw.rounded_rectangle(
            (x + size * 0.28, y + size * 0.08, x + size * 0.72, y + size * 0.34),
            radius=size * 0.1,
            fill=(28, 26, 34, 255),
        )
        self.draw.rectangle(
            (x + size * 0.12, y + size * 0.7, x + size * 0.88, y + size),
            fill=(40, 48, 70, 255),
        )
        self.draw.ellipse((x, y, x + size, y + size), outline=(255, 255, 255, 38), width=2)
        if initials:
            self.text((x + size / 2, y + size / 2), initials, 20, WHITE, "bold", "mm")

    def footer(self, page: str, label: str = "COMMUNICATION  ·  FABRIC 1.21.4") -> None:
        self.line(120, 1002, self.width - 120, 1002, (255, 255, 255, 18), 1)
        self.text((120, 1024), label, 18, MUTED_DARK, "semibold")
        self.text((self.width - 120, 1024), page, 18, MUTED_DARK, "semibold", "ra")

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        temp = path.with_name(path.name + ".tmp")
        image = self.img.convert("RGB")
        for attempt in range(6):
            try:
                image.save(temp, "PNG", optimize=True)
                os.replace(temp, path)
                print(f"Wrote {path}")
                return
            except OSError:
                if attempt == 5:
                    raise
                time.sleep(0.6)


def heading(art: Art, eyebrow: str, title: str, subtitle: str, page: str, accent=ACCENT) -> None:
    art.tracked((120, 108), eyebrow.upper(), 20, accent, 3.2, "semibold")
    art.text((120, 146), title, 74, TEXT, "bold")
    art.text((120, 246), subtitle, 28, MUTED, "regular")
    art.line(120, 312, 200, 312, accent, 4)
    art.footer(page)


def sparkle(art: Art, cx: float, cy: float, radius: float, color=(190, 205, 255, 210)) -> None:
    points = []
    for index in range(8):
        angle = math.radians(-90 + index * 45)
        r = radius if index % 2 == 0 else radius * 0.22
        points.append((cx + math.cos(angle) * r, cy + math.sin(angle) * r))
    art.draw.polygon(points, fill=color)
