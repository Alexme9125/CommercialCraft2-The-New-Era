#!/usr/bin/env python3
"""Static production JAR auditor for all 4 Monetera (mnera) targets."""

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
        ROOT / f"src/neoforge/1.21.1/build/libs/mnera-neoforge-1.21.1-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        False,
    ),
    (
        "neoforge-26.1.2",
        ROOT / f"src/neoforge/26.1.2/build/libs/mnera-neoforge-26.1.2-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        True,
    ),
    (
        "neoforge-26.2",
        ROOT / f"src/neoforge/26.2/build/libs/mnera-neoforge-26.2-{NEOFORGE_VER}.jar",
        "META-INF/neoforge.mods.toml",
        False,
        True,
    ),
    (
        "forge-1.19.2",
        ROOT / f"forge-1.19.2/build/libs/mnera-forge-1.19.2-{FORGE_VER}.jar",
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

        # Check QA leak & preview leak & legacy namespace leak
        qa_leaks = [e for e in entries if "/qa/" in e or "mnera_qa" in e or "mne_qa" in e or "tyc2_qa" in e]
        if qa_leaks:
            issues.append(f"[{name}] QA leaks in production JAR: {qa_leaks[:5]}")
        legacy_ns_leaks = [
            e for e in entries
            if e.startswith(("assets/mne/", "data/mne/", "assets/tyc2/", "data/tyc2/"))
        ]
        if legacy_ns_leaks:
            issues.append(f"[{name}] Legacy namespace leaks in production JAR: {legacy_ns_leaks[:5]}")
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
            if 'modId="mnera"' not in toml_text and 'modId = "mnera"' not in toml_text:
                issues.append(f"[{name}] modId=mnera not found in {mods_toml_path}")
            # Verify mhera / ecera / trera are NOT mandatory dependencies
            for opt_mod in ("mhera", "ecera", "trera"):
                pattern = re.compile(
                    rf'modId\s*=\s*"{opt_mod}".*?(?:mandatory\s*=\s*true|type\s*=\s*"required")',
                    re.DOTALL,
                )
                for block in toml_text.split("[[dependencies"):
                    if pattern.search(block):
                        issues.append(f"[{name}] Optional mod '{opt_mod}' marked REQUIRED/mandatory in {mods_toml_path}!")
            if 'modId="ecera"' not in toml_text and 'modId = "ecera"' not in toml_text:
                issues.append(f"[{name}] Optional mod 'ecera' missing from {mods_toml_path}")

        # Check mixins JSON
        if "mnera.mixins.json" not in entries:
            issues.append(f"[{name}] Missing mnera.mixins.json")
        else:
            mixin_cfg = json.loads(zf.read("mnera.mixins.json").decode("utf-8"))
            if not mixin_cfg.get("plugin"):
                issues.append(f"[{name}] mnera.mixins.json missing plugin (needed for conditional MHERA mixins)")
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
            if "MixinConfigs: mnera.mixins.json" not in manifest:
                issues.append(f"[{name}] Forge MANIFEST.MF missing 'MixinConfigs: mnera.mixins.json'")

        # Assets checks (35 textures: 18 block + 11 item + 6 GUI)
        pngs = [e for e in entries if e.startswith("assets/mnera/textures/") and e.endswith(".png")]
        if len(pngs) < 35:
            issues.append(f"[{name}] Expected >=35 textures under assets/mnera/textures/, found {len(pngs)}")
        for req_png in (
            "assets/mnera/textures/block/primitive_mint_machine_front.png",
            "assets/mnera/textures/block/primitive_mint_machine_front_active.png",
            "assets/mnera/textures/block/primitive_mint_machine_side.png",
            "assets/mnera/textures/block/primitive_mint_machine_top.png",
            "assets/mnera/textures/block/primitive_mint_machine_bottom.png",
            "assets/mnera/textures/gui/primitive_mint_machine.png",
        ):
            if req_png not in entries:
                issues.append(f"[{name}] Missing Primitive Mint texture: {req_png}")

        for lang in ("assets/mnera/lang/en_us.json", "assets/mnera/lang/zh_cn.json"):
            if lang not in entries:
                issues.append(f"[{name}] Missing {lang}")
        if "assets/mnera/lang/en_us.json" in entries and "assets/mnera/lang/zh_cn.json" in entries:
            en = json.loads(zf.read("assets/mnera/lang/en_us.json").decode("utf-8"))
            zh = json.loads(zf.read("assets/mnera/lang/zh_cn.json").decode("utf-8"))
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

        models = [e for e in entries if e.startswith("assets/mnera/models/") and e.endswith(".json")]
        blockstates = [e for e in entries if e.startswith("assets/mnera/blockstates/") and e.endswith(".json")]
        if len(models) < 21:
            issues.append(f"[{name}] Expected >=21 models, found {len(models)}")
        if len(blockstates) < 4:
            issues.append(f"[{name}] Expected 4 blockstates, found {len(blockstates)}")
        if is_26x:
            items_26 = [e for e in entries if e.startswith("assets/mnera/items/") and e.endswith(".json")]
            if len(items_26) < 14:
                issues.append(f"[{name}] Expected 14 26.x item definitions under assets/mnera/items/, found {len(items_26)}")
            if "assets/mnera/items/primitive_mint_machine.json" not in entries:
                issues.append(f"[{name}] Missing 26.x item definition assets/mnera/items/primitive_mint_machine.json")

        # Conditional recipe checks (primitive_mint_machine + mint_machine_mhera + mint_machine_standalone, NO stale mint_machine.json or mint_machine_ic2.json)
        recipe_dir = "data/mnera/recipes" if is_forge else "data/mnera/recipe"
        for req_recipe in (
            f"{recipe_dir}/primitive_mint_machine.json",
            f"{recipe_dir}/mint_machine_mhera.json",
            f"{recipe_dir}/mint_machine_standalone.json",
            f"{recipe_dir}/atm_machine.json",
            f"{recipe_dir}/comm_terminal.json",
        ):
            if req_recipe not in entries:
                issues.append(f"[{name}] Missing recipe: {req_recipe}")
        for stale_rec in (
            f"{recipe_dir}/mint_machine.json",
            f"{recipe_dir}/mint_machine_ic2.json",
            f"{recipe_dir}/mint_machine_mce.json",
        ):
            if stale_rec in entries:
                issues.append(f"[{name}] Stale recipe still present: {stale_rec}")
        cond_key = "conditions" if is_forge else "neoforge:conditions"
        if f"{recipe_dir}/mint_machine_mhera.json" in entries:
            mhera_rec = json.loads(zf.read(f"{recipe_dir}/mint_machine_mhera.json").decode("utf-8"))
            if cond_key not in mhera_rec:
                issues.append(f"[{name}] mint_machine_mhera.json missing '{cond_key}'")
        if f"{recipe_dir}/mint_machine_standalone.json" in entries:
            std_rec = json.loads(zf.read(f"{recipe_dir}/mint_machine_standalone.json").decode("utf-8"))
            if cond_key not in std_rec:
                issues.append(f"[{name}] mint_machine_standalone.json missing '{cond_key}'")

        # Common tag publishing & cross-platform namespace/path compliance checks
        expected_ns = "forge" if is_forge else "c"
        block_tag_dir = "tags/blocks" if is_forge else "tags/block"
        item_tag_dir = "tags/items" if is_forge else "tags/item"
        req_common_tags = (
            f"data/{expected_ns}/{block_tag_dir}/relocation_not_supported.json",
            f"data/{expected_ns}/{item_tag_dir}/coins.json",
        )
        for req_tag in req_common_tags:
            if req_tag not in entries:
                issues.append(f"[{name}] Missing published common tag: {req_tag}")
        if is_forge:
            c_leaks = [e for e in entries if e.startswith("data/c/")]
            if c_leaks:
                issues.append(f"[{name}] Forge 1.19.2 JAR contains NeoForge data/c/ tags: {c_leaks[:5]}")
        else:
            plural_leaks = [
                e for e in entries
                if e.startswith("data/") and ("/tags/items/" in e or "/tags/blocks/" in e)
            ]
            if plural_leaks:
                issues.append(f"[{name}] NeoForge 1.21+/26.x JAR contains legacy plural tag paths: {plural_leaks[:5]}")
            forge_leaks = [e for e in entries if e.startswith("data/forge/")]
            if forge_leaks:
                issues.append(f"[{name}] NeoForge 1.21+/26.x JAR contains legacy data/forge/ tags: {forge_leaks[:5]}")

        # Verify all 5 shaped recipes use common tags cleanly (no stale hardcoded tag-replaceable IDs or wrong namespace)
        banned_recipe_items = {
            "minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot",
            "minecraft:redstone", "minecraft:glass_pane", "minecraft:chest",
            "minecraft:iron_block", "minecraft:redstone_block", "minecraft:diamond",
            "minecraft:stone_bricks", "ic2:iron_plate", "ic2:circuit", "mhera:iron_plate", "mhera:circuit",
        }
        wrong_ns = "c:" if is_forge else "forge:"
        for rname in ("primitive_mint_machine", "mint_machine_mhera", "mint_machine_standalone", "atm_machine", "comm_terminal"):
            rpath = f"{recipe_dir}/{rname}.json"
            if rpath not in entries:
                continue
            rec_obj = json.loads(zf.read(rpath).decode("utf-8"))
            for sym, ing in (rec_obj.get("key") or {}).items():
                if is_26x:
                    if not isinstance(ing, str):
                        issues.append(f"[{name}] 26.x recipe {rpath} key '{sym}' must be bare string, got {type(ing).__name__}")
                        continue
                    if ing in banned_recipe_items:
                        issues.append(f"[{name}] Recipe {rpath} key '{sym}' still hardcodes {ing}")
                    if ing.startswith("#" + wrong_ns):
                        issues.append(f"[{name}] Recipe {rpath} key '{sym}' uses wrong namespace: {ing}")
                else:
                    if not isinstance(ing, dict):
                        issues.append(f"[{name}] Recipe {rpath} key '{sym}' must be object, got {type(ing).__name__}")
                        continue
                    if ing.get("item") in banned_recipe_items:
                        issues.append(f"[{name}] Recipe {rpath} key '{sym}' still hardcodes {ing.get('item')}")
                    tag_val = ing.get("tag", "")
                    if isinstance(tag_val, str) and tag_val.startswith(wrong_ns):
                        issues.append(f"[{name}] Recipe {rpath} key '{sym}' uses wrong namespace: {tag_val}")

        # Data presets checks
        required_data = [
            "data/mnera/mint_recipes/default_vanilla.json",
            "data/mnera/mint_recipes/default_mhera.json",
            "data/mnera/goods/vanilla.json",
            "data/mnera/goods/mhera.json",
            "data/mnera/goods/ecera.json",
            "data/mnera/goods/farmers_delight.json",
            "data/mnera/goods/kaleidoscope_cookery.json",
            "data/mnera/seasons/harvest_seasons.json",
            "data/mnera/levels/village_levels.json",
            "data/mnera/workplaces/vanilla_workplaces.json",
        ]
        for req in required_data:
            if req not in entries:
                issues.append(f"[{name}] Missing datapack preset: {req}")

        # WTHIT & EMI compat plugin checks
        if "wthit_plugins.json" not in entries:
            issues.append(f"[{name}] Missing wthit_plugins.json")
        else:
            wthit_cfg = json.loads(zf.read("wthit_plugins.json").decode("utf-8"))
            for plugin_id, spec in wthit_cfg.items():
                entrypoints = spec.get("entrypoints", {}) if isinstance(spec, dict) else {}
                for side_key, fqcn in entrypoints.items():
                    cls_path = fqcn.replace(".", "/") + ".class"
                    if cls_path not in entries:
                        issues.append(f"[{name}] WTHIT {side_key} entrypoint class missing from JAR: {cls_path}")
        emi_cls = (
            "com/tycooncraft/tyc2/forge/compat/TyC2EmiPlugin.class"
            if is_forge
            else "com/tycooncraft/tyc2/compat/TyC2EmiPlugin.class"
        )
        if emi_cls not in entries:
            issues.append(f"[{name}] Missing EMI plugin class in JAR: {emi_cls}")

        # Verify vanilla_workplaces.json covers all 13 vanilla villager professions
        if "data/mnera/workplaces/vanilla_workplaces.json" in entries:
            try:
                wp_data = json.loads(zf.read("data/mnera/workplaces/vanilla_workplaces.json").decode("utf-8"))
                wp_entries = wp_data.get("workplaces", [])
                wp_profs = {str(w.get("professionId", "")).lower() for w in wp_entries if isinstance(w, dict)}
                expected_profs = {
                    "minecraft:farmer",
                    "minecraft:fisherman",
                    "minecraft:shepherd",
                    "minecraft:fletcher",
                    "minecraft:butcher",
                    "minecraft:leatherworker",
                    "minecraft:mason",
                    "minecraft:cartographer",
                    "minecraft:toolsmith",
                    "minecraft:weaponsmith",
                    "minecraft:armorer",
                    "minecraft:cleric",
                    "minecraft:librarian",
                }
                missing_profs = sorted(expected_profs - wp_profs)
                if missing_profs:
                    issues.append(f"[{name}] vanilla_workplaces.json missing professions: {missing_profs}")
            except Exception as exc:
                issues.append(f"[{name}] Failed parsing vanilla_workplaces.json: {exc}")

        # Verify core Aid Construction, 7-biome, and HUD localization keys exist
        if "assets/mnera/lang/en_us.json" in entries:
            en_keys = set(json.loads(zf.read("assets/mnera/lang/en_us.json").decode("utf-8")).keys())
            for req_key in (
                "biome.mnera.jungle",
                "biome.mnera.swamp",
                "project.mnera.aid_workplace_small",
                "project.mnera.aid_workplace_large",
                "gui.mnera.hub.tab.aid",
                "hud.mnera.hub.unemployment",
                "hud.mnera.hub.employment_bonus",
                "hud.mnera.hub.aid_active",
                "hud.mnera.mayor.unemployment",
                "status.mnera.aid.started",
                "status.mnera.aid.cancelled",
                "status.mnera.aid.completed",
                "gui.mnera.order.tooltip.accepts_tag",
            ):
                if req_key not in en_keys:
                    issues.append(f"[{name}] Missing required lang key: {req_key}")

        # Validate ALL JSON files in JAR & ensure zero legacy namespace strings
        legacy_ns_tokens = ('"mne:"', '"mce:"', '"ece:"', '"tre:"', '"tyc2:"')
        for e in entries:
            if e.endswith(".json"):
                text = zf.read(e).decode("utf-8")
                try:
                    json.loads(text)
                except Exception as exc:
                    issues.append(f"[{name}] Invalid JSON {e}: {exc}")
                for tok in legacy_ns_tokens:
                    if tok in text:
                        issues.append(f"[{name}] Legacy namespace token {tok} found in {e}")

        # Bytecode audit: ZERO com/industrialage, mcp/mobius/waila, or dev/emi/emi outside compat/ and mixin/
        bad_opt_refs = []
        for cls in class_entries:
            if "/compat/" in cls or "/mixin/" in cls:
                continue
            raw = zf.read(cls)
            if (
                b"com/industrialage" in raw
                or b"com.industrialage" in raw
                or b"mcp/mobius/waila" in raw
                or b"dev/emi/emi" in raw
            ):
                bad_opt_refs.append(cls)
        if bad_opt_refs:
            issues.append(f"[{name}] Non-compat/mixin classes reference optional mod APIs: {bad_opt_refs}")

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
