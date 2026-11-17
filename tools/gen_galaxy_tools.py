#!/usr/bin/env python3
"""Gera as texturas animadas "galaxy_*" a partir da arte do client (textures/item/<material>_<ferramenta>.png).

Pixels vermelhos da arte (cabeca/lamina) viram galaxia animada (nebulosa + estrelas em camadas que andam em
velocidades diferentes); cabo e contorno ficam como na arte. Borda fina com luz pulsando na cabeca.
Saida: assets/witch/textures/item/galaxy_<id>.png (tira vertical) + .mcmeta, e o modelo/definicao de item
(assets/witch/models/item e assets/witch/items) que o modulo Galaxy usa para trocar o modelo.
Tudo gerado aqui, nada copiado de outros mods. uso: python3 tools/gen_galaxy_tools.py [--preview arq.png]
"""
import json, math, os, sys
import numpy as np
from PIL import Image

S = 16            # tamanho do quadro (igual a arte)
SS = 8            # supersample
H = S * SS        # 128 (periodo do toro)
N = 32            # quadros (loop perfeito)
ROOT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets')
ART = os.path.join(ROOT, 'minecraft/textures/item')
TEX = os.path.join(ROOT, 'witch/textures/item')
MODELS = os.path.join(ROOT, 'witch/models/item')
ITEMS = os.path.join(ROOT, 'witch/items')

YS, XS = np.mgrid[0:S, 0:S]

def sm(t):
    return t * t * (3 - 2 * t)

def noise(G, seed, ox=0.0, oy=0.0):
    r = np.random.default_rng(seed).random((G, G))
    xs = (np.arange(H) + 0.5) / H * G
    ys = xs + oy
    xs = xs + ox
    x0 = np.floor(xs).astype(int); fx = sm(xs - x0)
    y0 = np.floor(ys).astype(int); fy = sm(ys - y0)
    x1 = (x0 + 1) % G; x0 %= G
    y1 = (y0 + 1) % G; y0 %= G
    X0, X1, Y0, Y1 = x0[None, :], x1[None, :], y0[:, None], y1[:, None]
    FX, FY = fx[None, :], fy[:, None]
    return (1 - FY) * ((1 - FX) * r[Y0, X0] + FX * r[Y0, X1]) + FY * ((1 - FX) * r[Y1, X0] + FX * r[Y1, X1])

def fbm(G, seed, ox, oy, octaves=4):
    v = 0.0; a = 0.5; tot = 0.0
    for o in range(octaves):
        v = v + a * noise(G * 2 ** o, seed + o * 11, ox * 2 ** o, oy * 2 ** o)
        tot += a; a *= 0.5
    return v / tot

def sstep(e0, e1, x):
    return sm(np.clip((x - e0) / (e1 - e0), 0, 1))

PAL = {
    'diamond':   dict(base0=(0.012, 0.075, 0.085), base1=(0.03, 0.12, 0.12), c1=(0.07, 0.30, 0.30), c2=(0.20, 0.45, 0.32), c3=(0.10, 0.40, 0.75),
                      star=(0.80, 1.0, 0.95), glow=(0.35, 1.0, 0.85), dim=1.0),
    'netherite': dict(base0=(0.010, 0.050, 0.050), base1=(0.02, 0.09, 0.07), c1=(0.05, 0.26, 0.20), c2=(0.22, 0.52, 0.22), c3=(0.30, 0.62, 0.20),
                      star=(0.85, 1.0, 0.85), glow=(0.55, 1.0, 0.45), dim=0.9),
}

def stars(seed, count, smin, smax, bmin, bmax, glint):
    r = np.random.default_rng(seed)
    out = np.zeros((H, H))
    yy, xx = np.mgrid[0:H, 0:H]
    for _ in range(count):
        cx, cy = r.random() * H, r.random() * H
        sg = smin + r.random() * (smax - smin)
        b = bmin + r.random() * (bmax - bmin)
        dx = (xx - cx + H / 2) % H - H / 2
        dy = (yy - cy + H / 2) % H - H / 2
        out += b * np.exp(-(dx * dx + dy * dy) / (2 * sg * sg))
        if glint:
            out += 0.55 * b * np.exp(-(dy * dy) / (2 * 1.2 ** 2)) * np.exp(-(np.abs(dx)) / 7.0)
            out += 0.55 * b * np.exp(-(dx * dx) / (2 * 1.2 ** 2)) * np.exp(-(np.abs(dy)) / 7.0)
    return out

def down(a):
    return a.reshape(S, SS, S, SS, *a.shape[2:]).mean((1, 3))

