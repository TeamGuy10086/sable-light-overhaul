package dev.sablelightoverhaul;

import net.minecraft.resources.ResourceLocation;

/** Authored game-effect budgets. Missile body size and explosive power are separate inputs. */
public record InterceptionStyle(float hullScale, int metalColor, boolean explosive, float fireScale) {
    public static InterceptionStyle of(ResourceLocation missile, ResourceLocation warhead, float power) {
        float hull = 1;
        int metal = 0x776B63;
        if (missile.getNamespace().equals("wolfsmobs")) {
            hull = switch (missile.getPath()) {
                case "missile/agm179" -> .55f;
                case "missile/essm" -> .76f;
                case "missile/sm6" -> 1f;
                case "missile/lrasm" -> 1.16f;
                case "missile/vx51" -> 1.28f;
                default -> 1f;
            };
            metal = switch (missile.getPath()) {
                case "missile/vx51", "missile/lrasm" -> 0x6D625D;
                case "missile/sm6", "missile/essm" -> 0x9D9690;
                default -> metal;
            };
        }
        boolean explosive = power > 0 && (!warhead.getNamespace().equals("sablehealthbar")
            || !warhead.getPath().equals("none") && !warhead.getPath().equals("rods_ap_mk2"));
        float fire = explosive ? (float)Math.sqrt(Math.clamp(power, .25, 48) / 3.0) : 0;
        return new InterceptionStyle(hull, metal, explosive, fire);
    }

    public enum Detail {
        NEAR(5, 3, 6), MID(2, 1, 3), FAR(1, 1, 2);

        public final int hot, cold, fragments;

        Detail(int hot, int cold, int fragments) {
            this.hot = hot;
            this.cold = cold;
            this.fragments = fragments;
        }

        public static Detail at(double blocks) {
            if (blocks > InterceptionPayload.VISUAL_RANGE) return null;
            if (blocks <= 160) return NEAR;
            return blocks <= 640 ? MID : FAR;
        }
    }
}
