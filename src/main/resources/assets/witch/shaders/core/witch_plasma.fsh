#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:globals.glsl>

in vec3 pos;

out vec4 fragColor;

// Ceu de plasma do Arctic. ColorModulator.rgb = cor do plasma, .a = intensidade.
// GameTime volta ao zero a cada dia do jogo, entao toda animacao e periodica em GameTime (sem pulo).

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
    for (int i = 0; i < 4; i++) {
        s += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return s;
}

void main() {
    vec3 d = normalize(pos);
    vec3 tint = ColorModulator.rgb;
    vec3 deep = tint * 0.07 + vec3(0.004, 0.012, 0.024);
    bool below = ColorModulator.a < 0.0; // passe espelhado: plasma abaixo do horizonte, sem fade
    float inten = abs(ColorModulator.a);
    float up = 1.0; // metade de cima e de baixo espelhadas: continuo no horizonte
    if (up <= 0.0) {
        fragColor = vec4(deep, 1.0);
        return;
    }
    vec2 q = d.xz / (abs(d.y) + 0.30) * 1.5;
    float tt = GameTime * 6.2831853;
    vec3 w1 = vec3(cos(tt * 30.0), sin(tt * 30.0), sin(tt * 15.0)) * 0.55;
    vec3 w2 = vec3(sin(tt * 20.0), cos(tt * 20.0), cos(tt * 10.0)) * 0.55;

    float n1 = fbm(vec3(q * 0.9, 0.0) + w1);
    float n2 = fbm(vec3(q * 1.1 + n1 * 1.7, 2.0) + w2);
    float n3 = fbm(vec3(q * 1.7 - n2 * 1.3, 5.0) + w1.zxy);

    float l1 = 0.034 / (abs(n2 - 0.5) + 0.022);
    float l2 = 0.024 / (abs(n3 - 0.5) + 0.018);
    float glow = clamp(l1 * 0.85 + l2 * 0.55, 0.0, 1.6);

    vec3 base = mix(tint * 0.05, tint * 0.20, n1) + vec3(0.004, 0.012, 0.024);
    vec3 core = mix(tint, vec3(1.0), clamp(glow - 0.8, 0.0, 1.0));
    vec3 col = base + core * glow * inten * 0.95 + tint * n2 * 0.10;
    col = mix(deep, col, up);
    fragColor = vec4(col, 1.0);
}
