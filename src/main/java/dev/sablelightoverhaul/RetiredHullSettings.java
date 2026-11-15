package dev.sablelightoverhaul;

import java.util.Set;

/**
 * The hull-dust settings that describe the old constrained hull-surface path.
 *
 * <p>That path no longer takes part in the free world-space smoke, but its keys stay registered so an existing toml
 * file still loads and the server/client snapshots keep their shape. They are marked as disabled in their comments
 * and kept out of the configuration screen so nobody tries to tune a setting that does nothing.
 */
public final class RetiredHullSettings {
    /** Legacy TOML/payload fields cannot re-enable the rejected surface solver. */
    public static final boolean ENABLED = false;
    private static final Set<String> KEYS = Set.of(
            "particles.hullDust.queryBudget",
            "particles.hullDust.skinThickness",
            "particles.hullDust.renderer",
            "particles.hullDust.visualSurface",
            "particles.hullDust.visualReach",
            "particles.hullDust.clusterThickness",
            "muzzle.tankCannon.hullDust.flow.surfaceCoupling",
            "muzzle.tankCannon.hullDust.flow.attachSeconds",
            "muzzle.tankCannon.hullDust.flow.surfaceSpeed",
            "muzzle.tankCannon.hullDust.flow.clearance",
            "muzzle.tankCannon.hullDust.flow.inheritMotion",
            "muzzle.tankCannon.hullDust.flow.detachFadeSeconds",
            "muzzle.tankCannon.hullDust.flow.detachExpansion");

    /** The notice that leads every disabled comment, so the label reads as disabled before the old text. */
    public static final String NOTICE = "【已停用】仅保留旧配置兼容，不参与自由车体烟计算；";

    private RetiredHullSettings() {}

    /** Whether this exact dotted key belongs to the retired constrained path. */
    public static boolean contains(String dotted) {
        return dotted != null && KEYS.contains(dotted);
    }
}
