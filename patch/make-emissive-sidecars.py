"""One-off: write the labPBR specular sidecars the particle sprites were missing.

Photon derives a surface's emission from the specular map's ALPHA channel:

    material.emission = max(material.emission, material.albedo * specular_map.a);

and it receives that specular map the way Iris/OptiFine's labPBR convention says: a second
texture named <sprite>_s.png beside the sprite. None of our particle sprites had one, so every
glowing particle had an emission of exactly zero no matter what the mod asked for -- which is
why the muzzle flash's emission "did not feel like anything".

The sidecars below are written as (R=0 smoothness, G=0 dielectric F0, B=0 porosity, A=255
emissive): a fully rough, non-metallic surface whose emission equals its own albedo. That keeps
a flame orange and a tracer white-hot instead of forcing one colour on every sprite.
"""
import os

from PIL import Image

FOLDER = r"D:\DSH\McMOD\work\sable-light-overhaul\src\main\resources\assets\sablelightoverhaul\textures\particle"

# Everything here is a light source drawn as a particle. Dust and smoke are deliberately absent:
# they are lit by the flash, they do not emit.
EMISSIVE = ["auto_muzzle.png", "fireball_gradient.png"] + \
           [f"fireball_mask_{i}.png" for i in range(11)] + \
           [f"trail_{i}.png" for i in range(4)]

for name in EMISSIVE:
    base = os.path.join(FOLDER, name)
    if not os.path.exists(base):
        print("missing, skipped:", name)
        continue
    sprite = Image.open(base)
    width, height = sprite.size
    sidecar = Image.new("RGBA", (width, height), (0, 0, 0, 255))
    out = os.path.join(FOLDER, name[:-4] + "_s.png")
    sidecar.save(out)
    print("wrote", os.path.basename(out), width, "x", height)
