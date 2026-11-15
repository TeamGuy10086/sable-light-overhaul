package dev.sablelightoverhaul.mixin;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional integration: only applied when Create Big Cannons is installed, with no class reference to it from here. */
public final class CbcMixinPlugin implements IMixinConfigPlugin {
    private static Boolean present;

    private static boolean cbc() {
        if (present == null) {
            try {
                present = ModList.get() != null && ModList.get().isLoaded("createbigcannons");
            } catch (Throwable missing) {
                present = false;
            }
        }
        return present;
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        // Neither ModList (not populated yet when mixins are applied) nor Class.forName (which loads the class, and
        // with it its superclass -- CBC's explosions extend net.minecraft.world.level.Explosion, and loading that early
        // makes every other mod's mixin on it fail with MixinTargetAlreadyLoadedException). Asking the class loader for
        // the resource answers the same question without defining anything.
        boolean apply = CbcMixinPlugin.class.getClassLoader()
                .getResource("rbasamoyai/createbigcannons/CreateBigCannons.class") != null;
        org.slf4j.LoggerFactory.getLogger("sablelightoverhaul").info("SLO cbc plugin: {} -> {}", target, apply);
        return apply;
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}