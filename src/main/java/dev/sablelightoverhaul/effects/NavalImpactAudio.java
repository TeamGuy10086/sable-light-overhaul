package dev.sablelightoverhaul.effects;

import dev.sablelightoverhaul.SloNetwork;
import dev.sablelightoverhaul.HostileShellPayload.Model;
import dev.sablelightoverhaul.NavalSoundPayload;
import dev.sablelightoverhaul.NavalSoundPayload.Kind;
import dev.sablelightoverhaul.mixin.CbcArmorRegistryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.block_armor_properties.BlockArmorPropertiesHandler;

/** One sound per selected CBC naval block collision, using the state before the hit changes it. */
public final class NavalImpactAudio {
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> ARMOUR =
        BlockTags.create(ResourceLocation.fromNamespaceAndPath("sablehealthbar", "armour"));

    private NavalImpactAudio() {}

    public enum Surface { NONE, WOOD, ORDINARY, ARMOR }

    public static Surface surface(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return Surface.NONE;
        if (state.is(ARMOUR)) return Surface.ARMOR;
        var sound = state.getSoundType();
        if (state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS)
                || sound == SoundType.WOOD || sound == SoundType.NETHER_WOOD || sound == SoundType.BAMBOO_WOOD) {
            return Surface.WOOD;
        }
        var provider = BlockArmorPropertiesHandler.getProperties(state);
        if (provider == CbcArmorRegistryAccess.slo$fallbackProvider()) return Surface.ORDINARY;
        return provider.hardness(level, state, pos, true) > 0 && provider.toughness(level, state, pos, true) > 0
            ? Surface.ARMOR : Surface.ORDINARY;
    }

    static Kind kind(Model model, Surface surface, boolean bounce) {
        if (surface == Surface.NONE || surface == Surface.WOOD) return null;
        if (bounce) return Kind.RICOCHET;
        if (surface != Surface.ARMOR) return null;
        return switch (model) {
            case HE_76, HE_127, HE_203 -> Kind.ARMOR_HE;
            case AP_76, AP_127, AP_203 -> Kind.ARMOR_AP;
        };
    }

    public static void play(ServerLevel level, Vec3 point, Model model, Surface surface, boolean bounce) {
        var kind = kind(model, surface, bounce);
        if (kind == null || !NavalSoundPayload.validPosition(point)) return;
        var sound = new NavalSoundPayload(level.dimension().location(), point, kind, NavalSoundPayload.Profile.forShell(model));
        int limit = NavalSoundPayload.IMPACT_RANGE + 16;
        for (var player : level.players()) {
            if (player.distanceToSqr(point) <= limit * limit && player.connection.hasChannel(NavalSoundPayload.TYPE)) {
                SloNetwork.sendToPlayer(player, sound);
            }
        }
    }
}
