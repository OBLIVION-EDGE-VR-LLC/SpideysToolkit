"""
PackageCommand — Assembles multi-arch JAR from built artifacts.

Command Pattern (GoF): encapsulates the package operation.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved

"""


def add_package_command(app_class):
    """Register the package command with the cmd2 app class."""

    def do_package(self, _):
        """Assemble all built shared objects into a deployable JAR."""
        result = self._model.package_jar()
        output = self._formatter.format_result(result)
        self.poutput(output)

    app_class.do_package = do_package
    return app_class
