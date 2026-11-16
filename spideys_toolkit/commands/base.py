"""
Base mixin for build commands — provides access to model and view.

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026 All Rights Reserved
"""


class BuildCommandMixin:
    """Mixin providing model/view access for cmd2 command methods."""

    @property
    def model(self):
        return self._model

    @property
    def view_formatter(self):
        return self._formatter
