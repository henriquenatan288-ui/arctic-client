"""Gera as texturas animadas de chama da katana (suaves, translucidas, resolucao alta): textures/item/katana_flame(.png/.mcmeta) e katana_flame_blue."""
import numpy as np, json, os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/witch/textures/item')
W, H, N = 48, 528, 20
rng = np.random.default_rng(7)
comps = []
for _ in range(9):
    comps.append((rng.uniform(2.0, 7.0) * 2 * np.pi, rng.uniform(0.6, 3.0) * 2 * np.pi, int(rng.integers(1, 3)), rng.uniform(0, 6.28), rng.uniform(0.5, 1.0)))
xs = (np.arange(W) + 0.5) / W
ys = (np.arange(H) + 0.5) / H
X, Y = np.meshgrid(xs, ys)

def ramp(v, stops):
    out = np.zeros(v.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(v, [s[0] for s in stops], [s[1][c] for s in stops])
    return out

def frame(t, stops):
    f = np.zeros_like(X)
    tot = 0
    for fx, fy, m, ph, a in comps:
        # sobe com o tempo (fase decresce em y) e repete exatamente em N quadros (m inteiro)
        f += a * np.sin(fx * X + fy * (1.0 - Y) * 3.0 + ph - 2 * np.pi * m * t / N * np.sign(1)) 
        tot += a
    f = 0.5 + 0.5 * f / tot * 1.6
    center = np.exp(-(((X - 0.5) / 0.36) ** 2))
    flick = 0.8 + 0.2 * np.sin(2 * np.pi * t / N + Y * 9)
    v = np.clip(f * center * flick * 1.25, 0, 1)
    a = np.clip((v - 0.12) / 0.45, 0, 1) ** 1.3
    col = ramp(v, stops)
    img = np.dstack([col, a * 255]).astype(np.uint8)
    return img

FIRE = [(0.0, (120, 10, 0)), (0.3, (235, 60, 5)), (0.55, (255, 150, 25)), (0.8, (255, 225, 120)), (1.0, (255, 255, 235))]
BLUE = [(0.0, (10, 20, 120)), (0.3, (25, 70, 220)), (0.55, (40, 160, 255)), (0.8, (150, 230, 255)), (1.0, (240, 255, 255))]

os.makedirs(OUT, exist_ok=True)
for name, stops in (('katana_flame', FIRE), ('katana_flame_blue', BLUE)):
    strip = np.concatenate([frame(t, stops) for t in range(N)], axis=0)
    Image.fromarray(strip, 'RGBA').save(os.path.join(OUT, name + '.png'))
    json.dump({"animation": {"frametime": 2, "interpolate": True, "width": W, "height": H}}, open(os.path.join(OUT, name + '.png.mcmeta'), 'w'), indent=1)
print('ok')
