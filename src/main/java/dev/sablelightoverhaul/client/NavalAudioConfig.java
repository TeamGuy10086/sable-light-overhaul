package dev.sablelightoverhaul.client;

import dev.sablelightoverhaul.NavalSoundPayload;
import net.neoforged.neoforge.common.ModConfigSpec;

final class NavalAudioConfig {
    static ModConfigSpec.BooleanValue REPORTS, IMPACTS;
    static ModConfigSpec.IntValue NEAR, RANGE, DUCK_RANGE;
    static ModConfigSpec.DoubleValue SPEED, REPORT_GAIN, IMPACT_GAIN, DUCK_GAIN;

    private NavalAudioConfig() {}

    static void define(ModConfigSpec.Builder builder) {
        builder.push("navalAudio");
        REPORTS = builder.comment("播放 Sable Hostile 舰炮发射音。").define("reportsEnabled", true);
        NEAR = builder.comment("此距离及以内使用近声，超过后仅使用远声。单位为格。")
            .defineInRange("nearRange", 800, 0, NavalSoundPayload.REPORT_RANGE);
        RANGE = builder.comment("炮声最大可听距离及衰减终点。单位为格。")
            .defineInRange("reportRange", NavalSoundPayload.REPORT_RANGE, 16, NavalSoundPayload.REPORT_RANGE);
        SPEED = builder.comment("声音传播速度，单位为格/秒。0 表示无延迟。")
            .defineInRange("soundSpeed", 343.0, 0.0, 1000.0);
        REPORT_GAIN = builder.comment("发射音音量倍率。").defineInRange("reportGain", 1.0, 0.0, 2.0);
        DUCK_RANGE = builder.comment("近炮压低原版音量的最大距离，0 关闭。单位为格。")
            .defineInRange("duckRange", 48, 0, 128);
        DUCK_GAIN = builder.comment("近炮压低原版音量的强度倍率。")
            .defineInRange("duckGain", 1.0, 0.0, 1.0);
        IMPACTS = builder.comment("播放舰炮实际跳弹与装甲碰撞音。").define("impactsEnabled", true);
        IMPACT_GAIN = builder.comment("舰炮跳弹和装甲碰撞音量倍率。")
            .defineInRange("impactGain", 1.0, 0.0, 2.0);
        builder.pop();
    }
}
