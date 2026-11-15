package dev.sablelightoverhaul.client.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import dev.sablelightoverhaul.lighting.*;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** F9's editor hosted by the existing server menu, with its original lifetime and edit validation. */
public final class DeviceConfigWindow extends ConfigWindow implements MenuAccess<ModularUIContainerMenu> {
    private final ModularUIContainerMenu menu;
    public DeviceConfigWindow(ModularUIContainerMenu menu, Inventory inventory, Component title) {
        super(source(menu));
        this.menu = menu;
    }
    private static Source source(ModularUIContainerMenu menu) {
        if (menu.getType() == GeneratorLdlib.MENU.get()) return GeneratorLdlib.configSource(menu);
        if (menu.getType() == FlashlightLdlib.MENU.get()) return FlashlightLdlib.configSource(menu);
        if (menu.getType() == SpotlightLdlib.MENU.get()) return SpotlightLdlib.configSource(menu);
        if (menu.getType() == DiscoBallLdlib.MENU.get()) return DiscoBallLdlib.configSource(menu);
        if (menu.getType() == LightSourceLdlib.MENU.get()) return LightSourceLdlib.configSource(menu);
        if (menu.getType() == ProfileLdlib.MENU.get()) return ProfileLdlib.configSource(menu);
        throw new IllegalArgumentException("Unknown SLO configuration menu: " + menu.getType());
    }
    @Override public ModularUIContainerMenu getMenu() { return menu; }
    @Override public void tick() {
        super.tick();
        // The server owns validity: registered lights on physics bodies may not have a local block entity.
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu != menu) onClose();
    }
    @Override public void onClose() {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu == menu)
            minecraft.player.closeContainer();
        super.onClose();
    }
}
