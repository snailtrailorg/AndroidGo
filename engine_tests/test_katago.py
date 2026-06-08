"""KataGo engine GTP black-box tests.

Prerequisites:
    KataGo binary compiled for host.
    Default path: /tmp/katago/cpp/katago

Usage:
    python3 test_katago.py [--engine /path/to/katago] [--model /path/to/model.txt] [--config /path/to/katago.cfg]
"""

import sys
import os
import tempfile

sys.path.insert(0, os.path.dirname(__file__))
from gtp_test_harness import GtpEngine, GtpError, GtpTimeout, run_tests

KATAGO_BIN = os.environ.get("KATAGO_BIN", "/tmp/katago/cpp/katago")
KATAGO_MODEL = os.environ.get("KATAGO_MODEL", "")
KATAGO_CONFIG = os.environ.get("KATAGO_CONFIG", "")


def _make_config(tmpdir: str) -> str:
    """Create a minimal KataGo GTP config for testing."""
    cfg = os.path.join(tmpdir, "test_katago.cfg")
    model = KATAGO_MODEL
    if not model:
        candidates = [
            "/home/michael/AndroidStudioProjects/AndroidGo/app/src/main/assets/engine/katago_model.txt.gz",
        ]
        for c in candidates:
            if os.path.exists(c):
                model = c
                break
    if not model:
        raise RuntimeError(
            "KataGo model not found. Set KATAGO_MODEL=/path/to/model.txt.gz"
        )

    with open(cfg, "w") as f:
        f.write(f"homeDataDir = {tmpdir}\n")
        f.write("logDir = /dev/null\n")
        f.write("logToStderr = false\n")
        f.write("logAllGTPCommunication = false\n")
        f.write("logSearchInfo = false\n")
        f.write("numSearchThreads = 1\n")
        f.write("maxVisits = 20\n")
        f.write("maxTime = 10\n")
        f.write("nnMaxBatchSize = 1\n")
        f.write("nnCacheSizePowerOfTwo = 14\n")
        f.write("allowResignation = false\n")
        f.write("rules = tromp-taylor\n")
        f.write("reportAnalysisWinratesAs = SIDETOMOVE\n")
    return cfg


def _make_cmd(tmpdir: str) -> str:
    """Build the KataGo command line."""
    cfg = _make_config(tmpdir)
    model = KATAGO_MODEL
    if not model:
        model = "/home/michael/AndroidStudioProjects/AndroidGo/app/src/main/assets/engine/katago_model.txt.gz"
    return f"{KATAGO_BIN} gtp -config {cfg} -model {model}"


_skip_reason = None


def require_katago():
    """Check if KataGo is available. Raises RuntimeError with reason if not."""
    global _skip_reason
    if _skip_reason:
        raise RuntimeError(_skip_reason)

    if not os.path.exists(KATAGO_BIN):
        _skip_reason = f"KataGo binary not found at {KATAGO_BIN}"
        raise RuntimeError(_skip_reason)


def fresh_engine(timeout: float = 30.0) -> GtpEngine:
    """Create a fresh KataGo engine."""
    require_katago()
    tmpdir = tempfile.mkdtemp(prefix="katago_test_")
    cmd = _make_cmd(tmpdir)
    return GtpEngine(cmd, timeout=timeout)


# ── Handshake tests ──

def test_handshake_name():
    g = fresh_engine()
    bodies = g.send(["name"])
    assert "KataGo" in bodies[0], f"Expected 'KataGo' in name, got '{bodies[0]}'"


def test_handshake_version():
    g = fresh_engine()
    bodies = g.send(["version"])
    assert len(bodies[0]) > 0, "version should not be empty"


# ── Basic play tests ──

def test_genmove_returns_coordinate():
    g = fresh_engine(timeout=30.0)
    g.init_game(13, 3.75)
    g.send(["play black C11"])
    move = g.genmove_ok("white")
    assert move is not None, "first genmove should return a coordinate"


def test_genmove_5_turns():
    """5 consecutive genmove calls should all return valid moves."""
    g = fresh_engine(timeout=60.0)
    g.init_game(13, 3.75)
    human_moves = ["C11", "D3", "G7", "K10", "F5"]
    for hm in human_moves:
        g.send([f"play black {hm}"])
        move = g.genmove_ok("white")
        assert move is not None, f"genmove returned pass after {hm}: maxVisits=20 may be too low, try increasing"
        g.send([f"play white {move}"])


def test_clear_board_resets():
    g = fresh_engine(timeout=30.0)
    g.init_game(13, 3.75)
    g.send(["play black C11"])
    g.single("genmove white")
    # Clear and start fresh
    g.send(["clear_board"])
    move = g.genmove_ok("white")
    assert move is not None, "genmove after clear_board on empty board should work"


def test_no_pass_early_game():
    """In early game (board nearly empty), genmove should NOT pass."""
    g = fresh_engine(timeout=30.0)
    g.init_game(13, 3.75)
    g.send(["play black C11"])
    move = g.single("genmove white")
    assert move.upper() not in ("PASS", "RESIGN"), \
        f"genmove on nearly empty board should not pass/resign, got '{move}'"


# ── Note ──
# final_status_list dead and final_score are NOT tested here at the unit level.
# KataGo requires a proper homeDataDir with OpenCL tuning cache write access.
# The Android app integration tests these through the full JNI bridge.


# ── All tests ──

ALL_TESTS = [
    ("handshake name", test_handshake_name),
    ("handshake version", test_handshake_version),
    ("genmove returns coordinate", test_genmove_returns_coordinate),
    ("genmove 5 consecutive turns", test_genmove_5_turns),
    ("clear_board resets state", test_clear_board_resets),
    ("genmove does not pass in early game", test_no_pass_early_game),
]


if __name__ == "__main__":
    try:
        require_katago()
    except RuntimeError as e:
        print(f"SKIP: {e}")
        print("Build KataGo for host first, or set KATAGO_BIN=/path/to/katago")
        sys.exit(0)

    passed, failed = run_tests(ALL_TESTS, name="KataGo")
    sys.exit(0 if failed == 0 else 1)
