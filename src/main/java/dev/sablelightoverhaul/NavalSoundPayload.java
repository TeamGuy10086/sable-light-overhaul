package dev.sablelightoverhaul;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** The server selects a report profile or measures a real block collision. The client selects the sample. */
public record NavalSoundPayload(ResourceLocation dimension, Vec3 position, Kind kind, Profile profile)
        implements CustomPacketPayload {
    public static final int REPORT_RANGE = 2400;
    public static final int IMPACT_RANGE = 192;
    public static final Type<NavalSoundPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "naval_sound"));

    public enum Kind { REPORT, ARMOR_HE, ARMOR_AP, RICOCHET }

    public enum Profile {
        MM20("20mm", .25f), MM37("37mm", .25f), MM40("40mm", .25f),
        MM76("76mm", .45f), MM127("127mm", .65f), MM203("203mm", .65f);

        private final String caliber;
        private final float duckDepth;

        Profile(String caliber, float duckDepth) {
            this.caliber = caliber;
            this.duckDepth = duckDepth;
        }

        public float duckDepth() { return duckDepth; }

        public ResourceLocation reportEvent(boolean far) {
            return ResourceLocation.fromNamespaceAndPath("sablehostile", "naval." + caliber + (far ? ".far" : ".near"));
        }

        public ResourceLocation impactEvent(Kind kind) {
            String impact = switch (kind) {
                case ARMOR_HE -> "armor.he";
                case ARMOR_AP -> "armor.ap";
                case RICOCHET -> "ricochet";
                case REPORT -> throw new IllegalArgumentException("Reports use Sable Hostile sound events");
            };
            return ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, "naval." + impact + "." + caliber);
        }

        public static Profile forWeapon(ResourceLocation id) {
            if (!id.getNamespace().equals("sablehostile")) return null;
            return switch (id.getPath()) {
                case "oerlikon_20mm_quad" -> MM20;
                case "twin_37mm" -> MM37;
                case "bofors_40mm_twin", "bofors_40mm_quad" -> MM40;
                case "twin_76mm" -> MM76;
                case "twin_127mm" -> MM127;
                case "des_moines_203mm" -> MM203;
                default -> null;
            };
        }

        public static Profile forShell(HostileShellPayload.Model model) {
            return switch (model) {
                case HE_76, AP_76 -> MM76;
                case HE_127, AP_127 -> MM127;
                case HE_203, AP_203 -> MM203;
            };
        }
    }

    public static final StreamCodec<FriendlyByteBuf, NavalSoundPayload> CODEC = StreamCodec.of(
        (buf, sound) -> {
            buf.writeResourceLocation(sound.dimension);
            buf.writeDouble(sound.position.x);
            buf.writeDouble(sound.position.y);
            buf.writeDouble(sound.position.z);
            buf.writeEnum(sound.kind);
            buf.writeEnum(sound.profile);
        },
        buf -> new NavalSoundPayload(buf.readResourceLocation(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
            buf.readEnum(Kind.class), buf.readEnum(Profile.class)));

    public static boolean validPosition(Vec3 position) {
        return Math.abs(position.x) <= 6e7 && Math.abs(position.y) <= 6e7 && Math.abs(position.z) <= 6e7;
    }

    @Override public Type<NavalSoundPayload> type() { return TYPE; }
}
