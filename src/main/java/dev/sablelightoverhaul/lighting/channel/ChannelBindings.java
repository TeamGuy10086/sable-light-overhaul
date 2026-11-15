package dev.sablelightoverhaul.lighting.channel;

import net.neoforged.fml.ModList;

/** Makes the binding a block entity holds, without the block entity knowing which kind it got. */
public final class ChannelBindings {
 private ChannelBindings(){}
 /** Whether the channel mod is installed at all. */
 public static boolean available(){
  return ModList.get().isLoaded("create_afn");
 }
 /**
  * A fresh, unbound link.
  *
  * <p>The AFN implementation is named only inside the branch, so with AFN absent the class is never
  * loaded and none of its types are ever resolved.
  */
 public static ChannelBinding create(){
  return available()?new AfnBinding():new EmptyBinding();
 }
 /** Whether a held item is one of the channel mod's frequency boards. */
 public static boolean isBoard(net.minecraft.world.item.ItemStack stack){
  return available()&&AfnBinding.isBoard(stack);
 }
}
