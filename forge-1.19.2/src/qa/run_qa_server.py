#!/usr/bin/env python3
"""Two-phase (write + cold-restart read) live Forge 1.19.2 dedicated server QA runner for TyC2."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time


ERROR_PATTERNS = [
    re.compile(r"MixinApplyError|InvalidMixinException|MixinTransformerError|MixinPrepareError", re.IGNORECASE),
    re.compile(r"Critical injection failure", re.IGNORECASE),
    re.compile(r"Failed to load mod.*tyc2", re.IGNORECASE),
    re.compile(r"\[tyc2[^\]]*\]/\w*ERROR", re.IGNORECASE),
    re.compile(r"ERROR.*com\.tycooncraft\.tyc2", re.IGNORECASE),
    re.compile(r"ERROR.*tyc2\.mixins\.json", re.IGNORECASE),
    re.compile(r"\[TYC2-QA\] FAIL", re.IGNORECASE),
    re.compile(r"Encountered an unexpected exception", re.IGNORECASE),
    re.compile(r"Crash report saved to", re.IGNORECASE),
    re.compile(
        r"java\.lang\.(NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|ClassNotFoundException|VerifyError|IncompatibleClassChangeError|AbstractMethodError)",
        re.IGNORECASE,
    ),
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_properties(path: Path) -> dict:
    props = {}
    if not path.is_file():
        return props
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" in line:
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip().replace("\\:", ":").replace("\\\\", "\\")
    return props


def scan_log_for_errors(log_path: Path) -> list:
    if not log_path.is_file():
        return [f"Missing expected log file: {log_path}"]
    errors = []
    text = log_path.read_text(encoding="utf-8", errors="replace")
    for lineno, line in enumerate(text.splitlines(), 1):
        if 'does not specify "minVersion" property' in line:
            continue
        for pat in ERROR_PATTERNS:
            if pat.search(line):
                errors.append(f"{log_path.name}:{lineno}: {line.strip()}")
                break
    return errors


def build_command(manifest: dict, profile: str, phase: str) -> list:
    spec = manifest["server"]
    jvm_args = [
        arg for arg in spec["jvm"]
        if not arg.startswith("-Dtyc2.qa.mode=") and not arg.startswith("-Dtyc2.qa.profile=")
    ]
    jvm_args.append(f"-Dtyc2.qa.mode=server_{phase}")
    jvm_args.append(f"-Dtyc2.qa.profile={profile}")
    if not any(arg.startswith("-Xmx") for arg in jvm_args):
        jvm_args.append("-Xmx2048M")
    return [
        spec["java"],
        *jvm_args,
        "-cp",
        spec["classpath"],
        spec["main"],
        *spec["args"],
    ]


def prepare_run_directory(manifest: dict, run_dir: Path, profile: str) -> None:
    if run_dir.exists():
        shutil.rmtree(run_dir)
    mods_dir = run_dir / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)

    tyc2_jar_name = Path(manifest["tyc2ModJar"]).name
    shutil.copyfile(manifest["tyc2ModJar"], mods_dir / tyc2_jar_name)
    if profile == "enhanced":
        shutil.copyfile(manifest["ic2ModJar"], mods_dir / Path(manifest["ic2ModJar"]).name)
        shutil.copyfile(manifest["lc2ModJar"], mods_dir / Path(manifest["lc2ModJar"]).name)
    else:
        # Strict standalone check: ONLY tyc2 JAR in mods/
        jars = [p.name for p in mods_dir.glob("*.jar")]
        if jars != [tyc2_jar_name]:
            raise RuntimeError(f"Standalone mods/ must contain ONLY TyC2 JAR, got: {jars}")

    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "\n".join(
            [
                "server-ip=127.0.0.1",
                "server-port=0",
                "online-mode=false",
                "level-name=world",
                "level-seed=20261123",
                "level-type=minecraft\\:flat",
                "generator-settings={\"biome\"\\:\"minecraft\\:plains\",\"layers\"\\:[{\"block\"\\:\"minecraft\\:bedrock\",\"height\"\\:1},{\"block\"\\:\"minecraft\\:stone\",\"height\"\\:60},{\"block\"\\:\"minecraft\\:dirt\",\"height\"\\:2},{\"block\"\\:\"minecraft\\:grass_block\",\"height\"\\:1}]}",
                "generate-structures=false",
                "view-distance=4",
                "simulation-distance=4",
                "spawn-protection=0",
                "spawn-monsters=false",
                "spawn-animals=false",
                "spawn-npcs=true",
                "enable-command-block=false",
                "allow-nether=false",
                "difficulty=peaceful",
                "max-tick-time=120000",
                "sync-chunk-writes=true",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def run_phase(manifest: dict, run_dir: Path, profile: str, phase: str, timeout_sec: int) -> tuple:
    cmd = build_command(manifest, profile, phase)
    env = os.environ.copy()
    for k, v in manifest["server"]["env"].items():
        env[str(k)] = str(v)

    console_log = run_dir / f"qa-{profile}-{phase}-console.log"
    print(f"\n[TYC2-QA] === Starting Forge 1.19.2 Dedicated Server (profile={profile}, phase={phase}) ===", flush=True)
    print(f"[TYC2-QA] Run dir: {run_dir}", flush=True)
    print(f"[TYC2-QA] Mods staged: {[p.name for p in sorted((run_dir / 'mods').glob('*.jar'))]}", flush=True)

    start = time.monotonic()
    proc = subprocess.Popen(
        cmd,
        cwd=str(run_dir),
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )

    try:
        with console_log.open("w", encoding="utf-8") as out:
            assert proc.stdout is not None
            for line in proc.stdout:
                out.write(line)
                out.flush()
                stripped = line.rstrip("\r\n")
                if (
                    "[TYC2-QA]" in stripped
                    or "TycoonCraft" in stripped
                    or "TyC2" in stripped
                    or "ERROR]" in stripped
                    or "FATAL]" in stripped
                    or "Mixin" in stripped
                    or "Exception" in stripped
                    or "Caused by:" in stripped
                    or "Done (" in stripped
                ):
                    print(stripped, flush=True)
                if time.monotonic() - start > timeout_sec:
                    proc.kill()
                    raise TimeoutError(f"Phase '{profile}/{phase}' timed out after {timeout_sec}s; see {console_log}")
        rc = proc.wait(timeout=30)
    finally:
        if proc.poll() is None:
            proc.kill()
            proc.wait()

    elapsed = time.monotonic() - start
    print(f"[TYC2-QA] === Phase '{profile}/{phase}' exited rc={rc} in {elapsed:.1f}s ===", flush=True)

    latest_log = run_dir / "logs" / "latest.log"
    archived_log = run_dir / "logs" / f"{profile}-{phase}-latest.log"
    if latest_log.is_file():
        shutil.copyfile(latest_log, archived_log)

    log_errors = []
    if archived_log.is_file():
        log_errors.extend(scan_log_for_errors(archived_log))
    else:
        log_errors.append(f"Missing {latest_log} after phase '{profile}/{phase}'")
    log_errors.extend(scan_log_for_errors(console_log))
    unique_errors = list(dict.fromkeys(log_errors))

    props = parse_properties(run_dir / f"qa-{profile}-{phase}.properties")
    return rc, props, unique_errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--profile", required=True, choices=["standalone", "enhanced"])
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    run_dir = Path(manifest["runBaseDir"]) / args.profile
    report_dir = Path(manifest["reportDir"])

    prepare_run_directory(manifest, run_dir, args.profile)

    write_rc, write_props, write_errors = run_phase(manifest, run_dir, args.profile, "write", args.timeout)
    write_ok = (
        write_rc == 0
        and write_props.get("status") == "PASSED"
        and write_props.get("failedChecks") == "0"
        and len(write_errors) == 0
    )

    if write_ok:
        read_rc, read_props, read_errors = run_phase(manifest, run_dir, args.profile, "read", args.timeout)
    else:
        read_rc, read_props, read_errors = -1, {"status": "SKIPPED_AFTER_WRITE_FAILURE"}, []

    read_ok = (
        write_ok
        and read_rc == 0
        and read_props.get("status") == "PASSED"
        and read_props.get("failedChecks") == "0"
        and len(read_errors) == 0
        and write_props.get("pid") != read_props.get("pid")
    )

    overall_ok = write_ok and read_ok
    report_path = report_dir / f"qa-server-{args.profile}-report.properties"
    lines = [
        f"# TycoonCraft 2 Forge 1.19.2 Dedicated Server QA ({args.profile})",
        f"status={'PASSED' if overall_ok else 'FAILED'}",
        f"profile={args.profile}",
        f"packaged.tyc2.jar={manifest['packagedTyc2Jar']}",
        f"packaged.tyc2.sha256={sha256(Path(manifest['packagedTyc2Jar']))}",
        f"write.exitCode={write_rc}",
        f"write.status={write_props.get('status', 'MISSING')}",
        f"write.passedChecks={write_props.get('passedChecks', '0')}",
        f"write.failedChecks={write_props.get('failedChecks', '1')}",
        f"write.logErrors={len(write_errors)}",
        f"write.pid={write_props.get('pid', '')}",
        f"read.exitCode={read_rc}",
        f"read.status={read_props.get('status', 'MISSING')}",
        f"read.passedChecks={read_props.get('passedChecks', '0')}",
        f"read.failedChecks={read_props.get('failedChecks', '1')}",
        f"read.logErrors={len(read_errors)}",
        f"read.pid={read_props.get('pid', '')}",
    ]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print("\n[TYC2-QA] ==============================================", flush=True)
    print(f"[TYC2-QA] Forge 1.19.2 Server QA ({args.profile}): {'PASSED' if overall_ok else 'FAILED'}", flush=True)
    print(
        f"[TYC2-QA] Write: rc={write_rc}, status={write_props.get('status', 'MISSING')}, "
        f"passed={write_props.get('passedChecks', '0')}, failed={write_props.get('failedChecks', '?')}, "
        f"logErrors={len(write_errors)}, pid={write_props.get('pid', '?')}",
        flush=True,
    )
    print(
        f"[TYC2-QA] Read : rc={read_rc}, status={read_props.get('status', 'MISSING')}, "
        f"passed={read_props.get('passedChecks', '0')}, failed={read_props.get('failedChecks', '?')}, "
        f"logErrors={len(read_errors)}, pid={read_props.get('pid', '?')}",
        flush=True,
    )
    if write_errors:
        print("[TYC2-QA] Write log errors:", flush=True)
        for e in write_errors[:30]:
            print(f"  - {e}", flush=True)
    if read_errors:
        print("[TYC2-QA] Read log errors:", flush=True)
        for e in read_errors[:30]:
            print(f"  - {e}", flush=True)
    if not overall_ok:
        for name in (f"qa-{args.profile}-write-failure.txt", f"qa-{args.profile}-read-failure.txt"):
            fp = run_dir / name
            if fp.is_file():
                print(f"\n[TYC2-QA] Contents of {fp}:", flush=True)
                print(fp.read_text(encoding="utf-8", errors="replace"), flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
