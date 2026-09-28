package dev.cbcmuzzlelight.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Optional integration, no class initialization or hard dependency on CBCAT. */
public final class CbcatMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        try {
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            if(mixin.endsWith("LambTickMixin")) return node.methods.stream().anyMatch(m -> m.name.equals("shouldTick") && m.desc.equals("(Ldev/lambdaurora/lambdynlights/engine/source/DeferredDynamicLightSource;)Z"));
            if(mixin.endsWith("AdvancedFlywheelMixin")) return node.methods.stream().anyMatch(m -> m.name.equals("collectSection") && m.desc.equals("(JJ)V"))
                    && node.methods.stream().anyMatch(m -> m.name.equals("write") && m.desc.equals("(JIIIII)V"));
            return true;
        }
        catch(ClassNotFoundException | java.io.IOException missing) { return false; }
    }
    @Override public void onLoad(String name) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info) {}
}
