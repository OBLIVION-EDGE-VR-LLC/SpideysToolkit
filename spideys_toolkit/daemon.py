#!/usr/bin/env python3
"""
Spidey's Toolkit — Build Daemon

Run as: python3 -m spideys_toolkit.daemon

Keeps the container alive as a build server. Listens for signals
and provides a health endpoint. This replaces the `tail -f /dev/null`
entrypoint with a proper daemon that logs readiness and handles
graceful shutdown.

Usage inside Docker:
    ENTRYPOINT ["python3", "-m", "spideys_toolkit.daemon"]

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import os
import signal
import sys
import time


# Spider-Man colors
RED = '\033[91m'
BLUE = '\033[94m'
WHITE = '\033[97m'
RESET = '\033[0m'

BANNER = f"""
{RED}  ╔═══════════════════════════════════════════╗{RESET}
{RED}  ║{RESET} {BLUE}  ~ Spidey's Toolkit ~  Build Daemon     {RESET} {RED}║{RESET}
{RED}  ╚═══════════════════════════════════════════╝{RESET}
{WHITE}  With great power comes great compilation.{RESET}
"""


def main():
    """Run the build daemon — keeps container alive for docker exec."""

    print(BANNER)
    print(f"{BLUE}[daemon]{RESET} Build server ready.")
    print(f"{BLUE}[daemon]{RESET} JAVA_HOME = {os.environ.get('JAVA_HOME', 'not set')}")
    print(f"{BLUE}[daemon]{RESET} Waiting for build commands via docker exec...")
    print(f"{BLUE}[daemon]{RESET} Interactive: docker compose exec builder python3 -m spideys_toolkit")
    print(f"{BLUE}[daemon]{RESET} One-shot:    docker compose exec builder python3 -m spideys_toolkit compile x86_64")
    print()

    running = True

    def handle_signal(signum, frame):
        nonlocal running
        print(f"\n{RED}[daemon]{RESET} Received signal {signum}, shutting down gracefully...")
        running = False

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    # Keep the process alive
    while running:
        try:
            time.sleep(1)
        except KeyboardInterrupt:
            break

    print(f"{RED}[daemon]{RESET} Build server stopped.")
    sys.exit(0)


if __name__ == '__main__':
    main()
