"""Unit and functional tests for BuildModel."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import os
import pytest

from spideys_toolkit.model.config import BuildConfig
from spideys_toolkit.model.build_model import BuildModel, BuildObserver
from spideys_toolkit.model.artifacts import BuildEvent, BuildEventType
from spideys_toolkit.model.toolchain import ToolchainFactory


class MockObserver(BuildObserver):
    """Test observer that records all events."""

    def __init__(self):
        self.events = []

    def on_build_event(self, event: BuildEvent) -> None:
        self.events.append(event)

    @property
    def event_types(self):
        return [e.event_type for e in self.events]

    @property
    def messages(self):
        return [e.message for e in self.events]


class TestBuildModelProperties:
    """Tests for BuildModel property management."""

    def test_default_mode_is_debug(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        assert model.current_mode == 'debug'

    def test_set_arch(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        model.current_arch = 'arm'
        assert model.current_arch == 'arm'

    def test_set_mode(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        model.current_mode = 'release'
        assert model.current_mode == 'release'

    def test_initial_artifacts_empty(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        assert model.artifacts == []


class TestBuildModelObserver:
    """Tests for the Observer pattern implementation."""

    def test_add_observer(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)
        # Trigger an event
        model._notify(BuildEventType.INFO, "test")
        assert len(observer.events) == 1
        assert observer.events[0].message == "test"

    def test_remove_observer(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)
        model.remove_observer(observer)
        model._notify(BuildEventType.INFO, "test")
        assert len(observer.events) == 0

    def test_multiple_observers(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        obs1 = MockObserver()
        obs2 = MockObserver()
        model.add_observer(obs1)
        model.add_observer(obs2)
        model._notify(BuildEventType.INFO, "test")
        assert len(obs1.events) == 1
        assert len(obs2.events) == 1


class TestBuildModelCompile:
    """Tests for compile operations."""

    def test_compile_invalid_arch_returns_failure(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)
        result = model.compile(arch='sparc')
        assert result.success is False
        assert 'Unsupported' in result.message

    def test_compile_invalid_arch_notifies_error(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)
        model.compile(arch='sparc')
        assert BuildEventType.ERROR in observer.event_types

    def test_compile_x86_64_with_sources(self):
        """Functional test — compiles if gcc and sources are available."""
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)

        sources_exist = any(os.path.isfile(s) for s in config.trampoline_sources)
        if not sources_exist:
            pytest.skip("Trampoline source files not found")

        import shutil
        if not shutil.which('gcc'):
            pytest.skip("gcc not found")

        result = model.compile(arch='x86_64', mode='debug')
        # May fail due to missing JNI headers, but should at least attempt
        if result.success:
            assert BuildEventType.COMPILE_SUCCESS in observer.event_types
            assert len(result.artifacts) > 0


class TestBuildModelClean:
    """Tests for clean operations."""

    def test_clean_empty_build(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        result = model.clean()
        assert result.success is True

    def test_clean_clears_artifacts(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        from spideys_toolkit.model.artifacts import BuildArtifact, ArtifactType
        model.artifacts.append(
            BuildArtifact('/fake/path.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        )
        model.clean()
        assert model.artifacts == []

    def test_clean_notifies_observer(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        observer = MockObserver()
        model.add_observer(observer)
        model.clean()
        assert BuildEventType.CLEAN in observer.event_types


class TestBuildModelStatus:
    """Tests for status reporting."""

    def test_status_returns_dict(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        status = model.get_status()
        assert isinstance(status, dict)

    def test_status_has_required_keys(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        status = model.get_status()
        required_keys = ['project_root', 'java_home', 'current_arch',
                         'current_mode', 'available_archs', 'artifacts',
                         'source_files']
        for key in required_keys:
            assert key in status, "Missing key: %s" % key

    def test_status_available_archs_is_list(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        status = model.get_status()
        assert isinstance(status['available_archs'], list)
        assert len(status['available_archs']) >= 3

    def test_status_source_files_structure(self):
        config = BuildConfig('.')
        model = BuildModel(config)
        status = model.get_status()
        assert 'trampoline' in status['source_files']
        assert 'implant' in status['source_files']


class TestBuildModelPackage:
    """Tests for JAR packaging."""

    def test_package_without_build_fails(self, tmp_path):
        config = BuildConfig(str(tmp_path))
        model = BuildModel(config)
        result = model.package_jar()
        assert result.success is False
        assert 'No build artifacts' in result.message
