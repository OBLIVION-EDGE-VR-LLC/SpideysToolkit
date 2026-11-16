"""
CompileCommand — Compiles native shared objects for a target architecture.

Command Pattern (GoF): encapsulates the compile operation as a method
that can be invoked by name through the cmd2 shell.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import argparse


def add_compile_command(app_class):
    """Register the compile command with the cmd2 app class."""
    import cmd2

    compile_parser = cmd2.Cmd2ArgumentParser()
    compile_parser.add_argument(
        'arch', nargs='?', default=None,
        help='Target architecture (x86_64, arm, mips). Default: current setting.'
    )
    compile_parser.add_argument(
        'mode', nargs='?', default=None,
        help='Build mode (debug, release). Default: current setting.'
    )

    @cmd2.with_argparser(compile_parser)
    def do_compile(self, args):
        """Compile native shared objects for the target architecture."""
        result = self._model.compile(args.arch, args.mode)
        output = self._formatter.format_result(result)
        self.poutput(output)

    app_class.do_compile = do_compile
    return app_class
