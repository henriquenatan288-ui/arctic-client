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
    vec3 col = mix(vec3(0.012, 0.075, 0.085), vec3(0.03, 0.12, 0.12), fbm(sc * 1.3 + vec2(t * 0.010, -t * 0.006)));

    float n1 = fbm(sc * 2.2 + vec2(3.0, 1.0) + vec2(t * 0.020, t * 0.012));
    col += vec3(0.07, 0.30, 0.30) * smoothstep(0.40, 0.85, n1) * 1.2;

    float n2 = fbm(sc * 3.6 + vec2(11.0, 5.0) + n1 + vec2(-t * 0.030, t * 0.018));
    col += vec3(0.20, 0.45, 0.32) * smoothstep(0.45, 0.9, n2) * 1.1;

    float n3 = fbm(sc * 6.0 + vec2(2.0, 8.0) + vec2(t * 0.045, -t * 0.03));
    col += vec3(0.10, 0.40, 0.75) * smoothstep(0.62, 0.95, n3) * 0.6;

    float s = stars(sc * 55.0 + vec2(t * 0.05, 0.0), 0.93) + 0.7 * stars(sc * 90.0 + 5.0 + vec2(t * 0.10, 0.0), 0.95) + 0.5 * stars(sc * 140.0 + 9.0 + vec2(t * 0.18, 0.0), 0.96);
    col += vec3(0.80, 1.0, 0.95) * s;

    // mantem o desenho do item visivel: o brilho da textura original realca os detalhes
    // piso de brilho: nenhuma parte some no escuro
    col = max(col, vec3(0.04, 0.12, 0.13));
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    col = col * (0.70 + 0.8 * lum) + tex.rgb * 0.22;
    // sombreamento por face: separa frente e laterais (da profundidade ao formato)
    float shade = clamp(vertexColor.r, 0.0, 1.0);
    col *= mix(0.55, 1.15, shade);
    // brilho nas bordas da textura (partes escuras do desenho original viram contorno claro)
    col += vec3(0.10, 0.35, 0.33) * (1.0 - smoothstep(0.0, 0.35, lum)) * 0.30;

    // borda com luz: texels vizinhos transparentes => contorno do item brilha
    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 f = fract(texCoord0 / px);
    float e1 = 0.0;
    float e2 = 0.0;
    vec2 d[4] = vec2[](vec2(1, 0), vec2(-1, 0), vec2(0, 1), vec2(0, -1));
    for (int i = 0; i < 4; i++) {
        float near = dot(d[i], f - 0.5) + 0.5; // 0..1 em direcao ao vizinho
        if (texture(Sampler0, texCoord0 + d[i] * px).a < 0.1) {
            e1 = max(e1, smoothstep(0.62, 0.9, near));
            e2 = max(e2, smoothstep(0.35, 0.62, near));
        }
    }
    float pulse = 0.85 + 0.15 * sin(t * 2.0);
    vec3 glow = vec3(0.45, 1.0, 0.85);
    col = mix(col, glow * 1.25 * pulse, e1 * 0.85);
    col += glow * 0.30 * e2 * (1.0 - e1);

    vec4 color = vec4(col, tex.a) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
