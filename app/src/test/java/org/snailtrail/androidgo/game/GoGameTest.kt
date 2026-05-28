package org.snailtrail.androidgo.game

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GoGameTest {

    // --- 基本落子 ---

    @Test fun `place stone on empty intersection`() {
        val game = GoGame(19)
        assertTrue(game.placeStone(3, 3))
        val s = game.state.value
        assertEquals(StoneColor.Black, s.stones[3 to 3])
        assertEquals(StoneColor.White, s.currentPlayer)
        assertEquals(1, s.moveHistory.size)
    }

    @Test fun `cannot place on occupied intersection`() {
        val game = GoGame(19)
        game.placeStone(3, 3)
        assertFalse(game.placeStone(3, 3))
    }

    @Test fun `cannot place out of bounds`() {
        val game = GoGame(19)
        assertFalse(game.placeStone(-1, 0))
        assertFalse(game.placeStone(0, -1))
        assertFalse(game.placeStone(19, 0))
        assertFalse(game.placeStone(0, 19))
    }

    @Test fun `cannot place after game over`() {
        val game = GoGame(19)
        game.pass()
        game.pass()
        assertTrue(game.state.value.gameOver)
        assertFalse(game.placeStone(3, 3))
    }

    // --- 虚手 (pass now records to moveHistory) ---

    @Test fun `pass adds to moveHistory`() {
        val game = GoGame(19)
        game.pass()
        val s = game.state.value
        assertEquals(1, s.moveHistory.size)
        assertTrue(s.moveHistory.last().isPass)
        assertEquals(StoneColor.White, s.currentPlayer)
        assertEquals(1, s.consecutivePasses)
    }

    @Test fun `two passes end game`() {
        val game = GoGame(19)
        game.pass()
        assertFalse(game.state.value.gameOver)
        game.pass()
        assertTrue(game.state.value.gameOver)
        assertEquals(2, game.state.value.consecutivePasses)
        assertEquals(2, game.state.value.moveHistory.size)
    }

    @Test fun `consecutive passes reset after stone`() {
        val game = GoGame(19)
        game.pass()
        assertEquals(1, game.state.value.consecutivePasses)
        game.placeStone(3, 3)
        assertEquals(0, game.state.value.consecutivePasses)
    }

    // --- 提子 ---

    @Test fun `capture single corner stone`() {
        val game = GoGame(9)
        game.placeStone(0, 1) // B
        game.placeStone(0, 0) // W
        game.placeStone(1, 0) // B captures W at (0,0)
        val s = game.state.value
        assertNull(s.stones[0 to 0])
        assertEquals(StoneColor.Black, s.stones[0 to 1])
        assertEquals(StoneColor.Black, s.stones[1 to 0])
        assertEquals(1, s.moveHistory.last().capturedStones.size)
    }

    // --- 禁着 ---

    @Test fun `suicide in corner prevented`() {
        val game = GoGame(9)
        game.placeStone(0, 1) // B
        game.placeStone(8, 0) // W - play far away
        game.placeStone(1, 0) // B
        // Now (0,0) is surrounded by B at (0,1) and (1,0), corner
        // B has no liberties there, and W can't capture → suicide
        assertFalse(game.placeStone(0, 0))
    }

    // --- 打劫 ---

    @Test fun `ko prevents immediate recapture`() {
        val game = GoGame(9)
        game.placeStone(0, 1); game.placeStone(0, 2)
        game.placeStone(0, 3); game.placeStone(1, 1)
        game.pass(); game.placeStone(1, 3)
        game.pass(); game.placeStone(2, 2)
        assertTrue(game.placeStone(1, 2)) // B captures
        assertNull(game.state.value.stones[0 to 2])
        assertFalse(game.placeStone(0, 2)) // ko
    }

    @Test fun `non-ko recapture after intervening move`() {
        val game = GoGame(9)
        game.placeStone(0, 1); game.placeStone(0, 2)
        game.placeStone(0, 3); game.placeStone(1, 1)
        game.pass(); game.placeStone(1, 3)
        game.pass(); game.placeStone(2, 2)
        game.placeStone(1, 2) // B captures
        game.placeStone(5, 5) // W elsewhere
        game.placeStone(5, 6) // B elsewhere
        assertTrue(game.placeStone(0, 2)) // recapture OK
    }

    // --- 悔棋/恢复 ---

    @Test fun `undo restores previous state`() {
        val game = GoGame(19)
        game.placeStone(3, 3)
        assertEquals(1, game.state.value.moveHistory.size)
        game.undo()
        assertEquals(0, game.state.value.moveHistory.size)
        assertEquals(StoneColor.Black, game.state.value.currentPlayer)
    }

    @Test fun `undo pass restores pass count`() {
        val game = GoGame(19)
        game.pass()
        assertEquals(1, game.state.value.consecutivePasses)
        game.undo()
        assertEquals(0, game.state.value.moveHistory.size)
        assertEquals(0, game.state.value.consecutivePasses)
        assertEquals(StoneColor.Black, game.state.value.currentPlayer)
    }

    @Test fun `redo replays undone move`() {
        val game = GoGame(19)
        game.placeStone(3, 3)
        game.undo()
        assertTrue(game.redo())
        assertEquals(1, game.state.value.moveHistory.size)
    }

    @Test fun `redo clears after new move`() {
        val game = GoGame(19)
        game.placeStone(3, 3)
        game.undo()
        game.placeStone(4, 4)
        assertFalse(game.redo())
    }

    // --- 让子 ---

    @Test fun `handicap 2 places corner stones`() {
        val game = GoGame(19)
        game.setHandicap(2)
        assertEquals(2, game.state.value.stones.size)
        assertEquals(2, game.state.value.handicap)
        // Black plays first after handicap
        assertEquals(StoneColor.Black, game.state.value.currentPlayer)
    }

    @Test fun `handicap 9 places all stones`() {
        val game = GoGame(19)
        game.setHandicap(9)
        assertEquals(9, game.state.value.stones.size)
    }

    @Test fun `handicap 0 clears`() {
        val game = GoGame(19)
        game.setHandicap(4)
        assertEquals(4, game.state.value.handicap)
        game.reset(19)
        game.setHandicap(0)
        assertEquals(0, game.state.value.handicap)
    }

    // --- 点目 ---

    @Test fun `komi included in score`() {
        val game = GoGame(19)
        game.setKomi(6.5f)
        val score = game.countTerritory()
        assertEquals(6.5f, score.komi)
        assertEquals(6.5f, score.whiteScore)
    }

    @Test fun `dead stones excluded from score`() {
        val game = GoGame(9)
        game.placeStone(4, 4) // B
        game.pass()
        game.pass()
        val score = game.countTerritory(setOf(4 to 4))
        // Black stone at (4,4) treated as dead → 0 black stones
        assertEquals(0, score.blackStones)
    }

    // --- 重置 ---

    @Test fun `reset clears game`() {
        val game = GoGame(19)
        game.placeStone(3, 3)
        game.placeStone(15, 15)
        game.reset(19)
        val s = game.state.value
        assertEquals(0, s.stones.size)
        assertEquals(StoneColor.Black, s.currentPlayer)
        assertEquals(0, s.moveHistory.size)
    }
}

