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
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    // coordenadas de tela: o item vira uma "janela" para o espaco
    vec2 sc = gl_FragCoord.xy / 360.0;

    // camadas em profundidades diferentes (o deslocamento muda com a escala)
    float t = GameTime * 1200.0 * 8.0;
    vec3 col = mix(vec3(0.015, 0.025, 0.09), vec3(0.06, 0.03, 0.16), fbm(sc * 1.3 + vec2(t * 0.010, -t * 0.006)));

    float n1 = fbm(sc * 2.2 + vec2(3.0, 1.0) + vec2(t * 0.020, t * 0.012));
    col += vec3(0.10, 0.22, 0.75) * smoothstep(0.42, 0.85, n1) * 0.9;

    float n2 = fbm(sc * 3.6 + vec2(11.0, 5.0) + n1 + vec2(-t * 0.030, t * 0.018));
    col += vec3(0.42, 0.20, 0.95) * smoothstep(0.45, 0.9, n2) * 0.8;

    float n3 = fbm(sc * 6.0 + vec2(2.0, 8.0) + vec2(t * 0.045, -t * 0.03));
    col += vec3(0.25, 0.55, 1.0) * smoothstep(0.62, 0.95, n3) * 0.45;

    float s = stars(sc * 55.0 + vec2(t * 0.05, 0.0), 0.93) + 0.7 * stars(sc * 90.0 + 5.0 + vec2(t * 0.10, 0.0), 0.95) + 0.5 * stars(sc * 140.0 + 9.0 + vec2(t * 0.18, 0.0), 0.96);
    col += vec3(0.85, 0.92, 1.0) * s;

    // mantem o desenho do item visivel: o brilho da textura original realca os detalhes
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    col = col * (0.55 + 0.9 * lum) + tex.rgb * 0.10;

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
