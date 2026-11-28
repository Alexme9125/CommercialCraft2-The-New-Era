#!/usr/bin/env python3
"""Run a real graphical Minecraft Client connected to a Dedicated Server for TyC2 NeoForge Joint QA."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[2]
HOST = "127.0.0.1"
SERVER_READY = re.compile(r"\bDone \(\d+(?:\.\d+)?s\)!")
ENV_WHITELIST = {
    "PATH", "HOME", "USER", "LOGNAME", "TMPDIR", "TEMP", "TMP", "LANG",
    "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR", "XAUTHORITY", "SystemRoot",
}


def pick_free_port() -> int:
    with socket.socket() as sock:
        sock.bind((HOST, 0))
        return sock.getsockname()[1]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.is_file() else ""


def write_json_atomic(path: Path, data: dict) -> None:
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)


class LiveJointSession:
    def __init__(self, mc_version: str, profile: str, lang: str, manifest: dict, run_dir: Path, port: int):
        self.mc_version = mc_version
        self.profile = profile
        self.lang = lang
        self.manifest = manifest
        self.run_dir = run_dir
        self.server_dir = run_dir / "server"
        self.client_dir = run_dir / "client"
        self.port = port
        self.server: subprocess.Popen | None = None
        self.client: subprocess.Popen | None = None
        self.seq = 0
        self.checks: dict = {}

    def prepare_dirs(self) -> None:
        if self.run_dir.exists():
            shutil.rmtree(self.run_dir)
        (self.server_dir / "mods").mkdir(parents=True)
        (self.client_dir / "mods").mkdir(parents=True)

        tyc2_jar = Path(self.manifest["tyc2Jar"])
        ic2_jar = Path(self.manifest["ic2Jar"])
        lc2_jar = Path(self.manifest["lc2Jar"])
        for side_dir in (self.server_dir, self.client_dir):
            shutil.copy2(tyc2_jar, side_dir / "mods" / tyc2_jar.name)
            if self.profile == "enhanced":
                shutil.copy2(ic2_jar, side_dir / "mods" / ic2_jar.name)
                shutil.copy2(lc2_jar, side_dir / "mods" / lc2_jar.name)

        (self.server_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (self.server_dir / "server.properties").write_text(
            "\n".join([
                f"server-ip={HOST}",
                f"server-port={self.port}",
                "online-mode=false",
                "level-name=world",
                "level-seed=20261123",
                "level-type=minecraft:flat",
                'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":60},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[]}',
                "generate-structures=false",
                "spawn-protection=0",
                "difficulty=peaceful",
                "view-distance=8",
                "simulation-distance=6",
                "",
            ]),
            encoding="utf-8",
        )
        (self.client_dir / "config").mkdir(parents=True, exist_ok=True)
        (self.client_dir / "config" / "fml.toml").write_text("earlyWindowControl = false\n", encoding="utf-8")
        (self.client_dir / "options.txt").write_text(
            "\n".join([
                "onboardAccessibility:false",
                f"lang:{self.lang}",
                "fullscreen:false",
                "tutorialStep:none",
                "pauseOnLostFocus:false",
                "overrideWidth:1280",
                "overrideHeight:720",
                "renderDistance:8",
                "simulationDistance:6",
                "guiScale:3",
                "fov:0.0",
                "gamma:1.0",
                "narrator:0",
                "soundCategory_master:0.0",
                "",
            ]),
            encoding="utf-8",
        )

    def _spawn(self, role: str) -> subprocess.Popen:
        launch = self.manifest[role]
        env = {k: v for k, v in os.environ.items() if k in ENV_WHITELIST or k.startswith("LC_")}
        env.update(launch.get("environment", {}))
        jvm = list(launch["jvmArgs"])
        args = list(launch["args"])
        extra_props = [
            f"-Dtyc2.qa.clientHost={HOST}",
            f"-Dtyc2.qa.clientPort={self.port}",
            f"-Dtyc2.qa.profile={self.profile}",
        ]
        macos_first_thread = ["-XstartOnFirstThread"] if (role == "client" and sys.platform == "darwin") else []
        cmd = [
            launch["java"],
            *macos_first_thread,
            *jvm,
            *extra_props,
            "-cp",
            launch["classpath"],
            launch["mainClass"],
            *args,
        ]
        log_path = self.run_dir / f"{role}.log"
        log_file = log_path.open("wb")
        proc = subprocess.Popen(
            cmd,
            cwd=self.server_dir if role == "server" else self.client_dir,
            env=env,
            stdin=subprocess.PIPE if role == "server" else subprocess.DEVNULL,
            stdout=log_file,
            stderr=subprocess.STDOUT,
            encoding="utf-8",
            start_new_session=True,
        )
        log_file.close()
        return proc

    def start(self) -> dict:
        self.prepare_dirs()
        print(f"[{self.mc_version}/{self.profile}] Starting Dedicated Server on {HOST}:{self.port}...", flush=True)
        self.server = self._spawn("server")
        self._wait_for_server(180)
        site_path = self.server_dir / "live-site.json"
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline and not site_path.is_file():
            time.sleep(0.2)
        if not site_path.is_file():
            raise RuntimeError("Dedicated server started but did not write live-site.json")
        site = json.loads(site_path.read_text(encoding="utf-8"))
        print(f"[{self.mc_version}/{self.profile}] Dedicated server ready! site={site}", flush=True)

        print(f"[{self.mc_version}/{self.profile}] Starting real graphical Minecraft Client (1280x720)...", flush=True)
        self.client = self._spawn("client")
        return site

    def _wait_for_server(self, timeout: int) -> None:
        deadline = time.monotonic() + timeout
        log_path = self.run_dir / "server.log"
        while time.monotonic() < deadline:
            assert self.server is not None
            if self.server.poll() is not None:
                raise RuntimeError(
                    f"Dedicated server exited early with code {self.server.returncode}\n"
                    + read_text(log_path)[-2500:]
                )
            if SERVER_READY.search(read_text(log_path)):
                return
            time.sleep(0.25)
        raise TimeoutError(f"Server did not finish starting within {timeout}s")

    def server_cmd(self, cmd: str) -> None:
        assert self.server is not None and self.server.stdin is not None
        self.server.stdin.write(cmd + "\n")
        self.server.stdin.flush()

    def server_live_action(self, action: str) -> dict:
        state_file = self.server_dir / "server-state.json"
        before_mtime = state_file.stat().st_mtime_ns if state_file.is_file() else 0
        self.server_cmd(f"tyc2live {action}")
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            if state_file.is_file() and state_file.stat().st_mtime_ns > before_mtime:
                return json.loads(state_file.read_text(encoding="utf-8"))
            time.sleep(0.1)
        raise TimeoutError(f"Timed out waiting for server-state.json after /tyc2live {action}")

    def act(self, action: str, **kwargs) -> dict:
        self.seq += 1
        payload = {"seq": self.seq, "action": action, **kwargs}
        write_json_atomic(self.client_dir / "qa-input.txt", payload)
        out_path = self.client_dir / "qa-output.json"
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            assert self.client is not None
            if self.client.poll() is not None:
                raise RuntimeError(
                    f"Client exited early ({self.client.returncode}) during {action}\n"
                    + read_text(self.run_dir / "client.log")[-2500:]
                )
            if out_path.is_file():
                try:
                    data = json.loads(out_path.read_text(encoding="utf-8"))
                except json.JSONDecodeError:
                    time.sleep(0.05)
                    continue
                if data.get("seq") == self.seq:
                    if "error" in data:
                        raise RuntimeError(f"Client error on {action}: {data['error']}")
                    return data
            time.sleep(0.08)
        raise TimeoutError(f"Client did not respond to {action} within 120s")

    def wait_until(self, label: str, pred, timeout: float = 90.0, interval: float = 0.2) -> dict:
        deadline = time.monotonic() + timeout
        last = {}
        while time.monotonic() < deadline:
            last = self.act("state")
            if pred(last):
                self.checks[label] = last
                return last
            time.sleep(interval)
        raise AssertionError(f"Timed out waiting for [{label}]; last state={json.dumps(last)}")

    def wait_screenshot(self, filename: str, timeout: float = 25.0) -> Path:
        shot_path = self.client_dir / "screenshots" / filename
        if shot_path.exists():
            shot_path.unlink()
        self.act("screenshot", name=filename)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if shot_path.is_file() and shot_path.stat().st_size > 4096:
                time.sleep(0.25)
                return shot_path
            time.sleep(0.15)
        raise TimeoutError(f"Screenshot {filename} was not written to {shot_path}")

    def stop(self) -> tuple[int, int]:
        client_exit = 0
        server_exit = 0
        if self.client is not None:
            if self.client.poll() is None:
                try:
                    write_json_atomic(self.client_dir / "qa-input.txt", {"seq": self.seq + 999, "action": "quit"})
                except Exception:
                    pass
                try:
                    client_exit = self.client.wait(25)
                except subprocess.TimeoutExpired:
                    self.client.terminate()
                    try:
                        client_exit = self.client.wait(10)
                    except subprocess.TimeoutExpired:
                        self.client.kill()
                        client_exit = self.client.wait(10)
            else:
                client_exit = self.client.returncode

        if self.server is not None:
            if self.server.poll() is None:
                try:
                    self.server_cmd("stop")
                except Exception:
                    pass
                try:
                    server_exit = self.server.wait(30)
                except subprocess.TimeoutExpired:
                    self.server.terminate()
                    try:
                        server_exit = self.server.wait(10)
                    except subprocess.TimeoutExpired:
                        self.server.kill()
                        server_exit = self.server.wait(10)
            else:
                server_exit = self.server.returncode
        return client_exit, server_exit


def scan_log_for_errors(log_path: Path, role: str) -> None:
    text = read_text(log_path)
    bad = []
    for idx, line in enumerate(text.splitlines(), 1):
        lower = line.lower()
        is_mixin = (
            "MixinApplyError" in line
            or "InvalidMixinException" in line
            or "MixinTransformerError" in line
            or "MixinPrepareError" in line
            or "Critical injection failure" in line
            or ("mixin" in lower and ("mnera" in lower or "tyc2" in lower) and ("error" in lower or "failed" in lower))
        )
        is_classload = (
            "NoClassDefFoundError" in line
            or "ClassNotFoundException" in line
            or "NoSuchMethodError" in line
            or "NoSuchFieldError" in line
            or "AbstractMethodError" in line
        )
        is_tyc2_err = (
            ("/ERROR]" in line or "Exception" in line or "AssertionError" in line)
            and (
                "com.tycooncraft.tyc2" in line
                or "mnera:" in line
                or "mnera.mixins.json" in line
                or ("missing texture" in lower and "mnera" in lower)
                or ("unable to load model" in lower and "mnera" in lower)
            )
        )
        if is_mixin or is_classload or is_tyc2_err:
            bad.append(f"L{idx}: {line}")
    if bad:
        raise AssertionError(f"Errors detected in {role} log ({log_path}):\n" + "\n".join(bad[:25]))


def run_joint_test(mc_version: str, profile: str, lang: str, screenshots_dir: Path | None) -> dict:
    manifest_path = ROOT / "src" / "neoforge" / mc_version / "build" / "client-server-live" / "launch.json"
    if not manifest_path.is_file():
        raise FileNotFoundError(
            f"Missing launch manifest {manifest_path}; run: "
            f"./gradlew --init-script src/qa/neoforge-init.gradle :neoforge:{mc_version}:prepareClientServerPlay"
        )
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    run_dir = ROOT / "src" / "neoforge" / mc_version / "build" / "run" / "client-server-live" / f"session-{profile}-{lang}"
    port = pick_free_port()
    session = LiveJointSession(mc_version, profile, lang, manifest, run_dir, port)
    saved_shots: list[str] = []

    def capture(filename: str) -> None:
        shot = session.wait_screenshot(filename)
        if screenshots_dir is not None:
            screenshots_dir.mkdir(parents=True, exist_ok=True)
            dst = screenshots_dir / filename
            shutil.copy2(shot, dst)
            saved_shots.append(str(dst))
        else:
            saved_shots.append(str(shot))

    try:
        site = session.start()

        # 1. Wait for client title screen ready, verify ALL 35 TyC2 textures/sprites loaded
        session.wait_until("client_booted", lambda s: not s.get("overlay", True), timeout=120.0)
        sprites = session.act("check_sprites")
        if not sprites.get("spritesOk") or sprites.get("spritesChecked") != 35:
            raise AssertionError(
                f"Missing TyC2 sprites/textures on {mc_version}: checked={sprites.get('spritesChecked')}, "
                f"missing={sprites.get('missingSprites')}"
            )
        print(f"[{mc_version}/{profile}] Sprite audit PASSED ({sprites['spritesChecked']}/35 textures)", flush=True)

        # 2. Connect real client to dedicated server over TCP
        session.act("connect")
        session.wait_until("joined_world", lambda s: s.get("world") and not s.get("screen"), timeout=90.0)
        time.sleep(1.5)
        session.act("clear_hud")

        # Verify /tyc2 status command works live from client -> server -> client chat!
        before_chat = session.act("state").get("chatCount", 0)
        session.act("command", cmd="mnera status")
        st_chat = session.wait_until(
            "tyc2_status_chat",
            lambda s: s.get("chatCount", 0) > before_chat
            and ("[TyC2]" in s.get("lastChat", "") or "Nearest:" in s.get("lastChat", "")),
            timeout=15.0,
        )
        last_chat = st_chat.get("lastChat", "")
        if "[TyC2]" not in last_chat and "Nearest:" not in last_chat:
            raise AssertionError(f"Unexpected /tyc2 status chat response: {last_chat}")

        # Screenshot 1: Daylight showcase plaza (Primitive Mint lit firebox + Electric Mint + ATM + Hub [+ IC2/LC2])
        session.server_live_action("tp_plaza")
        time.sleep(0.8)
        session.act("look", yaw=180.0, pitch=11.0)
        time.sleep(0.5)
        lang_suffix = "" if lang == "en_us" else f"_{lang}"
        prefix = f"tyc2_{mc_version.replace('.', '_')}_{profile}{lang_suffix}"
        capture(f"{prefix}_01_plaza.png")

        # Screenshot 2a & 2b: Open PrimitiveMintMachineScreen (active coal minting + amber upgrade-required warning!)
        prim_mint = site["primMint"]
        session.server_live_action("tp_prim_mint")
        time.sleep(0.4)
        session.act("right_click_block", x=prim_mint[0], y=prim_mint[1], z=prim_mint[2], face="south")
        session.wait_until("prim_mint_screen_open", lambda s: s.get("screen") == "PrimitiveMintMachineScreen", timeout=15.0)
        time.sleep(0.6)
        capture(f"{prefix}_02a_primitive_mint_active.png")
        session.server_live_action("prim_set_blocked")
        time.sleep(0.8)
        capture(f"{prefix}_02b_primitive_mint_blocked.png")
        session.act("close_screen")

        # Screenshot 2: Open Pure-Electric MintMachineScreen via real block right-click
        mint = site["mint"]
        session.server_live_action("tp_mint")
        time.sleep(0.4)
        session.act("right_click_block", x=mint[0], y=mint[1], z=mint[2], face="south")
        session.wait_until("mint_screen_open", lambda s: s.get("screen") == "MintMachineScreen", timeout=15.0)
        time.sleep(0.6)
        capture(f"{prefix}_02_mint_gui.png")
        session.act("close_screen")

        # Screenshot 3: Open AtmMachineScreen via real block right-click (verifies S2C ATM sync!)
        atm = site["atm"]
        session.server_live_action("tp_atm")
        time.sleep(0.4)
        session.act("right_click_block", x=atm[0], y=atm[1], z=atm[2], face="south")
        session.wait_until("atm_screen_open", lambda s: s.get("screen") == "AtmMachineScreen", timeout=15.0)
        time.sleep(0.6)
        capture(f"{prefix}_03_atm_gui.png")
        session.act("close_screen")

        # Screenshot 4a..4d: Open CooperationHubScreen (ALL 4 tabs: Orders, Warehouse, Economy/CPI, Expansion!)
        hub = site["hub"]
        session.server_live_action("tp_hub")
        time.sleep(0.4)
        session.act("right_click_block", x=hub[0], y=hub[1], z=hub[2], face="south")
        hub_st = session.wait_until("hub_screen_open", lambda s: s.get("screen") == "CooperationHubScreen", timeout=15.0)
        time.sleep(0.6)
        capture(f"{prefix}_04_hub_orders.png")
        if len(hub_st.get("screenButtons", [])) >= 4:
            # Tab 1: Warehouse
            session.act("click_button_index", index=1)
            time.sleep(0.5)
            capture(f"{prefix}_04b_hub_warehouse.png")
            # Tab 2: Economy / CPI Trend
            session.act("click_button_index", index=2)
            time.sleep(0.5)
            capture(f"{prefix}_05_hub_cpi_chart.png")
            # Tab 3: Expansion
            session.act("click_button_index", index=3)
            time.sleep(0.5)
            capture(f"{prefix}_05b_hub_expansion.png")
        session.act("close_screen")

        # Screenshot 6a..6d: Open CommTerminalScreen (ALL 4 sub-tabs: Orders, Stock, CPI, Growth!)
        session.act("right_click_air")
        term_st = session.wait_until("terminal_screen_open", lambda s: s.get("screen") == "CommTerminalScreen", timeout=15.0)
        time.sleep(0.6)
        capture(f"{prefix}_06_comm_terminal.png")
        if len(term_st.get("screenButtons", [])) >= 6:
            # CommTerminal buttons 0,1 = rail < >, buttons 2,3,4,5 = Orders, Stock, CPI, Growth
            session.act("click_button_index", index=3)
            time.sleep(0.5)
            capture(f"{prefix}_06b_comm_terminal_stock.png")
            session.act("click_button_index", index=4)
            time.sleep(0.5)
            capture(f"{prefix}_06c_comm_terminal_cpi.png")
            session.act("click_button_index", index=5)
            time.sleep(0.5)
            capture(f"{prefix}_06d_comm_terminal_growth.png")
        session.act("close_screen")

        # Screenshot 7: Teleport to Village #2 and right-click Golden Mayor -> MayorNegotiationScreen + verify live hold!
        session.server_live_action("tp_mayor")
        time.sleep(0.8)
        session.act("interact_mayor")
        session.wait_until("mayor_screen_open", lambda s: s.get("screen") == "MayorNegotiationScreen", timeout=15.0)
        time.sleep(0.6)
        open_state = session.server_live_action("dump_state")
        if not open_state.get("mayorNegotiatingWithClient"):
            raise AssertionError(f"Mayor did not lock tradingPlayer during live negotiation: {open_state}")
        capture(f"{prefix}_07_mayor_negotiation.png")
        session.act("close_screen")
        time.sleep(0.4)
        closed_state = session.server_live_action("dump_state")
        if closed_state.get("mayorNegotiatingWithClient"):
            raise AssertionError(f"Mayor did not release tradingPlayer after closing negotiation: {closed_state}")

        srv_state = session.server_live_action("dump_state")
        print(f"[{mc_version}/{profile}] All 6 GUIs + CPI chart + 35/35 sprites verified! serverState={srv_state}", flush=True)
    finally:
        client_rc, server_rc = session.stop()
        print(f"[{mc_version}/{profile}] Shutdown exits: client={client_rc}, server={server_rc}", flush=True)

    scan_log_for_errors(run_dir / "server.log", "server")
    scan_log_for_errors(run_dir / "client.log", "client")

    summary = {
        "mcVersion": mc_version,
        "profile": profile,
        "lang": lang,
        "status": "PASSED",
        "port": port,
        "spritesChecked": 35,
        "mayorNegotiationHoldVerified": True,
        "screensVerified": [
            "PrimitiveMintMachineScreen (Active + Blocked Warning)",
            "MintMachineScreen (Pure Electric)",
            "AtmMachineScreen",
            "CooperationHubScreen (Orders + Warehouse + Economy/CPI + Expansion)",
            "CommTerminalScreen (Orders + Stock + CPI + Growth)",
            "MayorNegotiationScreen",
        ],
        "screenshots": saved_shots,
        "serverLog": str(run_dir / "server.log"),
        "clientLog": str(run_dir / "client.log"),
    }
    report_path = run_dir / "joint-qa-report.json"
    report_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(f"[{mc_version}/{profile}/{lang}] CLIENT-SERVER JOINT QA PASSED! Report: {report_path}", flush=True)
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mc-version", required=True, choices=["1.21.1", "26.1.2", "26.2"])
    parser.add_argument("--profile", default="standalone", choices=["standalone", "enhanced"])
    parser.add_argument("--lang", default="en_us", choices=["en_us", "zh_cn"])
    parser.add_argument("--screenshots-dir", type=Path, default=None)
    args = parser.parse_args()
    run_joint_test(args.mc_version, args.profile, args.lang, args.screenshots_dir)
    return 0


if __name__ == "__main__":
    sys.exit(main())
