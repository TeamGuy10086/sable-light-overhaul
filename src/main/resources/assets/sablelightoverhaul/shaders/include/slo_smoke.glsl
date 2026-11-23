// Continuous local smoke mask: two value-noise samples, independent of the pixel atlas.
float slo_smoke_hash(vec2 p) {
    vec3 q=fract(vec3(p.xyx)*0.1031);
    q+=dot(q,q.yzx+33.33);
    return fract((q.x+q.y)*q.z);
}


float slo_smoke_noise(vec2 p) {
    vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(slo_smoke_hash(i),slo_smoke_hash(i+vec2(1,0)),f.x),
               mix(slo_smoke_hash(i+vec2(0,1)),slo_smoke_hash(i+vec2(1,1)),f.x),f.y);
}
// Only the big-cannon reserved seed range executes the thermal material.
// Kept separate from optical density: cold smoke remains opaque after emissive pockets die.
float sloThermalHeat=0.0;
// Stable density threshold and independent optical fade for the current thermal fragment.
float sloThermalCut=0.0;
float sloThermalLife=1.0;
float sloThermalIslands=1.0;
vec3 slo_cold_smoke_palette(bool groundFireball) {
    return groundFireball ? vec3(.40,.41,.44) : vec3(.48,.50,.53);
}

vec4 slo_thermal_smoke(vec2 p,float encoded,bool groundFireball) {
    float id=mod(encoded-128.0,64.0),age=floor((encoded-128.0)/64.0)/32.0;
    vec2 shift=vec2(id*7.13,id*3.71);
    // Advection freezes once cooling saturates at age 1. The encoded age keeps counting for the
    // whole (much longer) lifetime, and a still-churning cold mask is invisible with soft alpha
    // but reads as every particle flickering once coverage is binary. The hot phase keeps
    // churning: that rolling is the wanted part of the effect.
    float ma=min(age,1.0);
    vec2 flow=vec2(slo_smoke_noise(p*1.6+shift+ma*.17),slo_smoke_noise(p*1.6+shift+vec2(11.7,3.1)-ma*.13))-.5;
    vec2 q=p+flow*.24;
    float coarse=slo_smoke_noise(q*3.8+shift+vec2(ma*.23,-ma*.16));
    float fine=slo_smoke_noise(q*10.5+shift*1.7+vec2(-ma*.11,ma*.2));
    float radius=length(q*vec2(.94,1.05));
    float edge=1.0-smoothstep(.44+.16*coarse,.78+.07*coarse,radius);
    vec2 support=1.0-smoothstep(vec2(.82),vec2(1.0),abs(p));
    float density=edge*(.60+.40*coarse)*(.75+.25*fine)*support.x*support.y;
    float pockets=coarse*.72+fine*.28;
    float cooling=clamp(age,0.0,1.0);
    sloThermalHeat=(1.0-cooling)*smoothstep(.28+cooling*.48,.60+cooling*.36,pockets);
    // A thick dark centre and brighter broken shoulders, with no camera-facing hard rim.
    float shade=.18+.32*coarse+.12*fine+.10*clamp(radius,0.0,1.0);
    vec3 cold=slo_cold_smoke_palette(groundFireball)*shade;
    vec3 hot=mix(vec3(.85,.20,.025),vec3(1.0,.82,.44),sloThermalHeat);
    return vec4(mix(cold,hot,sloThermalHeat),clamp(density,0.0,1.0));
}
vec4 slo_smoke_sample(vec2 p,float seed) {
    sloThermalHeat=0.0;
    if(seed>=128.0) return slo_thermal_smoke(p,seed,false);
    vec2 shift=vec2(seed*7.13,seed*3.71);
    float coarse=slo_smoke_noise(p*2.15+shift);
    float fine=slo_smoke_noise(p*6.2+shift*1.7);
    float low=slo_smoke_noise(p*0.85+shift*0.37);
    vec2 centre=0.075*vec2(sin(seed*1.3),cos(seed*0.9));
    // Warp the radius with a low-frequency field and angular lobes. The old mask was a
    // circle with almost constant rim; at 2-block puffs that reads as a flat sphere.
    vec2 q=p-centre;
    q+=0.065*vec2(sin(q.y*3.1+seed*1.7),cos(q.x*2.7-seed*1.3));
    float angle=atan(q.y,q.x);
    float radius=length(q*vec2(0.90+0.12*sin(seed*1.9),1.03+0.10*cos(seed*2.3)));
    float lobes=0.13*sin(angle*3.0+seed*2.1)+0.07*sin(angle*5.0-seed*1.4);
    float rim=0.62+0.16*(low-0.5)+lobes;
    float edge=1.0-smoothstep(rim-0.18,rim+0.10,radius);
    vec2 support=1.0-smoothstep(vec2(0.82),vec2(1.0),abs(p));
    // Break the interior into clumps instead of one uniform density cup.
    float clump=0.35+0.65*smoothstep(0.28,0.75,coarse*0.60+fine*0.40);
    float density=(0.45+0.55*low)*(0.55+0.45*fine);
    float alpha=edge*(0.45+0.55*clump)*(0.65+0.35*fine)*support.x*support.y;
    return vec4(vec3(0.80+0.25*low+0.06*fine),clamp(alpha,0.0,1.0));
}


