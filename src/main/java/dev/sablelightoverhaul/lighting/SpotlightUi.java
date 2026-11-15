package dev.sablelightoverhaul.lighting;

import dev.sablelightoverhaul.LocalizedText;
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
    com.mojang.logging.LogUtils.getLogger().error("Sable Light Overhaul spotlight page failed to open",problem);
   }
  }
  boolean hasLamp=player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity;
  if(LightingCommon.on(LightingCommon.GEN_DEBUG))com.mojang.logging.LogUtils.getLogger().info("Sable Light Overhaul spotlight ui: ldlib2={} opened={} blockEntity={}",ldlib,opened,hasLamp);
  if(opened)return true;
  if(!(player.level().getBlockEntity(pos) instanceof SpotlightBlockEntity lamp))return false;
  if(!player.level().isClientSide){
   // Every value comes from the block entity, so the line reads exactly like the page would.
   player.displayClientMessage(LocalizedText.component("红石射灯：控制 %s / 开关 %s / 锁存 %s / 强度 %s / 光锥张角 %s（%s°~%s°） / 颜色 %s / 朝向 仰角 %s 方位 %s / 红石扫描 基准 %s 幅度 %s / 距离 %s / 衰减 %s / 模型 %s",
     mode(lamp),onOff(lamp.number("switch")!=0),onOff(lamp.number("latch")!=0),
     whole(lamp,"intensity"),whole(lamp,"width"),decimal(lamp,"widthMin"),decimal(lamp,"widthMax"),
     whole(lamp,"color"),decimal(lamp,"angle"),decimal(lamp,"azimuth"),
     decimal(lamp,"sweepBase"),decimal(lamp,"sweepRange"),whole(lamp,"range"),whole(lamp,"falloff"),
     LocalizedText.component(lamp.number("visible")!=0?"显示":"隐藏")),false);
  }
  return true;
 }
 /** 0 全红石 / 1 半红石 / 2 手动, named rather than numbered so the one line stays readable. */
 private static Component mode(SpotlightBlockEntity lamp){
  return switch((int)lamp.number("control")){
   case SpotlightBlockEntity.FULL->LocalizedText.component("0 全红石");
   case SpotlightBlockEntity.SEMI->LocalizedText.component("1 半红石");
   case SpotlightBlockEntity.MANUAL->LocalizedText.component("2 手动");
   case SpotlightBlockEntity.CHANNEL->LocalizedText.component("3 AFN信道");
   default->LocalizedText.component("未知");
  };
 }
 private static Component onOff(boolean value){return LocalizedText.component(value?"开":"关");}
 private static String whole(SpotlightBlockEntity lamp,String field){return Long.toString(Math.round(lamp.number(field)));}
 private static String decimal(SpotlightBlockEntity lamp,String field){
  return String.format(java.util.Locale.ROOT,"%.2f",lamp.number(field));
 }
}
