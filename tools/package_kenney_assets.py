#!/usr/bin/env python3
"""Packages runtime WebP sprites from the Kenney RPG Urban tilemap.

Source art: art/kenney-rpg-urban (Kenney.nl, CC0).
Output:     app/src/main/res/drawable-nodpi

Pixel art is upscaled with nearest-neighbour only; the renderer disables
bitmap filtering so the sprites stay crisp at any board size.
"""
import colorsys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
TILEMAP = ROOT / "art/kenney-rpg-urban/Tilemap/tilemap_packed.png"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
T = 16

sheet = Image.open(TILEMAP).convert("RGBA")


def tile(col, row, w=1, h=1):
    """Grab a w×h tile block from the packed tilemap."""
    return sheet.crop((col * T, row * T, (col + w) * T, (row + h) * T))


def save(img, name, scale=8):
    """Upscale with nearest-neighbour and write a lossless WebP."""
    big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    path = OUT / f"{name}.webp"
    big.save(path, "WEBP", lossless=True, quality=100, method=6)
    return path


def trim(img):
    box = img.getbbox()
    return img.crop(box) if box else img


# --- Colour ramps ----------------------------------------------------------
# The side-view car exists only in green, so its four body shades are remapped
# onto ramps taken from the pack's own red and orange vehicles.
GREEN_RAMP = [(66, 163, 121), (54, 144, 105), (45, 122, 87), (30, 83, 70)]
RAMPS = {
    "green": GREEN_RAMP,
    "coral": [(220, 97, 93), (194, 80, 77), (165, 66, 64), (116, 44, 52)],
    "yellow": [(245, 169, 76), (218, 146, 62), (188, 125, 54), (135, 77, 39)],
    # No blue vehicle ships in the pack: rotate the green ramp's hue instead so
    # the shading structure stays identical to its siblings.
    "blue": None,
}


def hue_shifted(ramp, hue):
    out = []
    for r, g, b in ramp:
        _, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        r2, g2, b2 = colorsys.hsv_to_rgb(hue, s, v)
        out.append((round(r2 * 255), round(g2 * 255), round(b2 * 255)))
    return out


RAMPS["blue"] = hue_shifted(GREEN_RAMP, 0.56)


def recolour(img, ramp):
    if ramp is GREEN_RAMP:
        return img
    table = {src: dst for src, dst in zip(GREEN_RAMP, ramp)}
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in table:
                px[x, y] = (*table[(r, g, b)], a)
    return out


# --- Walkers ---------------------------------------------------------------
# Each character block is 4 columns (left/front/back/right) × 3 animation
# frames. Pedestrians cross away from the camera, so the board uses the back
# view and the UI uses the front view.
FRONT, BACK = 24, 25
WALKERS = {"walker_green": 0, "walker_coral": 3, "walker_blue": 9}

def union_box(images):
    """One crop box for a whole animation, so frames never shift or rescale."""
    boxes = [i.getbbox() for i in images if i.getbbox()]
    return (min(b[0] for b in boxes), min(b[1] for b in boxes),
            max(b[2] for b in boxes), max(b[3] for b in boxes))


for name, row in WALKERS.items():
    save(trim(tile(FRONT, row)), name)
    frames = [tile(BACK, row + i) for i in range(3)]
    box = union_box(frames)
    for i, frame in enumerate(frames):
        save(frame.crop(box), f"{name}_w{i}")

# --- Cars ------------------------------------------------------------------
# Side view facing right; Board mirrors it for lanes travelling left.
car = trim(tile(21, 17, 2, 1))
for suffix, ramp in RAMPS.items():
    save(recolour(car, ramp), f"car_{suffix}")

# --- Scenery ---------------------------------------------------------------
save(trim(tile(16, 8, 1, 2)), "tree")
save(trim(tile(16, 11, 1, 2)), "tree_autumn")

def build_house():
    """A facade assembled from pieces: cornice, brick, window row, door row."""
    brick, cornice = tile(18, 2), tile(17, 0)
    img = Image.new("RGBA", (T * 3, T * 4), (0, 0, 0, 0))
    for col in range(3):
        img.alpha_composite(cornice, (col * T, 0))
        for row in range(1, 4):
            img.alpha_composite(brick, (col * T, row * T))
    img.alpha_composite(tile(12, 9), (0, T))             # window
    img.alpha_composite(tile(12, 9), (T * 2, T))         # window
    img.alpha_composite(tile(13, 10, 1, 2), (T, T * 2))  # door
    return img


save(build_house(), "house")

