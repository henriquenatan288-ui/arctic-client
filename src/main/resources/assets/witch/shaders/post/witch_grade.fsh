#version 330

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
