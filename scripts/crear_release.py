#!/usr/bin/env python3
"""Spanish entry point: crear_release.py VERSION [--space]. Never publishes or uploads.
--space prepares a standalone Space integration bundle and dependency manifest;
without the flag, preserves the existing full-server release workflow.
"""

import hashlib
import json
import sys
import zipfile
from pathlib import Path

import build_release as release


def space(version):
    root = Path(__file__).resolve().parents[1]
    jars = list((root / "mods/space/build/libs").glob("*.jar"))
    if not jars:
        raise SystemExit("Build mods/space first")
    jar = max(
        (p for p in jars if not p.stem.endswith(("-sources", "-dev"))),
        key=lambda p: p.stat().st_mtime,
    )
    pack, index, mods = release.leer_pack()
    with zipfile.ZipFile(jar) as archive:
        metadata = json.loads(archive.read("fabric.mod.json"))
        if metadata["id"] != "dolfinspace":
            raise SystemExit("Wrong mod jar")
    manifest = {
        "version": version,
        "mod": metadata,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "pack": pack,
        "dependencies": mods,
        "clientAcceptance": "pending clean-client and multiplayer acceptance; see docs/espacio/PRUEBAS.md",
    }
    out = root / "dist" / f"DolfinSpace-{version}.zip"
    out.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(jar, "custom-mods/" + jar.name)
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        for p in [
            root / "CREDITS.md",
            *sorted((root / "pack").rglob("*.toml")),
            *sorted((root / "docs/space").glob("*")),
            *sorted((root / "docs/espacio").glob("*")),
        ]:
            if p.is_file():
                z.write(p, p.relative_to(root))
    print(out)


if __name__ == "__main__":
    if "--space" in sys.argv:
        sys.argv.remove("--space")
        if len(sys.argv) != 2:
            raise SystemExit(__doc__)
        space(sys.argv[1])
    else:
        release.main()
