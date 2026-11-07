"""Generate the Modrinth gallery images for Communication.

All images are 1920x1080 design mockups drawn from the mod's real palette and
layout constants. They are promotional material, not in-game screenshots.

Usage:
    python modrinth/tools/make_gallery.py
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

from art import (
    ACCENT,
    ACCENT_DEEP,
    ACCENT_LIGHT,
    BORDER,
    BORDER_SOFT,
    GREEN,
    MUTED,
    MUTED_DARK,
    PANEL,
    PANEL_DARK,
    PANEL_RAISED,
    RED,
    TEXT,
    WHITE,
    YELLOW,
    Art,
    heading,
    sparkle,
)

OUT = Path(__file__).resolve().parents[1] / "gallery"


def hero() -> Art:
    art = Art()
    accent = ACCENT

    art.tracked((120, 92), "FABRIC MOD  ·  MINECRAFT 1.21.4", 20, accent, 3.4, "semibold")
    art.text((120, 132), "COMMUNICATION", 92, TEXT, "bold")
    art.text(
        (120, 262),
        "Пати, метки и оверлей ESC —\nбез лишних модулей.",
        32,
        MUTED,
        "regular",
        spacing=12,
    )
    art.line(120, 380, 208, 380, accent, 4)

    x = 120
    for label, icon in [
        ("Пати по коду", "chat"),
        ("Полный инвентарь", "grid"),
        ("Метки и звук", "pin"),
        ("Клиентский мод", "monitor"),
    ]:
        width = art.chip(x, 424, label, icon, size=21, height=52)
        x += width + 14

    # Compact technical stats.
    stats = [("1.21.4", "MINECRAFT"), ("FABRIC", "LOADER"), ("CLIENT", "SIDE")]
    for index, (value, label) in enumerate(stats):
        sx = 120 + index * 220
        art.text((sx, 862), value, 34, TEXT, "bold")
        art.text((sx, 902), label, 16, MUTED_DARK, "semibold")
        if index:
            art.line(sx - 44, 858, sx - 44, 914, (255, 255, 255, 22), 1)

    # Orbit rings behind the icon.
    rings = Image.new("RGBA", (art.width, art.height), (0, 0, 0, 0))
    rd = ImageDraw.Draw(rings)
    cx, cy = 1510, 510
    for radius, alpha in ((318, 44), (372, 30), (426, 20)):
        rd.arc(
            (cx - radius, cy - radius, cx + radius, cy + radius),
            start=196,
            end=344,
            fill=(133, 156, 255, alpha),
            width=2,
        )
    rd.arc((cx - 318, cy - 318, cx + 318, cy + 318), start=20, end=68, fill=(133, 156, 255, 32), width=2)
    art.img.alpha_composite(rings)

    art.art_icon(1252, 250, 516)

    # Floating party-code card.
    art.panel(1064, 168, 342, 78, 18, fill=(12, 18, 33, 242), outline=(44, 58, 88, 255))
    art.draw.ellipse((1088, 198, 1102, 212), fill=GREEN)
    art.text((1116, 186), "PARTY / CODE", 15, MUTED_DARK, "semibold")
    art.text((1116, 206), "K7M4Q2", 30, ACCENT_LIGHT, "bold")
    art.text((1376, 207), "4", 22, TEXT, "bold", "rm")

    # Floating mark card.
    art.panel(1408, 788, 286, 70, 18, fill=(12, 18, 33, 242), outline=(44, 58, 88, 255))
    art.avatar(1428, 802, 42, hue=2)
    art.text((1482, 802), "TOP3R_PVP", 21, TEXT, "semibold")
    art.text((1482, 826), "метка пати", 15, MUTED_DARK, "regular")
    art.icon("pin", 1616, 816, 18, ACCENT)
    art.text((1642, 824), "24 м", 20, ACCENT_LIGHT, "semibold", "lm")

    sparkle(art, 1110, 610, 10, (170, 188, 255, 190))
    sparkle(art, 1742, 700, 7, (150, 170, 255, 150))
    sparkle(art, 1210, 318, 5, (150, 170, 255, 120))

    art.footer("01 / 06")
    return art


def party() -> Art:
    art = Art()
    heading(
        art,
        "PARTY / COMMANDS",
        "Пати по коду",
        "Создание, вход и состав — всё через .party.",
        "02 / 06",
    )

    art.panel(120, 356, 900, 566, 28)
    art.text((156, 388), "КОМАНДЫ ПАТИ", 17, MUTED_DARK, "semibold")
    art.draw.ellipse((918, 394, 930, 406), fill=GREEN)
    art.text((942, 390), "4 участника", 19, MUTED, "semibold")

    # Party code card.
    art.panel(156, 428, 828, 112, 18, fill=PANEL_RAISED, outline=(46, 60, 92, 255))
    art.text((184, 448), "КОД ПАТИ", 15, MUTED_DARK, "semibold")
    art.text((184, 470), "K7M4Q2", 44, ACCENT_LIGHT, "bold")
    art.text((396, 482), "6 символов  ·  регистр не важен", 18, MUTED, "regular")
    art.icon("copy", 854, 466, 26, MUTED)
    art.text((890, 480), "копировать", 17, MUTED_DARK, "regular", "lm")

    art.line(156, 570, 984, 570, (255, 255, 255, 18), 1)

    # Terminal.
    code = art.font("code", 26)
    y = 600
    rows = [
        (">", ".party create", ACCENT, None),
        (None, "Пати создана. Код: K7M4Q2", GREEN, None),
        (">", ".party join K7M4Q2", ACCENT, None),
        (None, "Вы вошли в пати", GREEN, None),
        (">", ".party info", ACCENT, None),
        (None, "Участников: 4   ·   anarchy 7", MUTED, None),
    ]
    for prompt, line, color, _ in rows:
        if prompt:
            art.draw.text((184, y), prompt, font=code, fill=ACCENT)
            art.draw.text((214, y), line, font=code, fill=TEXT)
        else:
            art.draw.text((214, y), line, font=code, fill=color)
        y += 48

    # Cursor.
    art.draw.rectangle((214, y + 4, 226, y + 30), fill=ACCENT)
    art.text((1000, 890), "клиентские команды", 16, MUTED_DARK, "regular", "ra")

    cards = [
        ("key", "Один код", "6 символов, вход без приглашений", "01"),
        ("lock", "AES-256-GCM", "Содержимое пати зашифровано", "02"),
        ("users", "До 8 карточек", "Остальные участники — в футере", "03"),
    ]
    for index, (icon, title, body, number) in enumerate(cards):
        cy = 360 + index * 152
        art.panel(1080, cy, 720, 132, 22, fill=(13, 19, 33, 235), outline=BORDER_SOFT)
        art.draw.rounded_rectangle((1108, cy + 30, 1180, cy + 102), radius=16, fill=(23, 32, 55, 255))
        art.icon(icon, 1125, cy + 47, 38, ACCENT)
        art.text((1206, cy + 30), title, 27, TEXT, "bold")
        art.text((1206, cy + 72), body, 20, MUTED, "regular")
        art.text((1768, cy + 32), number, 18, MUTED_DARK, "semibold", "ra")

    # Party members strip.
    art.panel(1080, 830, 720, 92, 22, fill=(13, 19, 33, 235), outline=BORDER_SOFT)
    for index, hue in enumerate((0, 1, 2, 3)):
        ax = 1108 + index * 40
        art.avatar(ax, 851, 50, hue=hue)
    art.text((1308, 852), "4 участника", 23, TEXT, "bold")
    art.text((1308, 884), "состояние обновляется каждую секунду", 18, MUTED, "regular")

    sparkle(art, 1052, 640, 8, (150, 170, 255, 160))
    return art


def item(art: Art, kind: str, x: float, y: float, size: float) -> None:
    d = art.draw
    pad = size * 0.16

    if kind == "sword":
        d.polygon(
            [
                (x + pad * 1.2, y + size - pad * 1.2),
                (x + size * 0.76, y + pad),
                (x + size * 0.9, y + pad * 1.1),
                (x + pad * 2.3, y + size - pad * 0.8),
            ],
            fill=(190, 211, 255, 255),
        )
        d.line(
            (x + pad * 1.5, y + size - pad * 1.5, x + size * 0.82, y + pad * 1.15),
            fill=(238, 244, 255, 255),
            width=2,
        )
        d.line(
            (x + size * 0.2, y + size * 0.64, x + size * 0.5, y + size * 0.92),
            fill=(214, 168, 89, 255),
            width=max(3, int(size * 0.09)),
        )
        d.line(
            (x + size * 0.12, y + size * 0.86, x + size * 0.36, y + size * 0.62),
            fill=(214, 168, 89, 255),
            width=max(3, int(size * 0.08)),
        )
    elif kind == "pickaxe":
        d.line(
            (x + size * 0.22, y + size * 0.84, x + size * 0.72, y + size * 0.24),
            fill=(164, 118, 68, 255),
            width=max(3, int(size * 0.1)),
        )
        d.arc(
            (x + size * 0.18, y + size * 0.08, x + size * 0.94, y + size * 0.6),
            start=196,
            end=344,
            fill=(170, 196, 255, 255),
            width=max(4, int(size * 0.12)),
        )
    elif kind == "apple":
        d.ellipse((x + size * 0.2, y + size * 0.3, x + size * 0.8, y + size * 0.94), fill=(226, 65, 65, 255))
        d.ellipse((x + size * 0.28, y + size * 0.36, x + size * 0.4, y + size * 0.52), fill=(255, 130, 120, 190))
        d.line(
            (x + size * 0.52, y + size * 0.3, x + size * 0.6, y + size * 0.12),
            fill=(122, 90, 54, 255),
            width=max(2, int(size * 0.06)),
        )
        d.ellipse((x + size * 0.6, y + size * 0.08, x + size * 0.84, y + size * 0.22), fill=(95, 190, 95, 255))
    elif kind == "block":
        d.polygon(
            [
                (x + size * 0.5, y + size * 0.14),
                (x + size * 0.88, y + size * 0.34),
                (x + size * 0.5, y + size * 0.54),
                (x + size * 0.12, y + size * 0.34),
            ],
            fill=(112, 170, 96, 255),
        )
        d.polygon(
            [
                (x + size * 0.12, y + size * 0.34),
                (x + size * 0.5, y + size * 0.54),
                (x + size * 0.5, y + size * 0.92),
                (x + size * 0.12, y + size * 0.72),
            ],
            fill=(104, 78, 53, 255),
        )
        d.polygon(
            [
                (x + size * 0.88, y + size * 0.34),
                (x + size * 0.5, y + size * 0.54),
                (x + size * 0.5, y + size * 0.92),
                (x + size * 0.88, y + size * 0.72),
            ],
            fill=(82, 61, 42, 255),
        )
    elif kind == "potion":
        d.rounded_rectangle(
            (x + size * 0.36, y + size * 0.1, x + size * 0.64, y + size * 0.28),
            radius=3,
            fill=(168, 188, 220, 255),
        )
        d.polygon(
            [
                (x + size * 0.36, y + size * 0.27),
                (x + size * 0.64, y + size * 0.27),
                (x + size * 0.8, y + size * 0.72),
                (x + size * 0.7, y + size * 0.94),
                (x + size * 0.3, y + size * 0.94),
                (x + size * 0.2, y + size * 0.72),
            ],
            fill=(174, 92, 230, 255),
        )
        d.ellipse((x + size * 0.3, y + size * 0.62, x + size * 0.7, y + size * 0.86), fill=(204, 130, 255, 255))
    elif kind == "pearl":
        d.ellipse((x + size * 0.2, y + size * 0.2, x + size * 0.8, y + size * 0.8), fill=(80, 190, 175, 255))
        d.ellipse((x + size * 0.32, y + size * 0.3, x + size * 0.52, y + size * 0.5), fill=(180, 255, 240, 180))
    elif kind == "helmet":
        d.arc(
            (x + size * 0.16, y + size * 0.16, x + size * 0.84, y + size * 0.82),
            start=180,
            end=360,
            fill=(164, 190, 240, 255),
            width=max(4, int(size * 0.12)),
        )
        d.line(
            (x + size * 0.16, y + size * 0.5, x + size * 0.16, y + size * 0.7),
            fill=(164, 190, 240, 255),
            width=max(4, int(size * 0.12)),
        )
        d.line(
            (x + size * 0.84, y + size * 0.5, x + size * 0.84, y + size * 0.7),
            fill=(164, 190, 240, 255),
            width=max(4, int(size * 0.12)),
        )
    elif kind == "chestplate":
        d.polygon(
            [
                (x + size * 0.28, y + size * 0.16),
                (x + size * 0.16, y + size * 0.34),
                (x + size * 0.24, y + size * 0.56),
                (x + size * 0.4, y + size * 0.5),
                (x + size * 0.4, y + size * 0.9),
                (x + size * 0.6, y + size * 0.9),
                (x + size * 0.6, y + size * 0.5),
                (x + size * 0.76, y + size * 0.56),
                (x + size * 0.84, y + size * 0.34),
                (x + size * 0.72, y + size * 0.16),
            ],
            fill=(150, 176, 228, 255),
        )
    elif kind == "leggings":
        d.polygon(
            [
                (x + size * 0.3, y + size * 0.14),
                (x + size * 0.7, y + size * 0.14),
                (x + size * 0.74, y + size * 0.9),
                (x + size * 0.58, y + size * 0.9),
                (x + size * 0.5, y + size * 0.42),
                (x + size * 0.42, y + size * 0.9),
                (x + size * 0.26, y + size * 0.9),
            ],
            fill=(140, 166, 218, 255),
        )
    elif kind == "boots":
        d.polygon(
            [(x + size * 0.2, y + size * 0.3), (x + size * 0.4, y + size * 0.3), (x + size * 0.4, y + size * 0.74), (x + size * 0.2, y + size * 0.74)],
            fill=(136, 162, 214, 255),
        )
        d.polygon(
            [(x + size * 0.6, y + size * 0.3), (x + size * 0.8, y + size * 0.3), (x + size * 0.8, y + size * 0.74), (x + size * 0.6, y + size * 0.74)],
            fill=(136, 162, 214, 255),
        )
        d.polygon(
            [(x + size * 0.14, y + size * 0.74), (x + size * 0.44, y + size * 0.74), (x + size * 0.44, y + size * 0.88), (x + size * 0.14, y + size * 0.88)],
            fill=(136, 162, 214, 255),
        )
        d.polygon(
            [(x + size * 0.56, y + size * 0.74), (x + size * 0.86, y + size * 0.74), (x + size * 0.86, y + size * 0.88), (x + size * 0.56, y + size * 0.88)],
            fill=(136, 162, 214, 255),
        )
    elif kind == "shield":
        d.polygon(
            [
                (x + size * 0.5, y + size * 0.12),
                (x + size * 0.82, y + size * 0.24),
                (x + size * 0.76, y + size * 0.64),
                (x + size * 0.5, y + size * 0.9),
                (x + size * 0.24, y + size * 0.64),
                (x + size * 0.18, y + size * 0.24),
            ],
            fill=(132, 158, 218, 255),
        )
        d.line(
            (x + size * 0.5, y + size * 0.24, x + size * 0.5, y + size * 0.76),
            fill=(220, 230, 255, 190),
            width=2,
        )


def slot(art: Art, x: float, y: float, size: float, item_kind: str | None = None) -> None:
    art.draw.rounded_rectangle(
        (x + 1, y + 1, x + size - 1, y + size - 1),
        radius=5,
        fill=(18, 24, 39, 190),
        outline=(255, 255, 255, 19),
        width=1,
    )
    if item_kind:
        item(art, item_kind, x, y, size)


def hearts(art: Art, x: float, y: float, size: float, full: int, total: int = 10) -> None:
    spacing = size * 1.14
    for index in range(total):
        color = RED if index < full else (53, 35, 43, 255)
        art.icon("heart", x + index * spacing, y, size, color)


def overlay_card(art: Art, x: float, y: float, width: float, name: str, health: str, full_hearts: int,
                 anarchy: str, hue: int, items: dict[int, str], armor: list[str | None],
                 offhand: str | None) -> None:
    scale = 2.5
    slot_size = 16 * scale
    art.avatar(x + 6 * scale, y + 4 * scale, 16 * scale, hue=hue)
    art.text((x + 26 * scale, y + 8 * scale), name, 19, TEXT, "semibold", "lm")
    art.text((x + width - 6 * scale, y + 8 * scale), health, 19, GREEN if full_hearts >= 8 else YELLOW, "semibold", "rm")

    hearts(art, x + 26 * scale, y + 12 * scale, 9 * scale, full_hearts)
    art.text((x + width - 6 * scale, y + 17 * scale), anarchy, 16, RED, "semibold", "rm")

    grid_x = x + 6 * scale
    grid_y = y + 28 * scale
    for cell in range(36):
        row = cell // 9
        column = cell % 9
        cy = grid_y + row * slot_size + (5 if row == 3 else 0)
        slot(art, grid_x + column * slot_size, cy, slot_size, items.get(cell))

    armor_x = x + (6 + 9 * 16 + 4) * scale
    for index, armor_kind in enumerate(armor):
        slot(art, armor_x, grid_y + index * slot_size, slot_size, armor_kind)
    slot(art, armor_x + slot_size, grid_y + 3 * slot_size + 5, slot_size, offhand)


def overlay() -> Art:
    art = Art()
    heading(
        art,
        "ESC / PARTY OVERLAY",
        "Оверлей ESC",
        "Состав пати, сердца и инвентарь поверх меню паузы.",
        "03 / 06",
    )

    # Game menu mockup.
    art.panel(120, 356, 1000, 580, 28, fill=(8, 12, 22, 250), outline=(38, 50, 76, 255))
    art.text((1084, 382), "МЕНЮ ПАУЗЫ", 15, MUTED_DARK, "semibold", "ra")

    # Pause menu buttons.
    for index, (label, bright) in enumerate(
        [("Вернуться в игру", True), ("Настройки...", False), ("Отключиться", False)]
    ):
        by = 540 + index * 90
        art.draw.rounded_rectangle(
            (770, by, 1080, by + 62),
            radius=10,
            fill=(27, 36, 56, 255) if bright else (19, 26, 42, 255),
            outline=(72, 91, 134, 255) if bright else (42, 55, 82, 255),
            width=2,
        )
        art.text((925, by + 31), label, 22, TEXT if bright else MUTED, "semibold", "mm")

    art.text((1080, 880), "Minecraft 1.21.4", 17, MUTED_DARK, "regular", "ra")

    # Party overlay.
    ov_x, ov_y, ov_w = 150, 380, 490
    art.text((ov_x + 6 * 2.5, ov_y + 8), "ПАТИ", 20, ACCENT, "bold")
    art.text((ov_x + ov_w - 15, ov_y + 8), "K7M4Q2", 20, TEXT, "bold", "ra")
    art.text((ov_x + 6 * 2.5, ov_y + 36), "3 участника", 15, MUTED, "regular")
    art.text((ov_x + ov_w - 15, ov_y + 36), "ЛКМ — копировать код", 14, ACCENT, "regular", "ra")

    items_one = {
        0: "sword",
        1: "pickaxe",
        2: "block",
        3: "apple",
        4: "potion",
        10: "block",
        12: "pearl",
        18: "apple",
        27: "block",
        28: "potion",
        29: "sword",
    }
    items_two = {
        0: "pickaxe",
        1: "sword",
        3: "pearl",
        9: "apple",
        11: "block",
        27: "apple",
        28: "block",
        29: "potion",
    }
    overlay_card(
        art,
        ov_x,
        ov_y + 65,
        ov_w,
        "TOP3R_PVP",
        "18.5/20",
        9,
        "anarchy 7",
        2,
        items_one,
        ["helmet", "chestplate", "leggings", "boots"],
        "shield",
    )
    overlay_card(
        art,
        ov_x,
        ov_y + 305,
        ov_w,
        "VQ",
        "11.0/20",
        5,
        "anarchy 7",
        1,
        items_two,
        ["helmet", None, "leggings", "boots"],
        "shield",
    )

    art.text((120, 946), "Схема интерфейса, не игровой скриншот", 14, MUTED_DARK, "regular")

    # Callouts.
    callouts = [
        ("heart", "Ванильные сердца", "HP, максимум и поглощение"),
        ("grid", "Инвентарь 9×4", "27 слотов + хотбар"),
        ("shield", "Броня и оффхенд", "4 слота брони и щит"),
        ("chat", "Превью шалкеров", "Содержимое по наведению"),
    ]
    for index, (icon, title, body) in enumerate(callouts):
        cy = 380 + index * 140
        art.panel(1200, cy, 600, 124, 22, fill=(13, 19, 33, 235), outline=BORDER_SOFT)
        art.draw.rounded_rectangle((1228, cy + 36, 1280, cy + 88), radius=14, fill=(23, 32, 55, 255))
        art.icon(icon, 1242, cy + 50, 24, ACCENT)
        art.text((1308, cy + 33), title, 25, TEXT, "bold")
        art.text((1308, cy + 73), body, 19, MUTED, "regular")

    sparkle(art, 1150, 480, 8, (150, 170, 255, 150))
    return art


def iso_block(art: Art, cx: float, top_y: float, size: float, height: float, ring: bool = False) -> None:
    d = art.draw
    top = [(cx, top_y), (cx + size, top_y + size * 0.5), (cx, top_y + size), (cx - size, top_y + size * 0.5)]
    left = [
        (cx - size, top_y + size * 0.5),
        (cx, top_y + size),
        (cx, top_y + size + height),
        (cx - size, top_y + size * 0.5 + height),
    ]
    right = [
        (cx + size, top_y + size * 0.5),
        (cx, top_y + size),
        (cx, top_y + size + height),
        (cx + size, top_y + size * 0.5 + height),
    ]
    d.polygon(left, fill=(42, 70, 49, 255))
    d.polygon(right, fill=(31, 52, 38, 255))
    d.polygon(top, fill=(68, 112, 75, 255))

    # Subtle top-face grid.
    for step in (0.33, 0.66):
        d.line(
            (
                cx - size * (1 - step),
                top_y + size * 0.5 * (1 + step),
                cx + size * step,
                top_y + size * (0.5 + 0.5 * step),
            ),
            fill=(255, 255, 255, 12),
            width=1,
        )
        d.line(
            (
                cx + size * (1 - step),
                top_y + size * 0.5 * (1 + step),
                cx - size * step,
                top_y + size * (0.5 + 0.5 * step),
            ),
            fill=(255, 255, 255, 12),
            width=1,
        )
    d.line((cx - size, top_y + size * 0.5, cx, top_y + size), fill=(255, 255, 255, 24), width=2)
    d.line((cx + size, top_y + size * 0.5, cx, top_y + size), fill=(255, 255, 255, 18), width=2)

    if ring:
        ring_glow = Image.new("RGBA", (art.width, art.height), (0, 0, 0, 0))
        rd = ImageDraw.Draw(ring_glow)
        for radius, alpha, width in ((size * 0.62, 90, 12), (size * 0.48, 160, 5), (size * 0.32, 100, 3)):
            rd.ellipse(
                (cx - radius, top_y + size * 0.5 - radius * 0.42, cx + radius, top_y + size * 0.5 + radius * 0.42),
                outline=(133, 156, 255, alpha),
                width=width,
            )
        ring_glow = ring_glow.filter(ImageFilter.GaussianBlur(10))
        art.img.alpha_composite(ring_glow)
        d.ellipse(
            (cx - size * 0.48, top_y + size * 0.5 - size * 0.2, cx + size * 0.48, top_y + size * 0.5 + size * 0.2),
            outline=ACCENT_LIGHT,
            width=4,
        )
        d.ellipse(
            (cx - size * 0.32, top_y + size * 0.5 - size * 0.135, cx + size * 0.32, top_y + size * 0.5 + size * 0.135),
            outline=(133, 156, 255, 180),
            width=2,
        )


def marks() -> Art:
    art = Art()
    heading(
        art,
        "MARKS / WORLD",
        "Метки в мире",
        "Блок под прицелом, звук у получателей и подпись с расстоянием.",
        "04 / 06",
    )

    art.panel(120, 356, 900, 580, 28, fill=(8, 12, 21, 238), outline=(36, 47, 70, 255))

    # Background blocks for depth.
    iso_block(art, 274, 790, 76, 60)
    iso_block(art, 806, 806, 58, 46)
    iso_block(art, 760, 708, 34, 28)

    # Beam above the marked block.
    beam = Image.new("RGBA", (art.width, art.height), (0, 0, 0, 0))
    bd = ImageDraw.Draw(beam)
    bd.rectangle((438, 208, 462, 545), fill=(133, 156, 255, 46))
    bd.rectangle((446, 208, 454, 545), fill=(190, 205, 255, 90))
    beam = beam.filter(ImageFilter.GaussianBlur(9))
    art.img.alpha_composite(beam)
    art.line(450, 220, 450, 520, (190, 205, 255, 120), 2)

    iso_block(art, 450, 430, 220, 180, ring=True)

    # Mark pin above the ring.
    art.draw.ellipse((422, 454, 478, 510), fill=(8, 12, 21, 245))
    art.icon("pin", 436, 462, 28, ACCENT_LIGHT, 3)
    art.draw.ellipse((444, 468, 456, 480), fill=(8, 12, 21, 255))

    # Label card.
    art.panel(620, 424, 328, 82, 18, fill=(10, 15, 28, 246), outline=(44, 58, 88, 255))
    art.avatar(640, 443, 44, hue=2)
    art.text((696, 444), "TOP3R_PVP", 21, TEXT, "semibold")
    art.text((696, 470), "метка пати", 15, MUTED_DARK, "regular")
    art.icon("pin", 836, 454, 20, ACCENT)
    art.text((866, 463), "24 м", 21, ACCENT_LIGHT, "semibold", "lm")

    art.text((160, 880), "до 128 блоков", 18, MUTED_DARK, "semibold")
    art.text((160, 906), "метка ставится на блок под прицелом", 15, MUTED_DARK, "regular")

    callouts = [
        ("pin", "До 128 блоков", "метка ставится под прицелом"),
        ("sound", "Звук у получателей", "громкость настраивается"),
        ("users", "5 секунд", "метку видит вся пати"),
        ("key", "Метки друзьям", "отдельная клавиша и канал"),
    ]
    for index, (icon, title, body) in enumerate(callouts):
        cy = 380 + index * 144
        art.panel(1100, cy, 700, 128, 22, fill=(13, 19, 33, 235), outline=BORDER_SOFT)
        art.draw.rounded_rectangle((1128, cy + 34, 1184, cy + 90), radius=15, fill=(23, 32, 55, 255))
        art.icon(icon, 1143, cy + 49, 27, ACCENT)
        art.text((1212, cy + 31), title, 26, TEXT, "bold")
        art.text((1212, cy + 74), body, 19, MUTED, "regular")

    art.icon("check", 1100, 960, 18, GREEN, 3)
    art.text((1130, 958), "Фильтры по серверу, измерению и анархии", 17, MUTED_DARK, "regular")

    sparkle(art, 1060, 690, 8, (150, 170, 255, 160))
    return art


def settings() -> Art:
    art = Art()
    heading(
        art,
        "GUI / SETTINGS",
        "Настройки мода",
        "Right Shift открывает компактное меню Communication.",
        "05 / 06",
    )

    scale = 2.05
    ox, oy = 150.0, 344.0
    panel_w, panel_h = 420.0, 310.0

    def px(value: float) -> float:
        return ox + value * scale

    def py(value: float) -> float:
        return oy + value * scale

    def ps(value: float) -> float:
        return value * scale

    # Soft backdrop: the screen sits over a dimmed game view.
    art.draw.rounded_rectangle(
        (ox - 22, oy - 22, ox + ps(panel_w) + 22, oy + ps(panel_h) + 22),
        radius=20,
        fill=(6, 9, 17, 150),
        outline=(52, 68, 104, 120),
        width=1,
    )

    art.draw.rounded_rectangle(
        (px(0), py(0), px(panel_w), py(panel_h)),
        radius=ps(9),
        fill=(13, 18, 31, 252),
        outline=(44, 57, 86, 255),
        width=2,
    )

    # Header.
    art.draw.rounded_rectangle(
        (px(20), py(14), px(23), py(33)), radius=3, fill=ACCENT
    )
    art.text((px(30), py(23)), "COMMUNICATION", 25, TEXT, "bold", "lm")
    art.text((px(30), py(38)), "Пати, метки и оверлей ESC", 14, MUTED, "regular", "lm")
    art.text((px(372), py(23)), "v1.1.0", 13, MUTED, "regular", "rm")
    art.draw.rounded_rectangle(
        (px(384), py(12), px(404), py(32)), radius=10, fill=(22, 29, 48, 255), outline=(45, 58, 88, 255), width=1
    )
    art.text((px(394), py(22)), "X", 15, MUTED, "semibold", "mm")

    art.line(px(20), py(50), px(400), py(50), (37, 48, 74, 255), 2)

    # Master switch row.
    art.draw.rounded_rectangle(
        (px(16), py(58), px(404), py(92)), radius=ps(6), fill=(19, 25, 42, 255), outline=(37, 48, 74, 255), width=1
    )
    art.draw.ellipse((px(28), py(70), px(38), py(80)), fill=ACCENT)
    art.text((px(46), py(69)), "Communication", 18, TEXT, "semibold", "lm")
    art.text((px(46), py(81)), "Функции включены", 13, MUTED, "regular", "lm")
    art.draw.rounded_rectangle((px(358), py(68), px(392), py(82)), radius=7, fill=ACCENT)
    art.draw.ellipse((px(378), py(71), px(386), py(79)), fill=WHITE)

    # Party section.
    art.text((px(20), py(100)), "ПАТИ", 14, MUTED, "semibold", "lm")
    art.text((px(400), py(100)), "Код K7M4Q2  /  участников: 4", 13, TEXT, "regular", "rm")

    art.draw.rounded_rectangle(
        (px(20), py(110), px(108), py(132)), radius=ps(4.5), fill=(22, 29, 48, 255),
        outline=(40, 52, 80, 255), width=1,
    )
    art.text((px(64), py(121)), "Создать", 14, MUTED_DARK, "semibold", "mm")

    art.draw.rounded_rectangle(
        (px(116), py(110), px(264), py(132)), radius=ps(4.5), fill=(19, 25, 42, 255),
        outline=ACCENT, width=2,
    )
    art.text((px(124), py(121)), "K7M4Q2", 16, TEXT, "code", "lm")
    art.draw.rectangle((px(206), py(116), px(208), py(126)), fill=ACCENT)

    for bx, bw, label in ((272, 64, "Войти"), (344, 56, "Выйти")):
        art.draw.rounded_rectangle(
            (px(bx), py(110), px(bx + bw), py(132)), radius=ps(4.5), fill=(22, 29, 48, 255),
            outline=(40, 52, 80, 255), width=1,
        )
        art.text((px(bx + bw / 2), py(121)), label, 14, TEXT, "semibold", "mm")

    art.text(
        (px(20), py(145)),
        "Друзьям: .party join K7M4Q2   /   код копируется по ЛКМ в ESC",
        13,
        MUTED,
        "regular",
        "lm",
    )
    art.line(px(20), py(157), px(400), py(157), (37, 48, 74, 255), 1)

    # Bind rows.
    art.text((px(20), py(167)), "МЕТКИ", 14, MUTED, "semibold", "lm")
    art.text((px(400), py(167)), "здесь или в управлении", 13, MUTED, "regular", "rm")

    def bind_row(y: float, title: str, value: str) -> None:
        art.draw.rounded_rectangle(
            (px(16), py(y), px(404), py(y + 24)), radius=ps(5), fill=(19, 25, 42, 255),
            outline=(33, 43, 67, 255), width=1,
        )
        art.text((px(28), py(y + 12)), title, 16, TEXT, "semibold", "lm")
        art.draw.rounded_rectangle(
            (px(250), py(y + 3), px(400), py(y + 21)), radius=ps(4), fill=(25, 33, 53, 255),
            outline=(45, 58, 88, 255), width=1,
        )
        art.text((px(325), py(y + 12)), value, 14, ACCENT_LIGHT, "semibold", "mm")

    bind_row(178, "Метка пати", "X")
    bind_row(206, "Метка друзьям", "C")

    # Volume row.
    art.draw.rounded_rectangle(
        (px(16), py(234), px(404), py(258)), radius=ps(5), fill=(19, 25, 42, 255),
        outline=(33, 43, 67, 255), width=1,
    )
    art.text((px(28), py(246)), "Громкость метки пати", 16, TEXT, "semibold", "lm")
    art.draw.rounded_rectangle(
        (px(250), py(243), px(360), py(249)), radius=3, fill=(35, 46, 72, 255)
    )
    art.draw.rounded_rectangle((px(250), py(243), px(338), py(249)), radius=3, fill=ACCENT)
    art.draw.rounded_rectangle((px(335), py(241), px(341), py(251)), radius=3, fill=(214, 223, 255, 255))
    art.text((px(400), py(246)), "80%", 15, TEXT, "semibold", "rm")

    art.text(
        (px(210), py(284)),
        "Right Shift — меню  /  ESC — закрыть  /  метки — в управлении",
        13,
        MUTED,
        "regular",
        "mm",
    )

    art.text((150, 1000), "Схема интерфейса, не игровой скриншот", 14, MUTED_DARK, "regular")

    # Callouts.
    callouts = [
        ("key", "Right Shift", "фиксированная клавиша меню"),
        ("chat", "Пати в один клик", "создать, войти или выйти"),
        ("grid", "Клавиши меток", "здесь или в «Управлении»"),
        ("sound", "Громкость", "звук метки от 0 до 100%"),
    ]
    for index, (icon, title, body) in enumerate(callouts):
        cy = 356 + index * 146
        art.panel(1058, cy, 742, 128, 22, fill=(13, 19, 33, 235), outline=BORDER_SOFT)
        art.draw.rounded_rectangle((1086, cy + 34, 1142, cy + 90), radius=15, fill=(23, 32, 55, 255))
        art.icon(icon, 1101, cy + 49, 27, ACCENT)
        art.text((1170, cy + 31), title, 26, TEXT, "bold")
        art.text((1170, cy + 74), body, 19, MUTED, "regular")

    art.icon("lock", 1058, 960, 16, GREEN, 3)
    art.text((1088, 956), "Настройки сохраняются сразу", 17, MUTED_DARK, "regular")

    sparkle(art, 1030, 560, 8, (150, 170, 255, 160))
    return art


def install() -> Art:
    art = Art()
    heading(
        art,
        "INSTALL / FABRIC",
        "Установка",
        "Три шага: Fabric Loader, Fabric API и jar в папку mods.",
        "06 / 06",
    )

    steps = [
        ("01", "monitor", "Fabric Loader", "Установи Fabric Loader для Minecraft 1.21.4\nи запускай игру через профиль Fabric."),
        ("02", "grid", "Fabric API", "Помести fabric-api в папку mods.\nБез него мод не загрузится."),
        ("03", "bolt", "Communication", "Помести communication-1.1.0.jar в mods,\nзатем перезапусти Minecraft."),
    ]
    for index, (number, icon, title, body) in enumerate(steps):
        sy = 356 + index * 176
        art.panel(120, sy, 980, 156, 24, fill=(13, 19, 33, 236), outline=BORDER_SOFT)
        art.draw.rounded_rectangle((152, sy + 42, 224, sy + 114), radius=18, fill=(23, 32, 55, 255))
        art.icon(icon, 170, sy + 59, 36, ACCENT)
        art.text((252, sy + 30), number, 15, MUTED_DARK, "semibold")
        art.text((252, sy + 52), title, 32, TEXT, "bold")
        art.text((252, sy + 98), body, 20, MUTED, "regular", spacing=9)
        if index < len(steps) - 1:
            art.line(196, sy + 156, 196, sy + 176, (60, 76, 112, 255), 2)

    # Requirements panel.
    art.panel(1140, 356, 660, 452, 24, fill=(13, 19, 33, 236), outline=BORDER_SOFT)
    art.text((1172, 384), "ТРЕБОВАНИЯ", 17, MUTED_DARK, "semibold")
    requirements = [
        ("Minecraft", "1.21.4"),
        ("Fabric Loader", "0.18.4+"),
        ("Fabric API", "0.119.4+1.21.4"),
        ("Java", "21"),
    ]
    for index, (name, version) in enumerate(requirements):
        ry = 424 + index * 62
        art.text((1172, ry + 20), name, 21, TEXT, "regular", "lm")
        art.text((1768, ry + 20), version, 20, ACCENT_LIGHT, "semibold", "rm")
        art.line(1172, ry + 46, 1768, ry + 46, (255, 255, 255, 16), 1)

    # Side note.
    art.panel(1140, 832, 660, 90, 20, fill=(15, 23, 40, 245), outline=(46, 62, 96, 255))
    art.draw.ellipse((1164, 862, 1194, 892), fill=(30, 47, 34, 255))
    art.icon("check", 1170, 868, 18, GREEN, 3)
    art.text((1206, 856), "Мод клиентский", 21, TEXT, "bold")
    art.text((1206, 884), "установка на сервер не нужна", 18, MUTED, "regular")

    # Bottom note.
    art.panel(120, 900, 980, 74, 18, fill=(12, 18, 32, 230), outline=(38, 50, 78, 255))
    art.icon("shield", 148, 918, 22, ACCENT)
    art.text(
        (184, 922),
        "Проверка: в логе есть Communication initialized, а в config появился communication.json.",
        19,
        MUTED,
        "regular",
        "lm",
    )

    sparkle(art, 1096, 700, 8, (150, 170, 255, 160))
    return art


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    images = {
        "01-hero.png": hero(),
        "02-party.png": party(),
        "03-overlay.png": overlay(),
        "04-marks.png": marks(),
        "05-settings.png": settings(),
        "06-install.png": install(),
    }
    for name, art in images.items():
        art.save(OUT / name)


if __name__ == "__main__":
    main()
