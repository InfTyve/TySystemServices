/*
 * pty.c —— 伪终端 JNI 桥
 *
 * 只做四件事：开 /dev/ptmx、fork 出子进程把 slave 端接到 stdin/stdout/stderr、
 * 同步窗口大小、回收子进程。终端状态机（VT 解析）在 Java 层，不在这里。
 *
 * JNI 符号与 com.termux.terminal.JNI 的 native 方法一一对应，
 * 库名 libtermux.so（Java 侧 System.loadLibrary("termux")）。
 */

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

#define LOG_TAG "TermuxPty"
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void set_window_size(int fd, int rows, int cols, int cell_width, int cell_height) {
    struct winsize size;
    memset(&size, 0, sizeof(size));
    size.ws_row = (unsigned short) rows;
    size.ws_col = (unsigned short) cols;
    size.ws_xpixel = (unsigned short) (cols * cell_width);
    size.ws_ypixel = (unsigned short) (rows * cell_height);
    ioctl(fd, TIOCSWINSZ, &size);
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_createSubprocess(JNIEnv *env, jclass clazz,
                                              jstring cmd, jstring cwd,
                                              jobjectArray args, jobjectArray envVars,
                                              jintArray processIdArray,
                                              jint rows, jint columns,
                                              jint cellWidth, jint cellHeight) {
    const char *cmd_utf = (*env)->GetStringUTFChars(env, cmd, NULL);
    const char *cwd_utf = (*env)->GetStringUTFChars(env, cwd, NULL);

    jsize argc = args != NULL ? (*env)->GetArrayLength(env, args) : 0;
    jsize envc = envVars != NULL ? (*env)->GetArrayLength(env, envVars) : 0;

    // argv[0] 用命令名本身，后面跟参数；最后一个位置留给 NULL
    char **argv = (char **) calloc((size_t) argc + 2, sizeof(char *));
    argv[0] = strdup(cmd_utf);
    for (jsize i = 0; i < argc; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *s = (*env)->GetStringUTFChars(env, js, NULL);
        argv[i + 1] = strdup(s);
        (*env)->ReleaseStringUTFChars(env, js, s);
        (*env)->DeleteLocalRef(env, js);
    }
    argv[argc + 1] = NULL;

    char **envp = (char **) calloc((size_t) envc + 1, sizeof(char *));
    for (jsize i = 0; i < envc; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
        const char *s = (*env)->GetStringUTFChars(env, js, NULL);
        envp[i] = strdup(s);
        (*env)->ReleaseStringUTFChars(env, js, s);
        (*env)->DeleteLocalRef(env, js);
    }
    envp[envc] = NULL;

    int master = -1, slave = -1;
    if (openpty(&master, &slave, NULL, NULL, NULL) == -1) {
        LOGE("openpty failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return -1;
    }

    set_window_size(slave, rows, columns, cellWidth, cellHeight);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        close(slave);
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);
        return -1;
    }

    if (pid == 0) {
        // 子进程：脱离父会话，把从端变成控制终端
        setsid();
        ioctl(slave, TIOCSCTTY, 0);
        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        close(master);
        if (slave > 2) close(slave);

        if (cwd_utf != NULL && cwd_utf[0] != '\0') {
            if (chdir(cwd_utf) != 0) {
                // 目录不可用时退回家目录，别直接失败
                chdir("/");
            }
        }

        if (envp[0] != NULL) {
            execvpe(cmd_utf, argv, envp);
        } else {
            execvp(cmd_utf, argv);
        }

        // exec 失败时把错误写进终端，用户能看到原因
        char buf[256];
        int n = snprintf(buf, sizeof(buf), "exec \"%s\" failed: %s\r\n", cmd_utf, strerror(errno));
        if (n > 0) write(STDERR_FILENO, buf, (size_t) n);
        _exit(127);
    }

    // 父进程只管主端
    close(slave);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_utf);

    if (processIdArray != NULL) {
        jint pid_value = (jint) pid;
        (*env)->SetIntArrayRegion(env, processIdArray, 0, 1, &pid_value);
    }

    for (jsize i = 0; i < argc + 1; i++) free(argv[i]);
    free(argv);
    for (jsize i = 0; i < envc; i++) free(envp[i]);
    free(envp);

    return master;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyWindowSize(JNIEnv *env, jclass clazz, jint fd,
                                              jint rows, jint cols,
                                              jint cellWidth, jint cellHeight) {
    (void) env;
    (void) clazz;
    set_window_size(fd, rows, cols, cellWidth, cellHeight);
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_setPtyUTF8Mode(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    struct termios tios;
    if (tcgetattr(fd, &tios) == 0) {
        tios.c_iflag |= IUTF8;
        tcsetattr(fd, TCSANOW, &tios);
    }
}

JNIEXPORT jint JNICALL
Java_com_termux_terminal_JNI_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status;
    // EINTR 后要继续等，否则会被信号打断而提前返回
    while (waitpid((pid_t) pid, &status, 0) == -1) {
        if (errno != EINTR) return -1;
    }
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return -WTERMSIG(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_termux_terminal_JNI_close(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    close(fd);
}
