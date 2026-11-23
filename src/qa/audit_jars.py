#!/usr/bin/env python3
"""Static production JAR auditor for all 4 TyC2 targets."""

from __future__ import annotations

import json
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def _read_mod_version(props_path: Path) -> str:
    for line in props_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("mod_version="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError(f"Missing mod_version in {props_path}")


NEOFORGE_VER = _read_mod_version(ROOT / "gradle.properties")
FORGE_VER = _read_mod_version(ROOT / "forge-1.19.2/gradle.properties")

BANNED_ADVERTISING_PHRASES = (
    "1000 格",
    "1000格",
    "千格",
    "0-499",
    "500-999",
    "防爆",
    "防破坏",
    "blast-proof",
    "1000-slot",
    "1,000-slot",
    "CL:3x3",
)

TARGETS = [
    (
        "neoforge-1.21.1",
        ROOT / f"src/neoforge/1.21.1/build/libs/tyc2-neoforge-1.21.1-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        False,
    ),
    (
        "neoforge-26.1.2",
        ROOT / f"src/neoforge/26.1.2/build/libs/tyc2-neoforge-26.1.2-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        True,
    ),
    (
        "neoforge-26.2",
        ROOT / f"src/neoforge/26.2/build/libs/tyc2-neoforge-26.2-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        True,
    ),
    (
        "forge-1.19.2",
        ROOT / f"forge-1.19.2/build/libs/tyc2-forge-1.19.2-{FORGE_VER}.jar",
        "META-INF/mods.toml",
        True,
        False,
    ),
]


def audit_jar(name: str, jar_path: Path, mods_toml_path: str, is_forge: bool, is_26x: bool) -> list[str]:
    issues: list[str] = []
    if not jar_path.is_file():
        return [f"[{name}] Missing JAR: {jar_path}"]
    size = jar_path.stat().st_size
    if size < 50_000:
        issues.append(f"[{name}] Suspiciously small JAR ({size} bytes): {jar_path}")

    with zipfile.ZipFile(jar_path, "r") as zf:
        entries = set(zf.namelist())
        class_entries = [e for e in entries if e.endswith(".class")]

        # Check QA leak & preview leak
        qa_leaks = [e for e in entries if "/qa/" in e or "tyc2_qa" in e]
        if qa_leaks:
            issues.append(f"[{name}] QA leaks in production JAR: {qa_leaks[:5]}")
        preview_leaks = [e for e in entries if "preview" in e.lower() and e.endswith(".png")]
        if preview_leaks:
            issues.append(f"[{name}] Preview PNG leaked in production JAR: {preview_leaks}")

        # Check LICENSE
        if "LICENSE" not in entries:
            issues.append(f"[{name}] Missing LICENSE in JAR")

        # Check mods.toml / neoforge.mods.toml
        if mods_toml_path not in entries:
            issues.append(f"[{name}] Missing {mods_toml_path}")
        else:
            toml_text = zf.read(mods_toml_path).decode("utf-8")
            unexpanded = re.findall(r"\$\{[^}]+\}", toml_text)
            if unexpanded:
                issues.append(f"[{name}] Unexpanded template vars in {mods_toml_path}: {unexpanded}")
            if 'modId="tyc2"' not in toml_text and 'modId = "tyc2"' not in toml_text:
                issues.append(f"[{name}] modId=tyc2 not found in {mods_toml_path}")
            # Verify ic2 / lc2 are NOT mandatory dependencies
            for opt_mod in ("ic2", "lc2"):
                pattern = re.compile(
                    rf'modId\s*=\s*"{opt_mod}".*?(?:mandatory\s*=\s*true|type\s*=\s*"required")',
                    re.DOTALL,
                )
                for block in toml_text.split("[[dependencies"):
                    if pattern.search(block):
                        issues.append(f"[{name}] Optional mod '{opt_mod}' marked REQUIRED/mandatory in {mods_toml_path}!")

        # Check mixins JSON
        if "tyc2.mixins.json" not in entries:
            issues.append(f"[{name}] Missing tyc2.mixins.json")
        else:
            mixin_cfg = json.loads(zf.read("tyc2.mixins.json").decode("utf-8"))
            if not mixin_cfg.get("plugin"):
                issues.append(f"[{name}] tyc2.mixins.json missing plugin (needed for conditional IC2 mixins)")
            pkg = mixin_cfg.get("package", "").replace(".", "/")
            for key in ("mixins", "client", "server"):
                for cls in mixin_cfg.get(key, []):
                    cls_path = f"{pkg}/{cls.replace('.', '/')}.class"
                    if cls_path not in entries:
                        issues.append(f"[{name}] Mixin class missing from JAR: {cls_path}")
            plugin_path = mixin_cfg.get("plugin", "").replace(".", "/") + ".class"
            if plugin_path not in entries:
                issues.append(f"[{name}] Mixin plugin class missing from JAR: {plugin_path}")

        # Forge MANIFEST.MF check
        if is_forge:
            manifest = zf.read("META-INF/MANIFEST.MF").decode("utf-8") if "META-INF/MANIFEST.MF" in entries else ""
            if "MixinConfigs: tyc2.mixins.json" not in manifest:
                issues.append(f"[{name}] Forge MANIFEST.MF missing 'MixinConfigs: tyc2.mixins.json'")

        # Assets checks (35 textures: 18 block + 11 item + 6 GUI)
        pngs = [e for e in entries if e.startswith("assets/tyc2/textures/") and e.endswith(".png")]
        if len(pngs) < 35:
            issues.append(f"[{name}] Expected >=35 textures under assets/tyc2/textures/, found {len(pngs)}")
        for req_png in (
            "assets/tyc2/textures/block/primitive_mint_machine_front.png",
            "assets/tyc2/textures/block/primitive_mint_machine_front_active.png",
            "assets/tyc2/textures/block/primitive_mint_machine_side.png",
            "assets/tyc2/textures/block/primitive_mint_machine_top.png",
            "assets/tyc2/textures/block/primitive_mint_machine_bottom.png",
            "assets/tyc2/textures/gui/primitive_mint_machine.png",
        ):
            if req_png not in entries:
                issues.append(f"[{name}] Missing Primitive Mint texture: {req_png}")

        for lang in ("assets/tyc2/lang/en_us.json", "assets/tyc2/lang/zh_cn.json"):
            if lang not in entries:
                issues.append(f"[{name}] Missing {lang}")
        if "assets/tyc2/lang/en_us.json" in entries and "assets/tyc2/lang/zh_cn.json" in entries:
            en = json.loads(zf.read("assets/tyc2/lang/en_us.json").decode("utf-8"))
            zh = json.loads(zf.read("assets/tyc2/lang/zh_cn.json").decode("utf-8"))
            if set(en.keys()) != set(zh.keys()):
                issues.append(f"[{name}] Lang key mismatch: en={len(en)}, zh={len(zh)}")
            else:
                for key in sorted(en.keys()):
                    en_val = str(en[key])
                    zh_val = str(zh[key])
                    if en_val.count("%s") != zh_val.count("%s"):
                        issues.append(
                            f"[{name}] Placeholder %s mismatch on '{key}': en={en_val.count('%s')}, zh={zh_val.count('%s')}"
                        )
                    for phrase in BANNED_ADVERTISING_PHRASES:
                        if phrase.lower() in en_val.lower():
                            issues.append(f"[{name}] Banned advertising phrase '{phrase}' in en_us['{key}']: {en_val}")
                        if phrase.lower() in zh_val.lower():
                            issues.append(f"[{name}] Banned advertising phrase '{phrase}' in zh_cn['{key}']: {zh_val}")

        models = [e for e in entries if e.startswith("assets/tyc2/models/") and e.endswith(".json")]
        blockstates = [e for e in entries if e.startswith("assets/tyc2/blockstates/") and e.endswith(".json")]
        if len(models) < 21:
            issues.append(f"[{name}] Expected >=21 models, found {len(models)}")
        if len(blockstates) < 4:
            issues.append(f"[{name}] Expected 4 blockstates, found {len(blockstates)}")
        if is_26x:
            items_26 = [e for e in entries if e.startswith("assets/tyc2/items/") and e.endswith(".json")]
            if len(items_26) < 14:
                issues.append(f"[{name}] Expected 14 26.x item definitions under assets/tyc2/items/, found {len(items_26)}")
            if "assets/tyc2/items/primitive_mint_machine.json" not in entries:
                issues.append(f"[{name}] Missing 26.x item definition assets/tyc2/items/primitive_mint_machine.json")

        # Conditional recipe checks (primitive_mint_machine + mint_machine_ic2 + mint_machine_standalone, NO stale mint_machine.json)
        recipe_dir = "data/tyc2/recipes" if is_forge else "data/tyc2/recipe"
        for req_recipe in (
            f"{recipe_dir}/primitive_mint_machine.json",
            f"{recipe_dir}/mint_machine_ic2.json",
            f"{recipe_dir}/mint_machine_standalone.json",
            f"{recipe_dir}/atm_machine.json",
            f"{recipe_dir}/comm_terminal.json",
        ):
            if req_recipe not in entries:
                issues.append(f"[{name}] Missing recipe: {req_recipe}")
        stale_mint = f"{recipe_dir}/mint_machine.json"
        if stale_mint in entries:
            issues.append(f"[{name}] Stale unconditional recipe still present: {stale_mint}")
        cond_key = "conditions" if is_forge else "neoforge:conditions"
        if f"{recipe_dir}/mint_machine_ic2.json" in entries:
            ic2_rec = json.loads(zf.read(f"{recipe_dir}/mint_machine_ic2.json").decode("utf-8"))
            if cond_key not in ic2_rec:
                issues.append(f"[{name}] mint_machine_ic2.json missing '{cond_key}'")
        if f"{recipe_dir}/mint_machine_standalone.json" in entries:
            std_rec = json.loads(zf.read(f"{recipe_dir}/mint_machine_standalone.json").decode("utf-8"))
            if cond_key not in std_rec:
                issues.append(f"[{name}] mint_machine_standalone.json missing '{cond_key}'")

        # Data presets checks
        required_data = [
            "data/tyc2/mint_recipes/default_vanilla.json",
            "data/tyc2/mint_recipes/default_ic2.json",
            "data/tyc2/goods/vanilla.json",
            "data/tyc2/goods/ic2.json",
            "data/tyc2/goods/farmers_delight.json",
            "data/tyc2/goods/kaleidoscope_cookery.json",
            "data/tyc2/seasons/harvest_seasons.json",
            "data/tyc2/levels/village_levels.json",
        ]
        for req in required_data:
            if req not in entries:
                issues.append(f"[{name}] Missing datapack preset: {req}")

        # Validate ALL JSON files in JAR
        for e in entries:
            if e.endswith(".json"):
                try:
                    json.loads(zf.read(e).decode("utf-8"))
                except Exception as exc:
                    issues.append(f"[{name}] Invalid JSON {e}: {exc}")

        # Bytecode audit: ZERO com/industrialage or com.industrialage outside compat/ and mixin/
        bad_ic2_refs = []
        for cls in class_entries:
            if "/compat/" in cls or "/mixin/" in cls:
                continue
            raw = zf.read(cls)
            if b"com/industrialage" in raw or b"com.industrialage" in raw:
                bad_ic2_refs.append(cls)
        if bad_ic2_refs:
            issues.append(f"[{name}] Non-compat/mixin classes reference com.industrialage: {bad_ic2_refs}")

        # Check Forge SRG reobfuscation on a known Minecraft call
        if is_forge:
            srg_hits = sum(1 for cls in class_entries if b"m_" in zf.read(cls) and cls.startswith("com/tycooncraft/tyc2/forge/"))
            if srg_hits < 5:
                issues.append(f"[{name}] Forge JAR does not appear SRG-reobfuscated (only {srg_hits} classes with m_ symbols)")

        print(
            f"[AUDIT] {name}: {size:,} bytes | {len(class_entries)} classes | "
            f"{len(pngs)} textures | {len(models)} models | {len(blockstates)} blockstates | "
            f"{'OK' if not issues else f'{len(issues)} ISSUES'}"
        )
    return issues


def main() -> int:
    all_issues: list[str] = []
    for args in TARGETS:
        all_issues.extend(audit_jar(*args))
    if all_issues:
        print("\nAUDIT FAILED:")
        for item in all_issues:
            print(f"  - {item}")
        return 1
    print("\nALL 4 PRODUCTION JARS PASSED STATIC AUDIT!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
