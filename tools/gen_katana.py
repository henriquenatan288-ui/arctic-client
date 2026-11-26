"""Regenera os modelos katana_*_sword.json: lamina reta-curva suave em segmentos finos, cores chapadas (1 texel), sem quadriculado."""
import json, glob, os
D = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/witch/models/item')
ROT = {"angle": -45, "axis": "z", "origin": [8, 8, 8]}
UV = [8, 8, 9, 9]  # um unico texel: cor lisa, sem padrao da textura de bloco

def box(x0, y0, z0, x1, y1, z1, tex):
    f = {k: {"uv": UV, "texture": "#" + tex} for k in ("north", "south", "east", "west", "up", "down")}
    return {"from": [round(x0, 3), round(y0, 3), round(z0, 3)], "to": [round(x1, 3), round(y1, 3), round(z1, 3)], "faces": f, "rotation": ROT}

def elements():
    E = []
    E.append(box(7.4, 0.0, 7.4, 8.6, 0.6, 8.6, 'g'))          # kashira
    E.append(box(7.55, 0.6, 7.55, 8.45, 6.0, 8.45, 'h'))        # cabo preto
    for y in (1.2, 2.7, 4.2):                                    # envolturas vermelhas
        E.append(box(7.48, y, 7.48, 8.52, y + 0.45, 8.52, 'w'))
    E.append(box(6.4, 6.0, 7.0, 9.6, 6.55, 9.0, 'g'))           # tsuba (guarda)
    E.append(box(7.0, 6.0, 6.4, 9.0, 6.55, 9.6, 'g'))
    E.append(box(7.35, 6.55, 7.7, 8.65, 7.6, 8.3, 'g'))         # habaki
    y = 7.6; seg = 1.5; n = 12; L = n * seg
    for i in range(n):
        t0, t1 = i / n, (i + 1) / n
        cx0, cx1 = 8 + 0.9 * t0 * t0, 8 + 0.9 * t1 * t1
        cx = (cx0 + cx1) / 2
        w = 1.15
        E.append(box(cx - w / 2, y, 7.75, cx + w / 2, y + seg + 0.02, 8.25, 'b'))
        E.append(box(cx - w / 2 - 0.0, y, 7.74, cx - w / 2 + 0.3, y + seg + 0.02, 8.26, 'e'))  # fio (hamon) mais claro
        y += seg
    # ponta (kissaki): 3 degraus que afinam
    cxe = 8 + 0.9
    for k, (h, w) in enumerate(((0.7, 0.95), (0.6, 0.7), (0.5, 0.42))):
        E.append(box(cxe - w / 2 + 0.05 * k, y, 7.78, cxe + w / 2 + 0.05 * k, y + h, 8.22, 'b'))
        y += h
    return E

def fire_overlay():
    """Chamas em volta da lamina: caixas um pouco maiores com a textura animada de fogo (brilho proprio)."""
    E = []
    n = 12; seg = 1.5; y = 7.6
    for i in range(n):
        t0, t1 = i / n, (i + 1) / n
        cx = 8 + 0.9 * ((t0 + t1) / 2) ** 2
        v1 = 16 * (1 - i / n); v0 = 16 * (1 - (i + 1) / n)  # chama continua ao longo da lamina (sobe)
        faces = {k: {"uv": [0, round(v0, 3), 16, round(v1, 3)], "texture": "#f", "light_emission": 15}
                 for k in ("north", "south", "east", "west", "up")}
        E.append({"from": [round(cx - 1.35, 3), y, 7.5], "to": [round(cx + 1.35, 3), round(y + seg + 0.02, 3), 8.5], "faces": faces, "rotation": ROT})
        y += seg
    return E

for f in glob.glob(os.path.join(D, 'katana_*_sword.json')):
    if os.path.basename(f).startswith(('katana_fire_', 'katana_sfire_')):
        os.remove(f); continue
for f in sorted(glob.glob(os.path.join(D, 'katana_*_sword.json'))):
    d = json.load(open(f))
    d['elements'] = elements()
    json.dump(d, open(f, 'w'), indent='\t')
    base = os.path.basename(f)[len('katana_'):-5]  # ex: iron_sword
    for tag, tex in (('fire', 'witch:block/katana_flame'), ('sfire', 'witch:block/katana_flame_blue')):
        v = json.loads(json.dumps(d))
        v['textures']['f'] = tex
        v['elements'] = d['elements'] + fire_overlay()
        json.dump(v, open(os.path.join(D, 'katana_%s_%s.json' % (tag, base)), 'w'), indent='\t')
        it = os.path.join(D, '..', '..', 'items', 'katana_%s_%s.json' % (tag, base))
        json.dump({"model": {"type": "minecraft:model", "model": "witch:item/katana_%s_%s" % (tag, base)}}, open(it, 'w'), indent=1)
print('ok')
