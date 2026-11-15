package dev.sablelightoverhaul.client;

/** Bounded pipeline counters; a render call proves submission, not successful per-pixel depth testing. */
public final class MuzzleVisualProbe {
    private static long autoSuppressed,autoTrailSuppressed,tankTrailSuppressed,tankNativeSmoke,tankOwnedSmoke,bigBorn,bigDraw;
    private static long smokeAliveTicks,smokeRemovedTicks,smokeDraws;
    private static double smokeTickAlpha,smokeDrawAlpha,smokeDrawSize;
    private static int smokeMinLife=Integer.MAX_VALUE,smokeMaxLife;
    private static long smokeEarlyRemoved;
    private static long autoBorn,tankBorn,autoDraw,tankDraw,tankGated,tankExpired,last=Long.MIN_VALUE,reported;
    private MuzzleVisualProbe() {}
    public static void bigBorn() { bigBorn++; }
    public static void bigDraw() { bigDraw++; }
    public static void born(boolean tank) { if(tank)tankBorn++;else autoBorn++; }
    public static void draw(boolean tank) { if(tank)tankDraw++;else autoDraw++; }
    public static void suppressedAutoPlume() { autoSuppressed++; }
    public static void autocannonTrailSuppressed() { autoTrailSuppressed++; }
    public static void tankTrailSuppressed() { tankTrailSuppressed++; }
    private static int spawnSamples,poseSamples;
    public static void ownedTankSmoke(){tankOwnedSmoke++;}
    public static void smokeSpawn(net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 at){
        if(spawnSamples>=4)return;spawnSamples++;
        com.mojang.logging.LogUtils.getLogger().info("SLO tank smoke world spawn: muzzle={} child={} offset={}",origin,at,at.distanceTo(origin));
    }
    public static void smokePose(double x,double y,double z,net.minecraft.world.phys.Vec3 eye,String sprite,float u0,float u1,float v0,float v1){
        if(poseSamples>=4)return;poseSamples++;
        var shader=com.mojang.blaze3d.systems.RenderSystem.getShader();
        var position=new net.minecraft.world.phys.Vec3(x,y,z);
        com.mojang.logging.LogUtils.getLogger().info("SLO tank smoke world draw: position={} eye={} distance={} sprite={} uv={},{},{},{} selectedShader={} texture0={}",
            position,eye,position.distanceTo(eye),sprite,u0,u1,v0,v1,shader==null?"null":shader.getName(),com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(0));
    }
    public static void nativeTankSmoke() { tankNativeSmoke++; }
    public static void smokeTick(boolean alive,int age,int lifetime,float alpha) {
        smokeMinLife=Math.min(smokeMinLife,lifetime);smokeMaxLife=Math.max(smokeMaxLife,lifetime);
        if(!alive&&age<lifetime)smokeEarlyRemoved++;
        if(alive){smokeAliveTicks++;smokeTickAlpha+=alpha;}else smokeRemovedTicks++;
    }
    public static void smokeDraw(float alpha,float size) {smokeDraws++;smokeDrawAlpha+=alpha;smokeDrawSize+=size;}
    public static void gated() { tankGated++; }
    public static void expired() { tankExpired++; }
    public static void clear() { bigBorn=bigDraw=autoBorn=tankBorn=autoDraw=tankDraw=tankGated=tankExpired=autoSuppressed=autoTrailSuppressed=tankTrailSuppressed=tankNativeSmoke=tankOwnedSmoke=reported=0;spawnSamples=poseSamples=0;smokeAliveTicks=smokeRemovedTicks=smokeDraws=smokeEarlyRemoved=0;smokeMinLife=Integer.MAX_VALUE;smokeMaxLife=0;smokeTickAlpha=smokeDrawAlpha=smokeDrawSize=0;last=Long.MIN_VALUE; }
    public static void tick(long now) {
        long changes=bigBorn+bigDraw+autoBorn+tankBorn+autoDraw+tankDraw+tankGated+tankExpired+autoSuppressed+autoTrailSuppressed+tankTrailSuppressed+tankNativeSmoke+tankOwnedSmoke+smokeAliveTicks+smokeRemovedTicks+smokeDraws;
        if(changes==reported || (last!=Long.MIN_VALUE && now>=last && now-last<20))return;
        last=now;reported=changes;
        com.mojang.logging.LogUtils.getLogger().info(
                "SLO muzzle visuals: autoBorn={} autoDrawCalls={} tankBorn={} tankDrawCalls={} tankGated={} tankExpired={} autoPlumeSuppressed={} autoTrailSuppressed={} tankTrailSuppressed={} tankNativeSmokeBorn={} tankOwnedSmokeBorn={} bigBorn={} bigDrawCalls={} (cumulative)",
                autoBorn,autoDraw,tankBorn,tankDraw,tankGated,tankExpired,autoSuppressed,autoTrailSuppressed,tankTrailSuppressed,tankNativeSmoke,tankOwnedSmoke,bigBorn,bigDraw);
        if(smokeAliveTicks+smokeDraws>0) com.mojang.logging.LogUtils.getLogger().info(
            "SLO tank smoke: aliveTickSamples={} removedTickSamples={} drawCalls={} meanTickAlpha={} meanDrawAlpha={} meanDrawSize={} lifeMin={} lifeMax={} earlyRemoved={} (cumulative; submission is not pixel visibility)",
            smokeAliveTicks,smokeRemovedTicks,smokeDraws,smokeTickAlpha/Math.max(1,smokeAliveTicks),smokeDrawAlpha/Math.max(1,smokeDraws),smokeDrawSize/Math.max(1,smokeDraws),smokeMinLife,smokeMaxLife,smokeEarlyRemoved);
    }
}
