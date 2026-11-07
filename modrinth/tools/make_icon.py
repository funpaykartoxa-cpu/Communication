"""Generate the Communication mod icon.

The artwork is drawn from primitives at 4x resolution and downsampled, so the
result stays crisp at 512x512 and at the small sizes used by launchers.

Usage:
    python modrinth/tools/make_icon.py
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
OUT_ICON = ROOT / "src" / "main" / "resources" / "assets" / "communication" / "icon.png"
OUT_MODRINTH = ROOT / "modrinth" / "icon.png"

SIZE = 512
SS = 4
W = SIZE * SS

# Palette, aligned with communication.render.Theme.
BG_TOP = (13, 19, 36, 255)
BG_BOTTOM = (5, 7, 14, 255)
ACCENT = (133, 156, 255, 255)
ACCENT_LIGHT = (190, 205, 255, 255)
ACCENT_DEEP = (83, 105, 222, 255)
INK = (8, 12, 24, 255)


def s(value: float) -> int:
    return int(round(value * SS))


def rounded_mask(size: int, radius: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=s(radius), fill=255)
    return mask


def vertical_gradient(top: tuple[int, int, int, int], bottom: tuple[int, int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (W, W))
    draw = ImageDraw.Draw(image)
    for y in range(W):
        t = y / (W - 1)
        eased = t * t * (3.0 - 2.0 * t)
        color = tuple(int(round(top[i] + (bottom[i] - top[i]) * eased)) for i in range(4))
        draw.line((0, y, W, y), fill=color)
    return image


def colorized(mask: Image.Image, color: tuple[int, int, int, int]) -> Image.Image:
    layer = Image.new("RGBA", mask.size, color[:3] + (0,))
    layer.putalpha(ImageChops.multiply(mask, Image.new("L", mask.size, color[3])))
    return layer


def gradient_layer(top: tuple[int, int, int, int], bottom: tuple[int, int, int, int]) -> Image.Image:
    return vertical_gradient(top, bottom)


def paste_gradient(base: Image.Image, mask: Image.Image, top, bottom, offset: int = 0) -> None:
    gradient = gradient_layer(top, bottom)
    if offset:
        shifted = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        shifted.paste(gradient, (0, offset))
        gradient = shifted
    base.paste(gradient, (0, 0), mask)


def add_glow(base: Image.Image, mask: Image.Image, color, radius: float, strength: float = 1.0) -> None:
    blurred = mask.filter(ImageFilter.GaussianBlur(s(radius)))
    if strength != 1.0:
        blurred = blurred.point(lambda value: int(value * strength))
    base.alpha_composite(colorized(blurred, color))


def bubble_mask() -> Image.Image:
    mask = Image.new("L", (W, W), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((s(118), s(124), s(388), s(330)), radius=s(82), fill=255)
    draw.polygon([(s(121), s(300)), (s(112), s(392)), (s(204), s(331))], fill=255)
    return mask


def pin_mask(expand: float = 0.0) -> Image.Image:
    mask = Image.new("L", (W, W), 0)
    draw = ImageDraw.Draw(mask)
    cx, cy = s(368), s(338)
    r = s(54 + expand)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=255)
    draw.polygon(
        [
            (s(320 - expand), s(354)),
            (s(416 + expand), s(354)),
            (s(368), s(446 + expand * 1.2)),
        ],
        fill=255,
    )
    return mask


def outline_from_mask(mask: Image.Image, width: float) -> Image.Image:
    k = s(width)
    grown = mask.filter(ImageFilter.MaxFilter(k * 2 + 1))
    shrunk = mask.filter(ImageFilter.MinFilter(k * 2 + 1))
    return ImageChops.subtract(grown, shrunk)


def sparkle(draw: ImageDraw.ImageDraw, cx: float, cy: float, radius: float, color) -> None:
    points = []
    for index in range(8):
        angle = math.radians(-90 + index * 45)
        r = radius if index % 2 == 0 else radius * 0.24
        points.append((s(cx + math.cos(angle) * r), s(cy + math.sin(angle) * r)))
    draw.polygon(points, fill=color)


def build() -> Image.Image:
    random.seed(21)
    image = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    shape = rounded_mask(W, 112)

    background = vertical_gradient(BG_TOP, BG_BOTTOM)

    # Soft blue light behind the emblem.
    glow = Image.new("L", (W, W), 0)
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse((s(70), s(50), s(450), s(430)), fill=180)
    glow_draw.ellipse((s(160), s(120), s(360), s(320)), fill=255)
    glow = glow.filter(ImageFilter.GaussianBlur(s(82)))
    background.alpha_composite(colorized(glow, (35, 60, 190, 84)))

    # Faint technical grid, faded towards the edges.
    grid = Image.new("L", (W, W), 0)
    grid_draw = ImageDraw.Draw(grid)
    step = s(32)
    for x in range(0, W, step):
        grid_draw.line((x, 0, x, W), fill=18)
    for y in range(0, W, step):
        grid_draw.line((0, y, W, y), fill=18)
    radial = Image.new("L", (W, W), 0)
    radial_draw = ImageDraw.Draw(radial)
    radial_draw.ellipse((s(-70), s(-70), s(582), s(582)), fill=255)
    radial = radial.filter(ImageFilter.GaussianBlur(s(60)))
    grid = ImageChops.multiply(grid, radial)
    grid = ImageChops.multiply(grid, shape)
    background.alpha_composite(colorized(grid, (120, 145, 220, 40)))

    # Diagonal sheen in the upper-left corner.
    sheen = Image.new("L", (W, W), 0)
    sheen_draw = ImageDraw.Draw(sheen)
    sheen_draw.polygon([(0, 0), (s(300), 0), (0, s(300))], fill=90)
    sheen = sheen.filter(ImageFilter.GaussianBlur(s(50)))
    sheen = ImageChops.multiply(sheen, shape)
    background.alpha_composite(colorized(sheen, (120, 150, 255, 26)))

    image.alpha_composite(Image.composite(background, Image.new("RGBA", (W, W), (0, 0, 0, 0)), shape))

    # Signal arcs in the top-right corner.
    arcs = Image.new("L", (W, W), 0)
    arcs_draw = ImageDraw.Draw(arcs)
    cx, cy = s(466), s(76)
    for index, radius in enumerate((36, 64, 92)):
        width = max(2, s(3.2 - index * 0.55))
        box = (cx - s(radius), cy - s(radius), cx + s(radius), cy + s(radius))
        arcs_draw.arc(box, start=92, end=178, fill=170 - index * 42, width=width)
    arcs = arcs.filter(ImageFilter.GaussianBlur(s(0.6)))
    image.alpha_composite(colorized(arcs, (120, 145, 255, 210)))
    add_glow(image, arcs, (100, 130, 255, 120), 10, 0.7)

    bubble = bubble_mask()
    bubble_outline = outline_from_mask(bubble, 3.4)

    # Drop shadow under the bubble.
    shadow = bubble.filter(ImageFilter.GaussianBlur(s(24)))
    shadow = shadow.point(lambda value: int(value * 0.55))
    shadow_layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    shadow_layer.paste((0, 0, 0, 190), (0, s(16)), shadow)
    image.alpha_composite(shadow_layer)

    paste_gradient(image, bubble, (31, 43, 76, 255), (11, 16, 34, 255))
    inner_light = Image.new("L", (W, W), 0)
    ImageDraw.Draw(inner_light).ellipse((s(128), s(104), s(378), s(344)), fill=255)
    inner_light = inner_light.filter(ImageFilter.GaussianBlur(s(54)))
    inner_light = ImageChops.multiply(inner_light, bubble)
    image.alpha_composite(colorized(inner_light, (64, 92, 210, 52)))
    add_glow(image, bubble_outline, (90, 116, 255, 140), 9, 0.8)
    paste_gradient(image, bubble_outline, (190, 204, 255, 255), (88, 110, 218, 255))

    # Inner top highlight.
    inner = Image.new("L", (W, W), 0)
    inner_draw = ImageDraw.Draw(inner)
    inner_draw.rounded_rectangle(
        (s(124), s(130), s(382), s(324)), radius=s(76), outline=64, width=s(1.4)
    )
    inner = ImageChops.multiply(inner, bubble)
    image.alpha_composite(colorized(inner, (220, 230, 255, 80)))

    # Three connected party dots.
    dot_positions = (184, 256, 328)
    dot_y = 229
    dot_r = 16.5
    connector = Image.new("L", (W, W), 0)
    connector_draw = ImageDraw.Draw(connector)
    connector_draw.line(
        (s(dot_positions[0]), s(dot_y), s(dot_positions[-1]), s(dot_y)),
        fill=110,
        width=s(2.2),
    )
    image.alpha_composite(colorized(connector, (133, 156, 255, 110)))

    dot_glow = Image.new("L", (W, W), 0)
    dot_glow_draw = ImageDraw.Draw(dot_glow)
    for x in dot_positions:
        dot_glow_draw.ellipse(
            (s(x - dot_r), s(dot_y - dot_r), s(x + dot_r), s(dot_y + dot_r)), fill=255
        )
    add_glow(image, dot_glow, (120, 145, 255, 190), 16, 1.0)

    dot_draw = ImageDraw.Draw(image)
    for x in dot_positions:
        # Dark gap keeps the dots readable over the bubble edge.
        dot_draw.ellipse(
            (s(x - dot_r - 5), s(dot_y - dot_r - 5), s(x + dot_r + 5), s(dot_y + dot_r + 5)),
            fill=(13, 19, 38, 235),
        )
        dot_draw.ellipse(
            (s(x - dot_r), s(dot_y - dot_r), s(x + dot_r), s(dot_y + dot_r)),
            fill=ACCENT_LIGHT,
        )
        dot_draw.ellipse(
            (s(x - dot_r * 0.62), s(dot_y - dot_r * 0.82), s(x + dot_r * 0.12), s(dot_y - dot_r * 0.08)),
            fill=(255, 255, 255, 105),
        )

    # Location pin accent, cut out of the bubble first for a clean overlap.
    cutout = pin_mask(expand=6.0)
    image.paste((7, 10, 20, 255), (0, 0), cutout)

    pin = pin_mask()
    pin_outline = outline_from_mask(pin, 3.0)
    pin_shadow = pin.filter(ImageFilter.GaussianBlur(s(18)))
    pin_shadow = pin_shadow.point(lambda value: int(value * 0.5))
    pin_shadow_layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    pin_shadow_layer.paste((0, 0, 0, 180), (s(8), s(14)), pin_shadow)
    image.alpha_composite(pin_shadow_layer)

    paste_gradient(image, pin, (206, 218, 255, 255), (86, 110, 232, 255))
    paste_gradient(image, pin_outline, (230, 237, 255, 255), (120, 142, 255, 255))
    add_glow(image, pin, (110, 135, 255, 190), 18, 0.8)

    # Hole in the pin.
    hole = Image.new("L", (W, W), 0)
    ImageDraw.Draw(hole).ellipse(
        (s(368 - 20), s(334 - 20), s(368 + 20), s(334 + 20)), fill=255
    )
    image.paste(INK, (0, 0), hole)
    ring = outline_from_mask(hole, 2.2)
    image.alpha_composite(colorized(ring, (210, 222, 255, 180)))

    # A small highlight on the pin.
    highlight = Image.new("L", (W, W), 0)
    ImageDraw.Draw(highlight).arc(
        (s(332), s(302), s(404), s(374)), start=196, end=300, fill=140, width=s(3.0)
    )
    image.alpha_composite(colorized(highlight, (255, 255, 255, 110)))

    # Sparkles.
    sparkles = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    sparkle_draw = ImageDraw.Draw(sparkles)
    sparkle(sparkle_draw, 98, 112, 15, (190, 205, 255, 235))
    sparkle(sparkle_draw, 444, 238, 8, (160, 180, 255, 190))
    sparkle(sparkle_draw, 86, 352, 5, (140, 165, 255, 150))
    image.alpha_composite(sparkles)

    # Vignette.
    vignette = Image.new("L", (W, W), 0)
    vignette_draw = ImageDraw.Draw(vignette)
    vignette_draw.rectangle((0, 0, W, W), fill=255)
    vignette_draw.ellipse((s(-120), s(-120), s(632), s(632)), fill=0)
    vignette = vignette.filter(ImageFilter.GaussianBlur(s(100)))
    vignette = ImageChops.multiply(vignette, shape)
    image.alpha_composite(colorized(vignette, (0, 0, 0, 150)))

    # Full-bleed border + inner highlight.
    border = Image.new("L", (W, W), 0)
    border_draw = ImageDraw.Draw(border)
    border_draw.rounded_rectangle(
        (s(0.75), s(0.75), W - s(0.75), W - s(0.75)), radius=s(112), outline=255, width=s(1.5)
    )
    image.alpha_composite(colorized(border, (86, 104, 160, 170)))
    inner_border = Image.new("L", (W, W), 0)
    inner_draw = ImageDraw.Draw(inner_border)
    inner_draw.rounded_rectangle(
        (s(2.6), s(2.6), W - s(2.6), W - s(2.6)), radius=s(109), outline=255, width=s(1.0)
    )
    image.alpha_composite(colorized(inner_border, (220, 230, 255, 38)))

    # Very light noise to avoid flat banding.
    noise = Image.effect_noise((W, W), 5.0).convert("L")
    noise = ImageChops.multiply(noise, shape)
    image.alpha_composite(colorized(noise, (255, 255, 255, 8)))

    return image.resize((SIZE, SIZE), Image.Resampling.LANCZOS)


def main() -> None:
    icon = build()
    OUT_ICON.parent.mkdir(parents=True, exist_ok=True)
    OUT_MODRINTH.parent.mkdir(parents=True, exist_ok=True)
    icon.save(OUT_ICON, "PNG", optimize=True)
    icon.save(OUT_MODRINTH, "PNG", optimize=True)
    print(f"Wrote {OUT_ICON}")
    print(f"Wrote {OUT_MODRINTH}")


if __name__ == "__main__":
    main()
