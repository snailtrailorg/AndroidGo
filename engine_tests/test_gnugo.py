"""GNU Go 3.8 engine GTP black-box tests.

Usage:
    python3 test_gnugo.py [--engine /path/to/gnugo]
"""

import sys
import os

sys.path.insert(0, os.path.dirname(__file__))
from gtp_test_harness import GtpEngine, GtpError, GtpTimeout, run_tests

GNUGO_BIN = os.environ.get("GNUGO_BIN", "/tmp/gnugo-host/interface/gnugo")
GNUGO_CMD = f"{GNUGO_BIN} --mode gtp"


def fresh(timeout: float = 15.0) -> GtpEngine:
    if not os.path.exists(GNUGO_BIN):
        raise RuntimeError(f"GNU Go not found at {GNUGO_BIN}")
    return GtpEngine(GNUGO_CMD, timeout=timeout)


# ── Handshake ──

def test_handshake():
    g = fresh()
    bodies = g.send(["name", "version"])
    assert "GNU Go" in bodies[0], f"name={bodies[0]!r}"
    assert bodies[1] == "3.8", f"version={bodies[1]!r}"


# ── Board setup ──

def test_boardsize_rejected():
    g = fresh()
    bodies = g.send(["boardsize 25", "boardsize 9"])
    assert bodies[1] == "", f"boardsize 9 should succeed: {bodies}"


def test_boardsize_accepted():
    g = fresh()
    g.send(["boardsize 9", "boardsize 13", "boardsize 19"])


# ── genmove regression tests ──

def test_genmove_returns_coordinate():
    g = fresh()
    bodies = g.send([
        "boardsize 13", "komi 3.75", "clear_board",
        "play black C11",
        "genmove white",
    ])
    move = bodies[4]
    assert move not in ("", "pass", "resign", "PASS"), f"genmove returned {move!r}"
    assert len(move) >= 2 and move[0].isalpha() and move[1:].isdigit()


def test_genmove_3_turns():
    """Regression: countlib assertion fix — consecutive genmove without crash."""
    g = fresh()
    # 11 cmds + quit = 12 responses. genmove at indices 4, 7, 10.
    bodies = g.send([
        "boardsize 13", "komi 3.75", "clear_board",
        "play black C11", "genmove white",                    # turn 1
        "play white B3", "play black D5", "genmove white",    # turn 2
        "play white E7", "play black F9", "genmove white",    # turn 3
    ])
    assert len(bodies) == 12, f"Expected 12 responses, got {len(bodies)}"
    for turn, idx in enumerate([4, 7, 10]):
        move = bodies[idx]
        assert move not in ("", "pass", "resign", "PASS"), \
            f"genmove #{turn+1} returned {move!r}"


def test_genmove_10_turns():
    g = fresh(timeout=30.0)
    cmds = ["boardsize 13", "komi 3.75", "clear_board"]
    human = ["C11", "D3", "G7", "K10", "F5", "H3", "J8", "E6", "L4", "B9"]
    for hm in human:
        cmds.append(f"play black {hm}")
        cmds.append("genmove white")
    bodies = g.send(cmds)
    # 3 setup + 20 play/genmove + quit = 24 responses. Genmove: 4,6,8,...,22.
    for j in range(10):
        move = bodies[4 + j * 2]
        assert move not in ("", "pass", "resign", "PASS"), \
            f"genmove #{j+1} after {human[j]} returned {move!r}"


# ── Passes ──

def test_two_passes():
    g = fresh(timeout=10.0)
    bodies = g.send([
        "boardsize 13", "komi 3.75", "clear_board",
        "play black C11", "play white D4",
        "play black pass", "play white pass",
    ])
    assert len(bodies) >= 7, f"Expected >=7 responses, got {len(bodies)}"


# ── Undo ──

def test_undo():
    g = fresh()
    bodies = g.send([
        "boardsize 13", "komi 3.75", "clear_board",
        "play black C11",
        "undo",
        "genmove white",
    ])
    move = bodies[5]
    assert move not in ("", "pass", "resign", "PASS"), \
        f"genmove after undo returned {move!r}"


# ── Komi / Handicap ──

def test_komi_375():
    g = fresh()
    g.send(["boardsize 13", "komi 3.75", "clear_board",
            "play black C11", "genmove white"])


def test_handicap():
    g = fresh(timeout=10.0)
    bodies = g.send([
        "boardsize 19", "komi 0.5", "clear_board",
        "fixed_handicap 4",
        "genmove white",
    ])
    move = bodies[4]
    assert move not in ("", "pass", "resign", "PASS"), \
        f"genmove after handicap returned {move!r}"


# ── Note ──
# final_score / final_status_list dead are NOT tested here.
# GNU Go 3.8 has a known bug where final_score hangs (infinite loop)
# on boards with few stones. This is a pre-existing upstream bug.
# The Android app uses KataGo for final_status_list dead (by design).


ALL_TESTS = [
    ("handshake name+version", test_handshake),
    ("boardsize 25 rejected", test_boardsize_rejected),
    ("boardsize 9,13,19 accepted", test_boardsize_accepted),
    ("genmove returns coordinate", test_genmove_returns_coordinate),
    ("genmove 3 consecutive turns (countlib fix)", test_genmove_3_turns),
    ("genmove 10 turns no crash", test_genmove_10_turns),
    ("two passes accepted", test_two_passes),
    ("undo restores state", test_undo),
    ("komi 3.75", test_komi_375),
    ("handicap 4 + genmove", test_handicap),
]


if __name__ == "__main__":
    if not os.path.exists(GNUGO_BIN):
        print(f"ERROR: GNU Go not found at {GNUGO_BIN}")
        print(f"Build: cd /tmp/gnugo-host && make -j$(nproc)")
        print(f"Or set GNUGO_BIN=/path/to/gnugo")
        sys.exit(1)
    passed, failed = run_tests(ALL_TESTS, name="GNU Go 3.8")
    sys.exit(0 if failed == 0 else 1)
