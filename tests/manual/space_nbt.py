"""Small read-only NBT/region inspector used for isolated Space smoke evidence (stdlib only)."""

import gzip
import io
import struct
import zlib
from pathlib import Path


def nbt(raw):
    stream = io.BytesIO(raw)

    def number(fmt):
        return struct.unpack(">" + fmt, stream.read(struct.calcsize(fmt)))[0]

    def string():
        return stream.read(number("H")).decode("utf-8")

    def payload(tag):
        if tag in range(1, 7):
            return number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[tag])
        if tag == 7:
            return stream.read(number("i"))
        if tag == 8:
            return string()
        if tag == 9:
            child, count = number("B"), number("i")
            return [payload(child) for _ in range(count)]
        if tag == 10:
            result = {}
            while child := number("B"):
                name = string()
                result[name] = payload(child)
            return result
        if tag in (11, 12):
            return [number("i" if tag == 11 else "q") for _ in range(number("i"))]
        raise ValueError(f"Unsupported NBT type {tag}")

    tag = number("B")
    string()
    return payload(tag)


def region_chunk(file, x=0, z=0):
    raw = Path(file).read_bytes()
    entry = int.from_bytes(
        raw[4 * ((x % 32) + (z % 32) * 32) : 4 * ((x % 32) + (z % 32) * 32) + 4], "big"
    )
    start = (entry >> 8) * 4096
    if not start:
        return None
    size = int.from_bytes(raw[start : start + 4], "big")
    compression, data = raw[start + 4], raw[start + 5 : start + 4 + size]
    return nbt({1: gzip.decompress, 2: zlib.decompress, 3: lambda b: b}[compression](data))


if __name__ == "__main__":
    import json
    import sys

    path = Path(sys.argv[1])
    print(
        json.dumps(
            region_chunk(path)
            if path.suffix == ".mca"
            else nbt(gzip.decompress(path.read_bytes())),
            indent=2,
        )
    )


def inspect_space_world(world):
    """Validate the final saved machine/ship contents even if an earlier console query was premature."""
    base = Path(world) / "dimensions/minecraft/overworld"
    blocks = region_chunk(base / "region/r.0.0.mca")["block_entities"]
    by_x = {b["x"]: b for b in blocks}

    def slot(x, index, item, count=1):
        return any(
            s["Slot"] == index and s["id"] == item and s["count"] == count for s in by_x[x]["Items"]
        )

    ship = next(
        e
        for e in region_chunk(base / "entities/r.0.0.mca")["Entities"]
        if "space_smoke_ship" in e.get("Tags", [])
    )
    return {
        "compressor_saved_plate": slot(4, 4, "dolfinspace:placa_acero"),
        "circuits_saved_basic": slot(6, 4, "dolfinspace:circuito_basico"),
        "refinery_saved_fuel_and_bucket": slot(8, 4, "dolfinspace:bidon_combustible")
        and slot(8, 5, "minecraft:bucket"),
        "loader_saved_two_empty_canisters": slot(3, 1, "dolfinspace:bidon_vacio", 2),
        "ship_saved_fuel": ship["Ship"]["fuel"] == 100,
        "ship_saved_cargo": any(
            s["Slot"] == 0 and s["id"] == "minecraft:diamond" and s["count"] == 3
            for s in ship["Items"]
        ),
        "ship_saved_owner_and_home": ship["Ship"]["owner"] == "00000000-0000-0000-0000-000000000001"
        and ship["Ship"]["home"] == 80,
    }
