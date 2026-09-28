package dev.cbcmuzzlelight.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cbcmuzzlelight.lighting.GroundDustParticleData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The dust cloud: this mod's control program over a smoke sprite.
 *
 * <p>Motion is integrated here per axis rather than by vanilla physics, because dust is not smoke:
 * the horizontal axes get a heavy damping so a cloud stops almost where it was stirred up and a
 * trail is made of many clouds left behind, while the vertical axis only ever sees gravity. There is
 * no lift term at all, so anything that leaves a hull sinks.
 *
 * <p><b>It does not grow.</b> The sprite used to be vanilla's {@code big_smoke_0..11}, and that
 * sequence swells from a smudge to its largest frame at index 4 before thinning away again, so playing
 * it from the start inflated every cloud even though its quad never changed. The frames are now this
 * mod's own copies — {@code cbcmuzzlelight:dust_0..7}, laid out in {@code particles/ground_dust.json}
 * — and they begin at the largest frame and only go forward from there. The puff is therefore born at
 * full size and dissolves; the lifetime is untouched.
 *
 * <p>Measured on the originals (covered pixels / bounding box): frame 4 is the largest at 107 px and
 * 12x12, falling to 86, 72, 47, 36, 21, 12 and finally 4 px at frame 11, which is where dust_0..7
 * come from.
 *
 * <p>One camera-facing quad is drawn. The flat, world-horizontal quad that used to be drawn alongside
 * it (behind a {@code dust.groundPlane} switch) was never visible from a normal viewing angle, and its
 * switch is gone with it.
 */
public final class GroundDustParticle extends TextureSheetParticle {
    private final int epoch = ClientFlashes.visualEpoch();
    /** The frames have to be kept: the cloud steps through them as it dissolves. */
    private final SpriteSet sprites;
    private final float birthSize;
    private final float stretch;
    private final float damping;
    private final float pull;
    private final float baseAlpha;
    private GroundDustParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                               GroundDustParticleData data, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.birthSize = Math.max(0.1F, data.size());
        // Dust is a patch, not a balloon: all of its presence comes from how big it is born.
        this.stretch = Math.min(Math.max(data.stretch(), 1.0F), 6.0F);
        this.damping = Math.min(Math.max(data.damping(), 0.1F), 1.0F);
        // Downward acceleration is applied by this class, so vanilla's own gravity stays off.
        this.pull = Math.max(data.gravity(), 0.0F);
        this.baseAlpha = Math.min(Math.max(data.alpha(), 0.02F), 1.0F);
        this.lifetime = Math.max(2, data.lifetime());
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = true;
        this.quadSize = birthSize;
        int rgb = data.color();
        this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
        this.setAlpha(baseAlpha);
        this.setParticleSpeed(dx, dy, dz);
        // Starts on the largest frame and only ever goes forward from there, so nothing inflates.
        this.setSprite(sprites.get(0, this.lifetime));
    }
    @Override public void tick() {
        // A new visual epoch means a new world or a mode change; old clouds must not survive it.
        if (!ClientFlashes.effectsEnabled() || epoch != ClientFlashes.visualEpoch()) { remove(); return; }
        this.xo = this.x; this.yo = this.y; this.zo = this.z;
        this.xd *= damping;
        this.zd *= damping;
        this.yd -= 0.04 * this.pull;
        this.move(this.xd, this.yd, this.zd);
        this.age++;
        float progress = (float) this.age / this.lifetime;
        // Duration, not size: only the opacity and the sprite frame change over its life, and the last
        // 40% is the fade. The frames are ours now (see the class comment) and run from the largest
        // one onwards, so stepping through them dissolves a cloud and never grows it.
        float fade = progress < 0.6F ? 1.0F : 1.0F - (progress - 0.6F) / 0.4F;
        this.setAlpha(baseAlpha * fade * fade);
        this.setSprite(sprites.get(Math.min(this.age, this.lifetime - 1), this.lifetime));
        if (this.age >= this.lifetime) remove();
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    /** One quad facing the camera: the only layer there is. */
    @Override public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (!ClientFlashes.effectsEnabled() || epoch != ClientFlashes.visualEpoch()) return;
        var cameraPos = camera.getPosition();
        float ox = (float) (this.x - cameraPos.x), oy = (float) (this.y - cameraPos.y), oz = (float) (this.z - cameraPos.z);
        int light = this.getLightColor(partialTick);
        float half = this.getQuadSize(partialTick) * 0.5F;
        float halfWidth = half * stretch;
        Quaternionf rotation = camera.rotation();
        Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation);
        quad(buffer, ox, oy, oz, right, up, halfWidth, half, light, alpha);
    }
    private void quad(VertexConsumer buffer, float ox, float oy, float oz, Vector3f right, Vector3f up,
                      float halfWidth, float halfHeight, int light, float alpha) {
        vertex(buffer, ox, oy, oz, right, up, -halfWidth, -halfHeight, this.getU0(), this.getV1(), light, alpha);
        vertex(buffer, ox, oy, oz, right, up, halfWidth, -halfHeight, this.getU1(), this.getV1(), light, alpha);
        vertex(buffer, ox, oy, oz, right, up, halfWidth, halfHeight, this.getU1(), this.getV0(), light, alpha);
        vertex(buffer, ox, oy, oz, right, up, -halfWidth, halfHeight, this.getU0(), this.getV0(), light, alpha);
    }
    private void vertex(VertexConsumer buffer, float ox, float oy, float oz, Vector3f right, Vector3f up,
                        float dx, float dy, float u, float v, int light, float alpha) {
        buffer.addVertex(ox + right.x() * dx + up.x() * dy, oy + right.y() * dx + up.y() * dy, oz + right.z() * dx + up.z() * dy)
                .setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }
    public static final class Provider implements ParticleProvider<GroundDustParticleData> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }
        @Override public Particle createParticle(GroundDustParticleData data, ClientLevel level,
                double x, double y, double z, double dx, double dy, double dz) {
            return new GroundDustParticle(level, x, y, z, dx, dy, dz, data, sprites);
        }
    }
}
