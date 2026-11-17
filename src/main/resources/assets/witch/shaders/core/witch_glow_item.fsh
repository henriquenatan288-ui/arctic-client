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

// cor do brilho vem como define (um pipeline por cor, ver GlowItems.java)
#ifndef GLOW_R
#define GLOW_R 0.35
#define GLOW_G 0.95
#define GLOW_B 1.0
#endif

float solid(vec2 uv) {
    return texture(Sampler0, uv).a < 0.1 ? 0.0 : 1.0;
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;
    }

    vec2 px = 1.0 / vec2(textureSize(Sampler0, 0));
    // borda: o texel encosta em vazio (4 vizinhos + diagonais). Segunda faixa: vazio a 2 texels.
    float e1 = 0.0;
    float e2 = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            if (i == 0 && j == 0) continue;
            float w = (i == 0 || j == 0) ? 1.0 : 0.7;
            e1 = max(e1, (1.0 - solid(texCoord0 + vec2(float(i), float(j)) * px)) * w);
            e2 = max(e2, (1.0 - solid(texCoord0 + vec2(float(i), float(j)) * px * 2.0)) * w);
        }
    }
    float t = GameTime * 1200.0 * 8.0;
    float pulse = 0.82 + 0.18 * sin(t * 2.5 + (texCoord0.x + texCoord0.y) * 40.0);
    vec3 glow = vec3(GLOW_R, GLOW_G, GLOW_B);

    vec3 base = tex.rgb;
    vec3 col = base;
    col = mix(col, glow * 0.9, e2 * (1.0 - e1) * 0.22);              // brilho suave por dentro
    col = mix(col, mix(glow, vec3(1.0), 0.35) * 1.15 * pulse, e1 * 0.9); // linha da borda

    vec4 color = vec4(col, tex.a);
    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    // a linha brilha mesmo no escuro: so um pouco do lightmap nela
    vec4 lm = mix(lightMapColor, vec4(1.0), e1 * 0.8);
    color *= lm;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
