#!/usr/bin/env python3
"""Gera as texturas animadas das ferramentas galaxia (sem shader).

Cada ferramenta vira uma tira vertical de N quadros 32x32 + .mcmeta de animacao.
Cabeca/lamina = galaxia (nebulosa + estrelas em 3 profundidades que andam em velocidades diferentes);
cabo = metal escuro; contorno fino brilhante pulsando. Ideia inspirada no Avaritia (mascara + camadas
cosmicas), tudo desenhado aqui, nada copiado.

uso: python3 tools/gen_galaxy_tools.py [--preview /tmp/prev.png]
"""
import json, math, os, sys
import numpy as np
from PIL import Image, ImageDraw

S = 32            # tamanho final
SS = 4            # supersample
H = S * SS        # 128 (periodo do toro)
N = 32            # quadros (um ciclo completo, tudo em loop perfeito)
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/minecraft/textures/item')

A = np.array([0.70710678, -0.70710678])   # eixo do cabo (para cima/direita)
P = np.array([0.70710678, 0.70710678])    # perpendicular (para baixo/direita)
Q = -P                                    # para cima/esquerda

YS, XS = np.mgrid[0:S, 0:S]

# ---------------------------------------------------------------- formas
def disk(m, cx, cy, r):
    m |= ((XS + 0.5 - cx) ** 2 + (YS + 0.5 - cy) ** 2) <= r * r

def seg(m, p0, p1, r0, r1=None):
    r1 = r0 if r1 is None else r1
    n = int(max(abs(p1[0] - p0[0]), abs(p1[1] - p0[1])) * 3) + 1
    for i in range(n + 1):
        t = i / n
        disk(m, p0[0] + (p1[0] - p0[0]) * t, p0[1] + (p1[1] - p0[1]) * t, r0 + (r1 - r0) * t)

def poly(m, pts):
    im = Image.new('L', (S * 8, S * 8), 0)
    ImageDraw.Draw(im).polygon([(x * 8, y * 8) for x, y in pts], fill=255)
    big = np.array(im).reshape(S, 8, S, 8).mean((1, 3))
    m |= big >= 128

def uv(o, u, v, ax=A, pp=P):
    return (o[0] + u * ax[0] + v * pp[0], o[1] + u * ax[1] + v * pp[1])

def blank():
    return np.zeros((S, S), bool)

def shape(tool):
    handle, head, guard = blank(), blank(), blank()
    if tool == 'sword':
        disk(handle, 3.4, 28.6, 1.9)
        seg(handle, (3.6, 28.4), (10.0, 22.0), 1.6)
        gc = (12.0, 20.0)
        poly(guard, [uv(gc, u, v) for u, v in [(-1.7, -6.8), (1.7, -6.8), (1.7, 6.8), (-1.7, 6.8)]])
        poly(head, [uv(gc, u, v) for u, v in [(1.7, -3.9), (19.5, -3.9), (26.0, 0.0), (19.5, 3.9), (1.7, 3.9)]])
    elif tool == 'pickaxe':
        seg(handle, (3.2, 28.8), (22.0, 10.0), 1.6)
        c = np.array([19.0, 13.0])
        n = 90
        for i in range(n + 1):
            s = -1 + 2 * i / n
            pt = c + s * 12.5 * P + (1 - s * s) * 7.5 * A
            disk(head, pt[0], pt[1], 1.15 + 2.35 * (1 - s * s))
    elif tool == 'axe':
        o = (15.0, 17.0)
        seg(handle, (3.2, 28.8), uv(o, 8.0, 0.0), 1.6)
        poly(head, [uv(o, u, v, A, Q) for u, v in [(-1.0, -3.0), (5.0, -3.0), (5.5, 2.0), (11.0, 6.0), (12.0, 11.0), (3.0, 12.5), (-6.0, 11.0), (-5.0, 6.0), (-1.0, 2.5)]])
    elif tool == 'shovel':
        seg(handle, (3.2, 28.8), (20.5, 11.5), 1.6)
        o = np.array([23.0, 9.0])
        for u in np.arange(-6.5, 6.6, 0.5):
            for v in np.arange(-5.5, 5.6, 0.5):
                if abs(u / 6.6) ** 3 + abs(v / 4.9) ** 3 <= 1.0:
                    px, py = uv(o, u, v)
                    disk(head, px, py, 0.55)
    elif tool == 'hoe':
        o = (22.0, 10.0)
        seg(handle, (3.2, 28.8), uv(o, 1.0, 0.0), 1.6)
        poly(head, [uv(o, u, v, A, Q) for u, v in [(-2.4, -2.0), (2.4, -2.0), (2.4, 12.0), (-2.4, 12.0)]])
        poly(head, [uv(o, u, v, A, Q) for u, v in [(2.4, 8.0), (-5.5, 8.0), (-5.5, 12.0), (2.4, 12.0)]])
    head &= True
    guard &= ~head
    handle &= ~head & ~guard
    return handle, head, guard

