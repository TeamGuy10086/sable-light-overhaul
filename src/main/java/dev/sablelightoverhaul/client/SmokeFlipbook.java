package dev.sablelightoverhaul.client;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
/** Resource-owned texture: TextureManager handles release/reload; no per-particle or per-frame allocation. */
public final class SmokeFlipbook {
 public static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","textures/effect/thermal_smoke.png");
 public static final ResourceLocation TANK_ATLAS=ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","textures/effect/tank_smoke.png");
 public static int tankTexture(){return Minecraft.getInstance().getTextureManager().getTexture(TANK_ATLAS).getId();}
 private static final ResourceLocation DUST_COLUMN=ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","textures/effect/dust_column_smoke.png");
 public static int dustColumnTexture(){return Minecraft.getInstance().getTextureManager().getTexture(DUST_COLUMN).getId();}
 private static final ResourceLocation MASK=ResourceLocation.fromNamespaceAndPath("sablelightoverhaul","textures/effect/thermal_smoke_mask.png");
 public static int maskTexture(){return Minecraft.getInstance().getTextureManager().getTexture(MASK).getId();}
 private SmokeFlipbook(){}
 public static int texture(){return Minecraft.getInstance().getTextureManager().getTexture(ATLAS).getId();}
}
