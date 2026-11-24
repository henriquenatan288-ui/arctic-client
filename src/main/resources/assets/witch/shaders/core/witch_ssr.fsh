#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D Sampler0; // cor da cena (copia do quadro)
uniform sampler2D Sampler1; // profundidade da cena (copia)

in vec3 vPos;   // posicao do piso com a camera na origem
in vec4 vColor; // rgb = normal do piso codificada (0..1), a = forca
out vec4 fragColor;

mat4 gVP;
mat4 gInv;
bool gIdz;

// posicao da cena (no mesmo espaco de vPos) no pixel uv
vec3 scenePos(vec2 uv) {
    float raw = texture(Sampler1, uv).r;
    float nz = gIdz ? raw : raw * 2.0 - 1.0;
    vec4 w = gInv * vec4(uv * 2.0 - 1.0, nz, 1.0);
    return w.xyz / w.w;
}

// >0 quando o ponto S esta atras da superficie visivel no pixel onde ele aparece
float behind(vec3 S, out vec2 uv, out bool ok) {
    vec4 c = gVP * vec4(S, 1.0);
    ok = c.w > 0.001;
    uv = c.xy / c.w * 0.5 + 0.5;
    if (!ok || uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) { ok = false; return 0.0; }
    return length(S) - length(scenePos(uv));
}

void main() {
    vec3 n = normalize(vColor.rgb * 2.0 - 1.0);
    float strength = vColor.a;
    vec3 P = vPos;
    float dist = max(length(P), 0.001);
    vec3 V = P / dist;
    vec3 R = reflect(V, n);

    float cosT = clamp(abs(dot(V, n)), 0.0, 1.0);
    float F = 0.22 + 0.78 * pow(1.0 - cosT, 3.0);

    gVP = ProjMat * ModelViewMat;
    gInv = inverse(gVP);
    // a convencao do buffer de profundidade e deduzida do proprio fragmento (0..1 direto ou -1..1 mapeado)
    vec4 cF = gVP * vec4(P, 1.0);
    float ndcF = cF.z / cF.w;
    float rawF = gl_FragCoord.z;
    gIdz = abs(rawF - ndcF) < abs(rawF - (ndcF * 0.5 + 0.5));

    float jitter = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
    float t = 0.12 + 0.10 * jitter;
    float prevT = t * 0.5;
    bool hit = false;
    bool escaped = false;
    vec2 huv = vec2(0.5);
    vec2 euv = vec2(0.5);
    vec2 lastUV = vec2(0.5);
    float hitT = 0.0;

    for (int i = 0; i < 30; i++) {
        vec3 S = P + R * t;
        vec2 uv;
        bool ok;
        float d = behind(S, uv, ok);
        if (!ok) {
            vec4 c = gVP * vec4(S, 1.0);
            if (c.w > 0.001) { escaped = true; euv = clamp(c.xy / c.w * 0.5 + 0.5, 0.003, 0.997); }
            break;
        }
        lastUV = uv;
        float thick = 0.45 + t * 0.14;
        if (d > 0.0 && d < thick) {
            // refina entre o passo anterior (fora) e este (dentro)
            float lo = prevT, hi = t;
            vec2 buv = uv;
            for (int k = 0; k < 5; k++) {
                float mid = 0.5 * (lo + hi);
                vec2 u2; bool ok2;
                float d2 = behind(P + R * mid, u2, ok2);
                if (ok2 && d2 > 0.0) { hi = mid; buv = u2; } else { lo = mid; }
            }
            hit = true;
            huv = buv;
            hitT = hi;
            break;
        }
        prevT = t;
        t = t * 1.17 + 0.05;
    }

    vec3 tint = ColorModulator.rgb;
    vec3 col = tint;
    float conf = 0.0;
    if (hit) {
        vec2 e = min(huv, 1.0 - huv);
        float edge = smoothstep(0.0, 0.10, min(e.x, e.y));
        col = texture(Sampler0, huv).rgb;
        conf = edge * (1.0 - smoothstep(14.0, 36.0, hitT));
        col = mix(tint, col, conf);
    } else {
        // o raio saiu da tela ou terminou sem achar nada: reflete o ceu (so a cor, sem amostrar a tela: evita listras)
        col = tint;
        conf = 0.5;
    }

    float a = clamp(F * strength * 1.5 * mix(0.45, 1.0, conf), 0.0, 0.92);
    if (a < 0.004) discard;
    fragColor = vec4(col, a);
}