// Ribbon-only density. U is continuous across shared joints, V spans -1..1.
// Two smooth samples; no per-segment radial support or periodic transverse stripes.
vec4 slo_ribbon_sample(vec2 p) {
    float coarse=slo_smoke_noise(vec2(p.x*2.3,p.y*1.8));
    float fine=slo_smoke_noise(vec2(p.x*6.1+19.7,p.y*4.7+8.1));
    float warped=p.y+(coarse-.5)*.22;
    float edge=1.0-smoothstep(.24+.10*coarse,.86,abs(warped));
    float support=1.0-smoothstep(.82,1.0,abs(p.y));
    float density=(.55+.45*coarse)*(.7+.3*fine);
    return vec4(vec3(.85+.15*coarse),edge*support*density);
}

// Linear DATA atlas, not a colour texture. Six 32-frame sequences; 128px cells + 2px gutter.
uniform sampler2D sloSmokeAtlas;
uniform sampler2D sloTankSmokeAtlas;
uniform sampler2D sloDustColumnAtlas;
uniform sampler2D sloSmokeMask;
uniform int sloSmokePerformance;
uniform int sloSmokeEdgeCull;
uniform int sloCachedSmokeSimpleLighting;
uniform float sloSmokeEdgeThreshold;

float slo_smoke_edge_min() {
    // Shader packs can be reloaded before the updated mod is installed.
    if(sloSmokeEdgeThreshold<=0.0) return 0.016;
    return clamp(sloSmokeEdgeThreshold,0.002,0.100);
}

float slo_soft_smoke_alpha(float alpha) {
    if(sloSmokeEdgeCull==0) return alpha;
    float cutoff = slo_smoke_edge_min();
    if(alpha<=cutoff) discard;
    return alpha*smoothstep(cutoff,cutoff+0.034,alpha);
}

