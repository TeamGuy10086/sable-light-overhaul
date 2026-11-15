// Shared by the native particle shader and both patched Photon particle programs.
// slo_gas_sample: high-pressure gas burst field for gun (0) and tank (1) muzzle blasts.
// p = (axial 0..1 along firing axis, y, z lateral / half-width); v = (age 0..1, ds/(2W), kind, seed 0..64)
// Returns straight RGB, alpha = 1 - exp(-tau * density * v.y). v.y is used ONLY in the final Beer alpha.
// GLSL 150/400 compatible; no texture, arrays, in/out qualifiers.

float slo_gas_hash(vec3 q) {
    q = fract(q * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float slo_gas_noise(vec3 q) {
    vec3 i = floor(q);
    vec3 f = fract(q);
    f = f * f * (3.0 - 2.0 * f);
    float n000 = slo_gas_hash(i);
    float n100 = slo_gas_hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = slo_gas_hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = slo_gas_hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = slo_gas_hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = slo_gas_hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = slo_gas_hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = slo_gas_hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, f.x), mix(n010, n110, f.x), f.y),
               mix(mix(n001, n101, f.x), mix(n011, n111, f.x), f.y), f.z);
}

// Per-shot fixed seed offsets (stable per shot, no per-frame randomness).
vec3 slo_gas_seed_offset(float seed) {
    float s = fract(seed / 64.0);
    return vec3(slo_gas_hash(vec3(s, 1.7, 3.1)), slo_gas_hash(vec3(s, 5.3, 7.9)),
                slo_gas_hash(vec3(s, 9.2, 2.4))) * 97.0;
}

// Signed soft ridge: 1 at u=0, falls to 0 at |u| = w, edge width e (ascending edges).
float slo_gas_band(float u, float w, float e) {
    return 1.0 - smoothstep(max(w - e, 0.0), w, abs(u));
}

// Shared flow field, exactly two 3D noise evaluations.
// Coarse lattice is advected downstream along the axis by age. The fine lattice is sheared by the
// coarse value so fine structures twist, tear and drift together with the coarse field.
vec2 slo_gas_flow(vec3 p, float age, vec3 off) {
    vec3 q = vec3(p.x * 5.0 - 1.4 * age, p.y * 5.0, p.z * 5.0) + off;
    float coarse = slo_gas_noise(q);
    float k = (coarse - 0.5) * 2.2;
    float fine = slo_gas_noise(q * 2.83 + vec3(age * 0.8 + k * 0.9, 7.3 + k, 3.1 - k));
    return vec2(coarse, fine);
}

// Hot-core palette (gun scale): white-yellow core, narrow orange tint, thin red-orange rim.
vec3 slo_gas_palette(float core, float rim) {
    vec3 white = vec3(1.00, 0.96, 0.86);
    vec3 yellow = vec3(1.00, 0.84, 0.42);
    vec3 orange = vec3(1.00, 0.52, 0.14);
    vec3 ember = vec3(0.92, 0.24, 0.06);
    vec3 c = mix(orange, yellow, smoothstep(0.15, 0.65, core));
    c = mix(c, white, smoothstep(0.60, 0.95, core));
    return mix(c, ember, clamp(rim, 0.0, 1.0) * 0.85);
}

// Soft cylindrical support; never evaluate smoothstep with equal or reversed edges.
float slo_gas_support(vec3 p) {
    return smoothstep(0.0,0.012,p.x)*(1.0-smoothstep(0.88,1.0,p.x))
         * (1.0-smoothstep(0.84,0.98,length(p.yz)));
}

// ---------------------------------------------------------------------------------------------
// Autocannon lobed field. This is deliberately a modulation of the existing volume, not extra
// billboards: the ray integrator still sees one continuous gas body, but its outer envelope and
// axial reach vary with a soft angular field. At strength 0 every expression below falls back to
// the 0.1.505 shape exactly, which is the live A/B switch.
//
// sloAutoGas  = (lobe count, strength, twist with age, axial reach spread)
// sloAutoGas2 = (lobe sharpness, core focus, edge softness, reserved)
// ---------------------------------------------------------------------------------------------
uniform vec4 sloAutoGas;
uniform vec4 sloAutoGas2;

