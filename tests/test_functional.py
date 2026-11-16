"""Functional / integration tests for the spideys_toolkit package."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import os
import sys
import pytest

from spideys_toolkit.model.config import BuildConfig
from spideys_toolkit.model.build_model import BuildModel, BuildObserver
from spideys_toolkit.model.artifacts import BuildEventType
from spideys_toolkit.model.toolchain import ToolchainFactory
from spideys_toolkit.view.formatters import VerboseFormatter, QuietFormatter


class EventCapture(BuildObserver):
    """Captures all events for assertion."""
    def __init__(self):
        self.events = []
    def on_build_event(self, event):
        self.events.append(event)


class TestMVCIntegration:
    """Test that Model-View-Controller components work together."""

    def test_observer_receives_compile_events(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        capture = EventCapture()
        model.add_observer(capture)

        # Attempt compile with invalid arch to trigger events
        model.compile(arch='nonexistent')
        assert len(capture.events) > 0
        assert capture.events[0].event_type == BuildEventType.ERROR

    def test_formatter_strategy_swap(self):
        """Test Strategy pattern: formatter swap at runtime."""
        verbose = VerboseFormatter()
        quiet = QuietFormatter()

        from spideys_toolkit.model.artifacts import BuildResult
        result = BuildResult(True, "Done", elapsed_seconds=1.0)

        verbose_out = verbose.format_result(result)
        quiet_out = quiet.format_result(result)

        assert len(verbose_out) > len(quiet_out)
        assert "Done" in verbose_out
        assert quiet_out == "OK"

    def test_factory_creates_all_registered_archs(self):
        """Test Factory pattern: all registered archs are creatable."""
        for arch in ToolchainFactory.available_architectures():
            tc = ToolchainFactory.create(arch)
            assert tc.get_compiler() is not None
            assert tc.get_arch_name() == arch

    def test_toolchain_flags_are_consistent(self):
        """Test Strategy pattern: all toolchains produce valid flag lists."""
        for arch in ToolchainFactory.available_architectures():
            tc = ToolchainFactory.create(arch)
            for mode in ('debug', 'release'):
                flags = tc.get_flags(mode)
                assert isinstance(flags, list)
                assert all(isinstance(f, str) for f in flags)
                # Debug should have -g, release should have -O2
                if mode == 'debug':
                    assert '-g' in flags
                else:
                    assert '-O2' in flags


class TestEndToEndWorkflow:
    """End-to-end test of the build workflow."""

    def test_status_clean_status_cycle(self):
        """Test that status -> clean -> status produces consistent state."""
        config = BuildConfig('.')
        model = BuildModel(config)

        status1 = model.get_status()
        model.clean()
        status2 = model.get_status()

        # Artifacts may persist if owned by root (Docker builds); check model state cleared
        assert model.artifacts == []
        assert status2['current_arch'] == status1['current_arch']
        assert status2['current_mode'] == status1['current_mode']

    def test_set_arch_persists_across_operations(self):
        """Test that setting arch persists."""
        config = BuildConfig('.')
        model = BuildModel(config)
        model.current_arch = 'arm'
        assert model.current_arch == 'arm'
        status = model.get_status()
        assert status['current_arch'] == 'arm'

    def test_compile_with_missing_cross_compiler_fails_gracefully(self):
        """Test that missing cross-compiler produces a clear error."""
        config = BuildConfig('.')
        model = BuildModel(config)
        capture = EventCapture()
        model.add_observer(capture)

        # MIPS cross-compiler is unlikely to be installed in CI
        result = model.compile(arch='mips')
        if not result.success:
            assert 'not found' in result.message.lower() or \
                   'no source' in result.message.lower()


class TestDLLDylibToolchainIntegration:
    """Test DLL sideloading and dylib shadowing toolchain integration."""

    def test_win_dll_toolchain_no_fpic(self):
        """Windows PE is inherently relocatable — no -fPIC."""
        from spideys_toolkit.model.toolchain import WindowsDLLToolchain
        tc = WindowsDLLToolchain()
        flags = tc.get_flags('debug')
        assert '-fPIC' not in flags

    def test_darwin_dylib_no_linker_flags_in_compile(self):
        """Darwin compile flags must not contain linker-only flags."""
        from spideys_toolkit.model.toolchain import DarwinDylibToolchain
        tc = DarwinDylibToolchain()
        flags = tc.get_flags('release')
        assert '-fPIC' in flags
        assert '-dynamiclib' not in flags
        assert '-install_name' not in flags

    def test_factory_aliases_all_resolve(self):
        """All platform aliases should resolve to correct toolchains."""
        alias_map = {
            'dll': 'win_dll',
            'windows': 'win_dll',
            'dylib': 'darwin_dylib',
            'macos': 'darwin_dylib',
            'amd64': 'x86_64',
            'aarch64': 'arm',
        }
        for alias, expected_arch in alias_map.items():
            tc = ToolchainFactory.create(alias)
        for alias in ('riscv', 'riscv64'):
            tc = ToolchainFactory.create(alias)
            assert tc.get_arch_name() == 'riscv64'

        for alias, expected_arch in alias_map.items():
            tc = ToolchainFactory.create(alias)
            assert tc.get_arch_name() == expected_arch, \
                "Alias '%s' resolved to '%s', expected '%s'" % (
                    alias, tc.get_arch_name(), expected_arch)
