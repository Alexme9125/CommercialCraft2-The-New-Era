#!/usr/bin/env python3
"""Deterministic pixel-art texture generator for TycoonCraft²: The New Era (tyc2).

Generates all original CC BY-SA 4.0 textures under assets/tyc2/textures/:
  - 9 Industrial Coin items (16x16): coin_1 .. coin_1000
  - 2 Communication Terminal item states (16x16): comm_terminal, comm_terminal_empty
  - 18 Block textures (16x16):
      * primitive_mint_machine_{front,front_active,side,top,bottom}
      * mint_machine_{front,front_active,side,top,bottom}
      * atm_machine_{front,side,top,bottom}
      * cooperation_hub_{front,side,top,bottom}
  - 6 Industrial GUI atlases (256x256):
      * gui/primitive_mint_machine.png (panel 176x166 + sprites at u=176..202)
      * gui/mint_machine.png           (panel 176x176 + sprites at u=176..210)
      * gui/atm_machine.png            (panel 220x214 + sprites at u=224..255)
      * gui/cooperation_hub.png        (panel 248x222 + sprites at v=226..254)
      * gui/comm_terminal.png          (panel 248x210 + sprites at v=216..248)
      * gui/mayor_negotiation.png      (panel 220x192 + sprites at v=196..240)

Also generates a crisp labeled preview sheet at:
  assets/tyc2/preview/texture_atlas_preview.png

Usage:
  python3 src/datagen/generate_textures.py
  python3 src/datagen/generate_textures.py --output <tex_dir> --preview <preview_png>
"""

from __future__ import annotations

import argparse
from pathlib import Path
from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parents[2]
TEX_ROOT = REPO_ROOT / "assets" / "tyc2" / "textures"
DEFAULT_PREVIEW = REPO_ROOT / "assets" / "tyc2" / "preview" / "texture_atlas_preview.png"


# =============================================================================
# Core Drawing Helpers
# =============================================================================

