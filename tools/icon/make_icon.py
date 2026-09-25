"""Builds Thor Pathfinder's launcher icon from tools/icon/icon-source.png.

    python tools/icon/make_icon.py [preview.png]

Writes app/src/main/res/drawable-nodpi/ic_launcher_foreground.png, the
adaptive icon's foreground: the artwork without its square frame, on solid
lavender, inside a ring in the frame's gradient. The background layer,
app/src/main/res/drawable/ic_launcher_background.xml, is the same gradient
over the same 108dp, so wherever a launcher's mask shows it, it matches the
ring. The Thor's own mask is a circle, and the ring ends exactly at the 72dp
viewport's circle. Give a path to also write a preview of the icon as the
Thor shows it, next to how a rounded-square launcher would. Needs Pillow.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "icon" / "icon-source.png"
DEST = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground.png"

LAVENDER = (250, 248, 255, 255)
N = 1024           # the artwork's size
BORDER = 27        # its gradient frame, in px
EXTENT = 523       # art radius shown: the outer circles reach 480, plus room
LAYER = 648        # 108dp at 6x
VIEW = LAYER * 72 // 108   # the 72dp the launcher's mask cuts from
SS = 4             # supersampling for smooth edges

src = Image.open(SRC).convert("RGBA")


def interior():
    m = Image.new("L", (N, N), 0)
    ImageDraw.Draw(m).rounded_rectangle([BORDER, BORDER, N - 1 - BORDER, N - 1 - BORDER], radius=150, fill=255)
    return m


def art():
    """The artwork without its frame, on solid lavender, VIEW px across."""
    side = 2 * EXTENT
    canvas = Image.new("RGBA", (side, side), LAVENDER)
    base = Image.new("RGBA", (N, N), LAVENDER)
    base.alpha_composite(src)  # the artwork's own background is partly see-through
    off = (side - N) // 2
    canvas.paste(base, (off, off), interior())
    return canvas.resize((VIEW, VIEW), Image.LANCZOS)


def disc(size, radius):
    """An antialiased filled circle centred in a size x size mask."""
    big = Image.new("L", (size * SS, size * SS), 0)
    c = size * SS / 2
    r = radius * SS
    ImageDraw.Draw(big).ellipse([c - r, c - r, c + r, c + r], fill=255)
    return big.resize((size, size), Image.LANCZOS)


def gradient(size, start, end):
    """The frame's colours, top-left to bottom-right, across the whole layer."""
    g = Image.new("RGBA", (size, size))
    px = g.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(int(a + (b - a) * t) for a, b in zip(start, end)) + (255,)
    return g


start = src.getpixel((40, 40))[:3]
end = src.getpixel((N - 41, N - 41))[:3]

radius = VIEW / 2
ring_width = VIEW * BORDER / N * 1.3
# The ring ends at the mask's edge. The background layer is the same
# gradient, so whatever the mask leaves at the rim is ring colour, and a
# launcher with a squarer mask fills its corners with the frame's gradient.
outer = radius
inner = radius - ring_width

off = (LAYER - VIEW) // 2
# Alpha is set rather than pasted through a mask: pasting onto a transparent
# canvas blends the edge pixels with black and leaves a dark rim. The art
# stops under the ring's solid middle, so the only outer edge is ring colour.
layer = Image.new("RGBA", (LAYER, LAYER), LAVENDER)
layer.paste(art(), (off, off))
layer.putalpha(disc(LAYER, inner + ring_width / 2))

ring = gradient(LAYER, start, end)
ring.putalpha(ImageChops.subtract(disc(LAYER, outer), disc(LAYER, inner)))
layer.alpha_composite(ring)

DEST.parent.mkdir(parents=True, exist_ok=True)
layer.save(DEST, optimize=True)
print("frame colours", start, end, "wrote", DEST.relative_to(ROOT))

if len(sys.argv) > 1:
    # What the Thor shows: the 72dp middle, cut to its circle, over the gradient background.
    shown = gradient(LAYER, start, end)
    shown.alpha_composite(layer)
    shown = shown.crop((off, off, off + VIEW, off + VIEW))
    circle = Image.new("RGBA", (VIEW, VIEW), (0, 0, 0, 0))
    circle.paste(shown, (0, 0), disc(VIEW, radius))
    rounded_mask = Image.new("L", (VIEW * SS, VIEW * SS), 0)
    ImageDraw.Draw(rounded_mask).rounded_rectangle([0, 0, VIEW * SS - 1, VIEW * SS - 1], radius=VIEW * SS // 4, fill=255)
    rounded = Image.new("RGBA", (VIEW, VIEW), (0, 0, 0, 0))
    rounded.paste(shown, (0, 0), rounded_mask.resize((VIEW, VIEW), Image.LANCZOS))

    sheet = Image.new("RGBA", (VIEW * 2 + 180, VIEW + 60), (24, 24, 28, 255))
    sheet.alpha_composite(circle, (20, 30))
    sheet.alpha_composite(rounded, (VIEW + 60, 30))
    sheet.alpha_composite(circle.resize((64, 64), Image.LANCZOS), (VIEW * 2 + 90, 60))
    sheet.convert("RGB").save(sys.argv[1])
    print("preview", sys.argv[1])
