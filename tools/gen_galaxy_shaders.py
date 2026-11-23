#!/usr/bin/env python3
"""Gera os shaders de galaxia (jogador e item) com as paletas de estilo.
Uso: python3 tools/gen_galaxy_shaders.py   (reescreve src/main/resources/assets/witch/shaders/core/witch_galaxy*.fsh)
STYLE chega por define (withShaderDefine("STYLE", n)); RELIEF (0..3) so no shader de jogador; ANYITEM so no de item."""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/witch/shaders/core")

# (base0, base1, n1, n2, n3, floor, rim, startint, stargain)
PAL = {
    1: ("0.012,0.075,0.085", "0.03,0.12,0.12", "0.084,0.36,0.36", "0.22,0.495,0.352", "0.06,0.24,0.45", "0.04,0.12,0.13", "0.45,1.0,0.85", "0.80,1.0,0.95", "1.0"),   # ciano (padrao)
    2: ("0.05,0.012,0.09", "0.10,0.03,0.16", "0.37,0.11,0.60", "0.65,0.22,0.70", "0.12,0.18,0.51", "0.10,0.04,0.16", "0.95,0.60,1.0", "1.0,0.88,1.0", "1.0"),             # roxo
    3: ("0.09,0.02,0.01", "0.16,0.05,0.02", "0.65,0.14,0.04", "0.90,0.38,0.08", "0.60,0.42,0.09", "0.16,0.05,0.02", "1.0,0.75,0.35", "1.0,0.93,0.80", "1.0"),            # fogo
    4: ("0.0,0.0,0.01", "0.015,0.015,0.03", "0.03,0.036,0.07", "0.05,0.06,0.125", "0.06,0.105,0.18", "0.012,0.012,0.025", "0.60,0.85,1.0", "0.85,0.95,1.0", "1.7"),     # vazio
    5: ("0.01,0.06,0.04", "0.02,0.10,0.09", "0.10,0.55,0.30", "0.55,0.30,0.75", "0.09,0.45,0.33", "0.03,0.10,0.08", "0.70,1.0,0.80", "0.85,1.0,0.90", "1.0"),             # aurora
    6: ("0.09,0.02,0.05", "0.15,0.04,0.09", "0.60,0.18,0.35", "0.95,0.50,0.65", "0.48,0.38,0.43", "0.14,0.05,0.09", "1.0,0.80,0.90", "1.0,0.92,0.95", "1.0"),           # rosa
    8: ("0.004,0.012,0.04", "0.01,0.03,0.09", "0.02,0.06,0.16", "0.04,0.10,0.26", "0.05,0.12,0.30", "0.01,0.03,0.08", "0.65,0.88,1.0", "0.85,0.95,1.0", "1.0"),     # plasma (mesmo plasma do ceu do Sky)
    7: ("0.012,0.075,0.085", "0.03,0.12,0.12", "0.084,0.36,0.36", "0.22,0.495,0.352", "0.06,0.24,0.45", "0.04,0.12,0.13", "0.55,1.0,0.90", "0.90,1.0,0.95", "1.0"),   # arco-iris (ciano girando o matiz)
}
NAMES = {1: "ciano", 2: "roxo", 3: "fogo", 4: "vazio", 5: "aurora", 6: "rosa", 7: "arco-iris", 8: "plasma"}


def palette():
    s = ["#ifndef STYLE", "#define STYLE 1", "#endif", ""]
    for i, p in PAL.items():
        s.append(("#if" if i == 1 else "#elif") + " STYLE == %d // %s" % (i, NAMES[i]))
        for n, v in zip(("G_BASE0", "G_BASE1", "G_N1", "G_N2", "G_N3", "G_FLOOR", "G_RIM", "G_STAR"), p[:8]):
            s.append("#define %s vec3(%s)" % (n, v))
        s.append("#define G_STARGAIN %s" % p[8])
    s.append("#endif")
    return "\n".join(s)