class GoUtilsTest {

    @Test fun `gtpToBoardPos basic conversions`() {
        // A1 = bottom-left
        assertEquals(18 to 0, gtpToBoardPos("A1", 19))
        // D4 in 19x19 = row 15, col 3
        assertEquals(15 to 3, gtpToBoardPos("D4", 19))
        // T19 = top-right
        assertEquals(0 to 18, gtpToBoardPos("T19", 19))
    }

    @Test fun `gtpToBoardPos skips I`() {
        // H is before I, no skip: H=col 7, row=19-1=18
        assertEquals(18 to 7, gtpToBoardPos("H1", 19))
        // J skips I: J=col 8 (A=0,B=1,...,H=7,skip I,J=8), row=19-1=18
        assertEquals(18 to 8, gtpToBoardPos("J1", 19))
    }

    @Test fun `gtpToBoardPos pass returns negative`() {
        assertEquals(-1 to -1, gtpToBoardPos("pass", 19))
        assertEquals(-1 to -1, gtpToBoardPos("PASS", 19))
        assertEquals(-1 to -1, gtpToBoardPos("tt", 19))
        assertEquals(-1 to -1, gtpToBoardPos("", 19))
    }

    @Test fun `boardPosToGtp roundtrip`() {
        assertEquals("D4", boardPosToGtp(15, 3, 19))
        assertEquals("A1", boardPosToGtp(18, 0, 19))
        assertEquals("T19", boardPosToGtp(0, 18, 19))
    }

    @Test fun `boardPosToGtp skips I`() {
        assertEquals("H1", boardPosToGtp(18, 7, 19))
        assertEquals("J1", boardPosToGtp(18, 8, 19))
    }

    @Test fun `gtpToBoardPos rejects invalid`() {
        assertEquals(-1 to -1, gtpToBoardPos("Z1", 19))
        assertEquals(-1 to -1, gtpToBoardPos("A", 19))
        assertEquals(-1 to -1, gtpToBoardPos("A99", 19))
    }
}

class SgfUtilTest {

    @Test fun `export and import roundtrip`() {
        val game = GoGame(13)
        game.setKomi(7.5f)
        game.placeStone(6, 6) // B
        game.placeStone(6, 7) // W
        game.placeStone(7, 6) // B
        game.pass()           // W
        game.pass()           // B → game over

        val file = File.createTempFile("test", ".sgf")
        file.deleteOnExit()
        SgfUtil.exportToFile(game.state.value, file)

        val parsed = SgfUtil.parseFromFile(file)
        assertNotNull(parsed)
        assertEquals(13, parsed!!.boardSize)
        assertEquals(7.5f, parsed.komi)
        // 3 stones + 2 passes = 5 moves
        assertEquals(5, parsed.moves.size)
        // Verify pass moves
        assertEquals(-1 to -1, parsed.moves[3])
        assertEquals(-1 to -1, parsed.moves[4])
    }

    @Test fun `import with handicap`() {
        val game = GoGame(9)
        game.setHandicap(4)
        val file = File.createTempFile("handicap", ".sgf")
        file.deleteOnExit()
        SgfUtil.exportToFile(game.state.value, file)

        val parsed = SgfUtil.parseFromFile(file)
        assertNotNull(parsed)
        assertEquals(4, parsed!!.handicap)
    }

    @Test fun `import pass moves via roundtrip`() {
        // Use roundtrip to verify pass moves survive export→import
        val game = GoGame(19)
        game.placeStone(3, 3) // B
        game.pass()           // W
        game.placeStone(15, 15) // B
        game.pass()           // W
        game.pass()           // B → game over
        val file = File.createTempFile("passrt", ".sgf")
        file.deleteOnExit()
        SgfUtil.exportToFile(game.state.value, file)
        val parsed = SgfUtil.parseFromFile(file)
        assertNotNull(parsed)
        // 2 stones + 3 passes = 5 moves (consecutive pass reset by stone)
        assertEquals(5, parsed!!.moves.size)
        assertEquals(-1 to -1, parsed.moves[1])
        assertEquals(-1 to -1, parsed.moves[3])
        assertEquals(-1 to -1, parsed.moves[4])
    }
}
