#include <jni.h>
#include <string>

extern "C"
jstring
Java_edu_psu_armstrong1_gridmeasure_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