COMMON = r'''
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return v;
}

float stars(vec2 p, float density, float rad) {
    vec2 g = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = hash(g);
    if (h < density) return 0.0;
    vec2 o = vec2(hash(g + 3.1), hash(g + 7.7)) - 0.5;
    float d = length(f - o * 0.6);
    float b = (h - density) / (1.0 - density);
    return smoothstep(rad, 0.0, d) * (0.4 + 0.6 * b);
}

// gira o matiz em volta do eixo cinza (estilo arco-iris)
vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

// fundo de nebulosa + camadas de estrelas, na paleta do STYLE. q = coordenada, sc = escala das estrelas, rad = raio da estrela
vec3 galaxy(vec2 q, float t, float sc, float sbase, float rad, out float n1o) {
#if STYLE == 8
    // plasma: mesmo padrao do ceu do Sky (filamentos eletricos azuis); periodico em GameTime = sem pulo na virada do dia
    float tp = t * 6.2831853 / 9600.0;
    vec2 w1 = vec2(cos(tp * 30.0), sin(tp * 30.0)) * 0.55;
    vec2 w2 = vec2(sin(tp * 20.0), cos(tp * 20.0)) * 0.55;
    float pa = fbm(q * 0.9 + w1);
    float pb = fbm(q * 1.1 + pa * 1.7 + w2 + 2.0);
    float pc = fbm(q * 1.7 - pb * 1.3 + w1.yx + 5.0);
    float pl1 = 0.034 / (abs(pb - 0.5) + 0.022);
    float pl2 = 0.024 / (abs(pc - 0.5) + 0.018);
    float pg = clamp(pl1 * 0.85 + pl2 * 0.55, 0.0, 1.6);
    vec3 ptint = vec3(0.30, 0.62, 1.0);
    vec3 pbase = mix(ptint * 0.05, ptint * 0.20, pa) + vec3(0.004, 0.012, 0.024);
    vec3 pcore = mix(ptint, vec3(1.0), clamp(pg - 0.8, 0.0, 1.0));
    n1o = pa;
    return pbase + pcore * pg * 0.95 + ptint * pb * 0.10;
#endif
    vec3 col = mix(G_BASE0, G_BASE1, fbm(q * 1.3 + vec2(t * 0.010, -t * 0.006)));
    float n1 = fbm(q * 2.2 + vec2(3.0, 1.0) + vec2(t * 0.020, t * 0.012));
    col += G_N1 * smoothstep(0.40, 0.85, n1);
    float n2 = fbm(q * 3.6 + vec2(11.0, 5.0) + n1 + vec2(-t * 0.030, t * 0.018));
    col += G_N2 * smoothstep(0.45, 0.9, n2);
    float n3 = fbm(q * 6.0 + vec2(2.0, 8.0) + vec2(t * 0.045, -t * 0.03));
    col += G_N3 * smoothstep(0.62, 0.95, n3);

    vec3 sc3 = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float ang = fi * 2.399 + 0.5;
        mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
        vec2 p = rot * q * (sbase + fi * sc) + vec2(t * (0.04 + 0.05 * fi), t * 0.02 * fi) + fi * 17.3;
        float s = stars(p, 0.93 + 0.010 * fi, rad) * (1.0 - 0.13 * fi);
        vec3 tint = 0.5 + 0.5 * cos(6.2831 * (hash(vec2(fi, 3.7)) + vec3(0.0, 0.33, 0.67)));
        tint = mix(tint, G_STAR, 0.6);
        sc3 += tint * s * G_STARGAIN;
    }
    col += sc3;
#if STYLE == 7
    col = hueShift(col, t * 0.03 + n1 * 1.6);
#endif
    n1o = n1;
    return max(col, G_FLOOR);
}
'''

HEAD = '''#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

// GERADO por tools/gen_galaxy_shaders.py - nao editar a mao.
'''

ENTITY = r'''
#ifndef RELIEF
#define RELIEF 1
#endif
#if RELIEF == 1
#define RK 0.18
#elif RELIEF == 2
#define RK 0.30
#else
#define RK 0.45
#endif

// luminancia do texel vizinho (so se for opaco; senao usa o do centro)
float lumAt(vec2 uv, float fallback) {
    vec4 c = texture(Sampler0, uv);
    return c.a < 0.1 ? fallback : dot(c.rgb, vec3(0.30, 0.59, 0.11));
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    // Espaco de tela: o corpo do jogador e uma "janela" para o espaco. Todos os jogadores
    // (e a sua mao) olham para o mesmo ceu de galaxia, que se move conforme a posicao na tela.
    vec2 q = (gl_FragCoord.xy - 0.5 * ScreenSize) / ScreenSize.y * 4.0;
    float t = GameTime * 1200.0 * 8.0;
    float n1;
    vec3 col = galaxy(q, t, 6.0, 9.0, 0.20, n1);

#if RELIEF > 0
    // relevo da skin: so leve e suavizado pelos vizinhos, senao cada pixel da skin vira um "recorte"
    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    float l0 = dot(tex.rgb, vec3(0.30, 0.59, 0.11));
    float lum = (4.0 * l0 + lumAt(texCoord0 + vec2(px.x, 0.0), l0) + lumAt(texCoord0 - vec2(px.x, 0.0), l0)
            + lumAt(texCoord0 + vec2(0.0, px.y), l0) + lumAt(texCoord0 - vec2(0.0, px.y), l0)) / 8.0;
    col *= mix(1.0 - RK, 1.0 + RK, smoothstep(0.05, 0.95, lum));
    // sombreamento por face bem mais fraco que antes: a diferenca entre faces era o que parecia "peca colada"
    col *= mix(1.0 - 0.5 * RK, 1.0 + 0.2 * RK, clamp(vertexColor.r, 0.0, 1.0));
#endif
    // luz do mundo so de leve: a galaxia brilha por conta propria
    col *= mix(vec3(1.0), lightMapColor.rgb, 0.25);

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
'''

