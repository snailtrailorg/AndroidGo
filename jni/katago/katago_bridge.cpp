/*
 * katago_bridge.cpp — dlopen entry point for KataGo on Android.
 *
 * The gtp_client.cpp JNI code dlopen's libkatago_engine.so and looks for
 * "katago_gtp_main".  This bridge provides that symbol.
 *
 * It redirects stdin/stdout to the pipe fds supplied by GtpClient, calls
 * KataGo's built-in GTP loop (MainCmds::gtp), and intercepts exit()/abort()
 * so the engine thread cleanly returns instead of killing the whole app
 * process.
 */

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <unistd.h>
#include <pthread.h>
#include <android/log.h>
#include "../cpp/neuralnet/nninterface.h"

// Forward declaration of KataGo's GTP entry point.
// Defined in command/gtp.cpp.
namespace MainCmds {
    int gtp(const std::vector<std::string>& args);
}

#define BRIDGE_LOG(...) __android_log_print(ANDROID_LOG_ERROR, "KataGoBridge", __VA_ARGS__)

/*
 * __wrap_exit / __wrap_abort — linked via -Wl,--wrap=exit -Wl,--wrap=abort.
 *
 * These intercept any call to exit() or abort() from within KataGo and
 * redirect it to pthread_exit(), so only the engine thread terminates —
 * not the entire Android app process.
 */
extern "C" {

void __wrap_exit(int status) {
    BRIDGE_LOG("__wrap_exit(%d) — calling pthread_exit", status);
    fflush(stdout);
    fflush(stderr);
    pthread_exit(nullptr);
}

void __wrap_abort(void) {
    BRIDGE_LOG("__wrap_abort() — calling pthread_exit");
    fflush(stdout);
    fflush(stderr);
    pthread_exit(nullptr);
}

/*
 * Entry point for dlopen + pthread.
 *
 * argc / argv : CLI arguments (e.g. "gtp", "-config", "...", "-model", "...")
 * stdinFd     : read end of a pipe — KataGo's stdin will be dup2'd here
 * stdoutFd    : write end of a pipe — KataGo's stdout will be dup2'd here
 */
__attribute__((visibility("default")))
int katago_gtp_main(int argc, const char** argv,
                    int stdinFd, int stdoutFd) {
    BRIDGE_LOG("katago_gtp_main starting, argc=%d", argc);

    // Save original fds so we can restore them on return.
    int savedStdin = dup(STDIN_FILENO);
    int savedStdout = dup(STDOUT_FILENO);

    // Redirect stdin/stdout to the pipes GtpClient created.
    dup2(stdinFd, STDIN_FILENO);
    dup2(stdoutFd, STDOUT_FILENO);
    close(stdinFd);
    close(stdoutFd);

    // Build argument vector for MainCmds::gtp.
    std::vector<std::string> args;
    for (int i = 0; i < argc; i++)
        args.push_back(std::string(argv[i]));

    // Initialize the neural net backend BEFORE entering GTP mode.
    // This can take minutes on first run (OpenCL kernel tuning), and GTP
    // commands sent during init would time out.
    BRIDGE_LOG("initializing neural net backend...");
    NeuralNet::globalInitialize();
    BRIDGE_LOG("neural net initialized");

    BRIDGE_LOG("calling MainCmds::gtp with %d args", (int)args.size());
    int rc = MainCmds::gtp(args);
    BRIDGE_LOG("MainCmds::gtp returned %d", rc);

    // Restore original stdin/stdout.
    dup2(savedStdin, STDIN_FILENO);
    dup2(savedStdout, STDOUT_FILENO);
    close(savedStdin);
    close(savedStdout);

    return rc;
}

}  // extern "C"
