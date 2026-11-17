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

float stars(vec2 p, float density) {
    vec2 g = floor(p);
    vec2 f = fract(p) - 0.5;
    float h = hash(g);
    if (h < density) return 0.0;
    vec2 o = vec2(hash(g + 3.1), hash(g + 7.7)) - 0.5;
    float d = length(f - o * 0.6);
    float b = (h - density) / (1.0 - density);
    return smoothstep(0.20, 0.0, d) * (0.4 + 0.6 * b);
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

    vec3 col = mix(vec3(0.012, 0.075, 0.085), vec3(0.03, 0.12, 0.12), fbm(q * 1.3 + vec2(t * 0.010, -t * 0.006)));
    float n1 = fbm(q * 2.2 + vec2(3.0, 1.0) + vec2(t * 0.020, t * 0.012));
    col += vec3(0.07, 0.30, 0.30) * smoothstep(0.40, 0.85, n1) * 1.2;
    float n2 = fbm(q * 3.6 + vec2(11.0, 5.0) + n1 + vec2(-t * 0.030, t * 0.018));
    col += vec3(0.20, 0.45, 0.32) * smoothstep(0.45, 0.9, n2) * 1.1;
    float n3 = fbm(q * 6.0 + vec2(2.0, 8.0) + vec2(t * 0.045, -t * 0.03));
    col += vec3(0.10, 0.40, 0.75) * smoothstep(0.62, 0.95, n3) * 0.6;

    vec3 sc3 = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float ang = fi * 2.399 + 0.5;
        mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
        vec2 p = rot * q * (9.0 + fi * 6.0) + vec2(t * (0.04 + 0.05 * fi), t * 0.02 * fi) + fi * 17.3;
        float s = stars(p, 0.93 + 0.010 * fi) * (1.0 - 0.13 * fi);
        vec3 tint = 0.5 + 0.5 * cos(6.2831 * (hash(vec2(fi, 3.7)) + vec3(0.0, 0.33, 0.67)));
        tint = mix(tint, vec3(0.80, 1.0, 0.95), 0.6);
        sc3 += tint * s;
    }
    col += sc3;
    col = max(col, vec3(0.04, 0.12, 0.13));

    // o relevo da skin (rosto, roupa, sombras) modula a galaxia: o jogador continua reconhecivel
    float lum = dot(tex.rgb, vec3(0.30, 0.59, 0.11));
    col *= mix(0.45, 1.55, clamp(lum * 1.15, 0.0, 1.0));
    // sombreamento por face (luz direcional do jogo) separa frente, lados e topo
    col *= mix(0.60, 1.15, clamp(vertexColor.r, 0.0, 1.0));
    // luz do mundo so de leve: a galaxia brilha por conta propria, mas nao some de dia nem fica igual no escuro
    col *= mix(vec3(1.0), lightMapColor.rgb, 0.25);

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