def dilate(m):
    d = m.copy()
    d[1:, :] |= m[:-1, :]; d[:-1, :] |= m[1:, :]
    d[:, 1:] |= m[:, :-1]; d[:, :-1] |= m[:, 1:]
    return d

def neighbors(m):
    """true onde o vizinho (cima, baixo, esq, dir) NAO e m"""
    pad = np.pad(m, 1, constant_values=False)
    return (~pad[:-2, 1:-1], ~pad[2:, 1:-1], ~pad[1:-1, :-2], ~pad[1:-1, 2:])

# ---------------------------------------------------------------- galaxia
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
            out += 0.55 * b * np.exp(-(dy * dy) / (2 * 0.6 ** 2)) * np.exp(-(np.abs(dx)) / 3.5)
            out += 0.55 * b * np.exp(-(dx * dx) / (2 * 0.6 ** 2)) * np.exp(-(np.abs(dy)) / 3.5)
    return out

def down(a):
    return a.reshape(S, SS, S, SS, *a.shape[2:]).mean((1, 3))

def galaxy_frames(kind):
    p = PAL[kind]
    seed = 100 if kind == 'diamond' else 200
    far = stars(seed + 1, 40, 1.2, 1.8, 0.45, 0.85, False)
    mid = stars(seed + 2, 18, 1.5, 2.1, 0.8, 1.2, False)
    near = stars(seed + 3, 8, 1.9, 2.7, 1.2, 1.8, True)
    # poeira (camada de nuvem que anda com o meio)
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
        c = c * (0.9 + 0.7 * dd[..., None]) * 1.75
        tw = lambda ph, m: 0.72 + 0.28 * math.sin(th * m + ph)
        s = (far * tw(0.0, 2)
             + np.roll(mid, (-4 * t, 4 * t), axis=(0, 1)) * tw(1.7, 1)
             + np.roll(near, (-8 * t, 8 * t), axis=(0, 1)) * tw(3.1, 1))
        c = c + col(p['star']) * s[..., None]
        c = np.maximum(c, np.array([0.05, 0.15, 0.16]) * p['dim'])   # piso: nada some no escuro
        frames.append(np.clip(down(c), 0, 1.4))
    return frames

# ---------------------------------------------------------------- montagem
OUTLINE = np.array([5, 7, 12])

