#!/usr/bin/env python3
"""Real Forge 1.19.2 Dedicated Server + Graphical Client (1280x720) Joint Test runner for TyC2."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import threading
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
    re.compile(r"Missing texture.*tyc2", re.IGNORECASE),
    re.compile(r"Unable to load model.*tyc2", re.IGNORECASE),
    re.compile(r"Missing model.*tyc2", re.IGNORECASE),
    re.compile(r"Using missing texture, unable to load tyc2:", re.IGNORECASE),
    re.compile(r"FileNotFoundException.*tyc2", re.IGNORECASE),
    re.compile(
        r"java\.lang\.(NoSuchMethodError|NoSuchFieldError|NoClassDefFoundError|ClassNotFoundException|VerifyError|IncompatibleClassChangeError|AbstractMethodError)",
        re.IGNORECASE,
    ),
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def find_free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.bind(("127.0.0.1", 0))
        return int(s.getsockname()[1])


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


def scan_log(log_path: Path) -> list:
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


def stream_proc(proc: subprocess.Popen, log_file: Path, tag: str) -> None:
    assert proc.stdout is not None
    with log_file.open("w", encoding="utf-8") as out:
        for line in proc.stdout:
            out.write(line)
            out.flush()
            s = line.rstrip("\r\n")
            if (
                "[TYC2-" in s
                or "TycoonCraft" in s
                or "ERROR]" in s
                or "FATAL]" in s
                or "Missing texture" in s
                or "Unable to load model" in s
                or "Mixin" in s
                or "Exception" in s
                or "Caused by:" in s
                or "Done (" in s
                or "joined the game" in s
                or "left the game" in s
            ):
                print(f"[{tag}] {s}", flush=True)


def build_jvm_cmd(spec: dict, extra_jvm: list, extra_args: list, xmx: str) -> list:
    jvm = [
        a for a in spec["jvm"]
        if not a.startswith("-Dtyc2.qa.mode=") and not a.startswith("-Dtyc2.qa.profile=")
    ]
    jvm.extend(extra_jvm)
    if not any(a.startswith("-Xmx") for a in jvm):
        jvm.append(f"-Xmx{xmx}")
    args = list(spec["args"])
    args.extend(extra_args)
    return [
        spec["java"],
        *jvm,
        "-cp",
        spec["classpath"],
        spec["main"],
        *args,
    ]


def stage_mods(manifest: dict, mods_dir: Path, profile: str) -> None:
    mods_dir.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(manifest["tyc2ModJar"], mods_dir / Path(manifest["tyc2ModJar"]).name)
    if profile == "enhanced":
        shutil.copyfile(manifest["ic2ModJar"], mods_dir / Path(manifest["ic2ModJar"]).name)
        shutil.copyfile(manifest["lc2ModJar"], mods_dir / Path(manifest["lc2ModJar"]).name)


def prepare_server_dir(manifest: dict, run_dir: Path, profile: str, port: int) -> None:
    if run_dir.exists():
        shutil.rmtree(run_dir)
    stage_mods(manifest, run_dir / "mods", profile)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "\n".join(
            [
                "server-ip=127.0.0.1",
                f"server-port={port}",
                "online-mode=false",
                "level-name=world",
                "level-seed=20261123",
                "level-type=minecraft\\:flat",
                "generator-settings={\"biome\"\\:\"minecraft\\:plains\",\"layers\"\\:[{\"block\"\\:\"minecraft\\:bedrock\",\"height\"\\:1},{\"block\"\\:\"minecraft\\:stone\",\"height\"\\:60},{\"block\"\\:\"minecraft\\:dirt\",\"height\"\\:2},{\"block\"\\:\"minecraft\\:grass_block\",\"height\"\\:1}]}",
                "generate-structures=false",
                "view-distance=8",
                "simulation-distance=6",
                "spawn-protection=0",
                "spawn-monsters=false",
                "spawn-animals=false",
                "spawn-npcs=true",
                "enable-command-block=false",
                "allow-nether=false",
                "allow-flight=true",
                "difficulty=peaceful",
                "max-tick-time=180000",
                "sync-chunk-writes=true",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def prepare_client_dir(manifest: dict, run_dir: Path, profile: str, lang: str) -> None:
    if run_dir.exists():
        shutil.rmtree(run_dir)
    stage_mods(manifest, run_dir / "mods", profile)
    (run_dir / "options.txt").write_text(
        "\n".join(
            [
                f"lang:{lang}",
                "overrideWidth:1280",
                "overrideHeight:720",
                "guiScale:3",
                "renderDistance:8",
                "simulationDistance:6",
                "particles:1",
                "graphicsMode:1",
                "ao:2",
                "gamma:0.8",
                "fov:0.0",
                "pauseOnLostFocus:false",
                "tutorialStep:none",
                "skipMultiplayerWarning:true",
                "joinedFirstServer:true",
                "narrator:0",
                "soundCategory_master:0.0",
                "onboardAccessibility:false",
            ]
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--profile", required=True, choices=["standalone", "enhanced"])
    parser.add_argument("--lang", default="en_us")
    parser.add_argument("--screenshots-dir", type=Path, default=None)
    parser.add_argument("--timeout", type=int, default=360)
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    base_dir = Path(manifest["runBaseDir"]) / f"{args.profile}-{args.lang}"
    server_dir = base_dir / "server"
    client_dir = base_dir / "client"
    report_dir = Path(manifest["reportDir"])
    screenshots_dir = args.screenshots_dir or (report_dir / f"live-screenshots-{args.profile}-{args.lang}")
    screenshots_dir.mkdir(parents=True, exist_ok=True)

    port = find_free_port()
    prepare_server_dir(manifest, server_dir, args.profile, port)
    prepare_client_dir(manifest, client_dir, args.profile, args.lang)

    server_spec = manifest["server"]
    server_cmd = build_jvm_cmd(
        server_spec,
        [
            "-Dtyc2.qa.mode=live_server",
            f"-Dtyc2.qa.profile={args.profile}",
            f"-Dtyc2.live.lang={args.lang}",
        ],
        [],
        "2048M",
    )
    server_env = os.environ.copy()
    for k, v in server_spec["env"].items():
        server_env[str(k)] = str(v)

    print(
        f"\n[TYC2-LIVE] === Starting Forge 1.19.2 Dedicated Server (profile={args.profile}, lang={args.lang}, port={port}) ===",
        flush=True,
    )
    start_time = time.monotonic()
    server_proc = subprocess.Popen(
        server_cmd,
        cwd=str(server_dir),
        env=server_env,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )
    server_console = server_dir / "live-server-console.log"
    server_thread = threading.Thread(
        target=stream_proc, args=(server_proc, server_console, "SERVER"), daemon=True
    )
    server_thread.start()

    client_proc = None
    client_thread = None
    client_console = client_dir / "live-client-console.log"
    ready_marker = server_dir / "live-server-ready.properties"

    try:
        while server_proc.poll() is None:
            if ready_marker.is_file():
                break
            if time.monotonic() - start_time > args.timeout:
                raise TimeoutError(f"Server timed out waiting for readiness after {args.timeout}s")
            time.sleep(0.4)

        if server_proc.poll() is not None and not ready_marker.is_file():
            print(f"[TYC2-LIVE] Server exited early (rc={server_proc.returncode}) before client launch!", flush=True)
        else:
            ready_props = parse_properties(ready_marker)
            live_port = int(ready_props.get("port", str(port)))
            client_spec = dict(manifest["client"])
            cleaned_args = []
            raw_args = list(client_spec["args"])
            i = 0
            while i < len(raw_args):
                if raw_args[i] in ("--username", "--width", "--height"):
                    i += 2
                    continue
                cleaned_args.append(raw_args[i])
                i += 1
            client_spec["args"] = cleaned_args

            client_cmd = build_jvm_cmd(
                client_spec,
                [
                    "-Dtyc2.qa.mode=live_client",
                    f"-Dtyc2.qa.profile={args.profile}",
                    f"-Dtyc2.live.lang={args.lang}",
                    f"-Dtyc2.live.port={live_port}",
                    f"-Dtyc2.live.screenshotsDir={screenshots_dir.resolve()}",
                ],
                ["--username", "TyC2Tester", "--width", "1280", "--height", "720"],
                "2048M",
            )
            client_env = os.environ.copy()
            for k, v in client_spec["env"].items():
                client_env[str(k)] = str(v)

            print(
                f"\n[TYC2-LIVE] === Starting Real Forge 1.19.2 Graphical Client (1280x720 -> 127.0.0.1:{live_port}) ===",
                flush=True,
            )
            client_proc = subprocess.Popen(
                client_cmd,
                cwd=str(client_dir),
                env=client_env,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                bufsize=1,
            )
            client_thread = threading.Thread(
                target=stream_proc, args=(client_proc, client_console, "CLIENT"), daemon=True
            )
            client_thread.start()

        while True:
            srv_done = server_proc.poll() is not None
            cli_done = client_proc is None or client_proc.poll() is not None
            if srv_done and cli_done:
                break
            if time.monotonic() - start_time > args.timeout:
                raise TimeoutError(f"Live Client-Server joint test timed out after {args.timeout}s")
            time.sleep(0.5)
    finally:
        if client_proc is not None and client_proc.poll() is None:
            client_proc.kill()
            client_proc.wait()
        if server_proc.poll() is None:
            server_proc.kill()
            server_proc.wait()
        server_thread.join(timeout=5)
        if client_thread is not None:
            client_thread.join(timeout=5)

    srv_rc = server_proc.returncode
    cli_rc = client_proc.returncode if client_proc is not None else -1
    srv_props = parse_properties(server_dir / "live-server-report.properties")
    cli_props = parse_properties(client_dir / "live-client-report.properties")

    srv_errors = list(dict.fromkeys(scan_log(server_dir / "logs" / "latest.log") + scan_log(server_console)))
    cli_errors = list(dict.fromkeys(scan_log(client_dir / "logs" / "latest.log") + scan_log(client_console)))

    srv_ok = srv_rc == 0 and srv_props.get("status") == "PASSED" and srv_props.get("failedChecks") == "0" and len(srv_errors) == 0
    cli_ok = cli_rc == 0 and cli_props.get("status") == "PASSED" and cli_props.get("failedChecks") == "0" and len(cli_errors) == 0
    overall_ok = srv_ok and cli_ok

    report_path = report_dir / f"live-client-server-{args.profile}-{args.lang}-report.properties"
    lines = [
        f"# TycoonCraft 2 Forge 1.19.2 Live Client-Server Report ({args.profile}, lang={args.lang})",
        f"status={'PASSED' if overall_ok else 'FAILED'}",
        f"profile={args.profile}",
        f"lang={args.lang}",
        f"server.exitCode={srv_rc}",
        f"server.status={srv_props.get('status', 'MISSING')}",
        f"server.passedChecks={srv_props.get('passedChecks', '0')}",
        f"server.logErrors={len(srv_errors)}",
        f"client.exitCode={cli_rc}",
        f"client.status={cli_props.get('status', 'MISSING')}",
        f"client.passedChecks={cli_props.get('passedChecks', '0')}",
        f"client.logErrors={len(cli_errors)}",
        f"screenshotsDir={screenshots_dir.resolve()}",
    ]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

    elapsed = time.monotonic() - start_time
    print("\n[TYC2-LIVE] ==============================================", flush=True)
    print(f"[TYC2-LIVE] Forge 1.19.2 Client-Server ({args.profile}, {args.lang}): {'PASSED' if overall_ok else 'FAILED'} ({elapsed:.1f}s)", flush=True)
    print(
        f"[TYC2-LIVE] Server: rc={srv_rc}, status={srv_props.get('status', 'MISSING')}, "
        f"passed={srv_props.get('passedChecks', '0')}, logErrors={len(srv_errors)}",
        flush=True,
    )
    print(
        f"[TYC2-LIVE] Client: rc={cli_rc}, status={cli_props.get('status', 'MISSING')}, "
        f"passed={cli_props.get('passedChecks', '0')}, logErrors={len(cli_errors)}",
        flush=True,
    )
    for png in sorted(screenshots_dir.glob(f"tyc2_1192_{args.profile}_{args.lang}_*.png")):
        print(f"  Screenshot: {png.resolve()} ({png.stat().st_size} bytes)", flush=True)
    if srv_errors:
        print("[TYC2-LIVE] Server log errors:", flush=True)
        for e in srv_errors[:30]:
            print(f"  - {e}", flush=True)
    if cli_errors:
        print("[TYC2-LIVE] Client log errors:", flush=True)
        for e in cli_errors[:30]:
            print(f"  - {e}", flush=True)
    if not overall_ok:
        for fp in (server_dir / "live-server-failure.txt", client_dir / "live-client-failure.txt"):
            if fp.is_file():
                print(f"\n[TYC2-LIVE] Contents of {fp}:", flush=True)
                print(fp.read_text(encoding="utf-8", errors="replace"), flush=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
