#!/usr/bin/env python3
"""Genera el icono de la app (adaptativo, vectorial) y la imagen docs/icon.png.

El icono dibuja las barras de intensidad de un entreno por bloques (como el cronograma de la app, con los
mismos colores de zona) sobre fondo oscuro, y una línea roja de marca debajo, como una pista.

Uso:  python3 scripts/icon/generate.py        (necesita ImageMagick, `magick`, solo para docs/icon.png)
Escribe en app/src/main/res/ (drawable/ y mipmap-anydpi-v26/) y en docs/icon.png.
"""
import os
import subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Lienzo adaptativo de 108 dp; la zona segura es el círculo central de ~66 dp.
BG_TOP, BG_BOTTOM = "#2E2E34", "#0F0F12"
RED = "#D0002A"
AMBER, PINK, GREEN, BLUE = "#FFC72C", "#E0115F", "#9DBF4A", "#4FC3F0"

BAR_W, GAP = 5.0, 2.0
# (alto, color): calentamiento, 3 × (trabajo + recuperación), enfriamiento
BARS = [(20, AMBER), (38, PINK), (13, GREEN), (38, PINK), (13, GREEN), (38, PINK), (20, BLUE)]
X0 = (108 - (len(BARS) * BAR_W + (len(BARS) - 1) * GAP)) / 2
BASE_Y = 69.5                  # base de las barras
SPAN = len(BARS) * BAR_W + (len(BARS) - 1) * GAP
LINE = (X0, 73.5, SPAN, 3.0)   # x, y, ancho, alto de la línea roja (mismo ancho que las barras)


def f(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def pill(x, y, w, h):
    """Rectángulo con las cuatro esquinas redondeadas (r = w/2 en las barras)."""
    r = min(w, h) / 2
    return (f"M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)}v{f(h - 2 * r)}"
            f"a{f(r)},{f(r)} 0 0 1 -{f(r)},{f(r)}h-{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 -{f(r)},-{f(r)}"
            f"v-{f(h - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},-{f(r)}z")


def bars(color_of):
    out = []
    for i, (h, c) in enumerate(BARS):
        x = X0 + i * (BAR_W + GAP)
        out.append((pill(x, BASE_Y - h, BAR_W, h), color_of(c)))
    lx, ly, lw, lh = LINE
    out.append((pill(lx, ly, lw, lh), color_of(RED)))
    return out


def vector(paths, extra_ns=""):
    body = "\n".join(f'    <path android:fillColor="{c}" android:pathData="{d}" />' for d, c in paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra_ns}\n'
            f'    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n{body}\n</vector>\n')


def write(rel, text):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)


# Fondo: degradado vertical
write("drawable/ic_launcher_background.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"
                android:startColor="{BG_TOP}" android:endColor="{BG_BOTTOM}" />
        </aapt:attr>
    </path>
</vector>
''')
write("drawable/ic_launcher_foreground.xml", vector(bars(lambda c: c)))
# Capa monocroma (iconos temáticos de Android 13+): solo la silueta; el sistema la tiñe.
write("drawable/ic_launcher_monochrome.xml", vector(bars(lambda c: "#000000")))
adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
write("mipmap-anydpi-v26/ic_launcher.xml", adaptive)
write("mipmap-anydpi-v26/ic_launcher_round.xml", adaptive)


# ---- docs/icon.png: la misma imagen renderizada con ImageMagick (cuadrado redondeado, 512 px) ----
def render_png(out, size=512):
    k = size / 72.0                      # solo se ve la zona central de 72 dp (la máscara del lanzador)
    off = -18 * k                        # 108 dp -> recorte de 72 dp centrado
    draw = []
    for d, color in bars(lambda c: c):
        pass
    cmd = ["magick", "-size", f"{size}x{size}", f"gradient:{BG_TOP}-{BG_BOTTOM}"]
    # El degradado cubre 108 dp; al recortar a 72 dp se ve su tramo central. Se aproxima con el degradado entero.
    def rect(x, y, w, h, color):
        r = min(w, h) / 2 * k
        x0, y0 = x * k + off, y * k + off
        return ["-fill", color, "-draw", f"roundrectangle {x0:.1f},{y0:.1f} {x0 + w * k:.1f},{y0 + h * k:.1f} {r:.1f},{r:.1f}"]
    for i, (h, c) in enumerate(BARS):
        cmd += rect(X0 + i * (BAR_W + GAP), BASE_Y - h, BAR_W, h, c)
    cmd += rect(LINE[0], LINE[1], LINE[2], LINE[3], RED)
    corner = int(size * 0.22)
    cmd += ["(", "+clone", "-alpha", "transparent", "-fill", "white", "-draw",
            f"roundrectangle 0,0 {size - 1},{size - 1} {corner},{corner}", ")",
            "-compose", "DstIn", "-composite", out]
    subprocess.run(cmd, check=True)


os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
render_png(os.path.join(ROOT, "docs", "icon.png"))
print("Icono generado en app/src/main/res/ y docs/icon.png")