# --- Ground tiles (tiled by the board's shaders) ---------------------------
save(tile(1, 1), "ground_grass", scale=4)
save(tile(7, 16), "ground_road", scale=4)
save(tile(1, 4), "ground_walk", scale=4)
save(tile(2, 16), "ground_crosswalk", scale=4)
save(tile(0, 16), "ground_lane_line", scale=4)

# --- Street furniture and tool icons ---------------------------------------
save(trim(tile(4, 15)), "traffic_light")
save(trim(tile(4, 15)), "icon_signal")
save(trim(tile(2, 16)), "icon_crosswalk")
save(trim(tile(4, 14)), "icon_bump")
save(trim(tile(4, 6, 1, 2)), "stop_sign")   # red sign on a pole, for the board
save(trim(tile(4, 6).crop((0, 0, T, 11))), "icon_officer")  # just the sign head, for the UI
save(trim(tile(5, 12)), "icon_wait")        # kerbside bench


def look_both_ways():
    """No tile means 'look both ways': flank a pedestrian with two arrows."""
    GOLD = (245, 169, 76, 255)
    img = Image.new("RGBA", (28, 16), (0, 0, 0, 0))
    img.alpha_composite(tile(FRONT, 0), (6, 0))
    px = img.load()
    for i in range(4):                       # arrow heads
        for y in range(7 - i, 9 + i):
            px[2 + i, y] = GOLD
            px[25 - i, y] = GOLD
    for x in range(2, 6):                    # shafts
        px[x, 7] = px[x, 8] = GOLD
        px[27 - x + 2, 7] = px[27 - x + 2, 8] = GOLD
    return img


save(look_both_ways(), "icon_look")


def star_sprite():
    """A 16×16 pixel star in the pack's gold, with a darker rim."""
    rows = [
        "0000000110000000",
        "0000000110000000",
        "0000001111000000",
        "0000001111000000",
        "1111111111111110",
        "0111111111111100",
        "0011111111111000",
        "0001111111110000",
        "0001111111110000",
        "0011111111111000",
        "0011110110111000",
        "0111100110011100",
        "0111000110001100",
        "0110000110000100",
        "0000000000000000",
        "0000000000000000",
    ]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, line in enumerate(rows):
        for x, ch in enumerate(line):
            if ch == "1":
                px[x, y] = (245, 169, 76, 255)
    # Rim: any lit pixel with an empty neighbour below or to the side.
    rim = img.copy()
    rpx = rim.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                continue
            edge = any(
                not (0 <= x + dx < 16 and 0 <= y + dy < 16) or px[x + dx, y + dy][3] == 0
                for dx, dy in ((0, 1), (1, 0), (-1, 0))
            )
            if edge:
                rpx[x, y] = (135, 77, 39, 255)
    return rim


save(star_sprite(), "star")

print(f"Assets written to {OUT.relative_to(ROOT)}")


# --- Home screen scene -----------------------------------------------------
def diorama():
    """A 4:5 street scene for the home screen, built from the same tiles.

    The top third stays plain grass: the logo and tagline are drawn over it.
    """
    W, H = 192, 240
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    grass, road, walk, zebra = tile(1, 1), tile(7, 16), tile(1, 4), tile(2, 16)

    def band(y0, y1, src):
        for y in range(y0, y1, T):
            for x in range(0, W, T):
                img.alpha_composite(src, (x, y))

    band(0, H, grass)
    band(136, 152, walk)          # far kerb
    band(152, 200, road)
    band(200, 216, walk)          # near kerb
    for y in range(152, 200, T):  # the crossing the game is named after
        for x in (72, 88, 104):
            img.alpha_composite(zebra, (x, y))

    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 152, W, 153], fill=(160, 156, 202, 255))
    draw.rectangle([0, 198, W, 199], fill=(160, 156, 202, 255))
    for x in range(4, W, 32):     # dashed centre line, interrupted by the zebra
        if not 60 < x < 120:
            draw.rectangle([x, 174, x + 17, 175], fill=(160, 156, 202, 255))

    # Buildings on the far side, trees filling the gaps.
    house = build_house()
    img.alpha_composite(house, (16, 72))
    img.alpha_composite(house, (128, 72))
    tree16 = trim(tile(16, 8, 1, 2))
    img.alpha_composite(tree16, (70, 104))
    img.alpha_composite(tree16, (106, 104))
    img.alpha_composite(trim(tile(4, 15)), (54, 120))      # traffic light on the kerb
    img.alpha_composite(trim(tile(16, 11, 1, 2)), (4, 206))
    img.alpha_composite(tree16, (172, 204))

    # Traffic waiting either side of the crossing, pedestrians using it.
    car = trim(tile(21, 17, 2, 1))
    img.alpha_composite(recolour(car, RAMPS["coral"]), (20, 158))
    img.alpha_composite(recolour(car, RAMPS["yellow"]).transpose(Image.FLIP_LEFT_RIGHT), (142, 180))
    img.alpha_composite(trim(tile(BACK, 0)), (76, 156))    # mid-crossing
    img.alpha_composite(trim(tile(BACK, 4)), (100, 174))
    img.alpha_composite(trim(tile(FRONT, 9)), (88, 216))   # waiting at the kerb
    return img


