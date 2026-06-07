#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
extern int gnugo_main(int argc, char *argv[]);

/* Intercept exit()/abort() via -Wl,--wrap=exit -Wl,--wrap=abort.
   Without this, any call to exit() or abort() from within GNU Go
   would kill the entire Android app process instead of just the
   engine thread. */
void __wrap_exit(int status) {
    fflush(stdout);
    fflush(stderr);
    pthread_exit(NULL);
}
void __wrap_abort(void) {
    fflush(stdout);
    fflush(stderr);
    pthread_exit(NULL);
}

int gnugo_gtp_main(int argc, const char **argv, int stdinFd, int stdoutFd) {
    int savedStdin = dup(STDIN_FILENO);
    int savedStdout = dup(STDOUT_FILENO);
    int i;
    dup2(stdinFd, STDIN_FILENO);
    dup2(stdoutFd, STDOUT_FILENO);
    close(stdinFd); close(stdoutFd);
    /* stdout is now a pipe (not a TTY) — force unbuffered so GTP
       responses are written immediately instead of getting stuck. */
    setvbuf(stdout, NULL, _IONBF, 0);
    /* main() expects argv[0] as program name.
       GNU Go's main.c is compiled with -Dmain=gnugo_main to avoid
       conflicting with the bridge entry point. */
    char **cargv = malloc(sizeof(char*) * (argc + 2));
    cargv[0] = strdup("gnugo");
    for (i = 0; i < argc; i++) cargv[i + 1] = strdup(argv[i]);
    cargv[argc + 1] = NULL;
    int rc = gnugo_main(argc + 1, cargv);
    for (i = 0; i <= argc; i++) free(cargv[i]);
    free(cargv);
    dup2(savedStdin, STDIN_FILENO);
    dup2(savedStdout, STDOUT_FILENO);
    close(savedStdin); close(savedStdout);
    return rc;
}
