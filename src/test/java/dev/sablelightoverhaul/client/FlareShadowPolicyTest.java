package dev.sablelightoverhaul.client;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlareShadowPolicyTest {
 @Test void flareDoesNotInheritMuzzleShadows() throws Exception {
  var spec=MuzzleClientConfig.SPEC;
  assertTrue(ExtraConfig.GPU_SHADOW_MUZZLE.getDefault());assertFalse(ExtraConfig.FLARE_SHADOWS.getDefault());
  // Only inject cached values: no game/config file lifecycle is started in this policy unit test.
  var cache=ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");cache.setAccessible(true);
  Object oldFlare=cache.get(ExtraConfig.FLARE_SHADOWS),oldMuzzle=cache.get(ExtraConfig.GPU_SHADOW_MUZZLE);
  var policy=AdvancedLights.class.getDeclaredMethod("canCast",String.class,AreaSnapshot.class);policy.setAccessible(true);
  try {
   cache.set(ExtraConfig.FLARE_SHADOWS,false);cache.set(ExtraConfig.GPU_SHADOW_MUZZLE,true);
   assertEquals(false,policy.invoke(null,"flare/37",null));assertEquals(true,policy.invoke(null,"shot/37",null));
   cache.set(ExtraConfig.FLARE_SHADOWS,true);cache.set(ExtraConfig.GPU_SHADOW_MUZZLE,false);
   assertEquals(true,policy.invoke(null,"flare/37",null));assertEquals(false,policy.invoke(null,"shot/37",null));
  }finally{cache.set(ExtraConfig.FLARE_SHADOWS,oldFlare);cache.set(ExtraConfig.GPU_SHADOW_MUZZLE,oldMuzzle);}
 }
}
