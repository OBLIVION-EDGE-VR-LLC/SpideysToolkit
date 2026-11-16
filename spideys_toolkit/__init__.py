"""
Spidey's Toolkit — CNO Package Development Platform
====================================================
With great power comes great compilation.

A cmd2-based interactive build tool using MVC architecture and
Gang of Four design patterns for compiling architecture-specific
shared objects and packaging them into deployable JAR files.

Architecture:
    spideys_toolkit/
    ├── __init__.py          # Package init
    ├── __main__.py          # Entry point (python -m spideys_toolkit)
    ├── app.py               # cmd2 application (Controller)
    ├── model/               # Model layer
    │   ├── __init__.py
    │   ├── config.py        # BuildConfig
    │   ├── artifacts.py     # BuildArtifact, BuildResult, BuildEvent
    │   ├── toolchain.py     # Toolchain Strategy + Factory
    │   └── build_model.py   # BuildModel (core state + logic)
    ├── view/                # View layer
    │   ├── __init__.py
    │   └── formatters.py    # OutputFormatter Strategy implementations
    ├── controller/          # Controller layer
    │   └── __init__.py
    └── commands/            # Command pattern implementations
        ├── __init__.py
        ├── base.py          # Abstract BuildCommand
        ├── compile_cmd.py   # CompileCommand
        ├── package_cmd.py   # PackageCommand
        ├── clean_cmd.py     # CleanCommand
        └── status_cmd.py    # StatusCommand

Design Patterns (Gang of Four):
    - Command:  Each build operation is a BuildCommand object
    - Strategy: Toolchain selection + output formatting
    - Factory:  ToolchainFactory creates arch-specific toolchains
    - Observer: View subscribes to Model build events

For authorized security research and educational use only.
# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

__version__ = '1.0.0'
