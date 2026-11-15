from math import floor, hypot
from pathlib import Path
from PIL import Image

SIZE = 128
SEEDS = (3, 12, 23, 34, 47, 58)


def fract(value):
    return value - floor(value)


def mix(a, b, t):
    return a + (b - a) * t


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def hash2(x, y):
    a, b, c = fract(x * .1031), fract(y * .1031), fract(x * .1031)
    dot = a * (b + 33.33) + b * (c + 33.33) + c * (a + 33.33)
    return fract((a + b + 2 * dot) * (c + dot))


def noise(x, y):
    ix, iy = floor(x), floor(y)
    fx, fy = smooth(x - ix), smooth(y - iy)
    return mix(mix(hash2(ix, iy), hash2(ix + 1, iy), fx),
               mix(hash2(ix, iy + 1), hash2(ix + 1, iy + 1), fx), fy)


def bake():
    image = Image.new("RGBA", (SIZE, SIZE * len(SEEDS)))
    for tile, seed in enumerate(SEEDS):
        for iy in range(SIZE):
            y = 2 * iy / (SIZE - 1) - 1
            for ix in range(SIZE):
                x = 2 * ix / (SIZE - 1) - 1
                # Match the realtime material in slo_smoke.glsl.
                bx, by = x * 7 + seed * 3.71, y * 7 + seed * 1.37
                density = .75 * noise(bx, by) + .25 * noise(2.3 * bx + 9.2, 2.3 * by + 9.2)
                radius = max(.001, hypot(x, y))
                ex, ey = x / radius * 1.65 + seed * 1.37, y / radius * 1.65 + seed * 2.71
                silhouette = .76 * noise(ex, ey) + .24 * noise(3.7 * ex + 12.4, 3.7 * ey + 12.4)
                image.putpixel((ix, tile * SIZE + iy),
                               (round(255 * smooth((density - .23) / .52)), round(255 * silhouette), 0, 255))
    target = Path(__file__).resolve().parents[1] / "src/main/resources/assets/sablelightoverhaul/textures/effect"
    image.save(target / "thermal_smoke_mask.png")


if __name__ == "__main__":
    bake()
