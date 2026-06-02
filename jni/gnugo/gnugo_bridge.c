#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
extern int main(int argc, char *argv[]);

/* Redirect exit()/abort() to thread exit. Calling these in a dlopen'd
   shared library would kill the entire Android process including the UI. */
void gnugo_exit(int status) {
    fflush(stdout);
    fflush(stderr);
    pthread_exit(NULL);
}
void gnugo_abort(void) {
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
    /* main() expects argv[0] as program name */
    char **cargv = malloc(sizeof(char*) * (argc + 2));
    cargv[0] = strdup("gnugo");
    for (i = 0; i < argc; i++) cargv[i + 1] = strdup(argv[i]);
    cargv[argc + 1] = NULL;
    int rc = main(argc + 1, cargv);
    for (i = 0; i <= argc; i++) free(cargv[i]);
    free(cargv);
    dup2(savedStdin, STDIN_FILENO);
    dup2(savedStdout, STDOUT_FILENO);
    close(savedStdin); close(savedStdout);
    return rc;
}
