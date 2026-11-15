package dev.sablelightoverhaul.munitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** An RGB triple, each channel 0..255. The named form of the packed {@code 0xRRGGBB} int a flare carries. */
public record RgbColor(int red, int green, int blue) {
    public static final int MIN = 0x00;
    public static final int MAX = 0xFF;

    /** Explicit three-channel input; each channel is range-checked. */
    public static final Codec<RgbColor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(MIN, MAX).fieldOf("red").forGetter(RgbColor::red),
        Codec.intRange(MIN, MAX).fieldOf("green").forGetter(RgbColor::green),
        Codec.intRange(MIN, MAX).fieldOf("blue").forGetter(RgbColor::blue)
    ).apply(instance, RgbColor::new));

    public RgbColor {
        red = Math.clamp(red, MIN, MAX);
        green = Math.clamp(green, MIN, MAX);
        blue = Math.clamp(blue, MIN, MAX);
    }

    /** Unpacks {@code 0xRRGGBB}; out-of-range ints clamp like the constructor does. */
    public static RgbColor fromInt(int rgb) {
        int packed = Math.clamp(rgb, 0x000000, 0xFFFFFF);
        return new RgbColor((packed >> 16) & 0xFF, (packed >> 8) & 0xFF, packed & 0xFF);
    }

    /** Packs back to {@code 0xRRGGBB}. */
    public int rgb() {
        return (red << 16) | (green << 8) | blue;
    }
}
