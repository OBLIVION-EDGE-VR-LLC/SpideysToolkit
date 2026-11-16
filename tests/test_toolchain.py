"""Unit tests for Toolchain Strategy and Factory patterns."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import pytest

from spideys_toolkit.model.toolchain import (
    Toolchain, X86_64Toolchain, ARMToolchain, MIPSToolchain,
    WindowsDLLToolchain, DarwinDylibToolchain, RISCVToolchain,
    ToolchainFactory
)


class TestX86_64Toolchain:
    """Tests for x86-64 toolchain strategy."""

    def test_compiler(self):
        tc = X86_64Toolchain()
        assert tc.get_compiler() == 'gcc'

    def test_arch_name(self):
        tc = X86_64Toolchain()
        assert tc.get_arch_name() == 'x86_64'

    def test_arch_flags_include_m64(self):
        tc = X86_64Toolchain()
        assert '-m64' in tc.get_arch_flags()

    def test_debug_flags_include_g(self):
        tc = X86_64Toolchain()
        flags = tc.get_flags('debug')
        assert '-g' in flags
        assert '-O0' in flags
        assert '-fPIC' in flags

    def test_release_flags_include_O2(self):
        tc = X86_64Toolchain()
        flags = tc.get_flags('release')
        assert '-O2' in flags
        assert '-s' in flags

    def test_fpic_in_all_modes(self):
        tc = X86_64Toolchain()
        for mode in ('debug', 'release'):
            assert '-fPIC' in tc.get_flags(mode)


class TestARMToolchain:
    """Tests for ARM64 cross-compilation toolchain."""

    def test_compiler(self):
        tc = ARMToolchain()
        assert tc.get_compiler() == 'aarch64-linux-gnu-gcc'

    def test_arch_name(self):
        tc = ARMToolchain()
        assert tc.get_arch_name() == 'arm'

    def test_march_flag(self):
        tc = ARMToolchain()
        assert '-march=armv8-a' in tc.get_arch_flags()


class TestMIPSToolchain:
    """Tests for MIPS cross-compilation toolchain."""

    def test_compiler(self):
        tc = MIPSToolchain()
        assert tc.get_compiler() == 'mips-linux-gnu-gcc'

    def test_arch_name(self):
        tc = MIPSToolchain()
        assert tc.get_arch_name() == 'mips'

    def test_mips32r2_flag(self):
        tc = MIPSToolchain()
        assert '-mips32r2' in tc.get_arch_flags()


class TestWindowsDLLToolchain:
    """Tests for Windows DLL sideloading toolchain."""

    def test_compiler(self):
        tc = WindowsDLLToolchain()
        assert tc.get_compiler() == 'x86_64-w64-mingw32-gcc'

    def test_arch_name(self):
        tc = WindowsDLLToolchain()
        assert tc.get_arch_name() == 'win_dll'

    def test_output_extension(self):
        tc = WindowsDLLToolchain()
        assert tc.get_output_extension() == '.dll'

    def test_no_fpic_on_windows(self):
        """Windows PE format is inherently relocatable — no -fPIC needed."""
        tc = WindowsDLLToolchain()
        flags = tc.get_flags('debug')
        assert '-fPIC' not in flags

    def test_shared_in_arch_flags(self):
        tc = WindowsDLLToolchain()
        assert '-shared' in tc.get_arch_flags()


class TestDarwinDylibToolchain:
    """Tests for macOS dylib shadowing toolchain."""

    def test_compiler(self):
        tc = DarwinDylibToolchain()
        assert tc.get_compiler() == 'gcc'

    def test_arch_name(self):
        tc = DarwinDylibToolchain()
        assert tc.get_arch_name() == 'darwin_dylib'

    def test_output_extension(self):
        tc = DarwinDylibToolchain()
        assert tc.get_output_extension() == '.dylib'

    def test_no_linker_flags_in_compile_flags(self):
        """Linker flags like -dynamiclib/-install_name are NOT in compile flags."""
        tc = DarwinDylibToolchain()
        flags = tc.get_flags('debug')
        assert '-dynamiclib' not in flags
        assert '-install_name' not in flags

    def test_fpic_in_compile_flags(self):
        tc = DarwinDylibToolchain()
        assert '-fPIC' in tc.get_flags('debug')

    def test_fpic_present(self):
        tc = DarwinDylibToolchain()
        flags = tc.get_flags('debug')
        assert '-fPIC' in flags


class TestRISCVToolchain:
    """Tests for RISC-V 64-bit cross-compilation toolchain."""

    def test_compiler(self):
        tc = RISCVToolchain()
        assert tc.get_compiler() == 'riscv64-linux-gnu-gcc'

    def test_arch_name(self):
        tc = RISCVToolchain()
        assert tc.get_arch_name() == 'riscv64'

    def test_march_flags(self):
        tc = RISCVToolchain()
        flags = tc.get_arch_flags()
        assert '-march=rv64gc' in flags
        assert '-mabi=lp64d' in flags

    def test_fpic_present(self):
        tc = RISCVToolchain()
        flags = tc.get_flags('debug')
        assert '-fPIC' in flags

    def test_debug_and_release_modes(self):
        tc = RISCVToolchain()
        assert '-g' in tc.get_flags('debug')
        assert '-O2' in tc.get_flags('release')


class TestToolchainFactory:
    """Tests for the Factory Method pattern."""

    def test_create_x86_64(self):
        tc = ToolchainFactory.create('x86_64')
        assert isinstance(tc, X86_64Toolchain)

    def test_create_amd64_alias(self):
        tc = ToolchainFactory.create('amd64')
        assert isinstance(tc, X86_64Toolchain)

    def test_create_arm(self):
        tc = ToolchainFactory.create('arm')
        assert isinstance(tc, ARMToolchain)

    def test_create_aarch64_alias(self):
        tc = ToolchainFactory.create('aarch64')
        assert isinstance(tc, ARMToolchain)

    def test_create_mips(self):
        tc = ToolchainFactory.create('mips')
        assert isinstance(tc, MIPSToolchain)

    def test_create_win_dll(self):
        tc = ToolchainFactory.create('win_dll')
        assert isinstance(tc, WindowsDLLToolchain)

    def test_create_dll_alias(self):
        tc = ToolchainFactory.create('dll')
        assert isinstance(tc, WindowsDLLToolchain)

    def test_create_windows_alias(self):
        tc = ToolchainFactory.create('windows')
        assert isinstance(tc, WindowsDLLToolchain)

    def test_create_darwin_dylib(self):
        tc = ToolchainFactory.create('darwin_dylib')
        assert isinstance(tc, DarwinDylibToolchain)

    def test_create_dylib_alias(self):
        tc = ToolchainFactory.create('dylib')
        assert isinstance(tc, DarwinDylibToolchain)

    def test_create_macos_alias(self):
        tc = ToolchainFactory.create('macos')
        assert isinstance(tc, DarwinDylibToolchain)

    def test_create_riscv64(self):
        tc = ToolchainFactory.create('riscv64')
        assert isinstance(tc, RISCVToolchain)

    def test_create_riscv_alias(self):
        tc = ToolchainFactory.create('riscv')
        assert isinstance(tc, RISCVToolchain)

    def test_create_case_insensitive(self):
        tc = ToolchainFactory.create('X86_64')
        assert isinstance(tc, X86_64Toolchain)

    def test_create_unsupported_raises(self):
        with pytest.raises(ValueError, match="Unsupported architecture"):
            ToolchainFactory.create('sparc')

    def test_available_architectures(self):
        archs = ToolchainFactory.available_architectures()
        assert isinstance(archs, list)
        assert 'x86_64' in archs
        assert 'arm' in archs
        assert 'mips' in archs
        assert 'win_dll' in archs
        assert 'darwin_dylib' in archs
        assert 'riscv64' in archs

    def test_available_architectures_sorted(self):
        archs = ToolchainFactory.available_architectures()
        assert archs == sorted(archs)

    def test_register_custom_toolchain(self):
        """Test the Open/Closed principle — new archs without modifying existing code."""
        class RISCVToolchain(Toolchain):
            def get_compiler(self): return 'riscv64-linux-gnu-gcc'
            def get_arch_flags(self): return ['-march=rv64gc']
            def get_arch_name(self): return 'riscv64'

        ToolchainFactory.register('riscv64', RISCVToolchain)
        tc = ToolchainFactory.create('riscv64')
        assert isinstance(tc, RISCVToolchain)
        assert tc.get_compiler() == 'riscv64-linux-gnu-gcc'

        # Cleanup
        del ToolchainFactory._registry['riscv64']
