"""Bake four 16-frame dust eruption sequences with linear shading and coverage."""
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image

TILE_SIZE = 192
TILES_PER_SIDE = 8
SIZE = TILE_SIZE * TILES_PER_SIDE
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/sablelightoverhaul"


def bake():
    source = (ROOT / "shaders/include/slo_smoke.glsl").read_text(encoding="utf-8")
    thermal = source[:source.index("vec4 slo_smoke_sample(")]
    context = moderngl.create_standalone_context(require=430)
    shader = context.compute_shader("#version 430\n" + thermal + """
layout (local_size_x=8,local_size_y=8) in;
layout (rgba8,binding=0) writeonly uniform image2D atlas;
uniform int tileSize;
uniform int tilesPerSide;
void main() {
    ivec2 pixel=ivec2(gl_GlobalInvocationID.xy);
    ivec2 tile=pixel/tileSize;
    vec2 local=vec2(pixel%tileSize)/float(tileSize-1);
    int tileId=tile.x+tile.y*tilesPerSide;
    float seed=float(tileId%4)*13.0+3.0;
    float age=float(tileId/4)/15.0;
    vec2 p=local*2.0-1.0;
    float spread=smoothstep(0.0,0.85,age);
    float fray=smoothstep(0.35,1.0,age);
    vec2 shift=vec2(seed*4.71,seed*1.59);
    vec2 flow=vec2(slo_smoke_noise(p*2.8+shift+vec2(0.0,-age*1.2)),
                  slo_smoke_noise(p*2.8+shift+vec2(8.7,-age*1.4)))-0.5;
    vec2 warped=p+flow*(0.16+0.19*spread);
    float granules=slo_smoke_noise(warped*19.0+shift*1.2-vec2(0.0,age*2.9));
    float clods=slo_smoke_noise(warped*7.8+shift-vec2(0.0,age*1.7));
    float alpha=0.0, premulShade=0.0;
    for(int i=0;i<3;i++){
        float scale=i==0?.67:i==1?.48:.43;
        vec2 offset=i==0?vec2(-.06,-.15):i==1?vec2(.20,.16):vec2(-.21,.26);
        offset.x*=1.0+.48*spread;
        offset.y+=float(i)*.08*spread;
        float angle=(i==0?0.0:i==1?.62:-.92)+(i==1?1.0:-1.0)*age*.37;
        float c=cos(angle),s=sin(angle);
        vec2 q=mat2(c,-s,s,c)*(warped-offset)/(scale*(.87+.16*spread));
        q.y*=.85+.17*spread;
        if(any(greaterThanEqual(abs(q),vec2(1.0)))) continue;
        vec4 lobe=slo_thermal_smoke(q,128.0+mod(seed+float(i)*17.0,64.0)+64.0*32.0,false);
        float density=clods*.72+granules*.28;
        float torn=mix(1.0,smoothstep(.22,.62,density),fray*.70);
        float coverage=lobe.a*(i==0?1.0:.88)*torn;
        float shade=clamp(lobe.r*2.1+.44*clods+.17*granules,0.0,1.0);
        premulShade=premulShade*(1.0-coverage)+shade*coverage;
        alpha=alpha*(1.0-coverage)+coverage;
    }
    vec2 support=1.0-smoothstep(vec2(.85),vec2(1.0),abs(p));
    imageStore(atlas,pixel,vec4(premulShade/max(alpha,1e-5),0.0,0.0,alpha*support.x*support.y));
}
""")
    atlas = context.texture((SIZE, SIZE), 4, dtype="f1")
    atlas.bind_to_image(0, read=False, write=True)
    shader["tileSize"] = TILE_SIZE
    shader["tilesPerSide"] = TILES_PER_SIDE
    shader.run(SIZE // 8, SIZE // 8)
    context.memory_barrier()
    pixels = np.frombuffer(atlas.read(), dtype=np.uint8).reshape(SIZE, SIZE, 4)
    for y in range(0, SIZE, TILE_SIZE):
        for x in range(0, SIZE, TILE_SIZE):
            tile = pixels[y:y + TILE_SIZE, x:x + TILE_SIZE]
            assert tile[:, :, 3].max() > 100
            assert not tile[0, :, 3].any() and not tile[-1, :, 3].any()
            assert not tile[:, 0, 3].any() and not tile[:, -1, 3].any()
    assert not np.array_equal(pixels[:TILE_SIZE, :TILE_SIZE], pixels[-TILE_SIZE:, 4*TILE_SIZE:5*TILE_SIZE])
    Image.fromarray(pixels).save(ROOT / "textures/effect/dust_column_smoke.png")


if __name__ == "__main__":
    bake()
