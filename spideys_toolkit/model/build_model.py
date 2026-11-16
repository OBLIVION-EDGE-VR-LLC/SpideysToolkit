"""
BuildModel — Core model in the MVC architecture.

Owns all build state, executes build logic, and notifies registered
observers (Observer pattern, GoF) of state changes. The model has
no knowledge of the view or controller — it communicates exclusively
through BuildEvent notifications and BuildResult return values.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import os
import platform
import shutil
import subprocess
import tempfile
import time
from typing import Dict, List, Optional

from spideys_toolkit.model.config import BuildConfig
from spideys_toolkit.model.artifacts import (
    ArtifactType, BuildArtifact, BuildEvent, BuildEventType, BuildResult
)
from spideys_toolkit.model.toolchain import ToolchainFactory


class BuildObserver:
    """Observer interface for build events (GoF Observer Pattern)."""

    def on_build_event(self, event: BuildEvent) -> None:
        pass


class BuildModel:
    """
    Model in MVC — owns build state, executes build operations,
    and notifies observers of state changes.

    All build logic is centralized here. The controller delegates
    to model methods; the view subscribes to model events.
    """

    def __init__(self, config: BuildConfig):
        self.config = config
        self.artifacts: List[BuildArtifact] = []
        self._observers: List[BuildObserver] = []
        self._current_arch = platform.machine()
        self._current_mode = 'debug'

    # --- Observer management ---

    def add_observer(self, observer: BuildObserver) -> None:
        self._observers.append(observer)

    def remove_observer(self, observer: BuildObserver) -> None:
        self._observers.remove(observer)

    def _notify(self, event_type: BuildEventType, message: str,
                artifact: Optional[BuildArtifact] = None) -> None:
        event = BuildEvent(event_type, message, artifact)
        for obs in self._observers:
            obs.on_build_event(event)

    # --- Properties ---

    @property
    def current_arch(self) -> str:
        return self._current_arch

    @current_arch.setter
    def current_arch(self, value: str) -> None:
        self._current_arch = value

    @property
    def current_mode(self) -> str:
        return self._current_mode

    @current_mode.setter
    def current_mode(self, value: str) -> None:
        self._current_mode = value

    # --- Build operations ---

    def compile(self, arch: str = None, mode: str = None) -> BuildResult:
        """Compile native shared objects for the specified architecture."""
        arch = arch or self._current_arch
        mode = mode or self._current_mode
        start = time.time()

        try:
            toolchain = ToolchainFactory.create(arch)
        except ValueError as e:
            self._notify(BuildEventType.ERROR, str(e))
            return BuildResult(False, str(e))

        arch_name = toolchain.get_arch_name()
        out_dir = os.path.join(self.config.build_dir, arch_name)
        os.makedirs(out_dir, exist_ok=True)

        compiler = toolchain.get_compiler()
        if not shutil.which(compiler):
            msg = "Compiler '%s' not found for arch '%s'" % (compiler, arch_name)
            self._notify(BuildEventType.COMPILE_FAILURE, msg)
            return BuildResult(False, msg)

        self._notify(BuildEventType.COMPILE_START,
                     "Compiling for %s (%s mode)" % (arch_name, mode))

        # Select architecture-appropriate sources:
        # x86_64 and win_dll use x86 inline assembly; everything else uses portable stubs
        if arch_name == 'x86_64':
            source_list = self.config.trampoline_sources_x86_64
        else:
            source_list = self.config.trampoline_sources_portable

        # Find available source files
        sources = [s for s in source_list if os.path.isfile(s)]
        if not sources:
            msg = "No source files found in %s" % self.config.source_dir
            self._notify(BuildEventType.COMPILE_FAILURE, msg)
            return BuildResult(False, msg)

        # Compile each source to object file
        obj_files = []
        for src in sources:
            basename = os.path.splitext(os.path.basename(src))[0]
            obj_path = os.path.join(out_dir, basename + '.o')
            cmd = [compiler, '-c'] + toolchain.get_flags(mode) + [
                '-I', self.config.jni_include,
                '-I', self.config.jni_platform,
                '-I', self.config.source_dir,
                '-o', obj_path,
                src,
            ]

            self._notify(BuildEventType.INFO,
                         "  CC %s" % os.path.basename(src))

            try:
                subprocess.check_output(cmd, stderr=subprocess.STDOUT)
                obj_files.append(obj_path)
            except subprocess.CalledProcessError as e:
                msg = "Compilation failed: %s\n%s" % (
                    os.path.basename(src), e.output.decode())
                self._notify(BuildEventType.COMPILE_FAILURE, msg)
                return BuildResult(False, msg)

        # Link into shared object
        so_path = os.path.join(out_dir, 'libtrampoline.so')
        link_cmd = [compiler, '-shared', '-o', so_path] + obj_files

        self._notify(BuildEventType.LINK_START, "  LD libtrampoline.so")

        try:
            subprocess.check_output(link_cmd, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as e:
            msg = "Linking failed: %s" % e.output.decode()
            self._notify(BuildEventType.LINK_FAILURE, msg)
            return BuildResult(False, msg)

        artifact = BuildArtifact(so_path, arch_name, ArtifactType.SHARED_OBJECT)
        self.artifacts.append(artifact)

        elapsed = time.time() - start
        self._notify(BuildEventType.COMPILE_SUCCESS,
                     "Built libtrampoline.so for %s (%.2fs)" % (arch_name, elapsed))

        return BuildResult(True, "Compilation successful", [artifact], elapsed)

    def compile_implant(self, arch: str = None, mode: str = None) -> BuildResult:
        """Compile the implant (reverse shell) shared object for the target arch."""
        arch = arch or self._current_arch
        mode = mode or self._current_mode
        start = time.time()

        try:
            toolchain = ToolchainFactory.create(arch)
        except ValueError as e:
            self._notify(BuildEventType.ERROR, str(e))
            return BuildResult(False, str(e))

        arch_name = toolchain.get_arch_name()
        out_dir = os.path.join(self.config.build_dir, arch_name)
        os.makedirs(out_dir, exist_ok=True)

        compiler = toolchain.get_compiler()
        if not shutil.which(compiler):
            msg = "Compiler '%s' not found for arch '%s'" % (compiler, arch_name)
            self._notify(BuildEventType.COMPILE_FAILURE, msg)
            return BuildResult(False, msg)

        self._notify(BuildEventType.COMPILE_START,
                     "Compiling implant for %s (%s mode)" % (arch_name, mode))

        # Select platform-appropriate implant source
        if arch_name == 'win_dll':
            source_list = self.config.implant_sources_win
        else:
            source_list = self.config.implant_sources_posix

        sources = [s for s in source_list if os.path.isfile(s)]
        if not sources:
            msg = "No implant source files found in %s" % self.config.implant_dir
            self._notify(BuildEventType.COMPILE_FAILURE, msg)
            return BuildResult(False, msg)

        # Compile each source to object file
        obj_files = []
        for src in sources:
            basename = os.path.splitext(os.path.basename(src))[0]
            obj_path = os.path.join(out_dir, basename + '.o')
            cmd = [compiler, '-c'] + toolchain.get_flags(mode) + [
                '-I', self.config.jni_include,
                '-I', self.config.jni_platform,
                '-I', self.config.implant_dir,
                '-o', obj_path,
                src,
            ]

            self._notify(BuildEventType.INFO,
                         "  CC %s" % os.path.basename(src))

            try:
                subprocess.check_output(cmd, stderr=subprocess.STDOUT)
                obj_files.append(obj_path)
            except subprocess.CalledProcessError as e:
                msg = "Compilation failed: %s\n%s" % (
                    os.path.basename(src), e.output.decode())
                self._notify(BuildEventType.COMPILE_FAILURE, msg)
                return BuildResult(False, msg)

        # Determine output name and linker flags by arch
        ext_map = {'win_dll': '.dll', 'darwin_dylib': '.so'}  # .dylib only on real macOS
        ext = ext_map.get(arch_name, '.so')
        out_name = 'implant' + ext
        out_path = os.path.join(out_dir, out_name)

        link_flags = ['-shared']
        extra_libs = []
        if arch_name == 'win_dll':
            extra_libs = ['-lws2_32', '-lkernel32']
        elif arch_name == 'darwin_dylib':
            # -dynamiclib/-install_name only work on macOS; on Linux just use -shared
            import sys as _sys
            if _sys.platform == 'darwin':
                link_flags = ['-dynamiclib', '-install_name', '@rpath/implant.dylib']
                ext = '.dylib'
                out_name = 'implant.dylib'
                out_path = os.path.join(out_dir, out_name)

        link_cmd = [compiler] + link_flags + ['-o', out_path] + obj_files + extra_libs

        self._notify(BuildEventType.LINK_START, "  LD %s" % out_name)

        try:
            subprocess.check_output(link_cmd, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as e:
            msg = "Linking failed: %s" % e.output.decode()
            self._notify(BuildEventType.LINK_FAILURE, msg)
            return BuildResult(False, msg)

        artifact = BuildArtifact(out_path, arch_name, ArtifactType.SHARED_OBJECT)
        self.artifacts.append(artifact)

        elapsed = time.time() - start
        self._notify(BuildEventType.COMPILE_SUCCESS,
                     "Built %s for %s (%.2fs)" % (out_name, arch_name, elapsed))

        return BuildResult(True, "Implant compiled", [artifact], elapsed)

    def package_jar(self) -> BuildResult:
        """Assemble all built shared objects into a deployable JAR."""
        start = time.time()
        self._notify(BuildEventType.PACKAGE_START, "Assembling JAR package")

        if not os.path.isdir(self.config.build_dir):
            msg = "No build artifacts found. Run 'compile' first."
            self._notify(BuildEventType.ERROR, msg)
            return BuildResult(False, msg)

        staging = tempfile.mkdtemp(prefix='spideys-jar-')
        try:
            meta_inf = os.path.join(staging, 'META-INF')
            os.makedirs(meta_inf)
            with open(os.path.join(meta_inf, 'MANIFEST.MF'), 'w') as f:
                f.write("Manifest-Version: 1.0\n")
                f.write("Premain-Class: com.utece.student.llpdetection.agents.JavaAgent\n")
                f.write("Can-Retransform-Classes: true\n\n")

            if os.path.isdir(self.config.classes_dir):
                com_src = os.path.join(self.config.classes_dir, 'com')
                if os.path.isdir(com_src):
                    shutil.copytree(com_src, os.path.join(staging, 'com'))
                    self._notify(BuildEventType.INFO, "  Copied Java classes")

            so_count = 0
            for arch_dir in os.listdir(self.config.build_dir):
                arch_path = os.path.join(self.config.build_dir, arch_dir)
                if not os.path.isdir(arch_path):
                    continue
                lib_dest = os.path.join(staging, 'lib', arch_dir)
                os.makedirs(lib_dest, exist_ok=True)
                for f in os.listdir(arch_path):
                    if f.endswith('.so') or f.endswith('.dylib'):
                        shutil.copy2(os.path.join(arch_path, f), lib_dest)
                        so_count += 1
                        self._notify(BuildEventType.INFO,
                                     "  Bundled %s/%s" % (arch_dir, f))

            if so_count == 0:
                msg = "No shared objects found. Run 'compile' first."
                self._notify(BuildEventType.ERROR, msg)
                return BuildResult(False, msg)

            jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
            subprocess.check_call([
                'jar', 'cmf',
                os.path.join(meta_inf, 'MANIFEST.MF'),
                jar_path, '-C', staging, '.'
            ])

            artifact = BuildArtifact(jar_path, 'multi', ArtifactType.JAR)
            self.artifacts.append(artifact)

            elapsed = time.time() - start
            self._notify(BuildEventType.PACKAGE_SUCCESS,
                         "JAR created: %s (%.1fKB)" % (jar_path, artifact.size_kb))

            return BuildResult(True, "Package created", [artifact], elapsed)
        finally:
            shutil.rmtree(staging, ignore_errors=True)

    def clean(self) -> BuildResult:
        """Remove all build artifacts."""
        self._notify(BuildEventType.CLEAN, "Cleaning build artifacts")
        removed = 0

        if os.path.isdir(self.config.build_dir):
            shutil.rmtree(self.config.build_dir, ignore_errors=True)
            removed += 1

        jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
        if os.path.isfile(jar_path):
            os.remove(jar_path)
            removed += 1

        self.artifacts.clear()
        return BuildResult(True, "Cleaned %d artifact(s)" % removed)

    def get_status(self) -> Dict:
        """Return current build state as a dictionary."""
        status = {
            'project_root': self.config.project_root,
            'java_home': self.config.java_home,
            'current_arch': self._current_arch,
            'current_mode': self._current_mode,
            'available_archs': ToolchainFactory.available_architectures(),
            'artifacts': [],
            'source_files': {
                'trampoline': [s for s in self.config.trampoline_sources
                               if os.path.isfile(s)],
                'implant': [s for s in self.config.implant_sources
                            if os.path.isfile(s)],
            },
        }

        if os.path.isdir(self.config.build_dir):
            for arch_dir in sorted(os.listdir(self.config.build_dir)):
                arch_path = os.path.join(self.config.build_dir, arch_dir)
                if os.path.isdir(arch_path):
                    for f in sorted(os.listdir(arch_path)):
                        if f.endswith('.so') or f.endswith('.dylib'):
                            fp = os.path.join(arch_path, f)
                            status['artifacts'].append({
                                'file': f, 'arch': arch_dir,
                                'size_kb': os.path.getsize(fp) / 1024.0,
                            })

        jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
        if os.path.isfile(jar_path):
            status['artifacts'].append({
                'file': 'spideys-agent.jar', 'arch': 'multi',
                'size_kb': os.path.getsize(jar_path) / 1024.0,
            })

        return status
