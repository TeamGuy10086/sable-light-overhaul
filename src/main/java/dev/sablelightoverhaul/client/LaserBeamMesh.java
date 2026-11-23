package dev.sablelightoverhaul.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Overlapping plasma filaments, a narrow translucent fringe, and detached wisps. */
final class LaserBeamMesh {
    private static final double BEAM_RADIUS = .90;
    private static final double HALO_WIDTH = .12;
    private static final double STEP = .72;
    private static final int FILAMENTS = 12;
    private static final double ETCH_CUTOFF = .32;

    // Repeated coordinates are the two lips of each narrow opening in the core.
    private static final double[] CORE_LANES = {
        -.88, -.71, -.58, -.47, -.47, -.29, -.29, -.10, -.10,
        .10, .10, .29, .29, .47, .47, .58, .71, .88
    };
    private static final double FLOW_SPEED = 6.0;

    private LaserBeamMesh() {}

    // A shared axial brightness envelope; send wrapped phases to GLSL to preserve sub-tick precision.
    static double phase(double tick, long seed, double frequency) {
        return (hash(seed) * 2 * Math.PI - tick * FLOW_SPEED * .80 * frequency) % (2 * Math.PI);
    }

    private static double envelope(double distance, double phaseA, double phaseB) {
        return .65 + .23 * Math.sin(distance * .85 + phaseA) + .12 * Math.sin(distance * 1.71 + phaseB);
    }

    static void draw(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start, Vec3 axis,
                     double length, double tick, long seed, boolean blocked, double energy) {
        Vec3 side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 up = axis.cross(side);
        Vec3 across = axis.cross(start).normalize();
        if (across.lengthSqr() < 1e-8) across = side;
        int segments = (int)Math.ceil(length / STEP);
        double flowTick = tick * FLOW_SPEED;
        double coreGain = Math.clamp((energy - .52) / .48, 0, 1);
        var coreMaterial = material.scaled(coreGain);
        if (coreGain > 0) emitCore(out, coreMaterial, start, axis, across, length, segments, flowTick, seed, blocked);
        // Inside the narrow axial view the ribbon can be behind the near plane or edge-on.
        // Give the core a real cross-section along its entire length, visible from inside as well.
        Vec3 mid = start.add(axis.scale(length * .5));
        if (coreGain > 0 && (Math.abs(mid.dot(axis)) > .75 * mid.length() || axis.cross(start).lengthSqr() < 4)) {
            emitInnerTube(out, coreMaterial, start, axis, side, up, length, flowTick, seed, blocked);
        }
        if (coreGain > 0) emitCoreEnd(out, coreMaterial, start, axis, side, up, length, blocked, false);
        if (coreGain > 0) emitCoreEnd(out, coreMaterial, start, axis, side, up, length, blocked, true);
        emitFilaments(out, material, start, axis, across, length, segments, flowTick, seed, blocked, energy);
        emitFringe(out, material.scaled(energy * energy), start, axis, across, length, segments, flowTick, seed, blocked);
        emitWisps(out, material.scaled(energy), start, axis, side, up, across, length, flowTick, seed);
    }

    private static void emitInnerTube(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start,
                                      Vec3 axis, Vec3 side, Vec3 up, double length, double tick, long seed,
                                      boolean blocked) {
        Point[] previous = new Point[8], next = new Point[8];
        Vec3[] rim = new Vec3[8];
        for (int j = 0; j < rim.length; j++) {
            double angle = Math.PI * j / 4;
            rim[j] = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
        }
        int segments = (int)Math.ceil(length / 2);
        for (int i = 0; i <= segments; i++) {
            double d = Math.min(length, i * 2.0);
            Vec3 center = start.add(axis.scale(d));
            double ripple = noise((d - tick * .8) * .35, seed + 73);
            double radius = (.23 + .05 * ripple) * endFade(d, length, blocked);
            int color = warmColor(.83 + .16 * ripple, .98);
            for (int j = 0; j < rim.length; j++) next[j] = new Point(center.add(rim[j].scale(radius)), color, 255);
            if (i > 0) {
                for (int j = 0; j < rim.length; j++) {
                    int k = (j + 1) % rim.length;
                    // Submit the winding facing the eye for this facet.
                    if (previous[j].position.dot(rim[j].add(rim[k])) <= 0)
                        quad(out, material, previous[j], previous[k], next[k], next[j]);
                    else quad(out, material, previous[k], previous[j], next[j], next[k]);
                }
            }
            Point[] swap = previous;
            previous = next;
            next = swap;
        }
    }

