package dev.sablelightoverhaul.munitions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import rbasamoyai.createbigcannons.munitions.fuzes.AbstractFuzeContainer;

/** Server-authoritative altitude fuze editor bound to one exact stack in one exact hand. */
public class AltitudeFuzeMenu extends AbstractFuzeContainer {
    private final Player player;
    private final ItemStack boundStack;
    private final InteractionHand hand;

    public static AltitudeFuzeMenu server(int id, Inventory inventory, ItemStack stack, InteractionHand hand) {
        return new AltitudeFuzeMenu(id, containerData(inventory.player, stack, hand), stack, inventory.player, stack, hand);
    }

    public static AltitudeFuzeMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        SimpleContainerData data = new SimpleContainerData(1);
        data.set(0, buf.readInt());
        ItemStack stack = ItemStack.STREAM_CODEC.decode(buf);
        return new AltitudeFuzeMenu(id, data, stack, inventory.player, ItemStack.EMPTY, null);
    }

    private AltitudeFuzeMenu(int id, ContainerData data, ItemStack renderStack, Player player, ItemStack boundStack,
                             InteractionHand hand) {
        super(FlareMunitions.ALTITUDE_MENU.get(), id, data, renderStack);
        this.player = player;
        this.boundStack = boundStack;
        this.hand = hand;
    }

    private boolean isHeld() {
        if (this.hand == null) return false;
        ItemStack held = this.player.getItemInHand(this.hand);
        return held == this.boundStack && held.getItem() instanceof AltitudeFuzeItem;
    }

    private static ContainerData containerData(Player player, ItemStack stack, InteractionHand hand) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index != 0) return 0;
                return stack.getOrDefault(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.DEFAULT);
            }

            @Override
            public void set(int index, int value) {
                if (index != 0) return;
                boolean held = hand != null && player.getItemInHand(hand) == stack && stack.getItem() instanceof AltitudeFuzeItem;
                if (!held) return;
                stack.set(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.clamp(value));
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    public void setValue(int value) {
        if (this.hand != null && this.isHeld()) {
            this.boundStack.set(FlareMunitions.ALTITUDE.get(), AltitudeTrigger.clamp(value));
        }
    }

    @Override
    public boolean stillValid(Player p) {
        if (this.hand == null) return true;
        return this.isHeld() && p == this.player;
    }
}
