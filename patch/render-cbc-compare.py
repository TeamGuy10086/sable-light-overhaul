"""Renders the fireball two ways, so the look can be judged against CBC's own.

Top row: CBC's alpha masks tinted by CBC's own gradient, sampled by age, which is as close as a plain image can
get to what CBC's particle does.
Bottom row: the same masks tinted by our own hot ramp.

Both at 384px with a good resampling filter, which also answers whether the earlier preview looked rough because
of resolution rather than because of the idea.
"""
import os, io, glob, zipfile
from PIL import Image

MODS = os.environ.get("MODS_DIR", "")
OUT = os.environ.get("OUT_DIR", ".")

FRAMES = 11
PANEL = 384
STEPS = 6
PREVIEW_FRAMES = 24
BACKGROUND = (24, 26, 30, 255)

# Our ramp: hot all the way, out by transparency rather than by darkening.
RAMP = [
    (0.00, (255, 255, 240, 255)),
    (0.15, (255, 246, 176, 255)),
    (0.35, (255, 216, 112, 255)),
    (0.55, (255, 172, 62, 255)),
    (0.75, (255, 134, 40, 235)),
    (0.90, (235, 100, 30, 130)),
    (1.00, (210, 82, 26, 0)),
]


def ramped(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(RAMP) - 1):
        a, ca = RAMP[i]
        b, cb = RAMP[i + 1]
        if a <= t <= b:
            k = 0.0 if b == a else (t - a) / (b - a)
            return tuple(round(ca[j] + (cb[j] - ca[j]) * k) for j in range(4))
    return RAMP[-1][1]


def load():
    hits = glob.glob(os.path.join(MODS, "*createbigcannons*.jar"))
    if not hits:
        raise SystemExit("no createbigcannons jar")
    masks, gradient = [], None
    with zipfile.ZipFile(hits[0]) as z:
        for i in range(FRAMES):
            with z.open("assets/createbigcannons/textures/particle/cannon_smoke_%d.png" % i) as f:
                masks.append(Image.open(io.BytesIO(f.read())).convert("RGBA"))
        with z.open("assets/createbigcannons/textures/particle/cannon_smoke_particle_gradient.png") as f:
            gradient = Image.open(io.BytesIO(f.read())).convert("RGBA")
    return masks, gradient


def describe(gradient):
    """Is CBC's gradient a strip stored square, or a two-dimensional blend? This decides how to sample it."""
    w, h = gradient.size
    cols = [tuple(sum(gradient.getpixel((x, y))[c] for y in range(h)) // h for c in range(4)) for x in range(w)]
    rows = [tuple(sum(gradient.getpixel((x, y))[c] for x in range(w)) // w for c in range(4)) for y in range(h)]
    print("gradient size:", gradient.size)
    print("per column:", " ".join("%02X%02X%02X" % c[:3] for c in cols))
    print("per row   :", " ".join("%02X%02X%02X" % r[:3] for r in rows))
    return cols


def cbc_colour(gradient, progress, hot_only=False):
    """CBC's own ramp, walked as the particle ages.

    Its 16 columns hold twelve real steps and then four magenta marker columns, and the twelve run from white-hot
    yellow to mid grey. That grey tail is what made a fireball look like a fog bank, so the second variant stops
    at the end of the hot part and lets transparency do the rest.
    """
    w, h = gradient.size
    usable = 6 if hot_only else 12          # 12 real steps; the last four columns are markers
    x = min(usable - 1, max(0, int(progress * usable)))
    return gradient.getpixel((x, h // 2))


def tint(mask, colour, fade, panel):
    size = int(mask.width * (panel / 16.0) * 0.62)
    big = mask.resize((size, size), Image.LANCZOS)
    px = [(colour[0], colour[1], colour[2], int(p[3] * fade * (colour[3] / 255.0))) for p in big.getdata()]
    out = Image.new("RGBA", big.size)
    out.putdata(px)
    return out, size


def render(masks, gradient, use_cbc):
    frames = []
    for f in range(PREVIEW_FRAMES):
        progress = f / (PREVIEW_FRAMES - 1)
        shape = min(FRAMES - 1, int(progress / 0.6 * FRAMES))
        colour = cbc_colour(gradient, progress, hot_only=not use_cbc)
        fade = 1.0 if progress < 0.33 else max(0.0, 1.0 - (progress - 0.33) / 0.67)
        blob, size = tint(masks[shape], colour, fade, PANEL)
        canvas = Image.new("RGBA", (PANEL, PANEL), BACKGROUND)
        canvas.alpha_composite(blob, ((PANEL - size) // 2, (PANEL - size) // 2 - int(progress * 24)))
        frames.append(canvas)
    return frames


def main():
    masks, gradient = load()
    describe(gradient)
    cbc = render(masks, gradient, True)      # CBC as it is: the whole ramp, grey tail included
    ours = render(masks, gradient, False)    # CBC own colours, stopped at the end of the hot part
    picks = [int(i * (PREVIEW_FRAMES - 1) / (STEPS - 1)) for i in range(STEPS)]
    sheet = Image.new("RGBA", (PANEL * STEPS, PANEL * 2), BACKGROUND)
    for i, p in enumerate(picks):
        sheet.alpha_composite(cbc[p], (i * PANEL, 0))
        sheet.alpha_composite(ours[p], (i * PANEL, PANEL))
    sheet.convert("RGB").save(os.path.join(OUT, "preview-compare.png"))
    cbc[0].save(os.path.join(OUT, "preview-cbc.gif"), save_all=True, append_images=cbc[1:],
                duration=83, loop=0, disposal=2)
    ours[0].save(os.path.join(OUT, "preview-ours.gif"), save_all=True, append_images=ours[1:],
                 duration=83, loop=0, disposal=2)
    print("wrote preview-compare.png (top: CBC full ramp, bottom: CBC hot part only)")


main()