    // Both ends have a cross section even when the camera sits between their axial planes.
    private static void emitCoreEnd(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start,
                                    Vec3 axis, Vec3 side, Vec3 up, double length, boolean blocked, boolean distal) {
        double d = distal ? Math.max(0, length - (blocked ? .02 : .70)) : Math.min(length, .02);
        Vec3 center = start.add(axis.scale(d));
        double radius = BEAM_RADIUS * .59 * (.60 + .40 * smoothUnit(d * 1.5)) * endFade(d, length, blocked);
        Point middle = new Point(center, warmColor(.99, 1), 255);
        Point previous = new Point(center.add(side.scale(radius)), warmColor(.82, .92), 255);
        for (int i = 1; i <= 12; i++) {
            double angle = (center.dot(axis) < 0 ? 1 : -1) * i * (Math.PI / 6);
            Vec3 rim = center.add(side.scale(radius * Math.cos(angle))).add(up.scale(radius * Math.sin(angle)));
            Point next = new Point(rim, previous.color, 255);
            quad(out, material, middle, previous, next, middle);
            previous = next;
        }
    }

    private record Point(Vec3 position, int color, int alpha) {}
    private record Strand(double radius, double phase, double speed, long seed) {}
    private record RibbonPoint(Vec3 position, double width, int color, double density) {}

    // A continuous hot center and darker shoulders underneath the moving edge strands.
    private static void emitCore(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start, Vec3 axis,
                                 Vec3 across, double length, int segments, double tick, long seed, boolean blocked) {
        double[] lanes = CORE_LANES;
        double phaseA = phase(tick / FLOW_SPEED, seed, .85);
        double phaseB = phase(tick / FLOW_SPEED, seed + 1, 1.71);
        Point[] previous = new Point[lanes.length], next = new Point[lanes.length];
        for (int segment = 0; segment <= segments; segment++) {
            double d = Math.min(length, segment * STEP);
            Vec3 center = start.add(axis.scale(d));
            double envelope = envelope(d, phaseA, phaseB);
            double profile = (.60 + .40 * smoothUnit(d * 1.5)) * endFade(d, length, blocked);
            for (int lane = 0; lane < lanes.length; lane++) {
                double u = lanes[lane];
                double ripple = noise((d - tick * .80) * .35, seed + (u < 0 ? 73 : 107));
                double offset = u * BEAM_RADIUS * profile * (.91 + .09 * ripple);
                double hot = 1 - smoothUnit((Math.abs(u) - .54) / .23);
                double level = (.32 + .10 * ripple + .57 * hot) * (.80 + .20 * envelope);
                boolean lowerLip = lane + 1 < lanes.length && u == lanes[lane + 1];
                boolean upperLip = lane > 0 && u == lanes[lane - 1];
                if (lowerLip || upperLip) {
                    int pairStart = lowerLip ? lane : lane - 1;
                    long slitSeed = seed + 229L + 53L * pairStart;
                    double flow = d - tick * (.76 + .22 * hash(slitSeed + 17));
                    double cut = smoothUnit((noise(flow * .41, slitSeed) - .66) / .27);
                    double drift = .06 * (noise(flow * .22, slitSeed + 9) - .5);
                    offset += profile * (drift + (lowerLip ? -.026 : .026) * cut);
                    level -= .16 * cut;
                }
                next[lane] = new Point(center.add(across.scale(offset)), warmColor(level, .08 + .90 * hot), 255);
            }
            if (segment > 0) {
                for (int j = 1; j < lanes.length; j++) {
                    if (lanes[j] == lanes[j - 1]) continue;
                    quad(out, material, previous[j], previous[j - 1], next[j - 1], next[j]);
                }
            }
            Point[] swap = previous;
            previous = next;
            next = swap;
        }
    }

