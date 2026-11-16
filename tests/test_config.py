"""Unit tests for BuildConfig."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import os
import pytest

from spideys_toolkit.model.config import BuildConfig


class TestBuildConfig:
    """Tests for build configuration detection and validation."""

    def test_project_root_is_absolute(self):
        config = BuildConfig('.')
        assert os.path.isabs(config.project_root)

    def test_build_dir_under_project_root(self):
        config = BuildConfig('.')
        assert config.build_dir.startswith(config.project_root)

    def test_source_dir_path_structure(self):
        config = BuildConfig('.')
        assert 'inlineassembly' in config.source_dir

    def test_implant_dir_path_structure(self):
        config = BuildConfig('.')
        assert 'reverseshell' in config.implant_dir

    def test_java_home_detected(self):
        config = BuildConfig('.')
        # Should detect something even if JAVA_HOME is not set
        assert config.java_home is not None
        assert len(config.java_home) > 0

    def test_jni_include_paths(self):
        config = BuildConfig('.')
        assert config.jni_include.endswith('include')
        assert 'linux' in config.jni_platform or 'darwin' in config.jni_platform

    def test_trampoline_sources_list(self):
        config = BuildConfig('.')
        assert len(config.trampoline_sources) == 3
        for src in config.trampoline_sources:
            assert src.endswith('.c')

    def test_implant_sources_list(self):
        config = BuildConfig('.')
        assert len(config.implant_sources) == 1
        assert config.implant_sources[0].endswith('.c')

    def test_validate_returns_list(self):
        config = BuildConfig('.')
        warnings = config.validate()
        assert isinstance(warnings, list)

    def test_custom_project_root(self, tmp_path):
        config = BuildConfig(str(tmp_path))
        assert config.project_root == str(tmp_path)
        assert config.build_dir == os.path.join(str(tmp_path), 'build')
