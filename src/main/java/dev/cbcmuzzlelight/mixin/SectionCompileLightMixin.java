package dev.cbcmuzzlelight.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.cbcmuzzlelight.client.LightReadScope;
import net.minecraft.client.renderer.chunk.*;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.core.SectionPos;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(SectionCompiler.class)
public abstract class SectionCompileLightMixin {
 @WrapMethod(method="compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;")
 private SectionCompiler.Results muzzle$compile(SectionPos pos,RenderChunkRegion region,VertexSorting sorting,SectionBufferBuilderPack buffers,List<?> extra,Operation<SectionCompiler.Results> original){
  try(var ignored=LightReadScope.enter()){return original.call(pos,region,sorting,buffers,extra);}
 }
}
