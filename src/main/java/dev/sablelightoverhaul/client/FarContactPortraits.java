package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.sablelightoverhaul.FarContacts.Health;
import dev.sablelightoverhaul.client.FarContactPortraitLibrary.Portrait;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/** Static portrait quads, drawn with the same scene projection as the WolfMisc world labels. */
final class FarContactPortraits {
    private static final Map<ResourceLocation, List<Quad>> BATCHES = new HashMap<>();

    private static final List<Focus> FOCUSED = new ArrayList<>();
    private static MultiBufferSource.BufferSource textBuffers;

    private FarContactPortraits() {}

    static void install() {
        // The NORMAL priority WolfMisc callback fills the queues in this render stage.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FarContactPortraits::draw);
    }

    static void beginFrame() {
        BATCHES.clear();
        FOCUSED.clear();
    }

    static boolean add(ResourceLocation type, Vec3 bottom, Vec3 axis, double size) {
        var portrait = FarContactPortraitLibrary.get(type);
        if (portrait == null) return false;
        add(portrait, bottom, axis, new Vec3(0, 1, 0), size);
        return true;
    }

    private static void add(Portrait portrait, Vec3 bottom, Vec3 axis, Vec3 up, double size) {
        Vec3 half = axis.scale(size * portrait.width() * .5);
        BATCHES.computeIfAbsent(portrait.texture(), texture -> new ArrayList<>())
            .add(new Quad(bottom.subtract(half), bottom.add(half), up.scale(size * portrait.height()), portrait));
    }

    static String nameWithHealth(ResourceLocation type, String health) {
        String name = BuiltInRegistries.ENTITY_TYPE.getOptional(type)
            .map(entity -> entity.getDescription().getString()).orElse(type.getPath());
        return health == null ? name : name + "  " + health;
    }

    static String healthText(Health health) {
        return health == null ? null : healthNumber(health.current()) + " / " + healthNumber(health.maximum());
    }

    private static String healthNumber(float value) {
        if (value > Long.MAX_VALUE / 10d) return Float.toString(value);
        long tenths = value == 0 ? 0 : Math.max(1, Math.round(value * 10d));
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }

    static void focus(ResourceLocation type, Vec3 center, Vec3 boxAxis, double width, double height,
                      int color, int pixels, String health) {
        FOCUSED.add(new Focus(type, center, boxAxis, width, height, color, pixels, health));
    }

    static boolean inFocus(double depth, double distance, double cosine) {
        return depth > 0 && depth >= distance * cosine;
    }

    static double projectedSize(double depth, double focalPixels, int pixels) {
        return depth * pixels / focalPixels;
    }

    private static void drawFocused(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        var camera = event.getCamera();
        var forward = new Vec3(camera.getLookVector());
        var right = new Vec3(camera.getLeftVector());
        var up = new Vec3(camera.getUpVector());
        double focal = Math.abs(event.getProjectionMatrix().m11()) * mc.getWindow().getGuiScaledHeight() * .5;
        if (!Double.isFinite(focal) || focal <= 0) return;
        boolean wroteText = false;
        for (var focus : FOCUSED) {
            double depth = focus.center.dot(forward);
            if (depth <= 0) continue;
            double size = projectedSize(depth, focal, focus.pixels);
            // Place the billboard above the projected rectangle, including when the camera is steeply pitched.
            double halfBox = (Math.abs(up.y) * focus.height + Math.abs(up.dot(focus.boxAxis)) * focus.width) * .5;
            Vec3 bottom = focus.center.add(up.scale(halfBox + projectedSize(depth, focal, 4)));
            var portrait = FarContactPortraitLibrary.get(focus.type);
            double textSize = portrait == null ? size : size * .65;
            if (portrait != null) {
                double lift = focus.health == null ? 0 : textSize * 1.3;
                add(portrait, bottom.add(up.scale(lift)), right, up, size);
            }
            String text = portrait == null ? nameWithHealth(focus.type, focus.health) : focus.health;
            if (text == null) continue;
            var font = mc.font;
            Vec3 top = bottom.add(up.scale(textSize));
            float unit = (float) (textSize / font.lineHeight);
            var transform = new Matrix4f().translation((float) top.x, (float) top.y, (float) top.z)
                .rotate(camera.rotation()).scale(unit, -unit, unit);
            if (textBuffers == null) textBuffers = MultiBufferSource.immediate(new ByteBufferBuilder(8192));
            font.drawInBatch(text, -font.width(text) * .5f, 0, 0xff000000 | focus.color, false,
                transform, textBuffers, Font.DisplayMode.NORMAL, 0xd018212a, LightTexture.FULL_BRIGHT);
            wroteText = true;
        }
        if (wroteText) textBuffers.endBatch();
    }

    private static void draw(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || (BATCHES.isEmpty() && FOCUSED.isEmpty())) return;
        var savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix().set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            if (!FOCUSED.isEmpty()) drawFocused(event);
            BATCHES.forEach(FarContactPortraits::drawBatch);
        } finally {
            beginFrame();
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, sorting);
        }
    }

    private static void drawBatch(ResourceLocation texture, List<Quad> quads) {
        RenderType layer = RenderType.text(texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(layer.mode(), layer.format());
        for (var quad : quads) {
            var portrait = quad.portrait;
            vertex(buffer, quad.left.add(quad.rise), portrait.u0(), portrait.v0());
            vertex(buffer, quad.left, portrait.u0(), portrait.v1());
            vertex(buffer, quad.right, portrait.u1(), portrait.v1());
            vertex(buffer, quad.right.add(quad.rise), portrait.u1(), portrait.v0());
        }
        layer.draw(buffer.buildOrThrow());
    }

    private record Focus(ResourceLocation type, Vec3 center, Vec3 boxAxis, double width, double height, int color, int pixels, String health) {}
    private record Quad(Vec3 left, Vec3 right, Vec3 rise, Portrait portrait) {}

    private static void vertex(BufferBuilder buffer, Vec3 point, float u, float v) {
        buffer.addVertex((float) point.x, (float) point.y, (float) point.z)
            .setColor(-1).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
