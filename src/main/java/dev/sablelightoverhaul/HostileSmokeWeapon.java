package dev.sablelightoverhaul;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stable Sable Hostile typeId paths. Unknown weapons inherit the template for their event family. */
public enum HostileSmokeWeapon {
    BOW("bow_chaser", "舰艏炮", 16, 21, 5),
    SWIVEL("swivel_muzzle_loader", "旋转前装炮", 8, 12, 3),
    TWIN_37("twin_37mm", "双联 37 mm", 1, 5.0, 0),
    RAIL("railgun", "磁轨炮", 4, 9, 2),
    GAU("gau_30_avenger", "GAU-30 机炮", 1, 3.8, 0),
    RAIL_TURRET("rail_turret", "磁轨炮塔", 4, 12, 2.5),
    BOFORS_TWIN("bofors_40mm_twin", "双联博福斯 40 mm", 1, 5.0, 0),
    BOFORS_QUAD("bofors_40mm_quad", "四联博福斯 40 mm", 1, 5.0, 0),
    OERLIKON("oerlikon_20mm_quad", "四联厄利孔 20 mm", 1, 3.8, 0),
    TWIN_76("twin_76mm", "双联 76 mm", 8, 14, 3.5),
    TWIN_127("twin_127mm", "双联 127 mm", 16, 24, 5.5),
    TRIPLE_203("des_moines_203mm", "三联 203 mm", 40, 55, 12),
    OTHER_THIN("otherAutocannon", "其他机炮", 1, 5, 0),
    OTHER_HEAVY("otherCannon", "其他大炮", 16, 24, 5.5);

    private static final Map<String, HostileSmokeWeapon> BY_ID = Arrays.stream(values())
        .collect(Collectors.toUnmodifiableMap(HostileSmokeWeapon::path, Function.identity()));
    private final String path, title;
    private final int count;
    private final double length, diameter;

    HostileSmokeWeapon(String path, String title, int count, double length, double diameter) {
        this.path = path; this.title = title; this.count = count; this.length = length; this.diameter = diameter;
    }

    public String path() { return path; }
    public String title() { return title; }
    public int count() { return count; }
    public double length() { return length; }
    public double diameter() { return diameter; }
    public boolean thin() { return diameter == 0; }

    public static HostileSmokeWeapon resolve(String path, boolean automatic) {
        return BY_ID.getOrDefault(path, automatic ? OTHER_THIN : OTHER_HEAVY);
    }
}
