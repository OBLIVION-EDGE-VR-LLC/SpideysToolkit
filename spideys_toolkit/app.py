"""
Spidey's Toolkit — cmd2-based interactive shell (Controller in MVC).

With great power comes great compilation.

This module is the Controller in the MVC architecture. It receives
user input through cmd2's command parsing infrastructure, dispatches
to Model methods for build operations, and renders output through
View formatters. The cmd2 framework provides readline integration,
command history, tab completion, scripting support, and transcript
testing out of the box.

Gang of Four patterns used:
    - Command:  Each do_* method is a Command object (cmd2 convention)
    - Strategy: Toolchain selection + output formatting
    - Factory:  ToolchainFactory creates arch-specific toolchains
    - Observer: View subscribes to Model build events

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import os
import sys

import cmd2

from spideys_toolkit.model.config import BuildConfig
from spideys_toolkit.model.build_model import BuildModel, BuildObserver
from spideys_toolkit.model.artifacts import BuildEvent
from spideys_toolkit.view.formatters import VerboseFormatter, OutputFormatter
from spideys_toolkit.commands.compile_cmd import add_compile_command
from spideys_toolkit.commands.implant_cmd import add_implant_command
from spideys_toolkit.commands.package_cmd import add_package_command
from spideys_toolkit.commands.clean_cmd import add_clean_command
from spideys_toolkit.commands.status_cmd import add_status_command, add_set_commands


class BuildEventPrinter(BuildObserver):
    """
    Observer that prints build events to the cmd2 output.
    Connects the Model (event source) to the View (formatter + output).
    """

    def __init__(self, app, formatter: OutputFormatter):
        self.app = app
        self.formatter = formatter

    def on_build_event(self, event: BuildEvent) -> None:
        text = self.formatter.format_event(event)
        if text:
            self.app.poutput(text)


@add_compile_command
@add_implant_command
@add_package_command
@add_clean_command
@add_status_command
@add_set_commands
class SpideysToolkitApp(cmd2.Cmd):
    """
    Spidey's Toolkit — Interactive CNO Package Build Tool.

    With great power comes great compilation.
    MVC Controller: receives input, dispatches to Model, renders via View.
    """

    def __init__(self, project_root: str = None):
        super().__init__(
            multiline_commands=[],
            persistent_history_file=os.path.expanduser('~/.spideys_toolkit_history'),
        )

        # Spider-Man ASCII banner
        self.intro = (
            "\n"
            "\033[91m  ____  \033[94m       _     _           _\033[0m\n"
            "\033[91m / ___| \033[94m _ __ (_) __| | ___ _   _( )___\033[0m\n"
            "\033[91m \\___ \\ \033[94m| '_ \\| |/ _` |/ _ \\ | | |// __|\033[0m\n"
            "\033[91m  ___) |\033[94m| |_) | | (_| |  __/ |_| | \\__ \\\033[0m\n"
            "\033[91m |____/ \033[94m| .__/|_|\\__,_|\\___|\\__, | |___/\033[0m\n"
            "\033[91m        \033[94m|_|    \033[97mT O O L K I T\033[94m |___/\033[0m\n"
            "\n"
            "\033[91m  With great power comes great compilation.\033[0m\n"
            "\033[94m  CNO Package Development Platform\033[0m\n"
            "\033[97m  Type 'help' for commands. Tab completion supported.\033[0m\n"
        )

        self.prompt = '\033[91mspidey\033[94m@\033[97mtoolkit\033[91m>\033[0m '

        # MVC wiring
        root = project_root or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        config = BuildConfig(root)
        self._model = BuildModel(config)
        self._formatter = VerboseFormatter()

        # Observer pattern: connect model events to output
        self._event_printer = BuildEventPrinter(self, self._formatter)
        self._model.add_observer(self._event_printer)

        # Validate configuration on startup
        warnings = config.validate()
        for w in warnings:
            self.pwarning(w)

    def do_info(self, _):
        """Show system information and design pattern summary."""
        self.poutput("""
\033[91mSpidey's Toolkit v1.0.0\033[0m
\033[94mCNO Package Development Platform\033[0m
\033[97mWith great power comes great compilation.\033[0m

\033[91mDesign Patterns (Gang of Four):\033[0m
  \033[94mCommand\033[0m   — Each build operation (compile, package, clean, status)
              is encapsulated as a cmd2 command object.
  \033[94mStrategy\033[0m  — Toolchain selection (x86_64, ARM, MIPS, RISC-V, DLL, Dylib)
              and output formatting are swappable strategies.
  \033[94mFactory\033[0m   — ToolchainFactory creates architecture-specific toolchains
              without exposing instantiation logic.
  \033[94mObserver\033[0m  — The View subscribes to Model events for real-time
              build progress updates.

\033[91mArchitecture (MVC):\033[0m
  Model      — spideys_toolkit/model/      (config, artifacts, toolchain, build_model)
  View       — spideys_toolkit/view/       (formatters)
  Controller — spideys_toolkit/app.py      (cmd2 application)
  Commands   — spideys_toolkit/commands/   (compile, package, clean, status)

\033[97mFor authorized security research and educational use only.\033[0m
""")


def main():
    """Entry point for the build tool."""
    project_root = None

    # If running from the project directory, use it
    candidates = [
        os.getcwd(),
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    ]
    for candidate in candidates:
        if os.path.isfile(os.path.join(candidate, 'SConstruct')):
            project_root = candidate
            break

    app = SpideysToolkitApp(project_root)

    # If CLI args provided, run as one-shot command
    if len(sys.argv) > 1:
        line = ' '.join(sys.argv[1:])
        app.onecmd(line)
    else:
        app.cmdloop()


if __name__ == '__main__':
    main()
