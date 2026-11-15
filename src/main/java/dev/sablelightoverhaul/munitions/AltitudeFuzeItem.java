package dev.sablelightoverhaul.munitions;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

/** Height fuze: detonates on descent through a set world Y. No impact or expiry behaviour. */
public class AltitudeFuzeItem extends FuzeItem {
    public AltitudeFuzeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        int threshold = stack.getOrDefault(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.DEFAULT);
        return AltitudeTrigger.fires(projectile.getY(), projectile.getDeltaMovement().y, threshold);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int height = AltitudeTrigger.clamp(stack.getOrDefault(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.DEFAULT));
            MenuProvider provider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.sablelightoverhaul.altitude_fuze");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                    return AltitudeFuzeMenu.server(id, inventory, stack, hand);
                }
            };
            serverPlayer.openMenu(provider, buf -> {
                buf.writeInt(height);
                ItemStack.STREAM_CODEC.encode(buf, new ItemStack(FlareMunitions.ALTITUDE_FUZE.get()));
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void addExtraInfo(List<Component> tooltip, boolean isSneaking, ItemStack stack) {
        super.addExtraInfo(tooltip, isSneaking, stack);
        int height = stack.getOrDefault(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.DEFAULT);
        MutableComponent info = Component.translatable("item.sablelightoverhaul.altitude_fuze.desc", height)
                .withStyle(ChatFormatting.GRAY);
        tooltip.add(info);
    }
    @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);
        addExtraInfo(tooltip,false,stack);
    }
}
