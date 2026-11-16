"""
StatusCommand — Shows current build state and artifacts.

Command Pattern (GoF): encapsulates the status query.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import cmd2


def add_status_command(app_class):
    """Register the status command with the cmd2 app class."""

    def do_status(self, _):
        """Show current build state, configuration, and artifacts."""
        status = self._model.get_status()
        output = self._formatter.format_status(status)
        self.poutput(output)

    app_class.do_status = do_status
    return app_class


def add_set_commands(app_class):
    """Register the set arch/mode/format commands."""

    set_parser = cmd2.Cmd2ArgumentParser()
    set_subparsers = set_parser.add_subparsers(dest='setting')

    arch_parser = set_subparsers.add_parser('arch', help='Set target architecture')
    arch_parser.add_argument('value', help='Architecture (x86_64, arm, mips)')

    mode_parser = set_subparsers.add_parser('mode', help='Set build mode')
    mode_parser.add_argument('value', help='Build mode (debug, release)')

    format_parser = set_subparsers.add_parser('format', help='Set output format')
    format_parser.add_argument('value', help='Format (verbose, quiet)')

    @cmd2.with_argparser(set_parser)
    def do_set(self, args):
        """Configure build settings: set <arch|mode|format> <value>"""
        from spideys_toolkit.model.toolchain import ToolchainFactory
        from spideys_toolkit.view.formatters import VerboseFormatter, QuietFormatter

        if args.setting == 'arch':
            try:
                ToolchainFactory.create(args.value)
                self._model.current_arch = args.value
                self.poutput("Architecture set to '%s'" % args.value)
            except ValueError as e:
                self.perror(str(e))

        elif args.setting == 'mode':
            if args.value in ('debug', 'release'):
                self._model.current_mode = args.value
                self.poutput("Build mode set to '%s'" % args.value)
            else:
                self.perror("Mode must be 'debug' or 'release'")

        elif args.setting == 'format':
            if args.value == 'verbose':
                self._formatter = VerboseFormatter()
                self.poutput("Output format set to 'verbose'")
            elif args.value == 'quiet':
                self._formatter = QuietFormatter()
                self.poutput("Output format set to 'quiet'")
            else:
                self.perror("Format must be 'verbose' or 'quiet'")

        else:
            self.perror("Unknown setting. Use: arch, mode, format")

    app_class.do_set = do_set
    return app_class
