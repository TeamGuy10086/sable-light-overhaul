#version 150

in vec3 Position;

void main() {
    // Position.xy is already clip space (projected light-volume bounds).
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
