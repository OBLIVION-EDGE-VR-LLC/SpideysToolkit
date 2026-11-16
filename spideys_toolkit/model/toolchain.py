"""
Toolchain — Strategy + Factory patterns (Gang of Four).

Strategy Pattern:
    Each Toolchain subclass encapsulates the compiler, flags, and
    conventions for a specific target architecture. The BuildModel
    selects the appropriate strategy at runtime based on user input.

Factory Method Pattern:
    ToolchainFactory.create(arch) produces the correct Toolchain
    instance without exposing the instantiation logic. New architectures
    are added by registering a new Toolchain subclass in the factory's
    registry — no existing code needs modification (Open/Closed Principle).
    
By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import abc
from typing import Dict, List


class Toolchain(abc.ABC):
    """
    Strategy interface for architecture-specific compilation.

    Each concrete toolchain knows its compiler binary, architecture
    flags, and ABI conventions. The BuildModel calls these methods
    without knowing which architecture is being targeted.
    """

    @abc.abstractmethod
    def get_compiler(self) -> str:
        """Return the compiler binary name (e.g., 'gcc', 'aarch64-linux-gnu-gcc')."""
        pass

    @abc.abstractmethod
    def get_arch_flags(self) -> List[str]:
        """Return architecture-specific compiler flags."""
        pass

    @abc.abstractmethod
    def get_arch_name(self) -> str:
        """Return the canonical architecture name for directory naming."""
        pass

    def get_flags(self, mode: str) -> List[str]:
        """Return complete flag list for the given build mode."""
        flags = ['-fPIC', '-Wall', '-Wextra', '-Wno-unused-parameter']
        if mode == 'debug':
            flags += ['-g', '-O0', '-DDEBUG']
        else:
            flags += ['-O2', '-s', '-DNDEBUG']
        flags += self.get_arch_flags()
        return flags


class X86_64Toolchain(Toolchain):
    """x86-64 compilation strategy using system GCC."""

    def get_compiler(self) -> str:
        return 'gcc'

    def get_arch_flags(self) -> List[str]:
        return ['-m64']

    def get_arch_name(self) -> str:
        return 'x86_64'


class ARMToolchain(Toolchain):
    """ARM64 cross-compilation strategy using aarch64-linux-gnu-gcc."""

    def get_compiler(self) -> str:
        return 'aarch64-linux-gnu-gcc'

    def get_arch_flags(self) -> List[str]:
        return ['-march=armv8-a']

    def get_arch_name(self) -> str:
        return 'arm'


class MIPSToolchain(Toolchain):
    """MIPS cross-compilation strategy using mips-linux-gnu-gcc."""

    def get_compiler(self) -> str:
        return 'mips-linux-gnu-gcc'

    def get_arch_flags(self) -> List[str]:
        return ['-mips32r2']

    def get_arch_name(self) -> str:
        return 'mips'


class WindowsDLLToolchain(Toolchain):
    """
    Windows DLL cross-compilation strategy using MinGW.
    Produces .dll files for DLL sideloading/shadowing research.

    DLL Sideloading (MITRE ATT&CK T1574.002):
    A shadow DLL with matching exports is placed in the target
    application's directory, where it is loaded before the
    legitimate system DLL due to Windows DLL search order.
    """

    def get_compiler(self) -> str:
        return 'x86_64-w64-mingw32-gcc'

    def get_arch_flags(self) -> List[str]:
        return ['-shared', '-Wl,--out-implib,libshadow.a']

    def get_arch_name(self) -> str:
        return 'win_dll'

    def get_flags(self, mode: str) -> List[str]:
        flags = ['-Wall', '-Wextra', '-Wno-unused-parameter']
        if mode == 'debug':
            flags += ['-g', '-O0', '-DDEBUG']
        else:
            flags += ['-O2', '-s', '-DNDEBUG']
        flags += self.get_arch_flags()
        return flags

    def get_output_extension(self) -> str:
        return '.dll'


class DarwinDylibToolchain(Toolchain):
    """
    macOS dylib compilation strategy.
    Produces .dylib files for dylib shadowing/proxying research.

    On macOS: uses -dynamiclib -install_name (real dylib).
    On Linux: uses -shared -fPIC (proxy .so for development/testing).
    """

    def get_compiler(self) -> str:
        return 'gcc'

    def get_arch_flags(self) -> List[str]:
        # Only compile-time flags here — no linker flags like -dynamiclib
        return []

    def get_arch_name(self) -> str:
        return 'darwin_dylib'

    def get_flags(self, mode: str) -> List[str]:
        flags = ['-fPIC', '-Wall', '-Wextra', '-Wno-unused-parameter']
        if mode == 'debug':
            flags += ['-g', '-O0', '-DDEBUG']
        else:
            flags += ['-O2', '-s', '-DNDEBUG']
        return flags

    def get_output_extension(self) -> str:
        return '.dylib'


class RISCVToolchain(Toolchain):
    """
    RISC-V 64-bit cross-compilation strategy.
    Uses riscv64-linux-gnu-gcc for targeting RISC-V hardware
    and QEMU user-mode emulation for testing.

    RISC-V is an open ISA gaining traction in embedded systems,
    IoT devices, and research platforms — making it a valuable
    target for CNO package development and vulnerability research.
    """

    def get_compiler(self) -> str:
        return 'riscv64-linux-gnu-gcc'

    def get_arch_flags(self) -> List[str]:
        return ['-march=rv64gc', '-mabi=lp64d']

    def get_arch_name(self) -> str:
        return 'riscv64'


class ToolchainFactory:
    """
    Factory Method (GoF) for creating architecture-specific toolchains.

    New architectures are added by calling register() with the arch name
    and toolchain class. The factory handles alias resolution (e.g.,
    'amd64' -> X86_64Toolchain) automatically.
    """

    _registry: Dict[str, type] = {
        'x86_64': X86_64Toolchain,
        'amd64': X86_64Toolchain,
        'arm': ARMToolchain,
        'aarch64': ARMToolchain,
        'mips': MIPSToolchain,
        'win_dll': WindowsDLLToolchain,
        'dll': WindowsDLLToolchain,
        'windows': WindowsDLLToolchain,
        'darwin_dylib': DarwinDylibToolchain,
        'dylib': DarwinDylibToolchain,
        'macos': DarwinDylibToolchain,
        'riscv64': RISCVToolchain,
        'riscv': RISCVToolchain,
    }

    @classmethod
    def create(cls, arch: str) -> Toolchain:
        """Create a Toolchain instance for the specified architecture."""
        key = arch.lower()
        if key not in cls._registry:
            available = ', '.join(sorted(set(
                tc().get_arch_name() for tc in cls._registry.values()
            )))
            raise ValueError(
                "Unsupported architecture: '%s'. Available: %s" % (arch, available)
            )
        return cls._registry[key]()

    @classmethod
    def register(cls, name: str, toolchain_class: type) -> None:
        """Register a new architecture in the factory."""
        cls._registry[name.lower()] = toolchain_class

    @classmethod
    def available_architectures(cls) -> List[str]:
        """Return sorted list of unique architecture names."""
        return sorted(set(tc().get_arch_name() for tc in cls._registry.values()))
