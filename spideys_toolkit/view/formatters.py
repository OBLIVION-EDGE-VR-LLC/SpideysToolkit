"""
Output Formatters — Strategy Pattern (Gang of Four).

Each formatter encapsulates a different output style. The View holds
a reference to the current formatter strategy and delegates all
rendering through it. Formatters can be swapped at runtime via the
'set format' command without restarting the application.
"""
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

import abc
import time
from typing import Dict

from spideys_toolkit.model.artifacts import BuildEvent, BuildEventType, BuildResult


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
    """Verbose output with timestamps and ANSI color coding."""

    # Spider-Man color scheme: Red, Blue, White
    SPIDEY_RED = '\033[91m'
    SPIDEY_BLUE = '\033[94m'
    SPIDEY_WHITE = '\033[97m'
    WEB_GRAY = '\033[37m'

    COLORS = {
        BuildEventType.COMPILE_START: '\033[94m',    # Spidey Blue
        BuildEventType.COMPILE_SUCCESS: '\033[92m',  # Web Green
        BuildEventType.COMPILE_FAILURE: '\033[91m',  # Spidey Red
        BuildEventType.LINK_START: '\033[94m',       # Spidey Blue
        BuildEventType.LINK_SUCCESS: '\033[92m',     # Web Green
        BuildEventType.LINK_FAILURE: '\033[91m',     # Spidey Red
        BuildEventType.PACKAGE_START: '\033[94m',    # Spidey Blue
        BuildEventType.PACKAGE_SUCCESS: '\033[92m',  # Web Green
        BuildEventType.DEPLOY_START: '\033[97m',     # Spidey White
        BuildEventType.DEPLOY_SUCCESS: '\033[92m',   # Web Green
        BuildEventType.CLEAN: '\033[97m',            # Spidey White
        BuildEventType.INFO: '\033[37m',             # Web Gray
        BuildEventType.ERROR: '\033[91m',            # Spidey Red
    }
    RESET = '\033[0m'

    def format_event(self, event: BuildEvent) -> str:
        color = self.COLORS.get(event.event_type, '')
        ts = time.strftime('%H:%M:%S', time.localtime(event.timestamp))
        return "%s[%s] %s%s" % (color, ts, event.message, self.RESET)

    def format_result(self, result: BuildResult) -> str:
        if result.success:
            return "\033[92m=== %s (%.2fs) ===\033[0m" % (
                result.message, result.elapsed_seconds)
        return "\033[91m=== FAILED: %s ===\033[0m" % result.message

    def format_status(self, status: Dict) -> str:
        lines = [
            "%s=== Spidey's Web Status ===%s" % (self.SPIDEY_RED, self.RESET),
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
    """Minimal output — errors and final results only."""

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
