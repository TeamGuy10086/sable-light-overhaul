package dev.sablelightoverhaul;

import dev.sablelightoverhaul.munitions.*;
import dev.sablelightoverhaul.lighting.DebugFlareServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import rbasamoyai.createbigcannons.index.CBCDataComponents;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Test-only mod, excluded from the distributable jar. Runs against real CBC registries and a fresh world. */
@Mod("sloflaretest")
public final class FlareIntegrationTest {
    private int checks;
    public FlareIntegrationTest() {
        if (Boolean.getBoolean("slo.flareIntegration")) NeoForge.EVENT_BUS.addListener(this::run);
    }
    private void check(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        checks++;
    }
    private void run(ServerStartedEvent event) {
        String result;
        try {
            test(event.getServer().overworld());
            result="PASS: " + checks + " runtime assertions";
        } catch (Throwable error) {
            error.printStackTrace();
            result="FAIL after " + checks + ": " + error;
        }
        try { Files.writeString(Path.of("result.txt"), result); } catch (Exception e) { throw new RuntimeException(e); }
        System.out.println("FLARE_INTEGRATION: " + result);
        event.getServer().halt(false);
    }
    @SuppressWarnings("unchecked")
    private void test(ServerLevel level) throws Exception {
        var block=FlareMunitions.SHELL.get();
        var expected=new FlareSettings(37,2.75F);
        var itemTag=TagParser.parseTag("{id:'sablelightoverhaul:illumination_shell',count:1,components:{'sablelightoverhaul:flare_settings':{burn_seconds:37,brightness:2.75f}}}");
        ItemStack stack=ItemStack.parseOptional(level.registryAccess(),itemTag);
        check(!stack.isEmpty(),"command-style item components decode");
        check(expected.equals(stack.get(FlareMunitions.SETTINGS.get())),"per-item settings");
        var fuze=new ItemStack(FlareMunitions.ALTITUDE_FUZE.get());
        fuze.set(FlareMunitions.ALTITUDE.get(),200);
        stack.set(CBCDataComponents.FUZE,ItemContainerContents.fromItems(List.of(fuze)));
        var savedItem=stack.save(level.registryAccess());
        var reloaded=ItemStack.parse(level.registryAccess(),savedItem).orElseThrow();
        check(expected.equals(reloaded.get(FlareMunitions.SETTINGS.get())),"item disk roundtrip");
        var info=block.getHandloadingInfo(reloaded,BlockPos.ZERO,Direction.EAST,level.registryAccess());
        check(expected.equals(FlareSettings.read(info.nbt())),"handloading payload NBT");
        var extracted=block.getExtractedItem(info,level.registryAccess());
        check(expected.equals(extracted.get(FlareMunitions.SETTINGS.get())),"extraction preserves settings");
        check(extracted.get(CBCDataComponents.FUZE).copyOne().get(FlareMunitions.ALTITUDE.get())==200,"extraction preserves fuze");
        var fromItem=block.getProjectile(level,reloaded);
        var fromBlocks=block.getProjectile(level,List.of(info));
        check(expected.equals(fromItem.settings()),"direct item firing");
        check(expected.equals(fromBlocks.settings()),"assembled cannon firing");
        CompoundTag entityTag=new CompoundTag();fromBlocks.addAdditionalSaveData(entityTag);
        var loaded=FlareMunitions.SHELL_ENTITY.get().create(level);loaded.readAdditionalSaveData(entityTag);
        check(expected.equals(loaded.settings()),"flying shell disk roundtrip");
        var pos=new BlockPos(0,120,0);level.getChunkAt(pos);level.setBlockAndUpdate(pos,block.defaultBlockState());
        var be=(IlluminationShellBlockEntity)level.getBlockEntity(pos);
        check(be!=null,"own block entity registered");
        be.applyComponentsFromItemStack(reloaded);
        check(expected.equals(be.settings()),"placed item components applied");
        var beTag=be.saveWithFullMetadata(level.registryAccess());
        level.removeBlock(pos,false);level.setBlockAndUpdate(pos,block.defaultBlockState());
        be=(IlluminationShellBlockEntity)level.getBlockEntity(pos);be.loadWithComponents(beTag,level.registryAccess());
        check(expected.equals(be.settings()),"placed block disk roundtrip");
        CompoundTag safe=new CompoundTag();be.writeSafe(safe,level.registryAccess());
        check(expected.equals(FlareSettings.read(safe)),"Create safe NBT");
        check(expected.equals(block.getProjectile(level,pos,block.defaultBlockState()).settings()),"placed block firing");
        check(expected.equals(block.getCloneItemStack(level,pos,block.defaultBlockState()).get(FlareMunitions.SETTINGS.get())),"pick block settings");
        var drops=net.minecraft.world.level.block.Block.getDrops(block.defaultBlockState(),level,pos,be);
        check(drops.stream().anyMatch(s->expected.equals(s.get(FlareMunitions.SETTINGS.get()))),"breaking shell loot preserves payload");
        check(block.getTicker(level,block.defaultBlockState(),FlareMunitions.SHELL_BLOCK_ENTITY.get())!=null,"own BE fuze ticker");
        // Real CBC flight tick must release once through the inherited HE fuze path.
        fromBlocks.setPos(0,180,0);fromBlocks.setDeltaMovement(0,-0.1,0);
        var field=DebugFlareServer.class.getDeclaredField("FLARES");field.setAccessible(true);
        var effects=(Map<ServerLevel,List<FlarePayload.State>>)field.get(null);
        check(FlareMunitions.ALTITUDE_FUZE.get().onProjectileTick(fuze,fromBlocks),"downward height triggers");
        fromBlocks.setDeltaMovement(0,0.1,0);
        check(!FlareMunitions.ALTITUDE_FUZE.get().onProjectileTick(fuze,fromBlocks),"upward height does not trigger");
        fromBlocks.setDeltaMovement(0,-0.1,0);fromBlocks.tick();
        check(fromBlocks.isRemoved(),"CBC tick removes carrier after release");
        var states=effects.get(level);
        check(states!=null && states.size()==1,"CBC fuze releases one effect");
        check(states.getFirst().durationTicks()==740 && states.getFirst().brightness()==2.75F,"release uses individual settings");
        CompoundTag released=new CompoundTag();fromBlocks.addAdditionalSaveData(released);
        check(released.getBoolean("SloFlareReleased"),"release guard persisted");
        var duplicate=FlareMunitions.SHELL_ENTITY.get().create(level);duplicate.readAdditionalSaveData(released);
        duplicate.setPos(0,180,0);duplicate.setDeltaMovement(0,-0.1,0);duplicate.tick();
        check(states.size()==1,"reloading released carrier does not duplicate light");
        level.removeBlock(pos,false);
    }
}
