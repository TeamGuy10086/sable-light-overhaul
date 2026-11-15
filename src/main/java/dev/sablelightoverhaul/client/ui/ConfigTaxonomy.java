package dev.sablelightoverhaul.client.ui;

import dev.sablelightoverhaul.HostileSmokeWeapon;
import dev.sablelightoverhaul.client.HostileSmokeConfig;
import java.util.List;

/**
 * Where a key belongs, in the three-level shape the config map describes: category, section, topic.
 *
 * <p><b>Why this is code and not a list of keys.</b> The map has four hundred keys; a table that names every
 * one of them would be stale after the next key, and the same table has to serve the toml page, the block
 * pages and the catalogue export. So the rule is about the key's path, and a new key in an existing section
 * falls into place without editing this class.
 *
 * <p><b>Why the categories are a fixed list.</b> Ordering is a design decision: the player looks for "the
 * cannon's smoke" before "diagnostics", so the list order is the navigation order. A key whose path matches
 * no rule is not silently dropped; it lands in "其它", which the catalogue test treats as a failure to fix.
 */
public final class ConfigTaxonomy {
    public static final List<String> CATEGORIES = List.of(
            "炮口特效", "弹着特效", "弹道特效", "光源", "大气与云",
            "粒子", "性能预算", "世界与超视距", "方块与装置", "系统", HostileSmokeConfig.CATEGORY);

    /** One key's place. {@code topic} is the third level; it may equal the section when the section has one topic. */
    public record Part(String category, String section, String topic) {}

    private static final String OTHER = "其它";

    private static final java.util.Map<String, List<String>> SECTIONS = java.util.Map.of(
            "炮口特效", List.of("机炮", "坦克炮", "大炮", "共用"),
            "弹着特效", List.of("AP 火花与跳弹", "爆炸与冲击波"),
            "弹道特效", List.of("曳光", "炮弹亮线", "导弹烟尾迹", "喂送与调试"),
            "光源", List.of("光照后端", "阴影", "点光源", "射灯", "迪斯科球", "手电筒", "照明弹与信号弹"),
            "大气与云", List.of("云光照", "云外观与雾", "粒子风场"),
            "粒子", List.of("粒子排序", "LOD 遮挡", "地面尘外观", "车体尘幕"),
            "性能预算", List.of("客户端预算", "服务端发送范围"),
            "世界与超视距", List.of("超视距实体", "Voxy / LSS", "物理体补画"),
            "方块与装置", List.of("生成器·共用", "生成器·尘", "生成器·烟"),
            "系统", List.of("逐炮档案", "诊断"));

    private ConfigTaxonomy() {}

    static HostileSmokeWeapon hostileWeapon(String key) {
        int start = HostileSmokeConfig.PREFIX.length();
        return HostileSmokeWeapon.resolve(key.substring(start, key.indexOf('.', start)), false);
    }

    public static int rank(String category) {
        int index = CATEGORIES.indexOf(category);
        return index < 0 ? CATEGORIES.size() : index;
    }

    /** The order sections appear inside one category; unknown sections sort last rather than vanish. */
    public static int sectionRank(String category, String section) {
        if (category.equals(HostileSmokeConfig.CATEGORY)) {
            for (var weapon : HostileSmokeWeapon.values()) if (weapon.title().equals(section)) return weapon.ordinal();
        }
        int index = SECTIONS.getOrDefault(category, List.of()).indexOf(section);
        return index < 0 ? 100 : index;
    }

