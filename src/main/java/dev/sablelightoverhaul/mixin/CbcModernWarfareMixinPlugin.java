package dev.sablelightoverhaul.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Optional integration: the tank cannon hook only loads when CBC Modern Warfare is installed, and the
 * exact method shape is checked before applying so an update to that mod degrades to "no effects"
 * instead of a crash.
 */
public final class CbcModernWarfareMixinPlugin implements IMixinConfigPlugin {
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        try {
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            if(mixin.endsWith("MediumCannonMixin")) {
                // fireShot(ServerLevel, PitchOrientedContraptionEntity) must still build its plume the
                // same way; without that call site there is nothing to hook and no effects to spawn.
                return node.methods.stream().anyMatch(m -> m.name.equals("fireShot")
                        && m.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lrbasamoyai/createbigcannons/cannon_control/contraption/PitchOrientedContraptionEntity;)V")
                        && m.localVariables!=null
                        && m.localVariables.stream().anyMatch(v -> v.name.equals("spawnPos"))
                        && m.localVariables.stream().anyMatch(v -> v.name.equals("vec1"))
                        && java.util.Arrays.stream(m.instructions.toArray()).filter(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("net/minecraft/server/level/ServerLevel") && call.name.equals("sendParticles")
                            && call.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/particles/ParticleOptions;ZDDDIDDDD)Z")).count()==1);
            }
            if(mixin.endsWith("MediumcannonBreechMixin")) {
                // The breech puff is only corrected where the block still emits it after firing; the
                // sendParticles call site lives in this method, and smokePos proves we found it.
                return node.methods.stream().anyMatch(m -> m.name.equals("onInteractWhileAssembled")
                        && m.localVariables!=null
                        && m.localVariables.stream().anyMatch(v -> v.name.equals("smokePos")));
            }
            if(mixin.endsWith("TankCannonTrailMixin")) {
                // The tank round has no displacement field, so the start point comes from its first
                // tick; that tick must still spawn its trail through exactly one always-visible call.
                return node.methods.stream().anyMatch(m -> m.name.equals("tick") && m.desc.equals("()V")
                        && java.util.Arrays.stream(m.instructions.toArray()).filter(i -> i instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.owner.equals("net/minecraft/world/level/Level") && call.name.equals("addAlwaysVisibleParticle")
                            && call.desc.equals("(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V")).count()==1);
            }
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
