package dev.cbcmuzzlelight.lighting;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Opens the spotlight's page, and says what the lamp is set to when the page toolkit is missing.
 *
 * <p>The toolkit is optional, so the reference to the page class is kept behind a mod-loaded check:
 * without it a player still gets the current settings in chat instead of a dead click.
 */
public final class SpotlightUi {
 private SpotlightUi(){}
 public static boolean open(Player player,BlockPos pos){
  boolean ldlib=net.neoforged.fml.ModList.get().isLoaded("ldlib2");
  boolean opened=false;
  if(ldlib){
   try{
    opened=SpotlightLdlib.open(player,pos);
   }catch(Throwable problem){
    com.mojang.logging.LogUtils.getLogger().error("CBC Muzzle Light spotlight page failed to open",problem);
   }
  }
  boolean hasLamp=player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity;
  if(LightingCommon.GEN_DEBUG.get())com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light spotlight ui: ldlib2={} opened={} blockEntity={}",ldlib,opened,hasLamp);
  if(opened)return true;
  if(!(player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity lamp))return false;
  if(!player.level().isClientSide){
   // Every value comes from the block entity, so the line reads exactly like the page would.
   player.displayClientMessage(Component.literal("红石射灯：控制 "+mode(lamp)+" / 开关 "+onOff(lamp.number("switch")!=0)
     +" / 锁存 "+onOff(lamp.number("latch")!=0)
     +" / 强度 "+whole(lamp,"intensity")+" / 光锥张角 "+whole(lamp,"width")+" / 颜色 "+whole(lamp,"color")
     +" / 朝向 仰角 "+decimal(lamp,"angle")+" 方位 "+decimal(lamp,"azimuth")
     +" / 红石扫描 基准 "+decimal(lamp,"sweepBase")+" 幅度 "+decimal(lamp,"sweepRange")+" / 距离 "+whole(lamp,"range")+" / 衰减 "+whole(lamp,"falloff")
     +" / 模型 "+(lamp.number("visible")!=0?"显示":"隐藏")),false);
  }
  return true;
 }
 /** 0 全红石 / 1 半红石 / 2 手动, named rather than numbered so the one line stays readable. */
 private static String mode(SpotlightBlockEntity lamp){
  return switch((int)lamp.number("control")){
   case SpotlightBlockEntity.FULL->"0 全红石";
   case SpotlightBlockEntity.SEMI->"1 半红石";
   case SpotlightBlockEntity.MANUAL->"2 手动";
   default->"未知";
  };
 }
 private static String onOff(boolean value){return value?"开":"关";}
 private static String whole(SpotlightBlockEntity lamp,String field){return Long.toString(Math.round(lamp.number(field)));}
 private static String decimal(SpotlightBlockEntity lamp,String field){
  return String.format(java.util.Locale.ROOT,"%.2f",lamp.number(field));
 }
}