ITEM = r'''
void main() {
    float fw = max(fwidth(texCoord0.x), fwidth(texCoord0.y)); // antes do discard (derivadas precisam de fluxo uniforme)
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    // Espaco de tela, centrado: o item e uma "janela" para o espaco. Escala pelo tamanho do texel na tela,
    // entao itens em posicoes diferentes (e o balanco da mao) deslocam as camadas de forma diferente = paralaxe.
    vec2 px0 = 1.0 / vec2(textureSize(Sampler0, 0));
#ifdef GUIITEM
    // hotbar/inventario: o item e desenhado fora da tela (atlas proprio), entao gl_FragCoord nao serve; usa o texel do item
    vec2 q = (texCoord0 / px0) / 14.0;
#else
    float ppt = fw > 1e-6 ? px0.x / fw : 12.0; // pixels de tela por texel
    ppt = clamp(ppt, 3.0, 160.0);
    vec2 q = (gl_FragCoord.xy - 0.5 * ScreenSize) / (ppt * 14.0);
#endif
    float t = GameTime * 1200.0 * 8.0;
    float n1;
    vec3 col = galaxy(q, t, 24.0, 34.0, 0.16, n1);

    float shade = clamp(vertexColor.r, 0.0, 1.0); // sombreamento por face: separa frente e laterais
#ifdef ANYITEM
    // item inteiro: toda a arte vira galaxia; o relevo e o matiz da propria arte seguram o reconhecimento (totem, maca, elytra...)
    float m = 1.0;
    float lum = dot(tex.rgb, vec3(0.30, 0.59, 0.11));
    col *= mix(0.55, 1.45, smoothstep(0.08, 0.95, lum));
    col *= mix(vec3(1.0), 0.60 + 0.80 * tex.rgb, 0.55);
    col *= mix(0.70, 1.10, shade);
#else
    // mascara pela propria arte: pixels vermelhos (cabeca/lamina) viram galaxia; cabo e contorno ficam como a textura
    float redness = tex.r - max(tex.g, tex.b);
    float m = smoothstep(0.05, 0.12, redness);
    float relief = clamp(tex.r * 1.6, 0.0, 1.0);
    col *= mix(0.55, 1.35, relief);
    col *= mix(0.55, 1.15, shade);
    vec3 orig = tex.rgb * (0.55 + 0.9 * shade);
    col = mix(orig, col, m);
#endif

    // borda com luz: so onde a galaxia encosta em vazio
    vec2 px = px0;
    vec2 f = fract(texCoord0 / px);
    float e1 = 0.0;
    float nr = dot(vec2(1, 0), f - 0.5) + 0.5;
    float nl = dot(vec2(-1, 0), f - 0.5) + 0.5;
    float nu = dot(vec2(0, 1), f - 0.5) + 0.5;
    float nd = dot(vec2(0, -1), f - 0.5) + 0.5;
    if (texture(Sampler0, texCoord0 + vec2(px.x, 0.0)).a < 0.1) { e1 = max(e1, smoothstep(0.62, 0.9, nr)); }
    if (texture(Sampler0, texCoord0 - vec2(px.x, 0.0)).a < 0.1) { e1 = max(e1, smoothstep(0.62, 0.9, nl)); }
    if (texture(Sampler0, texCoord0 + vec2(0.0, px.y)).a < 0.1) { e1 = max(e1, smoothstep(0.62, 0.9, nu)); }
    if (texture(Sampler0, texCoord0 - vec2(0.0, px.y)).a < 0.1) { e1 = max(e1, smoothstep(0.62, 0.9, nd)); }
    float pulse = 0.85 + 0.15 * sin(t * 2.0);
    col = mix(col, G_RIM * 1.25 * pulse, e1 * 0.85 * m);

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
'''


def main():
    pal = palette()
    for name, body in (("witch_galaxy_entity.fsh", ENTITY), ("witch_galaxy.fsh", ITEM)):
        with open(os.path.join(OUT, name), "w") as f:
            f.write(HEAD + "\n" + pal + "\n" + COMMON + body)
        print("ok", name)


if __name__ == "__main__":
    main()
