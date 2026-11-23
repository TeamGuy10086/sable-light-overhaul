package dev.sablelightoverhaul;

/** Private particle vertex contract, not world illumination or network data. */
public final class GasVertexData {
    private GasVertexData() {}
    public static final int TAG = 12;
    public static int materialLight(int packed,boolean flame) {
        // CBC cooling light legitimately occupies arbitrary low block-light bits.
        // The second marker is in the otherwise quantized sky-light nibble, restored in the pack vertex shader.
        return (packed & ~0x000F000F) | 0x000B0000 | (flame?13:14);
    }
    /** Owned soft-smoke material: its UV pair is local plane coordinates plus an integer seed. */
    public static int smokeLight(int packed) { return (packed & ~0x000F000F)|0x000B000F; }
    /** Continuous ribbon UV (longitudinal, transverse), distinct from the accepted round puff. */
    public static int ribbonLight(int packed) { return (packed & ~0x000F000F)|0x000B000B; }
    /** Streak-only marker: preserve actual position beyond the terrain far plane. */
    public static int streakLight(int packed) { return (packed & ~0x000F000F)|0x000B000A; }
    /** Plasma uses an independent HDR emission material in Photon and Complementary. */
    public static int plasmaLight(int packed) { return (packed & ~0x000F000F) | 0x000B0009; }
    // Signed logarithmic coordinate, decoded at vertices BEFORE interpolation.
    // Retains near-axis precision even for a long thin volume seen obliquely.
    public static int light(double transverse) { return light(transverse,0); }
    public static int light(double transverse,double seed) {
        double encoded = Math.copySign(Math.log1p(Math.abs(transverse))/Math.log(2)*128,transverse);
        int q = (int)Math.clamp(Math.round(encoded)+2048,0,4095);
        int shot=(int)Math.clamp(Math.round(seed/64*65535),0,65535);
        return (shot<<16) | (q<<4) | TAG;
    }
    public static double seed(int packed) { return ((packed >>> 16)&65535)*64.0/65535; }
    public static double transverse(int packed) {
        int q=((packed & 65535)>>4)-2048;
        return Math.copySign(Math.pow(2,Math.abs(q)/128.0)-1,q);
    }
    public static float aspect(double widthOverLength) {
        double value=0.5+Math.log(widthOverLength)/Math.log(2)/16;
        int quantized=(int)Math.clamp(Math.round(value*255),0,255);
        return (quantized+0.001f)/255f; // VertexConsumer truncates, rather than rounds, the colour byte.
    }
    public static double aspectRatio(float encoded) { return Math.pow(2,(encoded-0.5)*16); }
    public static float step(double ratio) {
        return (float)Math.clamp(Math.log1p(ratio)/Math.log(2)/16,0,1);
    }
    public static double stepRatio(float encoded) { return Math.pow(2,encoded*16)-1; }
}
