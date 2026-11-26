#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

// Fogo procedural da katana: a textura "katana_flame" guarda so coordenadas locais (R = largura, G = posicao na lamina);
// aqui viram chamas de ruido (fbm) subindo pela lamina, emissivas (ignoram a luz do mundo) e translucidas.
uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

#ifndef BLUE
#define BLUE 0
#endif

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i), b = hash(i + vec2(1, 0)), c = hash(i + vec2(0, 1)), d = hash(i + vec2(1, 1));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec2(17.1, 9.7);
        a *= 0.5;
    }
    return s;
}

vec3 ramp(float v) {
#if BLUE
    vec3 c0 = vec3(0.05, 0.12, 0.65), c1 = vec3(0.10, 0.45, 1.00), c2 = vec3(0.45, 0.85, 1.00), c3 = vec3(0.92, 1.00, 1.00);
#else
    vec3 c0 = vec3(0.55, 0.05, 0.00), c1 = vec3(1.00, 0.28, 0.02), c2 = vec3(1.00, 0.65, 0.10), c3 = vec3(1.00, 0.97, 0.75);
#endif
    if (v < 0.35) return mix(c0, c1, v / 0.35);
    if (v < 0.7) return mix(c1, c2, (v - 0.35) / 0.35);
    return mix(c2, c3, clamp((v - 0.7) / 0.3, 0.0, 1.0));
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    vec2 uv = tex.rg;              // u: 0..1 pela largura, v: 1 na base da lamina -> 0 na ponta
    float s = 1.0 - uv.y;          // 0 base, 1 ponta
    float t = GameTime * 1200.0;
    float side = abs(uv.x - 0.5) * 2.0; // 0 centro, 1 borda

    // ruido subindo + leve ondulacao lateral (as linguas de fogo balancam)
    vec2 q = vec2(uv.x * 2.2 + 0.35 * sin(s * 7.0 - t * 2.1), s * 5.5 - t * 1.9);
    float n = fbm(q) * 0.75 + fbm(q * 2.1 + vec2(3.7, -t * 1.2)) * 0.45;
    float body = 1.0 - smoothstep(0.15, 1.0, side + (n - 0.55) * 0.9); // forma da chama: mais larga no meio, rasgada nas bordas
    float fall = 1.0 - smoothstep(0.55, 1.0, s) * 0.6;                  // afina para a ponta
    float inten = clamp(body * fall * (0.55 + n * 0.9), 0.0, 1.0);
    float a = smoothstep(0.08, 0.5, inten);
    if (a < 0.02) discard;

    vec3 col = ramp(inten) * 1.35;
    vec4 color = vec4(col, a * 0.92);
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    // brilha no escuro: so um pouco do lightmap
    color.rgb *= mix(lightMapColor.rgb, vec3(1.0), 0.88);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
