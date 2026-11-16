/**
 * RegistersNative_portable.c
 *
 * Portable stub for non-x86_64 architectures (ARM, MIPS, RISC-V, etc.).
 * Provides the same JNI function signatures as RegistersNative_64.c but
 * uses __builtin_return_address() (GCC portable) instead of x86 inline
 * assembly (pushq/popq). Register reads return 0 since they are
 * architecture-specific — only the return address functions are meaningful
 * cross-platform.
 *
 * This file is selected automatically by the build system when compiling
 * for non-x86_64 targets.
 */

#include <stdio.h>
#include <stdint.h>

/* JNI header — may or may not exist for cross-compilation.
 * Use weak declarations if header is missing. */
#ifdef __has_include
#  if __has_include("NativeAssembly_64.h")
#    include "NativeAssembly_64.h"
#  else
#    include <jni.h>
#  endif
#else
#  include <jni.h>
#endif

/*
 * Return the instruction pointer / program counter.
 * Uses __builtin_return_address(0) as a portable approximation —
 * returns the caller's return address rather than the exact PC.
 */
long print_eip(void) {
    return (long)__builtin_extract_return_addr(__builtin_return_address(0));
}

/*
 * Return the return address at the specified callee depth.
 * __builtin_return_address(n) with n>0 is "unsafe" per GCC warnings
 * but functional on most platforms with frame pointers enabled.
 */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wframe-address"
long print_lr_at_callee(int callee) {
    long result = 0;
    if (callee == 0) {
        result = (long)__builtin_extract_return_addr(__builtin_return_address(0));
    } else if (callee == 1) {
        result = (long)__builtin_extract_return_addr(__builtin_return_address(1));
    } else if (callee == 2) {
        result = (long)__builtin_extract_return_addr(__builtin_return_address(2));
    }
    return result;
}
#pragma GCC diagnostic pop

/*
 * Register read stubs — return 0 on non-x86_64 platforms.
 * These registers (RAX, RBX, RCX, RDX) are x86-specific.
 * On ARM: the equivalent would be X0-X3 (argument registers).
 * On MIPS: $a0-$a3.
 * On RISC-V: a0-a3.
 * Implementing these per-arch is a future enhancement.
 */
long print_rax(void) { return 0; }
long print_rbx(void) { return 0; }
long print_rcx(void) { return 0; }
long print_rdx(void) { return 0; }

/* JNI exports — same signatures as the x86_64 version */
JNIEXPORT long JNICALL Java_NativeAssembly_print_eip(JNIEnv *env, jobject obj) {
    return print_eip();
}

JNIEXPORT long JNICALL Java_NativeAssembly_print_rax(JNIEnv *env, jobject obj) {
    return print_rax();
}

JNIEXPORT long JNICALL Java_NativeAssembly_print_rbx(JNIEnv *env, jobject obj) {
    return print_rbx();
}

JNIEXPORT long JNICALL Java_NativeAssembly_print_rcx(JNIEnv *env, jobject obj) {
    return print_rcx();
}

JNIEXPORT long JNICALL Java_NativeAssembly_print_rdx(JNIEnv *env, jobject obj) {
    return print_rdx();
}
