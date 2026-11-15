package dev.sablelightoverhaul.profiles;

import dev.sablelightoverhaul.api.EffectApi;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The storage layer's one job is its shape, so this is save then load then compare -- and the ways a file can
 * be wrong, none of which may cost more than a world with no profiles in it.
 */
class ProfileDataTest {
    private static final ResourceLocation CANNON = ResourceLocation.parse("createbigcannons:pitch_contraption");
    private static final ResourceLocation MISSILE = ResourceLocation.parse("createbigcannons:munitions_contraption");
    private static final UUID BODY = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

    private static EffectApi.SourceKey turret() {
        return new EffectApi.SourceKey(Level.OVERWORLD, BODY, new BlockPos(3, 1, 0), CANNON, "左炮塔");
    }

    private static EffectApi.SourceKey blockCannon() {
        return new EffectApi.SourceKey(Level.OVERWORLD, null, new BlockPos(40, 70, 40), CANNON, null);
    }

    @Test void whatIsSavedIsWhatIsLoaded() {
        var data = new ProfileData();
        data.store().put(turret(), CANNON, "shape.forwardRange", 32.0);
        data.store().put(turret(), MISSILE, "shape.forwardRange", 64.0);
        data.store().put(blockCannon(), CANNON, "time.durationTicks", 5.0);

        var back = ProfileData.load(data.save(new CompoundTag(), null), null);

        assertEquals(32.0, back.store().resolve(turret(), CANNON).orElseThrow().get("shape.forwardRange"));
        assertEquals(64.0, back.store().resolve(turret(), MISSILE).orElseThrow().get("shape.forwardRange"),
                "the type gate survives a round trip");
        assertEquals(5.0, back.store().resolve(blockCannon(), CANNON).orElseThrow().get("time.durationTicks"),
                "a cannon with no body keeps its identity and stays distinct from a bodied one");
        assertEquals(2, back.store().size(), "two cannons, and the bodyless one is one of them");
        assertTrue(back.store().resolve(blockCannon(), MISSILE).isEmpty());
    }

    @Test void aFileFromANewerFormatIsReadAsEmptyRatherThanMisread() {
        var tag = new CompoundTag();
        tag.putInt("format", 99);
        tag.put("cannons", new ListTag());
        assertEquals(0, ProfileData.load(tag, null).store().size());
    }

    @Test void aDamagedFileCostsDefaultsAndNotTheWorld() {
        var tag = new CompoundTag();
        tag.putInt("format", 1);
        var broken = new CompoundTag();
        broken.putString("dimension", "not a dimension at all");
        var cannons = new ListTag();
        cannons.add(broken);
        tag.put("cannons", cannons);

        // The game calls loadSafely, and this is the promise it makes: an unreadable file is an empty one.
        assertEquals(0, ProfileData.loadSafely(tag, null).store().size(),
                "nonsense in, no profiles out, and nothing thrown at the world loader");
    }

    @Test void aHalfWrittenFileIsAlsoJustEmpty() {
        var tag = new CompoundTag();
        tag.putInt("format", 1);
        var cannons = new ListTag();
        var partial = new CompoundTag();
        partial.putString("dimension", "minecraft:overworld");
        partial.putLong("pos", BlockPos.ZERO.asLong());
        var types = new ListTag();
        var entry = new CompoundTag();
        entry.putString("type", CANNON.toString());
        entry.put("values", new CompoundTag());            // no values at all
        types.add(entry);
        partial.put("types", types);
        cannons.add(partial);
        tag.put("cannons", cannons);

        assertEquals(0, ProfileData.loadSafely(tag, null).store().size(),
                "an entry with no values is not an entry");
    }
}
