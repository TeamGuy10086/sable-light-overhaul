package dev.sablelightoverhaul.client;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import dev.sablelightoverhaul.munitions.AltitudeFuzeMenu;
import dev.sablelightoverhaul.munitions.AltitudeTrigger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import rbasamoyai.createbigcannons.index.CBCGuiTextures;
import rbasamoyai.createbigcannons.munitions.fuzes.AbstractFuzeScreen;
/** CBC's native fuze screen, texture, scrolling widget, confirmation and update packet. */
public final class AltitudeFuzeScreen extends AbstractFuzeScreen<AltitudeFuzeMenu> {
 public AltitudeFuzeScreen(AltitudeFuzeMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
 @Override protected ScrollInput getScrollInput(){
  return new ScrollInput(leftPos+36,topPos+29,102,18).withRange(AltitudeTrigger.MIN,AltitudeTrigger.MAX+1)
   .withShiftStep(20).calling(value->{lastUpdated=0;setValue.titled(Component.translatable("sablelightoverhaul.altitude_fuze.height",value));})
   .setState(AltitudeTrigger.clamp(menu.getValue()));
 }
 @Override public int getUpdateState(){return setValue.getState();}
 @Override protected void renderBg(GuiGraphics graphics,float partialTicks,int mouseX,int mouseY){
  RenderSystem.setShaderColor(1,1,1,1);
  CBCGuiTextures.PROXIMITY_FUZE_BG.render(graphics,leftPos,topPos);
  graphics.drawCenteredString(font,title,leftPos+imageWidth/2-4,topPos+3,0xFFFFFF);
  int x=(int)Math.round(100.0*(setValue.getState()-AltitudeTrigger.MIN)/(AltitudeTrigger.MAX-AltitudeTrigger.MIN));
  CBCGuiTextures.PROXIMITY_FUZE_SELECTOR.render(graphics,leftPos+32+x,topPos+21);
  graphics.drawCenteredString(font,Component.translatable("sablelightoverhaul.altitude_fuze.height",setValue.getState()),leftPos+85,topPos+48,0xFFFFFF);
  graphics.pose().pushPose();graphics.pose().translate(leftPos+185,topPos+26,0);graphics.pose().scale(3,3,3);
  graphics.renderItem(menu.getStackToRender(),0,0);graphics.pose().popPose();
 }
}
