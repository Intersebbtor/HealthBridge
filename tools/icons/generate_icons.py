#!/usr/bin/env python3
"""Generate all HealthBridge icons from one glyph definition.

Glyph: a bowstring bridge (deck, arch, hangers) with a heart above it.
Coordinates live in the 108x108 Android adaptive icon space.

Outputs:
  - Android adaptive icon (vector background, foreground, monochrome)
  - macOS app icon (.icns + PNGs) and window/logo PNG
  - macOS menu bar template icon (SVG, black on transparent)

Usage: python3 tools/icons/generate_icons.py [--preview DIR]
"""
import math
import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# Brand colors
TEAL = (45, 212, 191)    # #2DD4BF
BLUE = (37, 99, 235)     # #2563EB
WHITE = (255, 255, 255)

# Glyph geometry (108 space): bowstring bridge with a heart above it.
STROKE = 6.0
DECK_Y = 76.0
DECK_X0, DECK_X1 = 20.0, 88.0
ARCH_X0, ARCH_X1, ARCH_RISE = 26.0, 82.0, 18.0
HANGER_X = (40.0, 68.0)
HEART_CX, HEART_CY, HEART_W = 54.0, 38.0, 28.0


def heart_path(cx, cy, w):
    """Heart as SVG path. (cx, cy) is roughly the visual center."""
    s = w / 2.0
    top = cy - s * 0.55
    bottom = cy + s * 0.95
    r = s * 0.5
    lx, rx = cx - r, cx + r
    return (
        f"M{cx:.2f},{bottom:.2f} "
        f"C{cx - s * 0.35:.2f},{bottom - s * 0.35:.2f} {cx - s:.2f},{cy + s * 0.15:.2f} {cx - s:.2f},{top + r * 0.15:.2f} "
        f"A{r:.2f},{r:.2f} 0 0 1 {cx:.2f},{top:.2f} "
        f"A{r:.2f},{r:.2f} 0 0 1 {cx + s:.2f},{top + r * 0.15:.2f} "
        f"C{cx + s:.2f},{cy + s * 0.15:.2f} {cx + s * 0.35:.2f},{bottom - s * 0.35:.2f} {cx:.2f},{bottom:.2f} Z"
    )



def _arch():
    half = (ARCH_X1 - ARCH_X0) / 2
    r = (half * half + ARCH_RISE * ARCH_RISE) / (2 * ARCH_RISE)
    cy = DECK_Y + r - ARCH_RISE
    hangers = [f"M{x:.2f},{DECK_Y:.2f} L{x:.2f},{cy - math.sqrt(r * r - (x - 54) ** 2):.2f}" for x in HANGER_X]
    return f"M{ARCH_X0:.2f},{DECK_Y:.2f} A{r:.2f},{r:.2f} 0 0 1 {ARCH_X1:.2f},{DECK_Y:.2f}", hangers


ARCH_PATH, HANGER_PATHS = _arch()
DECK_PATH = f"M{DECK_X0:.2f},{DECK_Y:.2f} L{DECK_X1:.2f},{DECK_Y:.2f}"
HEART_PATH = heart_path(HEART_CX, HEART_CY, HEART_W)

STROKES = [DECK_PATH, ARCH_PATH] + HANGER_PATHS
FILLS = [HEART_PATH]


# ---------------------------------------------------------------- path sampling
def _tokens(d):
    import re
    return re.findall(r"[MLCAZ]|-?\d+(?:\.\d+)?", d)


def _arc_points(x0, y0, r, large, sweep, x1, y1, n=48):
    # Endpoint to center parameterization for a circle (rx == ry, no rotation)
    dx, dy = (x0 - x1) / 2, (y0 - y1) / 2
    d2 = dx * dx + dy * dy
    r = max(r, math.sqrt(d2))
    f = math.sqrt(max(0.0, (r * r - d2) / d2)) if d2 else 0
    if large == sweep:
        f = -f
    cxp, cyp = f * dy, -f * dx
    cx, cy = cxp + (x0 + x1) / 2, cyp + (y0 + y1) / 2
    a0 = math.atan2(y0 - cy, x0 - cx)
    a1 = math.atan2(y1 - cy, x1 - cx)
    da = a1 - a0
    if sweep and da < 0:
        da += 2 * math.pi
    if not sweep and da > 0:
        da -= 2 * math.pi
    return [(cx + r * math.cos(a0 + da * i / n), cy + r * math.sin(a0 + da * i / n)) for i in range(1, n + 1)]


