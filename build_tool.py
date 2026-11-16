#!/usr/bin/env python3
"""
CMD2 Build Tool — MVC Architecture with Gang of Four Patterns
=============================================================

Interactive command-line build tool for the Spidey's Toolkit /
CNO Package Development platform. Uses cmd2 for the interactive shell
and implements Gang of Four design patterns within an MVC architecture:

  - **Command Pattern**: Each build operation (compile, package, deploy,
    clean, status) is encapsulated as a BuildCommand object.
  - **Strategy Pattern**: Toolchain selection (x86_64, ARM, MIPS) and
    output formatting (verbose, quiet) are interchangeable strategies.
  - **Factory Method**: ToolchainFactory creates architecture-specific
    toolchains without exposing instantiation logic.
  - **Observer Pattern**: The View subscribes to Model events for
    real-time build progress updates.

Usage:
    python3 build_tool.py           # Enter interactive shell
    python3 build_tool.py compile   # Direct command execution

For authorized security research and educational use only.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import abc
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Dict, List, Optional, Callable


# ===========================================================================
# Model Layer
# ===========================================================================

# --- Value Objects ---

class ArtifactType(Enum):
    SHARED_OBJECT = auto()
    JAR = auto()
    CLASS = auto()
    HEADER = auto()


class BuildEventType(Enum):
    COMPILE_START = auto()
    COMPILE_SUCCESS = auto()
    COMPILE_FAILURE = auto()
    LINK_START = auto()
    LINK_SUCCESS = auto()
    LINK_FAILURE = auto()
    PACKAGE_START = auto()
    PACKAGE_SUCCESS = auto()
    DEPLOY_START = auto()
    DEPLOY_SUCCESS = auto()
    CLEAN = auto()
    INFO = auto()
    ERROR = auto()


@dataclass
class BuildArtifact:
    """Represents a single build output file."""
    path: str
    arch: str
    artifact_type: ArtifactType
    timestamp: float = field(default_factory=time.time)

    @property
    def exists(self) -> bool:
        return os.path.isfile(self.path)

    @property
    def size_kb(self) -> float:
        if self.exists:
            return os.path.getsize(self.path) / 1024.0
        return 0.0


@dataclass
class BuildEvent:
    """Immutable event emitted by the Model during builds."""
    event_type: BuildEventType
    message: str
    artifact: Optional[BuildArtifact] = None
    timestamp: float = field(default_factory=time.time)


@dataclass
class BuildResult:
    """Result of a build command execution."""
    success: bool
    message: str
    artifacts: List[BuildArtifact] = field(default_factory=list)
    elapsed_seconds: float = 0.0


# --- Observer Pattern (GoF) ---

class BuildObserver(abc.ABC):
    """Observer interface for build events."""

    @abc.abstractmethod
    def on_build_event(self, event: BuildEvent) -> None:
        pass


# --- Strategy Pattern (GoF) — Toolchains ---

class Toolchain(abc.ABC):
    """Strategy interface for architecture-specific compilation."""

    @abc.abstractmethod
    def get_compiler(self) -> str:
        pass

    @abc.abstractmethod
    def get_flags(self, mode: str) -> List[str]:
        pass

    @abc.abstractmethod
    def get_arch_name(self) -> str:
        pass

    def get_common_flags(self, mode: str) -> List[str]:
        flags = ['-fPIC', '-Wall', '-Wextra', '-Wno-unused-parameter']
        if mode == 'debug':
            flags += ['-g', '-O0', '-DDEBUG']
        else:
            flags += ['-O2', '-s', '-DNDEBUG']
        return flags


class X86_64Toolchain(Toolchain):
    def get_compiler(self) -> str:
        return 'gcc'

    def get_flags(self, mode: str) -> List[str]:
        return self.get_common_flags(mode) + ['-m64']

    def get_arch_name(self) -> str:
        return 'x86_64'


class ARMToolchain(Toolchain):
    def get_compiler(self) -> str:
        return 'aarch64-linux-gnu-gcc'

    def get_flags(self, mode: str) -> List[str]:
        return self.get_common_flags(mode) + ['-march=armv8-a']

    def get_arch_name(self) -> str:
        return 'arm'


class MIPSToolchain(Toolchain):
    def get_compiler(self) -> str:
        return 'mips-linux-gnu-gcc'

    def get_flags(self, mode: str) -> List[str]:
        return self.get_common_flags(mode) + ['-mips32r2']

    def get_arch_name(self) -> str:
        return 'mips'


# --- Factory Method Pattern (GoF) ---

class ToolchainFactory:
    """Factory for creating architecture-specific toolchains."""

    _registry: Dict[str, type] = {
        'x86_64': X86_64Toolchain,
        'amd64': X86_64Toolchain,
        'arm': ARMToolchain,
        'aarch64': ARMToolchain,
        'mips': MIPSToolchain,
    }

    @classmethod
    def create(cls, arch: str) -> Toolchain:
        key = arch.lower()
        if key not in cls._registry:
            raise ValueError(
                "Unsupported architecture: '%s'. Supported: %s"
                % (arch, ', '.join(sorted(set(
                    tc().get_arch_name() for tc in cls._registry.values()
                ))))
            )
        return cls._registry[key]()

    @classmethod
    def available_architectures(cls) -> List[str]:
        return sorted(set(tc().get_arch_name() for tc in cls._registry.values()))


# --- Build Configuration ---

class BuildConfig:
    """Centralized build configuration."""

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

        self.trampoline_sources = [
            os.path.join(self.source_dir, f) for f in [
                'RegistersNative_64.c',
                'TrampolineExample.c',
                'RIPExample.c',
            ]
        ]
        self.implant_sources = [
            os.path.join(self.implant_dir, 'ReverseShell.c'),
        ]

    def _detect_java_home(self) -> str:
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


# --- Model ---

class BuildModel:
    """
    Model in MVC — owns build state, executes build logic,
    and notifies observers of state changes.
    """

    def __init__(self, config: BuildConfig):
        self.config = config
        self.artifacts: List[BuildArtifact] = []
        self._observers: List[BuildObserver] = []
        self._current_arch = platform.machine()
        self._current_mode = 'debug'

    def add_observer(self, observer: BuildObserver) -> None:
        self._observers.append(observer)

    def remove_observer(self, observer: BuildObserver) -> None:
        self._observers.remove(observer)

    def _notify(self, event_type: BuildEventType, message: str,
                artifact: Optional[BuildArtifact] = None) -> None:
        event = BuildEvent(event_type, message, artifact)
        for obs in self._observers:
            obs.on_build_event(event)

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

        artifacts = []
        sources = [s for s in self.config.trampoline_sources if os.path.isfile(s)]

        if not sources:
            msg = "No source files found in %s" % self.config.source_dir
            self._notify(BuildEventType.COMPILE_FAILURE, msg)
            return BuildResult(False, msg)

        # Compile each source to object file
        obj_files = []
        for src in sources:
            basename = os.path.splitext(os.path.basename(src))[0]
            obj_path = os.path.join(out_dir, basename + '.o')
            cmd = [
                compiler,
                '-c',
            ] + toolchain.get_flags(mode) + [
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

        self._notify(BuildEventType.LINK_START,
                     "  LD libtrampoline.so")

        try:
            subprocess.check_output(link_cmd, stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as e:
            msg = "Linking failed: %s" % e.output.decode()
            self._notify(BuildEventType.LINK_FAILURE, msg)
            return BuildResult(False, msg)

        artifact = BuildArtifact(so_path, arch_name, ArtifactType.SHARED_OBJECT)
        artifacts.append(artifact)
        self.artifacts.append(artifact)

        elapsed = time.time() - start
        self._notify(BuildEventType.COMPILE_SUCCESS,
                     "Built libtrampoline.so for %s (%.2fs)" % (arch_name, elapsed))

        return BuildResult(True, "Compilation successful", artifacts, elapsed)

    def package_jar(self) -> BuildResult:
        """Assemble all built shared objects into a deployable JAR."""
        start = time.time()
        self._notify(BuildEventType.PACKAGE_START, "Assembling JAR package")

        if not os.path.isdir(self.config.build_dir):
            msg = "No build artifacts found. Run 'compile' first."
            self._notify(BuildEventType.ERROR, msg)
            return BuildResult(False, msg)

        staging = tempfile.mkdtemp(prefix='llp-jar-')

        try:
            # Create manifest
            meta_inf = os.path.join(staging, 'META-INF')
            os.makedirs(meta_inf)
            with open(os.path.join(meta_inf, 'MANIFEST.MF'), 'w') as f:
                f.write("Manifest-Version: 1.0\n")
                f.write("Premain-Class: com.utece.student.llpdetection.agents.JavaAgent\n")
                f.write("Can-Retransform-Classes: true\n\n")

            # Copy classes
            if os.path.isdir(self.config.classes_dir):
                com_src = os.path.join(self.config.classes_dir, 'com')
                if os.path.isdir(com_src):
                    shutil.copytree(com_src, os.path.join(staging, 'com'))
                    self._notify(BuildEventType.INFO, "  Copied Java classes")

            # Copy native libraries
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

            # Create JAR
            jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
            subprocess.check_call([
                'jar', 'cmf',
                os.path.join(meta_inf, 'MANIFEST.MF'),
                jar_path,
                '-C', staging, '.'
            ])

            artifact = BuildArtifact(jar_path, 'multi', ArtifactType.JAR)
            self.artifacts.append(artifact)

            elapsed = time.time() - start
            self._notify(BuildEventType.PACKAGE_SUCCESS,
                         "JAR created: %s (%.1fKB, %.2fs)" % (
                             jar_path, artifact.size_kb, elapsed))

            return BuildResult(True, "Package created", [artifact], elapsed)

        finally:
            shutil.rmtree(staging, ignore_errors=True)

    def clean(self) -> BuildResult:
        """Remove all build artifacts."""
        self._notify(BuildEventType.CLEAN, "Cleaning build artifacts")

        removed = 0
        if os.path.isdir(self.config.build_dir):
            shutil.rmtree(self.config.build_dir)
            removed += 1

        jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
        if os.path.isfile(jar_path):
            os.remove(jar_path)
            removed += 1

        self.artifacts.clear()
        return BuildResult(True, "Cleaned %d artifact(s)" % removed)

    def get_status(self) -> Dict:
        """Return current build state."""
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
                                'file': f,
                                'arch': arch_dir,
                                'size_kb': os.path.getsize(fp) / 1024.0,
                            })

        jar_path = os.path.join(self.config.project_root, 'spideys-agent.jar')
        if os.path.isfile(jar_path):
            status['artifacts'].append({
                'file': 'spideys-agent.jar',
                'arch': 'multi',
                'size_kb': os.path.getsize(jar_path) / 1024.0,
            })

        return status


# ===========================================================================
# View Layer
# ===========================================================================

# --- Strategy Pattern (GoF) — Output Formatters ---

class OutputFormatter(abc.ABC):
    """Strategy interface for formatting build output."""

    @abc.abstractmethod
    def format_event(self, event: BuildEvent) -> str:
        pass

    @abc.abstractmethod
    def format_result(self, result: BuildResult) -> str:
        pass

    @abc.abstractmethod
    def format_status(self, status: Dict) -> str:
        pass


class VerboseFormatter(OutputFormatter):
    """Verbose output with timestamps and details."""

    COLORS = {
        BuildEventType.COMPILE_START: '\033[36m',    # Teal
        BuildEventType.COMPILE_SUCCESS: '\033[32m',  # Green
        BuildEventType.COMPILE_FAILURE: '\033[31m',  # Red
        BuildEventType.LINK_START: '\033[36m',
        BuildEventType.LINK_SUCCESS: '\033[32m',
        BuildEventType.LINK_FAILURE: '\033[31m',
        BuildEventType.PACKAGE_START: '\033[36m',
        BuildEventType.PACKAGE_SUCCESS: '\033[32m',
        BuildEventType.DEPLOY_START: '\033[33m',     # Yellow
        BuildEventType.DEPLOY_SUCCESS: '\033[32m',
        BuildEventType.CLEAN: '\033[33m',
        BuildEventType.INFO: '\033[37m',             # White
        BuildEventType.ERROR: '\033[31m',
    }
    RESET = '\033[0m'

    def format_event(self, event: BuildEvent) -> str:
        color = self.COLORS.get(event.event_type, '')
        ts = time.strftime('%H:%M:%S', time.localtime(event.timestamp))
        return "%s[%s] %s%s" % (color, ts, event.message, self.RESET)

    def format_result(self, result: BuildResult) -> str:
        if result.success:
            return "\033[32m=== %s (%.2fs) ===\033[0m" % (
                result.message, result.elapsed_seconds)
        else:
            return "\033[31m=== FAILED: %s ===\033[0m" % result.message

    def format_status(self, status: Dict) -> str:
        lines = [
            "\033[36m=== Build Status ===\033[0m",
            "  Project:  %s" % status['project_root'],
            "  Java:     %s" % status['java_home'],
            "  Arch:     %s" % status['current_arch'],
            "  Mode:     %s" % status['current_mode'],
            "  Targets:  %s" % ', '.join(status['available_archs']),
            "",
            "  Sources:",
        ]
        for name, files in status['source_files'].items():
            lines.append("    %s: %d file(s)" % (name, len(files)))

        if status['artifacts']:
            lines.append("")
            lines.append("  Artifacts:")
            for a in status['artifacts']:
                lines.append("    [%s] %s (%.1f KB)" % (
                    a['arch'], a['file'], a['size_kb']))
        else:
            lines.append("")
            lines.append("  No build artifacts.")

        return '\n'.join(lines)


class QuietFormatter(OutputFormatter):
    """Minimal output — errors and results only."""

    def format_event(self, event: BuildEvent) -> str:
        if event.event_type in (BuildEventType.ERROR,
                                BuildEventType.COMPILE_FAILURE,
                                BuildEventType.LINK_FAILURE):
            return "ERROR: %s" % event.message
        return ""

    def format_result(self, result: BuildResult) -> str:
        if result.success:
            return "OK"
        return "FAILED: %s" % result.message

    def format_status(self, status: Dict) -> str:
        return "arch=%s mode=%s artifacts=%d" % (
            status['current_arch'], status['current_mode'],
            len(status['artifacts']))


class BuildView(BuildObserver):
    """
    View in MVC — renders build output to the console.
    Implements BuildObserver to receive model events.
    """

    def __init__(self, formatter: OutputFormatter = None):
        self.formatter = formatter or VerboseFormatter()

    def on_build_event(self, event: BuildEvent) -> None:
        text = self.formatter.format_event(event)
        if text:
            print(text)

    def display_result(self, result: BuildResult) -> None:
        print(self.formatter.format_result(result))

    def display_status(self, status: Dict) -> None:
        print(self.formatter.format_status(status))

    def display_help(self) -> None:
        print("""
