package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelightoverhaul.lighting.SourceAnchor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Heat stays in the struck surface frame. Detached ejecta keep their world-space launch frame. */
final class PlasmaImpact {
    record Contact(SourceAnchor anchor, Vec3 point, Vec3 normal, Vec3 tangent, double skew) {
        static Contact of(SourceAnchor anchor, Vec3 point, Vec3 normal, Vec3 localIncoming) {
            Vec3 projected = localIncoming.subtract(normal.scale(localIncoming.dot(normal)));
            double skew = projected.length();
            Vec3 tangent = skew > .001 ? projected.scale(1 / skew)
                : normal.cross(Math.abs(normal.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
            return new Contact(anchor, point, normal, tangent, skew);
        }

        Frame frame(ClientLevel level, float partial) {
            if (anchor.ship() == null) return new Frame(point, normal, tangent);
            float[] pose = anchor.pointAt(level, point, partial);
            if (pose == null) return null;
            return frameAt(pose);
        }

        Frame frameAt(float[] pose) {
            var q = new Quaternionf(pose[3], pose[4], pose[5], pose[6]);
            return new Frame(new Vec3(pose[0],pose[1],pose[2]), new Vec3(q.transform(normal.toVector3f())),
                new Vec3(q.transform(tangent.toVector3f())));
        }
    }

    record Frame(Vec3 point, Vec3 normal, Vec3 tangent) {
        Vec3 sparkVelocity(Vec3 radial, double skew, double spread, double lift) {
            return radial.scale(spread).add(tangent.scale(.78 * skew)).add(normal.scale(lift));
        }
    }
    private record Mark(Contact contact, long tick) {}
    private record Spray(Vec3 start, Vec3 velocity, long tick, int life, double radius, boolean molten) {
        Vec3 at(double age) {
            double drop = molten ? -.034 * age * age : 0;
            return start.add(velocity.scale(age)).add(0, drop, 0);
        }
    }

    private final List<Mark> marks = new ArrayList<>();
    private final List<Spray> spray = new ArrayList<>();

    void tick(ClientLevel level, Contact hit, long tick, RandomSource random) {
        marks.removeIf(mark -> tick - mark.tick >= 11);
        spray.removeIf(drop -> tick - drop.tick >= drop.life);
        if (hit == null) return;
        marks.removeIf(mark -> mark.contact.anchor.equals(hit.anchor) && mark.contact.normal.equals(hit.normal)
            && mark.contact.point.distanceToSqr(hit.point) < .14);
        marks.add(new Mark(hit, tick));
        Frame frame = hit.frame(level, 1);
        if (frame == null) return;
        int jets = 1 + random.nextInt(2);
        boolean drip = random.nextBoolean();
        for (int i = 0; i < jets + (drip ? 1 : 0); i++) {
            boolean molten = i == jets;
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 planar = frame.tangent.scale(Math.cos(angle)).add(frame.normal.cross(frame.tangent).scale(Math.sin(angle)));
            double lift = molten ? .24 + .18 * random.nextDouble() : .055 + .13 * random.nextDouble();
            Vec3 velocity = frame.sparkVelocity(planar, hit.skew, .24 + .1 * random.nextDouble(), lift);
            Vec3 start = frame.point.add(frame.normal.scale(.075)).add(planar.scale(.42));
            double speed = molten ? .50 + .18 * random.nextDouble() : 1.25 + .55 * random.nextDouble();
            double radius = molten ? .065 + .062 * random.nextDouble() : .035 + .035 * random.nextDouble();
            spray.add(new Spray(start, velocity.scale(speed), tick,
                molten ? 7 + random.nextInt(4) : 3 + random.nextInt(3), radius, molten));
        }
    }

    void render(ClientLevel level, VertexConsumer out, LaserTestRenderer.Material material, Vec3 eye,
                float partial, long tick) {
        for (Mark mark : marks) {
            Frame frame = mark.contact.frame(level, partial);
            if (frame == null) continue;
            double heat = Math.clamp(1 - (tick - mark.tick + partial) / 11.0, 0, 1);
            Vec3 center = frame.point.subtract(eye).add(frame.normal.scale(.025));
            Vec3 along = frame.tangent.scale(1.60 * (1 + .75 * mark.contact.skew));
            Vec3 across = frame.normal.cross(frame.tangent).scale(1.60);
            // Separate back-to-front radial bands with a broken rim and a small white-hot centre.
            for (int j = 0; j < 16; j++) {
                double a = j * Math.PI / 8, b = (j + 1) * Math.PI / 8;
                Vec3 ra = along.scale(Math.cos(a)).add(across.scale(Math.sin(a)));
                Vec3 rb = along.scale(Math.cos(b)).add(across.scale(Math.sin(b)));
                double hot = heat * heat * heat;
                int core = 0xff0000 | ((int)(32 + 206 * hot) << 8) | (int)(18 + 211 * hot);
                int alpha = (int)(235 * heat);
                material.vertex(out, center, core, alpha);
                material.vertex(out, center.add(ra.scale(.70)), core, alpha);
                material.vertex(out, center.add(rb.scale(.70)), core, alpha);
                material.vertex(out, center, core, alpha);
                material.vertex(out, center.add(ra.scale(.70)), core, alpha);
                material.vertex(out, center.add(ra.scale(.95 + .12 * Math.sin(5*a))), 0xff3206, 0);
                material.vertex(out, center.add(rb.scale(.95 + .12 * Math.sin(5*b))), 0xff3206, 0);
                material.vertex(out, center.add(rb.scale(.70)), core, alpha);
            }
        }
        for (Spray drop : spray) {
            double age = tick - drop.tick + partial;
            Vec3 tip = drop.at(age).subtract(eye);
            if (drop.molten) {
                drawMelt(out, material, tip, drop, age);
            } else {
                Vec3 tail = drop.at(Math.max(0, age - 1.8)).subtract(eye);
                // Two nested tapers give the reflected stream the same white core and red lip as the beam.
                streak(out, material, tail, tip, drop.radius, 0xff2515, 195);
                streak(out, material, tail.lerp(tip, .16), tip, drop.radius * .42, 0xffe8e4, 245);
            }
        }
    }

    private static void streak(VertexConsumer out, LaserTestRenderer.Material material, Vec3 tail, Vec3 tip,
                               double radius, int color, int alpha) {
        Vec3 side = tip.cross(tip.subtract(tail)).normalize().scale(radius);
        Vec3 shoulder = tail.lerp(tip, .72);
        material.vertex(out, tail, color, 0);
        material.vertex(out, shoulder.add(side), color, alpha);
        material.vertex(out, tip, color, alpha);
        material.vertex(out, shoulder.subtract(side), color, alpha);
    }

    private static void drawMelt(VertexConsumer out, LaserTestRenderer.Material material, Vec3 center,
                                 Spray drop, double age) {
        // A compact, faceted teardrop with a hot leading face and a cooling red skin.
        Vec3 axis = drop.velocity.add(0, -.068 * age, 0).normalize();
        Vec3 right = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0)).normalize();
        Vec3 up = axis.cross(right);
        Vec3 front = center.add(axis.scale(drop.radius));
        Vec3 back = center.subtract(axis.scale(1.9 * drop.radius));
        double heat = Math.clamp(1 - age / drop.life, 0, 1);
        int red = (int)(93 + 162 * heat);
        int skin = (red << 16) | ((int)(15 + 43 * heat) << 8) | 9;
        int hot = (red << 16) | ((int)(19 + 155 * heat * heat) << 8) | (int)(10 + 97 * heat * heat);
        Vec3 prev = center.add(right.scale(drop.radius));
        for (int j = 1; j <= 6; j++) {
            double angle = j * Math.PI / 3;
            Vec3 next = center.add(right.scale(drop.radius * Math.cos(angle))).add(up.scale(drop.radius * Math.sin(angle)));
            material.vertex(out, front, hot, 255);
            material.vertex(out, prev, skin, 255);
            material.vertex(out, next, skin, 255);
            material.vertex(out, front, hot, 255);
            material.vertex(out, back, skin, 255);
            material.vertex(out, next, skin, 255);
            material.vertex(out, prev, skin, 255);
            material.vertex(out, back, skin, 255);
            prev = next;
        }
    }
}