// Six seed-stable tiles. Sample between texel centers without crossing a tile boundary.
vec2 slo_baked_smoke_mask(vec2 p, float id) {
    vec2 pixel = clamp(p*.5+.5,0.0,1.0)*127.0+0.5;
    pixel.y += mod(id,6.0)*128.0;
    return texture(sloSmokeMask,pixel/vec2(128.0,768.0)).rg;
}
uniform vec4 sloSmokeOptions; // baked material, simple lighting, ambient gain, emission gain
void slo_thermal_shape(vec2 p,float seed,vec4 tint) {
    bool thermal=abs(seed)>=128.0;
    bool erode=thermal && tint.g>0.0;
    bool detail=thermal && tint.b<0.998;
    float maskId=mod(abs(seed)-128.0,64.0);
    if(sloSmokePerformance==1 && (erode || detail)) {
        vec2 mask=slo_baked_smoke_mask(p,maskId);
        if(erode) {
            float edge=mix(0.94,0.43+0.16*mask.g,clamp(tint.g,0.0,1.0));
            float erosion=smoothstep(edge,edge+0.10,length(p));
            sloThermalIslands=1.0-0.58*erosion;
        }
        if(detail) sloThermalIslands*=pow(mix(0.42,1.0,mask.r),3.0*(1.0-tint.b));
    } else {
        if(erode) {
            // A noisy contour retreats inward. Along each ray erosion always connects to the rim.
            float radius=length(p);
            vec2 direction=p/max(radius,0.001);
            vec2 q=direction*1.65+vec2(maskId*1.37,maskId*2.71);
            float shape=0.76*slo_smoke_noise(q)+0.24*slo_smoke_noise(q*3.7+12.4);
            float progress=clamp(tint.g,0.0,1.0);
            float edge=mix(0.94,0.43+0.16*shape,progress);
            float erosion=smoothstep(edge,edge+0.10,radius);
            sloThermalIslands=1.0-0.58*erosion;
        }
        // Blue carries inverse late-density detail normalized to 0..3 for thermal smoke.
        if(detail) {
            vec2 q=p*7.0+vec2(maskId*3.71,maskId*1.37);
            float density=0.75*slo_smoke_noise(q)+0.25*slo_smoke_noise(q*2.3+9.2);
            float mottling=mix(0.42,1.0,smoothstep(0.23,0.75,density));
            sloThermalIslands*=pow(mottling,3.0*(1.0-tint.b));
        }
    }
}

vec4 slo_smoke_flipbook(vec2 p,float encoded,float steady,vec4 tint,bool groundFireball) {
    bool edgeCull=sloSmokeEdgeCull!=0;
    float cutoff=slo_smoke_edge_min();
    float maxLife=min(1.0,1.8*sloThermalLife);
    // Even the brightest dense core cannot survive the coverage cutoff at this point in its life.
    if(edgeCull && maxLife<=cutoff) discard;
    float id=mod(-encoded-128.0,64.0),age=clamp(floor((-encoded-128.0)/64.0)/32.0,0.0,8.0);
    float frame=age<=1.0?age*15.0:15.0+(age-1.0)*(16.0/7.0);
    float first=floor(frame),last=min(first+1.0,31.0),variant=mod(id,6.0);
    vec2 local=clamp(p/.8*.5+.5,0.0,1.0)*128.0+2.0;
    float a=variant*32.0+first,b=variant*32.0+last;
    vec2 uvA=(vec2(mod(a,8.0),floor(a/8.0))*132.0+local)/vec2(1056.0,3168.0);
    vec2 uvB=(vec2(mod(b,8.0),floor(b/8.0))*132.0+local)/vec2(1056.0,3168.0);
    vec4 data=mix(texture(sloSmokeAtlas,uvA),texture(sloSmokeAtlas,uvB),fract(frame));
    if(data.a<=0.0) discard;
    // The mask can only reduce coverage. Bound it with the steepest supported
    // core (Complementary) and its boosted lifespan before evaluating the mask.
    float alpha=1.0-pow(max(1.0-data.a,1e-5),steady);
    if(edgeCull) {
        float body=smoothstep(0.55*sloThermalCut,sloThermalCut+0.11,alpha);
        float fringe=0.35*smoothstep(0.0,sloThermalCut+0.20,alpha);
        float maxCoverage=maxLife*mix(fringe,1.0,body);
        if(maxCoverage<=cutoff) discard;
    }
    slo_thermal_shape(p,encoded,tint);
    float inverseCoverage=1.0/max(data.a,1e-5);
    vec3 albedo=slo_cold_smoke_palette(groundFireball)*(data.r*inverseCoverage);
    // Bake each lobe's nonlinear cooling before composition. Averaging noise first washes out pockets.
    // Data channels are cold shade*(1-heat), heat, heat^2; palette is linear in these moments.
    sloThermalHeat=0.0;
    if(age<1.0 && data.g>0.0) {
        sloThermalHeat=clamp(data.g*inverseCoverage,0.0,1.0);
        albedo+=vec3(.85,.20,.025)*sloThermalHeat+vec3(.15,.62,.415)*(data.b*inverseCoverage);
    }
    return vec4(albedo,alpha);
}

