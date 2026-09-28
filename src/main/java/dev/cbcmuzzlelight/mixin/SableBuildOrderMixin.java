package dev.cbcmuzzlelight.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import dev.cbcmuzzlelight.client.*;
import dev.ryanhcode.sable.sublevel.render.fancy.FancySubLevelSectionCompiler.RenderSection;
import dev.ryanhcode.sable.sublevel.render.fancy.task.*;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo @Mixin(targets="dev.ryanhcode.sable.sublevel.render.fancy.task.FancySubLevelTaskScheduler",remap=false)
public abstract class SableBuildOrderMixin {
 @WrapMethod(method="scheduleCompile")
 private void muzzle$request(RenderSection section,RenderChunkRegion region,double distance,Consumer<RenderSection> complete,Operation<Void> original){
  try(var scope=SableBuildScope.enter(SableBuildScope.request(section))){original.call(section,region,distance,complete);}
 }
 @WrapOperation(method="scheduleCompile",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/sublevel/render/fancy/task/FancySubLevelTaskScheduler;schedule(Ldev/ryanhcode/sable/sublevel/render/fancy/task/SubLevelTask;DLjava/lang/Runnable;)V"))
 private void muzzle$worker(FancySubLevelTaskScheduler scheduler,SubLevelTask task,double distance,Runnable complete,Operation<Void> original){
  var ticket=SableBuildScope.current();
  SubLevelTask wrapped=(pack,uploader)->{
   if(ticket.order().obsolete(ticket.version()))return;
   try(var scope=SableBuildScope.enter(ticket);var lights=LightReadScope.enter(ticket.lights())){task.process(pack,uploader);}
  };
  original.call(scheduler,wrapped,distance,complete);
 }
}
