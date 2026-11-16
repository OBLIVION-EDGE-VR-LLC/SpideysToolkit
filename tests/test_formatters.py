"""Unit tests for output formatters (Strategy pattern)."""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import pytest

from spideys_toolkit.view.formatters import VerboseFormatter, QuietFormatter
from spideys_toolkit.model.artifacts import BuildEvent, BuildEventType, BuildResult


class TestVerboseFormatter:
    """Tests for verbose output formatting."""

    def test_format_event_includes_message(self):
        fmt = VerboseFormatter()
        event = BuildEvent(BuildEventType.COMPILE_START, "Compiling x86_64")
        output = fmt.format_event(event)
        assert "Compiling x86_64" in output

    def test_format_event_includes_timestamp(self):
        fmt = VerboseFormatter()
        event = BuildEvent(BuildEventType.INFO, "test")
        output = fmt.format_event(event)
        assert ':' in output  # HH:MM:SS format

    def test_format_event_uses_color(self):
        fmt = VerboseFormatter()
        event = BuildEvent(BuildEventType.ERROR, "error")
        output = fmt.format_event(event)
        assert '\033[91m' in output  # Spidey Red for errors

    def test_format_result_success(self):
        fmt = VerboseFormatter()
        result = BuildResult(True, "Done", elapsed_seconds=1.5)
        output = fmt.format_result(result)
        assert "Done" in output
        assert "1.50" in output

    def test_format_result_failure(self):
        fmt = VerboseFormatter()
        result = BuildResult(False, "Compilation failed")
        output = fmt.format_result(result)
        assert "FAILED" in output
        assert "Compilation failed" in output

    def test_format_status(self):
        fmt = VerboseFormatter()
        status = {
            'project_root': '/tmp/test',
            'java_home': '/usr/lib/jvm/java-17',
            'current_arch': 'x86_64',
            'current_mode': 'debug',
            'available_archs': ['arm', 'mips', 'x86_64'],
            'artifacts': [],
            'source_files': {'trampoline': [], 'implant': []},
        }
        output = fmt.format_status(status)
        assert 'x86_64' in output
        assert 'debug' in output
        assert 'No build artifacts' in output

    def test_format_status_with_artifacts(self):
        fmt = VerboseFormatter()
        status = {
            'project_root': '/tmp/test',
            'java_home': '/usr/lib/jvm/java-17',
            'current_arch': 'x86_64',
            'current_mode': 'debug',
            'available_archs': ['x86_64'],
            'artifacts': [
                {'file': 'libtrampoline.so', 'arch': 'x86_64', 'size_kb': 42.0}
            ],
            'source_files': {'trampoline': [], 'implant': []},
        }
        output = fmt.format_status(status)
        assert 'libtrampoline.so' in output
        assert '42.0 KB' in output


class TestQuietFormatter:
    """Tests for quiet output formatting."""

    def test_format_event_suppresses_info(self):
        fmt = QuietFormatter()
        event = BuildEvent(BuildEventType.INFO, "some info")
        output = fmt.format_event(event)
        assert output == ""

    def test_format_event_shows_errors(self):
        fmt = QuietFormatter()
        event = BuildEvent(BuildEventType.ERROR, "something broke")
        output = fmt.format_event(event)
        assert "ERROR" in output
        assert "something broke" in output

    def test_format_result_success(self):
        fmt = QuietFormatter()
        result = BuildResult(True, "Done")
        assert fmt.format_result(result) == "OK"

    def test_format_result_failure(self):
        fmt = QuietFormatter()
        result = BuildResult(False, "broken")
        output = fmt.format_result(result)
        assert "FAILED" in output

    def test_format_status_concise(self):
        fmt = QuietFormatter()
        status = {
            'current_arch': 'arm',
            'current_mode': 'release',
            'artifacts': [1, 2],
        }
        output = fmt.format_status(status)
        assert 'arm' in output
        assert 'release' in output
        assert '2' in output
