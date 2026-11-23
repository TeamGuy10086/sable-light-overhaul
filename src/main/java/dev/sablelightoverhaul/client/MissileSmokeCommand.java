package dev.sablelightoverhaul.client;
import dev.sablelightoverhaul.SableLightOverhaul;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
@EventBusSubscriber(modid=SableLightOverhaul.ID,value=Dist.CLIENT)
public final class MissileSmokeCommand {
    private MissileSmokeCommand(){}
    @SubscribeEvent public static void register(RegisterClientCommandsEvent e){
        var root=Commands.literal("slomissilesmoke").executes(c->{c.getSource().sendSuccess(()->Component.literal(MissileSmokeMesh.stats()),false);return 1;});
        for(String shape:new String[]{"straight","curve"})root.then(Commands.literal(shape).executes(c->{
            boolean ok=MissileSmokeMesh.demo(shape.equals("curve"));
            if(!ok)c.getSource().sendFailure(Component.translatable("sablelightoverhaul.missile_smoke.unavailable"));
            return ok?1:0;
        }));
        root.then(Commands.literal("clear").executes(c->{MissileSmokeMesh.clear();return 1;}));
        root.then(Commands.literal("reset").executes(c->{MissileSmokeMesh.width=.45;MissileSmokeMesh.expansion=.6;MissileSmokeMesh.lifetime=160;MissileSmokeMesh.alpha=1;return 1;}));
        for(String field:new String[]{"width","expansion","lifetime","alpha"})root.then(Commands.literal(field)
            .then(Commands.argument("value",DoubleArgumentType.doubleArg(field.equals("lifetime")?1:0,field.equals("lifetime")?30:field.equals("alpha")?1:4))
            .executes(c->{double v=DoubleArgumentType.getDouble(c,"value");switch(field){case "width"->MissileSmokeMesh.width=Math.max(.02,v);case "expansion"->MissileSmokeMesh.expansion=v;case "lifetime"->MissileSmokeMesh.lifetime=v*20;case "alpha"->MissileSmokeMesh.alpha=v;}
                c.getSource().sendSuccess(()->Component.literal(MissileSmokeMesh.stats()),false);return 1;})));
        e.getDispatcher().register(root);
    }
}
