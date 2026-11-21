#!/usr/bin/env python3
"""Gera os efeitos sonoros do Witch Client (sintese propria, nada copiado) em assets/witch/sounds/*.ogg.
Uso: python3 tools/gen_sounds.py   (precisa de numpy, scipy e ffmpeg com libvorbis)"""
import os, subprocess, tempfile
import numpy as np
from scipy.io import wavfile

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/witch/sounds')
os.makedirs(OUT, exist_ok=True)


def silence(sec):
    return np.zeros(int(SR * sec))


def t_of(sec):
    return np.arange(int(SR * sec)) / SR


def place(buf, sig, at):
    i = int(SR * at)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[:end - i]


def adsr(t, atk, tau):
    return np.minimum(1.0, t / atk) * np.exp(-t / tau)


def sine_note(f, dur, tau, harm=(1.0, 0.28, 0.1), atk=0.004):
    t = t_of(dur)
    s = sum(h * np.sin(2 * np.pi * f * (k + 1) * t) for k, h in enumerate(harm))
    return s * adsr(t, atk, tau)


def blip(f0, f1, dur, wave='tri'):
    t = t_of(dur)
    f = np.linspace(f0, f1, len(t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    if wave == 'tri':
        s = 2 / np.pi * np.arcsin(np.sin(ph))
    else:
        s = np.tanh(3 * np.sin(ph))
    e = np.minimum(1.0, t / 0.003) * np.exp(-t / (dur * 0.45))
    return s * e


def fm_bell(f, dur, tau, ratio=3.5, index=3.0):
    t = t_of(dur)
    mod = index * np.exp(-t / (tau * 0.7)) * np.sin(2 * np.pi * f * ratio * t)
    return np.sin(2 * np.pi * f * t + mod) * adsr(t, 0.002, tau)


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def reverb(x, wet=0.25):
    y = x.copy()
    for d, g in ((0.029, 0.5), (0.043, 0.38), (0.061, 0.28), (0.083, 0.2)):
        n = int(SR * d)
        y[n:] += wet * g * x[:-n]
    return y


def finish(x, peak=0.72, fade=0.03):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    n = int(SR * fade)
    x[-n:] *= np.linspace(1, 0, n)
    return x


def save(name, x):
    wav = os.path.join(tempfile.gettempdir(), name + '.wav')
    wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5',
                    '-ac', '1', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(wav)
    print(name, round(len(x) / SR, 2), 's')


E5, B5, G5, D6, G6, C6, E6, C7 = 659.25, 987.77, 783.99, 1174.66, 1567.98, 1046.5, 1318.51, 2093.0
A5, E6b = 880.0, 1318.51

# ---------- toggle: estilo 1 "suave" (sinos macios) ----------
def toggle1(on):
    buf = silence(0.55)
    seq = [(E5, 0.0), (B5, 0.075)] if on else [(B5, 0.0), (E5, 0.075)]
    for f, at in seq:
        place(buf, sine_note(f, 0.45, 0.10), at)
    return finish(reverb(buf, 0.3) * (1.0 if on else 0.8))

# ---------- estilo 2 "digital" (blips curtos) ----------
def toggle2(on):
    buf = silence(0.3)
    if on:
        place(buf, blip(880, 1000, 0.07), 0.0)
        place(buf, blip(1318, 1500, 0.1), 0.075)
    else:
        place(buf, blip(1318, 1200, 0.07), 0.0)
        place(buf, blip(660, 520, 0.12), 0.075)
    return finish(lowpass(buf, 7000), 0.6)

# ---------- estilo 3 "cristal" (FM, brilho) ----------
def toggle3(on):
    buf = silence(0.7)
    seq = [(G5, 0.0), (D6, 0.04), (G6, 0.08)] if on else [(G6, 0.0), (D6, 0.045)]
    for f, at in seq:
        place(buf, fm_bell(f, 0.6, 0.14), at)
    return finish(reverb(buf, 0.35) * (1.0 if on else 0.75))

for on, nm in ((True, 'on'), (False, 'off')):
    save(f'toggle_{nm}_1', toggle1(on))
    save(f'toggle_{nm}_2', toggle2(on))
    save(f'toggle_{nm}_3', toggle3(on))

# ---------- kill 1 "witch": arpejo brilhante ----------
buf = silence(0.9)
for f, at in ((C6, 0.0), (E6, 0.06), (G6, 0.12), (C7, 0.18)):
    place(buf, fm_bell(f, 0.7, 0.16, ratio=2.0, index=2.2), at)
save('kill_1', finish(reverb(buf, 0.4)))

# ---------- kill 2 "impacto": queda grave + estalo + sino ----------
t = t_of(0.5)
f = 220 * np.exp(-t * 6) + 45
thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.12)
rng = np.random.default_rng(7)
noise = lowpass(rng.standard_normal(len(t)), 5000) * np.exp(-t / 0.03) * 0.5
buf = silence(0.9)
place(buf, thump + noise, 0.0)
place(buf, fm_bell(A5, 0.7, 0.2) * 0.6, 0.05)
save('kill_2', finish(reverb(buf, 0.3)))

# ---------- alerta (Sus Chunk / Solo): ping-ping ----------
buf = silence(0.8)
place(buf, fm_bell(A5, 0.5, 0.12, ratio=2.76, index=1.6), 0.0)
place(buf, fm_bell(E6b, 0.6, 0.15, ratio=2.76, index=1.6), 0.13)
save('alert', finish(reverb(buf, 0.35)))

# ---------- alarme da Elytra ESP: sirene subindo (2x) + acorde de 3 notas. Unico, nao parecido com os outros ----------
def siren(f0, f1, dur):
    t = t_of(dur)
    f = np.linspace(f0, f1, len(t))
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.15 * np.sin(3 * ph)
    return s * np.minimum(1.0, t / 0.01) * np.minimum(1.0, (dur - t) / 0.04)

buf = silence(1.7)
place(buf, siren(520, 1180, 0.32), 0.0)
place(buf, siren(520, 1180, 0.32), 0.36)
for f, at in ((E6, 0.78), (G6, 0.86), (C7, 0.94)):
    place(buf, fm_bell(f, 0.9, 0.3, ratio=2.0, index=2.4), at)
save('elytra_alarm', finish(reverb(buf, 0.3)))
