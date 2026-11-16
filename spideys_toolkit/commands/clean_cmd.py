"""
CleanCommand — Removes all build artifacts.

Command Pattern (GoF): encapsulates the clean operation.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""


def add_clean_command(app_class):
    """Register the clean command with the cmd2 app class."""

    def do_clean(self, _):
        """Remove all build artifacts and reset state."""
        result = self._model.clean()
        output = self._formatter.format_result(result)
        self.poutput(output)

    app_class.do_clean = do_clean
    return app_class
