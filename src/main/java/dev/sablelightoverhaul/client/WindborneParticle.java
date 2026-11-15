package dev.sablelightoverhaul.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;

abstract class WindborneParticle extends TextureSheetParticle {
    protected WindborneParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Override public void move(double dx, double dy, double dz) {
        var wind = ParticleWind.step();
        super.move(dx + wind.x(), dy, dz + wind.z());
    }
}
