#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Shader do ClickGUI Arctic: plasma azul suave + reflexo de vidro + brilho na borda, mascarado em retangulo arredondado.
// Parametros vem na cor do vertice: r,g = fase (16 bits, 0..1, loop); b = intensidade; a = proporcao largura/altura * 60.

float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i + vec3(0, 0, 0)), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float a = 0.5;
    float s = 0.0;
    for (int i = 0; i < 3; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

void main() {
    float keep = texture(Sampler0, texCoord0).a * 0.0; // mantem o sampler ligado
    float phase = (vertexColor.r * 255.0 * 256.0 + vertexColor.g * 255.0) / 65535.0;
    float inten = vertexColor.b;
    float aspect = vertexColor.a * 255.0 / 60.0;

    vec2 p = (texCoord0 - 0.5) * vec2(aspect, 1.0);
    vec2 half_ = vec2(aspect, 1.0) * 0.5 - 0.055;
    vec2 qd = abs(p) - half_;
    float sdf = length(max(qd, 0.0)) + min(max(qd.x, qd.y), 0.0) - 0.055;
    float mask = smoothstep(0.004, -0.004, sdf);
    if (mask <= 0.0) { fragColor = vec4(0.0); return; }

    float tt = phase * 6.2831853;
    vec3 w1 = vec3(cos(tt), sin(tt), sin(tt * 2.0)) * 0.6;
    vec3 w2 = vec3(sin(tt * 2.0), cos(tt), cos(tt * 3.0)) * 0.6;
    vec2 q2 = p * 2.2;
    float n1 = fbm(vec3(q2, 0.0) + w1);
    float n2 = fbm(vec3(q2 * 1.2 + n1 * 1.6, 2.0) + w2);
    float line = 0.030 / (abs(n2 - 0.5) + 0.030);
    float glow = clamp(line * 0.75, 0.0, 1.2);
    vec3 ice = vec3(0.55, 0.80, 1.0);
    vec3 plasma = mix(ice, vec3(1.0), clamp(glow - 0.7, 0.0, 1.0)) * glow * 0.30;

    // reflexo diagonal de vidro que atravessa a janela
    float s = texCoord0.x * 0.85 + texCoord0.y * 0.45;
    float band = fract(phase) * 2.6 - 0.7;
    float sheen = exp(-pow((s - band) * 6.5, 2.0)) * 0.16;

    // brilho fino junto da borda
    float edge = exp(-max(-sdf, 0.0) * 55.0) * 0.18;

    vec3 col = plasma + vec3(sheen) + ice * edge;
    float a = clamp(max(max(plasma.r, plasma.b) * 1.4, sheen) + edge, 0.0, 1.0) * inten * mask + keep;
    fragColor = vec4(col / max(a, 0.001) * min(a, 1.0), a);
}