save(diorama(), "home_diorama", scale=5)
print("Home scene written")


# --- Extra pedestrians -------------------------------------------------------
# Two more characters from the same tileset: a teenager (row 12) who crosses
# glued to a phone, and a kid (row 15) who rides a wheelchair.
EXTRA_WALKERS = {"walker_slate": 12, "walker_rust": 15}
for name, row in EXTRA_WALKERS.items():
    save(trim(tile(FRONT, row)), name)
    frames = [tile(BACK, row + i) for i in range(3)]
    box = union_box(frames)
    for i, frame in enumerate(frames):
        save(frame.crop(box), f"{name}_w{i}")

# --- Wheelchair rider --------------------------------------------------------
# Chair from "[LPC Revised] Elders!" (Eliza Wyatt et al., OGA-BY 3.0), a 64 px
# sheet halved with nearest-neighbour so it sits at the tileset's 16 px scale.
# Back view: the rider's head shows above the backrest. Front view: the rider's
# upper body sits over the seat, legs hidden by the chair.
LPC = ROOT / "art/lpc-elders/wheelchair_blue.png"
chair_sheet = Image.open(LPC).convert("RGBA")


def chair_frame(row):
    f = chair_sheet.crop((0, row * 64, 64, (row + 1) * 64))
    f = trim(f)
    return f.resize((f.width // 2, f.height // 2), Image.NEAREST)


CHAIR_BACK, CHAIR_FRONT = chair_frame(0), chair_frame(2)   # 16×15 and 16×21
RIDER = 15


def chair_back_view(frame, bob):
    img = Image.new("RGBA", (20, 24), (0, 0, 0, 0))
    img.alpha_composite(tile(BACK, RIDER + frame), (4, -bob))
    img.alpha_composite(CHAIR_BACK, (2, 7))
    return img


def chair_front_view():
    img = Image.new("RGBA", (20, 26), (0, 0, 0, 0))
    img.alpha_composite(CHAIR_FRONT, (2, 3))
    img.alpha_composite(tile(FRONT, RIDER).crop((0, 0, T, 11)), (4, 2))
    return img


save(chair_front_view(), "walker_chair")
for i, bob in enumerate((0, 1, 0)):
    save(chair_back_view(i, bob), f"walker_chair_w{i}")

# --- Phone marker ------------------------------------------------------------
# A classic handset hovers over the teenager; the tool icon crosses it out.
INK = (43, 37, 64, 255)
PHONE = ROOT / "art/phones/nokia.png"


def ink_outline(img):
    w, h = img.size
    out = Image.new("RGBA", (w + 2, h + 2), (0, 0, 0, 0))
    src, dst = img.load(), out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    dst[x + 1 + dx, y + 1 + dy] = INK
    for y in range(h):
        for x in range(w):
            if src[x, y][3]:
                dst[x + 1, y + 1] = src[x, y]
    return out


def phone_sprite(height=13):
    im = trim(Image.open(PHONE).convert("RGBA"))
    im = im.resize((round(im.width * height / im.height), height), Image.LANCZOS)
    px = im.load()
    for y in range(im.height):            # hard alpha: pixel art has no fringe
        for x in range(im.width):
            r, g, b, a = px[x, y]
            px[x, y] = (r, g, b, 255 if a > 100 else 0)
    return ink_outline(im)


def crossed_out(img):
    out = img.copy()
    px = out.load()
    w, h = out.size
    for i in range(max(w, h)):
        x = int(i * w / max(w, h))
        y = h - 1 - int(i * h / max(w, h))
        for dx in (0, 1):
            if x + dx < w:
                px[x + dx, y] = (220, 97, 93, 255)
    return out


phone = phone_sprite()
save(phone, "phone")
save(crossed_out(phone), "icon_phone_away")
print("Extra pedestrians, wheelchair rider and phone marker written")
