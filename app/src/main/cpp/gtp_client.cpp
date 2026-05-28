#include "gtp_client.h"

#include <android/log.h>
#include <array>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include <stdexcept>
#include <unistd.h>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <fcntl.h>

#define GTP_LOG(...) __android_log_print(ANDROID_LOG_ERROR, "GtpClient", __VA_ARGS__)

namespace gtp {

// --- Stone ---

bool Stone::isValid() const {
    return row >= 1 && row <= 19 &&
           col >= 'A' && col != 'I' && col <= 'T';
}

std::string Stone::toGtp() const {
    return std::string(1, col) + std::to_string(row);
}

Stone Stone::fromGtp(const std::string &s) {
    if (s.empty() || s.size() < 2) return Stone{0, 0};
    return Stone{s[0], std::stoi(s.substr(1))};
}

Stone parseCoord(const std::string &s) {
    return Stone::fromGtp(s);
}

std::string coordToString(const Stone &s) {
    return s.toGtp();
}

std::vector<std::string> splitTokens(const std::string &s) {
    std::vector<std::string> result;
    std::istringstream iss(s);
    std::string token;
    while (iss >> token) result.push_back(token);
    return result;
}

// --- Process Management ---

bool GtpClient::spawnProcess(const std::string &command, int &pid, int &inFd, int &outFd) {
    int stdinPipe[2] = {-1, -1};
    int stdoutPipe[2] = {-1, -1};

    if (pipe(stdinPipe) < 0 || pipe(stdoutPipe) < 0) {
        if (stdinPipe[0] >= 0) { close(stdinPipe[0]); close(stdinPipe[1]); }
        if (stdoutPipe[0] >= 0) { close(stdoutPipe[0]); close(stdoutPipe[1]); }
        return false;
    }

    pid = fork();
    if (pid < 0) {
        GTP_LOG("fork() failed: %s", strerror(errno));
        close(stdinPipe[0]); close(stdinPipe[1]);
        close(stdoutPipe[0]); close(stdoutPipe[1]);
        return false;
    }

    if (pid == 0) {
        // === Child process setup before exec ===
        // 1. New session — detach from parent's process group
        setsid();

        // 2. Redirect stdin/stdout to our pipes BEFORE closing all fds
        dup2(stdinPipe[0], STDIN_FILENO);
        dup2(stdoutPipe[1], STDOUT_FILENO);

        // 3. Now close ALL other inherited fds
        int maxFd = (int)sysconf(_SC_OPEN_MAX);
        if (maxFd <= 0 || maxFd > 4096) maxFd = 1024;
        for (int i = 3; i < maxFd; i++) close(i);

        // 3. Ignore SIGPIPE — engine may crash but pipe should not kill us
        signal(SIGPIPE, SIG_IGN);

        // 4. Inform lmkd: treat this as a normal app process, not a phantom
        int oomFd = open("/proc/self/oom_score_adj", O_WRONLY);
        if (oomFd >= 0) { ssize_t n = write(oomFd, "0", 1); (void)n; close(oomFd); }

        // 5. Android security: no new privileges after exec
        prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0);

        // 6. Parse command and exec
        std::vector<std::string> args;
        std::istringstream iss(command);
        std::string token;
        while (iss >> token) { args.push_back(token); }
        std::vector<char*> argv;
        for (auto &a : args) { argv.push_back(&a[0]); }
        argv.push_back(nullptr);

        GTP_LOG("execv(%s)", argv[0]);
        execv(argv[0], argv.data());
        GTP_LOG("execv failed: %s", strerror(errno));
        _exit(127);
    }

    // Parent: keep the write end of stdin and read end of stdout
    close(stdinPipe[0]);
    close(stdoutPipe[1]);
    inFd = stdinPipe[1];
    outFd = stdoutPipe[0];

    // Set stdout to non-blocking
    int flags = fcntl(outFd, F_GETFL, 0);
    fcntl(outFd, F_SETFL, flags | O_NONBLOCK);

    return true;
}

