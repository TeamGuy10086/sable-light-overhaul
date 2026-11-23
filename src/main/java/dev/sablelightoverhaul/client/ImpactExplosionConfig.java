package dev.sablelightoverhaul.client;

import net.neoforged.neoforge.common.ModConfigSpec;

final class ImpactExplosionConfig {
    private static final String PREFIX = "explosions.impactVisual.";
    static ModConfigSpec.BooleanValue enabled;
    static ModConfigSpec.IntValue fireTicks, fireSmokeTicks, fireCount, smokeCount, smokeTicks, trailCount, buriedColumnCount, buriedColumnTicks, trailTicks,
        sparkCount, sparkTicks, debrisCount, debrisTicks, debrisTailTicks;
    static ModConfigSpec.DoubleValue fireRadius, fireOpacity, smokeRadius, smokeSpread, smokeRise,
        smokeExpansion, smokeOpacity, trailWidth, trailReach, trailOpacity,
        sparkWidth, sparkLength, sparkSpeed, debrisSize, debrisSpeed;
    static ModConfigSpec.DoubleValue smokePushSpeed, smokeFadeStart, debrisTailOpacity;

    static ModConfigSpec.DoubleValue buriedColumnHeight, buriedColumnWidth, buriedColumnSpread,
        buriedColumnSpeed, buriedColumnOpacity, groundDustColumnChance;
    static ModConfigSpec.DoubleValue groundFireballLift, groundFireballRiseBias, groundFireballWidth,
        groundFireballBirthSize, groundFireballGrowTime, groundFireballExpansion;

    private ImpactExplosionConfig() {}

    static ModConfigSpec.IntValue dustColumnPuffs;
    static ModConfigSpec.BooleanValue dustHeightFromDestruction;
    static ModConfigSpec.DoubleValue dustColumnBaseWidth, dustColumnTipWidth, dustColumnBend, dustColumnExpansion,
        dustColumnSpin, dustColumnFadeStart, dustColumnBuriedAngle, dustColumnGroundAngle,
        dustColumnGroundOffset;

