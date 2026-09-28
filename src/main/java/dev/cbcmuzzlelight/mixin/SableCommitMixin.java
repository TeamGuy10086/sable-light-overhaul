package dev.cbcmuzzlelight.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.SableBuildScope;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelSectionCompiler.CompiledSection;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelSectionCompiler$RenderSection",remap=false)
public abstract class SableCommitMixin {
 @WrapMethod(method="setCompiledSection")
 private void muzzle$commit(CompiledSection section,Operation<Void> original){
  var ticket=SableBuildScope.current();
  if(ticket==null||ticket.section()!=(Object)this){original.call(section);return;}
  if(!ticket.order().commit(ticket.version(),()->original.call(section)) && section!=CompiledSection.EMPTY && section!=CompiledSection.UNCOMPILED)
   Minecraft.getInstance().execute(section::free);
 }
}
