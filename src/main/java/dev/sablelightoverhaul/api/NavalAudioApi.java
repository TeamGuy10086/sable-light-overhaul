package dev.sablelightoverhaul.api;

import dev.sablelightoverhaul.NavalSoundPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Optional server-thread integration for Sable Hostile's existing report dispatcher. */
public final class NavalAudioApi {
    private NavalAudioApi() {}

    /**
     * Offers a report to a single eligible viewer. True means the caller must skip its report for this
     * viewer, even if their local audio settings or resource pack ultimately make the event silent.
     * Call once from the firing path, with a world-space muzzle and the registered weapon ID.
     */
    public static boolean tryReport(ServerPlayer viewer, ResourceLocation weapon, Vec3 muzzle) {
        var profile = NavalSoundPayload.Profile.forWeapon(weapon);
        int limit = NavalSoundPayload.REPORT_RANGE + 16;
        if (profile == null || !NavalSoundPayload.validPosition(muzzle) || viewer.distanceToSqr(muzzle) > limit * limit
                || !viewer.connection.hasChannel(NavalSoundPayload.TYPE)) return false;
        PacketDistributor.sendToPlayer(viewer, new NavalSoundPayload(viewer.serverLevel().dimension().location(),
            muzzle, NavalSoundPayload.Kind.REPORT, profile));
        return true;
    }

}
