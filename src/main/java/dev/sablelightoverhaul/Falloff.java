package dev.sablelightoverhaul;

import java.util.ArrayList;
import java.util.List;

/**
 * The named angular profiles a spotlight, a disco ball and the flashlight's cross-section are set to.
 *
 * <p>One table, one meaning: the shader takes three knee fractions and a brightness scale, and these are the
 * names a player picks them by. It lives here rather than in the spotlight's block class because the disco
 * ball and the flashlight offer the same five, and because a table that reads "which of these may a page
 * offer" should not need a level, a registration or a block to answer.
 *
 * <p>The sixth is <b>disabled</b>. The physical inverse-square term is a flag rather than a curve (a negative
 * core fraction that the shader's sphere branch tests for), and it is documented as buggy; see the TODO's
 * §11-T21. Offering it in a dropdown is a promise the mod cannot keep, so no page lists it and the
 * management tool does not cycle onto it. A lamp already stored on it keeps showing it -- the label says it
 * is disabled -- because otherwise the page would show a neighbour's name and quietly move the lamp there.
 */
public final class Falloff {
    private Falloff(){}
    /** Names, in the order the block state and the page rows use. */
    public static final String[] NAMES={"强制最大","快速衰减","均匀衰减","缓慢衰减","昏暗","物理平方反比"};
    /** The index of the disabled profile: the last one. */
    public static final int DISABLED=NAMES.length-1;
    /** The profiles a configuration page may offer, plus the disabled one only if already stored. */
    public static List<String> choices(int current){
        List<String> labels=new ArrayList<>(NAMES.length);
        for(int i=0;i<DISABLED;i++)labels.add(NAMES[i]);
        if(current>=DISABLED)labels.add(NAMES[DISABLED]+"（已停用）");
        return labels;
    }
    /** The next profile the management tool cycles to: the ones that are offered, in order. */
    public static int next(int mode){
        return mode<0||mode>=DISABLED?0:(mode+1)%DISABLED;
    }
    /** A stored mode clamped to something that exists, including the disabled one it may already be on. */
    public static int clamp(int mode){
        return Math.max(0,Math.min(DISABLED,mode));
    }
    /** The label for a stored mode, for a chat line or a tooltip. */
    public static String name(int mode){
        return NAMES[clamp(mode)];
    }
}
