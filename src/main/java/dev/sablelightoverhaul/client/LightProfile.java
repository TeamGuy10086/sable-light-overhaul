package dev.sablelightoverhaul.client;
import dev.sablelightoverhaul.*;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class LightProfile {
    public final ModConfigSpec.DoubleValue front,rear,width,height,cutoff,core,middle,tail,minimum,decay,reference,cap;
    public final ModConfigSpec.IntValue duration,hold;
    private final boolean diamond;
    public LightProfile(ModConfigSpec.Builder b,boolean diamond,double f,double r,double w,double cut,
                        int ticks,int peak,double rate,double ref) {
        this.diamond=diamond;
        b.push("shape");
        front=b.comment("光源沿炮口轴向到亮度归零的距离，单位格，范围 0.1~96：这只是正前方的半轴，斜向会提前变暗（大型炮是椭球，自动炮是菱形——三轴归一化距离相加）。Compatible 与 GPU 档的面光都读它，Legacy 档不读。").defineInRange("forwardRange",f,0.1,96.0);
        rear=b.comment("光源向后（炮尾方向）到亮度归零的距离，单位格，范围 0.1~96；它与 forwardRange 一起定出前后总长，比 rearClipDistance 更靠后的部分被裁掉。").defineInRange("rearRange",r,0.1,96.0);
        width=b.comment("光源的横向半径，单位格，范围 0.1~96：横向与垂向都在光源处最宽，沿炮口轴向收尖。大型炮按 referenceSize 等比缩放，自动炮是固定尺寸。").defineInRange("sideRange",w,0.1,96.0);
        height=b.comment("光源的垂向半径，单位格，范围 0.1~96，默认与 sideRange 相同；以世界竖直方向为参照，不随炮身滚转，调得和 sideRange 不同才会把光斑压扁或拉高。").defineInRange("verticalRange",w,0.1,96.0);
        cutoff=b.comment("在光源后方多少格就把光整块裁掉（近似炮口遮挡，不是真遮挡），单位格，范围 0.0~96，随尺寸缩放并受 maximumRange 封顶：设 0 会让光源后方的光全灭，设 >= rearRange 才等于关闭裁剪。").defineInRange("rearClipDistance",cut,0.0,96.0);
        b.pop().push("spatial");
        core=b.comment("归一化距离（0=光源，1=光场边界）内保持满亮的中心比例，范围 0~0.9：越大硬核越大，0 是纯软边光斑；它与 middleFraction、tailBrightness 一起构成三段空间衰减。").defineInRange("coreFraction",0.25,0.0,0.9);
        middle=b.comment("核心之后剩余距离中中间段所占的比例，范围 0.05~0.95：越小则亮度掉得越快，留给外层尾巴的距离越长（尾段斜率总比中间段缓）。").defineInRange("middleFraction",0.55,0.05,0.95);
        tail=b.comment("中间段结束处的亮度，范围 0~0.9；实际会被压到 (1-middleFraction)*0.95 以下，保证外层尾巴比中间段衰减更缓，调超过这个上限也不会让外圈发白。").defineInRange("tailBrightness",0.2,0.0,0.9);
        b.pop().push("time");
        duration=b.comment("本阶段存活多少 tick，范围 1~600；大型炮的 blast 与 smoke 是两段接力，各自从本段起点计时（烟雾从炮口保持期结束才开始）。Compatible 档还用 blast+smoke 的时长之和当整条闪光寿命，改它会直接改闪光的存在时间。").defineInRange("durationTicks",ticks,1,600);
        hold=b.comment("出现后亮度保持满值的 tick 数，范围 0~600，0 表示一出现就开始衰减；它与 duration、decayPerTick、minimumBrightness 属于同一条亮度曲线，Compatible 与 GPU 档都读。").defineInRange("peakHoldTicks",peak,0,600);
        minimum=b.comment("亮度衰减的下限，范围 0~1：大于 0 时阶段末尾仍留一层余光，直到 durationTicks 走完才熄灭。只在亮度曲线里生效；Compatible 档拼大型炮总寿命时那处按 0 处理，所以调高它不会延长闪光的存在时间。").defineInRange("minimumBrightness",0.0,0.0,1.0);
        decay=b.comment("保持期结束后每 tick 损失初始亮度的比例，范围 0~1（线性扣减，不是指数衰减）：0 表示一直亮到时间结束，1 表示一 tick 就掉到 minimumBrightness。").defineInRange("decayPerTick",rate,0.0,1.0);
        b.pop().push("scaling");
        reference=b.comment(diamond ? "自动炮不使用这一条：它固定尺寸，缩放参考值写死为 1，改了没有任何效果。" : "缩放参考值，范围 0.1~64：前后/横向/垂向半径都按 size/referenceSize 等比缩放，所以它越小光场越大。blast 段用炮弹的 CBC 装药强度当 size，烟雾段用按热度加权的烟雾粒子平均尺寸（没跟踪到烟雾时退回 plumeSize*0.25）。").defineInRange("referenceSize",ref,0.1,64.0);
        cap=b.comment("缩放后单个半径的上限，单位格，范围 1~96：前后/横向/垂向半径与 rearClipDistance 都被它截断，避免装药过强时胀出超大光场。").defineInRange("maximumRange",64.0,1.0,96.0);
        b.pop();
    }
    public LightCurve curve() { return new LightCurve(duration.get(),minimum.get(),decay.get(),hold.get()); }
    public AreaLight light(double x,double y,double z,double dx,double dy,double dz,double size,double strength) {
        double scale=diamond?1:Math.max(0,size)/reference.get();
        double f=LightSizing.radius(front.get(),scale,cap.get()),r=LightSizing.radius(rear.get(),scale,cap.get());
        double w=LightSizing.radius(width.get(),scale,cap.get()),h=LightSizing.radius(height.get(),scale,cap.get());
        double c=core.get(),fraction=middle.get();
        return AreaLight.directional(x,y,z,dx,dy,dz,f,r,w,h,Math.min(cap.get(),cutoff.get()*scale),
                c,c+(1-c)*fraction,Math.min(tail.get(),(1-fraction)*0.95),15*strength,diamond);
    }
    /**
     * One leaf's effective value: the carried form's number if it set one, otherwise the configuration's.
     *
     * <p>NaN means "the form does not set this leaf", which is why the absent value is a NaN rather than a
     * zero. Only the client knows what its own configuration says, so a form that sets one leaf has to leave
     * every other leaf exactly as it was rather than substitute a number the server invented.
     */
    private static double slot(float[] slots, int index, double configured) {
        if (slots == null || index < 0 || index >= slots.length) return configured;
        float value = slots[index];
        return Float.isNaN(value) ? configured : value;
    }

    /** The same curve, with the carried form's values wherever it has them. */
    public LightCurve curve(float[] slots) {
        return new LightCurve((int) slot(slots, 8, duration.get()), slot(slots, 10, minimum.get()),
                slot(slots, 11, decay.get()), (int) slot(slots, 9, hold.get()));
    }

    /**
     * The same light, with the carried form's values wherever it has them.
     *
     * <p>Slot order is the one FlashPayload documents, grouped by the section each leaf lives in: forwards,
     * rear, side, vertical, rear clip, then core, middle, tail, then duration, hold, minimum, decay, then
     * reference and maximum range.
     */
    public AreaLight light(double x, double y, double z, double dx, double dy, double dz,
                           double size, double strength, float[] slots) {
        double front0 = slot(slots, 0, front.get()), rear0 = slot(slots, 1, rear.get());
        double width0 = slot(slots, 2, width.get()), height0 = slot(slots, 3, height.get());
        double cutoff0 = slot(slots, 4, cutoff.get());
        double core0 = slot(slots, 5, core.get()), middle0 = slot(slots, 6, middle.get());
        double tail0 = slot(slots, 7, tail.get());
        double reference0 = slot(slots, 12, reference.get()), cap0 = slot(slots, 13, cap.get());
        double scale = diamond ? 1 : Math.max(0, size) / reference0;
        double f = LightSizing.radius(front0, scale, cap0), r = LightSizing.radius(rear0, scale, cap0);
        double w = LightSizing.radius(width0, scale, cap0), h = LightSizing.radius(height0, scale, cap0);
        return AreaLight.directional(x, y, z, dx, dy, dz, f, r, w, h, Math.min(cap0, cutoff0 * scale),
                core0, core0 + (1 - core0) * middle0, Math.min(tail0, (1 - middle0) * 0.95),
                15 * strength, diamond);
    }}
