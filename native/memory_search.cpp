#include <jni.h>
#include <cmath>
#include <limits>
// Pure numeric nearest-neighbour scan. No Minecraft/JVM object access inside the loop.
// Data layout: x,y,z,goalCode,moveCode repeated per event.
extern "C" JNIEXPORT jint JNICALL
Java_org_nagineco_doppel_NativeMemorySearch_nearestIndex(
    JNIEnv* env, jclass, jdoubleArray data, jint count,
    jint goal, jdouble x, jdouble y, jdouble z, jdouble radiusSquared) {
    if (!data || count <= 0 || !std::isfinite(radiusSquared) || radiusSquared <= 0) return -1;
    jsize length = env->GetArrayLength(data);
    if (count > length / 5) return -1;
    jboolean copied = JNI_FALSE;
    const jdouble* a = env->GetDoubleArrayElements(data, &copied);
    if (!a) return -1;
    jint best = -1;
    double nearest = radiusSquared;
    for (jint i = 0; i < count; ++i) {
        const jdouble* p = a + i * 5;
        if (static_cast<int>(p[3]) != goal || static_cast<int>(p[4]) == 0) continue;
        double dx = x-p[0], dy = y-p[1], dz = z-p[2];
        double d = dx*dx + dy*dy + dz*dz;
        if (d < nearest) { nearest = d; best = i; }
    }
    env->ReleaseDoubleArrayElements(data, a, JNI_ABORT);
    return best;
}
