package com.witch.module;

public enum Category {
	MOVEMENT("Movement"),
	COMBAT("Combat"),
	VISUALS("Visuals"),
	WORLD("World"),
	UTILITY("Utility"),
	HUD("HUD"),
	EFFECTS("Effects"),
	AVATAR("Avatar"),
	SHADERS("Shaders");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}