"""Builds the muzzle-fireball preview and the textures it is made of.

Two outputs:
  * the copied CBC smoke masks and our own orange gradient, written into the mod's assets;
  * a GIF that shows what the particle will look like over its whole life, so the look can be judged before
    any of it is wired into the game.

The masks are alpha shapes: CBC's frames are pure black with the cloud in the alpha channel, which is why the
orange cannot come from them. The gradient is ours, sampled by age, and only its first part is used -- the
yellow-to-orange part -- so the dark, slow smoke of CBC's later life never appears.
"""
import os, io, math, zipfile, glob
from PIL import Image, ImageDraw

MODS = os.environ.get("MODS_DIR", "")
OUT = os.environ.get("OUT_DIR", ".")
ASSETS = os.environ.get("ASSETS_DIR", ".")
FRAMES = 11
GRADIENT_STEPS = 16
# How the fireball should look: fast out, quick fade, gone before CBC's smoke phase would begin.
LIFETIME_TICKS = 10
PREVIEW_FRAMES = 24
PREVIEW_SIZE = 256

# The part of the gradient the fireball uses, as a fraction of its length. Only the hot end.
GRADIENT_USE = 0.55
# Colour ramp: white-hot, yellow, orange, and then out by transparency rather than by darkening. A fireball
# that turns brown on its way out reads as smoke; this one stays hot and simply stops existing.
RAMP = [
    (0.00, (255, 255, 240, 255)),
    (0.15, (255, 246, 176, 255)),
    (0.35, (255, 216, 112, 255)),
    (0.55, (255, 172, 62, 255)),
    (0.75, (255, 134, 40, 235)),
    (0.90, (235, 100, 30, 130)),
    (1.00, (210, 82, 26, 0)),
]


def jar_path():
    hits = glob.glob(os.path.join(MODS, "*createbigcannons*.jar"))
    if not hits:
        raise SystemExit("no createbigcannons jar under " + MODS)
    return hits[0]


def ramp_colour(t):
    t = max(0.0, min(1.0, t))
    for i in range(len(RAMP) - 1):
        a, ca = RAMP[i]
        b, cb = RAMP[i + 1]
        if a <= t <= b:
            k = 0.0 if b == a else (t - a) / (b - a)
            return tuple(round(ca[j] + (cb[j] - ca[j]) * k) for j in range(4))
    return RAMP[-1][1]


def build_gradient():
    im = Image.new("RGBA", (GRADIENT_STEPS, 1))
    for x in range(GRADIENT_STEPS):
        # The texture spans the hot end of the ramp; the game samples it by age.
        im.putpixel((x, 0), ramp_colour(GRADIENT_USE * x / (GRADIENT_STEPS - 1)))
    return im


def extract_masks():
    masks = []
    with zipfile.ZipFile(jar_path()) as z:
        for i in range(FRAMES):
            name = "assets/createbigcannons/textures/particle/cannon_smoke_%d.png" % i
            with z.open(name) as f:
                masks.append(Image.open(io.BytesIO(f.read())).convert("RGBA"))
    return masks


def save_assets(masks, gradient):
    part = os.path.join(ASSETS, "textures", "particle")
    os.makedirs(part, exist_ok=True)
    for i, m in enumerate(masks):
        # CBC's masks are pure black with the cloud in the alpha channel, and a particle is drawn as
        # texture times colour -- so a black texture stays black whatever colour the code asks for. Only the
        # channels are the shape, so the colour is replaced with white and the alpha is left exactly as it is.
        white = Image.new("RGBA", m.size)
        white.putdata([(255, 255, 255, p[3]) for p in m.getdata()])
        white.save(os.path.join(part, "fireball_mask_%d.png" % i))
    gradient.save(os.path.join(part, "fireball_gradient.png"))
    return part


def preview(masks, gradient):
    """The animation as it will read in game: shape from the frames, colour and alpha from the gradient."""
    background = (24, 26, 30, 255)
    frames = []
    for f in range(PREVIEW_FRAMES):
        progress = f / (PREVIEW_FRAMES - 1)
        # The shape walks the mask sequence over the first part of the life, then holds the last frame while it
        # fades: growth is fast, dissipation is by opacity, which is how the dust does it too.
        shape = min(FRAMES - 1, int(progress / 0.6 * FRAMES))
        mask = masks[shape]
        colour = gradient.getpixel((min(GRADIENT_STEPS - 1, int(progress * GRADIENT_STEPS)), 0))
        # Alpha: full until a third of the life, then a straight fade to nothing.
        fade = 1.0 if progress < 0.33 else max(0.0, 1.0 - (progress - 0.33) / 0.67)
        # The blob grows a little and drifts upward, so the preview shows motion as well as colour.
        scale = 0.45 + 0.55 * min(1.0, progress / 0.45)
        size = int(mask.width * scale * (PREVIEW_SIZE / mask.width) * 0.9)
        blob = mask.resize((size, size), Image.BICUBIC)
        tinted = Image.new("RGBA", blob.size)
        px = []
        for p in blob.getdata():
            a = int(p[3] * fade * (colour[3] / 255.0))
            px.append((colour[0], colour[1], colour[2], a))
        tinted.putdata(px)
        canvas = Image.new("RGBA", (PREVIEW_SIZE, PREVIEW_SIZE), background)
        x = (PREVIEW_SIZE - size) // 2
        y = (PREVIEW_SIZE - size) // 2 - int(progress * 18)
        canvas.alpha_composite(tinted, (x, y))
        canvas.convert("P", palette=Image.ADAPTIVE, colors=128)
        frames.append(canvas)
    out = os.path.join(OUT, "preview-fireball.gif")
    frames[0].save(out, save_all=True, append_images=frames[1:], duration=1000 // 12, loop=0, disposal=2)
    # A sheet as well as the animation: a still that shows the whole life at once is far easier to judge than a
    # single frame of a GIF, and it is the thing that can be looked at without pressing play.
    picks = [frames[int(i * (len(frames) - 1) / 5)] for i in range(6)]
    sheet = Image.new("RGBA", (PREVIEW_SIZE * 6, PREVIEW_SIZE), background)
    for i, frame in enumerate(picks):
        sheet.alpha_composite(frame.convert("RGBA"), (i * PREVIEW_SIZE, 0))
    sheet.convert("RGB").save(os.path.join(OUT, "preview-fireball-sheet.png"))
    return out


def main():
    masks = extract_masks()
    gradient = build_gradient()
    part = save_assets(masks, gradient)
    gif = preview(masks, gradient)
    print("assets written to", part)
    print("frames:", len(masks), "gradient:", gradient.size)
    print("preview:", gif)


main()
