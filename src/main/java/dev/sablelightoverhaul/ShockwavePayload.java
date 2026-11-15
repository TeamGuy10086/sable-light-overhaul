package dev.sablelightoverhaul;

import java.util.List;
import dev.sablelightoverhaul.api.EffectApi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * What an explosion actually was, measured well enough for a client to draw a shockwave from.
 *
 * <p>The KubeJS generator this replaces judged an explosion by the number of blocks its event reports.
 * That number is the count of blocks the blast's rays <em>walked through</em>, which grows with the solid
 * material the blast is buried in, so a shell into a hill and a shell into the air can give the same
 * number for completely different events, and the same shell can give two different numbers depending on
 * what it hit. It conflates the charge with the terrain.
 *
 * <p>Two numbers separate them:
 *
 * <ul>
 *   <li>{@code yield} — {@code Explosion.radius()}. CBC's {@code CustomExplosion} passes its
 *       {@code block_damaging_explosive_power} straight into the vanilla constructor, so this is the
 *       charge itself: the same shell gives the same value anywhere. Falls back to
 *       {@link #DEFAULT_YIELD} for an explosion that has none (a plain vanilla blast).
 *   <li>{@code reach} — how far the blast actually got. Per octant, the distance of the farthest block it
 *       walked through. This is the shape, not the size: it is what makes a blast into a cliff open
 *       sideways and one into a deck open upward, and it is measured, not modelled.
 * </ul>
 *
 * <p>{@code solidity} is derived from both ({@code 1 - carried / capacity}) and says how enclosed the
 * charge was: near 0 in the open air, near 1 deep in rock.
 */
public record ShockwavePayload(ResourceLocation dimension, double x, double y, double z,
                               float yield, float solidity, int tier,
                               float[] reach, int blocks, BlastClass blastClass, int sourceId)
        implements CustomPacketPayload {
    /**
     * Where the charge went off, which decides what the client is allowed to draw there.
     *
     * <p>{@code AIR} means nothing at all is drawn: the KubeJS ring the client port imitates is a ground
     * effect, and a disc of dust hanging in the sky looks wrong. {@code BURIED} keeps the ring's body but
     * not its cloud and spark rings, and {@code GROUND} is the full effect.
     */
    public enum BlastClass { AIR, GROUND, BURIED }
    /**
     * The public API's view of a {@link BlastClass}, and the way back.
     *
     * <p>The two enums exist for one reason -- this record is the wire format and the API is the contract --
     * and they must agree exactly. Both directions therefore live here, in the file that owns {@code
     * BlastClass}, instead of being spelled out again at each end of the network: a third mapping copy is how
     * one side quietly stops matching the other. The API's two extra cases ({@code BLOCK}, {@code ENTITY}) are
     * not explosions, so they travel as {@code AIR}; nothing produces them today.
     */
    public static EffectApi.HitClass toHitClass(BlastClass blastClass) {
        return switch (blastClass) {
            case AIR -> EffectApi.HitClass.AIR;
            case GROUND -> EffectApi.HitClass.GROUND;
            case BURIED -> EffectApi.HitClass.BURIED;
        };
    }

    public static BlastClass toBlastClass(EffectApi.HitClass hitClass) {
        return switch (hitClass) {
            case BURIED -> BlastClass.BURIED;
            case GROUND -> BlastClass.GROUND;
            case AIR, BLOCK, ENTITY -> BlastClass.AIR;
        };
    }
    /** Yield assumed when an explosion carries none: the smallest charge worth a shockwave. */
    public static final float DEFAULT_YIELD = 6.0F;
    /** Octants a {@link #reach} array covers, in the order {@link #octant} produces. */
    public static final int OCTANTS = 8;
    /** What the yield is compared against, so a blast with no power of its own still has one. */
    public static final float FALLBACK_BELOW = 0.05F;

    public static final Type<ShockwavePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "shockwave"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShockwavePayload> CODEC = StreamCodec.of(
        (buf, value) -> {
            buf.writeResourceLocation(value.dimension);
            buf.writeDouble(value.x); buf.writeDouble(value.y); buf.writeDouble(value.z);
            buf.writeFloat(value.yield); buf.writeFloat(value.solidity);
            buf.writeVarInt(value.tier);
            for (float r : value.reach) buf.writeFloat(r);
            buf.writeVarInt(value.blocks);
            buf.writeByte(value.blastClass.ordinal());
            buf.writeVarInt(value.sourceId);
        },
        buf -> {
            ResourceLocation dimension = buf.readResourceLocation();
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
            float yield = buf.readFloat(), solidity = buf.readFloat();
            int tier = buf.readVarInt();
            float[] reach = new float[OCTANTS];
            for (int i = 0; i < OCTANTS; i++) reach[i] = buf.readFloat();
            int blocks = buf.readVarInt();
            BlastClass blastClass = BlastClass.values()[Math.min(Math.max(buf.readByte(), 0), BlastClass.values().length - 1)];
            return new ShockwavePayload(dimension, x, y, z, yield, solidity, tier, reach, blocks, blastClass, buf.readVarInt());
        });
    @Override public Type<ShockwavePayload> type() { return TYPE; }

    /** The centre as a vector, for callers that think in vectors. */
    public Vec3 center() {
        return new Vec3(x, y, z);
    }

    /** The largest reach of any octant: how far the wave has to travel. */
    public float reachMax() {
        float max = 0.0F;
        for (float r : reach) max = Math.max(max, r);
        return max;
    }

    /** The mean reach, which is the radius a sphere approximating this blast would have. */
    public static float reachMean(float[] reach) {
        float total = 0.0F;
        for (float r : reach) total += r;
        return total / Math.max(1, reach.length);
    }

    /**
     * Which octant a block belongs to, mirroring the order used to build {@link #reach}.
     *
     * <p>A block only belongs to an octant when it has a sign on <b>every</b> axis. Treating a zero
     * component as positive would put the whole of the +x axis into the +x+y+z octant and leave +x-y-z
     * empty, which is exactly the asymmetry this measurement exists to avoid; a block sitting on an axis
     * belongs to no octant, and the octant it would have landed in keeps the floor value instead.
     */
    public static int octant(double dx, double dy, double dz) {
        if (dx == 0.0 || dy == 0.0 || dz == 0.0) return -1;
        int index = 0;
        if (dx > 0) index |= 1;
        if (dy > 0) index |= 2;
        if (dz > 0) index |= 4;
        return index;
    }

    /**
     * Measures one explosion from the blocks its rays walked through.
     *
     * <p>This does not re-run the blast: vanilla has already cast its 1352 rays and applied each block's
     * explosion resistance, so the farthest block per octant is the answer to "how far did it get in that
     * direction", which is the number that would be expensive to recompute and is free to read.
     *
     * @param yield the charge, from {@code Explosion.radius()}
     * @param blocks every block the blast reported
     * @param center where it went off
     * @return eight reaches, in {@link #octant} order
     */
    public static float[] measure(Vec3 center, float yield, List<BlockPos> blocks) {
        // Two guards against nonsense, both learned from the field. A hit on something indestructible
        // reports no blocks at all, so the octants are synthesised from the charge instead of coming back
        // as zero -- otherwise the light derived from them is a one-block dot. And a block list can carry
        // positions from another coordinate space entirely (a Sable sub-level's plot lies far outside the
        // world), which turns a mean reach into millions of blocks and, with it, the light's radius.
        //
        // The second guard is this cap, and it only works if it is applied to every block. It was computed
        // and then never used, and on 2026-10-09 a live log showed what that costs:
        //   reach[min/mean/max] = 3.59 / 3641626.00 / 29132942.00
        // for an explosion that had touched a ship. The mean reach is the light's size, and the profile's
        // maximum range is 64 blocks, so every explosion's light sat at that ceiling: a power 8 shell and a
        // power 24 shell lit the same area, which is exactly what the owner reported.
        float cap = Math.max(8.0F, yield * 3.0F);
        float[] reach = new float[OCTANTS];
        // An octant with no blocks at all still needs a value: the blast reached nothing there, but a wave
        // that collapsed to zero in one direction would look broken rather than blocked, so it is pinned to
        // a fraction of the charge.
        float floor = Math.max(0.5F, yield * 0.2F);
        java.util.Arrays.fill(reach, floor);
        for (BlockPos pos : blocks) {
            // Measured to the block's centre, and a block straddling an axis belongs to no octant.
            double dx = pos.getX() + 0.5 - center.x;
            double dy = pos.getY() + 0.5 - center.y;
            double dz = pos.getZ() + 0.5 - center.z;
            int index = octant(dx, dy, dz);
            if (index < 0) continue;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            // A ray of this charge is at most about 1.3 times the charge long, so everything a real blast
            // can reach is inside the cap; anything past it came from another coordinate space and is not
            // part of this measurement.
            if (distance > cap) continue;
            if (distance > reach[index]) reach[index] = distance;
        }
        return reach;
    }

    /**
     * How much of the charge's reach the terrain actually absorbed, from 0 (open air) to 1 (enclosed).
     *
     * <p>The denominator is the radius a blast of this charge reaches when nothing is in the way, which is
     * the charge itself: vanilla's rays lose {@code 0.3} per block of travel and start at the charge, so an
     * unobstructed blast reaches about {@code yield} blocks. Air does not drain them at all.
     */
    public static float solidity(float yield, float[] reach) {
        double unimpeded = Math.max(0.5, yield);
        double total = 0.0;
        for (float r : reach) total += Math.min(r, unimpeded);
        double mean = total / Math.max(1, reach.length);
        double ratio = mean / unimpeded;
        return (float) Math.max(0.0, Math.min(1.0, 1.0 - ratio));
    }
}
