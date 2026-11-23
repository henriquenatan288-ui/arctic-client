#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;

out vec3 pos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    float sy = ColorModulator.a < 0.0 ? -0.05 : 0.05; // mesma escala aplicada no modelview (PlasmaSky)
    pos = vec3(Position.x, Position.y * sy, Position.z);
}