void GtpClient::killProcess() {
    if (m_useThread) {
        // Signal the engine thread to exit by closing stdin.
        // The engine's getline(cin) will see EOF and exit the GTP loop.
        if (m_stdinFd >= 0) { close(m_stdinFd); m_stdinFd = -1; }
        // Wait for engine thread to finish before cleanup.
        // Search is bounded by maxVisits (100) so this is fast.
        if (m_engineThread) {
            pthread_join(m_engineThread, nullptr);
            m_engineThread = 0;
        }
        // Safe to clean up now — thread has exited
        if (m_stdoutFd >= 0) { close(m_stdoutFd); m_stdoutFd = -1; }
        if (m_engineHandle) { dlclose(m_engineHandle); m_engineHandle = nullptr; }
        m_useThread = false;
        return;
    }
    if (m_pid > 0) {
        // Send quit command first, then terminate
        if (m_stdinFd >= 0) {
            const char *quit = "quit\n";
            ssize_t n = write(m_stdinFd, quit, strlen(quit));
            (void)n;
        }
        // Give it a moment, then SIGTERM
        usleep(100000);
        kill(m_pid, SIGTERM);
        int status;
        waitpid(m_pid, &status, WNOHANG);
        // Force kill after 1s
        usleep(1000000);
        waitpid(m_pid, &status, WNOHANG);
        if (kill(m_pid, 0) == 0) {
            kill(m_pid, SIGKILL);
            waitpid(m_pid, &status, 0);
        }
    }
    if (m_stdinFd >= 0) { close(m_stdinFd); m_stdinFd = -1; }
    if (m_stdoutFd >= 0) { close(m_stdoutFd); m_stdoutFd = -1; }
    m_pid = -1;
}

// --- GtpClient ---

GtpClient::GtpClient() = default;

GtpClient::~GtpClient() {
    stop();
}

struct ThreadArgs {
    int (*fn)(int, const char**, int, int);
    int argc;
    const char** argv;
    int stdinFd;
    int stdoutFd;
};

static void* engineThreadFunc(void* arg) {
    ThreadArgs* ta = (ThreadArgs*)arg;
    ta->fn(ta->argc, ta->argv, ta->stdinFd, ta->stdoutFd);
    delete ta;
    return nullptr;
}

bool GtpClient::start(const std::string &command) {
    if (m_running) stop();

    // Parse command into argv
    std::vector<std::string> args;
    std::istringstream iss(command);
    std::string token;
    while (iss >> token) { args.push_back(token); }

    // Try in-process thread mode (方案1: no fork, no cgroup issue)
    void* handle = dlopen(args[0].c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle) {
        auto* gtpmain = (int(*)(int, const char**, int, int))
            dlsym(handle, "katago_gtp_main");
        if (gtpmain) {
            GTP_LOG("using in-process thread mode");
            // Create pipe pair: stdin for GTP commands, stdout for responses
            int inPipe[2], outPipe[2];
            if (pipe(inPipe) < 0 || pipe(outPipe) < 0) {
                dlclose(handle);
                return false;
            }

            // Build argv for bridge — skip args[0] (engine path) so
            // katago_gtp_main receives args matching normal CLI convention
            std::vector<const char*> argv;
            for (size_t i = 1; i < args.size(); i++) argv.push_back(args[i].c_str());
            argv.push_back(nullptr);

            auto* ta = new ThreadArgs{gtpmain, (int)argv.size()-1, argv.data(),
                                       inPipe[0], outPipe[1]};

            pthread_create(&m_engineThread, nullptr, engineThreadFunc, ta);

            m_pid = 0;  // no real PID for thread
            m_stdinFd = inPipe[1];
            m_stdoutFd = outPipe[0];
            m_engineHandle = handle;
            m_useThread = true;
            m_running = true;

            // Set non-blocking
            int flags = fcntl(m_stdoutFd, F_GETFL, 0);
            fcntl(m_stdoutFd, F_SETFL, flags | O_NONBLOCK);

            // Flush banner
            usleep(200000);
            char discardBuf[4096];
            ssize_t flushed;
            while ((flushed = read(m_stdoutFd, discardBuf, sizeof(discardBuf))) > 0) {}

            // GTP handshake
            GTP_LOG("handshake: sending name");
            if (!sendCommand("name")) { GTP_LOG("handshake: send name FAILED"); return false; }
            std::string nameResp;
            GTP_LOG("handshake: waiting for name response...");
            if (!readResponse(nameResp)) { GTP_LOG("handshake: read name FAILED"); return false; }
            m_engineName = nameResp;
            GTP_LOG("handshake: name=%s", nameResp.c_str());
            if (!sendCommand("version")) { GTP_LOG("handshake: send version FAILED"); return false; }
            std::string verResp;
            if (!readResponse(verResp)) { GTP_LOG("handshake: read version FAILED"); return false; }
            m_engineVersion = verResp;

            GTP_LOG("engine started: %s v%s", m_engineName.c_str(), m_engineVersion.c_str());
            return true;
        }
        dlclose(handle);
    }

    // Fallback: fork+exec for engines without katago_gtp_main (GNU Go)
    if (!spawnProcess(command, m_pid, m_stdinFd, m_stdoutFd)) {
        if (callbacks.onError)
            callbacks.onError("Failed to start engine process: " + std::string(strerror(errno)));
        return false;
    }
    m_running = true;

    // Flush engine banner from stdout before GTP handshake.
    // Some engines (KataGo) print startup info to stdout, which breaks
    // GTP parsing (banner lines don't start with '=' or '?').
    usleep(200000);  // 200ms for engine to print its banner
    char discardBuf[4096];
    ssize_t flushed;
    while ((flushed = read(m_stdoutFd, discardBuf, sizeof(discardBuf))) > 0) {
        // Keep draining until pipe is empty
    }

    // GTP handshake: ask for engine name
    if (!sendCommand("name")) return false;
    std::string nameResp;
    if (!readResponse(nameResp)) return false;
    m_engineName = nameResp;

    // Ask for engine version
    if (!sendCommand("version")) return false;
    std::string verResp;
    if (!readResponse(verResp)) return false;
    m_engineVersion = verResp;

    GTP_LOG("engine started: %s v%s", m_engineName.c_str(), m_engineVersion.c_str());
    return true;
}