float slo_dust_column_alpha(float base, float maxLife) {
    float clear = 1.0 - smoothstep(0.02, 0.43, base);
    return (1.0 - clear * clear * clear) * maxLife;
}

// Cold atlas: RGB stores each lobe coverage, A stores composed premultiplied grey/1.2.
vec4 slo_tank_smoke_cached(vec2 p,float seed,vec4 tint) {
    sloThermalHeat=0.0;
    float id=abs(seed)-64.0;
    if (seed>=64.0) {
        float maxLife=min(1.0,tint.a*1.4);
        if(sloSmokeEdgeCull!=0 && maxLife<=slo_smoke_edge_min()) discard;
        if(any(greaterThanEqual(abs(p),vec2(1.0)))) discard;
        // Each tile is one baked frame of the dust eruption.
        vec2 tile = vec2(mod(id,8.0),floor(id/8.0));
        vec2 uv = (tile*192.0+clamp(p*.5+.5,0.0,1.0)*191.0+0.5)/1536.0;
        vec4 dust = texture(sloDustColumnAtlas,uv);
        float alpha = slo_dust_column_alpha(dust.a,maxLife);
        if (sloSmokeEdgeCull!=0) {
            // As life fades, the baked channels open holes in the less dense parts first.
            float erosion = 1.0-smoothstep(0.08,0.82,tint.a);
            float localDensity = dust.a * mix(0.55,1.0,dust.r);
            alpha *= smoothstep(erosion*0.58-0.05,erosion*0.58+0.10,localDensity);
            alpha = slo_soft_smoke_alpha(alpha);
        }
        float shade = mix(0.61,1.16,dust.r);
        return vec4(tint.rgb*shade,alpha);
    }
    bool impact=p.y>1.1;
    bool thickCore=impact && p.y<3.0;
    if(impact) p.y-=thickCore?2.0:4.0;
    vec2 uv=(vec2(mod(id,8.0),floor(id/8.0))*132.0+clamp(p/.8*.5+.5,0.0,1.0)*128.0+2.0)/1056.0;
    vec4 data=texture(sloTankSmokeAtlas,uv);
    vec3 layers=clamp(data.rgb,0.0,1.0);
    float base=1.0-(1.0-layers.r)*(1.0-layers.g)*(1.0-layers.b);
    float shade=data.a/max(base,1e-5)*1.2;
    if(impact) {
        // Keep the dense lobe highlights and bring out where two smoke lobes overlap.
        float overlap=layers.r*layers.g+layers.r*layers.b+layers.g*layers.b;
        float body=smoothstep(0.12,0.45,base);
        float contrast=clamp(0.97+2.7*(shade-0.97)-0.14*smoothstep(0.03,0.30,overlap),0.45,1.36);
        shade=mix(shade,contrast,body);
    }
    vec3 transmission=1.0-layers*clamp(tint.a,0.0,1.0);
    float alpha=1.0-transmission.r*transmission.g*transmission.b;
    if(thickCore) {
        // Increase optical thickness in the body while leaving the thinnest edges alone.
        float body=smoothstep(0.08,0.48,alpha);
        float remaining=1.0-alpha;
        alpha=1.0-remaining*mix(1.0,remaining*remaining,body);
    }
    return vec4(vec3(shade)*tint.rgb,alpha);
}