def rgba(hex_color: str, alpha: int = 255) -> tuple[int, int, int, int]:
    h = hex_color.lstrip("#")
    if len(h) == 8:
        return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16))
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def render_grid(grid: list[str], palette: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    h = len(grid)
    w = len(grid[0])
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(grid):
        assert len(row) == w, f"Row {y} width {len(row)} != {w}"
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = palette[ch]
    return im


def fill_rect(px, x0: int, y0: int, x1: int, y1: int, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = col


def hline(px, x0: int, x1: int, y: int, col):
    for x in range(x0, x1 + 1):
        px[x, y] = col


def vline(px, x: int, y0: int, y1: int, col):
    for y in range(y0, y1 + 1):
        px[x, y] = col


def draw_steel_casing(tone: str = "standard") -> Image.Image:
    """Create a crisp 16x16 industrial steel machine block face."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()
    if tone == "dark":
        c_out = rgba("#141920")
        c_hi = rgba("#8E9FB4")
        c_mid = rgba("#5D6E82")
        c_base = rgba("#455365")
        c_sh = rgba("#2A3442")
        c_deep = rgba("#1C232D")
        c_spec = rgba("#B4C6DA")
    elif tone == "sleek":
        # Brushed silver-steel for Industrial ATM
        c_out = rgba("#171E26")
        c_hi = rgba("#C4D4E4")
        c_mid = rgba("#92A5B8")
        c_base = rgba("#728599")
        c_sh = rgba("#475666")
        c_deep = rgba("#29333E")
        c_spec = rgba("#E4EFFA")
    else:
        # Heavy industrial steel for Minting Machine
        c_out = rgba("#161B23")
        c_hi = rgba("#A9B9CC")
        c_mid = rgba("#7B8B9E")
        c_base = rgba("#5E6F83")
        c_sh = rgba("#3A4656")
        c_deep = rgba("#232B36")
        c_spec = rgba("#CFE0F2")

    fill_rect(px, 0, 0, 15, 15, c_out)
    fill_rect(px, 1, 1, 14, 14, c_mid)
    hline(px, 1, 14, 1, c_hi)
    vline(px, 1, 1, 14, c_hi)
    hline(px, 1, 14, 14, c_sh)
    vline(px, 14, 1, 14, c_sh)
    px[1, 1] = c_spec
    px[14, 1] = c_mid
    px[1, 14] = c_mid
    px[14, 14] = c_deep

    fill_rect(px, 2, 2, 13, 13, c_base)
    hline(px, 2, 13, 2, c_sh)
    vline(px, 2, 2, 13, c_sh)
    hline(px, 2, 13, 13, c_hi)
    vline(px, 13, 2, 13, c_hi)

    for y in range(3, 13):
        for x in range(3, 13):
            if (x * 7 + y * 13) % 11 == 0:
                px[x, y] = c_mid
            elif (x * 5 + y * 3) % 13 == 0:
                px[x, y] = c_sh

    for bx, by in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[bx, by] = c_deep
    px[1, 2] = c_spec
    px[2, 1] = c_spec
    return im


def draw_hub_masonry_frame() -> Image.Image:
    """Create a 16x16 monumental reinforced-stone + gilded-bronze frame for Cooperation Hub."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()

    c_out = rgba("#1C1A18")
    st_hi = rgba("#888D94")
    st_mid = rgba("#656A72")
    st_base = rgba("#4E535B")
    st_sh = rgba("#353940")

    au_sp = rgba("#FFF2A8")
    au_hi = rgba("#F0C246")
    au_mid = rgba("#C79124")
    au_dk = rgba("#7E5410")

    # Dark border & stone masonry body
    fill_rect(px, 0, 0, 15, 15, c_out)
    fill_rect(px, 1, 1, 14, 14, st_base)

    # Subtle stone brick grain
    for y in range(2, 14):
        for x in range(2, 14):
            if (x * 11 + y * 7) % 13 == 0:
                px[x, y] = st_mid
            elif (x * 3 + y * 5) % 11 == 0:
                px[x, y] = st_sh

    # Stone bevel border
    hline(px, 1, 14, 1, st_hi)
    vline(px, 1, 1, 14, st_hi)
    hline(px, 1, 14, 14, st_sh)
    vline(px, 14, 1, 14, st_sh)

    # Ornate gilded-bronze horizontal moldings (top y=1..2 and bottom y=13..14)
    hline(px, 2, 13, 1, au_hi)
    hline(px, 2, 13, 2, au_mid)
    hline(px, 2, 13, 13, au_mid)
    hline(px, 2, 13, 14, au_dk)

    # Gilded-bronze corner pilasters (left x=1..2 and right x=13..14)
    vline(px, 1, 2, 13, au_hi)
    vline(px, 2, 3, 12, au_mid)
    vline(px, 13, 3, 12, au_mid)
    vline(px, 14, 2, 13, au_dk)

    # Corner gold studs
    px[1, 1] = au_sp
    px[2, 2] = au_sp
    px[14, 1] = au_hi
    px[13, 2] = au_hi
    px[1, 14] = au_mid
    px[14, 14] = au_dk
    return im


# =============================================================================
# 1. NINE DISTINCT INDUSTRIAL COIN TEXTURES (16x16)
# =============================================================================

def _make_coin_base(pal: dict[str, tuple[int, int, int, int]]) -> Image.Image:
    """Render a mathematically symmetric 16x16 industrial octagonal coin with top-left bevel lighting."""
    grid = [
        "................",
        ".....oooooo.....",
        "....oHHHHHRo....",
        "...oHHRSSSRRo...",
        "..oHRSSFFFFRDo..",
        ".oHRSFFFFFFFRDo.",
        ".oHRFFFFFFFFRDo.",
        ".oHRFFFFFFFFRDo.",
        ".oHRFFFFFFFMRDo.",
        ".oHRFFFFFFMMRDo.",
        ".oRRFFFFFMMMDDo.",
        "..oRRFFFMMMDDo..",
        "...oRRMMMDDDo...",
        "....oRDDDDDo....",
        ".....oooooo.....",
        "................",
    ]
    return render_grid(grid, pal)


def make_coin_1() -> Image.Image:
    """1x: Brushed silver-iron octagonal coin with clean '1' industrial stamp."""
    pal = {
        "o": rgba("#1E252E"),
        "H": rgba("#F0F5FA"),
        "S": rgba("#DCE5EE"),
        "R": rgba("#AEBCCC"),
        "F": rgba("#C2CEDB"),
        "M": rgba("#8E9CAE"),
        "D": rgba("#5A6778"),
    }
    im = _make_coin_base(pal)
    px = im.load()
    # Recessed inner octagon groove
    groove_dk = rgba("#6B7888")
    groove_lt = rgba("#E8F0F7")
    for x in range(5, 11):
        px[x, 3] = groove_dk
        px[x, 12] = groove_lt
    for y in range(5, 11):
        px[3, y] = groove_dk
        px[12, y] = groove_lt

    # Crisp stamped numeral '1' (dark engraved core + bright top-left bevel catch)
    stamp = rgba("#384352")
    hi = rgba("#FFFFFF")
    num_1 = [
        (7, 5), (8, 5),
        (6, 6), (7, 6), (8, 6),
        (7, 7), (8, 7),
        (7, 8), (8, 8),
        (7, 9), (8, 9),
        (6, 10), (7, 10), (8, 10), (9, 10),
    ]
    for x, y in num_1:
        px[x, y] = stamp
    for x, y in ((6, 5), (5, 6), (6, 7), (6, 8), (6, 9), (9, 6), (9, 7)):
        if (x, y) not in num_1:
            px[x, y] = hi
    # Specular glints
    px[5, 2] = rgba("#FFFFFF")
    px[4, 3] = rgba("#FFFFFF")
    return im


def make_coin_5() -> Image.Image:
    """5x: Burnished copper-bronze octagonal coin with riveted rim & crisp '5' stamp."""
    pal = {
        "o": rgba("#301609"),
        "H": rgba("#FFCFA3"),
        "S": rgba("#F5AD73"),
        "R": rgba("#D1783B"),
        "F": rgba("#E08A4B"),
        "M": rgba("#B35E25"),
        "D": rgba("#783710"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    # Riveted copper rim studs at 4 cardinal points + 4 diagonals
    riv_hi = rgba("#FFE4C7")
    riv_dk = rgba("#5E290A")
    for rx, ry in ((7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)):
        px[rx, ry] = riv_hi if (rx + ry < 15) else riv_dk

    # Crisp stamped '5'
    stamp = rgba("#542107")
    hi = rgba("#FFE6C9")
    num_5 = [
        (6, 5), (7, 5), (8, 5), (9, 5),
        (6, 6),
        (6, 7), (7, 7), (8, 7),
        (9, 8),
        (9, 9),
        (6, 10), (7, 10), (8, 10),
    ]
    for x, y in num_5:
        px[x, y] = stamp
    # Highlight under/beside stamp for embossed copper depth
    for x, y in ((7, 6), (8, 6), (9, 6), (6, 8), (7, 8), (8, 8), (7, 9), (8, 9)):
        px[x, y] = hi
    px[5, 2] = rgba("#FFF0DC")
    px[4, 4] = rgba("#FFE0BD")
    return im


def make_coin_10() -> Image.Image:
    """10x: Tempered blue-steel octagonal coin with machined bevel & bright 'X' emblem."""
    pal = {
        "o": rgba("#0F1B2B"),
        "H": rgba("#CBE8FF"),
        "S": rgba("#91C6F2"),
        "R": rgba("#528BC2"),
        "F": rgba("#69A3D9"),
        "M": rgba("#3C6EA3"),
        "D": rgba("#23446B"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    # Machined steel rim notches (subtle dark teeth on corners)
    notch = rgba("#1A3252")
    glint = rgba("#EBF7FF")
    for nx, ny in ((4, 3), (11, 3), (3, 4), (12, 4), (3, 11), (12, 11), (4, 12), (11, 12)):
        px[nx, ny] = notch
    px[5, 2] = glint
    px[6, 2] = glint
    px[2, 5] = glint

    # Bold industrial Roman 'X' (10) emblem with metallic bevel
    dark_x = rgba("#152B47")
    bright_x = rgba("#E8F6FF")
    x_pts = [
        (5, 5), (6, 5), (9, 5), (10, 5),
        (6, 6), (7, 6), (8, 6), (9, 6),
        (7, 7), (8, 7),
        (7, 8), (8, 8),
        (6, 9), (7, 9), (8, 9), (9, 9),
        (5, 10), (6, 10), (9, 10), (10, 10),
    ]
    for x, y in x_pts:
        px[x, y] = dark_x
    # Specular center cross highlight
    for x, y in ((5, 5), (6, 6), (7, 7), (9, 5), (8, 7)):
        px[x, y] = bright_x
    px[8, 8] = rgba("#88C8FF")
    return im


def make_coin_20() -> Image.Image:
    """20x: Silver-emerald inlaid octagonal coin with twin emerald rim pips & emerald gem center."""
    pal = {
        "o": rgba("#12261C"),
        "H": rgba("#F2FBF6"),
        "S": rgba("#D4EAE0"),
        "R": rgba("#9EB8AD"),
        "F": rgba("#B8CFC5"),
        "M": rgba("#7C968B"),
        "D": rgba("#496158"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    em_sp = rgba("#B8FFD4")
    em_hi = rgba("#47F289")
    em_md = rgba("#1CC45E")
    em_dk = rgba("#0C7A38")
    em_bd = rgba("#0A4A23")

    # Twin emerald rim pips on left & right (plus top/bottom emerald ticks)
    for px_x, px_y in ((2, 7), (2, 8), (13, 7), (13, 8)):
        px[px_x, px_y] = em_hi if px_y == 7 else em_md
    px[7, 2] = em_hi
    px[8, 2] = em_md
    px[7, 13] = em_md
    px[8, 13] = em_dk

    # Faceted octagonal emerald inlaid diamond in center
    em_grid = [
        (7, 4, em_bd), (8, 4, em_bd),
        (6, 5, em_bd), (7, 5, em_sp), (8, 5, em_hi), (9, 5, em_bd),
        (5, 6, em_bd), (6, 6, em_sp), (7, 6, em_hi), (8, 6, em_md), (9, 6, em_md), (10, 6, em_bd),
        (5, 7, em_bd), (6, 7, em_hi), (7, 7, em_sp), (8, 7, em_hi), (9, 7, em_dk), (10, 7, em_bd),
        (5, 8, em_bd), (6, 8, em_md), (7, 8, em_hi), (8, 8, em_md), (9, 8, em_dk), (10, 8, em_bd),
        (5, 9, em_bd), (6, 9, em_md), (7, 9, em_md), (8, 9, em_dk), (9, 9, em_dk), (10, 9, em_bd),
        (6, 10, em_bd), (7, 10, em_dk), (8, 10, em_dk), (9, 10, em_bd),
        (7, 11, em_bd), (8, 11, em_bd),
    ]
    for x, y, col in em_grid:
        px[x, y] = col
    return im


def make_coin_50() -> Image.Image:
    """50x: Bi-metallic gilded coin — golden outer rim & brushed silver center with gold crest."""
    # Gold rim palette
    pal = {
        "o": rgba("#332006"),
        "H": rgba("#FFF3AD"),
        "S": rgba("#FFE066"),
        "R": rgba("#E5AE2B"),
        "F": rgba("#C9D6E3"),  # brushed silver center!
        "M": rgba("#9AAAB8"),
        "D": rgba("#8C580B"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    # Thicken gold outer ring cleanly (bi-metallic look!)
    au_hi = rgba("#FFE678")
    au_md = rgba("#E0A624")
    au_dk = rgba("#9E660E")
    ag_hi = rgba("#EEF5FC")
    ag_md = rgba("#BDCAD6")
    ag_dk = rgba("#798899")

    # Crisp 2px-wide gold ring border around silver inner medallion (x=4..11, y=4..11)
    for x in range(5, 11):
        px[x, 3] = au_hi
        px[x, 12] = au_dk
    for y in range(5, 11):
        px[3, y] = au_hi
        px[12, y] = au_dk
    px[4, 4] = au_md
    px[11, 4] = au_md
    px[4, 11] = au_md
    px[11, 11] = au_dk

    # Silver inner field sheen
    fill_rect(px, 5, 5, 10, 10, ag_md)
    hline(px, 5, 10, 4, ag_hi)
    vline(px, 4, 5, 10, ag_hi)
    hline(px, 5, 10, 11, ag_dk)
    vline(px, 11, 5, 10, ag_dk)
    px[5, 5] = rgba("#FFFFFF")
    px[6, 5] = ag_hi

    # Gold crest emblem (high-contrast royal chevron-shield for 50x)
    crest_sh = rgba("#6E4106")
    crest = [
        (5, 6), (10, 6),
        (5, 7), (6, 7), (9, 7), (10, 7),
        (6, 8), (7, 8), (8, 8), (9, 8),
        (7, 9), (8, 9),
        (7, 10), (8, 10),
    ]
    for x, y in crest:
        if y + 1 <= 10:
            px[x, y + 1] = crest_sh
    for x, y in crest:
        px[x, y] = au_md
    for x, y in ((5, 6), (6, 7), (7, 8), (7, 5), (8, 5)):
        px[x, y] = au_hi
    px[7, 6] = crest_sh
    px[8, 6] = crest_sh
    return im


def make_coin_100() -> Image.Image:
    """100x: Pure radiant gold sovereign coin with notched/crowned gold bezel."""
    pal = {
        "o": rgba("#381D03"),
        "H": rgba("#FFFAD1"),
        "S": rgba("#FFE875"),
        "R": rgba("#F2BD29"),
        "F": rgba("#FFD43B"),
        "M": rgba("#D48F14"),
        "D": rgba("#8C4F06"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    # Crowned top sovereign notch into row y=0..1!
    c_out = rgba("#381D03")
    c_sp = rgba("#FFFFE0")
    c_hi = rgba("#FFE866")
    c_md = rgba("#E8A81A")
    c_dk = rgba("#9E5B08")

    # Small royal crown nibs along top rim (y=0..2)
    px[5, 0] = c_out
    px[6, 0] = c_out
    px[7, 0] = c_hi
    px[8, 0] = c_hi
    px[9, 0] = c_out
    px[10, 0] = c_out
    px[6, 1] = c_sp
    px[7, 1] = c_sp
    px[8, 1] = c_hi
    px[9, 1] = c_md

    # Notched sovereign rim glints
    for x, y in ((2, 6), (2, 9), (13, 6), (13, 9), (6, 13), (9, 13)):
        px[x, y] = c_dk

    # Embossed radiant industrial gear / sunburst sovereign emblem in center
    em_dk = rgba("#8F4E05")
    em_sp = rgba("#FFFFE8")
    # Outer ring
    for x in range(6, 10):
        px[x, 5] = em_dk
        px[x, 10] = em_dk
    for y in range(6, 10):
        px[5, y] = em_dk
        px[10, y] = em_dk
    # Inner radiant gold core + cross rays
    fill_rect(px, 6, 6, 9, 9, c_hi)
    px[6, 6] = em_sp
    px[7, 6] = em_sp
    px[6, 7] = em_sp
    px[7, 7] = rgba("#FFFFFF")
    px[8, 8] = c_md
    px[9, 9] = em_dk
    # 4 cardinal sovereign rays
    px[7, 4] = em_sp
    px[8, 4] = c_hi
    px[7, 11] = c_md
    px[8, 11] = em_dk
    px[4, 7] = em_sp
    px[4, 8] = c_hi
    px[11, 7] = c_md
    px[11, 8] = em_dk
    return im


def make_coin_200() -> Image.Image:
    """200x: Refined sapphire-and-gold alloy coin with deep cobalt crystal core & gold bevel."""
    pal = {
        "o": rgba("#0C1633"),
        "H": rgba("#FFF3A6"),
        "S": rgba("#F7C948"),
        "R": rgba("#D99A1E"),
        "F": rgba("#1F5ED1"),  # deep sapphire interior field!
        "M": rgba("#143E99"),
        "D": rgba("#85520A"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    au_sp = rgba("#FFF9CC")
    au_hi = rgba("#FAD04B")
    au_md = rgba("#CF901B")
    au_dk = rgba("#7D4C08")

    sap_sp = rgba("#D4F6FF")
    sap_hi = rgba("#54C4FF")
    sap_md = rgba("#267EF0")
    sap_dk = rgba("#12398F")
    sap_dp = rgba("#0A1D54")

    # Heavy milled gold bezel framing deep sapphire field
    for x in range(5, 11):
        px[x, 3] = au_hi
        px[x, 12] = au_dk
    for y in range(5, 11):
        px[3, y] = au_hi
        px[12, y] = au_dk
    px[4, 4] = au_sp
    px[11, 4] = au_md
    px[4, 11] = au_md
    px[11, 11] = au_dk

    # Deep cobalt inner chamber
    fill_rect(px, 4, 5, 11, 10, sap_dk)
    fill_rect(px, 5, 4, 10, 11, sap_dk)
    # Dark inner sapphire bezel
    hline(px, 5, 10, 4, sap_dp)
    vline(px, 4, 5, 10, sap_dp)

    # Luminous faceted sapphire crystal core
    fill_rect(px, 6, 5, 9, 10, sap_md)
    fill_rect(px, 5, 6, 10, 9, sap_md)
    # Diagonal bright crystal specular flash
    for x, y in ((6, 6), (7, 6), (6, 7), (7, 7)):
        px[x, y] = sap_hi
    px[6, 6] = sap_sp
    px[7, 5] = sap_hi
    px[5, 7] = sap_hi
    px[8, 7] = sap_sp
    px[7, 8] = sap_hi
    px[9, 9] = sap_dp
    px[8, 9] = sap_dk
    px[9, 8] = sap_dk
    # Gold corner pins piercing rim
    px[2, 7] = au_sp
    px[13, 8] = au_md
    return im


def make_coin_500() -> Image.Image:
    """500x: Netherite-dark-gold & amethyst imperial coin with glowing violet crest."""
    pal = {
        "o": rgba("#120D17"),
        "H": rgba("#786C80"),  # dark forged netherite highlight
        "S": rgba("#574D5E"),
        "R": rgba("#433A4A"),
        "F": rgba("#322A38"),  # dark volcanic netherite field
        "M": rgba("#231D29"),
        "D": rgba("#1A1420"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    au_sp = rgba("#FFF0A6")
    au_hi = rgba("#F0BC42")
    au_md = rgba("#B87B1A")
    au_dk = rgba("#6E4209")

    am_sp = rgba("#FCE0FF")
    am_hi = rgba("#DF7BFF")
    am_md = rgba("#A838E0")
    am_dk = rgba("#631896")

    # Dark-gold imperial inner fillet ring
    for x in range(5, 11):
        px[x, 3] = au_hi
        px[x, 12] = au_dk
    for y in range(5, 11):
        px[3, y] = au_hi
        px[12, y] = au_dk

    # 4 imperial gold corner studs protruding slightly on octagon corners
    for gx, gy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[gx, gy] = au_sp if (gx < 8 and gy < 8) else au_md
    # Top/bottom gold imperial clasps
    px[7, 1] = au_hi
    px[8, 1] = au_md
    px[7, 14] = au_md
    px[8, 14] = au_dk

    # Glowing imperial amethyst crystal crest in center
    crest = [
        (7, 4, am_hi), (8, 4, am_md),
        (6, 5, am_hi), (7, 5, am_sp), (8, 5, am_hi), (9, 5, am_md),
        (5, 6, am_md), (6, 6, am_sp), (7, 6, rgba("#FFFFFF")), (8, 6, am_hi), (9, 6, am_md), (10, 6, am_dk),
        (5, 7, am_hi), (6, 7, am_hi), (7, 7, am_sp), (8, 7, am_hi), (9, 7, am_md), (10, 7, am_dk),
        (5, 8, am_md), (6, 8, am_md), (7, 8, am_hi), (8, 8, am_md), (9, 8, am_dk), (10, 8, am_dk),
        (6, 9, am_md), (7, 9, am_md), (8, 9, am_dk), (9, 9, am_dk),
        (7, 10, am_dk), (8, 10, am_dk),
        (7, 11, am_md), (8, 11, am_dk),
    ]
    for x, y, col in crest:
        px[x, y] = col
    return im


def make_coin_1000() -> Image.Image:
    """1000x: Iridium-platinum luminous sovereign coin with cyan-white star halo & double-bevel rim."""
    pal = {
        "o": rgba("#122638"),
        "H": rgba("#FFFFFF"),
        "S": rgba("#E6FAFF"),
        "R": rgba("#B4DCEF"),
        "F": rgba("#D2EEFA"),
        "M": rgba("#8EBBD4"),
        "D": rgba("#437394"),
    }
    im = _make_coin_base(pal)
    px = im.load()

    ir_wt = rgba("#FFFFFF")
    ir_cy = rgba("#6BFAFF")
    ir_md = rgba("#19C8E6")
    ir_dp = rgba("#0E6E94")
    ir_pr = rgba("#DEC2FF")  # subtle iridium pearl-violet shimmer

    # Double-bevel iridium-cyan inner ring
    for x in range(5, 11):
        px[x, 3] = ir_cy
        px[x, 12] = ir_dp
    for y in range(5, 11):
        px[3, y] = ir_cy
        px[12, y] = ir_dp
    # Subtle iridescent pearl diagonal facets
    for x, y in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[x, y] = ir_pr

    # Deep luminous cosmic-teal backdrop inside inner halo so the white-cyan iridium star pops!
    fill_rect(px, 5, 5, 10, 10, rgba("#134B69"))
    for x in (5, 10):
        for y in (5, 10):
            px[x, y] = rgba("#267CA3")

    # Iconic 4-point luminous iridium star emblem + pure white core
    star_cy = [
        (7, 4), (8, 4), (7, 11), (8, 11),
        (4, 7), (4, 8), (11, 7), (11, 8),
        (6, 6), (9, 6), (6, 9), (9, 9),
    ]
    for x, y in star_cy:
        px[x, y] = ir_cy
    star_wt = [
        (7, 5), (8, 5), (7, 10), (8, 10),
        (5, 7), (5, 8), (10, 7), (10, 8),
        (7, 6), (8, 6), (7, 9), (8, 9),
        (6, 7), (6, 8), (9, 7), (9, 8),
        (7, 7), (8, 7), (7, 8), (8, 8),
    ]
    for x, y in star_wt:
        px[x, y] = ir_wt
    # Subtle outer cardinal sovereign star flares on the 1px outer border!
    for x, y in ((7, 0), (8, 0), (7, 15), (8, 15), (0, 7), (0, 8), (15, 7), (15, 8)):
        px[x, y] = ir_cy if (x < 9 and y < 9) else ir_md
    px[7, 1] = ir_wt
    px[8, 1] = ir_cy
    px[1, 7] = ir_wt
    px[1, 8] = ir_cy
    return im


# =============================================================================
# 2. COMMUNICATION TERMINAL ITEMS (16x16)
# =============================================================================

def make_comm_terminal(empty: bool) -> Image.Image:
    """Handheld industrial radio/PDA with antenna, LCD screen, and tactile keypad."""
    grid = [
        "...........oAo..",
        "...........oAo..",
        "...........oao..",
        "...oooooooooao..",
        "..oYHHHHHHHHYo..",
        "..oHddddddddMo..",
        "..oHdSSSSSSdMo..",
        "..oHdSGGGGSdMo..",
        "..oHdSCCCCSdMo..",
        "..oHddddddddMo..",
        "..oHLLKLLKLLMo..",
        "..oHKKKLLKKKMo..",
        "..oHLLKLLKLLMo..",
        "..oYMMMMMMMMYo..",
        "...oooooooooo...",
        "................",
    ]
    if not empty:
        pal = {
            "o": rgba("#141920"),
            "A": rgba("#44F5FF"),  # lit cyan antenna tip
            "a": rgba("#8598AD"),  # telescopic steel antenna
            "Y": rgba("#E8A824"),  # industrial amber corner grip bumpers
            "H": rgba("#B2C2D4"),  # brushed steel top/left bevel
            "M": rgba("#576778"),  # shaded chassis
            "d": rgba("#122429"),  # recessed LCD bezel
            "S": rgba("#163B40"),  # active dark teal screen
            "G": rgba("#38F0A8"),  # glowing emerald-cyan chart/signal readout
            "C": rgba("#88FFF0"),  # bright LCD highlight
            "L": rgba("#7B8B9E"),  # keypad housing
            "K": rgba("#34404F"),  # dark tactile buttons
        }
    else:
        pal = {
            "o": rgba("#141920"),
            "A": rgba("#5E3838"),  # unlit dim antenna tip
            "a": rgba("#697685"),  # steel antenna
            "Y": rgba("#9C7022"),  # muted grip bumpers
            "H": rgba("#97A5B5"),
            "M": rgba("#4B5766"),
            "d": rgba("#14171C"),  # powered-down bezel
            "S": rgba("#1C2026"),  # dark off screen
            "G": rgba("#E03228"),  # red low-battery warning icon
            "C": rgba("#FF786E"),  # red alert core
            "L": rgba("#6B7887"),
            "K": rgba("#2D3642"),
        }
    im = render_grid(grid, pal)
    px = im.load()
    if not empty:
        # Clean green status LED + signal waves on LCD
        px[4, 4] = rgba("#42F56C")
        px[5, 6] = rgba("#38F0A8")
        px[6, 7] = rgba("#88FFF0")
        px[7, 6] = rgba("#38F0A8")
        px[8, 7] = rgba("#88FFF0")
        px[9, 6] = rgba("#44F5FF")
        px[10, 6] = rgba("#FFFFFF")
        px[10, 7] = rgba("#38F0A8")
        px[5, 8] = rgba("#24998A")
        px[6, 8] = rgba("#24998A")
        px[8, 8] = rgba("#24998A")
        px[9, 8] = rgba("#24998A")
        # Bright action key
        px[10, 12] = rgba("#38F0A8")
    else:
        # Red warning LED + crisp red empty-battery glyph centered on dark LCD
        px[4, 4] = rgba("#FF3B30")
        fill_rect(px, 5, 6, 10, 8, rgba("#1B1F26"))
        for x in range(6, 10):
            px[x, 6] = rgba("#D92B22")
            px[x, 8] = rgba("#D92B22")
        px[6, 7] = rgba("#FF5A4F")
        px[9, 7] = rgba("#D92B22")
        px[10, 7] = rgba("#FF5A4F")  # battery nub
        px[10, 12] = rgba("#8F322E")
    return im


# =============================================================================
# 3. BLOCK TEXTURES (16x16)
# =============================================================================

# --- 3.0 Primitive Minting Machine (Stone-Brick & Forged-Iron Furnace Press) ---

def draw_primitive_stone_casing() -> Image.Image:
    """Create a 16x16 weathered stone-brick & hammered wrought-iron block face."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()

    c_out = rgba("#1C1A18")
    st_hi = rgba("#9B958B")
    st_lt = rgba("#847E75")
    st_md = rgba("#6E6860")
    st_dk = rgba("#544F49")
    mortar = rgba("#383430")
    ir_hi = rgba("#70757D")
    ir_md = rgba("#474B52")
    ir_dk = rgba("#26282C")

    # Outer mortar frame & warm stone brick base
    fill_rect(px, 0, 0, 15, 15, c_out)
    fill_rect(px, 1, 1, 14, 14, st_md)

    # Stone-brick courses (4 horizontal rows with staggered mortar joints)
    for row_y in (4, 8, 12):
        hline(px, 1, 14, row_y, mortar)
        if row_y + 1 <= 14:
            hline(px, 1, 14, row_y + 1, st_hi)
    hline(px, 1, 14, 1, st_hi)
    # Staggered vertical stone joints
    for vx in (5, 11):
        vline(px, vx, 1, 3, mortar)
        vline(px, vx, 9, 11, mortar)
    for vx in (3, 8, 13):
        vline(px, vx, 5, 7, mortar)
        vline(px, vx, 13, 14, mortar)

    # Natural weathered stone texture variation
    for y in range(2, 14):
        for x in range(2, 14):
            if px[x, y] in (st_md, st_hi):
                if (x * 7 + y * 11) % 9 == 0:
                    px[x, y] = st_lt
                elif (x * 5 + y * 3) % 7 == 0:
                    px[x, y] = st_dk

    # Dark wrought-iron corner reinforcement brackets & rivets
    for bx, by in ((1, 1), (13, 1), (1, 13), (13, 13)):
        fill_rect(px, bx, by, bx + 1, by + 1, ir_md)
        px[bx, by] = ir_hi if (bx == 1 and by == 1) else ir_dk
    px[2, 1] = ir_hi
    px[1, 2] = ir_hi
    px[14, 13] = ir_dk
    px[13, 14] = ir_dk
    return im


def make_primitive_mint_front(active: bool) -> Image.Image:
    im = draw_primitive_stone_casing()
    px = im.load()

    c_dk = rgba("#141312")
    ir_hi = rgba("#888E96")
    ir_md = rgba("#565B63")
    ir_dk = rgba("#2C2F34")
    br_hi = rgba("#E8B954")
    br_md = rgba("#A87724")

    # Upper forged-iron manual die press arch (x=3..12, y=2..8)
    fill_rect(px, 3, 2, 12, 8, ir_dk)
    hline(px, 3, 12, 2, ir_hi)
    vline(px, 3, 2, 8, ir_md)
    vline(px, 12, 2, 8, ir_dk)
    fill_rect(px, 4, 3, 11, 7, c_dk)

    if not active:
        # Forged iron press hammer raised (y=3..4)
        hline(px, 6, 9, 3, ir_md)
        hline(px, 5, 10, 4, ir_hi)
        # Brass coin mold sitting on iron anvil (y=6..7)
        px[7, 6] = br_md
        px[8, 6] = br_md
        hline(px, 4, 11, 7, ir_md)
    else:
        # Press hammer lowered onto glowing hot forged coin blank!
        hline(px, 6, 9, 3, ir_md)
        hline(px, 5, 10, 4, ir_hi)
        hline(px, 5, 10, 5, rgba("#FFB833"))
        px[7, 5] = rgba("#FFF7B2")
        px[8, 5] = rgba("#FFF7B2")
        hline(px, 4, 11, 6, rgba("#E65C14"))
        px[6, 6] = rgba("#FFD24C")
        px[9, 6] = rgba("#FFD24C")
        # Forge sparks inside press chamber
        px[4, 3] = rgba("#FFE678")
        px[11, 4] = rgba("#FF8C1A")
        hline(px, 4, 11, 7, rgba("#7D4020"))

    # Riveted wrought-iron separator beam between press & firebox (y=9, x=2..13)
    hline(px, 2, 13, 9, ir_md)
    px[3, 9] = ir_hi
    px[12, 9] = ir_hi
    px[7, 9] = br_hi if active else br_md
    px[8, 9] = br_hi if active else br_md

    # Lower stone-framed furnace firebox hearth (x=4..11, y=10..13)
    fill_rect(px, 3, 10, 12, 13, ir_dk)
    fill_rect(px, 4, 11, 11, 13, c_dk)
    if not active:
        # Cold charcoal ash bed & 3 iron firebox grate bars
        hline(px, 4, 11, 13, rgba("#2E2A28"))
        for gx in (5, 8, 10):
            vline(px, gx, 11, 13, ir_md)
        px[6, 13] = rgba("#47413D")
        px[9, 13] = rgba("#3D3734")
    else:
        # Roaring furnace firebox glowing behind iron grate bars!
        hline(px, 4, 11, 11, rgba("#D93B12"))
        hline(px, 4, 11, 12, rgba("#FF8819"))
        hline(px, 4, 11, 13, rgba("#FFF294"))
        px[6, 12] = rgba("#FFE05C")
        px[7, 12] = rgba("#FFFFCC")
        px[9, 12] = rgba("#FFE05C")
        # Warm firelight cast on outer stone jambs
        px[3, 12] = rgba("#B85826")
        px[12, 12] = rgba("#B85826")
        px[3, 13] = rgba("#8F3F1A")
        px[12, 13] = rgba("#8F3F1A")
        # Silhouetted iron hearth grate bars
        for gx in (5, 8, 10):
            px[gx, 11] = ir_dk
            px[gx, 12] = rgba("#3D2518")
            px[gx, 13] = rgba("#592B12")
    return im


def make_primitive_mint_side() -> Image.Image:
    im = draw_primitive_stone_casing()
    px = im.load()

    ir_hi = rgba("#838991")
    ir_md = rgba("#52575F")
    ir_dk = rgba("#26282D")
    wd_hi = rgba("#B58043")
    wd_dk = rgba("#6E491E")

    # Twin horizontal hammered iron reinforcement bands (y=3 & y=12)
    for by in (3, 12):
        hline(px, 1, 14, by, ir_md)
        for rx in (3, 7, 8, 12):
            px[rx, by] = ir_hi if by == 3 else ir_dk

    # Center mechanical press flywheel & wooden crank handle (NO electric port!)
    fill_rect(px, 5, 5, 10, 10, ir_dk)
    fill_rect(px, 6, 6, 9, 9, ir_md)
    hline(px, 6, 9, 6, ir_hi)
    vline(px, 6, 6, 9, ir_hi)
    # Cross spokes
    px[7, 7] = ir_dk
    px[8, 8] = ir_dk
    px[8, 7] = wd_hi
    px[7, 8] = wd_dk
    # Outer gear teeth
    for tx, ty in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        px[tx, ty] = ir_md
    return im


def make_primitive_mint_top() -> Image.Image:
    im = draw_primitive_stone_casing()
    px = im.load()

    ir_hi = rgba("#8C929B")
    ir_md = rgba("#575C64")
    ir_dk = rgba("#25282D")
    br_hi = rgba("#DFB252")
    br_dk = rgba("#8C621C")

    # Heavy riveted iron circular screw-press cap in center (x=4..11, y=4..11)
    fill_rect(px, 4, 4, 11, 11, ir_dk)
    fill_rect(px, 5, 5, 10, 10, ir_md)
    hline(px, 5, 10, 5, ir_hi)
    vline(px, 5, 5, 10, ir_hi)
    # Central iron-and-brass screw press nut
    fill_rect(px, 6, 6, 9, 9, ir_dk)
    fill_rect(px, 7, 7, 8, 8, br_hi)
    px[8, 8] = br_dk
    # Corner cap rivets
    for rx, ry in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[rx, ry] = ir_hi
    return im


def make_primitive_mint_bottom() -> Image.Image:
    im = draw_primitive_stone_casing()
    px = im.load()
    c_dk = rgba("#2B2825")
    c_md = rgba("#47423D")
    fill_rect(px, 3, 3, 12, 12, c_md)
    hline(px, 3, 12, 3, c_dk)
    vline(px, 3, 3, 12, c_dk)
    for y in (6, 9):
        hline(px, 5, 10, y, c_dk)
    return im


# --- 3.1 Industrial Minting Machine ---

def make_mint_front(active: bool) -> Image.Image:
    im = draw_steel_casing("standard")
    px = im.load()

    c_dk = rgba("#141921")
    c_ch = rgba("#1E2530")
    cu_hi = rgba("#F2A866") if active else rgba("#D68549")
    cu_md = rgba("#CC6B2B") if active else rgba("#A35524")
    cu_dk = rgba("#6E3310")
    st_hi = rgba("#C7D6E6")
    st_md = rgba("#78899C")
    au_hi = rgba("#FFE066")
    au_md = rgba("#D99B1E")

    # Top status lamps (x=3..4 and x=11..12, y=2)
    if active:
        px[3, 2] = rgba("#B8FFD0")
        px[4, 2] = rgba("#36E865")
        px[11, 2] = rgba("#FF9924")
        px[12, 2] = rgba("#FFE675")
    else:
        px[3, 2] = rgba("#475866")
        px[4, 2] = rgba("#2C3742")
        px[11, 2] = rgba("#2C3742")
        px[12, 2] = rgba("#475866")

    # Flanking copper induction coils (x=2..3 and x=12..13, y=4..10)
    for y in range(4, 11):
        col_l = cu_hi if (y % 2 == 0) else cu_md
        col_r = cu_md if (y % 2 == 0) else cu_dk
        px[2, y] = col_l
        px[3, y] = col_r
        px[12, y] = col_l
        px[13, y] = col_r

    # Central recessed die-press chamber (x=4..11, y=3..11)
    fill_rect(px, 4, 3, 11, 11, c_dk)
    fill_rect(px, 5, 4, 10, 10, c_ch)

    if not active:
        # Upper hydraulic press ram raised (y=4..5)
        hline(px, 6, 9, 4, st_md)
        hline(px, 5, 10, 5, st_hi)
        # Coin press die silhouette in center
        px[7, 7] = rgba("#586778")
        px[8, 7] = rgba("#586778")
        px[7, 8] = rgba("#404C5C")
        px[8, 8] = rgba("#404C5C")
        # Lower steel anvil (y=9..10)
        hline(px, 5, 10, 9, st_md)
        hline(px, 5, 10, 10, rgba("#364252"))
    else:
        # Ram pressed down onto glowing hot amber coin die + flying sparks!
        hline(px, 6, 9, 4, st_md)
        hline(px, 6, 9, 5, st_hi)
        hline(px, 5, 10, 6, rgba("#FFE875"))
        # Molten amber press glow
        hline(px, 5, 10, 7, rgba("#FF8C1A"))
        px[7, 7] = rgba("#FFFFCC")
        px[8, 7] = rgba("#FFF080")
        hline(px, 5, 10, 8, rgba("#E65C12"))
        px[6, 8] = rgba("#FFB833")
        px[9, 8] = rgba("#FFB833")
        # Sparks in chamber corners
        px[5, 4] = rgba("#FFF294")
        px[10, 5] = rgba("#FFAE2B")
        px[5, 9] = rgba("#FF8C1A")
        # Heated anvil
        hline(px, 5, 10, 9, rgba("#9E4B1A"))
        hline(px, 5, 10, 10, rgba("#4A3429"))

    # Bottom coin output dispenser slot with gilded lip (x=5..10, y=12..13)
    fill_rect(px, 4, 12, 11, 13, c_dk)
    hline(px, 5, 10, 12, au_hi if active else au_md)
    hline(px, 5, 10, 13, rgba("#11161C"))
    px[7, 13] = au_hi if active else rgba("#6E541E")
    px[8, 13] = au_hi if active else rgba("#6E541E")
    return im


def make_mint_side() -> Image.Image:
    im = draw_steel_casing("standard")
    px = im.load()
    c_dk = rgba("#151A22")
    c_in = rgba("#252F3C")
    cu_hi = rgba("#EB9A59")
    cu_md = rgba("#B86228")
    cu_dk = rgba("#6E3410")
    yel = rgba("#E8B024")

    # Upper cooling louvers (x=4..11, y=3..5)
    for y in (3, 5):
        hline(px, 4, 11, y, c_dk)
        hline(px, 4, 11, y + 1 if y == 3 else 4, c_in)

    # Central octagonal EU/FE power input socket (x=5..10, y=6..11)
    fill_rect(px, 5, 6, 10, 11, c_dk)
    fill_rect(px, 6, 7, 9, 10, cu_md)
    hline(px, 6, 9, 7, cu_hi)
    vline(px, 6, 7, 10, cu_hi)
    hline(px, 6, 9, 10, cu_dk)
    vline(px, 9, 7, 10, cu_dk)
    # Central dark connector core + brass pin
    fill_rect(px, 7, 8, 8, 9, c_dk)
    px[7, 8] = yel
    px[8, 9] = yel

    # Subtle industrial warning trim at bottom (y=13)
    for x in range(3, 13):
        px[x, 13] = yel if (x % 3 == 0) else c_in
    return im


def make_mint_top() -> Image.Image:
    im = draw_steel_casing("standard")
    px = im.load()
    c_dk = rgba("#151B23")
    c_in = rgba("#2A3442")
    cu_hi = rgba("#EBA05E")
    cu_md = rgba("#B56026")
    st_hi = rgba("#C4D4E6")
    st_md = rgba("#78899E")

    # Recessed circular hydraulic press cap (x=4..11, y=4..11)
    fill_rect(px, 4, 4, 11, 11, c_dk)
    fill_rect(px, 5, 5, 10, 10, cu_md)
    hline(px, 5, 10, 5, cu_hi)
    vline(px, 5, 5, 10, cu_hi)
    # Inner steel piston crown (x=6..9, y=6..9)
    fill_rect(px, 6, 6, 9, 9, st_md)
    hline(px, 6, 9, 6, st_hi)
    vline(px, 6, 6, 9, st_hi)
    px[7, 7] = c_in
    px[8, 8] = c_in
    # Corner anchor bolts
    for rx, ry in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[rx, ry] = st_hi
    return im


def make_mint_bottom() -> Image.Image:
    im = draw_steel_casing("dark")
    px = im.load()
    c_dk = rgba("#131820")
    c_sl = rgba("#26303D")
    c_lt = rgba("#7A8C9F")

    fill_rect(px, 3, 3, 12, 12, c_sl)
    hline(px, 3, 12, 3, c_dk)
    vline(px, 3, 3, 12, c_dk)
    for y in (5, 7, 9, 11):
        hline(px, 5, 10, y, c_dk)
    for rx, ry in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[rx, ry] = c_lt
    return im


# --- 3.2 Industrial ATM ---

def make_atm_front() -> Image.Image:
    im = draw_steel_casing("sleek")
    px = im.load()

    c_dk = rgba("#111720")
    lcd_bg = rgba("#0D2336")
    lcd_cy = rgba("#30E2FF")
    lcd_hi = rgba("#C2FDFF")
    lcd_gd = rgba("#FFD647")
    key_hi = rgba("#D8E6F2")
    key_dk = rgba("#344252")
    grn = rgba("#39EB76")

    # Header gold/cyan banking strip (x=3..12, y=2)
    hline(px, 3, 12, 2, rgba("#1C4B7A"))
    px[4, 2] = lcd_gd
    px[5, 2] = lcd_gd
    px[11, 2] = grn

    # Glowing Cyan LCD Monitor (x=3..12, y=3..8)
    fill_rect(px, 2, 3, 13, 8, c_dk)
    fill_rect(px, 3, 4, 12, 7, lcd_bg)
    # Crisp UI readout lines & golden balance indicator on LCD
    hline(px, 4, 9, 5, lcd_cy)
    px[4, 5] = lcd_hi
    px[11, 5] = lcd_gd
    hline(px, 4, 7, 6, rgba("#1D84A8"))
    hline(px, 9, 11, 6, grn)

    # Lower-left numeric keypad (x=3..7, y=10..13)
    fill_rect(px, 2, 9, 8, 13, c_dk)
    for ky in (10, 12):
        for kx in (3, 5, 7):
            px[kx, ky] = key_hi
            px[kx, ky + 1] = key_dk
    px[7, 13] = grn  # Enter key

    # Lower-right coin intake slot + terminal dock (x=10..13, y=10..13)
    fill_rect(px, 9, 9, 13, 13, c_dk)
    # Illuminated green coin slot
    hline(px, 10, 12, 10, grn)
    hline(px, 10, 12, 11, rgba("#0B1117"))
    # Cyan terminal charging dock port
    hline(px, 10, 12, 12, lcd_cy)
    px[11, 13] = lcd_hi
    return im


def make_atm_side() -> Image.Image:
    im = draw_steel_casing("sleek")
    px = im.load()
    c_dk = rgba("#151C24")
    c_cy = rgba("#26B6D9")
    c_cyd = rgba("#165973")
    c_sl = rgba("#384657")

    # Cyan industrial banking accent line (y=3)
    hline(px, 2, 13, 3, c_cy)
    hline(px, 2, 13, 4, c_cyd)

    # Sleek vertical cooling vents (x=4..11, y=6..10)
    fill_rect(px, 4, 6, 11, 10, c_sl)
    for x in (5, 7, 9, 11):
        vline(px, x, 6, 10, c_dk)

    # Lower rear power port (x=6..9, y=12..13)
    fill_rect(px, 6, 12, 9, 13, c_dk)
    px[7, 12] = rgba("#F2B830")
    px[8, 12] = rgba("#F2B830")
    return im


def make_atm_top() -> Image.Image:
    im = draw_steel_casing("sleek")
    px = im.load()
    c_dk = rgba("#18202B")
    c_in = rgba("#4C5D70")
    c_hi = rgba("#DCE9F5")

    # Flush maintenance hatch
    fill_rect(px, 4, 4, 11, 11, c_in)
    hline(px, 4, 11, 4, c_dk)
    vline(px, 4, 4, 11, c_dk)
    hline(px, 4, 11, 11, c_hi)
    vline(px, 11, 4, 11, c_hi)
    # Recessed cyan security beacon LED at front-center
    px[7, 2] = c_dk
    px[8, 2] = rgba("#38EBFF")
    for rx, ry in ((5, 5), (10, 5), (5, 10), (10, 10)):
        px[rx, ry] = c_dk
    return im


def make_atm_bottom() -> Image.Image:
    im = draw_steel_casing("dark")
    px = im.load()
    c_dk = rgba("#12171E")
    c_lt = rgba("#8899AD")
    fill_rect(px, 4, 4, 11, 11, c_dk)
    fill_rect(px, 5, 5, 10, 10, rgba("#2E3845"))
    for rx, ry in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[rx, ry] = c_lt
    return im


# --- 3.3 Village Cooperation Hub ---

def make_hub_front() -> Image.Image:
    """Monumental gilded-bronze & stone municipal trade shrine with golden village bell-crest."""
    im = draw_hub_masonry_frame()
    px = im.load()

    bg_dp = rgba("#152238")  # deep royal-navy recessed shrine arch
    bg_md = rgba("#1E3354")
    au_sp = rgba("#FFF8C4")
    au_hi = rgba("#FFE05C")
    au_md = rgba("#E0A21E")
    au_dk = rgba("#8C560A")
    em_cy = rgba("#47EBFF")

    # Recessed arched municipal trade shrine niche (x=4..11, y=3..11)
    fill_rect(px, 4, 3, 11, 11, bg_dp)
    fill_rect(px, 5, 4, 10, 10, bg_md)
    # Arch top bezel
    px[4, 3] = rgba("#353940")
    px[11, 3] = rgba("#353940")

    # Golden Village Bell / Trade Crest centered inside niche!
    bell = [
        (7, 4, au_sp), (8, 4, au_hi),
        (6, 5, au_sp), (7, 5, au_hi), (8, 5, au_hi), (9, 5, au_md),
        (6, 6, au_hi), (7, 6, au_sp), (8, 6, au_hi), (9, 6, au_md),
        (6, 7, au_hi), (7, 7, au_hi), (8, 7, au_md), (9, 7, au_dk),
        (5, 8, au_sp), (6, 8, au_hi), (7, 8, au_hi), (8, 8, au_md), (9, 8, au_md), (10, 8, au_dk),
        (5, 9, au_md), (6, 9, au_dk), (7, 9, au_dk), (8, 9, au_dk), (9, 9, au_dk), (10, 9, au_dk),
        (7, 10, au_sp), (8, 10, au_hi),  # bell clapper / medallion
    ]
    for x, y, col in bell:
        px[x, y] = col

    # Glowing azure trade-link jewel on plinth below bell (y=12)
    hline(px, 4, 11, 12, rgba("#252B34"))
    px[7, 12] = em_cy
    px[8, 12] = em_cy
    px[6, 12] = rgba("#1F78A8")
    px[9, 12] = rgba("#1F78A8")
    return im


def make_hub_side() -> Image.Image:
    """Dual symmetric Inbound (blue arrow) & Outbound (orange arrow) industrial logistics ports."""
    im = draw_hub_masonry_frame()
    px = im.load()

    c_dk = rgba("#161B22")
    c_in = rgba("#202833")
    bl_hi = rgba("#A8EEFF")
    bl_md = rgba("#2FA0F5")
    bl_dk = rgba("#155394")
    or_hi = rgba("#FFE488")
    or_md = rgba("#F58C1F")
    or_dk = rgba("#9E480A")

    # Left Port: Inbound Delivery Chute (x=3..7, interior x=4..6, y=4..11) — Blue Inward/Down Arrow
    fill_rect(px, 3, 4, 7, 11, c_dk)
    fill_rect(px, 4, 5, 6, 10, c_in)
    hline(px, 3, 7, 4, bl_dk)
    px[5, 5] = bl_hi
    px[5, 6] = bl_hi
    px[5, 7] = bl_md
    px[4, 8] = bl_md
    px[5, 8] = bl_hi
    px[6, 8] = bl_md
    px[5, 9] = bl_md
    px[4, 10] = bl_dk
    px[6, 10] = bl_dk

    # Right Port: Outbound Purchase Chute (x=8..12, interior x=9..11, y=4..11) — Orange Outward/Up Arrow
    fill_rect(px, 8, 4, 12, 11, c_dk)
    fill_rect(px, 9, 5, 11, 10, c_in)
    hline(px, 8, 12, 4, or_dk)
    px[10, 5] = or_hi
    px[9, 6] = or_md
    px[10, 6] = or_hi
    px[11, 6] = or_md
    px[10, 7] = or_hi
    px[10, 8] = or_md
    px[10, 9] = or_md
    px[9, 10] = or_dk
    px[11, 10] = or_dk
    return im


def make_hub_top() -> Image.Image:
    """Monumental gilded-bronze finial cap with luminous center municipal seal."""
    im = draw_hub_masonry_frame()
    px = im.load()

    au_sp = rgba("#FFF8C4")
    au_hi = rgba("#F2C446")
    au_md = rgba("#C48A1C")
    au_dk = rgba("#784C0C")
    cy_hi = rgba("#A8FCFF")
    cy_md = rgba("#26C6E8")
    navy = rgba("#192B47")

    # Stepped gilded bronze square canopy (x=3..12, y=3..12)
    fill_rect(px, 3, 3, 12, 12, au_md)
    hline(px, 3, 12, 3, au_hi)
    vline(px, 3, 3, 12, au_hi)
    hline(px, 3, 12, 12, au_dk)
    vline(px, 12, 3, 12, au_dk)

    # Inner recessed navy compass plate (x=5..10, y=5..10)
    fill_rect(px, 5, 5, 10, 10, navy)
    # Golden finial cross & luminous center trade jewel
    for x in (7, 8):
        px[x, 5] = au_hi
        px[x, 10] = au_md
    for y in (7, 8):
        px[5, y] = au_hi
        px[10, y] = au_md
    fill_rect(px, 6, 6, 9, 9, au_hi)
    px[6, 6] = au_sp
    px[9, 9] = au_dk
    px[7, 7] = cy_hi
    px[8, 7] = cy_md
    px[7, 8] = cy_md
    px[8, 8] = rgba("#14789E")
    return im


def make_hub_bottom() -> Image.Image:
    im = draw_hub_masonry_frame()
    px = im.load()
    c_dk = rgba("#22262C")
    c_md = rgba("#3D434C")
    fill_rect(px, 4, 4, 11, 11, c_dk)
    fill_rect(px, 5, 5, 10, 10, c_md)
    hline(px, 5, 10, 7, c_dk)
    vline(px, 7, 5, 10, c_dk)
    return im


# =============================================================================
# 4. INDUSTRIAL GUI TEXTURES (256x256 ATLASES)
# =============================================================================

def _draw_panel_frame(px, w: int, h: int, theme: str = "steel"):
    """Draw a crisp IC2/LC2-style beveled GUI panel at (0, 0, w, h)."""
    if theme == "pda":
        c_out = rgba("#10151C")
        c_hi = rgba("#788A9E")
        c_bg = rgba("#394452")
        c_bg2 = rgba("#343F4C")
        c_sh = rgba("#1D242E")
        c_riv = rgba("#222B36")
        c_riv_hi = rgba("#92A6BC")
    elif theme == "stone":
        # Warm stone-and-forged-iron panel for Primitive Minting Machine
        c_out = rgba("#1E1B18")
        c_hi = rgba("#ECE7DF")
        c_bg = rgba("#C2BEB5")
        c_bg2 = rgba("#BBB6AC")
        c_sh = rgba("#5C564D")
        c_riv = rgba("#474B52")
        c_riv_hi = rgba("#DCE0E6")
    elif theme == "gold":
        c_out = rgba("#241B10")
        c_hi = rgba("#F5EBD6")
        c_bg = rgba("#C8C3B8")
        c_bg2 = rgba("#C2BDB2")
        c_sh = rgba("#635948")
        c_riv = rgba("#A3751F")
        c_riv_hi = rgba("#FFE88A")
    else:
        c_out = rgba("#1E232B")
        c_hi = rgba("#F2F4F7")
        c_bg = rgba("#C6C6C6")
        c_bg2 = rgba("#C0C2C6")
        c_sh = rgba("#555B66")
        c_riv = rgba("#707680")
        c_riv_hi = rgba("#F2F4F7")

    fill_rect(px, 0, 0, w - 1, h - 1, c_out)
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[cx, cy] = (0, 0, 0, 0)
    fill_rect(px, 1, 1, w - 2, h - 2, c_bg)
    hline(px, 1, w - 3, 1, c_hi)
    hline(px, 1, w - 4, 2, c_hi)
    vline(px, 1, 1, h - 3, c_hi)
    vline(px, 2, 1, h - 4, c_hi)
    hline(px, 2, w - 2, h - 2, c_sh)
    hline(px, 3, w - 2, h - 3, c_sh)
    vline(px, w - 2, 2, h - 2, c_sh)
    vline(px, w - 3, 3, h - 2, c_sh)
    for y in range(4, h - 3, 2):
        hline(px, 3, w - 4, y, c_bg2)
    for rx, ry in ((4, 4), (w - 6, 4), (4, h - 6), (w - 6, h - 6)):
        px[rx, ry] = c_riv
        px[rx + 1, ry + 1] = c_riv_hi


def _draw_slot(px, item_x: int, item_y: int, accent=None, tint: str = "neutral"):
    """Draw an 18x18 recessed inventory slot around (item_x, item_y)."""
    x0, y0, x1, y1 = item_x - 1, item_y - 1, item_x + 16, item_y + 16
    if tint == "blue":
        c_bg, c_dk, c_lt, c_mid = rgba("#8293A3"), rgba("#294159"), rgba("#E0F0FA"), rgba("#ADC4D6")
    elif tint == "amber":
        c_bg, c_dk, c_lt, c_mid = rgba("#9B9182"), rgba("#543B1C"), rgba("#FAF0E0"), rgba("#CFC0AB")
    elif tint == "gold":
        c_bg, c_dk, c_lt, c_mid = rgba("#99917E"), rgba("#5C4616"), rgba("#FFF5D6"), rgba("#CFC29B")
    else:
        c_bg, c_dk, c_lt, c_mid = rgba("#8B9098"), rgba("#373B42"), rgba("#EEF1F5"), rgba("#B8BDC6")

    fill_rect(px, x0, y0, x1, y1, c_bg)
    hline(px, x0, x1 - 1, y0, c_dk)
    vline(px, x0, y0, y1 - 1, c_dk)
    hline(px, x0 + 1, x1, y1, c_lt)
    vline(px, x1, y0 + 1, y1, c_lt)
    px[x1, y0] = c_mid
    px[x0, y1] = c_mid
    if accent is not None:
        hline(px, x0 + 1, x1 - 1, y0 + 1, accent)


def _draw_lcd(px, x0: int, y0: int, w: int, h: int, bg_hex="#151C24", scan_hex="#1B242F", rim_lt="#DCE2EA"):
    x1, y1 = x0 + w - 1, y0 + h - 1
    fill_rect(px, x0, y0, x1, y1, rgba(bg_hex))
    hline(px, x0, x1 - 1, y0, rgba("#0B0F14"))
    vline(px, x0, y0, y1 - 1, rgba("#0B0F14"))
    hline(px, x0 + 1, x1, y1, rgba(rim_lt))
    vline(px, x1, y0 + 1, y1, rgba(rim_lt))
    for y in range(y0 + 2, y1 - 1, 2):
        hline(px, x0 + 1, x1 - 1, y, rgba(scan_hex))


def _draw_recessed_box(px, x0: int, y0: int, w: int, h: int, fill="#AEB3BC"):
    x1, y1 = x0 + w - 1, y0 + h - 1
    fill_rect(px, x0, y0, x1, y1, rgba(fill))
    hline(px, x0, x1 - 1, y0, rgba("#555B66"))
    vline(px, x0, y0, y1 - 1, rgba("#555B66"))
    hline(px, x0 + 1, x1, y1, rgba("#EDF0F5"))
    vline(px, x1, y0 + 1, y1, rgba("#EDF0F5"))


def _draw_watermark(px, item_x: int, item_y: int, pattern: list[str], col_hi, col_lo):
    for dy, row in enumerate(pattern):
        for dx, ch in enumerate(row):
            if ch == "#":
                px[item_x + dx, item_y + dy] = col_lo
            elif ch == "+":
                px[item_x + dx, item_y + dy] = col_hi


def _draw_eu_bolt(px, x0: int, y0: int, lit: bool):
    bolt = [
        "....oo....",
        "...oBBoo..",
        "..oBHHBo..",
        "..oBHBo...",
        ".oBBHHBoo.",
        ".oBHHHBBoo",
        "..oooBHBo.",
        "....oBBBo.",
        "...oBHHBo.",
        "...oBHBo..",
        "..oBBBo...",
        "..oBBo....",
        "...oo.....",
        "..........",
    ]
    pal = (
        {"o": rgba("#591808"), "B": rgba("#FF7B1C"), "H": rgba("#FFE854")}
        if lit else
        {"o": rgba("#2B313B"), "B": rgba("#555D6B"), "H": rgba("#6E7787")}
    )
    for dy, row in enumerate(bolt):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _draw_flame(px, x0: int, y0: int, lit: bool):
    flame = [
        "......oo......",
        ".....oAAo.....",
        "....oAAYYo....",
        "...oAAAYYo....",
        "...oAAYYYo....",
        "..oAAYYYYAo...",
        "..oAAYWWYAo...",
        ".oAAYYWWYYAo..",
        ".oAAYYWWYYAo..",
        ".oAAYYYYYYAo..",
        "..oAAYYYYAo...",
        "..oAAAAAAo....",
        "...oooooo.....",
        "..............",
    ]
    pal = (
        {"o": rgba("#5C1808"), "A": rgba("#F25C19"), "Y": rgba("#FFBF2B"), "W": rgba("#FFF6AD")}
        if lit else
        {"o": rgba("#434852"), "A": rgba("#6E7480"), "Y": rgba("#818794"), "W": rgba("#9197A3")}
    )
    for dy, row in enumerate(flame):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _draw_battery_discharge_14x14(px, x0: int, y0: int, lit: bool):
    """Draw a crisp 14x14 electric battery-discharge lightning icon (pure-electric Mint)."""
    icon = [
        "....oooooo....",
        "...oTTTTTTo...",
        "..oBBBBBBBBo..",
        "..oB..LL..Bo..",
        "..oB.LLH..Bo..",
        "..oB.LLLL.Bo..",
        "..oB..HHLLBo..",
        "..oB...LL.Bo..",
        "..oB..LL..Bo..",
        "..oB.LL...Bo..",
        "..oB......Bo..",
        "..oBBBBBBBBo..",
        "...oooooooo...",
        "..............",
    ]
    pal = (
        {
            "o": rgba("#0F2436"),
            "T": rgba("#85E8FF"),
            "B": rgba("#1F88B8"),
            "L": rgba("#FFCC29"),
            "H": rgba("#FFFFB8"),
        }
        if lit else
        {
            "o": rgba("#383E47"),
            "T": rgba("#78808C"),
            "B": rgba("#5E6570"),
            "L": rgba("#878E99"),
            "H": rgba("#9AA2AD"),
        }
    )
    for dy, row in enumerate(icon):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _draw_warning_badge_14x14(px, x0: int, y0: int):
    """Draw a crisp 14x14 amber warning triangle icon (Requires Industrial Mint)."""
    grid = [
        "......oo......",
        ".....oAAo.....",
        "....oAAAAo....",
        "....oAWWAo....",
        "...oAAWWAAo...",
        "...oAAWWAAo...",
        "..oAAAWWAAAo..",
        "..oAAAWWAAAo..",
        ".oAAAAWWAAAAo.",
        ".oAAAAAAAAAAo.",
        "oAAAAAWWAAAAAo",
        "oAAAAAWWAAAAAo",
        ".oooooooooooo.",
        "..............",
    ]
    pal = {
        "o": rgba("#471E06"),
        "A": rgba("#F2A61E"),
        "W": rgba("#FFF8C4"),
    }
    for dy, row in enumerate(grid):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _draw_arrow_24x16(px, x0: int, y0: int, lit: bool):
    """Draw a 24x16 horizontal industrial press progress arrow."""
    # Shaft y=5..10 x=1..14, arrowhead x=14..22
    c_out = rgba("#4A2506") if lit else rgba("#3A404A")
    c_mid = rgba("#F2A61E") if lit else rgba("#787E8A")
    c_hi = rgba("#FFF08A") if lit else rgba("#9197A3")
    c_sh = rgba("#BD6A0C") if lit else rgba("#5E6470")

    # Outline + fill
    fill_rect(px, x0 + 1, y0 + 5, x0 + 14, y0 + 10, c_out)
    fill_rect(px, x0 + 2, y0 + 6, x0 + 14, y0 + 9, c_mid)
    hline(px, x0 + 2, x0 + 14, y0 + 6, c_hi)
    hline(px, x0 + 2, x0 + 14, y0 + 9, c_sh)

    # Arrowhead triangle (dx = 14..22)
    for step in range(8):
        x = x0 + 14 + step
        y_top = y0 + 1 + step
        y_bot = y0 + 14 - step
        if y_top <= y_bot:
            px[x, y_top] = c_out
            px[x, y_bot] = c_out
            for y in range(y_top + 1, y_bot):
                px[x, y] = c_hi if (y < y0 + 7) else (c_sh if y > y0 + 9 else c_mid)
    px[x0 + 22, y0 + 7] = c_out
    px[x0 + 22, y0 + 8] = c_out


def _draw_crown_16x16(px, x0: int, y0: int):
    """Draw a crisp 16x16 golden Mayor Crown emblem."""
    grid = [
        "................",
        ".......oo.......",
        "..oo..oSSo..oo..",
        ".oSSo.oHHo.oSSo.",
        ".oHMo.oHMo.oHMo.",
        ".oHMooHMHMooHMo.",
        ".oHHHMHGGHMHHHo.",
        ".oHMMMMMMMMMMHo.",
        ".oHMCMHMHMHMCMo.",
        ".oHMMMMMMMMMMHo.",
        ".oHDDDDDDDDDDHo.",
        "..oHHHHHHHHHHo..",
        "..oDDDDDDDDDDo..",
        "...oooooooooo...",
        "................",
        "................",
    ]
    pal = {
        "o": rgba("#382005"),
        "S": rgba("#FFFAD1"),
        "H": rgba("#FFE05C"),
        "M": rgba("#E09E1B"),
        "D": rgba("#995C09"),
        "G": rgba("#38EBFF"),  # sapphire crown jewel
        "C": rgba("#F0445B"),  # ruby side jewels
    }
    for dy, row in enumerate(grid):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _verify_rects(rects: list[tuple[str, int, int, int, int]], max_w: int, max_h: int, margin: int = 0):
    for name, x, y, w, h in rects:
        assert x >= margin and y >= margin and x + w <= max_w - margin and y + h <= max_h - margin, (
            f"Rect {name} ({x},{y},{w},{h}) out of bounds ({margin}..{max_w - margin}, {margin}..{max_h - margin})"
        )
    for i, (n1, x1, y1, w1, h1) in enumerate(rects):
        for n2, x2, y2, w2, h2 in rects[i + 1:]:
            if x1 < x2 + w2 and x2 < x1 + w1 and y1 < y2 + h2 and y2 < y1 + h1:
                raise AssertionError(f"GUI overlap between {n1} ({x1},{y1},{w1},{h1}) and {n2} ({x2},{y2},{w2},{h2})")


# Watermark patterns (16x16)
WM_INGOT = [
    "................",
    "................",
    "................",
    "......######....",
    "....##++++++##..",
    "...#+++++++++##.",
    "..#++########++#",
    "..#+#++++++++#+#",
    "..#+#++++++++#+#",
    "..#+##########+#",
    "..############..",
    "................",
    "................",
    "................",
    "................",
    "................",
]

WM_COIN = [
    "................",
    ".....######.....",
    "....#++++++#....",
    "...#++####++#...",
    "..#++#....#++#..",
    "..#+#..++..#+#..",
    "..#+#..++..#+#..",
    "..#+#..++..#+#..",
    "..#+#..++..#+#..",
    "..#+#..++..#+#..",
    "..#++#....#++#..",
    "...#++####++#...",
    "....#++++++#....",
    ".....######.....",
    "................",
    "................",
]

WM_BATTERY = [
    "................",
    "......####......",
    ".....######.....",
    "....########....",
    "....#++++++#....",
    "....#+####+#....",
    "....#++##++#....",
    "....#++++++#....",
    "....#+####+#....",
    "....#++++++#....",
    "....#+####+#....",
    "....#++++++#....",
    "....########....",
    "................",
    "................",
    "................",
]

WM_TERMINAL = [
    "................",
    "..........##....",
    "..........##....",
    "....########....",
    "....#++++++#....",
    "....#+####+#....",
    "....#+#++#+#....",
    "....#+####+#....",
    "....#++++++#....",
    "....#+#++#+#....",
    "....#++++++#....",
    "....#+#++#+#....",
    "....########....",
    "................",
    "................",
    "................",
]

WM_UPGRADE = [
    "................",
    "...##########...",
    "..##++++++++##..",
    "..#+########+#..",
    "..#+#......#+#..",
    "..#+#..++..#+#..",
    "..#+#.++++.#+#..",
    "..#+#..++..#+#..",
    "..#+#......#+#..",
    "..#+########+#..",
    "..############..",
    "...##..##..##...",
    "...##..##..##...",
    "................",
    "................",
    "................",
]


WM_COAL_FUEL = [
    "................",
    "......##........",
    ".....#++#.......",
    "....#++++#......",
    "....#++++#......",
    "...#++##++#.....",
    "...#++##++#.....",
    "....######......",
    "................",
    "...########.....",
    "..#++++++++#....",
    "..#++####++#....",
    "...########.....",
    "................",
    "................",
    "................",
]


def make_gui_primitive_mint_machine() -> Image.Image:
    """256x256 GUI atlas for Primitive Minting Machine (panel 176x166, fuel-only 1x-100x Vanilla)."""
    PW, PH = 176, 166
    panel_rects = [
        ("input_0", 37, 21, 18, 18),
        ("input_1", 57, 21, 18, 18),
        ("flame", 49, 41, 14, 14),
        ("fuel_3", 47, 56, 18, 18),
        ("arrow", 82, 23, 24, 16),
        ("output_2", 113, 21, 18, 18),
        ("status_lcd", 76, 48, 92, 25),
        ("player_inv", 7, 83, 162, 54),
        ("hotbar", 7, 141, 162, 18),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_panel_frame(px, PW, PH, "stone")

    # Subtle forged-iron header accent bar
    hline(px, 8, 167, 16, rgba("#8C8478"))
    hline(px, 8, 167, 17, rgba("#E4DED2"))

    # Input Slots 0 & 1
    _draw_slot(px, 38, 22, tint="amber")
    _draw_watermark(px, 38, 22, WM_INGOT, rgba("#877C6E"), rgba("#6E6356"))
    _draw_slot(px, 58, 22, tint="amber")
    _draw_watermark(px, 58, 22, WM_INGOT, rgba("#877C6E"), rgba("#6E6356"))

    # Unlit Furnace Flame Gauge centered between inputs & fuel slot (49, 41, 14x14)
    _draw_flame(px, 49, 41, lit=False)

    # Burnable Fuel Slot 3 (48, 57)
    _draw_slot(px, 48, 57, accent=rgba("#D97A26"), tint="amber")
    _draw_watermark(px, 48, 57, WM_COAL_FUEL, rgba("#8A7B69"), rgba("#6E6050"))

    # Press Progress Arrow (82, 23, 24x16)
    _draw_arrow_24x16(px, 82, 23, lit=False)

    # Output Coin Slot 2 (114, 22, gold-tinted!)
    _draw_slot(px, 114, 22, accent=rgba("#E5B53A"), tint="gold")
    _draw_watermark(px, 114, 22, WM_COIN, rgba("#877C63"), rgba("#6E634C"))

    # Status / Upgrade-Required Banner LCD (76, 48, 92x25)
    _draw_lcd(px, 76, 48, 92, 25, bg_hex="#1E1B18", scan_hex="#26221E", rim_lt="#E6E0D5")
    vline(px, 77, 49, 71, rgba("#D98826"))

    # Player Inventory (84, 102, 120) & Hotbar (142)
    for row in range(3):
        for col in range(9):
            _draw_slot(px, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        _draw_slot(px, 8 + col * 18, 142)

    # Dynamic Sprites in Right Margin (u = 176..204)
    # 1) Lit Furnace Flame: UV (176, 0, 14, 14)
    _draw_flame(px, 176, 0, lit=True)
    # 2) Lit Press Arrow: UV (176, 14, 24, 16)
    _draw_arrow_24x16(px, 176, 14, lit=True)
    # 3) Upgrade Warning Badge: UV (176, 32, 14, 14)
    _draw_warning_badge_14x14(px, 176, 32)
    return im


def make_gui_mint_machine() -> Image.Image:
    """256x256 GUI atlas for Pure-Electric Industrial Minting Machine (panel 176x176)."""
    PW, PH = 176, 176
    panel_rects = [
        ("energy_gauge", 12, 20, 14, 48),
        ("eu_bolt", 14, 70, 10, 14),
        ("input_0", 37, 25, 18, 18),
        ("input_1", 57, 25, 18, 18),
        ("arrow", 80, 27, 24, 16),
        ("output_2", 111, 25, 18, 18),
        ("battery_3", 37, 55, 18, 18),
        ("discharge_icon", 59, 57, 14, 14),
        ("terminal_4", 111, 55, 18, 18),
        ("divider", 145, 14, 2, 74),
        ("upgrades", 151, 17, 18, 72),
        ("player_inv", 7, 93, 162, 54),
        ("hotbar", 7, 151, 162, 18),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_panel_frame(px, PW, PH, "steel")

    # Energy Gauge (12, 20, 14, 48) -> inner fill (13, 21, 12, 46)
    _draw_lcd(px, 12, 20, 14, 48)
    for ty in range(25, 65, 5):
        hline(px, 14, 16, ty, rgba("#2E3E50"))
        hline(px, 21, 23, ty, rgba("#2E3E50"))
    _draw_eu_bolt(px, 14, 70, lit=False)

    # Input Slots 0 & 1
    _draw_slot(px, 38, 26)
    _draw_watermark(px, 38, 26, WM_INGOT, rgba("#79808A"), rgba("#626872"))
    _draw_slot(px, 58, 26)
    _draw_watermark(px, 58, 26, WM_INGOT, rgba("#79808A"), rgba("#626872"))

    # Press progress arrow (unlit)
    _draw_arrow_24x16(px, 80, 27, lit=False)

    # Output Coin Slot 2 (gold-tinted!)
    _draw_slot(px, 112, 26, accent=rgba("#E5B53A"), tint="gold")
    _draw_watermark(px, 112, 26, WM_COIN, rgba("#877C63"), rgba("#6E634C"))

    # Pure-Electric Battery Discharge Slot 3 + Unlit Battery-Discharge Lightning Icon (NO coal/flame!)
    _draw_slot(px, 38, 56, accent=rgba("#2FB8D9"), tint="blue")
    _draw_watermark(px, 38, 56, WM_BATTERY, rgba("#667C8F"), rgba("#4D6273"))
    _draw_battery_discharge_14x14(px, 59, 57, lit=False)

    # Terminal Charging Slot 4
    _draw_slot(px, 112, 56, accent=rgba("#2FB8D9"), tint="blue")
    _draw_watermark(px, 112, 56, WM_TERMINAL, rgba("#667C8F"), rgba("#4D6273"))

    # Upgrade Column Divider & 4 Upgrade Slots (5..8)
    vline(px, 145, 14, 87, rgba("#9BA0A8"))
    vline(px, 146, 14, 87, rgba("#E4E7EC"))
    for uy in (18, 36, 54, 72):
        _draw_slot(px, 152, uy)
        _draw_watermark(px, 152, uy, WM_UPGRADE, rgba("#7A808A"), rgba("#636872"))

    # Player Inventory (94, 112, 130) & Hotbar (152)
    for row in range(3):
        for col in range(9):
            _draw_slot(px, 8 + col * 18, 94 + row * 18)
    for col in range(9):
        _draw_slot(px, 8 + col * 18, 152)

    # Dynamic Sprites in Right Margin (u = 176..210)
    # 1) Lit Battery-Discharge Lightning Icon: UV (176, 0, 14, 14)
    _draw_battery_discharge_14x14(px, 176, 0, lit=True)
    # 2) Lit Press Arrow: UV (176, 14, 24, 16)
    _draw_arrow_24x16(px, 176, 14, lit=True)
    # 3) Lit Energy Fill: UV (176, 32, 12, 46)
    for dy in range(46):
        frac = 1.0 - (dy / 45.0)
        if frac > 0.68:
            c_l, c_m, c_r = rgba("#FFF066"), rgba("#FFCC29"), rgba("#D48814")
        elif frac > 0.35:
            c_l, c_m, c_r = rgba("#FFB838"), rgba("#FF821A"), rgba("#C4480E")
        else:
            c_l, c_m, c_r = rgba("#FF7638"), rgba("#E63B17"), rgba("#961D0B")
        if dy % 5 == 4:
            hline(px, 176, 187, 32 + dy, rgba("#591C0B"))
        else:
            hline(px, 176, 187, 32 + dy, c_m)
            px[176, 32 + dy] = c_r
            px[177, 32 + dy] = c_l
            px[187, 32 + dy] = c_r
    # 4) Lit EU Bolt: UV (190, 32, 10, 14)
    _draw_eu_bolt(px, 190, 32, lit=True)
    return im


def make_gui_atm_machine() -> Image.Image:
    """256x256 GUI atlas for Industrial ATM (panel 220x214)."""
    PW, PH = 220, 214
    panel_rects = [
        ("top_lcd", 8, 18, 204, 32),
        ("deposit_slot", 13, 61, 18, 18),
        ("withdraw_slot", 13, 87, 18, 18),
        ("left_controls", 35, 56, 72, 68),
        ("right_lcd", 111, 56, 75, 68),
        ("divider", 189, 56, 2, 70),
        ("mini_energy", 196, 56, 14, 30),
        ("terminal_slot", 194, 89, 18, 18),
        ("fuel_slot", 194, 109, 18, 18),
        ("player_inv", 28, 131, 162, 54),
        ("hotbar", 28, 189, 162, 18),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_panel_frame(px, PW, PH, "steel")

    # Cyan banking accent bar along top header
    hline(px, 8, 211, 16, rgba("#25B5D9"))
    hline(px, 8, 211, 17, rgba("#166E8C"))

    # Top Banking Account LCD (8, 18, 204, 32)
    _draw_lcd(px, 8, 18, 204, 32, bg_hex="#0F2030", scan_hex="#14293D")
    vline(px, 9, 19, 48, rgba("#2BD8FF"))

    # Left Cash Deposit / Withdraw Slots
    _draw_slot(px, 14, 62, accent=rgba("#38E67A"), tint="gold")
    _draw_watermark(px, 14, 62, WM_COIN, rgba("#857B62"), rgba("#6E634B"))
    _draw_slot(px, 14, 88, accent=rgba("#F0B82E"), tint="gold")
    _draw_watermark(px, 14, 88, WM_COIN, rgba("#857B62"), rgba("#6E634B"))

    # Recessed control area for Deposit/Withdraw/Denomination controls (35, 56, 72, 68)
    _draw_recessed_box(px, 35, 56, 72, 68, fill="#B2B7C0")

    # Right Account / Guild / Transfer / History LCD (111, 56, 75, 68)
    _draw_lcd(px, 111, 56, 75, 68, bg_hex="#131D29", scan_hex="#192534")

    # Right Power & Terminal Charging Column
    vline(px, 189, 56, 125, rgba("#9BA0A8"))
    vline(px, 190, 56, 125, rgba("#E4E7EC"))
    _draw_lcd(px, 196, 56, 14, 30)
    _draw_slot(px, 195, 90, accent=rgba("#2FB8D9"), tint="blue")
    _draw_watermark(px, 195, 90, WM_TERMINAL, rgba("#667C8F"), rgba("#4D6273"))
    _draw_slot(px, 195, 110)
    _draw_watermark(px, 195, 110, WM_BATTERY, rgba("#79808A"), rgba("#626872"))

    # Player Inventory centered at x=29
    for row in range(3):
        for col in range(9):
            _draw_slot(px, 29 + col * 18, 132 + row * 18)
    for col in range(9):
        _draw_slot(px, 29 + col * 18, 190)

    # Dynamic Sprites in Right Margin (u = 224..255)
    # 1) Mini Energy Fill: UV (224, 0, 12, 28)
    for dy in range(28):
        frac = 1.0 - (dy / 27.0)
        col = rgba("#3CEEFF") if frac > 0.45 else rgba("#FFB833")
        hline(px, 224, 235, dy, col)
        px[224, dy] = rgba("#197A99")
        px[235, dy] = rgba("#197A99")
    # 2) Personal Bank Badge: UV (224, 32, 16, 16)
    fill_rect(px, 224, 32, 239, 47, rgba("#13283B"))
    _draw_watermark(px, 224, 32, WM_COIN, rgba("#FFE475"), rgba("#D99B1E"))
    # 3) Guild/Company Badge: UV (224, 52, 16, 16)
    fill_rect(px, 224, 52, 239, 67, rgba("#292112"))
    _draw_crown_16x16(px, 224, 52)
    return im


def _draw_hub_bronze_panel_frame(px, w: int, h: int):
    """Draw a bespoke 'Gilded Bronze & Deep Ledger' architectural municipal panel at (0, 0, w, h)."""
    c_out = rgba("#1E160E")       # deep espresso-bronze contour
    c_hi = rgba("#F5EAD4")        # warm sunlit parchment bevel
    c_hi2 = rgba("#E6D8BE")       # inner warm limestone highlight
    c_bg = rgba("#CFC4B0")        # warm architectural limestone body
    c_bg2 = rgba("#C8BCA6")       # subtle stone-grain rhythm
    c_bg3 = rgba("#D4CAB6")       # subtle mineral speckle
    c_sh = rgba("#5E4B33")        # deep antique bronze shadow
    c_sh2 = rgba("#826B4D")       # mid antique bronze bevel

    au_sp = rgba("#FFF2A8")       # specular gold highlight
    au_hi = rgba("#F2C446")       # burnished gold top
    au_md = rgba("#C48D22")       # antique gold mid
    au_dk = rgba("#7A5210")       # deep bronze shade

    # Outer contour with 1px transparent rounded corners
    fill_rect(px, 0, 0, w - 1, h - 1, c_out)
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[cx, cy] = (0, 0, 0, 0)

    # Limestone body & dual-step chamfer bevel
    fill_rect(px, 1, 1, w - 2, h - 2, c_bg)
    hline(px, 1, w - 3, 1, c_hi)
    hline(px, 2, w - 4, 2, c_hi2)
    vline(px, 1, 1, h - 3, c_hi)
    vline(px, 2, 2, h - 4, c_hi2)
    hline(px, 2, w - 2, h - 2, c_sh)
    hline(px, 3, w - 3, h - 3, c_sh2)
    vline(px, w - 2, 2, h - 2, c_sh)
    vline(px, w - 3, 3, h - 3, c_sh2)

    # Subtle architectural limestone grain
    for y in range(4, h - 4):
        if y % 2 == 0:
            hline(px, 3, w - 4, y, c_bg2)
        for x in range(4, w - 4):
            if (x * 13 + y * 7) % 29 == 0:
                px[x, y] = c_bg3

    # Burnished gold top molding (y=2..3, x=7..w-8) & bottom molding (y=h-5..h-4, x=7..w-8)
    hline(px, 7, w - 8, 2, au_hi)
    hline(px, 7, w - 8, 3, au_md)
    hline(px, 7, w - 8, h - 5, au_hi)
    hline(px, 7, w - 8, h - 4, au_dk)

    # Riveted gilded-bronze corner plates (strictly outside x=7..w-8 and clear of tab/slots)
    corners = (
        (1, 1, 1, 1),
        (w - 2, 1, -1, 1),
        (1, h - 2, 1, -1),
        (w - 2, h - 2, -1, -1),
    )
    for cx, cy, dx, dy in corners:
        # L-shaped bronze corner armor arms (length 6 along edge, thickness 2)
        for step in range(6):
            # Horizontal arm
            px[cx + dx * step, cy] = au_hi if (dy > 0 and step < 5) else au_md
            px[cx + dx * step, cy + dy] = au_md if dy > 0 else au_dk
            # Vertical arm
            px[cx, cy + dy * step] = au_hi if (dx > 0 and step < 5) else au_md
            px[cx + dx, cy + dy * step] = au_md if dx > 0 else au_dk
        # Inner corner bevel plate (2x2) + crisp brass rivet stud
        px[cx + dx * 2, cy + dy * 2] = au_dk
        px[cx + dx * 3, cy + dy * 2] = au_md
        px[cx + dx * 2, cy + dy * 3] = au_md
        px[cx + dx * 3, cy + dy * 3] = au_sp if (dx > 0 and dy > 0) else au_hi
        px[cx + dx, cy + dy] = au_sp if (dx > 0 and dy > 0) else au_hi

    # Subtle bronze architectural side clasps aligned with viewport/inventory split (y=135..138)
    for sx in (3, w - 5):
        fill_rect(px, sx, 135, sx + 1, 138, au_md)
        px[sx, 135] = au_hi
        px[sx + 1, 138] = au_dk


def _draw_pda_tactical_frame(px, w: int, h: int):
    """Draw a sleek 'Deep-Space Cyan/Teal Tactical Handheld PDA' chassis at (0, 0, w, h)."""
    c_out = rgba("#090D13")       # void-black outer shell contour
    c_hi = rgba("#54687F")        # machined anodized edge highlight
    c_hi2 = rgba("#38485B")       # secondary chamfer highlight
    c_bg = rgba("#232E3C")        # anodized dark gunmetal-navy chassis
    c_bg2 = rgba("#1E2835")       # brushed micro-texture
    c_sh = rgba("#10161F")        # deep shadow bevel
    c_sh2 = rgba("#17202B")       # inner shadow bevel
    c_rim_dk = rgba("#111923")    # inner recessed screen bezel shadow
    c_rim_lt = rgba("#3B5066")    # inner recessed screen bezel catchlight

    # Outer shell with 1px transparent rounded corners
    fill_rect(px, 0, 0, w - 1, h - 1, c_out)
    for cx, cy in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        px[cx, cy] = (0, 0, 0, 0)

    # Anodized gunmetal-navy body + chamfer bevel
    fill_rect(px, 1, 1, w - 2, h - 2, c_bg)
    hline(px, 1, w - 3, 1, c_hi)
    hline(px, 2, w - 4, 2, c_hi2)
    vline(px, 1, 1, h - 3, c_hi)
    vline(px, 2, 2, h - 4, c_hi2)
    hline(px, 2, w - 2, h - 2, c_sh)
    hline(px, 3, w - 3, h - 3, c_sh2)
    vline(px, w - 2, 2, h - 2, c_sh)
    vline(px, w - 3, 3, h - 3, c_sh2)

    for y in range(4, h - 4, 2):
        hline(px, 3, w - 4, y, c_bg2)

    # Recessed top OLED header strip behind title/signal/battery (x=6..w-7, y=4..19)
    fill_rect(px, 6, 4, w - 7, 19, rgba("#182330"))
    hline(px, 6, w - 7, 4, c_rim_dk)
    hline(px, 6, w - 7, 19, rgba("#2A3D52"))
    # Subtle cyan header micro-accent flanking title
    hline(px, 24, 174, 18, rgba("#1C455C"))

    # Beveled inner display bay rim framing both screens (x=6..w-7, y=20..h-9)
    hline(px, 6, w - 7, 20, c_rim_dk)
    vline(px, 6, 20, h - 9, c_rim_dk)
    hline(px, 6, w - 7, h - 9, c_rim_lt)
    vline(px, w - 7, 20, h - 9, c_rim_lt)

    # Rugged amber-gold armored corner bumpers with crisp bevel & dark rubberized seam
    amb_sp = rgba("#FFE680")
    amb_hi = rgba("#F2B632")
    amb_md = rgba("#C77E14")
    amb_dk = rgba("#784407")
    seam = rgba("#0D131B")
    bumper_corners = (
        (1, 1, 1, 1),
        (w - 2, 1, -1, 1),
        (1, h - 2, 1, -1),
        (w - 2, h - 2, -1, -1),
    )
    for cx, cy, dx, dy in bumper_corners:
        # Horizontal & vertical armored bumper arms (8px long, 3px thick)
        for s in range(8):
            px[cx + dx * s, cy] = amb_hi if dy > 0 else amb_dk
            px[cx + dx * s, cy + dy] = amb_md
            px[cx + dx * s, cy + dy * 2] = amb_dk if dy > 0 else amb_md
            px[cx, cy + dy * s] = amb_hi if dx > 0 else amb_dk
            px[cx + dx, cy + dy * s] = amb_md
            px[cx + dx * 2, cy + dy * s] = amb_dk if dx > 0 else amb_md
        # Specular corner knuckle & dark mounting seam
        px[cx, cy] = amb_sp if (dx > 0 and dy > 0) else amb_hi
        px[cx + dx, cy + dy] = amb_hi if (dx > 0 and dy > 0) else amb_md
        px[cx + dx * 8, cy] = seam
        px[cx + dx * 8, cy + dy] = seam
        px[cx + dx * 8, cy + dy * 2] = seam
        px[cx, cy + dy * 8] = seam
        px[cx + dx, cy + dy * 8] = seam
        px[cx + dx * 2, cy + dy * 8] = seam

    # Tactile knurled chassis grip ticks on left (x=1..3) & right (x=w-4..w-2) edges
    for gy in range(52, h - 52, 4):
        hline(px, 1, 3, gy, rgba("#0F151E"))
        hline(px, 1, 3, gy + 1, rgba("#475B70"))
        hline(px, w - 4, w - 2, gy, rgba("#0F151E"))
        hline(px, w - 4, w - 2, gy + 1, rgba("#475B70"))

    # Bottom chassis tactical speaker micro-grille & status LEDs (y=h-6..h-4, well below screens y=199)
    for vx in range(106, 142, 3):
        fill_rect(px, vx, h - 6, vx + 1, h - 5, rgba("#101720"))
        px[vx, h - 4] = rgba("#354759")
    # Small glowing teal power indicator LED on bottom-left chin
    fill_rect(px, 14, h - 6, 17, h - 5, rgba("#0E292A"))
    px[15, h - 6] = rgba("#3BF5D6")
    px[16, h - 6] = rgba("#1BB8A2")
    # Small amber RF link indicator on bottom-right chin
    fill_rect(px, w - 18, h - 6, w - 15, h - 5, rgba("#2B1F0E"))
    px[w - 17, h - 6] = rgba("#F2B632")
    px[w - 16, h - 6] = rgba("#B57514")


def _draw_framed_lcd(px, x0: int, y0: int, w: int, h: int, bg_hex: str, scan_hex: str, rim_dk: str, rim_lt: str, rim_mid: str):
    """Draw a recessed dark LCD viewport with custom bevel colors and crisp 2px scanlines."""
    x1, y1 = x0 + w - 1, y0 + h - 1
    fill_rect(px, x0, y0, x1, y1, rgba(bg_hex))
    hline(px, x0, x1 - 1, y0, rgba(rim_dk))
    vline(px, x0, y0, y1 - 1, rgba(rim_dk))
    hline(px, x0 + 1, x1, y1, rgba(rim_lt))
    vline(px, x1, y0 + 1, y1, rgba(rim_lt))
    px[x1, y0] = rgba(rim_mid)
    px[x0, y1] = rgba(rim_mid)
    for y in range(y0 + 2, y1 - 1, 2):
        hline(px, x0 + 1, x1 - 1, y, rgba(scan_hex))


def _draw_hub_slot(px, item_x: int, item_y: int, style: str = "neutral"):
    """Draw a refined 18x18 recessed slot tailored to the Gilded Bronze Cooperation Hub palette."""
    x0, y0, x1, y1 = item_x - 1, item_y - 1, item_x + 16, item_y + 16
    accent = None
    if style == "gold":
        c_bg, c_dk, c_lt, c_mid = rgba("#9E9172"), rgba("#593F10"), rgba("#FFF3C8"), rgba("#C9B47E")
        accent = rgba("#F2C242")
    elif style == "bronze":
        c_bg, c_dk, c_lt, c_mid = rgba("#89857E"), rgba("#3E362B"), rgba("#F0E6D2"), rgba("#B8AE9C")
        accent = rgba("#C49448")
    elif style == "inbound":
        c_bg, c_dk, c_lt, c_mid = rgba("#788FA6"), rgba("#1E3854"), rgba("#DDF0FF"), rgba("#9DBCD6")
        accent = rgba("#38B2FF")
    elif style == "outbound":
        c_bg, c_dk, c_lt, c_mid = rgba("#9C8C75"), rgba("#573814"), rgba("#FFF0D8"), rgba("#CFB999")
        accent = rgba("#FF9E26")
    else:
        c_bg, c_dk, c_lt, c_mid = rgba("#8F8D88"), rgba("#423B31"), rgba("#F5ECDA"), rgba("#BEB5A4")

    fill_rect(px, x0, y0, x1, y1, c_bg)
    hline(px, x0, x1 - 1, y0, c_dk)
    vline(px, x0, y0, y1 - 1, c_dk)
    hline(px, x0 + 1, x1, y1, c_lt)
    vline(px, x1, y0 + 1, y1, c_lt)
    px[x1, y0] = c_mid
    px[x0, y1] = c_mid
    if accent is not None:
        hline(px, x0 + 1, x1 - 1, y0 + 1, accent)


def _draw_pixel_sprite(px, x0: int, y0: int, grid: list[str], pal: dict[str, tuple[int, int, int, int]]):
    for dy, row in enumerate(grid):
        for dx, ch in enumerate(row):
            if ch != ".":
                px[x0 + dx, y0 + dy] = pal[ch]


def _draw_season_badge_12x12(px, x0: int, y0: int, season: str):
    """Draw crisp 12x12 pixel-art season badges (spring, summer, autumn, winter) on dark rounded plates."""
    if season == "spring":
        grid = [
            ".rrrrrrrrrr.",
            "rbbbbbbbbbbr",
            "rbbbbPPbbbbr",
            "rbPPbHHbPPbr",
            "rbHHPYYPHHbr",
            "rbbPPWWPPbbr",
            "rbbPHYYHPbbr",
            "rbPPbPPbPPbr",
            "rbbGbbbbLGbr",
            "rbbbGGbGGbbr",
            "rbbbbbbbbbbr",
            ".rrrrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2B6943"),
            "b": rgba("#102418"),
            "P": rgba("#F57EB6"),
            "H": rgba("#FFD6EB"),
            "Y": rgba("#FFD942"),
            "W": rgba("#FFFCE6"),
            "G": rgba("#2CD96B"),
            "L": rgba("#94FFBA"),
        }
    elif season == "summer":
        grid = [
            ".rrrrrrrrrr.",
            "rbbbbRRbbbbr",
            "rbRbbbbbbRbr",
            "rbbbYHHYbbbr",
            "rbbYHWWYHbbr",
            "rRbHWWWWHbRr",
            "rRbHWWYYHbRr",
            "rbbYHHHHYbbr",
            "rbbbYYYYbbbr",
            "rbRbbbbbbRbr",
            "rbbbbRRbbbbr",
            ".rrrrrrrrrr.",
        ]
        pal = {
            "r": rgba("#8A5C18"),
            "b": rgba("#2B1B08"),
            "R": rgba("#FF9E1C"),
            "Y": rgba("#FFB824"),
            "H": rgba("#FFE55C"),
            "W": rgba("#FFFBD6"),
        }
    elif season == "autumn":
        grid = [
            ".rrrrrrrrrr.",
            "rbbbbHHbbbbr",
            "rbbbHOOHbbbr",
            "rbHbHOOHbHbr",
            "rbOHOYYOHObr",
            "rbbOOYYOObbr",
            "rbROOYYOORbr",
            "rbbRROORRbbr",
            "rbbbbSSbbbbr",
            "rbbbbbSbbbbr",
            "rbbbbSbbbbbr",
            ".rrrrrrrrrr.",
        ]
        pal = {
            "r": rgba("#8A3C1C"),
            "b": rgba("#2B140B"),
            "H": rgba("#FFD259"),
            "Y": rgba("#FFB02E"),
            "O": rgba("#F0641E"),
            "R": rgba("#C43314"),
            "S": rgba("#8C471E"),
        }
    else:  # winter
        grid = [
            ".rrrrrrrrrr.",
            "rbbbbCCbbbbr",
            "rbCbbWWbbCbr",
            "rbbCbCCbCbbr",
            "rbbbCWWCbbbr",
            "rCWCCWWCCWCr",
            "rCWCCWWCCWCr",
            "rbbbCWWCbbbr",
            "rbbCbCCbCbbr",
            "rbCbbWWbbCbr",
            "rbbbbCCbbbbr",
            ".rrrrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2B7099"),
            "b": rgba("#0C1E2E"),
            "C": rgba("#47DAFF"),
            "W": rgba("#F2FEFF"),
        }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_weekly_calendar_12x12(px, x0: int, y0: int):
    """Draw a crisp 12x12 weekly restock calendar icon with amber-gold header and green restock day."""
    grid = [
        "..oo....oo..",
        ".oBBooooBBo.",
        "oHGGGGGGGGHo",
        "oAAAAAAAAAAo",
        "oWWWWWWWWWWo",
        "oWDDWDDWDDWo",
        "oWWWWWWWWWWo",
        "oWDDWDDWDDWo",
        "oWWWWWWWWWWo",
        "oWDDWDDWEEWo",
        "oSSSSSSSSSSo",
        ".oooooooooo.",
    ]
    pal = {
        "o": rgba("#1D1710"),
        "B": rgba("#E2EAF2"),
        "H": rgba("#FFF099"),
        "G": rgba("#F0B62B"),
        "A": rgba("#BD7E14"),
        "W": rgba("#F2F6FA"),
        "S": rgba("#C4D0DE"),
        "D": rgba("#475C75"),
        "E": rgba("#22CF66"),
    }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_biome_badge_10x10(px, x0: int, y0: int, biome: str):
    """Draw crisp 10x10 biome emblem badges (plains, desert, savanna, taiga, snowy, jungle, swamp)."""
    if biome == "plains":
        grid = [
            ".rrrrrrrr.",
            "rbbbbbbbbr",
            "rbLLbbLLbr",
            "rbbLGGLbbr",
            "rbbbGGbbbr",
            "rbbbGGbbbr",
            "rbbGLLGbbr",
            "rbGGGGGGbr",
            "rbDDDDDDbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2E6E44"),
            "b": rgba("#10241A"),
            "L": rgba("#98FFAD"),
            "G": rgba("#36D968"),
            "D": rgba("#7A5532"),
        }
    elif biome == "desert":
        grid = [
            ".rrrrrrrr.",
            "rbbbbbbbbr",
            "rbbbLGbbbr",
            "rbGbLGbbbr",
            "rbGGLGbGbr",
            "rbbbLGGGbr",
            "rbbSGGSSbr",
            "rbSSSSSSbr",
            "rbAAAAAAbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#8A682A"),
            "b": rgba("#291D0D"),
            "L": rgba("#78F088"),
            "G": rgba("#2DB84A"),
            "S": rgba("#FFE07D"),
            "A": rgba("#D49C35"),
        }
    elif biome == "savanna":
        grid = [
            ".rrrrrrrr.",
            "rbbbbbbbbr",
            "rbbLLLLbbr",
            "rbLLGGGGbr",
            "rbbGTTGbbr",
            "rbbbbTbbbr",
            "rbbbTbbbbr",
            "rbOOTOOObr",
            "rbCCCCCCbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#8C4B28"),
            "b": rgba("#2B170E"),
            "L": rgba("#BEE854"),
            "G": rgba("#78A828"),
            "T": rgba("#6E4A2E"),
            "O": rgba("#E09448"),
            "C": rgba("#B55B2A"),
        }
    elif biome == "taiga":
        grid = [
            ".rrrrrrrr.",
            "rbbbLLbbbr",
            "rbbbLGbbbr",
            "rbbLLGGbbr",
            "rbbbLGbbbr",
            "rbbLLGGbbr",
            "rbLLGGGGbr",
            "rbbbTTbbbr",
            "rbSSTTSSbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2B6158"),
            "b": rgba("#0E211F"),
            "L": rgba("#5AE09B"),
            "G": rgba("#1E8A5E"),
            "T": rgba("#6E4A2C"),
            "S": rgba("#6B8582"),
        }
    elif biome == "snowy":
        grid = [
            ".rrrrrrrr.",
            "rbbbWWbbbr",
            "rbbWCCWbbr",
            "rbWWHCCWbr",
            "rbWHCCDDbr",
            "rbWCCDDDbr",
            "rbbWCDDbbr",
            "rbbbCDbbbr",
            "rbbbbbbbbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2B6B99"),
            "b": rgba("#0D1E30"),
            "H": rgba("#FFFFFF"),
            "W": rgba("#A8FAFF"),
            "C": rgba("#4BE2FF"),
            "D": rgba("#1B98D6"),
        }
    elif biome == "jungle":
        grid = [
            ".rrrrrrrr.",
            "rbbLbbLLbr",
            "rbbGbbGLbr",
            "rbbNbbNGbr",
            "rbLGbbGbbr",
            "rbGGbLGLbr",
            "rbbNbbNGbr",
            "rbbGbbGbbr",
            "rbbGbbGbbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#247336"),
            "b": rgba("#0C2412"),
            "L": rgba("#8EFF54"),
            "G": rgba("#28C946"),
            "N": rgba("#E0FF9E"),
        }
    else:  # swamp
        grid = [
            ".rrrrrrrr.",
            "rbbbbbbbbr",
            "rbbbVVbbbr",
            "rbbVPPVbbr",
            "rbbbVVbbbr",
            "rbbLLGGbbr",
            "rbLLGGGGbr",
            "rbGGGGbGbr",
            "rbWWWWWWbr",
            ".rrrrrrrr.",
        ]
        pal = {
            "r": rgba("#2D665F"),
            "b": rgba("#112423"),
            "P": rgba("#FFD9FF"),
            "V": rgba("#DA6BFF"),
            "L": rgba("#6CE6A0"),
            "G": rgba("#279E6C"),
            "W": rgba("#1A5C6B"),
        }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_order_tag_8x8(px, x0: int, y0: int, kind: str):
    """Draw crisp 8x8 mini order tag icons (biome specialty star, seasonal spark)."""
    if kind == "biome":
        grid = [
            "...oo...",
            "..oGGo..",
            ".oGEEGo.",
            "oGEWWEGo",
            "oGEWWEGo",
            ".oGEEGo.",
            "..oGGo..",
            "...oo...",
        ]
        pal = {
            "o": rgba("#16291D"),
            "G": rgba("#FFD447"),
            "E": rgba("#36EB7C"),
            "W": rgba("#E8FFF2"),
        }
    else:  # season
        grid = [
            "...oo...",
            "..oAAo..",
            ".oAWWAo.",
            "oAWWWCCo",
            "oAAWWCCo",
            ".oACCDo.",
            "..oCCo..",
            "...oo...",
        ]
        pal = {
            "o": rgba("#15202E"),
            "A": rgba("#FFBE3B"),
            "C": rgba("#42EAFF"),
            "D": rgba("#1D9ED4"),
            "W": rgba("#FFFCE8"),
        }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_cpi_arrow_10x10(px, x0: int, y0: int, kind: str):
    """Draw crisp 10x10 shaded CPI trend icons (up, down, stable)."""
    if kind == "up":
        grid = [
            "....oo....",
            "...oHHo...",
            "..oHRRHo..",
            ".oHRRRRHo.",
            "oHRRRRRRHo",
            "oRRRRRRRRo",
            ".ooRRRRoo.",
            "..oRRRRo..",
            "..oDDDDo..",
            "..oooooo..",
        ]
        pal = {
            "o": rgba("#4A120E"),
            "H": rgba("#FFA69E"),
            "R": rgba("#FF4F42"),
            "D": rgba("#B8251C"),
        }
    elif kind == "down":
        grid = [
            "..oooooo..",
            "..oHHHHo..",
            "..oCCCCo..",
            ".ooCCCCoo.",
            "oCCCCCCCCo",
            "oCCCDDDCCo",
            ".oCDDDDCo.",
            "..oDDDDo..",
            "...oDDo...",
            "....oo....",
        ]
        pal = {
            "o": rgba("#0C2D47"),
            "H": rgba("#BDF7FF"),
            "C": rgba("#38DCFF"),
            "D": rgba("#1799C9"),
        }
    else:  # stable
        grid = [
            "..........",
            "..........",
            "..........",
            ".oooooooo.",
            "oHHHHHHHHo",
            "oGGGGGGGGo",
            "oDDDDDDDDo",
            ".oooooooo.",
            "..........",
            "..........",
        ]
        pal = {
            "o": rgba("#0E3B21"),
            "H": rgba("#A8FFCA"),
            "G": rgba("#3CE87A"),
            "D": rgba("#1C9C4C"),
        }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_auto_badge_14x14(px, x0: int, y0: int, enabled: bool):
    """Draw crisp 14x14 Auto-Fulfill ON (emerald checkmark) and OFF (slate dash) badges."""
    if enabled:
        grid = [
            ".oooooooooooo.",
            "oHHHHHHHHHHHGo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGWWDo",
            "oHGGGGGGGWWWDo",
            "oHGWWGGGWWWGDo",
            "oHGWWWGGWWGGDo",
            "oHGGWWWWWGGGDo",
            "oHGGGWWWGGGGDo",
            "oHGGGGWGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oGDDDDDDDDDDDo",
            ".oooooooooooo.",
        ]
        pal = {
            "o": rgba("#0C2B19"),
            "H": rgba("#6BF7A1"),
            "G": rgba("#20B85C"),
            "D": rgba("#117037"),
            "W": rgba("#F0FFF6"),
        }
    else:
        grid = [
            ".oooooooooooo.",
            "oHHHHHHHHHHHGo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGWWWWWWGGDo",
            "oHGGWWWWWWGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oHGGGGGGGGGGDo",
            "oGDDDDDDDDDDDo",
            ".oooooooooooo.",
        ]
        pal = {
            "o": rgba("#181D24"),
            "H": rgba("#5E6775"),
            "G": rgba("#3D4552"),
            "D": rgba("#282E38"),
            "W": rgba("#8B95A6"),
        }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_chunk_loader_16x16(px, x0: int, y0: int):
    """Draw a crisp symmetric 16x16 Chunk-Loader Active 3x3 chunk beacon badge."""
    grid = [
        ".rrrrrrrrrrrrrr.",
        "rbbbbbbbbbbbbbbr",
        "rbbgbbbbbbbbgbbr",
        "rbbCCbbCCbbCCbbr",
        "rbbCCbbCCbbCCbbr",
        "rbbbbbbbbbbbbbbr",
        "rbbbbbTTTTbbbbbr",
        "rbbCCbTWWTbCCbbr",
        "rbbCCbTWWTbCCbbr",
        "rbbbbbTTTTbbbbbr",
        "rbbbbbbbbbbbbbbr",
        "rbbCCbbCCbbCCbbr",
        "rbbCCbbCCbbCCbbr",
        "rbbgbbbbbbbbgbbr",
        "rbbbbbbbbbbbbbbr",
        ".rrrrrrrrrrrrrr.",
    ]
    pal = {
        "r": rgba("#207A68"),
        "b": rgba("#0C1E1C"),
        "C": rgba("#26CC9F"),
        "T": rgba("#5EFFDB"),
        "W": rgba("#ECFFFA"),
        "g": rgba("#1A5C4E"),
    }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def _draw_village_pin_12x12(px, x0: int, y0: int):
    """Draw a crisp 12x12 glowing cyan tactical map pin for Comm Terminal."""
    grid = [
        "....oooo....",
        "..ooHHHHoo..",
        ".oHCCCCCCDo.",
        ".oCCWWWWCDo.",
        ".oCCWWWWCDo.",
        ".oHCCCCCCDo.",
        "..oCCCCCCo..",
        "...oCDDDCo..",
        "....oCDCo...",
        ".....oDo....",
        "......o.....",
        "............",
    ]
    pal = {
        "o": rgba("#082030"),
        "H": rgba("#A8FAFF"),
        "C": rgba("#3AEBFF"),
        "D": rgba("#158CB8"),
        "W": rgba("#FFFFFF"),
    }
    _draw_pixel_sprite(px, x0, y0, grid, pal)


def make_gui_cooperation_hub() -> Image.Image:
    """256x256 GUI atlas for Village Cooperation Hub (panel 248x222) — Gilded Bronze & Deep Ledger."""
    PW, PH = 248, 222
    panel_rects = [
        ("tab_rail", 8, 4, 232, 16),
        ("banner_lcd", 8, 21, 232, 25),
        ("viewport", 8, 48, 232, 87),
        ("player_inv", 7, 139, 162, 54),
        ("hotbar", 7, 197, 162, 18),
        ("divider", 173, 139, 2, 76),
        ("cash_slot", 180, 143, 18, 18),
        ("upg_slot_0", 202, 143, 18, 18),
        ("upg_slot_1", 222, 143, 18, 18),
        ("status_lcd", 179, 166, 61, 49),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    sprite_rects = [
        ("panel_body", 0, 0, PW, PH),
        ("slot_neutral", 0, 226, 18, 18),
        ("slot_inbound", 20, 226, 18, 18),
        ("slot_outbound", 40, 226, 18, 18),
        ("season_spring", 62, 226, 12, 12),
        ("season_summer", 76, 226, 12, 12),
        ("season_autumn", 90, 226, 12, 12),
        ("season_winter", 104, 226, 12, 12),
        ("cpi_up", 120, 226, 10, 10),
        ("cpi_down", 132, 226, 10, 10),
        ("cpi_stable", 144, 226, 10, 10),
        ("auto_on", 158, 226, 14, 14),
        ("auto_off", 174, 226, 14, 14),
        ("chunk_loader", 192, 226, 16, 16),
        ("mayor_crown", 212, 226, 16, 16),
        ("weekly_calendar", 232, 226, 12, 12),
        ("biome_plains", 0, 244, 10, 10),
        ("biome_desert", 12, 244, 10, 10),
        ("biome_savanna", 24, 244, 10, 10),
        ("biome_taiga", 36, 244, 10, 10),
        ("biome_snowy", 48, 244, 10, 10),
        ("biome_jungle", 60, 244, 10, 10),
        ("biome_swamp", 72, 244, 10, 10),
        ("tag_biome", 86, 246, 8, 8),
        ("tag_season", 96, 246, 8, 8),
    ]
    _verify_rects(sprite_rects, 256, 256, margin=0)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_hub_bronze_panel_frame(px, PW, PH)

    # Recessed Header Tab Rail (8, 4, 232, 16) -> integrates seamlessly with 4 beveled tabs
    _draw_framed_lcd(px, 8, 4, 232, 16, "#1B2534", "#202C3D", "#3B2F1E", "#F2E7CF", "#9E8D70")
    hline(px, 9, 238, 5, rgba("#121924"))
    for sep_x in (65, 123, 181):
        vline(px, sep_x, 5, 18, rgba("#33291B"))

    # Top Village Status Banner LCD (8, 21, 232, 25) — Deep Royal-Navy Ledger Header
    _draw_framed_lcd(px, 8, 21, 232, 25, "#101B2B", "#142236", "#292014", "#EFE1C4", "#9E8D70")
    # Subtle inner top highlight across banner glass
    hline(px, 11, 238, 22, rgba("#1E3452"))
    # Gilded left accent bar (x=9) + shadow separator (x=10)
    vline(px, 9, 22, 44, rgba("#F2C244"))
    px[9, 22] = rgba("#FFF2A8")
    px[9, 44] = rgba("#AD7916")
    vline(px, 10, 22, 44, rgba("#0B121D"))

    # Subtle bronze accent rail between Banner and Viewport (y=46..47)
    hline(px, 9, 238, 46, rgba("#D9A834"))
    hline(px, 9, 238, 47, rgba("#7D5714"))

    # Main Multi-Tab Content Viewport (8, 48, 232, 87) — Deep Slate-Navy Ledger Surface
    _draw_framed_lcd(px, 8, 48, 232, 87, "#111924", "#151F2D", "#261E14", "#EBDCB8", "#9A896B")
    hline(px, 9, 238, 49, rgba("#0C121B"))

    # Architectural gilded-bronze divider groove between Viewport & Inventory/Utility row (y=136..137)
    hline(px, 7, 239, 136, rgba("#8F6E30"))
    hline(px, 7, 239, 137, rgba("#F0E4CA"))

    # Player Inventory (8 + col*18, y=140/158/176) & Hotbar (y=198) — exact menu coordinates!
    for row in range(3):
        for col in range(9):
            _draw_hub_slot(px, 8 + col * 18, 140 + row * 18, style="neutral")
    # Subtle bronze separator groove between inventory & hotbar (y=194..195)
    hline(px, 8, 167, 194, rgba("#9C8E78"))
    hline(px, 8, 167, 195, rgba("#EDE2CC"))
    for col in range(9):
        _draw_hub_slot(px, 8 + col * 18, 198, style="neutral")

    # Vertical Divider & Right Utility Column (Cash Slot + 2 Upgrade Slots + Status LCD)
    vline(px, 173, 139, 214, rgba("#7D694D"))
    vline(px, 174, 139, 214, rgba("#F2E8D2"))
    px[173, 139] = rgba("#C9962C")
    px[173, 214] = rgba("#C9962C")

    # Gilded Cash Slot (181, 144)
    _draw_hub_slot(px, 181, 144, style="gold")
    _draw_watermark(px, 181, 144, WM_COIN, rgba("#8A7A56"), rgba("#6E5F3E"))

    # Brushed Bronze Upgrade Slots 0 & 1 (203, 144 & 223, 144)
    _draw_hub_slot(px, 203, 144, style="bronze")
    _draw_watermark(px, 203, 144, WM_UPGRADE, rgba("#756F66"), rgba("#5C564E"))
    _draw_hub_slot(px, 223, 144, style="bronze")
    _draw_watermark(px, 223, 144, WM_UPGRADE, rgba("#756F66"), rgba("#5C564E"))

    # Right Status Summary LCD (179, 166, 61, 49) with gilded top accent hairline
    _draw_framed_lcd(px, 179, 166, 61, 49, "#101924", "#14202F", "#261E14", "#EBDCB8", "#9A896B")
    hline(px, 180, 238, 167, rgba("#E8B838"))
    px[180, 167] = rgba("#FFF2A8")
    px[238, 167] = rgba("#AD7916")
    hline(px, 180, 238, 168, rgba("#0B111A"))

    # --- Sprite Atlas Strip (v = 224..255) ---
    # 1) Reusable 18x18 Warehouse Slots: Neutral (0,226), Inbound Sapphire (20,226), Outbound Amber (40,226)
    _draw_hub_slot(px, 1, 227, style="neutral")
    _draw_hub_slot(px, 21, 227, style="inbound")
    _draw_hub_slot(px, 41, 227, style="outbound")

    # 2) Season Badges (12x12 at u=62, 76, 90, 104, v=226)
    _draw_season_badge_12x12(px, 62, 226, "spring")
    _draw_season_badge_12x12(px, 76, 226, "summer")
    _draw_season_badge_12x12(px, 90, 226, "autumn")
    _draw_season_badge_12x12(px, 104, 226, "winter")

    # 3) CPI Trend Arrows (10x10 at u=120, 132, 144, v=226)
    _draw_cpi_arrow_10x10(px, 120, 226, "up")
    _draw_cpi_arrow_10x10(px, 132, 226, "down")
    _draw_cpi_arrow_10x10(px, 144, 226, "stable")

    # 4) Auto-Fulfill ON (158, 226, 14x14) & OFF (174, 226, 14x14)
    _draw_auto_badge_14x14(px, 158, 226, True)
    _draw_auto_badge_14x14(px, 174, 226, False)

    # 5) Chunk-Loader Active Badge (192, 226, 16x16)
    _draw_chunk_loader_16x16(px, 192, 226)

    # 6) Golden Mayor Crown Badge (212, 226, 16x16)
    _draw_crown_16x16(px, 212, 226)

    # 7) Weekly Calendar/Restock Icon (232, 226, 12x12)
    _draw_weekly_calendar_12x12(px, 232, 226)

    # 8) 7 Biome Emblem Badges (10x10 each at v=244)
    for idx, biome_id in enumerate(("plains", "desert", "savanna", "taiga", "snowy", "jungle", "swamp")):
        _draw_biome_badge_10x10(px, idx * 12, 244, biome_id)

    # 9) Mini Order Tag Icons (8x8 at v=246: u=86 Biome Specialty, u=96 Seasonal)
    _draw_order_tag_8x8(px, 86, 246, "biome")
    _draw_order_tag_8x8(px, 96, 246, "season")
    return im


def make_gui_comm_terminal() -> Image.Image:
    """256x256 GUI atlas for Handheld Communication Terminal PDA (panel 248x210) — Deep-Space Cyan/Teal."""
    PW, PH = 248, 210
    panel_rects = [
        ("signal_led", 8, 6, 14, 12),
        ("battery_frame", 178, 6, 62, 12),
        ("village_rail", 8, 22, 64, 178),
        ("main_screen", 76, 22, 164, 178),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    sprite_rects = [
        ("panel_body", 0, 0, PW, PH),
        ("battery_green", 0, 216, 56, 10),
        ("battery_red", 0, 228, 56, 10),
        ("village_pin", 64, 216, 12, 12),
        ("mayor_crown", 82, 216, 16, 16),
        ("season_spring", 102, 216, 12, 12),
        ("season_summer", 116, 216, 12, 12),
        ("season_autumn", 130, 216, 12, 12),
        ("season_winter", 144, 216, 12, 12),
        ("weekly_calendar", 160, 216, 12, 12),
        ("biome_plains", 64, 236, 10, 10),
        ("biome_desert", 76, 236, 10, 10),
        ("biome_savanna", 88, 236, 10, 10),
        ("biome_taiga", 100, 236, 10, 10),
        ("biome_snowy", 112, 236, 10, 10),
        ("biome_jungle", 124, 236, 10, 10),
        ("biome_swamp", 136, 236, 10, 10),
        ("tag_biome", 150, 238, 8, 8),
        ("tag_season", 160, 238, 8, 8),
    ]
    _verify_rects(sprite_rects, 256, 256, margin=0)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_pda_tactical_frame(px, PW, PH)

    # Top-left Glowing Emerald-Cyan RF Signal Bars (8, 6, 14, 12)
    _draw_framed_lcd(px, 8, 6, 14, 12, "#09151E", "#0C1B26", "#070E14", "#32495E", "#1A2B3B")
    bar_colors = (
        (rgba("#2BE899"), rgba("#A6FFD8")),
        (rgba("#30EBB5"), rgba("#B0FFE8")),
        (rgba("#36ECD6"), rgba("#B8FFF6")),
        (rgba("#4AEFFF"), rgba("#D6FDFF")),
    )
    for idx, bar_h in enumerate((2, 4, 6, 8)):
        bx = 10 + idx * 3
        col_mid, col_hi = bar_colors[idx]
        fill_rect(px, bx, 16 - bar_h, bx + 1, 15, col_mid)
        hline(px, bx, bx + 1, 16 - bar_h, col_hi)

    # Top-right Recessed Segmented Battery Gauge Frame (178, 6, 58, 12) + battery nub (236, 9)
    _draw_framed_lcd(px, 178, 6, 58, 12, "#09131C", "#0D1A26", "#050A0F", "#4F6880", "#26384A")
    # Subtle internal cell notches (8 x 7px segments) visible when partially discharged
    for seg_x in range(185, 235, 7):
        vline(px, seg_x, 7, 16, rgba("#152536"))
    # Metallic battery terminal nub at (236, 9, 3x6)
    fill_rect(px, 236, 9, 238, 14, rgba("#667C94"))
    hline(px, 236, 238, 9, rgba("#9EB4CC"))
    hline(px, 236, 238, 14, rgba("#334457"))

    # Left Bound Villages List Rail (8, 22, 64, 178) — Deep Ocean-Black + Glowing Teal Top Bar
    _draw_framed_lcd(px, 8, 22, 64, 178, "#0A141E", "#0E1A26", "#050A0F", "#3D556B", "#1F3040")
    hline(px, 9, 70, 23, rgba("#24D9B8"))
    hline(px, 20, 59, 23, rgba("#5EFFDF"))
    # Subtle rail header tint (y=24..32) + separator line under rail header (y=33)
    fill_rect(px, 9, 24, 70, 32, rgba("#0E1D2B"))
    hline(px, 10, 69, 33, rgba("#1C3B52"))
    hline(px, 10, 22, 33, rgba("#258F84"))

    # Right Tactical Viewport (76, 22, 164, 178) — Deep Midnight Tactical Screen
    _draw_framed_lcd(px, 76, 22, 164, 178, "#0B1622", "#0F1D2C", "#050A0F", "#425B73", "#223447")
    # Bright cyan tactical top bar (y=23)
    hline(px, 77, 238, 23, rgba("#2CE6FF"))
    hline(px, 100, 215, 23, rgba("#8AF6FF"))
    # Crisp tactical header zone framing (y=24..40) + horizontal divider (y=41)
    fill_rect(px, 77, 24, 238, 40, rgba("#0F1E2E"))
    for hy in range(25, 40, 2):
        hline(px, 77, 238, hy, rgba("#132538"))
    vline(px, 77, 24, 40, rgba("#2CE6FF"))
    px[77, 24] = rgba("#B8FAFF")
    hline(px, 77, 238, 41, rgba("#1F4B69"))
    hline(px, 77, 94, 41, rgba("#32C8EB"))

    # --- Sprite Atlas Strip (v = 214..255) ---
    # 1) Battery Fill Green-Cyan Segmented Cells (8 clean 7px LED cells): UV (0, 216, 56, 10)
    cell_palette = (
        (rgba("#B8FFDF"), rgba("#2CE894"), rgba("#127A4E")),
        (rgba("#B8FFE5"), rgba("#2CEAA0"), rgba("#127A56")),
        (rgba("#B8FFEC"), rgba("#2EEBB0"), rgba("#127A62")),
        (rgba("#C0FFF2"), rgba("#32ECC2"), rgba("#127D6F")),
        (rgba("#C4FFF6"), rgba("#36EDD4"), rgba("#147D7D")),
        (rgba("#CCF9FF"), rgba("#3CEEE6"), rgba("#157E8E")),
        (rgba("#D4FBFF"), rgba("#46F0F5"), rgba("#16809E")),
        (rgba("#DEFDFF"), rgba("#54F2FF"), rgba("#1882AD")),
    )
    for dx in range(56):
        if dx % 7 == 6:
            vline(px, dx, 216, 225, rgba("#0B2626"))
        else:
            c_hi, c_mid, c_sh = cell_palette[dx // 7]
            vline(px, dx, 216, 225, c_mid)
            px[dx, 216] = c_hi
            px[dx, 217] = c_hi if (dx % 7 == 1) else c_mid
            px[dx, 224] = c_sh
            px[dx, 225] = rgba("#0C4A3D")

    # 2) Battery Fill Red Low Segmented Cells: UV (0, 228, 56, 10)
    for dx in range(56):
        if dx % 7 == 6:
            vline(px, dx, 228, 237, rgba("#380D0B"))
        else:
            vline(px, dx, 228, 237, rgba("#F03E32"))
            px[dx, 228] = rgba("#FFA39B")
            px[dx, 236] = rgba("#B32219")
            px[dx, 237] = rgba("#78140F")

    # 3) Village Pin Icon (64, 216, 12x12)
    _draw_village_pin_12x12(px, 64, 216)

    # 4) Golden Mayor Crown (82, 216, 16x16)
    _draw_crown_16x16(px, 82, 216)

    # 5) Season Badges (12x12 at u=102, 116, 130, 144, v=216)
    _draw_season_badge_12x12(px, 102, 216, "spring")
    _draw_season_badge_12x12(px, 116, 216, "summer")
    _draw_season_badge_12x12(px, 130, 216, "autumn")
    _draw_season_badge_12x12(px, 144, 216, "winter")

    # 6) Weekly Calendar/Restock Icon (160, 216, 12x12)
    _draw_weekly_calendar_12x12(px, 160, 216)

    # 7) 7 Biome Emblem Badges (10x10 each at v=236: u=64..136)
    for idx, biome_id in enumerate(("plains", "desert", "savanna", "taiga", "snowy", "jungle", "swamp")):
        _draw_biome_badge_10x10(px, 64 + idx * 12, 236, biome_id)

    # 8) Mini Order Tag Icons (8x8 at v=238: u=150 Biome Specialty, u=160 Seasonal)
    _draw_order_tag_8x8(px, 150, 238, "biome")
    _draw_order_tag_8x8(px, 160, 238, "season")
    return im


def make_gui_mayor_negotiation() -> Image.Image:
    """256x256 GUI atlas for Village Mayor Negotiation Contract Screen (panel 220x192)."""
    PW, PH = 220, 192
    panel_rects = [
        ("mayor_banner", 8, 14, 204, 34),
        ("contract_box", 8, 52, 204, 62),
        ("action_bar", 8, 118, 204, 26),
        ("benefits_box", 8, 148, 204, 36),
    ]
    _verify_rects(panel_rects, PW, PH, margin=3)

    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = im.load()
    _draw_panel_frame(px, PW, PH, "gold")

    # Ornate gilded-bronze municipal border highlight
    hline(px, 6, 213, 3, rgba("#F0C246"))
    hline(px, 6, 213, 4, rgba("#A87314"))
    hline(px, 6, 213, PH - 4, rgba("#A87314"))

    # Top Mayor Diplomatic Header Banner (8, 14, 204, 34)
    _draw_lcd(px, 8, 14, 204, 34, bg_hex="#15233B", scan_hex="#1B2C4A", rim_lt="#F5EBD6")
    # Baked Golden Mayor Crown emblem at (14, 23, 16, 16)
    _draw_crown_16x16(px, 14, 23)
    vline(px, 35, 18, 43, rgba("#B88928"))

    # Middle Contract Requirements Box (8, 52, 204, 62)
    _draw_recessed_box(px, 8, 52, 204, 62, fill="#D6D0C2")
    # Subtle vertical separator between Village Info (left) and Supply Checklist (right)
    vline(px, 92, 56, 109, rgba("#9E9582"))
    vline(px, 93, 56, 109, rgba("#F0EAD8"))

    # Action Strip (8, 118, 204, 26)
    _draw_recessed_box(px, 8, 118, 204, 26, fill="#BAB4A6")

    # Benefits Summary LCD (8, 148, 204, 36)
    _draw_lcd(px, 8, 148, 204, 36, bg_hex="#18202B", scan_hex="#1E2836", rim_lt="#F5EBD6")

    # Dynamic Sprites in Bottom Margin (v = 196..236)
    # 1) Green Checkmark (8, 196, 14, 14)
    chk_pts = [(10, 203), (11, 204), (12, 205), (13, 206), (14, 205), (15, 204), (16, 203), (17, 202), (18, 201), (19, 200)]
    for cx, cy in chk_pts:
        px[cx, cy] = rgba("#3CE86F")
        px[cx, cy + 1] = rgba("#1B8A3C")
    # 2) Red Missing X (26, 196, 14, 14)
    for d in range(8):
        px[29 + d, 199 + d] = rgba("#F24438")
        px[36 - d, 199 + d] = rgba("#F24438")
    # 3) Golden Mayor Crown (44, 196, 16, 16)
    _draw_crown_16x16(px, 44, 196)
    # 4) Diplomatic Gold Wax Seal (64, 196, 20, 20)
    for y in range(198, 214):
        for x in range(66, 82):
            dist2 = (x - 73.5) ** 2 + (y - 205.5) ** 2
            if dist2 <= 52:
                px[x, y] = rgba("#FFE066") if dist2 <= 22 else (rgba("#D98C16") if dist2 <= 40 else rgba("#8C4B08"))
    return im


# =============================================================================
# 5. LABELED VISUAL PREVIEW CONTACT SHEET
# =============================================================================

def generate_preview_hq(out_path: Path, generated: dict[str, Image.Image]):
    scale = 4  # 16x16 -> 64x64
    gui_scale = 2  # 256x256 -> 512x512 crisp nearest-neighbor!
    bg = rgba("#151A22")
    card_bg = rgba("#202733")
    border = rgba("#425066")
    text_col = rgba("#E8F0F8")
    sub_col = rgba("#98AEC6")
    hdr_col = rgba("#FFD859")
    acc_col = rgba("#4CE8FF")

    coins = [
        ("item/coin_1", "coin_1 (1x)", "Silver-Iron"),
        ("item/coin_5", "coin_5 (5x)", "Copper-Bronze"),
        ("item/coin_10", "coin_10 (10x)", "Blue-Steel"),
        ("item/coin_20", "coin_20 (20x)", "Silver-Emerald"),
        ("item/coin_50", "coin_50 (50x)", "Gilded Crest"),
        ("item/coin_100", "coin_100 (100x)", "Gold Sovereign"),
        ("item/coin_200", "coin_200 (200x)", "Sapphire-Gold"),
        ("item/coin_500", "coin_500 (500x)", "Nether-Amethyst"),
        ("item/coin_1000", "coin_1000 (1000x)", "Iridium-Platinum"),
    ]
    terminals = [
        ("item/comm_terminal", "comm_terminal", "Charged PDA"),
        ("item/comm_terminal_empty", "comm_terminal_empty", "Empty PDA"),
    ]
    blocks = [
        ("block/primitive_mint_machine_front", "prim_front", "Stone Press"),
        ("block/primitive_mint_machine_front_active", "prim_active", "Firebox Lit"),
        ("block/primitive_mint_machine_side", "prim_side", "Flywheel"),
        ("block/primitive_mint_machine_top", "prim_top", "Stamp Cap"),
        ("block/primitive_mint_machine_bottom", "prim_bottom", "Stone Base"),
        ("block/mint_machine_front", "mint_front", "Elec Mint"),
        ("block/mint_machine_front_active", "mint_active", "Elec Active"),
        ("block/mint_machine_side", "mint_side", "EU/FE Port"),
        ("block/mint_machine_top", "mint_top", "Mint Top"),
        ("block/mint_machine_bottom", "mint_bottom", "Mint Base"),
        ("block/atm_machine_front", "atm_front", "ATM Screen"),
        ("block/atm_machine_side", "atm_side", "ATM Side"),
        ("block/atm_machine_top", "atm_top", "ATM Top"),
        ("block/atm_machine_bottom", "atm_bottom", "ATM Base"),
        ("block/cooperation_hub_front", "hub_front", "Hub Shrine"),
        ("block/cooperation_hub_side", "hub_side", "Hub In/Out"),
        ("block/cooperation_hub_top", "hub_top", "Hub Crown"),
        ("block/cooperation_hub_bottom", "hub_bottom", "Hub Base"),
    ]
    guis = [
        ("gui/primitive_mint_machine", "gui/primitive_mint_machine.png (176x166 panel in 256x256)"),
        ("gui/mint_machine", "gui/mint_machine.png (176x176 pure-electric in 256x256)"),
        ("gui/atm_machine", "gui/atm_machine.png (220x214 panel in 256x256)"),
        ("gui/cooperation_hub", "gui/cooperation_hub.png (248x222 panel in 256x256)"),
        ("gui/comm_terminal", "gui/comm_terminal.png (248x210 panel in 256x256)"),
        ("gui/mayor_negotiation", "gui/mayor_negotiation.png (220x192 panel in 256x256)"),
    ]

    W = 1640
    H = 1580
    sheet = Image.new("RGBA", (W, H), bg)
    draw = ImageDraw.Draw(sheet)

    draw.text(
        (24, 14),
        "TycoonCraft2: The New Era (tyc2) - Complete Original CC BY-SA 4.0 Pixel Art & GUI Atlas (35 Textures)",
        fill=hdr_col,
    )

    # Row 1: 9 Coins
    y = 40
    draw.text((24, y), "INDUSTRIAL CURRENCY COINS (9 x 16x16 @ 4x)", fill=acc_col)
    y += 18
    for idx, (key, title, subtitle) in enumerate(coins):
        cx = 24 + idx * 177
        cy = y
        draw.rectangle([cx, cy, cx + 168, cy + 112], fill=card_bg, outline=border)
        tile = generated[key].resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(tile, (cx + 52, cy + 10))
        draw.text((cx + 10, cy + 80), title, fill=text_col)
        draw.text((cx + 10, cy + 94), subtitle, fill=sub_col)

    # Row 2 & 3: Comm Terminals (2) + Block Faces (18) = 20 tiles in 2 clean rows of 10!
    y += 128
    draw.text((24, y), "COMMUNICATION TERMINAL (2) & PRIMITIVE / INDUSTRIAL / ATM / HUB BLOCKS (18) (16x16 @ 4x)", fill=acc_col)
    y += 18
    row_items = terminals + blocks
    for idx, (key, title, subtitle) in enumerate(row_items):
        col = idx % 10
        row = idx // 10
        cx = 24 + col * 159
        cy = y + row * 116
        draw.rectangle([cx, cy, cx + 152, cy + 106], fill=card_bg, outline=border)
        tile = generated[key].resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(tile, (cx + 44, cy + 8))
        draw.text((cx + 8, cy + 77), title[:20], fill=text_col)
        draw.text((cx + 8, cy + 91), subtitle, fill=sub_col)

    # Section 3: 6 GUI Atlases (256x256 @ 2x = 512x512 in crisp 3x2 grid!)
    y += 248
    draw.text((24, y), "INDUSTRIAL GUI ATLASES (6 x 256x256 @ 2x = 512x512)", fill=acc_col)
    y += 18
    for idx, (key, caption) in enumerate(guis):
        col = idx % 3
        row = idx // 3
        cx = 24 + col * 536
        cy = y + row * 556
        draw.rectangle([cx, cy, cx + 526, cy + 544], fill=card_bg, outline=border)
        draw.text((cx + 10, cy + 8), caption, fill=text_col)
        ax, ay = cx + 7, cy + 25
        draw.rectangle([ax - 1, ay - 1, ax + 512, ay + 512], fill=rgba("#11161E"), outline=rgba("#384457"))
        g_img = generated[key].resize((256 * gui_scale, 256 * gui_scale), Image.NEAREST)
        sheet.alpha_composite(g_img, (ax, ay))

    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)
    print(f"Saved preview atlas: {out_path} ({sheet.size[0]}x{sheet.size[1]})")


def main():
    parser = argparse.ArgumentParser(description="Generate all TyC2 pixel-art textures and GUI atlases.")
    parser.add_argument("--output", type=Path, default=TEX_ROOT, help="Output textures directory")
    parser.add_argument("--preview", type=Path, default=DEFAULT_PREVIEW, help="Output labeled preview atlas PNG")
    parser.add_argument("--no-preview", action="store_true", help="Skip generating the preview atlas")
    args = parser.parse_args()

    out_root = args.output.resolve()
    generators = {
        # 9 Coins
        "item/coin_1": make_coin_1,
        "item/coin_5": make_coin_5,
        "item/coin_10": make_coin_10,
        "item/coin_20": make_coin_20,
        "item/coin_50": make_coin_50,
        "item/coin_100": make_coin_100,
        "item/coin_200": make_coin_200,
        "item/coin_500": make_coin_500,
        "item/coin_1000": make_coin_1000,
        # 2 Communication Terminal states
        "item/comm_terminal": lambda: make_comm_terminal(False),
        "item/comm_terminal_empty": lambda: make_comm_terminal(True),
        # 5 Primitive Minting Machine block faces
        "block/primitive_mint_machine_front": lambda: make_primitive_mint_front(False),
        "block/primitive_mint_machine_front_active": lambda: make_primitive_mint_front(True),
        "block/primitive_mint_machine_side": make_primitive_mint_side,
        "block/primitive_mint_machine_top": make_primitive_mint_top,
        "block/primitive_mint_machine_bottom": make_primitive_mint_bottom,
        # 5 Electric Industrial Minting Machine block faces
        "block/mint_machine_front": lambda: make_mint_front(False),
        "block/mint_machine_front_active": lambda: make_mint_front(True),
        "block/mint_machine_side": make_mint_side,
        "block/mint_machine_top": make_mint_top,
        "block/mint_machine_bottom": make_mint_bottom,
        # 4 Industrial ATM block faces
        "block/atm_machine_front": make_atm_front,
        "block/atm_machine_side": make_atm_side,
        "block/atm_machine_top": make_atm_top,
        "block/atm_machine_bottom": make_atm_bottom,
        # 4 Cooperation Hub block faces
        "block/cooperation_hub_front": make_hub_front,
        "block/cooperation_hub_side": make_hub_side,
        "block/cooperation_hub_top": make_hub_top,
        "block/cooperation_hub_bottom": make_hub_bottom,
        # 6 Industrial GUI atlases (256x256)
        "gui/primitive_mint_machine": make_gui_primitive_mint_machine,
        "gui/mint_machine": make_gui_mint_machine,
        "gui/atm_machine": make_gui_atm_machine,
        "gui/cooperation_hub": make_gui_cooperation_hub,
        "gui/comm_terminal": make_gui_comm_terminal,
        "gui/mayor_negotiation": make_gui_mayor_negotiation,
    }

    generated: dict[str, Image.Image] = {}
    for rel, fn in generators.items():
        im = fn()
        dest = out_root / (rel + ".png")
        dest.parent.mkdir(parents=True, exist_ok=True)
        im.save(dest)
        generated[rel] = im
        rel_disp = dest.relative_to(REPO_ROOT) if REPO_ROOT in dest.parents else dest
        print(f"Wrote {rel_disp} ({im.size[0]}x{im.size[1]})")

    if not args.no_preview and args.preview is not None:
        generate_preview_hq(args.preview.resolve(), generated)


if __name__ == "__main__":
    main()