    public static Part of(String key) {
        if (key.startsWith(HostileSmokeConfig.PREFIX)) {
            var weapon = hostileWeapon(key);
            return new Part(HostileSmokeConfig.CATEGORY, weapon.title(), weapon.thin() ? "机炮薄烟" : "大炮热烟");
        }
        if (key.startsWith("navalAudio.")) {
            String topic = key.startsWith("navalAudio.impact") ? "命中与跳弹" : "发射";
            return new Part(HostileSmokeConfig.CATEGORY, "舰炮音效", topic);
        }
        String category;
        String section;
        if (has(key,"flares")) {
            category="光源"; section="照明弹与信号弹";
        } else if (has(key, "lighting") || has(key, "shadows")) {
            category = "光源";
            section = has(key, "lighting") ? "光照后端" : "阴影";
        } else if (has(key, "staticSources") || has(key, "spotlights") || has(key, "disco")
                || has(key, "flashlight")) {
            category = "光源";
            if (has(key, "staticSources.colors") || has(key, "staticSources.spotlight")) section = "点光源";
            else if (has(key, "staticSources")) section = "点光源";
            else if (has(key, "spotlights")) section = "射灯";
            else if (has(key, "disco")) section = "迪斯科球";
            else section = "手电筒";
        } else if (has(key, "tracerLight")) {
            category = "弹道特效"; section = "曳光";
        } else if (has(key, "projectileStreak")) {
            category = "弹道特效"; section = "炮弹亮线";
        } else if (has(key, "flakSmoke")) {
            category = "弹着特效"; section = "高射爆点烟雾";
        } else if (has(key, "flakCone")) {
            category = "弹着特效"; section = "高射弹锥形伤害";
        } else if (has(key, "trail")) {
            category = "弹道特效"; section = "导弹烟尾迹";
        } else if (has(key, "tracerFeed")) {
            category = "弹道特效"; section = "喂送与调试";
        } else if (has(key, "apSparks")) {
            category = "弹着特效"; section = "AP 火花与跳弹";
        } else if (has(key, "shockwave") || has(key, "explosions")) {
            category = "弹着特效"; section = "爆炸与冲击波";
        } else if (key.startsWith("wind.")) {
            category = "大气与云"; section = "粒子风场";
        } else if (has(key, "clouds")) {
            category = "大气与云";
            section = key.equals("clouds.nearCull") || key.equals("clouds.fogScale") ? "云外观与雾" : "云光照";
        } else if (key.startsWith("particles.sort") || has(key, "particles.depthSort")) {
            category = "粒子"; section = "粒子排序";
        } else if (key.startsWith("particles.occlusion")) {
            category = "粒子"; section = "LOD 遮挡";
        } else if (has(key, "particles.groundDust")) {
            category = "粒子"; section = "地面尘外观";
        } else if (has(key, "hullDust") || has(key,"particles.hullDust")) {
            category = "粒子"; section = "车体尘幕";
        } else if (has(key, "particles.trackedPerShot") || has(key, "particles.hotLifetimeFraction")) {
            category = "炮口特效"; section = "共用";
        } else if (key.equals("particles.muzzle.flameRenderer")) {
            category = "炮口特效"; section = "共用";
        } else if (has(key, "particles.muzzle.autocannon") || has(key, "lightProfile.autocannon")
                || has(key, "muzzle.autocannon")) {
            category = "炮口特效"; section = "机炮";
        } else if (has(key, "muzzle.tankCannon") || has(key,"particles.muzzle.tankCannon") || has(key,"lightProfile.bigCannon.tankCannon")) {
            category = "炮口特效"; section = "坦克炮";
        } else if (has(key, "particles.muzzle.bigCannon") || has(key, "lightProfile.bigCannon")
                || has(key, "muzzle.bigCannon")) {
            category = "炮口特效"; section = "大炮";
        } else if (has(key, "haze")) {
            category = "炮口特效"; section = "共用";
        } else if (has(key, "generators.dust")) {
            category = "方块与装置"; section = "生成器·尘";
        } else if (has(key, "generators.smoke")) {
            category = "方块与装置"; section = "生成器·烟";
        } else if (has(key, "generators")) {
            category = "方块与装置"; section = "生成器·共用";
        } else if (has(key, "budgets")) {
            category = "性能预算"; section = "客户端预算";
        } else if (has(key, "particles") && isServerSendDistance(key)) {
            category = "性能预算"; section = "服务端发送范围";
        } else if (has(key, "voxy")) {
            category = "世界与超视距"; section = "Voxy / LSS";
        } else if (has(key, "farEntities") || has(key, "farContacts")) {
            category = "世界与超视距"; section = "超视距实体";
        } else if (has(key, "subLevels")) {
            category = "世界与超视距"; section = "物理体补画";
        } else if (has(key, "profiles")) {
            category = "系统"; section = "逐炮档案";
        } else if (has(key, "debug")) {
            category = "系统"; section = "诊断";
        } else {
            return new Part(OTHER, OTHER, OTHER);
        }
        return new Part(category, section, topic(key, category, section));
    }