float slo_gas_lobe(vec3 p, float x, float age, vec3 off, float coarse, float fine) {
    float strength = clamp(sloAutoGas.y, 0.0, 1.0);
    if (strength <= 0.001) return 0.0;
    float count = clamp(sloAutoGas.x, 3.0, 6.0);
    // p.yz are already relative to the bent centreline. The +epsilon keeps atan defined on the axis.
    float angle = atan(p.z, p.y + 0.0001);
    // The phase drifts along the barrel and with age. Per-shot offsets keep two bursts from sharing
    // the same petal orientation, without re-randomising the shape every frame.
    float phase = off.x * 0.041 + age * sloAutoGas.z + x * (1.20 + 0.15 * off.y);
    float primary = cos(angle * count + phase);
    float secondary = cos(angle * (count + 2.0) - phase * 0.63 + off.y * 0.07);
    float shape = max(primary * 0.78 + secondary * 0.22, 0.0);
    float sharpness = clamp(sloAutoGas2.x, 1.0, 4.0);
    float petal = pow(shape, sharpness);
    // Noise breaks the rotational symmetry; a lobe is never a clean geometric petal.
    petal *= 0.55 + 0.35 * coarse + 0.25 * fine;
    return clamp(petal, 0.0, 1.0);
}

// ---------------------------------------------------------------------------------------------
// Gun: the blast expands in a cone directly from the muzzle -- no thin jet or throat section,
// which read as a detached bright rod in front of the muzzle and aliased into separate beads
// under the 12-sample integration. One non-convex axial stream envelope (cone-like width
// profile, bent/sheared centreline) plus a connected hot core from x=0, thin twisted sheets
// and noise-etched dark holes on the outer layer. Late life tears the stream open and leaves
// thin strands that fade.
// ---------------------------------------------------------------------------------------------
vec4 slo_gas_gun(vec3 p, vec4 v, vec3 off) {
    float x = p.x;
    float age = clamp(v.x, 0.0, 1.0);
    vec2 fl = slo_gas_flow(p, age, off);
    float c = fl.x;                                        // coarse: large tears, holes, skew
    float f = fl.y;                                        // fine: twisted sheets, strands
    float grow = smoothstep(0.10, 0.70, age);
    float late = smoothstep(0.45, 1.0, age);
    float sway = (c - 0.5) * 2.0;
    float skew = off.z / 97.0 - 0.5;                       // stable per-shot left/right bias

    // Length: forward creep with age, wobbling tip.
    float reach = mix(0.48, 0.91, grow) + (c - 0.5) * 0.10 * (1.0 - late);

    // Bent / sheared lateral centreline. The lobes need this before the tip is cut, because a
    // petal that points sideways also reaches a little farther than a gap between two petals.
    float cy = x * x * skew * 0.34 + sway * 0.15 * smoothstep(0.1, 0.9, x);
    float cz = sway * 0.10 * smoothstep(0.2, 1.0, x);
    float dy = p.y - cy;
    float dz = p.z - cz;
    float r = length(vec2(dy, dz * 1.15));
    float strength = clamp(sloAutoGas.y, 0.0, 1.0);
    float petal = slo_gas_lobe(vec3(x, dy, dz), x, age, off, c, f);
    float tailReach = reach * (1.0 - 0.65 * strength * max(sloAutoGas.w, 0.0) * (1.0 - petal));
    float tail = 1.0 - smoothstep(tailReach - 0.08, tailReach, x);

    // A moving crest and tapered head, not a constant-radius cylinder or ellipsoid.
    // The cone opens directly at the muzzle: a third of full width at x=0, full width by x~.10.
    float u=clamp((x-0.06)/max(reach-0.06,0.1),0.0,1.0);
    float opening=smoothstep(0.0,0.10,x);
    float crest=1.0-0.66*smoothstep(0.28,1.0,u);
    float W=mix(0.38,0.92,smoothstep(0.0,0.34,age))*mix(0.34,1.0,opening)*crest;
    W*=1.0+0.45*(c-0.5);
    W*=mix(1.0,0.82,late);
    // Petals push the envelope out; the gaps between them are left slightly narrower. At strength 0
    // this factor is exactly 1, so the old stream is unchanged.
    W*=1.0+0.45*strength*(petal-0.30);
    float t = r / max(W, 0.02);                            // normalised lateral coordinate

    // Non-convex stream envelope: soft ragged edge, occasional noise-driven bulge, then holes.
    float edge0=mix(0.45,mix(0.55,0.28,clamp(sloAutoGas2.z,0.0,1.0)),strength);
    float env = 1.0 - smoothstep(edge0, 1.0, t + (c - 0.5) * 0.45);
    float bulge = smoothstep(0.42, 0.60, c) * (1.0 - smoothstep(0.85, 1.05, t));
    env = max(env,bulge*0.85)*tail*smoothstep(0.0,0.035,x);
    float holes = smoothstep(0.48 - 0.12*late,0.69,c) * smoothstep(0.22, 0.50, t + (f - 0.5) * 0.20);
    env *= 1.0 - holes;
    // A shallow gap between petals keeps the silhouette from reading as a smooth cone. The strength
    // is deliberately lower than the width modulation: holes in a volume read as smoke, not flame.
    env *= mix(1.0, 0.35 + 0.65 * petal, strength * 0.90);

    // Core: connected to the muzzle from x=0, thinning and pitted later (local pockets, not a global scale).
    float coreR=mix(0.66,0.36,smoothstep(0.20,1.0,age));
    coreR*=mix(1.0,0.72,clamp(sloAutoGas2.y,0.0,1.0)*strength);
    float core = (1.0 - smoothstep(0.35, 1.0, r / max(W * coreR, 0.02)))
               * smoothstep(0.0, 0.06, x) * tail * (1.0 - 0.55 * late);
    core *= 1.0 - 0.85 * smoothstep(0.55, 0.72, c) * smoothstep(0.25, 0.50, r);
    core *= mix(1.0, 0.62 + 0.38 * petal, strength * 0.55);

    // Thin twisted sheets: narrow fine-noise band standing off the core.
    float sheet = (1.0 - smoothstep(0.08, 0.14, abs(f - 0.53)))
                * smoothstep(0.18, 0.36, r) * smoothstep(0.04, 0.16, x) * tail;
    sheet *= (1.0-smoothstep(0.72,1.12,t))*(1.0-late*smoothstep(0.50,0.70,c));

    // Mid-flame hollow that opens with age. There is deliberately no throat/pinch filament: a
    // structure a few percent of the half-width wide is far under one integration step, and it
    // sampled as a chain of isolated white beads instead of a line.
    float hole = (1.0 - smoothstep(0.015, 0.045, r)) * smoothstep(0.30, 0.50, x) * smoothstep(0.10, 0.40, late);

    // Advected fine structures are a ragged part of the stream, never two global planes.
    float strand=env*smoothstep(0.58,0.78,f)*smoothstep(0.3,0.65,age);
    float breakUp=mix(1.0,0.16+0.84*smoothstep(0.32,0.64,f),smoothstep(0.48,0.9,age));
    core*=breakUp;
    sheet*=env;

    float dens = core*(2.2+0.8*c)+env*(0.80+0.90*f)*breakUp+sheet*0.6 + strand * 0.7;
    dens *= 1.0 - 0.55 * hole;

    // Independent thermal radiance (about 1x palette); no peak modulation.
    float heat = clamp(core*(1.0+0.30*c)+sheet*0.4+env*(0.6+0.2*f), 0.0, 1.0);
    float rim = clamp(env + sheet + strand - heat * 0.75, 0.0, 1.0);
    return vec4(slo_gas_palette(heat, rim), dens);
}

