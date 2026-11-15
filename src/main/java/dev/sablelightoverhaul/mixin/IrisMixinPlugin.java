package dev.sablelightoverhaul.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Applies the Iris bridge only when Iris is actually present, decided from bytecode so no Iris class
 * is resolved or initialised here. That keeps Iris a compile-only dependency and keeps dedicated
 * servers free of it.
 */
public final class IrisMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (Boolean.getBoolean("sablelightoverhaul.disableIrisBridge")) return false;
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(target);
            return true;
        } catch (ClassNotFoundException | java.io.IOException missing) {
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
