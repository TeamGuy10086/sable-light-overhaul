package dev.cbcmuzzlelight.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.cbcmuzzlelight.MuzzleLight;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * A client-side command that lays a trail along a straight line, for telling the two halves of the trail
 * apart when something is wrong.
 *
 * <p>The trail has two things that can fail independently: the positions arriving from the server, and
 * the frames being drawn from them. Running {@code /mkTrail} exercises only the second, through exactly
 * the same code the feed uses. So a trail that the command draws and the feed does not means the
 * positions are the problem, and a trail that neither draws means the drawing is.
 */
@EventBusSubscriber(modid=MuzzleLight.ID,value=Dist.CLIENT)
public final class TrailDebugCommand {
 private TrailDebugCommand(){}
 @SubscribeEvent public static void register(RegisterClientCommandsEvent event){
  event.getDispatcher().register(
   Commands.literal("mkTrail")
    .then(Commands.argument("x1",DoubleArgumentType.doubleArg())
     .then(Commands.argument("y1",DoubleArgumentType.doubleArg())
      .then(Commands.argument("z1",DoubleArgumentType.doubleArg())
       .then(Commands.argument("x2",DoubleArgumentType.doubleArg())
        .then(Commands.argument("y2",DoubleArgumentType.doubleArg())
         .then(Commands.argument("z2",DoubleArgumentType.doubleArg())
          .executes(context->{
           var from=new Vec3(DoubleArgumentType.getDouble(context,"x1"),
             DoubleArgumentType.getDouble(context,"y1"),DoubleArgumentType.getDouble(context,"z1"));
           var to=new Vec3(DoubleArgumentType.getDouble(context,"x2"),
             DoubleArgumentType.getDouble(context,"y2"),DoubleArgumentType.getDouble(context,"z2"));
           TrailTrack.debugTrail(from,to);
           com.mojang.logging.LogUtils.getLogger().info("CBC Muzzle Light trail mkTrail: from {} to {}",from,to);
           context.getSource().sendSuccess(()->Component.literal(
             "CBC Muzzle Light: trail laid from "+fmt(from)+" to "+fmt(to)),false);
           return 1;
          }))))))));
 }
 private static String fmt(Vec3 v){
  return String.format(java.util.Locale.ROOT,"(%.1f, %.1f, %.1f)",v.x,v.y,v.z);
 }
}