// ---------------------------------------------------------------------------------------------
// Tank: tiny mouth kernel -> broad near-mouth cloud (lat > .85 at age~.2-.28, mass in x<.6),
// plus a thin forward jet; outer layers curl, tear and fracture late; hot core stays solid.
// ---------------------------------------------------------------------------------------------
// Each component is evaluated around the muzzle in its own scaled coordinates.
// The proxy encloses the largest component. Divide density by its relative scale
// so enlarging a different component does not dilute the unchanged component.
uniform vec4 sloTankGasScale; // core, cloud, jet, maximum; zero means legacy/default
uniform vec4 sloBigGasScale; // seed 32..40 identifies a big-cannon gas proxy
vec4 slo_tank_scales(float seed) {
    vec4 scales=seed>=32.0?sloBigGasScale:sloTankGasScale;
    return scales.w>0.0?scales:vec4(1.0);
}
float slo_tank_width(float x,float reach,float bloom,float settle,float sway) {
    float w=mix(0.10,0.92,bloom)*mix(1.0,0.86,settle)+0.08*sway*(1.0-settle);
    return w*mix(0.14,1.0,smoothstep(0.0,0.10,x))*(1.0-0.65*smoothstep(0.22,max(0.23,reach),x));
}
vec4 slo_gas_tank(vec3 p,vec4 v,vec3 off) {
    vec4 scales=slo_tank_scales(v.w);
    vec3 ratios=scales.xyz/scales.w;
    vec3 bp=p/ratios.y,cp=p/ratios.x,jp=p/ratios.z;
    float age=clamp(v.x,0.0,1.0);
    // Noise stays in muzzle-relative coordinates when another component changes the proxy.
    vec2 fl=slo_gas_flow(p*scales.w,age,off);
    float c=fl.x,f=fl.y,sway=(c-0.5)*2.0,skew=off.y/97.0-0.5;
    float bloom=smoothstep(0.02,0.24,age),settle=smoothstep(0.40,1.0,age),late=smoothstep(0.60,1.0,age);
    float reach=mix(0.24,0.60,smoothstep(0.0,0.40,age))+(c-0.5)*0.08;
    float tail=1.0-smoothstep(reach-0.12,reach,bp.x);
    vec2 bc=vec2(skew*0.20*bloom*smoothstep(0.0,0.5,bp.x)+sway*0.12*smoothstep(0.20,0.80,bp.x),sway*0.10*bloom);
    float t=length(bp.yz-bc)/max(slo_tank_width(bp.x,reach,bloom,settle,sway),0.02);
    float env=1.0-smoothstep(0.55,1.0,t+(c-0.5)*0.50);
    float curl=smoothstep(0.45,0.62,c)*(1.0-smoothstep(0.90,1.10,t));
    env=max(env*tail,curl*0.85)*tail;
    float tears=smoothstep(0.30-0.10*late,0.54,c)*smoothstep(0.30,0.60,t+(f-0.5)*0.20);
    env*=1.0-tears*(0.7+0.3*late);
    float sheet=(1.0-smoothstep(0.08,0.14,abs(f-0.50)))*smoothstep(0.25,0.45,t)
        *(1.0-smoothstep(0.90,1.10,t))*tail*smoothstep(0.0,0.10,bp.x);
    vec2 cc=vec2(skew*0.20*bloom*smoothstep(0.0,0.5,cp.x)+sway*0.12*smoothstep(0.20,0.80,cp.x),sway*0.10*bloom);
    float ct=length(cp.yz-cc)/max(slo_tank_width(cp.x,reach,bloom,settle,sway),0.02);
    float core=(1.0-smoothstep(0.30,0.85,ct))*smoothstep(0.0,0.05,cp.x)
        *(1.0-smoothstep(0.25,0.55,cp.x))*smoothstep(0.0,0.03,bloom);
    core*=1.0-0.55*late*smoothstep(0.35,0.60,c);
    float neck=(1.0-smoothstep(0.05,0.10,length(cp.yz-cc)))*smoothstep(0.0,0.015,cp.x)
        *(1.0-smoothstep(0.12,0.22,cp.x))*(1.0-0.6*late);
    vec2 jc=vec2(skew*0.20*bloom*smoothstep(0.0,0.5,jp.x)+sway*0.12*smoothstep(0.20,0.80,jp.x),sway*0.10*bloom);
    float jetLen=mix(0.12,0.92,smoothstep(0.0,0.15,age))*(1.0-0.35*settle);
    float jet=slo_gas_band((jp.y-jc.x)*1.3,0.09+0.06*bloom,0.040)*slo_gas_band(jp.z-jc.y,0.09+0.06*bloom,0.040);
    jet*=smoothstep(0.05,0.12,jp.x)*(1.0-smoothstep(jetLen*0.75,jetLen,jp.x))*mix(0.65,1.0,smoothstep(0.42,0.60,c));
    jet*=1.0-smoothstep(0.30,0.45,f)*smoothstep(0.2,0.5,late);
    float dens=(core*(3.0+0.9*c)+neck*1.6)*slo_gas_support(cp)/ratios.x
        +(env*(0.55+0.80*f)+sheet*0.9)*slo_gas_support(bp)/ratios.y
        +jet*6.0*slo_gas_support(jp)/ratios.z;
    dens*=1.0-0.45*smoothstep(0.70,1.0,age);
    float heat=clamp(core*(1.0+0.25*c)+neck*0.85+jet*0.60+sheet*0.30+env*0.10,0.0,1.0);
    float rim=clamp(env+sheet+jet-heat*0.80,0.0,1.0);
    vec3 col=slo_gas_palette(heat,rim)*(1.0+2.2*smoothstep(0.10,0.80,heat)*(1.0-0.65*late));
    return vec4(col,dens);
}