def build(tool, kind):
    handle, head, guard = shape(tool)
    body = handle | head | guard
    outline = dilate(body) & ~body
    sil = body | outline
    p = PAL[kind]
    up, dn, lf, rt = neighbors(body)         # vizinho fora do corpo (cima, baixo, esq, dir)
    edge_head = head & (up | dn | lf | rt)   # anel da cabeca (brilha)
    frames = galaxy_frames(kind)
    # sombreamento fixo: luz vem de cima/esquerda
    shade = 1.14 - 0.34 * ((XS + YS) / 62.0)
    if tool == 'sword':
        gc = np.array([12.0, 20.0])
        vv = ((XS + 0.5 - gc[0]) * P[0] + (YS + 0.5 - gc[1]) * P[1])
        shade = shade * np.where(vv < -0.7, 1.20, np.where(vv > 0.7, 0.80, 1.0))
        shade = shade + 0.30 * (np.abs(vv) <= 0.7)       # espinha da lamina
    out = []
    for t, g in enumerate(frames):
        img = np.zeros((S, S, 4))
        img[..., :3] = OUTLINE / 255.0
        pulse0 = 0.78 + 0.22 * math.sin(2 * math.pi * t / N * 2)
        # cabo
        hb = np.array([30, 28, 40]) / 255.0
        hh = np.broadcast_to(hb, (S, S, 3)).copy()
        hh[up & handle | lf & handle] = np.array([66, 62, 84]) / 255.0
        hh[dn & handle | rt & handle] = np.array([15, 13, 21]) / 255.0
        # runas: pontinhos de luz fraca ao longo do cabo
        rune = handle & ~(up | dn | lf | rt) & (((XS + (31 - YS)) % 7) == 0)
        hh[rune] = np.array(p['glow']) * (0.30 + 0.14 * pulse0)
        img[..., :3] = np.where(handle[..., None], hh, img[..., :3])
        # guarda (metal) + gema
        gm = np.broadcast_to(np.array([64, 70, 88]) / 255.0, (S, S, 3)).copy()
        gm[(up | lf) & guard] = np.array([128, 138, 160]) / 255.0
        gm[(dn | rt) & guard] = np.array([30, 33, 44]) / 255.0
        if tool == 'sword':
            gx, gy = int(round(12.0 - 0.5)), int(round(20.0 - 0.5))
            gem = np.zeros((S, S), bool); gem[gy:gy + 2, gx:gx + 2] = True
            gm[gem & guard] = np.array(p['glow']) * (0.9 + 0.3 * pulse0)
        img[..., :3] = np.where(guard[..., None], gm, img[..., :3])
        # cabeca = galaxia
        gal = np.clip(g * shade[..., None], 0, 1.3)
        pulse = 0.78 + 0.22 * math.sin(2 * math.pi * t / N * 2)
        glow = np.array(p['glow']) * (0.95 + 0.30 * pulse)
        gal = np.where(edge_head[..., None], np.clip(gal * 0.35 + glow[None, None, :] * 0.85, 0, 1.2), gal)
        img[..., :3] = np.where(head[..., None], gal, img[..., :3])
        img[..., 3] = sil.astype(float)
        out.append(img)
    if tool == 'sword':
        # gema no centro da guarda (pulsa devagar)
        pass
    return out, sil

def save(tool, kind):
    frames, sil = build(tool, kind)
    strip = np.concatenate(frames, axis=0)
    rgb = np.clip(strip[..., :3] * 255 + 0.5, 0, 255).astype(np.uint8)
    a = (strip[..., 3] * 255).astype(np.uint8)
    im = Image.fromarray(np.dstack([rgb, a]), 'RGBA')
    name = f'{kind}_{tool}'
    path = os.path.join(OUT, name + '.png')
    im.save(path, optimize=True)
    with open(path + '.mcmeta', 'w') as f:
        json.dump({'animation': {'frametime': 3, 'interpolate': True}}, f)
    return frames, name

if __name__ == '__main__':
    prev = None
    if '--preview' in sys.argv:
        prev = sys.argv[sys.argv.index('--preview') + 1]
    tools = ['sword', 'pickaxe', 'axe', 'shovel', 'hoe']
    cells = {}
    for kind in ['diamond', 'netherite']:
        for tool in tools:
            frames, name = save(tool, kind)
            cells[name] = frames
    if prev:
        Z = 6
        W = Image.new('RGBA', (5 * (S * Z + 8) + 8, 2 * 3 * (S * Z + 8) // 3 + 8 + 2 * (S * Z + 8)), (60, 62, 74, 255))
        W = Image.new('RGBA', (5 * (S * Z + 8) + 8, 4 * (S * Z + 8) + 8), (60, 62, 74, 255))
        for ki, kind in enumerate(['diamond', 'netherite']):
            for ti, tool in enumerate(tools):
                for fi, t in enumerate([0, 16]):
                    f = cells[f'{kind}_{tool}'][t]
                    rgb = (np.clip(f[..., :3], 0, 1) * 255).astype(np.uint8)
                    cell = Image.fromarray(np.dstack([rgb, (f[..., 3] * 255).astype(np.uint8)]), 'RGBA').resize((S * Z, S * Z), Image.NEAREST)
                    W.alpha_composite(cell, (8 + ti * (S * Z + 8), 8 + (ki * 2 + fi) * (S * Z + 8)))
        W.save(prev)
