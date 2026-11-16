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
    return smoothstep(0.16, 0.0, d) * (0.4 + 0.6 * b);
}

void main() {
    float fw = max(fwidth(texCoord0.x), fwidth(texCoord0.y)); // antes do discard (derivadas precisam de fluxo uniforme)
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    // Espaco de tela, centrado: o item e uma "janela" para o espaco. Cada camada usa escala e rotacao proprias,
    // entao itens em posicoes diferentes da tela (e o balanco da mao) deslocam as camadas de forma diferente = paralaxe.
    vec2 px0 = 1.0 / vec2(textureSize(Sampler0, 0));
    float ppt = fw > 1e-6 ? px0.x / fw : 12.0; // pixels de tela por texel
    ppt = clamp(ppt, 3.0, 160.0);
    vec2 q = (gl_FragCoord.xy - 0.5 * ScreenSize) / (ppt * 14.0);

    float t = GameTime * 1200.0 * 8.0;

    // nevoa de fundo (cores padrao do portal: azul-petroleo e verde)
    vec3 col = mix(vec3(0.012, 0.075, 0.085), vec3(0.03, 0.12, 0.12), fbm(q * 1.3 + vec2(t * 0.010, -t * 0.006)));
    float n1 = fbm(q * 2.2 + vec2(3.0, 1.0) + vec2(t * 0.020, t * 0.012));
    col += vec3(0.07, 0.30, 0.30) * smoothstep(0.40, 0.85, n1) * 1.2;
    float n2 = fbm(q * 3.6 + vec2(11.0, 5.0) + n1 + vec2(-t * 0.030, t * 0.018));
    col += vec3(0.20, 0.45, 0.32) * smoothstep(0.45, 0.9, n2) * 1.1;
    float n3 = fbm(q * 6.0 + vec2(2.0, 8.0) + vec2(t * 0.045, -t * 0.03));
    col += vec3(0.10, 0.40, 0.75) * smoothstep(0.62, 0.95, n3) * 0.6;

    // camadas de estrelas: cada uma gira num angulo diferente, tem escala e velocidade proprias e uma cor sorteada
    vec3 sc3 = vec3(0.0);
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float ang = fi * 2.399 + 0.5;
        mat2 rot = mat2(cos(ang), -sin(ang), sin(ang), cos(ang));
        vec2 p = rot * q * (34.0 + fi * 24.0) + vec2(t * (0.04 + 0.05 * fi), t * 0.02 * fi) + fi * 17.3;
        float s = stars(p, 0.925 + 0.012 * fi) * (1.0 - 0.13 * fi);
        vec3 tint = 0.5 + 0.5 * cos(6.2831 * (hash(vec2(fi, 3.7)) + vec3(0.0, 0.33, 0.67)));
        tint = mix(tint, vec3(0.80, 1.0, 0.95), 0.6);
        sc3 += tint * s;
    }
    col += sc3;
    col = max(col, vec3(0.04, 0.12, 0.13)); // piso: nada some no escuro

    // mascara pela propria arte: pixels vermelhos (cabeca/lamina) viram galaxia; cabo e contorno ficam como a textura
    float redness = tex.r - max(tex.g, tex.b);
    float m = smoothstep(0.05, 0.12, redness);
    // o relevo da arte (vermelho claro/escuro) modula a galaxia, mantendo o volume do desenho
    float relief = clamp(tex.r * 1.6, 0.0, 1.0);
    col *= mix(0.55, 1.35, relief);
    float shade = clamp(vertexColor.r, 0.0, 1.0); // sombreamento por face: separa frente e laterais
    col *= mix(0.55, 1.15, shade);
    vec3 orig = tex.rgb * (0.55 + 0.9 * shade);
    col = mix(orig, col, m);

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
    col = mix(col, vec3(0.45, 1.0, 0.85) * 1.25 * pulse, e1 * 0.85 * m);

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
