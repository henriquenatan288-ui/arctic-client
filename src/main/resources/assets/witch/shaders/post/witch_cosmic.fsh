#version 330

// Shader do Cosmic FX: lente gravitacional real em volta do buraco negro (distorce o mundo/ceu atras),
// bloom suave, rastro anamorfico azul, aberracao cromatica leve e vinheta. O HUD fica de fora.
uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform LensConfig {
    vec4 Hole;   // xy = posicao do buraco na tela (0..1), z = raio do horizonte (em alturas de tela), w = forca da lente (0 = sem lente)
    vec4 Params; // x = bloom, y = aberracao cromatica, z = rastro anamorfico, w = vinheta
};

out vec4 fragColor;

vec3 S(vec2 uv) {
    return texture(InSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

vec3 bright(vec3 c) {
    float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
    return c * smoothstep(0.28, 0.85, l);
}

void main() {
    // protecao: valores de uniforme fora do esperado (driver/GPU diferente) nunca podem inverter cor nem entortar a tela toda
    vec4 hole = vec4(clamp(Hole.xy, vec2(-1.0), vec2(2.0)), clamp(Hole.z, 0.0, 0.09), clamp(Hole.w, 0.0, 1.0));
    vec4 prm = clamp(Params, vec4(0.0), vec4(14.0, 2.0, 14.0, 1.0));
    float aspect = OutSize.x / OutSize.y;
    vec2 asp = vec2(aspect, 1.0);
    vec2 uv = texCoord;

    // ---- lente gravitacional (ponto de massa): fonte = p * (1 - thetaE^2 / d^2)
    if (hole.w > 0.001) {
        vec2 p = (uv - hole.xy) * asp;
        float d = max(length(p), 1e-4);
        float te = hole.z * 1.7;
        float keep = smoothstep(hole.z * 2.4, hole.z * 3.8, d);          // dentro do horizonte nao mexe
        float far = 1.0 - smoothstep(te * 3.0, te * 9.0, d);               // some com a distancia
        float k = te * te / max(d * d, te * te * 0.35);
        vec2 src = p * (1.0 - clamp(k, 0.0, 0.9) * hole.w * keep * far);
        uv = hole.xy + src / asp;
    }

    // ---- aberracao cromatica radial
    vec2 dir = uv - 0.5;
    float ca = prm.y * 0.008;
    vec3 c = vec3(S(uv + dir * ca).r, S(uv).g, S(uv - dir * ca).b);

    // ---- bloom: 3 aneis de 10 amostras
    vec3 bl = vec3(0.0);
    for (int r = 0; r < 3; r++) {
        float rad = r == 0 ? 0.006 : (r == 1 ? 0.020 : 0.052);
        float wt = r == 0 ? 1.0 : (r == 1 ? 0.75 : 0.55);
        for (int i = 0; i < 10; i++) {
            float a = float(i) * 0.6283 + float(r) * 0.31;
            vec2 o = vec2(cos(a), sin(a)) * rad / asp;
            bl += bright(S(uv + o)) * wt;
        }
    }
    c += bl * (prm.x * 0.05);

    // ---- rastro anamorfico horizontal azulado
    vec3 st = vec3(0.0);
    for (int i = -8; i <= 8; i++) {
        if (i == 0) continue;
        float w = exp(-abs(float(i)) * 0.28);
        st += bright(S(uv + vec2(float(i) * 0.011 / aspect, 0.0))) * w;
    }
    c += st * vec3(0.45, 0.65, 1.0) * (prm.z * 0.035);

    // ---- cor com vida: saturacao, curva de contraste suave e tinta dividida (sombras azul-violeta, luzes quentes)
    float lum = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(lum), c, clamp(1.0 + 0.22 * prm.w * 4.0, 1.0, 1.35));
    c = c * c * (3.0 - 2.0 * c) * 0.35 + c * 0.65;
    c += vec3(0.020, 0.012, 0.045) * (1.0 - lum) * 0.8;
    c *= mix(vec3(1.0), vec3(1.06, 1.02, 0.95), smoothstep(0.35, 0.9, lum));
    // brilho suave geral (neblina de luz): da maciez mesmo sem fontes fortes na tela
    c += bl * 0.004 * prm.x;

    // ---- vinheta suave
    float v = length((texCoord - 0.5) * asp);
    c *= 1.0 - prm.w * 0.7 * smoothstep(0.55, 1.2, v);

    fragColor = vec4(clamp(max(c, vec3(0.0)), 0.0, 1.0), 1.0);
}
