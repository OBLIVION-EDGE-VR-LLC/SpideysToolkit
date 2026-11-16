"""Unit tests for build artifacts, events, and results."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import os
import time
import pytest

from spideys_toolkit.model.artifacts import (
    ArtifactType, BuildArtifact, BuildEvent, BuildEventType, BuildResult
)


class TestArtifactType:
    """Tests for ArtifactType enum."""

    def test_enum_values_exist(self):
        assert ArtifactType.SHARED_OBJECT is not None
        assert ArtifactType.JAR is not None
        assert ArtifactType.CLASS is not None
        assert ArtifactType.HEADER is not None

    def test_enum_uniqueness(self):
        values = [e.value for e in ArtifactType]
        assert len(values) == len(set(values))


class TestBuildArtifact:
    """Tests for BuildArtifact dataclass."""

    def test_creation(self):
        artifact = BuildArtifact('/tmp/test.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        assert artifact.path == '/tmp/test.so'
        assert artifact.arch == 'x86_64'
        assert artifact.artifact_type == ArtifactType.SHARED_OBJECT

    def test_exists_false_for_nonexistent(self):
        artifact = BuildArtifact('/nonexistent/path.so', 'arm', ArtifactType.SHARED_OBJECT)
        assert artifact.exists is False

    def test_exists_true_for_real_file(self, tmp_path):
        test_file = tmp_path / 'test.so'
        test_file.write_text('fake')
        artifact = BuildArtifact(str(test_file), 'x86_64', ArtifactType.SHARED_OBJECT)
        assert artifact.exists is True

    def test_size_kb_nonexistent(self):
        artifact = BuildArtifact('/nonexistent/path.so', 'arm', ArtifactType.SHARED_OBJECT)
        assert artifact.size_kb == 0.0

    def test_size_kb_real_file(self, tmp_path):
        test_file = tmp_path / 'test.so'
        test_file.write_bytes(b'x' * 2048)
        artifact = BuildArtifact(str(test_file), 'x86_64', ArtifactType.SHARED_OBJECT)
        assert artifact.size_kb == 2.0

    def test_filename(self):
        artifact = BuildArtifact('/some/path/libtrampoline.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        assert artifact.filename == 'libtrampoline.so'

    def test_timestamp_auto_set(self):
        before = time.time()
        artifact = BuildArtifact('/tmp/test.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        after = time.time()
        assert before <= artifact.timestamp <= after


class TestBuildEvent:
    """Tests for BuildEvent dataclass."""

    def test_creation(self):
        event = BuildEvent(BuildEventType.COMPILE_START, "Compiling...")
        assert event.event_type == BuildEventType.COMPILE_START
        assert event.message == "Compiling..."
        assert event.artifact is None

    def test_with_artifact(self):
        artifact = BuildArtifact('/tmp/test.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        event = BuildEvent(BuildEventType.COMPILE_SUCCESS, "Done", artifact)
        assert event.artifact is artifact

    def test_timestamp_auto_set(self):
        event = BuildEvent(BuildEventType.INFO, "test")
        assert event.timestamp > 0


class TestBuildResult:
    """Tests for BuildResult dataclass."""

    def test_success_result(self):
        result = BuildResult(True, "OK")
        assert result.success is True
        assert result.message == "OK"
        assert result.artifacts == []
        assert result.elapsed_seconds == 0.0

    def test_failure_result(self):
        result = BuildResult(False, "Failed to compile")
        assert result.success is False

    def test_with_artifacts(self):
        artifact = BuildArtifact('/tmp/test.so', 'x86_64', ArtifactType.SHARED_OBJECT)
        result = BuildResult(True, "OK", [artifact], 1.5)
        assert len(result.artifacts) == 1
        assert result.elapsed_seconds == 1.5