def sample(d):
    t = _tokens(d)
    i, pts, cur = 0, [], (0.0, 0.0)
    num = lambda k: float(t[i + k])
    while i < len(t):
        c = t[i]
        if c == "M":
            cur = (num(1), num(2)); pts.append(cur); i += 3
        elif c == "L":
            cur = (num(1), num(2)); pts.append(cur); i += 3
        elif c == "C":
            p0, p1, p2, p3 = cur, (num(1), num(2)), (num(3), num(4)), (num(5), num(6))
            for k in range(1, 33):
                u = k / 32
                x = (1-u)**3*p0[0] + 3*(1-u)**2*u*p1[0] + 3*(1-u)*u**2*p2[0] + u**3*p3[0]
                y = (1-u)**3*p0[1] + 3*(1-u)**2*u*p1[1] + 3*(1-u)*u**2*p2[1] + u**3*p3[1]
                pts.append((x, y))
            cur = p3; i += 7
        elif c == "A":
            r, large, sweep, x1, y1 = num(1), int(num(4)), int(num(5)), num(6), num(7)
            pts += _arc_points(cur[0], cur[1], r, large, sweep, x1, y1)
            cur = (x1, y1); i += 8
        elif c == "Z":
            i += 1
        else:
            raise ValueError(c)
    return pts


def _bbox():
    xs, ys = [], []
    for d in STROKES:
        for x, y in sample(d):
            xs += [x - STROKE / 2, x + STROKE / 2]; ys += [y - STROKE / 2, y + STROKE / 2]
    for d in FILLS:
        for x, y in sample(d):
            xs.append(x); ys.append(y)
    return min(xs), min(ys), max(xs), max(ys)


BBOX = _bbox()


# ---------------------------------------------------------------- raster
def draw_glyph(size, color, transform, stroke_scale=1.0):
    """Render glyph to an RGBA image. transform maps 108-space -> pixels (scale, ox, oy)."""
    ss = 8
    s, ox, oy = transform
    img = Image.new("L", (size * ss, size * ss), 0)
    dr = ImageDraw.Draw(img)
    tp = lambda p: ((p[0] * s + ox) * ss, (p[1] * s + oy) * ss)
    w = STROKE * stroke_scale * s * ss
    for d in STROKES:
        pts = [tp(p) for p in sample(d)]
        dr.line(pts, fill=255, width=int(round(w)), joint="curve")
        for p in (pts[0], pts[-1]):
            dr.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=255)
    for d in FILLS:
        dr.polygon([tp(p) for p in sample(d)], fill=255)
    mask = img.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), color + (0,))
    out.putalpha(mask)
    return out


def gradient(size, c0=TEAL, c1=BLUE):
    g = Image.new("RGB", (size, size))
    px = g.load()
    for y in range(size):
        for x in range(size):
            u = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(int(c0[k] + (c1[k] - c0[k]) * u) for k in range(3))
    return g


def macos_icon(size=1024):
    """Big Sur style: rounded square with margin, shadow, gradient, white glyph."""
    ss = 2
    S = size * ss
    inset = int(100 / 1024 * S)
    rad = int(185 / 1024 * S)
    body = (inset, inset, S - inset, S - inset)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow = Image.new("L", (S, S), 0)
    ImageDraw.Draw(shadow).rounded_rectangle((body[0], body[1] + int(12 / 1024 * S), body[2], body[3] + int(12 / 1024 * S)), rad, fill=90)
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(18 / 1024 * S)))
    canvas.paste((0, 0, 0, 255), (0, 0), shadow)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(body, rad, fill=255)
    canvas.paste(gradient(S), (0, 0), mask)
    # subtle top highlight
    hl = Image.new("L", (S, S), 0)
    ImageDraw.Draw(hl).rounded_rectangle(body, rad, fill=28)
    fade = Image.linear_gradient("L").resize((S, S)).point(lambda v: 255 - v)
    hl = Image.composite(hl, Image.new("L", (S, S), 0), fade)
    canvas.paste((255, 255, 255, 255), (0, 0), hl)
    canvas = canvas.resize((size, size), Image.LANCZOS)
    # glyph: fit bbox into ~56% of body width
    bw, bh = BBOX[2] - BBOX[0], BBOX[3] - BBOX[1]
    target = (body[2] - body[0]) / ss * 0.58
    s = target / bw
    ox = size / 2 - (BBOX[0] + bw / 2) * s
    oy = size / 2 - (BBOX[1] + bh / 2) * s
    glyph = draw_glyph(size, WHITE, (s, ox, oy))
    canvas.alpha_composite(glyph)
    return canvas


def logo_square(size, radius_ratio=0.225):
    """Flat logo for in-app use: gradient rounded square, no margin."""
    ss = 4
    S = size * ss
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), int(S * radius_ratio), fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(gradient(S), (0, 0), mask)
    out = out.resize((size, size), Image.LANCZOS)
    bw, bh = BBOX[2] - BBOX[0], BBOX[3] - BBOX[1]
    s = size * 0.64 / bw
    out.alpha_composite(draw_glyph(size, WHITE, (s, size / 2 - (BBOX[0] + bw / 2) * s, size / 2 - (BBOX[1] + bh / 2) * s)))
    return out