bool GtpClient::attachToProcess(int pid, int stdinFd, int stdoutFd) {
    if (m_running) stop();
    m_pid = pid;
    m_stdinFd = stdinFd;
    m_stdoutFd = stdoutFd;

    // Set stdout to non-blocking
    int flags = fcntl(m_stdoutFd, F_GETFL, 0);
    fcntl(m_stdoutFd, F_SETFL, flags | O_NONBLOCK);
    m_running = true;

    // Flush engine banner before GTP handshake
    usleep(200000);
    char discardBuf[4096];
    ssize_t flushed;
    while ((flushed = read(m_stdoutFd, discardBuf, sizeof(discardBuf))) > 0) {}

    // GTP handshake
    if (!sendCommand("name")) return false;
    std::string nameResp;
    if (!readResponse(nameResp)) return false;
    m_engineName = nameResp;

    if (!sendCommand("version")) return false;
    std::string verResp;
    if (!readResponse(verResp)) return false;
    m_engineVersion = verResp;

    return true;
}

void GtpClient::stop() {
    m_running = false;
    killProcess();
}

void GtpClient::interrupt() {
    m_interrupted = true;
    // Write an empty line to engine stdin — GTP engines treat received
    // input during genmove as a signal to abort search and return best-so-far.
    if (m_stdinFd >= 0) {
        const char *cmd = "\n";
        ssize_t n = write(m_stdinFd, cmd, 1);
        (void)n;
    }
}

bool GtpClient::isRunning() const {
    if (!m_running) return false;
    if (m_useThread) return true;  // thread is alive while m_running
    if (m_pid > 0 && kill(m_pid, 0) != 0) return false;
    return true;
}

// --- GTP Low-level ---

bool GtpClient::sendCommand(const std::string &cmd) {
    if (!isRunning()) {
        if (callbacks.onError) callbacks.onError("Engine not running");
        return false;
    }

    std::string line = cmd + "\n";
    ssize_t written = write(m_stdinFd, line.c_str(), line.size());
    return written == (ssize_t)line.size();
}

std::string GtpClient::readLine() {
    std::string line;
    char c;
    while (true) {
        ssize_t n = read(m_stdoutFd, &c, 1);
        if (n <= 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // No data yet — wait a bit
                usleep(10000);
                continue;
            }
            break;
        }
        if (c == '\n') break;
        line += c;
    }
    return line;
}