vec4 slo_gas_sample(vec3 p, vec4 v, float peak) {
    if (p.x < 0.0 || p.x > 1.0 || abs(p.y) > 1.0 || abs(p.z) > 1.0) return vec4(0.0);
    float tau = -log(max(1.0 - clamp(peak, 0.0, 0.995), 0.005));
    vec3 off = slo_gas_seed_offset(clamp(v.w, 0.0, 64.0));
    vec4 g = (v.z < 0.5) ? slo_gas_gun(p, v, off) : slo_gas_tank(p, v, off);
    float alpha = 1.0 - exp(-tau * max(g.a,0.0)*(v.z<0.5?slo_gas_support(p):1.0)*max(v.y,0.0));
    return vec4(g.rgb, clamp(alpha, 0.0, 1.0));
}

// One proxy fragment integrates forward along the actual perspective ray, clipped to opaque/LOD depth.
vec4 slo_gas_volume(vec3 p,vec4 params,float peak,vec3 view_pos,float scene_distance) {
    float aspect=max(params.y,0.001);
    vec3 q=vec3(p.x/aspect,p.yz);
    vec3 dx=dFdx(q),dy=dFdy(q);
    float lx=length(dx),ly=length(dy);
    if(lx<1e-9 || ly<1e-9)return vec4(0.0);
    vec3 right=dx/lx;
    vec3 up=normalize(dy-right*dot(right,dy)),back=normalize(cross(right,up));
    vec3 ray_physical=normalize(right*view_pos.x+up*view_pos.y+back*view_pos.z);
    vec3 ray=vec3(ray_physical.x*aspect,ray_physical.yz);
    float width=max(length(dFdx(view_pos))/lx,1e-5);
    float lo=0.0,hi=min(1e5,max(0.0,(scene_distance-length(view_pos))/width));
    vec3 lower=vec3(0.0,-1.0,-1.0),upper=vec3(1.0,1.0,1.0);
    for(int axis=0;axis<3;axis++) {
        if(abs(ray[axis])<1e-6) {
            if(p[axis]<lower[axis] || p[axis]>upper[axis])return vec4(0.0);
        } else {
            float a=(lower[axis]-p[axis])/ray[axis],b=(upper[axis]-p[axis])/ray[axis];
            lo=max(lo,min(a,b));hi=min(hi,max(a,b));
        }
    }
    if(hi<=lo)return vec4(0.0);
    int samples=int(clamp(floor(fract(params.z*2.0)*32.0+0.5),8.0,24.0));
    float step_length=(hi-lo)/float(samples),trans=1.0;
    vec3 sum=vec3(0.0);
    vec4 field_params=vec4(params.x,step_length*0.5,step(0.5,params.z),params.w);
    for(int i=0;i<24;i++) {
        if(i>=samples || trans<0.01)break;
        vec4 cell=slo_gas_sample(p+ray*(lo+(float(i)+0.5)*step_length),field_params,peak);
        sum+=trans*cell.a*cell.rgb;
        trans*=1.0-cell.a;
    }
    float alpha=1.0-trans;
    return vec4(sum/max(alpha,1e-6),alpha); // straight colour; Photon premultiplies once later
}