# ---------------------------------------------------------------- writers
def write_icns(icon1024, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        iconset = os.path.join(tmp, "icon.iconset")
        os.makedirs(iconset)
        for base in (16, 32, 128, 256, 512):
            icon1024.resize((base, base), Image.LANCZOS).save(f"{iconset}/icon_{base}x{base}.png")
            icon1024.resize((base * 2, base * 2), Image.LANCZOS).save(f"{iconset}/icon_{base}x{base}@2x.png")
        subprocess.run(["iconutil", "-c", "icns", iconset, "-o", path], check=True)


def tray_svg():
    pad = 1.0
    x0, y0, x1, y1 = BBOX
    w, h = x1 - x0 + 2 * pad, y1 - y0 + 2 * pad
    side = max(w, h)
    vx, vy = x0 - pad - (side - w) / 2, y0 - pad - (side - h) / 2
    strokes = "\n".join(
        f'  <path d="{d}" fill="none" stroke="#000" stroke-width="{STROKE * 1.15:.2f}" stroke-linecap="round" stroke-linejoin="round"/>'
        for d in STROKES)
    fills = "\n".join(f'  <path d="{d}" fill="#000"/>' for d in FILLS)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" '
            f'viewBox="{vx:.2f} {vy:.2f} {side:.2f} {side:.2f}">\n{strokes}\n{fills}\n</svg>\n')


def android_vector(color="#FFFFFFFF"):
    strokes = "\n".join(
        f'    <path android:pathData="{d}" android:strokeColor="{color}" android:strokeWidth="{STROKE}" '
        f'android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000"/>'
        for d in STROKES)
    fills = "\n".join(f'    <path android:pathData="{d}" android:fillColor="{color}"/>' for d in FILLS)
    # Fit the glyph into the adaptive icon safe zone (centered 66dp circle)
    bw, bh = BBOX[2] - BBOX[0], BBOX[3] - BBOX[1]
    sc = min(56 / bw, 56 / bh)
    tx = -((BBOX[0] + bw / 2) - 54) * sc
    ty = -((BBOX[1] + bh / 2) - 54) * sc
    group = (f'    <group android:pivotX="54" android:pivotY="54" android:scaleX="{sc:.4f}" android:scaleY="{sc:.4f}" '
             f'android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n')
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/icons/generate_icons.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            f'{group}{strokes}\n{fills}\n    </group>\n</vector>\n')


def android_background():
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by tools/icons/generate_icons.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <path android:pathData="M0,0h108v108h-108z">\n'
            '        <aapt:attr name="android:fillColor">\n'
            '            <gradient android:type="linear" android:startX="0" android:startY="0"\n'
            '                android:endX="108" android:endY="108"\n'
            '                android:startColor="#FF2DD4BF" android:endColor="#FF2563EB"/>\n'
            '        </aapt:attr>\n'
            '    </path>\n'
            '</vector>\n')


ADAPTIVE = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background" />\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_foreground" />\n'
            '</adaptive-icon>\n')


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)


def main():
    preview = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    icon = macos_icon()
    if preview:
        os.makedirs(preview, exist_ok=True)
        icon.save(f"{preview}/macos_1024.png")
        for sz in (16, 32, 64):
            icon.resize((sz, sz), Image.LANCZOS).resize((sz * 4, sz * 4), Image.NEAREST).save(f"{preview}/macos_{sz}.png")
        logo_square(256).save(f"{preview}/logo.png")
        write(f"{preview}/tray.svg", tray_svg())
        # tray preview: black glyph at 18 and 36 px on light + white on dark menubar
        for px in (18, 36):
            s = px * 0.9 / (BBOX[2] - BBOX[0])
            g = draw_glyph(px, (0, 0, 0), (s, px / 2 - (BBOX[0] + (BBOX[2] - BBOX[0]) / 2) * s, px / 2 - (BBOX[1] + (BBOX[3] - BBOX[1]) / 2) * s), 1.15)
            bg = Image.new("RGBA", (px, px), (236, 236, 236, 255)); bg.alpha_composite(g)
            bg.resize((px * 6, px * 6), Image.NEAREST).save(f"{preview}/tray_{px}.png")
        return

    desk = f"{ROOT}/desktop/src/main/resources"
    write_icns(icon, f"{desk}/icons/icon.icns")
    icon.resize((512, 512), Image.LANCZOS).save(f"{desk}/icons/icon.png")
    logo_square(256).save(f"{desk}/logo.png")
    write(f"{desk}/tray_template.svg", tray_svg())

    res = f"{ROOT}/composeApp/src/androidMain/res"
    write(f"{res}/drawable/ic_launcher_foreground.xml", android_vector())
    write(f"{res}/drawable/ic_launcher_background.xml", android_background())
    write(f"{res}/mipmap-anydpi-v26/ic_launcher.xml", ADAPTIVE)
    write(f"{res}/mipmap-anydpi-v26/ic_launcher_round.xml", ADAPTIVE)
    icon.resize((256, 256), Image.LANCZOS).save(f"{ROOT}/docs/icon.png")
    print("Icons written.")


if __name__ == "__main__":
    main()
