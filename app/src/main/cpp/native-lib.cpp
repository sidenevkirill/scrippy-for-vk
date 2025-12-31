#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_ru_lisdevs_messenger_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++ with 16KB pages";
    return env->NewStringUTF(hello.c_str());
}