bool GtpClient::readResponse(std::string &out, bool nonBlocking) {
    if (callbacks.onWaiting) callbacks.onWaiting(true);

    std::string accum;
    int consecutiveNewlines = 0;
    bool gotFirstChar = false;
    char firstChar = 0;
    int interruptStrikes = 0;

    while (true) {
        char c;
        ssize_t n = read(m_stdoutFd, &c, 1);

        if (n <= 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                if (nonBlocking) break;
                // Safety net: if engine didn't respond to interrupt after ~1s
                if (m_interrupted && ++interruptStrikes > 200) {
                    if (callbacks.onWaiting) callbacks.onWaiting(false);
                    m_interrupted = false;
                    out = "";
                    return false;
                }
                usleep(5000);
                continue;
            }
            // Process error
            if (callbacks.onError) {
                std::string errMsg;
                if (errno != 0) errMsg = "Engine read error: " + std::string(strerror(errno));
                else errMsg = "Engine closed connection unexpectedly";
                callbacks.onError(errMsg);
            }
            if (callbacks.onWaiting) callbacks.onWaiting(false);
            return false;
        }

        accum += c;

        if (!gotFirstChar) {
            firstChar = c;
            gotFirstChar = true;
        }

        if (c == '\n') {
            consecutiveNewlines++;
            // GTP response ends with \n\n (blank line)
            if (consecutiveNewlines >= 2) break;
        } else if (c != '\r') {
            consecutiveNewlines = 0;
        }
    }

    if (callbacks.onWaiting) callbacks.onWaiting(false);

    // Parse response: first char is '=' (success) or '?' (error)
    if (!gotFirstChar) {
        out = "";
        return false;
    }

    bool success = (firstChar == '=');

    // Strip leading "= " or "? " and trailing "\n\n"
    size_t start = 1;
    if (start < accum.size() && accum[start] == ' ') start++;

    // Find end (before the double newline)
    size_t end = accum.size();
    while (end > start && (accum[end - 1] == '\n' || accum[end - 1] == '\r'))
        end--;

    out = accum.substr(start, end - start);
    return success;
}

bool GtpClient::waitResponse(bool nonBlocking) {
    std::string dummy;
    return readResponse(dummy, nonBlocking);
}

// --- Board Setup ---

bool GtpClient::setBoardSize(int size) {
    if (!sendCommand("boardsize " + std::to_string(size))) return false;
    if (!waitResponse()) return false;
    m_boardSize = size;
    if (callbacks.onBoardSizeChanged) callbacks.onBoardSizeChanged(size);
    return true;
}

bool GtpClient::setKomi(float komi) {
    char buf[32];
    snprintf(buf, sizeof(buf), "komi %.1f", komi);
    if (!sendCommand(buf)) return false;
    if (!waitResponse()) return false;
    m_komi = komi;
    return true;
}

bool GtpClient::setFixedHandicap(int handicap) {
    if (!sendCommand("fixed_handicap " + std::to_string(handicap))) return false;
    if (!waitResponse()) return false;
    return true;
}

bool GtpClient::init() {
    m_undoStack.clear();
    m_undoIndex = 0;
    m_blackTurn = true;
    m_consecutivePasses = 0;
    emitUndoRedoState();

    if (!sendCommand("clear_board")) return false;
    if (!waitResponse()) return false;
    if (callbacks.onBoardChanged) callbacks.onBoardChanged();
    return true;
}

bool GtpClient::init(const std::string &sgfFile, int moveNumber) {
    m_undoStack.clear();
    m_undoIndex = 0;
    m_blackTurn = true;
    m_consecutivePasses = 0;
    emitUndoRedoState();

    std::string cmd = "loadsgf " + sgfFile;
    if (moveNumber > 0) cmd += " " + std::to_string(moveNumber);
    if (!sendCommand(cmd)) return false;
    if (!waitResponse()) return false;

    // Query board size from loaded SGF
    if (!sendCommand("query_boardsize")) return false;
    std::string bsResp;
    if (readResponse(bsResp)) {
        try { m_boardSize = std::stoi(bsResp); } catch (...) {}
    }

    // Query komi
    if (!sendCommand("get_komi")) return false;
    std::string komiResp;
    if (readResponse(komiResp)) {
        try { m_komi = std::stof(komiResp); } catch (...) {}
    }

    // Query handicap
    if (!sendCommand("get_handicap")) return false;
    std::string hcResp;
    readResponse(hcResp);

    // Sync turn: count moves
    if (m_boardSize > 0) {
        int count = moveCount();
        m_blackTurn = (count % 2 == 0);
    }

    if (callbacks.onBoardChanged) callbacks.onBoardChanged();
    if (callbacks.onBoardSizeChanged) callbacks.onBoardSizeChanged(m_boardSize);
    return true;
}