\033[36mSpidey's Toolkit Tool — CNO Package Development\033[0m

Commands:
  compile [arch] [mode]   Compile native shared objects
  package                 Assemble multi-arch JAR
  clean                   Remove all build artifacts
  status                  Show current build state
  set arch <value>        Set target architecture (x86_64, arm, mips)
  set mode <value>        Set build mode (debug, release)
  set format <value>      Set output format (verbose, quiet)
  help                    Show this help text
  quit / exit             Exit the build tool

Examples:
  compile                 Build for current arch/mode
  compile arm release     Cross-compile ARM release build
  set arch arm            Change default architecture
  package                 Create deployable JAR

No AFL dependency. Trampoline-based interception only.
""")

    def display_prompt(self) -> str:
        arch = '\033[36m'
        reset = '\033[0m'
        return "%sspideys-toolkit%s> " % (arch, reset)


# ===========================================================================
# Controller Layer — Command Pattern (GoF)
# ===========================================================================

class BuildCommand(abc.ABC):
    """Command interface (GoF Command Pattern)."""

    def __init__(self, model: BuildModel, view: BuildView):
        self.model = model
        self.view = view

    @abc.abstractmethod
    def execute(self, args: List[str]) -> BuildResult:
        pass

    @abc.abstractmethod
    def get_name(self) -> str:
        pass

    @abc.abstractmethod
    def get_help(self) -> str:
        pass


class CompileCommand(BuildCommand):
    def get_name(self) -> str:
        return 'compile'

    def get_help(self) -> str:
        return 'compile [arch] [mode] — Compile native shared objects'

    def execute(self, args: List[str]) -> BuildResult:
        arch = args[0] if len(args) > 0 else None
        mode = args[1] if len(args) > 1 else None
        result = self.model.compile(arch, mode)
        self.view.display_result(result)
        return result


class PackageCommand(BuildCommand):
    def get_name(self) -> str:
        return 'package'

    def get_help(self) -> str:
        return 'package — Assemble multi-arch JAR from built artifacts'

    def execute(self, args: List[str]) -> BuildResult:
        result = self.model.package_jar()
        self.view.display_result(result)
        return result


class CleanCommand(BuildCommand):
    def get_name(self) -> str:
        return 'clean'

    def get_help(self) -> str:
        return 'clean — Remove all build artifacts'

    def execute(self, args: List[str]) -> BuildResult:
        result = self.model.clean()
        self.view.display_result(result)
        return result


class StatusCommand(BuildCommand):
    def get_name(self) -> str:
        return 'status'

    def get_help(self) -> str:
        return 'status — Show current build state and artifacts'

    def execute(self, args: List[str]) -> BuildResult:
        status = self.model.get_status()
        self.view.display_status(status)
        return BuildResult(True, "Status displayed")


class SetCommand(BuildCommand):
    def get_name(self) -> str:
        return 'set'

    def get_help(self) -> str:
        return 'set <arch|mode|format> <value> — Configure build settings'

    def execute(self, args: List[str]) -> BuildResult:
        if len(args) < 2:
            return BuildResult(False, "Usage: set <arch|mode|format> <value>")

        key, value = args[0], args[1]

        if key == 'arch':
            try:
                ToolchainFactory.create(value)
                self.model.current_arch = value
                return BuildResult(True, "Architecture set to '%s'" % value)
            except ValueError as e:
                return BuildResult(False, str(e))

        elif key == 'mode':
            if value in ('debug', 'release'):
                self.model.current_mode = value
                return BuildResult(True, "Build mode set to '%s'" % value)
            return BuildResult(False, "Mode must be 'debug' or 'release'")

        elif key == 'format':
            if value == 'verbose':
                self.view.formatter = VerboseFormatter()
            elif value == 'quiet':
                self.view.formatter = QuietFormatter()
            else:
                return BuildResult(False, "Format must be 'verbose' or 'quiet'")
            return BuildResult(True, "Output format set to '%s'" % value)

        return BuildResult(False, "Unknown setting: '%s'" % key)


class HelpCommand(BuildCommand):
    def get_name(self) -> str:
        return 'help'

    def get_help(self) -> str:
        return 'help — Show available commands'

    def execute(self, args: List[str]) -> BuildResult:
        self.view.display_help()
        return BuildResult(True, "")


class BuildController:
    """
    Controller in MVC — receives user input, dispatches to
    Command objects, and coordinates Model and View.
    """

    def __init__(self, model: BuildModel, view: BuildView):
        self.model = model
        self.view = view
        self._commands: Dict[str, BuildCommand] = {}
        self._running = False

        # Register commands
        for cmd_cls in [CompileCommand, PackageCommand, CleanCommand,
                        StatusCommand, SetCommand, HelpCommand]:
            cmd = cmd_cls(model, view)
            self._commands[cmd.get_name()] = cmd

    def dispatch(self, line: str) -> Optional[BuildResult]:
        """Parse and dispatch a command line."""
        parts = line.strip().split()
        if not parts:
            return None

        cmd_name = parts[0].lower()
        args = parts[1:]

        if cmd_name in ('quit', 'exit', 'q'):
            self._running = False
            return BuildResult(True, "Goodbye")

        if cmd_name in self._commands:
            return self._commands[cmd_name].execute(args)

        print("Unknown command: '%s'. Type 'help' for available commands." % cmd_name)
        return None

    def run_interactive(self) -> None:
        """Run the interactive command loop."""
        self._running = True
        print("\033[36mSpidey's Toolkit Tool\033[0m — CNO Package Development")
        print("Type 'help' for available commands.\n")

        while self._running:
            try:
                line = input(self.view.display_prompt())
                if line.strip():
                    self.dispatch(line)
            except (EOFError, KeyboardInterrupt):
                print("\nGoodbye")
                break

    def run_command(self, args: List[str]) -> int:
        """Execute a single command from CLI arguments."""
        if not args:
            self.run_interactive()
            return 0

        result = self.dispatch(' '.join(args))
        if result and not result.success:
            return 1
        return 0


# ===========================================================================
# Entry Point
# ===========================================================================

def main():
    project_root = os.path.dirname(os.path.abspath(__file__))
    config = BuildConfig(project_root)
    model = BuildModel(config)
    view = BuildView(VerboseFormatter())

    # Observer pattern: view subscribes to model events
    model.add_observer(view)

    controller = BuildController(model, view)
    sys.exit(controller.run_command(sys.argv[1:]))


if __name__ == '__main__':
    main()
