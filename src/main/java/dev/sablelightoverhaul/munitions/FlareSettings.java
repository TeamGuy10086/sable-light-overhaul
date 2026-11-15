package dev.sablelightoverhaul.munitions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

/** Per-round payload, shared by item components, CBC loading NBT and the flying shell. */
public record FlareSettings(int burnSeconds, float brightness, int rgb, int angleDegrees) {
    public static final int DEFAULT_RGB = 0xFF5030;
    public static final int DEFAULT_ANGLE = 360;
    public static final FlareSettings DEFAULT = new FlareSettings(60, 6F, DEFAULT_RGB, DEFAULT_ANGLE);
    public static final String NBT_KEY = "SloFlare";
    /** The named colour form a component stores. */
    public static final Codec<RgbColor> COLOR_CODEC = RgbColor.CODEC;
    /** The packed {@code 0xRRGGBB} int older components and configs still carry. */
    public static final Codec<Integer> LEGACY_COLOR_CODEC = Codec.intRange(0x000000, 0xFFFFFF);
    /** {@code "color"} reads either shape and always writes the named one. */
    public static final Codec<Integer> COLOR = Codec.either(COLOR_CODEC, LEGACY_COLOR_CODEC)
        .xmap(either -> either.map(RgbColor::rgb, rgb -> rgb), rgb -> com.mojang.datafixers.util.Either.left(RgbColor.fromInt(rgb)));
    public static final Codec<FlareSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(1, 3600).optionalFieldOf("burn_seconds", 60).forGetter(FlareSettings::burnSeconds),
        Codec.floatRange(0F, 15F).optionalFieldOf("brightness", 6F).forGetter(FlareSettings::brightness),
        COLOR.optionalFieldOf("color", DEFAULT_RGB).forGetter(FlareSettings::rgb),
        Codec.intRange(1, 360).optionalFieldOf("angle", DEFAULT_ANGLE).forGetter(FlareSettings::angleDegrees)
    ).apply(instance, FlareSettings::new));
    public static final StreamCodec<ByteBuf, FlareSettings> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, FlareSettings::burnSeconds,
        ByteBufCodecs.FLOAT, FlareSettings::brightness,
        ByteBufCodecs.VAR_INT, FlareSettings::rgb,
        ByteBufCodecs.VAR_INT, FlareSettings::angleDegrees,
        FlareSettings::new);

    /** Legacy two-argument form; delegates to the default color and angle. */
    public FlareSettings(int burnSeconds, float brightness) {
        this(burnSeconds, brightness, DEFAULT_RGB, DEFAULT_ANGLE);
    }

    /** Legacy three-argument form; delegates to the default angle. */
    public FlareSettings(int burnSeconds, float brightness, int rgb) {
        this(burnSeconds, brightness, rgb, DEFAULT_ANGLE);
    }

    public FlareSettings {
        burnSeconds = Math.clamp(burnSeconds, 1, 3600);
        brightness = Float.isFinite(brightness) ? Math.clamp(brightness, 0F, 15F) : 6F;
        rgb = Math.clamp(rgb, 0x000000, 0xFFFFFF);
        angleDegrees = Math.clamp(angleDegrees, 1, 360);
    }

    /** The colour as channels rather than a packed int. */
    public RgbColor color() {
        return RgbColor.fromInt(rgb);
    }

    /** Writes {@code BurnSeconds}, {@code Brightness}, {@code Color{Red,Green,Blue}} and {@code Angle}. */
    public void write(CompoundTag root) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("BurnSeconds", burnSeconds);
        payload.putFloat("Brightness", brightness);
        RgbColor color = color();
        CompoundTag named = new CompoundTag();
        named.putInt("Red", color.red());
        named.putInt("Green", color.green());
        named.putInt("Blue", color.blue());
        payload.put("Color", named);
        payload.putInt("Angle", angleDegrees);
        root.put(NBT_KEY, payload);
    }

    /** Reads both the compound colour and the legacy packed int; a missing key of any of the four defaults. */
    public static FlareSettings read(CompoundTag root) {
        if (root == null || !root.contains(NBT_KEY, Tag.TAG_COMPOUND)) return DEFAULT;
        CompoundTag payload = root.getCompound(NBT_KEY);
        return new FlareSettings(
            payload.contains("BurnSeconds", Tag.TAG_ANY_NUMERIC) ? payload.getInt("BurnSeconds") : 60,
            payload.contains("Brightness", Tag.TAG_ANY_NUMERIC) ? payload.getFloat("Brightness") : 6F,
            readColor(payload),
            payload.contains("Angle", Tag.TAG_ANY_NUMERIC) ? payload.getInt("Angle") : DEFAULT_ANGLE);
    }

    private static int readColor(CompoundTag payload) {
        Tag color = payload.get("Color");
        if (color instanceof CompoundTag named) {
            // Each channel defaults to 0 and clamps on its own, so a hand-written partial colour is finite.
            return new RgbColor(channel(named, "Red"), channel(named, "Green"), channel(named, "Blue")).rgb();
        }
        return payload.contains("Color", Tag.TAG_ANY_NUMERIC) ? payload.getInt("Color") : DEFAULT_RGB;
    }

    private static int channel(CompoundTag named, String key) {
        return named.contains(key, Tag.TAG_ANY_NUMERIC) ? named.getInt(key) : 0;
    }
}
