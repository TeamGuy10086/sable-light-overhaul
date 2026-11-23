package dev.sablelightoverhaul;

import dev.sablelightoverhaul.lighting.LightingCommon;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.phys.Vec3;

/** Dedicated-server snapshots only. Never loads a chunk or starts tracking a vanilla entity. */
public final class FarContacts {
    public static final int LIMIT = 64;
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    public static java.util.function.Consumer<Snapshot> receiver = ignored -> {};
    private FarContacts() {}

    public record Request(int chunks) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(id("far_contact_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
            StreamCodec.of((buf, request) -> buf.writeVarInt(request.chunks), buf -> new Request(buf.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Contact(int id, ResourceLocation type, Vec3 position, float width, float height, int color,
                          UUID uuid, boolean hostile, boolean neutral, Health health) {
        public Contact(int id, ResourceLocation type, Vec3 position, float width, float height, int color,
                       UUID uuid, boolean hostile, boolean neutral) {
            this(id, type, position, width, height, color, uuid, hostile, neutral, null);
        }

        public Contact(int id,ResourceLocation type,Vec3 position,float width,float height,int color) {
            this(id,type,position,width,height,color,null,false,false);
        }
    }

    public record Health(float current, float maximum) {
        static Health capture(LivingEntity entity) {
            float current = entity.getHealth(), maximum = entity.getMaxHealth();
            return valid(current, maximum) ? new Health(current, maximum) : null;
        }

        private static boolean valid(float current, float maximum) {
            return Float.isFinite(current) && Float.isFinite(maximum) && current >= 0 && maximum > 0;
        }
    }

    public record Snapshot(ResourceLocation dimension, List<Contact> contacts) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("far_contacts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = codec(false, false);
        private static StreamCodec<RegistryFriendlyByteBuf,Snapshot> codec(boolean identified, boolean healthIncluded) {
            return StreamCodec.of((buf, snapshot) -> {
            buf.writeResourceLocation(snapshot.dimension);
            buf.writeCollection(snapshot.contacts, (out, contact) -> {
                out.writeVarInt(contact.id);
                out.writeResourceLocation(contact.type);
                out.writeDouble(contact.position.x);
                out.writeDouble(contact.position.y);
                out.writeDouble(contact.position.z);
                out.writeFloat(contact.width);
                out.writeFloat(contact.height);
                out.writeInt(contact.color);
                if(identified){
                    out.writeUUID(contact.uuid);
                    out.writeBoolean(contact.hostile);
                    out.writeBoolean(contact.neutral);
                }
                if (healthIncluded) {
                    Health health = contact.health;
                    out.writeFloat(health == null ? 0 : health.current);
                    out.writeFloat(health == null ? 0 : health.maximum);
                }
            });
        }, buf -> {
            var dimension = buf.readResourceLocation();
            int count = buf.readVarInt();
            if (count < 0 || count > LIMIT) throw new io.netty.handler.codec.DecoderException("Invalid far contact count");
            var contacts = new java.util.ArrayList<Contact>(count);
            for (int i = 0; i < count; i++) {
                int id = buf.readVarInt();
                var type = buf.readResourceLocation();
                var position = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                float width = buf.readFloat(), height = buf.readFloat();
                if (!Double.isFinite(position.x) || !Double.isFinite(position.y) || !Double.isFinite(position.z)
                        || !Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0) {
                    throw new io.netty.handler.codec.DecoderException("Invalid far contact bounds");
                }
                int color=buf.readInt();
                UUID uuid = identified ? buf.readUUID() : null;
                boolean hostile = identified && buf.readBoolean();
                boolean neutral = identified && buf.readBoolean();
                Health health = null;
                if (healthIncluded) {
                    float current = buf.readFloat(), maximum = buf.readFloat();
                    if (Health.valid(current, maximum)) health = new Health(current, maximum);
                    else if (current != 0 || maximum != 0) throw new io.netty.handler.codec.DecoderException("Invalid far contact health");
                }
                contacts.add(new Contact(id, type, position, width, height, color, uuid, hostile, neutral, health));
            }
            return new Snapshot(dimension, List.copyOf(contacts));
            });
        }
        @Override public Type<Snapshot> type() { return TYPE; }
    }

    public record IdentifiedSnapshot(Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<IdentifiedSnapshot> TYPE=new Type<>(id("far_contacts_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,IdentifiedSnapshot> CODEC=
                Snapshot.codec(true, false).map(IdentifiedSnapshot::new,IdentifiedSnapshot::snapshot);
        @Override public Type<IdentifiedSnapshot> type(){return TYPE;}
    }

    public record HealthSnapshot(Snapshot snapshot) implements CustomPacketPayload {
        public static final Type<HealthSnapshot> TYPE = new Type<>(id("far_contacts_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HealthSnapshot> CODEC =
            Snapshot.codec(true, true).map(HealthSnapshot::new, HealthSnapshot::snapshot);
        @Override public Type<HealthSnapshot> type() { return TYPE; }
    }

    public static void request(ServerPlayer player, Request request) {
        var level = player.serverLevel();
        var server = level.getServer();
        boolean healthIncluded = player.connection.hasChannel(HealthSnapshot.TYPE);
        boolean identified = player.connection.hasChannel(IdentifiedSnapshot.TYPE);
        if (!server.isDedicatedServer() || !LightingCommon.FAR_CONTACTS.get()
                || !healthIncluded && !identified && !player.connection.hasChannel(Snapshot.TYPE)) return;
        long tick = server.getTickCount();
        long last = LAST_REQUEST.getOrDefault(player, -20L);
        if (tick - last < 10) return;
        LAST_REQUEST.put(player, tick);

        int outer = Math.min(server.getPlayerList().getViewDistance(), Math.clamp(request.chunks, 0, 32));
        int inner = Math.max(2, player.requestedViewDistance());
        var playerChunk = player.chunkPosition();
        var contacts = outer <= inner ? List.<Contact>of() : level.getEntitiesOfClass(LivingEntity.class,
            player.getBoundingBox().inflate((outer + 1) * 16.0, level.getHeight(), (outer + 1) * 16.0), entity -> {
                var chunk = entity.chunkPosition();
                int distance = Math.max(Math.abs(chunk.x - playerChunk.x), Math.abs(chunk.z - playerChunk.z));
                return entity.isAlive() && !(entity instanceof ServerPlayer)
                    && !entity.isSpectator() && !entity.isInvisibleTo(player)
                    && distance > inner && distance <= outer;
            }).stream().sorted(Comparator.comparingDouble(player::distanceToSqr)).limit(LIMIT)
            .map(entity -> new Contact(entity.getId(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                entity.position(), entity.getBbWidth(), entity.getBbHeight(),
                entity instanceof NeutralMob ? 0xF3CD69
                    : entity.getType().getCategory() == MobCategory.MONSTER ? 0xFF6464 : 0x89CDFA,
                entity.getUUID(),entity instanceof net.minecraft.world.entity.monster.Enemy,
                entity instanceof NeutralMob||entity instanceof net.minecraft.world.entity.Mob
                        && !(entity instanceof net.minecraft.world.entity.monster.Enemy)
                        && entity.getType().getCategory()!=MobCategory.MONSTER,
                healthIncluded ? Health.capture(entity) : null)).toList();
        var snapshot=new Snapshot(level.dimension().location(),contacts);
        if (healthIncluded) SloNetwork.sendToPlayer(player, new HealthSnapshot(snapshot));
        else if (identified) SloNetwork.sendToPlayer(player, new IdentifiedSnapshot(snapshot));
        else SloNetwork.sendToPlayer(player, snapshot);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SableLightOverhaul.ID, path);
    }
}
