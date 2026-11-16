"""
Command implementations — Command Pattern (Gang of Four).

Each command encapsulates a single build operation as an object.
Commands are registered with the cmd2 application and dispatched
by name. This pattern supports undo, logging, macro composition,
and command history — all provided by cmd2's infrastructure.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved
"""

from spideys_toolkit.commands.base import BuildCommandMixin

__all__ = ['BuildCommandMixin']
