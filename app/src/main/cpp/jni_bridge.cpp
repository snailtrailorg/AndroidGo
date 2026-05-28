#include <jni.h>
#include <string>
#include <memory>
#include "gtp_client.h"

// JNI bridge for the GTP client
// Each GtpClient* is stored as a jlong (opaque pointer) on the Kotlin side

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeCreate(
    JNIEnv *env, jobject /* this */) {
    auto *client = new gtp::GtpClient();
    return reinterpret_cast<jlong>(client);
}

JNIEXPORT void JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeDestroy(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (client) {
        client->stop();
        delete client;
    }
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeStart(
    JNIEnv *env, jobject /* this */, jlong ptr, jstring command) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;

    const char *cmd = env->GetStringUTFChars(command, nullptr);
    bool result = client->start(cmd);
    env->ReleaseStringUTFChars(command, cmd);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeAttachToProcess(
    JNIEnv *env, jobject /* this */, jlong ptr, jint pid, jint stdinFd, jint stdoutFd) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;
    return client->attachToProcess(pid, stdinFd, stdoutFd) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeStop(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (client) client->stop();
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeIsRunning(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    return (client && client->isRunning()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeInit(
    JNIEnv *env, jobject /* this */, jlong ptr, jint boardSize, jfloat komi) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;
    if (!client->setBoardSize(boardSize)) return JNI_FALSE;
    if (!client->setKomi(komi)) return JNI_FALSE;
    return client->init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativePlayMove(
    JNIEnv *env, jobject /* this */, jlong ptr,
    jboolean black, jint row, jint col) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;

    gtp::Move move;
    move.black = black;

    // Convert row/col to GTP coordinate
    // row 0 = bottom (row 1 in GTP), col 0 = A
    char gtpCol = 'A' + col;
    if (gtpCol >= 'I') gtpCol++; // skip I
    int gtpRow = client->boardSize() - row;

    if (row < 0) {
        move.type = gtp::Move::Pass;
    } else {
        move.type = gtp::Move::Stone;
        move.stone.col = gtpCol;
        move.stone.row = gtpRow;
    }
    return client->playMove(move) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativePass(
    JNIEnv *env, jobject /* this */, jlong ptr, jboolean black) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;

    gtp::Move move;
    move.black = black;
    move.type = gtp::Move::Pass;
    return client->playMove(move) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGenerateMove(
    JNIEnv *env, jobject /* this */, jlong ptr, jboolean black) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;
    return client->generateMove(black) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeUndo(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_FALSE;
    return client->undoMove() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetEngineName(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");
    return env->NewStringUTF(client->engineName().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetEngineVersion(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");
    return env->NewStringUTF(client->engineVersion().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeFinalScore(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");
    return env->NewStringUTF(client->finalScore().c_str());
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeEstimatedScore(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");
    return env->NewStringUTF(client->estimatedScore().c_str());
}

JNIEXPORT jint JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetBoardSize(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return 19;
    return client->boardSize();
}

JNIEXPORT jboolean JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeIsBlackTurn(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return JNI_TRUE;
    return client->isBlackTurn() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetConsecutivePasses(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return 0;
    return client->consecutivePasses();
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetLastGeneratedMove(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");
    return env->NewStringUTF(client->lastGeneratedMove().c_str());
}

JNIEXPORT void JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeInterrupt(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (client) client->interrupt();
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeDeadStones(
    JNIEnv *env, jobject /* this */, jlong ptr) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");

    auto stones = client->deadStones();
    std::string result;
    for (const auto &s : stones) {
        if (!result.empty()) result += " ";
        result += s.toGtp();
    }
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_org_snailtrail_androidgo_engine_GtpEngine_nativeGetStones(
    JNIEnv *env, jobject /* this */, jlong ptr, jboolean black) {
    auto *client = reinterpret_cast<gtp::GtpClient *>(ptr);
    if (!client) return env->NewStringUTF("");

    auto stones = client->stones(black);
    std::string result;
    for (const auto &s : stones) {
        if (!result.empty()) result += " ";
        result += s.toGtp();
    }
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
