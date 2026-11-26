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
    return c * smoothstep(0.50, 1.0, l);
}

void main() {
    float aspect = OutSize.x / OutSize.y;
    vec2 asp = vec2(aspect, 1.0);
    vec2 uv = texCoord;

    // ---- lente gravitacional (ponto de massa): fonte = p * (1 - thetaE^2 / d^2)
    if (Hole.w > 0.001) {
        vec2 p = (uv - Hole.xy) * asp;
        float d = max(length(p), 1e-4);
        float te = Hole.z * 1.9;
        float keep = smoothstep(Hole.z * 1.02, Hole.z * 1.7, d);          // dentro do horizonte nao mexe
        float far = 1.0 - smoothstep(te * 3.0, te * 9.0, d);               // some com a distancia
        float k = te * te / max(d * d, te * te * 0.35);
        vec2 src = p * (1.0 - clamp(k, 0.0, 1.6) * Hole.w * keep * far);
        uv = Hole.xy + src / asp;
    }

    // ---- aberracao cromatica radial
    vec2 dir = uv - 0.5;
    float ca = Params.y * 0.012;
    vec3 c = vec3(S(uv + dir * ca).r, S(uv).g, S(uv - dir * ca).b);

    // ---- bloom: 3 aneis de 10 amostras
    vec3 bl = vec3(0.0);
    float radii[3] = float[3](0.006, 0.020, 0.052);
    float wts[3] = float[3](1.0, 0.75, 0.55);
    for (int r = 0; r < 3; r++) {
        for (int i = 0; i < 10; i++) {
            float a = float(i) * 0.6283 + float(r) * 0.31;
            vec2 o = vec2(cos(a), sin(a)) * radii[r] / asp;
            bl += bright(S(uv + o)) * wts[r];
        }
    }
    c += bl * (Params.x * 0.05);

    // ---- rastro anamorfico horizontal azulado
    vec3 st = vec3(0.0);
    for (int i = -8; i <= 8; i++) {
        if (i == 0) continue;
        float w = exp(-abs(float(i)) * 0.28);
        st += bright(S(uv + vec2(float(i) * 0.011 / aspect, 0.0))) * w;
    }
    c += st * vec3(0.45, 0.65, 1.0) * (Params.z * 0.035);

    // ---- vinheta suave
    float v = length((texCoord - 0.5) * asp);
    c *= 1.0 - Params.w * smoothstep(0.45, 1.15, v);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
