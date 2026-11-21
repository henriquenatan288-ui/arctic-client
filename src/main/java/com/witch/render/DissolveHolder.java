package com.witch.render;

/** Guarda no estado de render do mob o quanto ele ja virou po (1 = normal, menor = mais transparente). */
public interface DissolveHolder {
	float witch$getDissolve();

	void witch$setDissolve(float a);
}
