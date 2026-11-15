package dev.sablelightoverhaul.profiles;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The per-cannon values, on disk.
 *
 * <p>This class only moves {@link ProfileStore}'s maps in and out of NBT. Deciding what applies is the
 * store's job and is tested without a game; the only thing that can go wrong here is a shape mismatch, so the
 * format carries its own version and anything it does not understand is skipped rather than guessed at.
 *
 * <p>Nothing in here throws. A world whose profile data is damaged, half written, or written by a future
 * version starts with no profiles -- cannons fire with their defaults, which is what an unconfigured world
 * does anyway, and is strictly better than refusing to load.
 */
public final class ProfileData extends net.minecraft.world.level.saveddata.SavedData {
    /** One level of the file's own format, separate from the mod version, so a future change can be read. */
    private static final int FORMAT = 1;
    private static final String FILE_ID = "sablelightoverhaul_profiles";

    private final ProfileStore store = new ProfileStore();

    public ProfileStore store() {
        return store;
    }

    public static ProfileData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(ProfileData::new, ProfileData::loadSafely, null),
                FILE_ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("format", FORMAT);
        var cannons = new ListTag();
        store.snapshot().forEach((id, byType) -> {
            var cannon = new CompoundTag();
            cannon.putString("dimension", id.dimension().location().toString());
            if (id.ship() != null) cannon.putUUID("body", id.ship());
            cannon.putLong("pos", id.localPos().asLong());
            var types = new ListTag();
            byType.forEach((type, values) -> {
                var entry = new CompoundTag();
                entry.putString("type", type.toString());
                var valueTag = new CompoundTag();
                values.forEach(valueTag::putDouble);
                entry.put("values", valueTag);
                types.add(entry);
            });
            cannon.put("types", types);
            cannons.add(cannon);
        });
        tag.put("cannons", cannons);
        return tag;
    }

    public static ProfileData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ProfileData();
        if (tag.getInt("format") > FORMAT) return data;
        var restored = new LinkedHashMap<ProfileStore.CannonId,
                Map<ResourceLocation, Map<String, Double>>>();
        for (Tag element : tag.getList("cannons", Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag cannon)) continue;
            var dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(cannon.getString("dimension")));
            UUID ship = cannon.hasUUID("body") ? cannon.getUUID("body") : null;
            var pos = BlockPos.of(cannon.getLong("pos"));
            var byType = new LinkedHashMap<ResourceLocation, Map<String, Double>>();
            for (Tag typeElement : cannon.getList("types", Tag.TAG_COMPOUND)) {
                if (!(typeElement instanceof CompoundTag entry)) continue;
                var values = entry.getCompound("values");
                var read = new LinkedHashMap<String, Double>();
                for (String name : values.getAllKeys()) read.put(name, values.getDouble(name));
                if (!read.isEmpty()) byType.put(ResourceLocation.parse(entry.getString("type")), read);
            }
            if (!byType.isEmpty()) restored.put(new ProfileStore.CannonId(dimension, ship, pos), byType);
        }
        data.store.restore(restored);
        return data;
    }

    /** Convenience for the callers that have a level rather than a server level. */
    public static ProfileStore storeOf(Level level) {
        return level instanceof ServerLevel server ? get(server).store() : new ProfileStore();
    }
    /**
     * {@link #load} with the rule the rest of this mod follows: bad input degrades, it does not throw.
     *
     * <p>This is what the game calls, so a file whose contents are nonsense -- a dimension name that parses
     * as nothing, a coordinate that is not a number -- ends as a world with no profiles rather than as an
     * exception in the middle of loading, which is the difference between losing a cosmetic preference and
     * losing the save.
     */
    public static ProfileData loadSafely(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            return load(tag, registries);
        } catch (RuntimeException damaged) {
            com.mojang.logging.LogUtils.getLogger().warn(
                    "Sable Light Overhaul: profile data could not be read ({}); starting with none",
                    damaged.toString());
            return new ProfileData();
        }
    }}
