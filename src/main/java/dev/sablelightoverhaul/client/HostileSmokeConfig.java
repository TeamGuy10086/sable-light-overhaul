package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.HostileSmokeWeapon;
import dev.sablelightoverhaul.lighting.GroundDustParticleData;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HostileSmokeConfig {
    public static final String CATEGORY = "Sable Hostile 武器特效";
    public static final String PREFIX = "sableHostileSmoke.";
    private static final Map<HostileSmokeWeapon, Profile> PROFILES = new EnumMap<>(HostileSmokeWeapon.class);

    private HostileSmokeConfig() {}

    static void define(ModConfigSpec.Builder builder) {
        for (var weapon : HostileSmokeWeapon.values()) {
            var profile = new Profile(builder, weapon);
            PROFILES.put(weapon, profile);
            if (weapon.thin()) {
                profile.integer("hazeCount", weapon.count(), 0, 24);
                profile.decimal("hazeSize", weapon.length(), .5, 16);
                profile.integer("hazeLifetime", 75, 10, 400);
                profile.decimal("hazeAlpha", .32, 0, .8);
                profile.integer("hazeInterval", 3, 0, 100);
                profile.decimal("hazeSpread", .045, 0, .4);
                profile.decimal("hazeDrift", .04, 0, .6);
                profile.decimal("hazeSink", 0, 0, .04);
                profile.decimal("hazeClearance", .4, 0, 8);
                profile.decimal("hazePush", .04, 0, .4);
                profile.decimal("hazePushRadius", 3, .5, 24);
                profile.decimal("hazeLightGain", .6, 0, 4);
                profile.integer("color", 0xFFFFFF, 0, 0xFFFFFF);
            } else {
                profile.integer("lobesPerShot", weapon.count(), 0, 256);
                profile.decimal("length", weapon.length(), .25, 128);
                profile.decimal("diameter", weapon.diameter(), .25, 64);
                profile.integer("lifetimeTicks", weapon.count() < 16 ? 110 : 160, 10, 1200);
                profile.integer("hotTicks", 20, 1, 200);
                profile.integer("releaseTicks", 5, 1, 60);
                profile.decimal("rootHotScale", .35, .05, 8);
                profile.decimal("tipHotScale", 1.5, .05, 8);
                profile.decimal("opacity", .8, 0, 1);
                profile.decimal("riseSpeed", .35, 0, 8);
                profile.decimal("spread", .85, 0, 4);
                profile.decimal("expansion", 1.6, 0, 8);
                profile.decimal("fadeFrom", .95, 0, .95);
                profile.decimal("coldFraction", .2, 0, 1);
                profile.decimal("coldSize", 1.5, .25, 4);
                profile.decimal("curl", .25, 0, 2);
                builder.comment("冷烟透明度纹理强度，0关闭，3最强。寿命30%至75%逐渐加强，稀薄区域更透明，浓烟保留。");
                profile.decimal("densityDetail", 3, 0, 3);
            }
        }
    }

    static Profile profile(HostileSmokeWeapon weapon) { return PROFILES.get(weapon); }

    static final class Profile {
        private final ModConfigSpec.Builder builder;
        private final String prefix;
        private final ModConfigSpec.BooleanValue enabled;
        private final Map<String, ModConfigSpec.IntValue> integers = new HashMap<>();
        private final Map<String, ModConfigSpec.DoubleValue> decimals = new HashMap<>();

        Profile(ModConfigSpec.Builder builder, HostileSmokeWeapon weapon) {
            this.builder = builder;
            this.prefix = PREFIX + weapon.path() + ".";
            enabled = builder.comment("Enable muzzle smoke for this weapon.").define(prefix + "enabled", true);
        }

        private void integer(String key, int value, int min, int max) {
            integers.put(key, builder.defineInRange(prefix + key, value, min, max));
        }
        private void decimal(String key, double value, double min, double max) {
            decimals.put(key, builder.defineInRange(prefix + key, value, min, max));
        }
        boolean enabled() { return enabled.get(); }
        int integer(String key) { return integers.get(key).get(); }
        double decimal(String key) { return decimals.get(key).get(); }
        float number(String key) { return (float) decimal(key); }

        GroundDustParticleData haze() {
            return new GroundDustParticleData(number("hazeSize"), integer("color"), integer("hazeLifetime"),
                1, false, 1, 0, number("hazeAlpha"), true);
        }
        BigSmokeParticle.Appearance thermal() {
            return new BigSmokeParticle.Appearance(decimal("length"), decimal("diameter") * .5,
                integer("lifetimeTicks"), integer("hotTicks"), decimal("rootHotScale"), decimal("tipHotScale"),
                decimal("opacity"), decimal("riseSpeed"), decimal("spread"), decimal("expansion"), decimal("fadeFrom"),
                decimal("coldFraction"), decimal("coldSize"), decimal("curl"), decimal("densityDetail"), true);
        }
    }
}
