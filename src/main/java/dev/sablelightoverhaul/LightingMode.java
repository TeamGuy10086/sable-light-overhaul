package dev.sablelightoverhaul;

/** Client lighting modes and their shader-availability fallback. */
public enum LightingMode {
    SloOnly,
    DynamicOnly,
    Advanced,
    Off;

    public LightingMode resolve(boolean gpuAvailable, boolean packActive, boolean supportedPack) {
        if (this == Off || !gpuAvailable) return Off;
        if (!packActive) return this == DynamicOnly ? DynamicOnly : SloOnly;
        return this == Advanced && supportedPack ? Advanced : DynamicOnly;
    }
}
