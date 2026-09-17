"""Check the built replay port against its own core, patches and API reports."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--addon", type=Path, required=True)
parser.add_argument("--core", type=Path, required=True)
parser.add_argument("--mc", required=True)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
jar = args.addon / f"build/libs/irlite-1.1.7+mc{args.mc}.jar"
expected_major = 61 if args.mc.startswith("1.20.") else 65
with zipfile.ZipFile(jar) as archive:
    cores = [n for n in archive.namelist() if n.startswith("META-INF/jars/irl-core")]
    assert len(cores) == 1, cores
    core_bytes = archive.read(cores[0])
    assert core_bytes == args.core.read_bytes(), "Nested core differs from this MC build"
    with zipfile.ZipFile(io.BytesIO(core_bytes)) as core:
        assert "org/qualet/irl/light/LightProfilesBuffer.class" in core.namelist()
        major = int.from_bytes(core.read("org/qualet/irl/light/LightBuffer.class")[6:8], "big")
        assert major == expected_major, major
        assert json.loads(core.read("fabric.mod.json"))["version"] == "1.1.7"
    patches = list((args.addon / "patches").glob("*.irlights"))
    assert len(patches) == 7
    for patch in patches:
        assert archive.read("assets/irlite/patches/" + patch.name) == patch.read_bytes(), patch
    metadata = json.loads(archive.read("fabric.mod.json"))
    assert metadata["version"] == "1.1.7+mc" + args.mc
    assert "bbs-client-addon" in metadata["entrypoints"]
    if not args.mc.startswith("1.20."):
        assert metadata["depends"]["minecraft"] == "~" + args.mc
    for mixin_config in ("irlite.mixins.json", "irlite.client.mixins.json"):
        config = json.loads(archive.read(mixin_config))
        for name in config.get("mixins", []) + config.get("client", []):
            assert (config["package"] + "." + name).replace(".", "/") + ".class" in archive.namelist(), name
api = json.loads((args.addon / "build/profiles-bbs-test/api-report.json").read_text())
assert api["minecraft"] == args.mc and api["status"] == "PASS", api
result = {
    "minecraft": args.mc,
    "jar": str(jar.resolve()),
    "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
    "nestedCoreMatches": True,
    "coreSha256": hashlib.sha256(core_bytes).hexdigest(),
    "javaClassMajor": major,
    "bundledPatches": len(patches),
    "api": api,
    "status": "PASS",
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + "\n")
print(f"Replay port bundle PASS: {args.mc}; core, {len(patches)} patches, mixin classes, bytecode anchors")
