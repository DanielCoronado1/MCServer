"""Render actual server block slices as a diagnostic plan, never a client screenshot.

uv run --with pillow python -m tests.manual.inspeccionar_estacion \
    runtime/stage3-final/station-block-slices.json
"""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("slices", type=Path)
    args = parser.parse_args()
    data = json.loads(args.slices.read_text())
    colors = {
        "air": "#122032",
        "smooth_quartz": "#dfe5e5",
        "quartz_bricks": "#b9cbd0",
        "quartz_pillar": "#e4eeee",
        "glass": "#80b4c4",
        "sea_lantern": "#a4fff0",
        "polished_deepslate": "#324553",
        "lime_concrete": "#a3cf43",
        "green_concrete": "#587a34",
        "blue_concrete": "#304a9b",
        "cyan_concrete": "#21a7ba",
        "orange_concrete": "#dd9b43",
        "yellow_concrete": "#e6c84f",
        "stone_button": "#eaa4a4",
        "diamond_block": "#00f7dd",
        "bedrock": "#484858",
    }
    result = Image.new("RGB", (1250, 1250), "#0d1727")
    draw = ImageDraw.Draw(result)
    draw.text((20, 8), "ACTUAL SERVER BLOCKS / PLAN ONLY / NOT A CLIENT SCREENSHOT", fill="white")
    for i, (world, layers) in enumerate(data.items()):
        for j, (y, rows) in enumerate(list(layers.items())[:3]):
            ox, oy, scale = 20 + j * 410, 55 + i * 405, 4
            draw.text((ox, oy - 20), world + " y=" + y, fill="white")
            for z, row in enumerate(rows):
                for x, block in enumerate(row):
                    color = colors.get(block.removeprefix("minecraft:"), "#b276ad")
                    draw.rectangle(
                        (ox + x * scale, oy + z * scale, ox + x * scale + 3, oy + z * scale + 3),
                        fill=color,
                    )
    target = args.slices.with_name("station-plan-inspection.png")
    result.save(target)
    print(target)


if __name__ == "__main__":
    main()