    private static void emitFilaments(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start,
                                      Vec3 axis, Vec3 across, double length, int segments,
                                      double tick, long seed, boolean blocked, double energy) {
        Vec3 front = axis.cross(across);
        for (int i = 0; i < FILAMENTS; i++) {
            long strandSeed = seed + 179L * i;
            double extinction = .05 + .50 * hash(strandSeed + 315);
            double gain = Math.clamp((energy - extinction) / .35, 0, 1);
            if (gain <= 0) continue;
            var strandMaterial = material.scaled(gain);
            double spread = ((i / 2) + .5) / (FILAMENTS / 2);
            double radius = BEAM_RADIUS * (.60 + .36 * spread);
            double phase = (i % 2 == 0 ? 0 : Math.PI) + .18 * (hash(strandSeed) - .5);
            var strand = new Strand(radius, phase, .52 + .83 * hash(strandSeed + 1), strandSeed);
            RibbonPoint previous = sampleFilament(strand, start, across, front, 0, tick, endFade(0, length, blocked));
            for (int segment = 1; segment <= segments; segment++) {
                double distance = Math.min(segment * STEP, length);
                RibbonPoint next = sampleFilament(strand, start.add(axis.scale(distance)), across, front,
                    distance, tick, endFade(distance, length, blocked));
                emitEtchedSegment(out, strandMaterial, across, previous, next);
                previous = next;
            }
        }
    }

    private static RibbonPoint sampleFilament(Strand strand, Vec3 center, Vec3 side, Vec3 up,
                                              double distance, double tick, double tail) {
        double flow = distance - tick * strand.speed;
        double coarse = noise(flow * .21, strand.seed + 7);
        double fine = noise(flow * .47, strand.seed + 23);
        double grain = noise(flow * .82, strand.seed + 151);
        double fraction = strand.radius / BEAM_RADIUS;
        double swell = noise(flow * .27, strand.seed + 61);
        double tear = smoothUnit((.54 * coarse + .27 * fine + .19 * grain - .24
            + .28 * (1 - fraction) + .12 * swell) / .38);
        double width = (.013 + .018 * swell) * (.78 + .22 * grain);
        double opening = .60 + .40 * smoothUnit(distance * 1.5);
        width *= opening;
        double radius = Math.clamp(opening * (strand.radius + .21 * (swell - .5) + .07 * (coarse - .5)),
            0, BEAM_RADIUS * opening - width);
        double angle = strand.phase + .22 * (noise(flow * .11, strand.seed + 39) - .5);
        Vec3 position = center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
        double heat = smoothUnit((.60 * fine + .24 * grain + .16 * swell - .21) / .70);
        double brightness = .74 + .26 * heat;
        return new RibbonPoint(position, width, warmColor(brightness, .33 + .62 * heat), tear * tail);
    }

    private static void emitEtchedSegment(VertexConsumer out, LaserTestRenderer.Material material,
                                          Vec3 across, RibbonPoint from, RibbonPoint to) {
        if (from.density < ETCH_CUTOFF && to.density < ETCH_CUTOFF) return;
        if (from.density < ETCH_CUTOFF) from = cutAtDensity(from, to);
        if (to.density < ETCH_CUTOFF) to = cutAtDensity(to, from);
        Vec3 a = across.scale(from.width);
        Vec3 b = across.scale(to.width);
        quad(out, material, new Point(from.position.add(a), from.color, 255),
            new Point(from.position.subtract(a), from.color, 255),
            new Point(to.position.subtract(b), to.color, 255), new Point(to.position.add(b), to.color, 255));
    }

    private static RibbonPoint cutAtDensity(RibbonPoint gap, RibbonPoint solid) {
        double blend = (ETCH_CUTOFF - gap.density) / (solid.density - gap.density);
        return new RibbonPoint(gap.position.lerp(solid.position, blend), .26 * solid.width, gap.color, ETCH_CUTOFF);
    }

    private static void emitFringe(VertexConsumer out, LaserTestRenderer.Material material, Vec3 start,
                                   Vec3 axis, Vec3 across, double length, int segments, double tick,
                                   long seed, boolean blocked) {
        for (int sign : new int[]{-1, 1}) {
            Vec3 edge = across.scale(sign);
            Point inner = fringeEdge(start, edge, 0, tick, seed + sign, endFade(0, length, blocked));
            Point outer = new Point(inner.position.add(edge.scale(HALO_WIDTH)), 0xFF0C05, 0);
            for (int i = 1; i <= segments; i++) {
                double distance = Math.min(i * STEP, length);
                double fade = endFade(distance, length, blocked);
                Point nextInner = fringeEdge(start.add(axis.scale(distance)), edge, distance, tick, seed + sign, fade);
                Point nextOuter = new Point(nextInner.position.add(edge.scale(HALO_WIDTH * fade)), 0xFF0C05, 0);
                if (sign < 0) {
                    quad(out, material, inner, outer, nextOuter, nextInner);
                } else {
                    quad(out, material, outer, inner, nextInner, nextOuter);
                }
                inner = nextInner;
                outer = nextOuter;
            }
        }
    }

