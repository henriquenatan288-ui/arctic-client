#!/usr/bin/env python3
"""Gera os post effects do modulo Filter: assets/witch/post_effect/filter_<preset>_<intensidade>.json
(color grading de tela cheia via PostChain do jogo; o HUD fica de fora). 8 presets x 4 intensidades."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/witch")
OUT = os.path.join(ROOT, "post_effect")

# lift (soma nas sombras), gain (multiplica), saturacao, contraste, brilho, vinheta
PRESETS = {
    1: ("quente",     (0.03, 0.01, -0.01), (1.10, 1.00, 0.86), 1.10, 1.05,  0.00, 0.25),
    2: ("frio",       (-0.01, 0.01, 0.04), (0.88, 0.98, 1.12), 1.00, 1.05,  0.00, 0.25),
    3: ("neon",       (0.05, 0.00, 0.10),  (1.05, 0.95, 1.20), 1.55, 1.15,  0.00, 0.30),
    4: ("sombrio",    (0.00, 0.01, 0.03),  (0.82, 0.88, 0.98), 0.75, 1.12, -0.05, 0.55),
    5: ("por do sol", (0.06, 0.00, 0.06),  (1.18, 0.92, 0.80), 1.20, 1.08,  0.00, 0.30),
    6: ("menta",      (0.00, 0.04, 0.03),  (0.90, 1.10, 1.00), 1.10, 1.03,  0.00, 0.20),
    7: ("cinema",     (-0.02, 0.04, 0.06), (1.10, 1.00, 0.88), 1.15, 1.18,  0.00, 0.35),
    8: ("p&b",        (0.00, 0.00, 0.00),  (1.00, 1.00, 1.00), 0.00, 1.20,  0.00, 0.40),
}
LEVELS = {1: 0.25, 2: 0.50, 3: 0.75, 4: 1.00}


def lerp(a, b, t):
    return a + (b - a) * t


def chain(p, lvl):
    _, lift, gain, sat, con, bri, vig = PRESETS[p]
    s = LEVELS[lvl]
    lf = [lerp(0.0, v, s) for v in lift]
    gn = [lerp(1.0, v, s) for v in gain]
    params = [lerp(1.0, sat, s), lerp(1.0, con, s), lerp(0.0, bri, s), lerp(0.0, vig, s)]
    r = lambda xs: [round(x, 4) for x in xs]
    return {
        "targets": {"swap": {}},
        "passes": [
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "witch:post/witch_grade",
                "inputs": [{"sampler_name": "In", "target": "minecraft:main"}],
                "output": "swap",
                "uniforms": {"GradeConfig": [
                    {"name": "Lift", "type": "vec4", "value": r(lf + [0.0])},
                    {"name": "Gain", "type": "vec4", "value": r(gn + [1.0])},
                    {"name": "Params", "type": "vec4", "value": r(params)},
                ]},
            },
            {
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "swap"}],
                "uniforms": {"BlitConfig": [{"name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0]}]},
                "output": "minecraft:main",
            },
        ],
    }


FSH = '''#version 330

// Color grading de tela cheia do modulo Filter (post effect do jogo: o HUD nao e afetado).
uniform sampler2D InSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform GradeConfig {
    vec4 Lift;   // xyz somado nas sombras
    vec4 Gain;   // xyz multiplica tudo (tinta das luzes)
    vec4 Params; // x saturacao, y contraste, z brilho, w vinheta
};

out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    const vec3 LUM = vec3(0.2126, 0.7152, 0.0722);

    float l = dot(c, LUM);
    c = c * Gain.rgb + Lift.rgb * (1.0 - l);
    c = (c - 0.5) * Params.y + 0.5 + Params.z;
    float l2 = dot(c, LUM);
    c = mix(vec3(l2), c, Params.x);

    float r = length((texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0));
    c *= 1.0 - Params.w * smoothstep(0.40, 1.10, r);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
'''


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.join(ROOT, "shaders/post"), exist_ok=True)
    for p in PRESETS:
        for lvl in LEVELS:
            with open(os.path.join(OUT, "filter_%d_%d.json" % (p, lvl)), "w") as f:
                json.dump(chain(p, lvl), f, indent=2)
    with open(os.path.join(ROOT, "shaders/post/witch_grade.fsh"), "w") as f:
        f.write(FSH)
    print("ok", len(PRESETS) * len(LEVELS), "efeitos")


if __name__ == "__main__":
    main()
