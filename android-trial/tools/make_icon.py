#!/usr/bin/env python3
"""Genera los íconos de PipaCo Prueba: ícono real + insignia roja 'PRUEBA'. Uso: python3 android-trial/tools/make_icon.py"""
import os
from PIL import Image, ImageDraw, ImageFont
H = os.path.dirname(os.path.abspath(__file__)); REPO = os.path.abspath(os.path.join(H, '..', '..'))
RES = os.path.join(H, '..', 'app', 'src', 'main', 'res')
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
base = Image.open(os.path.join(REPO, 'icons', 'icon-512.png')).convert('RGBA')

def badge(img, cx, cy, w):
    d = ImageDraw.Draw(img); txt = 'PRUEBA'
    fs = int(w * 0.2); f = ImageFont.truetype(FONT, fs)
    while d.textlength(txt, font=f) > w * 0.78: fs -= 1; f = ImageFont.truetype(FONT, fs)
    tw = d.textlength(txt, font=f); bh = int(fs * 1.55); bw = int(tw + fs * 1.1)
    x0, y0 = int(cx - bw / 2), int(cy - bh / 2)
    d.rounded_rectangle([x0, y0, x0 + bw, y0 + bh], radius=bh // 2, fill='#dc2626', outline='white', width=max(2, bh // 12))
    d.text((cx, cy), txt, font=f, fill='white', anchor='mm')
    return img

def legacy(S):  # ícono clásico (cuadrado completo)
    im = base.resize((S * 4, S * 4), Image.LANCZOS)
    badge(im, S * 2, int(S * 4 * 0.80), S * 4 * 0.86)
    return im.resize((S, S), Image.LANCZOS)

def foreground(S):  # capa adaptativa 108dp: contenido en la zona segura (66dp)
    C = S * 4; im = Image.new('RGBA', (C, C), (0, 0, 0, 0)); inner = int(C * 66 / 108)
    ic = base.resize((inner, inner), Image.LANCZOS); off = (C - inner) // 2; im.alpha_composite(ic, (off, off))
    badge(im, C // 2, off + int(inner * 0.84), inner * 0.92)
    return im.resize((S, S), Image.LANCZOS)

for dens, s in {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}.items():
    d = os.path.join(RES, 'mipmap-' + dens); os.makedirs(d, exist_ok=True)
    legacy(s).save(os.path.join(d, 'ic_launcher.png'))
    foreground(int(s * 108 / 48)).save(os.path.join(d, 'ic_launcher_foreground.png'))
os.makedirs(os.path.join(RES, 'mipmap-anydpi-v26'), exist_ok=True)
open(os.path.join(RES, 'mipmap-anydpi-v26', 'ic_launcher.xml'), 'w').write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/pipaco_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
''')
legacy(512).save('/workspace/pipaco_prueba_icon_512.png')
# vista previa: clásico + adaptativo (círculo)
fg = foreground(432); bg = Image.new('RGBA', (432, 432), '#09090B'); bg.alpha_composite(fg)
m = Image.new('L', (432, 432), 0); ImageDraw.Draw(m).ellipse([0, 0, 431, 431], fill=255)
pv = Image.new('RGBA', (900, 460), '#3f3f46'); pv.alpha_composite(legacy(192).resize((400, 400)), (20, 30)); circ = Image.new('RGBA', (432, 432)); circ.paste(bg, (0, 0), m); pv.alpha_composite(circ, (450, 14))
pv.convert('RGB').save('/workspace/pipaco_prueba_icon_preview.png'); print('ok')