    private static Point fringeEdge(Vec3 center, Vec3 outward, double distance, double tick, long seed, double fade) {
        double flutter = noise((distance - tick * .94) * .43, seed + 113);
        double radius = BEAM_RADIUS * (.84 + .10 * flutter) * (.60 + .40 * smoothUnit(distance * 1.5));
        return new Point(center.add(outward.scale(radius)), 0xFF3012, (int)((5 + 22 * flutter * flutter) * fade));
    }

    private static double endFade(double distance, double length, boolean blocked) {
        return blocked ? 1 : Math.clamp((length - distance) * 1.35, 0, 1);
    }

    private static void quad(VertexConsumer out, LaserTestRenderer.Material material, Point a, Point b, Point c, Point d) {
        material.vertex(out, a.position, a.color, a.alpha);
        material.vertex(out, b.position, b.color, b.alpha);
        material.vertex(out, c.position, c.color, c.alpha);
        material.vertex(out, d.position, d.color, d.alpha);
    }

    private static void emitWisps(VertexConsumer out, LaserTestRenderer.Material material,
                                  Vec3 start, Vec3 axis, Vec3 side, Vec3 up, Vec3 across,
                                  double length, double tick, long seed) {
        for (int cell = 0; cell * 5.4 < length; cell++) {
            long cellSeed = seed + 977L * cell;
            double clock = tick * (.13 + .06 * hash(cellSeed)) + hash(cellSeed + 1);
            long cycle = (long)Math.floor(clock);
            double age = (clock - cycle) / .72;
            if (age >= 1) continue;
            long wisp = cellSeed + 83L * cycle;
            double head = cell * 5.4 + 1.9 * hash(wisp + 2) + age * (2.1 + 1.4 * hash(wisp + 3));
            double from = Math.max(0, head - (.39 + .85 * hash(wisp + 4)));
            double to = Math.min(length, head);
            if (from >= to) continue;
            double angle = 2 * Math.PI * hash(wisp + 5);
            Vec3 outward = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            double flare = Math.sin(Math.PI * age);
            double radius = BEAM_RADIUS * (.87 + age * (.18 + .28 * hash(wisp + 6)));
            double width = (.014 + .023 * hash(wisp + 7)) * flare;
            int color = warmColor(.55 + .40 * flare, .25 + .48 * hash(wisp + 8));
            Vec3 a = start.add(axis.scale(from)).add(outward.scale(radius - .13));
            Vec3 b = start.add(axis.scale(to)).add(outward.scale(radius));
            quad(out, material, new Point(a.add(across.scale(width * .25)), color, 255),
                new Point(a.subtract(across.scale(width * .25)), color, 255),
                new Point(b.subtract(across.scale(width)), color, 255),
                new Point(b.add(across.scale(width)), color, 255));
        }
    }

    private static double smoothUnit(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static int warmColor(double intensity, double warmth) {
        double heat = Math.clamp((intensity * (.40 + .60 * (126 + 126 * warmth) / 255) - .22) / .72, 0, 1);
        int red = (int)(255 * (.46 + .54 * smoothUnit(heat / .64)));
        int green = (int)(255 * (.025 + .96 * smoothUnit((heat - .25) / .74)));
        int blue = (int)(255 * (.008 + .94 * smoothUnit((heat - .56) / .43)));
        return (red << 16) | (green << 8) | blue;
    }

    private static double noise(double x, long seed) {
        long cell = (long)Math.floor(x);
        double t = x - cell;
        t = t * t * (3 - 2 * t);
        double left = hash(cell + seed);
        return left + (hash(cell + seed + 1) - left) * t;
    }

    private static double hash(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return ((value ^ (value >>> 33)) >>> 11) * 0x1.0p-53;
    }

}
