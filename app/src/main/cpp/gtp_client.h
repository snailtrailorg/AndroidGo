#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace gtp {

// Go stone coordinate: column A-T (skip I), row 1-19
struct Stone {
    char col; // 'A'-'T', skipping 'I'
    int row;  // 1-19

    bool isValid() const;
    bool operator==(const Stone &o) const { return col == o.col && row == o.row; }
    bool operator!=(const Stone &o) const { return !(*this == o); }
    std::string toGtp() const;       // e.g. "D4", "pass" handled separately
    static Stone fromGtp(const std::string &s);
};

struct Move {
    enum Type { Stone, Pass, Resign };
    Type type;
    gtp::Stone stone;
    bool black; // true = Black, false = White
};

// Callback interface — replaces Qt signals
struct ClientCallbacks {
    std::function<void()> onBoardChanged;
    std::function<void(int)> onBoardSizeChanged;
    std::function<void(bool)> onCurrentPlayerChanged; // true=Black
    std::function<void(int)> onConsecutivePasses;     // 0 to clear
    std::function<void()> onPassMovePlayed;
    std::function<void()> onResigned;
    std::function<void(bool)> onWaiting;
    std::function<void(bool)> onCanUndoChanged;
    std::function<void(bool)> onCanRedoChanged;
    std::function<void(const std::string &)> onError;
};

// Pure C++ GTP client — no Qt, no Boost
// Communicates with a Go engine (GNU Go, KataGo) via stdin/stdout pipes
class GtpClient {
public:
    GtpClient();
    ~GtpClient();

    // Lifecycle
    bool start(const std::string &command);
    // Attach to an already-running process (started via ProcessBuilder).
    // Takes ownership of the fds — caller must not close them.
    bool attachToProcess(int pid, int stdinFd, int stdoutFd);
    void stop();
    bool isRunning() const;

    // Interrupt a running genmove — writes stop command to engine
    void interrupt();

    // Board setup
    bool setBoardSize(int size);
    bool setKomi(float komi);
    bool setFixedHandicap(int handicap);
    bool init();           // clear_board
    bool init(const std::string &sgfFile, int moveNumber = 0);
    bool save(const std::string &sgfFile);

    // Gameplay
    bool playMove(const Move &move);
    bool generateMove(bool black, bool undoable = true);
    bool undoMove();
    bool redoMove();

    // Queries
    std::string engineName() const { return m_engineName; }
    std::string engineVersion() const { return m_engineVersion; }
    int boardSize() const { return m_boardSize; }
    float komi() const { return m_komi; }
    int moveCount();
    std::vector<Stone> stones(bool black);    // list_stones
    std::vector<Move> moveHistory();
    int captures(bool black);
    std::string finalScore();
    std::string estimatedScore();
    std::vector<Stone> deadStones();
    std::vector<Stone> bestMoves(bool black);
    std::vector<Stone> legalMoves(bool black);
    std::vector<Stone> liberties(const Stone &stone);

    // Last generated move (from genmove response)
    std::string lastGeneratedMove() const;

    // State
    bool isBlackTurn() const { return m_blackTurn; }
    int consecutivePasses() const { return m_consecutivePasses; }
    bool isFinished() const { return m_consecutivePasses >= 2; }

    // Callbacks
    ClientCallbacks callbacks;

private:
    // Process/thread management
    int m_pid = -1;
    int m_stdinFd = -1;
    int m_stdoutFd = -1;
    bool m_running = false;
    void* m_engineHandle = nullptr;
    pthread_t m_engineThread = 0;
    bool m_useThread = false;  // true = in-process thread, false = fork+exec

    // Engine info
    std::string m_engineName;
    std::string m_engineVersion;

    // Board state (tracked separately from engine for undo/redo)
    int m_boardSize = 19;
    float m_komi = 6.5f;
    bool m_blackTurn = true;
    int m_consecutivePasses = 0;

    // Last generated move response
    std::string m_lastGeneratedMove;

    // Undo/redo
    struct UndoEntry {
        Move move;
        std::vector<Stone> captured;
    };
    std::vector<UndoEntry> m_undoStack;
    int m_undoIndex = 0;

    // GTP communication
    bool sendCommand(const std::string &cmd);
    bool readResponse(std::string &out, bool nonBlocking = false);
    std::string readLine();
    bool waitResponse(bool nonBlocking = false);

    // Interrupt flag — set by interrupt(), checked in readResponse
    std::atomic<bool> m_interrupted{false};

    // Process helpers
    static bool spawnProcess(const std::string &command, int &pid, int &inFd, int &outFd);
    void killProcess();

    // Undo helpers
    bool canUndo() const { return m_undoIndex > 0; }
    bool canRedo() const { return m_undoIndex < (int)m_undoStack.size(); }
    void emitUndoRedoState();
};

// Parse a GTP coordinate string like "A1", "T19", "pass", "resign"
// col: A-T (skip I), row: 1-19
Stone parseCoord(const std::string &s);
std::string coordToString(const Stone &s);

// Split GTP space-separated response into tokens
std::vector<std::string> splitTokens(const std::string &s);

} // namespace gtp
