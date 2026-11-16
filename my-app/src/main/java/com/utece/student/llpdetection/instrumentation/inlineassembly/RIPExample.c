#include <jni.h>

JNIEXPORT jlong JNICALL Java_RIPExample_getRIP(JNIEnv *env, jobject obj, jint callee) {
    jlong rip;
    /*
    // Inline assembly to read the RIP register
    asm("movq $0, %0\n\t"
        "call 1f\n\t"
        "1: popq %0"
        : "=r" (rip));

    return rip;
    */

    long result;

    if(callee == 0){
        result = ((long)__builtin_extract_return_addr (__builtin_return_address(0)));
    }
    else if(callee == 1){
        result = ((long)__builtin_extract_return_addr (__builtin_return_address(1)));
    }
    else if(callee == 2){
        result = ((long)__builtin_extract_return_addr (__builtin_return_address(2)));
    }

    return result;


}