// Compose the three thermal lobes BEFORE scene lighting. Negative thermal seeds select this path.
// Coverage is composited with the real vertex alpha, not a max/union that changes smoke density.
//
// Red carries the steady density; alpha carries that density times the lifecycle envelope.
// Keep the density mask steady while the whole puff becomes more transmissive.
vec4 slo_smoke_tinted(vec2 p,float seed,vec4 tint) {
    sloThermalCut=0.0;
    sloThermalLife=1.0;
    sloThermalIslands=1.0;
    if((seed<=-64.0 && seed>-128.0) || (seed>=64.0 && seed<128.0)) return slo_tank_smoke_cached(p,seed,tint);
    bool groundFireball=seed<=-128.0 && p.y>1.1;
    if(groundFireball) p.y-=2.0;
    bool flipbook=seed<=-128.0 && sloSmokeOptions.x>.5;
    if(!flipbook) slo_thermal_shape(p,seed,tint);
    if(seed > -128.0) {
        if(seed >= 128.0) {
            // Single thermal lobe quad: the lobe weight is baked into both vertex channels.
            float steady=clamp(tint.r,0.05,1.0);
            sloThermalLife=clamp(tint.a/max(steady,1e-4),0.0,1.0);
            sloThermalCut=0.42*steady;
            vec4 s=slo_smoke_sample(p,seed);
            return vec4(s.rgb,s.a*steady);
        }
        return slo_smoke_sample(p,seed)*tint;
    }
    float steady=clamp(tint.r,0.05,1.0);
    sloThermalLife=clamp(tint.a/max(steady,1e-4),0.0,1.0);
    if(flipbook) {
        // Flipbook coverage peaks at 1 (not at the lobe union), so its cut is in post-pow alpha
        // units: at the default steady 0.48 this discards atlas coverage below ~0.20, the tail of
        // the baked distribution rather than its body.
        sloThermalCut=(1.0-pow(0.80,steady));
        return slo_smoke_flipbook(p,seed,steady,tint,groundFireball);
    }
    float encoded=-seed,id=mod(encoded-128.0,64.0),frame=floor((encoded-128.0)/64.0);
    vec3 premul=vec3(0.0);float alpha=0.0,heat=0.0;
    // Cut at the same raw mask density whatever the configured opacity: 0.22 of the composed
    // alpha a fully dense pixel would have at the steady opacity (lobe 1 alone then needs a raw
    // density of ~0.42, and lobes 2/3 can pass on their own instead of never reaching the cut).
    float full=1.0-(1.0-0.58*steady)*(1.0-0.44*steady)*(1.0-0.36*steady);
    sloThermalCut=0.22*full;
    for(int i=0;i<3;i++) {
        float scale=i==0?.68:i==1?.48:.40,weight=i==0?.58:i==1?.44:.36;
        vec2 offset=i==0?vec2(0):i==1?vec2(.22,.12):vec2(-.20,.16);
        float angle=i==0?0.0:i==1?.82:-1.13;
        vec2 q=(p-offset)/scale;
        float cs=cos(angle),sn=sin(angle);q=mat2(cs,-sn,sn,cs)*q;
        // Entirely outside this lobe: skip all four noise evaluations.
        if(length(q)>1.08 || any(greaterThan(abs(q),vec2(1.0)))) continue;
        vec4 value=slo_thermal_smoke(q,128.0+frame*64.0+mod(id+float(i)*17.0,64.0),groundFireball);
        float a=value.a*weight*steady;
        premul=premul*(1.0-a)+value.rgb*a;
        heat=heat*(1.0-a)+sloThermalHeat*a;
        alpha=alpha*(1.0-a)+a;
    }
    sloThermalHeat=alpha>0.0?heat/alpha:0.0;
    return vec4(alpha>0.0?premul/alpha:vec3(0.0),alpha);
}

// Keep a dense core and recover the soft tail of the same density mask.
vec4 slo_thermal_coverage(vec4 smoke) {
    if(smoke.a <= 0.0) discard;
    float core=smoothstep(0.55*sloThermalCut,sloThermalCut+0.20,smoke.a);
    float fringe=0.35*smoothstep(0.0,sloThermalCut+0.20,smoke.a);
    float coverage=core+(1.0-core)*fringe;
    // Thin the density locally while preserving smoke until the lifetime fade.
    coverage*=sloThermalIslands;
    return vec4(smoke.rgb,slo_soft_smoke_alpha(0.94*sloThermalLife*coverage));
}
