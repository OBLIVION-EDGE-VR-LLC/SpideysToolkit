"""
BuildConfig — Centralized build configuration.

Detects JAVA_HOME, source directories, and compiler include paths.
Provides a single source of truth for all file system paths used
by the build pipeline.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import os
import subprocess


class BuildConfig:
    """Centralized build configuration for the CNO package pipeline."""

    def __init__(self, project_root: str = '.'):
        self.project_root = os.path.abspath(project_root)
        self.build_dir = os.path.join(self.project_root, 'build')
        self.source_dir = os.path.join(
            self.project_root, 'my-app', 'src', 'main', 'java',
            'com', 'utece', 'student', 'llpdetection',
            'instrumentation', 'inlineassembly'
        )
        self.implant_dir = os.path.join(
            self.project_root, 'my-app', 'src', 'main', 'java',
            'com', 'utece', 'student', 'reversecshell',
            'instrumentation', 'reverseshell'
        )
        self.classes_dir = os.path.join(
            self.project_root, 'my-app', 'target', 'classes'
        )
        self.java_home = self._detect_java_home()
        self.jni_include = os.path.join(self.java_home, 'include')
        self.jni_platform = os.path.join(self.java_home, 'include', 'linux')

        # x86-64 specific sources (inline asm with pushq/popq)
        self.trampoline_sources_x86_64 = [
            os.path.join(self.source_dir, f) for f in [
                'RegistersNative_64.c',
                'TrampolineExample.c',
                'RIPExample.c',
            ]
        ]
        # Portable sources for non-x86 architectures (ARM, MIPS, RISC-V, etc.)
        self.trampoline_sources_portable = [
            os.path.join(self.source_dir, f) for f in [
                'RegistersNative_portable.c',
                'TrampolineExample.c',
                'RIPExample.c',
            ]
        ]
        # Default (selected by build model based on target arch)
        self.trampoline_sources = self.trampoline_sources_x86_64
        # POSIX implant (Linux, macOS, ARM, MIPS, RISC-V)
        self.implant_sources_posix = [
            os.path.join(self.implant_dir, 'ReverseShell.c'),
        ]
        # Windows implant (Winsock2 API, MinGW cross-compile)
        self.implant_sources_win = [
            os.path.join(self.implant_dir, 'ReverseShell_win.c'),
        ]
        # Default
        self.implant_sources = self.implant_sources_posix

    def _detect_java_home(self) -> str:
        """Detect JAVA_HOME from environment or javac location."""
        java_home = os.environ.get('JAVA_HOME', '')
        if java_home and os.path.isdir(java_home):
            return java_home
        try:
            javac = subprocess.check_output(
                ['which', 'javac'], stderr=subprocess.DEVNULL
            ).decode().strip()
            return os.path.dirname(os.path.dirname(os.path.realpath(javac)))
        except (subprocess.CalledProcessError, FileNotFoundError):
            return '/usr/lib/jvm/default-java'

    def validate(self) -> list:
        """Return a list of validation warnings."""
        warnings = []
        if not os.path.isfile(os.path.join(self.jni_include, 'jni.h')):
            warnings.append("jni.h not found at %s" % self.jni_include)
        if not any(os.path.isfile(s) for s in self.trampoline_sources):
            warnings.append("No trampoline source files found")
        return warnings
