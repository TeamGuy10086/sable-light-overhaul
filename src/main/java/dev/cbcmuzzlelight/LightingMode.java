package dev.cbcmuzzlelight;

/**
 * Lighting tiers, ordered from most conservative to most capable.
 *
 * <p>{@code Legacy} writes synthetic block light into the vanilla light engine: the light is "real"
 * (other systems that read block light see it) but every change rebuilds affected meshes.
 *
 * <p>{@code Compatible} is the area-light backend (Lamb + Sable plot receivers). It works with any
 * shader pack and is the only tier that moves static light between the world and physical bodies,
 * but it also rebuilds meshes, so rapid fire is limited by rebuild throughput.
 *
 * <p>{@code DynamicOnly} is the screen-space GPU pass: muzzle and tracer lights only, and it never
 * touches a mesh.
 *
 * <p>{@code Advanced} injects the same lights into a shader pack that has the CBC muzzle-light patch
 * installed, so the pack's own lighting pass shades them (normals, AO, exposure, shadows).
 */
public enum LightingMode {
    Legacy,
    Compatible,
    DynamicOnly,
    Advanced,
    None;

    /**
     * Falls back to the most capable tier that is actually available.
     *
     * @param gpuAvailable  the screen-space/in-pack GPU pass has a working shader
     * @param lambAvailable the area-light backend (Lamb) is usable
     */
    public LightingMode resolve(boolean gpuAvailable, boolean lambAvailable) {
        return switch (this) {
            case Compatible -> lambAvailable ? this : Legacy;
            case DynamicOnly, Advanced -> gpuAvailable ? this : (lambAvailable ? Compatible : Legacy);
            default -> this;
        };
    }
}
