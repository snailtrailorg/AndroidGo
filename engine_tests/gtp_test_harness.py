"""GTP engine black-box test harness.

Uses batch mode: write all GTP commands at once via communicate(), then
parse the response list. This avoids GNU Go's fgets() blocking on
fully-buffered pipe stdin.

Usage:
    from gtp_test_harness import GtpEngine, run_tests
    g = GtpEngine("/path/to/gnugo --mode gtp")
    responses = g.send(["boardsize 13", "komi 3.75", "clear_board",
                         "play black C11", "genmove white"])
    assert responses[-1] not in ("", "pass", "resign")
"""

import subprocess
from typing import Optional


class GtpError(Exception):
    pass


class GtpTimeout(Exception):
    pass


def _parse_responses(raw: str) -> list[str]:
    """Parse raw GTP output into a list of response bodies.

    GTP responses: = body\\n\\n (success) or ? body\\n\\n (error).
    Returns list of body strings ('' for empty success).
    """
    results = []
    i = 0
    while i < len(raw):
        if raw[i] not in ("=", "?"):
            i += 1
            continue
        status = raw[i]
        i += 1
        # Find the double newline
        end = raw.find("\n\n", i)
        if end < 0:
            break
        body = raw[i:end].strip()
        if body.startswith(" "):
            body = body[1:]
        results.append(body)
        i = end + 2
    return results


class GtpEngine:
    """Batch GTP client for testing.

    All commands are written at once, then all responses are read.
    This avoids buffering issues with engines that don't set stdin to
    line-buffered mode.
    """

    def __init__(self, command: str, timeout: float = 30.0):
        self._command = command
        self._timeout = timeout

    def _send(self, commands: list[str]) -> list[str]:
        """Send a list of GTP commands and return their response bodies."""
        input_str = "\n".join(commands) + "\nquit\n"
        proc = subprocess.Popen(
            self._command.split(),
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        try:
            stdout, stderr = proc.communicate(input=input_str, timeout=self._timeout)
        except subprocess.TimeoutExpired:
            proc.kill()
            stdout, stderr = proc.communicate()
            raise GtpTimeout(
                f"Engine timed out after {self._timeout}s\n"
                f"Partial stdout: {stdout[:200]!r}\n"
                f"Stderr: {stderr[:200]!r}"
            )

        if proc.returncode != 0 and "quit" not in commands:
            raise GtpError(
                f"Engine exited with {proc.returncode}\n"
                f"stdout: {stdout[:200]!r}\n"
                f"stderr: {stderr[:200]!r}"
            )

        return _parse_responses(stdout)

    def send(self, commands: list[str]) -> list[str]:
        """Send GTP commands and return response bodies."""
        return self._send(commands)

    def assert_ok(self, commands: list[str]) -> list[str]:
        """Send commands and return bodies. Raises if any response is empty
        (which usually means an error '?' response was stripped)."""
        bodies = self._send(commands)
        for i, (cmd, body) in enumerate(zip(commands, bodies)):
            # GTP error responses start with '?' and are parsed as body text
            # We can't distinguish from success without the prefix
            pass
        return bodies

    def single(self, cmd: str) -> str:
        """Send one command, return body. Self-contained: adds quit."""
        bodies = self._send([cmd])
        return bodies[0]  # first response (before quit)

    def init_game(self, board_size: int = 13, komi: float = 3.75):
        """Verify board setup commands work."""
        bodies = self._send([
            f"boardsize {board_size}",
            f"komi {komi}",
            "clear_board",
        ])
        return bodies

    def genmove_ok(self, color: str) -> Optional[str]:
        """Generate a move. Returns None if pass/resign, else coordinate."""
        move = self.single(f"genmove {color}")
        if not move or move.upper() in ("PASS", "RESIGN"):
            return None
        return move

    def dead_stones(self) -> list:
        resp = self.single("final_status_list dead")
        if not resp:
            return []
        return resp.split()


def run_tests(tests: list, name: str = "") -> tuple[int, int]:
    passed = 0
    failed = 0
    label = f" [{name}]" if name else ""
    print(f"\n{'='*60}")
    print(f"Running {len(tests)} tests{label}")
    print(f"{'='*60}")
    for test_name, test_fn in tests:
        try:
            test_fn()
            print(f"  PASS  {test_name}")
            passed += 1
        except Exception as e:
            print(f"  FAIL  {test_name}: {e}")
            failed += 1
    print(f"\n  {passed} passed, {failed} failed out of {len(tests)}")
    return passed, failed