bool GtpClient::save(const std::string &sgfFile) {
    if (!sendCommand("printsgf " + sgfFile)) return false;
    return waitResponse();
}

// --- Gameplay ---

bool GtpClient::playMove(const Move &move) {
    std::string cmd = "play ";
    cmd += move.black ? "black " : "white ";

    switch (move.type) {
    case Move::Stone:
        cmd += move.stone.toGtp();
        break;
    case Move::Pass:
        cmd += "pass";
        break;
    case Move::Resign:
        cmd += "resign";
        break;
    }

    // Get captures before the move
    std::string capCmd = "captures ";
    capCmd += move.black ? "white" : "black";
    if (!sendCommand(capCmd)) return false;
    std::string capResp;
    int capsBefore = 0;
    if (readResponse(capResp)) {
        try { capsBefore = std::stoi(capResp); } catch (...) {}
    }

    if (!sendCommand(cmd)) return false;
    if (!waitResponse()) return false;

    // Get captures after the move
    if (!sendCommand(capCmd)) return false;
    std::string capAfterResp;
    int capsAfter = 0;
    if (readResponse(capAfterResp)) {
        try { capsAfter = std::stoi(capAfterResp); } catch (...) {}
    }

    // Build undo entry
    UndoEntry entry;
    entry.move = move;
    if (capsAfter > capsBefore) {
        // Some stones were captured — we'd need to query which ones
        // For now, store the count; the undo will be handled by the engine
    }

    // Trim redo stack, push new entry
    m_undoStack.resize(m_undoIndex);
    m_undoStack.push_back(entry);
    m_undoIndex = (int)m_undoStack.size();

    m_blackTurn = !m_blackTurn;

    if (move.type == Move::Pass) {
        m_consecutivePasses++;
        if (callbacks.onPassMovePlayed) callbacks.onPassMovePlayed();
        if (callbacks.onConsecutivePasses) callbacks.onConsecutivePasses(m_consecutivePasses);
    } else {
        m_consecutivePasses = 0;
        if (callbacks.onConsecutivePasses) callbacks.onConsecutivePasses(0);
    }

    if (move.type == Move::Resign) {
        if (callbacks.onResigned) callbacks.onResigned();
    }

    emitUndoRedoState();
    if (callbacks.onBoardChanged) callbacks.onBoardChanged();
    if (callbacks.onCurrentPlayerChanged) callbacks.onCurrentPlayerChanged(m_blackTurn);
    return true;
}

bool GtpClient::generateMove(bool black, bool undoable) {
    m_interrupted = false; // Fresh start for this genmove
    std::string cmd = "genmove ";
    cmd += black ? "black" : "white";

    if (!sendCommand(cmd)) return false;
    std::string response;
    if (!readResponse(response)) return false;

    // Parse the engine's move response
    Move move;
    move.black = black;

    // Store the raw response
    m_lastGeneratedMove = response;

    if (response == "resign") {
        move.type = Move::Resign;
    } else if (response == "PASS" || response == "pass") {
        move.type = Move::Pass;
    } else {
        move.type = Move::Stone;
        move.stone = Stone::fromGtp(response);
    }

    if (!undoable) {
        // Just play it
        return playMove(move);
    }

    // Add to undo stack directly (engine already made the move)
    UndoEntry entry;
    entry.move = move;
    m_undoStack.resize(m_undoIndex);
    m_undoStack.push_back(entry);
    m_undoIndex = (int)m_undoStack.size();

    m_blackTurn = !m_blackTurn;

    if (move.type == Move::Pass) {
        m_consecutivePasses++;
    } else {
        m_consecutivePasses = 0;
    }

    emitUndoRedoState();
    if (callbacks.onBoardChanged) callbacks.onBoardChanged();
    if (callbacks.onCurrentPlayerChanged) callbacks.onCurrentPlayerChanged(m_blackTurn);
    return true;
}

bool GtpClient::undoMove() {
    if (!canUndo()) return false;
    if (!sendCommand("undo")) return false;
    if (!waitResponse()) return false;

    m_undoIndex--;
    m_blackTurn = !m_blackTurn;
    m_consecutivePasses = 0;

    emitUndoRedoState();
    if (callbacks.onBoardChanged) callbacks.onBoardChanged();
    if (callbacks.onCurrentPlayerChanged) callbacks.onCurrentPlayerChanged(m_blackTurn);
    return true;
}

