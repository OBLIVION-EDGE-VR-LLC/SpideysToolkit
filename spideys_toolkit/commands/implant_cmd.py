"""
ImplantCommand — Compiles the implant (reverse shell) for a target architecture.

Command Pattern (GoF): encapsulates the implant compile operation.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""

import cmd2


def add_implant_command(app_class):
    """Register the implant command with the cmd2 app class."""

    implant_parser = cmd2.Cmd2ArgumentParser()
    implant_parser.add_argument(
        'arch', nargs='?', default=None,
        help='Target architecture (x86_64, arm, mips, riscv64, win_dll, darwin_dylib)'
    )
    implant_parser.add_argument(
        'mode', nargs='?', default=None,
        help='Build mode (debug, release). Default: current setting.'
    )

    @cmd2.with_argparser(implant_parser)
    def do_implant(self, args):
        """Compile the implant (reverse shell) for the target architecture.

        Examples:
            implant                    # Current arch, current mode
            implant win_dll release    # Windows DLL for sideloading
            implant darwin_dylib       # macOS dylib for shadowing
            implant arm release        # ARM shared object
        """
        result = self._model.compile_implant(args.arch, args.mode)
        output = self._formatter.format_result(result)
        self.poutput(output)

    app_class.do_implant = do_implant
    return app_class
