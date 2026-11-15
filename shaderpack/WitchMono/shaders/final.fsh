#version 120

// Witch Mono / Void - post-processing com visual de End / galáxia / profundidade
// Mantém as opções do menu do Iris e adiciona tom void + bloom mais forte.

// ===== opções (aparecem no menu do Iris) =====
#define MONO 0.25          // [0.0 0.1 0.15 0.2 0.25 0.3 0.4 0.5 0.6 0.7 0.8 1.0]
#define CONTRAST 1.18      // [1.0 1.05 1.1 1.15 1.18 1.25 1.35 1.5]
#define BRIGHTNESS -0.02   // [-0.1 -0.05 -0.02 0.0 0.05 0.1]
#define VIGNETTE 0.55      // [0.0 0.2 0.35 0.45 0.55 0.7 0.85]
#define GRAIN 0.018        // [0.0 0.01 0.018 0.03 0.05]
#define BLOOM 0.55         // [0.0 0.2 0.35 0.45 0.55 0.7 0.9 1.2]
#define VOID_STRENGTH 0.45 // [0.0 0.2 0.3 0.4 0.45 0.55 0.7 0.9]  // quanto puxa pro roxo/void

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float frameTimeCounter;

varying vec2 texcoord;

float luma(vec3 c) {
	return dot(c, vec3(0.299, 0.587, 0.114));
}

float hash(vec2 p) {
	return fract(sin(dot(p, vec2(12.9898, 78.233)) + frameTimeCounter * 0.7) * 43758.5453);
}

// Bloom mais forte nas partes claras (estrelas / brilhos)
vec3 bloom(vec2 uv) {
	vec2 px = 1.0 / vec2(viewWidth, viewHeight);
	vec3 sum = vec3(0.0);
	float total = 0.0;
	for (int i = -3; i <= 3; i++) {
		for (int j = -3; j <= 3; j++) {
			vec2 o = vec2(float(i), float(j)) * px * 2.8;
			vec3 s = texture2D(colortex0, uv + o).rgb;
			float w = 1.0 / (1.0 + float(i*i + j*j) * 0.6);
			float bright = max(luma(s) - 0.52, 0.0);
			sum += s * bright * w;
			total += w;
		}
	}
	return sum / max(total, 0.001);
}

// Leve deslocamento de cor para o void/roxo do End
vec3 applyVoidTint(vec3 c, float strength) {
	// tom base do void (roxo bem escuro)
	vec3 voidColor = vec3(0.12, 0.04, 0.22);
	float l = luma(c);
	// partes escuras recebem mais o tom void, partes claras menos
	float factor = strength * (1.0 - smoothstep(0.25, 0.75, l));
	return mix(c, c * 0.65 + voidColor * 0.9, factor);
}

void main() {
	vec3 c = texture2D(colortex0, texcoord).rgb;

	// Bloom primeiro (dá brilho de "estrelas")
	if (BLOOM > 0.0) {
		c += bloom(texcoord) * BLOOM * 1.9;
	}

	// Empurra pro visual void / End
	c = applyVoidTint(c, VOID_STRENGTH);

	// Mono controlado
	float l = luma(c);
	c = mix(c, vec3(l), MONO);

	// Contraste + brilho
	c = (c - 0.5) * CONTRAST + 0.5 + BRIGHTNESS;

	// Vinheta mais forte nas bordas (sensação de profundidade)
	vec2 d = texcoord - 0.5;
	float aspect = viewHeight / viewWidth;
	float v = smoothstep(0.92, 0.18, length(d * vec2(1.0, aspect + 0.4)));
	c *= mix(1.0 - VIGNETTE, 1.0, v);

	// Grain sutil
	c += (hash(texcoord * vec2(viewWidth, viewHeight)) - 0.5) * GRAIN;

	// Pequeno boost nas partes muito claras (estrelas)
	float bright = max(luma(c) - 0.7, 0.0);
	c += vec3(0.08, 0.05, 0.14) * bright * 1.5;

	gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
