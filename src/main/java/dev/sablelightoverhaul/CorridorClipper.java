package dev.sablelightoverhaul;

/** Feather an axial dust corridor in world space. Only quads touching its edge are subdivided. */
public final class CorridorClipper {
    public interface Output { void quad(float[] a, float[] b, float[] c, float[] d); }

    private static final int MAX_DEPTH = 3;
    private final float[][] input = new float[4][9];
    private final float[][] outputVertices = new float[4][9];
    private final Output output;
    private int cursor;
    private double ox, oy, oz, ax, ay, az, radius, feather, jitter, phase, edgeFrequency;
    private double uExtent, vExtent;

    public CorridorClipper(Output output) {
        this.output = java.util.Objects.requireNonNull(output);
    }

    public void cylinder(double ox, double oy, double oz, double ax, double ay, double az,
                         double radius, double phase) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.radius = radius;
        this.phase = phase;
        feather = Math.min(0.8, radius * 0.55);
        jitter = feather * 0.45;
        edgeFrequency = 1.3 / Math.max(radius, 0.5);
        cursor = 0;
    }

    public void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float alpha) {
        float[] point = input[cursor++];
        point[0] = x;
        point[1] = y;
        point[2] = z;
        point[3] = u;
        point[4] = v;
        point[5] = r;
        point[6] = g;
        point[7] = b;
        point[8] = alpha;
        if (cursor == 4) {
            // Each bilinear derivative is a blend of two opposite edges.
            uExtent = Math.max(edgeLength(0, 1), edgeLength(3, 2)) * 0.5;
            vExtent = Math.max(edgeLength(0, 3), edgeLength(1, 2)) * 0.5;
            draw(0, 0, 1, 1, 0);
            cursor = 0;
        }
    }

    private void draw(double u0, double v0, double u1, double v1, int depth) {
        double uMid = (u0 + u1) * 0.5;
        double vMid = (v0 + v1) * 0.5;
        double cx = component(0, uMid, vMid);
        double cy = component(1, uMid, vMid);
        double cz = component(2, uMid, vMid);
        double extent = uExtent * (u1 - u0) + vExtent * (v1 - v0);
        double centerDistance = radialDistance(cx - ox, cy - oy, cz - oz);
        if (centerDistance + extent <= radius - jitter) return;
        if (centerDistance - extent >= radius + jitter + feather) {
            if (depth == 0) output.quad(input[0], input[1], input[2], input[3]);
            else {
                sampleQuad(u0, v0, u1, v1);
                emit();
            }
            return;
        }

        if (depth < MAX_DEPTH && extent > feather * 0.35) {
            draw(u0, v0, uMid, vMid, depth + 1);
            draw(uMid, v0, u1, vMid, depth + 1);
            draw(uMid, vMid, u1, v1, depth + 1);
            draw(u0, vMid, uMid, v1, depth + 1);
            return;
        }

        sampleQuad(u0, v0, u1, v1);
        boolean visible = false;
        for (float[] point : outputVertices) {
            point[8] *= opacity(point[0] - ox, point[1] - oy, point[2] - oz);
            visible |= point[8] > 0.0f;
        }
        if (visible) emit();
    }

    private double radialDistance(double x, double y, double z) {
        double along = x * ax + y * ay + z * az;
        double dx = x - along * ax, dy = y - along * ay, dz = z - along * az;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private float opacity(double x, double y, double z) {
        double distance = radialDistance(x, y, z);
        if (distance <= radius - jitter) return 0.0f;
        if (distance >= radius + jitter + feather) return 1.0f;
        // Coherent across neighboring puffs and stationary relative to the firing axis.
        double edge = jitter * (0.62 * Math.sin(edgeFrequency * (x + 1.7 * y - 0.8 * z) + phase)
                             + 0.38 * Math.sin(edgeFrequency * (-2.3 * x + y + 2.1 * z) + 2.7 * phase));
        double t = Math.clamp((distance - radius - edge) / feather, 0.0, 1.0);
        return (float) (t * t * (3.0 - 2.0 * t));
    }

    private double edgeLength(int a, int b) {
        double x = input[a][0] - input[b][0];
        double y = input[a][1] - input[b][1];
        double z = input[a][2] - input[b][2];
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void sampleQuad(double u0, double v0, double u1, double v1) {
        sample(outputVertices[0], u0, v0);
        sample(outputVertices[1], u1, v0);
        sample(outputVertices[2], u1, v1);
        sample(outputVertices[3], u0, v1);
    }

    private void sample(float[] point, double u, double v) {
        for (int i = 0; i < point.length; i++) point[i] = (float) component(i, u, v);
    }

    private double component(int i, double u, double v) {
        double low = input[0][i] + u * (input[1][i] - input[0][i]);
        double high = input[3][i] + u * (input[2][i] - input[3][i]);
        return low + v * (high - low);
    }

    private void emit() {
        output.quad(outputVertices[0], outputVertices[1], outputVertices[2], outputVertices[3]);
    }
}
