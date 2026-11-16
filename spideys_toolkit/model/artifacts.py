"""
Build artifacts, events, and results — value objects for the Model layer.

BuildArtifact: Represents a compiled output file (.so, .jar, etc.)
BuildEvent:    Immutable notification emitted during builds (Observer pattern)
BuildResult:   Return value from command execution

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import os
import time
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import List, Optional


class ArtifactType(Enum):
    """Types of build output artifacts."""
    SHARED_OBJECT = auto()
    JAR = auto()
    CLASS = auto()
    HEADER = auto()


class BuildEventType(Enum):
    """Event types emitted by the BuildModel during operations."""
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

    @property
    def filename(self) -> str:
        return os.path.basename(self.path)


@dataclass
class BuildEvent:
    """Immutable event emitted by the Model (Observer pattern)."""
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
