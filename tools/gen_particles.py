#!/usr/bin/env python3
"""Sprites BRANCOS das particulas 2D (tingidos pela cor do modulo): brilho, coracao, bolha, floco. Desenho proprio.
Uso: python3 tools/gen_particles.py"""
import math, os
from PIL import Image, ImageDraw, ImageFilter
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/witch/textures/particle')
S = 4; N = 128 * S
def P(v): return int(round(v * S))
def canvas():
    im = Image.new("RGBA", (N, N), (255, 255, 255, 0)); return im, ImageDraw.Draw(im)
def finish(im, name, glow=0.5, blur=3.0):
    a = im.split()[3]
    g = a.filter(ImageFilter.GaussianBlur(P(blur))).point(lambda v: int(v * glow))
    base = Image.new("RGBA", (N, N), (255, 255, 255, 0)); base.putalpha(g)
    base.alpha_composite(im)
    base.resize((128, 128), Image.LANCZOS).save(os.path.join(OUT, name))
    print(name)

# brilho de 4 pontas (curvas concavas) com miolo
im, d = canvas()
c = 64
pts = []
for k in range(400):
    t = 2 * math.pi * k / 400
    x = math.copysign(abs(math.cos(t)) ** 3, math.cos(t)); y = math.copysign(abs(math.sin(t)) ** 3, math.sin(t))
    pts.append((P(c + 54 * x), P(c + 54 * y)))
d.polygon(pts, fill=(255, 255, 255, 255))
d.ellipse([P(c - 9), P(c - 9), P(c + 9), P(c + 9)], fill=(255, 255, 255, 255))
finish(im, 'shape_sparkle.png', 0.7, 4)

# coracao
im, d = canvas()
pts = []
for k in range(360):
    t = math.radians(k)
    x = 16 * math.sin(t) ** 3
    y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
    pts.append((P(64 + x * 3.3), P(60 - y * 3.3)))
d.polygon(pts, fill=(255, 255, 255, 255))
d.ellipse([P(34), P(34), P(50), P(46)], fill=(255, 255, 255, 255))
finish(im, 'shape_heart.png', 0.5, 3)

# bolha / ponto de luz (gradiente radial suave + anel)
im = Image.new("RGBA", (N, N), (255, 255, 255, 0))
px = im.load()
for y in range(N):
    for x in range(N):
        r = math.hypot(x - N / 2, y - N / 2) / (N / 2)
        if r >= 1: continue
        a = (1 - r) ** 2.2 * 0.85 + (math.exp(-((r - 0.62) / 0.06) ** 2) * 0.55)
        px[x, y] = (255, 255, 255, int(min(1, a) * 255))
im.resize((128, 128), Image.LANCZOS).save(os.path.join(OUT, 'shape_glow.png')); print('shape_glow.png')

# floco de 6 bracos
im, d = canvas()
for k in range(6):
    t = math.radians(60 * k)
    ux, uy = math.cos(t), math.sin(t); vx, vy = -uy, ux
    def pt(a, b): return (P(64 + ux * a + vx * b), P(64 + uy * a + vy * b))
    d.line([pt(0, 0), pt(54, 0)], fill=(255, 255, 255, 255), width=P(5))
    for a in (24, 38):
        d.line([pt(a, 0), pt(a + 13, 13)], fill=(255, 255, 255, 255), width=P(4))
        d.line([pt(a, 0), pt(a + 13, -13)], fill=(255, 255, 255, 255), width=P(4))
d.ellipse([P(56), P(56), P(72), P(72)], fill=(255, 255, 255, 255))
finish(im, 'shape_flake.png', 0.5, 3)

# po: ponto de luz macio, sem anel (usado no Kill Effect)
im = Image.new("RGBA", (N, N), (255, 255, 255, 0))
px = im.load()
for y in range(N):
    for x in range(N):
        r = math.hypot(x - N / 2, y - N / 2) / (N / 2)
        if r >= 1: continue
        a = (1 - r) ** 1.7
        if r < 0.22: a = 1.0
        px[x, y] = (255, 255, 255, int(min(1, a) * 255))
im.resize((128, 128), Image.LANCZOS).save(os.path.join(OUT, 'shape_dust.png')); print('shape_dust.png')
