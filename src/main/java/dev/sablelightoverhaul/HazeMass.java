package dev.sablelightoverhaul;

/** Bounded birth cohorts: merging draw carriers never resets old smoke's lifetime or caps its density. */
public final class HazeMass {
    private final int life;
    private final double[] units;
    private final long[] born;
    private final double[] curve;
    private long newest = Long.MIN_VALUE, cachedTick = Long.MIN_VALUE;
    private double depth, nextDepth, weightedAge;

    public HazeMass(int lifetime, float alpha) {
        life = Math.max(2, Math.min(400, lifetime));
        units = new double[life + 1];
        born = new long[life + 1];
        java.util.Arrays.fill(born, Long.MIN_VALUE);
        curve = new double[life + 1];
        for (int age = 0; age <= life; age++) {
            double a = Math.clamp(alpha, 0, 1) * HazeEnvelope.opacity((float) age / life, life);
            curve[age] = -Math.log1p(-Math.min(a, 1 - 1e-7));
        }
    }

    public void add(long tick) {
        if (newest != Long.MIN_VALUE && tick < newest) throw new IllegalArgumentException("smoke clock moved backwards");
        int slot = Math.floorMod(tick, units.length);
        if (born[slot] != tick) { born[slot] = tick; units[slot] = 0; }
        units[slot]++;
        newest = tick;
        cachedTick = Long.MIN_VALUE;
    }

    public boolean alive(long tick) { return newest != Long.MIN_VALUE && tick >= newest && tick - newest < life; }

    private void sample(long tick) {
        if (cachedTick == tick) return;
        cachedTick = tick;
        depth = nextDepth = weightedAge = 0;
        for (int i = 0; i < units.length; i++) {
            if (born[i] == Long.MIN_VALUE || born[i] > tick || tick - born[i] >= life) continue;
            int age = (int) (tick - born[i]);
            double d = units[i] * curve[age];
            depth += d;
            nextDepth += units[i] * curve[age + 1];
            weightedAge += d * age;
        }
        weightedAge = depth > 0 ? weightedAge / depth : 0;
    }

    public float alpha(long tick, float partialTick) {
        sample(tick);
        double d = depth + (nextDepth - depth) * Math.clamp(partialTick, 0, 1);
        return (float) -Math.expm1(-d);
    }

    public int spriteAge(long tick) { sample(tick); return (int) weightedAge; }
    public double opticalDepth(long tick) { sample(tick); return depth; }
}