def galaxy_frames(kind):
    p = PAL[kind]
    seed = 100 if kind == 'diamond' else 200
    # estrelas ~2x maiores que no 32px, pois o quadro agora e 16px
    far = stars(seed + 1, 22, 2.4, 3.6, 0.5, 0.9, False)
    mid = stars(seed + 2, 10, 3.0, 4.2, 0.8, 1.25, False)
    near = stars(seed + 3, 5, 3.8, 5.4, 1.2, 1.8, True)
    dust = fbm(8, seed + 4, 0, 0, 3)
    frames = []
    col = lambda c: np.array(c)[None, None, :]
    for t in range(N):
        th = 2 * math.pi * t / N
        ox, oy = 1.1 * math.cos(th), 1.1 * math.sin(th)
        b = fbm(4, seed + 10, 0.6 * ox, 0.6 * oy, 3)
        n1 = fbm(6, seed + 20, ox + 3, oy + 1)
        n2 = fbm(8, seed + 30, -ox + 7, oy - 4)
        n3 = fbm(12, seed + 40, ox * 1.4 - 2, -oy * 1.4 + 5)
        dd = np.roll(dust, (-4 * t, 4 * t), axis=(0, 1))
        c = col(p['base0']) * (1 - b[..., None]) + col(p['base1']) * b[..., None]
        c = c + col(p['c1']) * (sstep(0.40, 0.85, n1) * 1.2)[..., None]
        c = c + col(p['c2']) * (sstep(0.45, 0.9, n2) * 1.1)[..., None]
        c = c + col(p['c3']) * (sstep(0.62, 0.95, n3) * 0.6)[..., None]
        c = c * (0.8 + 0.8 * dd[..., None]) * 1.05
        tw = lambda ph, m: 0.72 + 0.28 * math.sin(th * m + ph)
        s = (far * tw(0.0, 2)
             + np.roll(mid, (-4 * t, 4 * t), axis=(0, 1)) * tw(1.7, 1)
             + np.roll(near, (-8 * t, 8 * t), axis=(0, 1)) * tw(3.1, 1))
        c = c + col(p['star']) * s[..., None]
        c = np.maximum(c, np.array([0.02, 0.07, 0.08]) * p['dim'])
        frames.append(np.clip(down(c), 0, 1.4))
    return frames

def build(kind, tool):
    art = np.array(Image.open(os.path.join(ART, f'{kind}_{tool}.png')).convert('RGBA')).astype(float)
    rgb = art[..., :3] / 255.0
    opaque = art[..., 3] > 128
    red = (art[..., 0] - np.maximum(art[..., 1], art[..., 2])) >= 20
    head = opaque & red
    pad = np.pad(opaque, 1, constant_values=False)
    nb = [~pad[:-2, 1:-1], ~pad[2:, 1:-1], ~pad[1:-1, :-2], ~pad[1:-1, 2:]]
    rim = head & (nb[0] | nb[1] | nb[2] | nb[3])   # so onde a cabeca encosta em vazio
    p = PAL[kind]
    relief = np.clip(art[..., 0] / 255.0 * 1.6, 0, 1)          # vermelho claro/escuro da arte = relevo
    shade = (0.7 + 0.7 * relief)[..., None]
    out = []
    for t, g in enumerate(galaxy_frames(kind)):
        img = np.zeros((S, S, 4))
        img[..., :3] = rgb
        gal = np.clip(g * shade, 0, 1.3)
        pulse = 0.78 + 0.22 * math.sin(2 * math.pi * t / N * 2)
        glow = np.array(p['glow']) * (0.95 + 0.30 * pulse)
        gal = np.where(rim[..., None], np.clip(gal * 0.75 + glow[None, None, :] * 0.30, 0, 1.2), gal)
        img[..., :3] = np.where(head[..., None], gal, img[..., :3])
        img[..., 3] = art[..., 3] / 255.0
        out.append(img)
    return out

def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)

def save(kind, tool):
    frames = build(kind, tool)
    strip = np.concatenate(frames, axis=0)
    rgb = np.clip(strip[..., :3] * 255 + 0.5, 0, 255).astype(np.uint8)
    a = np.clip(strip[..., 3] * 255 + 0.5, 0, 255).astype(np.uint8)
    name = f'galaxy_{kind}_{tool}'
    os.makedirs(TEX, exist_ok=True)
    path = os.path.join(TEX, name + '.png')
    Image.fromarray(np.dstack([rgb, a]), 'RGBA').save(path, optimize=True)
    write_json(path + '.mcmeta', {'animation': {'frametime': 3, 'interpolate': True}})
    write_json(os.path.join(MODELS, name + '.json'), {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'witch:item/{name}'}})
    write_json(os.path.join(ITEMS, name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'witch:item/{name}'}})
    return frames

if __name__ == '__main__':
    prev = sys.argv[sys.argv.index('--preview') + 1] if '--preview' in sys.argv else None
    tools = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']
    cells = {}
    for kind in ['diamond', 'netherite']:
        for tool in tools:
            cells[(kind, tool)] = save(kind, tool)
    if prev:
        Z = 12
        W = Image.new('RGBA', (5 * (S * Z + 8) + 8, 4 * (S * Z + 8) + 8), (60, 62, 74, 255))
        for ki, kind in enumerate(['diamond', 'netherite']):
            for ti, tool in enumerate(tools):
                for fi, t in enumerate([0, 16]):
                    f = cells[(kind, tool)][t]
                    rgb = (np.clip(f[..., :3], 0, 1) * 255).astype(np.uint8)
                    cell = Image.fromarray(np.dstack([rgb, (f[..., 3] * 255).astype(np.uint8)]), 'RGBA').resize((S * Z, S * Z), Image.NEAREST)
                    W.alpha_composite(cell, (8 + ti * (S * Z + 8), 8 + (ki * 2 + fi) * (S * Z + 8)))
        W.save(prev)