    static void define(ModConfigSpec.Builder b) {
        enabled = b.comment("Use SLO visuals for explosions with power of at least 6, and use mesh shockwaves while enabled.")
            .define(PREFIX + "enabled", true);
        fireTicks = integer(b, "fireTicks", "How long the fireball stays luminous, in ticks.", 12, 2, 120);
        fireSmokeTicks = integer(b, "fireSmokeTicks", "How long the fireball lobes persist as cold smoke after cooling, in ticks.", 64, 4, 240);
        fireCount = integer(b, "fireCount", "Number of fireball lobes at full particle density. All start hot and cool as they rise.", 5, 1, 24);
        fireRadius = decimal(b, "fireRadius", "Fireball size multiplier.", 1.0, .1, 4);
        groundFireballLift = decimal(b, "groundFireballLift", "Ground fireball rise distance multiplier.", 1, 0, 3);
        groundFireballRiseBias = decimal(b, "groundFireballRiseBias", "Extra initial rise speed at the same final height.", .8, 0, 1.2);
        groundFireballWidth = decimal(b, "groundFireballWidth", "Ground fireball cluster width multiplier.", 1, .4, 2.5);
        groundFireballBirthSize = decimal(b, "groundFireballBirthSize", "Starting size as a fraction of the ground fireball base size.", .36, .1, 1);
        groundFireballGrowTime = decimal(b, "groundFireballGrowTime", "Time to grow out of the starting size, relative to the luminous duration.", 1, .1, 2);
        groundFireballExpansion = decimal(b, "groundFireballExpansion", "Additional lifetime growth relative to the base size.", .96, 0, 3);
        fireOpacity = decimal(b, "fireOpacity", "Fireball opacity. Zero hides the fireball.", 1.0, 0, 1);
        smokeCount = integer(b, "smokeCount", "Number of large cold puffs at full particle density.", 9, 0, 96);
        smokeTicks = integer(b, "smokeTicks", "Base lifetime of the main smoke in ticks.", 74, 8, 240);
        smokeRadius = decimal(b, "smokeRadius", "Main smoke initial size multiplier.", 1.0, .1, 4);
        smokeSpread = decimal(b, "smokeSpread", "Horizontal travel multiplier for the main smoke.", 1.0, 0, 4);
        smokePushSpeed = decimal(b, "smokePushSpeed", "How quickly the main smoke moves through its horizontal travel.", 1.0, .1, 4);
        smokeFadeStart = decimal(b, "smokeFadeStart", "Fraction of the main smoke lifetime before erosion and fading begin.", .62, 0, .95);
        smokeRise = decimal(b, "smokeRise", "Vertical rise multiplier for the main smoke.", 1.0, 0, 4);
        smokeExpansion = decimal(b, "smokeExpansion", "Growth added to the starting smoke size over its lifetime.", 1.35, 0, 4);
        smokeOpacity = decimal(b, "smokeOpacity", "Main smoke initial density. Zero hides the main smoke.", .94, 0, 1);
        groundDustColumnChance = decimal(b, "groundDustColumnChance", "Probability that a ground explosion adds rising dust columns.", .20, 0, 1);
        dustHeightFromDestruction = b.comment("Scale dust column height using the number of blocks actually removed.").define(PREFIX + "dustHeightFromDestruction", true);
        buriedColumnCount = integer(b, "buriedColumnCount", "Dust column count at full particle density.", 4, 0, 16);
        buriedColumnHeight = decimal(b, "buriedColumnHeight", "Dust column reach multiplier.", 1.0, 0.10, 8.0);
        buriedColumnWidth = decimal(b, "buriedColumnWidth", "Dust column thickness multiplier.", 1.0, 0.15, 8.0);
        buriedColumnSpread = decimal(b, "buriedColumnSpread", "Dust column lateral spread multiplier.", 1.0, 0.0, 8.0);
        buriedColumnSpeed = decimal(b, "buriedColumnSpeed", "Dust column launch speed multiplier.", 1.6, 0.25, 12.0);
        buriedColumnOpacity = decimal(b, "buriedColumnOpacity", "Dust column opacity. Zero hides the columns.", 0.94, 0.0, 1.0);
        buriedColumnTicks = integer(b, "buriedColumnTicks", "Dust column lifetime in ticks.", 33, 6, 240);
        dustColumnPuffs = integer(b, "dustColumnPuffs", "Puffs per column.", 6, 2, 16);
        dustColumnBaseWidth = decimal(b, "dustColumnBaseWidth", "Base puff width relative to spacing.", 0.91, 0.05, 3);
        dustColumnTipWidth = decimal(b, "dustColumnTipWidth", "Tip puff width relative to spacing.", 0.44, 0.05, 3);
        dustColumnBend = decimal(b, "dustColumnBend", "Outward curvature during launch.", 0.52, 0, 5);
        dustColumnExpansion = decimal(b, "dustColumnExpansion", "Puff expansion over its lifetime.", 0.82, 0, 6);
        dustColumnSpin = decimal(b, "dustColumnSpin", "Puff spin multiplier after launch.", 0.9, 0, 6);
        dustColumnFadeStart = decimal(b, "dustColumnFadeStart", "Lifetime fraction before fading starts.", 0.72, 0, 0.98);
        dustColumnBuriedAngle = decimal(b, "dustColumnBuriedAngle", "Buried column angle from the outlet axis, degrees.", 28, 0, 80);
        dustColumnGroundAngle = decimal(b, "dustColumnGroundAngle", "Ground column angle from the launch axis, degrees.", 42, 0, 80);
        dustColumnGroundOffset = decimal(b, "dustColumnGroundOffset", "Ground column root offset relative to blast scale.", 1.4, 0, 8);
        trailCount = integer(b, "trailCount", "Number of radial smoke ribbons at full particle density.", 5, 0, 48);
        trailTicks = integer(b, "trailTicks", "Radial smoke ribbon lifetime in ticks.", 33, 6, 120);
        trailWidth = decimal(b, "trailWidth", "Radial smoke ribbon width multiplier.", 1.0, .1, 12);
        trailReach = decimal(b, "trailReach", "Radial smoke ribbon travel multiplier.", 1.0, .1, 8);
        trailOpacity = decimal(b, "trailOpacity", "Radial smoke ribbon opacity.", .64, 0, 1);
        sparkCount = integer(b, "sparkCount", "Number of thin sparks at full particle density.", 14, 0, 128);
        sparkTicks = integer(b, "sparkTicks", "Lifetime of the thin sparks in ticks.", 8, 2, 40);
        sparkWidth = decimal(b, "sparkWidth", "Thin spark width multiplier.", 1.0, .1, 4);
        sparkLength = decimal(b, "sparkLength", "Thin spark length multiplier.", 1.0, .1, 4);
        sparkSpeed = decimal(b, "sparkSpeed", "Thin spark launch speed multiplier.", 1.0, 0, 4);
        debrisCount = integer(b, "debrisCount", "Number of tumbling fragments at full particle density.", 8, 0, 96);
        debrisTicks = integer(b, "debrisTicks", "Lifetime of tumbling fragments in ticks.", 18, 2, 80);
        debrisSize = decimal(b, "debrisSize", "Tumbling fragment size multiplier.", 1.0, .1, 4);
        debrisSpeed = decimal(b, "debrisSpeed", "Tumbling fragment launch speed multiplier.", 1.0, 0, 4);
        debrisTailTicks = integer(b, "debrisTailTicks", "Length of the fragment trail history in ticks. Zero disables the trails.", 4, 0, 8);
        debrisTailOpacity = decimal(b, "debrisTailOpacity", "Opacity of the tapered fragment trails.", .65, 0, 1);
    }

    private static ModConfigSpec.IntValue integer(ModConfigSpec.Builder b, String key, String comment,
                                                  int value, int min, int max) {
        return b.comment(comment).defineInRange(PREFIX + key, value, min, max);
    }

    private static ModConfigSpec.DoubleValue decimal(ModConfigSpec.Builder b, String key, String comment,
                                                     double value, double min, double max) {
        return b.comment(comment).defineInRange(PREFIX + key, value, min, max);
    }
}
