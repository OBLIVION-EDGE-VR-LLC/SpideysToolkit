"""
SConstruct — CNO Package Build System
=====================================
SCons-based build configuration for compiling architecture-specific
shared objects and packaging them into deployable JAR files.

Usage:
    scons                       # Build for host architecture (debug)
    scons arch=x86_64           # Build for x86-64
    scons arch=arm              # Cross-compile for ARM64
    scons arch=mips             # Cross-compile for MIPS
    scons mode=release          # Release build (optimized, stripped)
    scons package               # Assemble multi-arch JAR
    scons -c                    # Clean build artifacts

This build system is part of the Spidey's Toolkit / CNO Package
Development platform. It compiles C source files containing JNI native
method implementations and inline assembly into shared objects (.so)
that are loaded by the Java agent framework at runtime.

No AFL dependency. Trampoline-based function interception only.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import os
import platform
import subprocess
import sys

# ---------------------------------------------------------------------------
# Build Variables
# ---------------------------------------------------------------------------

vars = Variables()
vars.Add(EnumVariable('arch', 'Target architecture',
                       platform.machine(),
                       allowed_values=('x86_64', 'arm', 'mips', 'riscv64', 'win_dll', 'darwin_dylib')))
vars.Add(EnumVariable('mode', 'Build mode',
                       'debug',
                       allowed_values=('debug', 'release')))

# ---------------------------------------------------------------------------
# Detect JAVA_HOME
# ---------------------------------------------------------------------------

java_home = os.environ.get('JAVA_HOME', '')
if not java_home:
    # Try to detect from javac location
    try:
        javac_path = subprocess.check_output(['which', 'javac'],
                                              stderr=subprocess.DEVNULL).decode().strip()
        java_home = os.path.dirname(os.path.dirname(os.path.realpath(javac_path)))
    except (subprocess.CalledProcessError, FileNotFoundError):
        print("ERROR: JAVA_HOME not set and javac not found in PATH.")
        print("       Set JAVA_HOME to your JDK 17+ installation directory.")
        Exit(1)

jni_include = os.path.join(java_home, 'include')
jni_platform_include = os.path.join(java_home, 'include', 'linux')

if not os.path.isfile(os.path.join(jni_include, 'jni.h')):
    print("ERROR: jni.h not found at %s" % jni_include)
    print("       Verify that JAVA_HOME points to a valid JDK installation.")
    Exit(1)

# ---------------------------------------------------------------------------
# Toolchain Configuration
# ---------------------------------------------------------------------------

TOOLCHAINS = {
    'x86_64': {
        'CC': 'gcc',
        'arch_flags': ['-m64', '-fPIC'],
        'march': [],
        'link_flags': ['-shared'],
        'link_libs': ['-lc', '-ldl'],         # glibc + dynamic loader
        'sysroot': None,                       # native toolchain
        'pic': True,
        'output_ext': '.so',
        'platform_defs': ['-D_GNU_SOURCE', '-D__linux__'],
        'stdio_header': '<stdio.h>',           # glibc stdio
    },
    'arm': {
        'CC': 'aarch64-linux-gnu-gcc',
        'arch_flags': ['-fPIC'],
        'march': ['-march=armv8-a'],
        'link_flags': ['-shared'],
        'link_libs': ['-lc'],                  # aarch64 glibc
        'sysroot': '/usr/aarch64-linux-gnu',   # cross sysroot
        'pic': True,
        'output_ext': '.so',
        'platform_defs': ['-D_GNU_SOURCE', '-D__linux__', '-D__aarch64__'],
        'stdio_header': '<stdio.h>',           # glibc stdio (cross)
    },
    'mips': {
        'CC': 'mips-linux-gnu-gcc',
        'arch_flags': ['-fPIC'],
        'march': ['-mips32r2'],
        'link_flags': ['-shared'],
        'link_libs': ['-lc'],                  # mips glibc
        'sysroot': '/usr/mips-linux-gnu',      # cross sysroot
        'pic': True,
        'output_ext': '.so',
        'platform_defs': ['-D_GNU_SOURCE', '-D__linux__', '-D__mips__'],
        'stdio_header': '<stdio.h>',           # glibc stdio (cross)
    },
    'riscv64': {
        'CC': 'riscv64-linux-gnu-gcc',
        'arch_flags': ['-fPIC'],
        'march': ['-march=rv64gc', '-mabi=lp64d'],
        'link_flags': ['-shared'],
        'link_libs': ['-lc'],                  # RISC-V glibc
        'sysroot': '/usr/riscv64-linux-gnu',   # cross sysroot
        'pic': True,
        'output_ext': '.so',
        'platform_defs': ['-D_GNU_SOURCE', '-D__linux__', '-D__riscv',
                          '-D__riscv_xlen=64'],
        'stdio_header': '<stdio.h>',           # glibc stdio (cross)
    },
    'win_dll': {
        'CC': 'x86_64-w64-mingw32-gcc',
        'arch_flags': [],                       # No -fPIC on Windows (all code is relocatable)
        'march': [],
        'link_flags': ['-shared', '-Wl,--out-implib,libshadow.a',
                       '-Wl,--enable-stdcall-fixup'],
        'link_libs': ['-lkernel32', '-lws2_32', '-lmsvcrt'],  # Windows CRT + Winsock
        'sysroot': '/usr/x86_64-w64-mingw32',  # MinGW sysroot
        'pic': False,                           # PE format is inherently relocatable
        'output_ext': '.dll',
        'platform_defs': ['-DWIN32', '-D_WIN32', '-D__MINGW64__',
                          '-D_WIN32_WINNT=0x0601'],
        'stdio_header': '<stdio.h>',           # MSVCRT stdio
    },
    'darwin_dylib': {
        'CC': 'gcc',                            # or 'clang' on macOS
        'arch_flags': ['-fPIC'],
        'march': [],
        'link_flags': ['-dynamiclib',
                       '-install_name', '@rpath/libshadow.dylib',
                       '-compatibility_version', '1.0',
                       '-current_version', '1.0'],
        'link_libs': ['-lSystem'],              # macOS libSystem (libc + libm + etc.)
        'sysroot': None,                        # native or Xcode SDK
        'pic': True,
        'output_ext': '.dylib',
        'platform_defs': ['-D__APPLE__', '-D__MACH__'],
        'stdio_header': '<stdio.h>',           # libSystem stdio
    },
}

# ---------------------------------------------------------------------------
# Source Files
# ---------------------------------------------------------------------------

NATIVE_SRC_DIR = os.path.join(
    'my-app', 'src', 'main', 'java', 'com', 'utece', 'student',
    'llpdetection', 'instrumentation', 'inlineassembly'
)

IMPLANT_SRC_DIR = os.path.join(
    'my-app', 'src', 'main', 'java', 'com', 'utece', 'student',
    'reversecshell', 'instrumentation', 'reverseshell'
)

# Trampoline / register capture sources — x86_64 (inline asm)
TRAMPOLINE_SOURCES_X86_64 = [
    os.path.join(NATIVE_SRC_DIR, 'RegistersNative_64.c'),
    os.path.join(NATIVE_SRC_DIR, 'TrampolineExample.c'),
    os.path.join(NATIVE_SRC_DIR, 'RIPExample.c'),
]

# Trampoline sources — portable (ARM, MIPS, RISC-V, etc.)
TRAMPOLINE_SOURCES_PORTABLE = [
    os.path.join(NATIVE_SRC_DIR, 'RegistersNative_portable.c'),
    os.path.join(NATIVE_SRC_DIR, 'TrampolineExample.c'),
    os.path.join(NATIVE_SRC_DIR, 'RIPExample.c'),
]

# C2 channel sources
IMPLANT_SOURCES = [
    os.path.join(IMPLANT_SRC_DIR, 'ReverseShell.c'),
]

# ---------------------------------------------------------------------------
# Build Environment Factory
# ---------------------------------------------------------------------------

def make_env(arch, mode):
    """Create a SCons Environment for the given architecture and mode."""
    tc = TOOLCHAINS[arch]

    # Check that the cross-compiler is available
    if arch != platform.machine():
        compiler = tc['CC']
        if not WhereIs(compiler):
            print("ERROR: Cross-compiler '%s' not found for arch '%s'." % (compiler, arch))
            print("       Install with: sudo apt install %s" % (
                'gcc-aarch64-linux-gnu' if arch == 'arm' else 'gcc-mips-linux-gnu'))
            Exit(1)

    # Common flags — PIC is per-platform (Windows PE is always relocatable)
    common_flags = ['-Wall', '-Wextra', '-Wno-unused-parameter']
    if tc.get('pic', True):
        common_flags.append('-fPIC')

    if mode == 'debug':
        common_flags += ['-g', '-O0', '-DDEBUG']
    else:
        common_flags += ['-O2', '-s', '-DNDEBUG']

    # Platform-specific preprocessor defines
    platform_defs = tc.get('platform_defs', [])

    link_flags = tc.get('link_flags', ['-shared'])
    link_libs = tc.get('link_libs', [])

    # Include paths — add sysroot if cross-compiling
    include_paths = [jni_include, jni_platform_include, NATIVE_SRC_DIR, IMPLANT_SRC_DIR]
    sysroot = tc.get('sysroot')
    if sysroot and os.path.isdir(sysroot):
        include_paths.append(os.path.join(sysroot, 'include'))

    env = Environment(
        variables=vars,
        CC=tc['CC'],
        CCFLAGS=common_flags + tc['arch_flags'] + tc['march'] + platform_defs,
        CPPPATH=include_paths,
        SHLIBPREFIX='lib',
        LINKFLAGS=link_flags,
        LIBS=link_libs,
    )

    # Set sysroot for cross-compilation
    if sysroot and os.path.isdir(sysroot):
        env.Append(CCFLAGS=['--sysroot=%s' % sysroot])
        env.Append(LINKFLAGS=['--sysroot=%s' % sysroot])

    return env


# ---------------------------------------------------------------------------
# Build Targets
# ---------------------------------------------------------------------------

arch = ARGUMENTS.get('arch', platform.machine())
mode = ARGUMENTS.get('mode', 'debug')

# Normalize architecture variants
if arch in ('amd64', 'AMD64'):
    arch = 'x86_64'
elif arch in ('aarch64', 'ARM64'):
    arch = 'arm'
elif arch in ('riscv', 'rv64'):
    arch = 'riscv64'
elif arch in ('dll', 'windows', 'win64'):
    arch = 'win_dll'
elif arch in ('dylib', 'macos', 'darwin'):
    arch = 'darwin_dylib'

build_dir = os.path.join('build', arch)
env = make_env(arch, mode)

# --- Select arch-appropriate sources ---
if arch == 'x86_64':
    TRAMPOLINE_SOURCES = TRAMPOLINE_SOURCES_X86_64
else:
    TRAMPOLINE_SOURCES = TRAMPOLINE_SOURCES_PORTABLE

# --- Trampoline shared object ---
trampoline_sources_exist = all(os.path.isfile(s) for s in TRAMPOLINE_SOURCES)

if trampoline_sources_exist:
    trampoline_objs = []
    for src in TRAMPOLINE_SOURCES:
        obj = env.SharedObject(
            target=os.path.join(build_dir, os.path.splitext(os.path.basename(src))[0]),
            source=src
        )
        trampoline_objs.append(obj)

    output_ext = TOOLCHAINS.get(arch, {}).get('output_ext', '.so')
    if output_ext == '.dll':
        lib_name = os.path.join(build_dir, 'trampoline.dll')
        libtrampoline = env.Command(lib_name, trampoline_objs,
            '$CC $LINKFLAGS -o $TARGET $SOURCES')
    elif output_ext == '.dylib':
        lib_name = os.path.join(build_dir, 'libtrampoline.dylib')
        libtrampoline = env.Command(lib_name, trampoline_objs,
            '$CC $LINKFLAGS -o $TARGET $SOURCES')
    else:
        libtrampoline = env.SharedLibrary(
            target=os.path.join(build_dir, 'trampoline'),
            source=trampoline_objs
        )
    Default(libtrampoline)
    Alias('trampoline', libtrampoline)
else:
    print("WARNING: Trampoline source files not found in %s" % NATIVE_SRC_DIR)
    print("         Skipping trampoline build.")
    libtrampoline = None

# --- Implant shared object ---
implant_sources_exist = all(os.path.isfile(s) for s in IMPLANT_SOURCES)

if implant_sources_exist:
    implant_objs = []
    for src in IMPLANT_SOURCES:
        obj = env.SharedObject(
            target=os.path.join(build_dir, os.path.splitext(os.path.basename(src))[0]),
            source=src
        )
        implant_objs.append(obj)

    libimplant = env.SharedLibrary(
        target=os.path.join(build_dir, 'implant'),
        source=implant_objs
    )
    Default(libimplant)
    Alias('implant', libimplant)
else:
    print("NOTE: Implant source files not found in %s" % IMPLANT_SRC_DIR)
    print("      Skipping implant build. This is normal if C2 is not needed.")
    libimplant = None


# ---------------------------------------------------------------------------
# Package Target — Assemble multi-arch JAR
# ---------------------------------------------------------------------------

def package_jar(target, source, env):
    """Assemble all built shared objects and Java classes into a JAR."""
    import shutil
    import tempfile

    staging = tempfile.mkdtemp(prefix='spideys-jar-')
    meta_inf = os.path.join(staging, 'META-INF')
    os.makedirs(meta_inf)

    # Write manifest
    with open(os.path.join(meta_inf, 'MANIFEST.MF'), 'w') as f:
        f.write("Manifest-Version: 1.0\n")
        f.write("Premain-Class: com.utece.student.llpdetection.agents.JavaAgent\n")
        f.write("Can-Retransform-Classes: true\n")
        f.write("\n")

    # Copy Java classes
    classes_dir = os.path.join('my-app', 'target', 'classes')
    if os.path.isdir(classes_dir):
        com_dir = os.path.join(classes_dir, 'com')
        if os.path.isdir(com_dir):
            shutil.copytree(com_dir, os.path.join(staging, 'com'))

    # Copy native libraries from all architecture builds
    for arch_dir in os.listdir('build'):
        arch_path = os.path.join('build', arch_dir)
        if not os.path.isdir(arch_path):
            continue
        lib_dest = os.path.join(staging, 'lib', arch_dir)
        os.makedirs(lib_dest, exist_ok=True)
        for f in os.listdir(arch_path):
            if f.endswith('.so') or f.endswith('.dylib'):
                shutil.copy2(os.path.join(arch_path, f), lib_dest)

    # Create JAR
    jar_path = str(target[0])
    subprocess.check_call([
        'jar', 'cmf',
        os.path.join(meta_inf, 'MANIFEST.MF'),
        jar_path,
        '-C', staging, '.'
    ])

    # Cleanup
    shutil.rmtree(staging)
    print("=== Package created: %s ===" % jar_path)
    return 0

jar_target = env.Command(
    'spideys-agent.jar',
    [],
    package_jar
)
Alias('package', jar_target)
AlwaysBuild(jar_target)


# ---------------------------------------------------------------------------
# Deploy Target — Install to local Maven repository
# ---------------------------------------------------------------------------

def deploy_maven(target, source, env):
    """Install the shared object into the local Maven repository."""
    arch_val = ARGUMENTS.get('arch', platform.machine())
    so_path = os.path.join('build', arch_val, 'libtrampoline.so')
    if not os.path.isfile(so_path):
        print("ERROR: %s not found. Build first with: scons arch=%s" % (so_path, arch_val))
        return 1

    cmd = [
        'mvn', 'install:install-file',
        '-Dfile=%s' % so_path,
        '-DgroupId=com.utece.student.llpdetection',
        '-DartifactId=libtrampoline',
        '-Dversion=1.0',
        '-Dpackaging=so',
        '-Dclassifier=%s' % arch_val,
    ]
    print("Installing %s to local Maven repository..." % so_path)
    subprocess.check_call(cmd)
    return 0

deploy_target = env.Command(
    'deploy_marker',
    [],
    deploy_maven
)
Alias('deploy', deploy_target)
AlwaysBuild(deploy_target)


# ---------------------------------------------------------------------------
# Help Text
# ---------------------------------------------------------------------------

Help("""
Spidey's Toolkit — CNO Package Build System
===================================================

Targets:
    scons                  Build trampoline + implant for host arch (default)
    scons trampoline       Build trampoline shared object only
    scons implant          Build implant shared object only
    scons package          Assemble multi-arch JAR
    scons deploy           Install .so to local Maven repository
    scons -c               Clean all build artifacts

Options:
    arch=x86_64|arm|mips|riscv64|win_dll|darwin_dylib
                           Target architecture (default: host)
    mode=debug|release     Build mode (default: debug)

Examples:
    scons arch=x86_64 mode=debug       Debug build for x86-64
    scons arch=arm mode=release        Release build for ARM64
    scons arch=win_dll                 DLL sideloading build (MinGW)
    scons arch=darwin_dylib            Dylib shadowing build (macOS)
    scons arch=x86_64 && scons arch=arm && scons package
                                       Build multi-arch JAR

DLL Sideloading / Dylib Shadowing:
    scons arch=win_dll    -> build/win_dll/trampoline.dll
    scons arch=darwin_dylib -> build/darwin_dylib/libtrampoline.dylib

No AFL dependency. Trampoline-based deterministic interception only.
""")
