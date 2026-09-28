package dev.cbcmuzzlelight.lighting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
public final class RegistrarItem extends Item {
 public RegistrarItem(Properties p){super(p);}
 @Override public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack,UseOnContext c){return useOn(c);}
 @Override public InteractionResult useOn(UseOnContext c){
  var clicked=c.getLevel().getBlockState(c.getClickedPos());
  // On a spotlight the tool drives the lamp itself instead of registering a source.
  if(clicked.is(LightingBlocks.SPOTLIGHT.get())){
   var player=c.getPlayer();
   if(player==null) return InteractionResult.FAIL;
   return LightingBlocks.SPOTLIGHT.get().toolUse(clicked,c.getLevel(),c.getClickedPos(),player);
  }
  // A stand-in is registered like any other block. It used to answer the tool with a brightness step,
  // which took the tool's only remaining gesture away from registration: after taking a lamp over there
  // was no way to register it, and the two jobs were crammed onto one click. Brightness moved to the
  // empty hand (see LightSourceBlock.useWithoutItem), so the tool means one thing again.
  if(c.getLevel() instanceof ServerLevel level){var player=c.getPlayer();if(player==null||!player.mayBuild())return InteractionResult.FAIL;
   var a=SourceAnchor.capture(level,c.getClickedPos());var data=RegisteredSources.get(level);
   var state=level.getBlockState(c.getClickedPos());
   if(data.find(a,false)==null&&LightSourceBlock.emissionOf(level,c.getClickedPos(),state)<=0){player.displayClientMessage(Component.literal("这个方块当前不发光。"),true);return InteractionResult.FAIL;}
   if(data.entries.size()>=8192&&data.find(a,false)==null){player.displayClientMessage(Component.literal("登记数量已达8192。"),true);return InteractionResult.FAIL;}
   int mode=data.cycle(a);
   var entry=data.find(a,false);
   // The colour is decided by the block id (config table, then the id's own name) and can be
   // overridden later with a dye. It only ever renders in the Advanced tier.
   int color=entry==null?SourceColor.UNTINTED:entry.color()<0?LightingCommon.colorOf(state.getBlock()):entry.color();
   String colorText=entry==null?"":"，颜色："+SourceColor.name(color);
   player.displayClientMessage(Component.literal("静态光源："+new String[]{"取消登记","启用","强制启用"}[mode]+colorText),true);
  }return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
 }
}