bool GtpClient::redoMove() {
    if (!canRedo()) return false;

    // Replay from the beginning up to the redo point
    // GTP doesn't have redo, so we clear and replay
    const auto &entry = m_undoStack[m_undoIndex];
    if (!playMove(entry.move)) return false;
    return true;
}

// --- Queries ---

int GtpClient::moveCount() {
    if (!sendCommand("move_history")) return 0;
    std::string resp;
    if (!readResponse(resp)) return 0;
    // Count lines in the response (each line = one move)
    if (resp.empty()) return 0;
    int lines = 0;
    for (char c : resp) if (c == '\n') lines++;
    return lines;
}

std::vector<Stone> GtpClient::stones(bool black) {
    std::string cmd = "list_stones ";
    cmd += black ? "black" : "white";

    if (!sendCommand(cmd)) return {};
    std::string resp;
    if (!readResponse(resp)) return {};

    std::vector<Stone> result;
    auto tokens = splitTokens(resp);
    for (const auto &t : tokens) {
        Stone s = Stone::fromGtp(t);
        if (s.isValid() || t == "pass") result.push_back(s);
    }
    return result;
}

int GtpClient::captures(bool black) {
    std::string cmd = "captures ";
    cmd += black ? "black" : "white";

    if (!sendCommand(cmd)) return 0;
    std::string resp;
    if (!readResponse(resp)) return 0;
    try { return std::stoi(resp); } catch (...) { return 0; }
}

std::string GtpClient::finalScore() {
    if (!sendCommand("final_score")) return "?";
    std::string resp;
    readResponse(resp);
    return resp;
}

std::string GtpClient::estimatedScore() {
    if (!sendCommand("estimate_score")) return "?";
    std::string resp;
    readResponse(resp);
    return resp;
}

std::vector<Stone> GtpClient::deadStones() {
    std::string resp;
    if (!sendCommand("final_status_list dead")) return {};
    if (!readResponse(resp)) return {};

    std::vector<Stone> result;
    auto tokens = splitTokens(resp);
    for (const auto &t : tokens) {
        Stone s = Stone::fromGtp(t);
        if (s.isValid()) result.push_back(s);
    }
    return result;
}

std::vector<Stone> GtpClient::bestMoves(bool black) {
    std::string cmd = "top_moves_";
    cmd += black ? "black" : "white";

    if (!sendCommand(cmd)) return {};
    std::string resp;
    if (!readResponse(resp)) return {};

    std::vector<Stone> result;
    auto tokens = splitTokens(resp);
    for (const auto &t : tokens) {
        Stone s = Stone::fromGtp(t);
        if (s.isValid()) result.push_back(s);
    }
    return result;
}

std::vector<Stone> GtpClient::legalMoves(bool black) {
    std::string cmd = "all_legal ";
    cmd += black ? "black" : "white";

    if (!sendCommand(cmd)) return {};
    std::string resp;
    if (!readResponse(resp)) return {};

    std::vector<Stone> result;
    auto tokens = splitTokens(resp);
    for (const auto &t : tokens) {
        Stone s = Stone::fromGtp(t);
        if (s.isValid()) result.push_back(s);
    }
    return result;
}

std::vector<Stone> GtpClient::liberties(const Stone &stone) {
    if (!sendCommand("findlib " + stone.toGtp())) return {};
    std::string resp;
    if (!readResponse(resp)) return {};

    std::vector<Stone> result;
    auto tokens = splitTokens(resp);
    for (const auto &t : tokens) {
        Stone s = Stone::fromGtp(t);
        if (s.isValid()) result.push_back(s);
    }
    return result;
}

std::vector<Move> GtpClient::moveHistory() {
    std::vector<Move> result;
    for (const auto &entry : m_undoStack) {
        result.push_back(entry.move);
    }
    return result;
}

void GtpClient::emitUndoRedoState() {
    if (callbacks.onCanUndoChanged) callbacks.onCanUndoChanged(canUndo());
    if (callbacks.onCanRedoChanged) callbacks.onCanRedoChanged(canRedo());
}

std::string GtpClient::lastGeneratedMove() const {
    return m_lastGeneratedMove;
}

} // namespace gtp
