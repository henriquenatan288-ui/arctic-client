#version 330

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

#ifndef STYLE
#define STYLE 1
#endif

#if STYLE == 1 // ciano
#define G_BASE0 vec3(0.012,0.075,0.085)
#define G_BASE1 vec3(0.03,0.12,0.12)
#define G_N1 vec3(0.084,0.36,0.36)
#define G_N2 vec3(0.22,0.495,0.352)
#define G_N3 vec3(0.06,0.24,0.45)
#define G_FLOOR vec3(0.04,0.12,0.13)
#define G_RIM vec3(0.45,1.0,0.85)
#define G_STAR vec3(0.80,1.0,0.95)
#define G_STARGAIN 1.0
#elif STYLE == 2 // roxo
#define G_BASE0 vec3(0.05,0.012,0.09)
#define G_BASE1 vec3(0.10,0.03,0.16)
#define G_N1 vec3(0.37,0.11,0.60)
#define G_N2 vec3(0.65,0.22,0.70)
#define G_N3 vec3(0.12,0.18,0.51)
#define G_FLOOR vec3(0.10,0.04,0.16)
#define G_RIM vec3(0.95,0.60,1.0)
#define G_STAR vec3(1.0,0.88,1.0)
#define G_STARGAIN 1.0
#elif STYLE == 3 // fogo
#define G_BASE0 vec3(0.09,0.02,0.01)
#define G_BASE1 vec3(0.16,0.05,0.02)
#define G_N1 vec3(0.65,0.14,0.04)
#define G_N2 vec3(0.90,0.38,0.08)
#define G_N3 vec3(0.60,0.42,0.09)
#define G_FLOOR vec3(0.16,0.05,0.02)
#define G_RIM vec3(1.0,0.75,0.35)
#define G_STAR vec3(1.0,0.93,0.80)
#define G_STARGAIN 1.0
#elif STYLE == 4 // vazio
#define G_BASE0 vec3(0.0,0.0,0.01)
#define G_BASE1 vec3(0.015,0.015,0.03)
#define G_N1 vec3(0.03,0.036,0.07)
#define G_N2 vec3(0.05,0.06,0.125)
#define G_N3 vec3(0.06,0.105,0.18)
#define G_FLOOR vec3(0.012,0.012,0.025)
#define G_RIM vec3(0.60,0.85,1.0)
#define G_STAR vec3(0.85,0.95,1.0)
#define G_STARGAIN 1.7
#elif STYLE == 5 // aurora
#define G_BASE0 vec3(0.01,0.06,0.04)
#define G_BASE1 vec3(0.02,0.10,0.09)
#define G_N1 vec3(0.10,0.55,0.30)
#define G_N2 vec3(0.55,0.30,0.75)
#define G_N3 vec3(0.09,0.45,0.33)
#define G_FLOOR vec3(0.03,0.10,0.08)
#define G_RIM vec3(0.70,1.0,0.80)
#define G_STAR vec3(0.85,1.0,0.90)
#define G_STARGAIN 1.0
#elif STYLE == 6 // rosa
#define G_BASE0 vec3(0.09,0.02,0.05)
#define G_BASE1 vec3(0.15,0.04,0.09)
#define G_N1 vec3(0.60,0.18,0.35)
#define G_N2 vec3(0.95,0.50,0.65)
#define G_N3 vec3(0.48,0.38,0.43)
#define G_FLOOR vec3(0.14,0.05,0.09)
#define G_RIM vec3(1.0,0.80,0.90)
#define G_STAR vec3(1.0,0.92,0.95)
#define G_STARGAIN 1.0
#elif STYLE == 7 // arco-iris
#define G_BASE0 vec3(0.012,0.075,0.085)
#define G_BASE1 vec3(0.03,0.12,0.12)
#define G_N1 vec3(0.084,0.36,0.36)
#define G_N2 vec3(0.22,0.495,0.352)
#define G_N3 vec3(0.06,0.24,0.45)
#define G_FLOOR vec3(0.04,0.12,0.13)
#define G_RIM vec3(0.55,1.0,0.90)
#define G_STAR vec3(0.90,1.0,0.95)
#define G_STARGAIN 1.0
#endif

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