    private static String topic(String key, String category, String section) {
        String last = key.substring(key.lastIndexOf('.') + 1);
        switch (category) {
            case "炮口特效" -> {
                if (key.contains(".thermalSmoke.")) return "大炮热烟团";
                if (key.contains(".afterSmoke.")) return "炮口余烟";
                if (key.startsWith("lightProfile.")) return "光曲线（范围/时间/缩放）";
                if (key.contains(".plumeAlpha.")) return "炮口烟透明度";
                if (key.contains(".jet.")) return "前向拉伸";
                if (key.contains(".cloud.")) return "膨胀云";
                if (key.contains(".burst.")) return "爆散烟";
                if (key.contains(".plume.")) return "燃气烟柱";
                if (key.contains(".fireball.")) return "炮口火球";
                if (key.contains(".groundDust.")) return "地面扬尘";
                if (key.contains(".hullDust.")) return "车体扬尘";
                if (key.contains(".breechSmoke.")) return "炮尾烟";
                if (has(key, "particles.muzzle")) return "炮口粒子";
                if (has(key, "haze")) {
                    return switch (last) {
                        case "muzzleSpread", "muzzleDrift", "muzzleSink", "muzzleClearance",
                             "muzzlePush", "muzzlePushRadius", "lightGain" -> "薄烟运动与受光";
                        default -> "薄烟外观与配额";
                    };
                }
                if (last.equals("enabled") || last.equals("plumeScale") || last.equals("particlesPerShot")) {
                    return "总开关与规模";
                }
                if (last.equals("trackedPerShot") || last.equals("hotLifetimeFraction")) return "光跟烟";
                return OTHER;
            }
            case "弹着特效" -> {
                if (section.equals("AP 火花与跳弹")) {
                    if (key.contains(".hit.")) return "命中";
                    if (key.contains(".ricochet.")) return "跳弹";
                    if (key.contains(".bigShells.")) return "巨炮弹体";
                    return "总开关与颜色";
                }
                if (last.toLowerCase(java.util.Locale.ROOT).contains("ring")) return "地面尘环";
                if (has(key, "explosions.light")) return "爆炸闪光";
                if (has(key, "explosions.shockwave")) return "爆炸数据";
                if (has(key, "explosions")) {
                    return switch (last) {
                        case "minimumBlocks", "minimumPower", "yieldTiers", "airBand", "blastGroundBand",
                             "blastBuriedDepth", "blastBuriedSolidity", "blastDownUpRatio" -> "判定与分级";
                        default -> "爆炸光/外观";
                    };
                }
                if (last.equals("shaderWave") || last.equals("refract") || last.equals("dispersion")) return "合成与折射";
                if (last.equals("enabled") || last.equals("mode")) return "总开关与画法";
                return "波面外观";
            }
            case "弹道特效" -> {
                if (section.equals("曳光")) {
                    if (key.contains(".autocannon.")) return "自动炮";
                    if (key.contains(".bigCannon.")) return "大型炮";
                    return "共用";
                }
                if (section.equals("尾迹")) return "尾迹粒子";
                return "喂送与调试";
            }
            case "光源" -> {
                if (section.equals("光照后端")) {
                    return key.contains(".color.") ? "GPU 光路色调" : "光路与档位";
                }
                if (section.equals("阴影")) {
                    if (key.contains(".classes.")) return "类别授权";
                    if (last.equals("count") || last.equals("countAuto")) return "总开关";
                    return "质量与距离";
                }
                if (section.equals("点光源")) {
                    if (key.contains(".colors.")) return "颜色";
                    if (key.contains(".spotlight.")) return "距离档位";
                    return "登记与接管";
                }
                if (section.equals("照明弹与信号弹")) return "远距辉光";
                if (section.equals("射灯")) return key.contains("beam") ? "体积光" : "距离档位";
                if (section.equals("迪斯科球")) {
                    if (last.equals("colors") || last.equals("beams")) return "颜色与光束";
                    if (last.equals("control") || last.equals("latched") || last.equals("channel")) return "控制";
                    return "光锥与旋转";
                }
                if (last.equals("pointRadius") || last.equals("pointIntensity")) return "手持点光";
                if (last.equals("color")) return "颜色";
                return "光锥与衰减";
            }
            case "大气与云" -> {
                if (section.equals("云外观与雾")) return "雾与贴脸渐隐";
                if (last.equals("enabled") || last.equals("dynamic") || last.equals("spotlights")
                        || last.equals("intensity")) return "光照开关与强度";
                if (last.equals("phase") || last.equals("multiScatter") || last.equals("integration")) return "质量档";
                return "触达与衰减";
            }
            case "粒子" -> {
                return switch (section) {
                    case "粒子排序" -> "排序与开销";
                    case "LOD 遮挡" -> "遮挡策略";
                    case "地面尘外观" -> "贴图帧与旋转";
                    default -> "车体尘幕实验";
                };
            }
            case "性能预算" -> {
                return section.equals("客户端预算") ? "客户端预算" : "发送距离";
            }
            case "世界与超视距" -> {
                return switch (section) {
                    case "Voxy / LSS" -> "重推与节流";
                    case "超视距实体" -> "补画规则";
                    default -> "补画开关";
                };
            }
            case "方块与装置" -> {
                return switch (section) {
                    case "生成器·共用" -> "开关与节奏";
                    case "生成器·尘" -> "尘的参数";
                    default -> "烟的程序";
                };
            }
            case "系统" -> {
                return section.equals("逐炮档案") ? "总开关" : "诊断日志";
            }
            default -> {
                return OTHER;
            }
        }
    }

    private static boolean isServerSendDistance(String key) {
        String last = key.substring(key.lastIndexOf('.') + 1);
        return switch (last) {
            case "generatorCull", "forceLoad", "muzzleCull", "shockwaveRange",
                 "tracerFeedRange", "explosionCull" -> true;
            default -> false;
        };
    }

    private static boolean has(String key, String prefix) {
        return key.equals(prefix) || key.startsWith(prefix + ".");
    }
}
