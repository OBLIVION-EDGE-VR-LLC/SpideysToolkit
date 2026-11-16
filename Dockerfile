###############################################################################
# Spidey's Toolkit — Cross-Compilation Docker Container
#
# Central compilation machine with all cross-compiler toolchains installed.
# The cmd2 client on the host communicates with this container to compile
# shared objects for all supported architectures.
#
# Build:  docker build -t spideys-toolkit-server .
# Run:    docker-compose up -d
#
# The /output volume is shared with the host so build artifacts persist
# when the container is stopped or removed.

# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026
###############################################################################

FROM ubuntu:22.04

LABEL maintainer="Spidey's Toolkit Project"
LABEL description="Spidey's Toolkit — CNO Cross-Compilation Server"
LABEL version="1.0"

# Avoid interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------------------
# Install base development tools
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    g++ \
    make \
    scons \
    python3 \
    python3-pip \
    openjdk-17-jdk \
    maven \
    curl \
    wget \
    git \
    file \
    binutils \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Install cross-compiler toolchains for all supported architectures
# ---------------------------------------------------------------------------
RUN apt-get update && apt-get install -y --no-install-recommends \
    # ARM64 (aarch64) cross-compiler
    gcc-aarch64-linux-gnu \
    g++-aarch64-linux-gnu \
    binutils-aarch64-linux-gnu \
    # MIPS cross-compiler
    gcc-mips-linux-gnu \
    g++-mips-linux-gnu \
    binutils-mips-linux-gnu \
    # RISC-V 64-bit cross-compiler
    gcc-riscv64-linux-gnu \
    g++-riscv64-linux-gnu \
    binutils-riscv64-linux-gnu \
    # MinGW for Windows DLL cross-compilation
    gcc-mingw-w64-x86-64 \
    g++-mingw-w64-x86-64 \
    binutils-mingw-w64-x86-64 \
    # QEMU user-mode emulation for testing cross-compiled binaries
    qemu-user-static \
    && rm -rf /var/lib/apt/lists/*

# ---------------------------------------------------------------------------
# Install Python dependencies (cmd2 for the build tool)
# ---------------------------------------------------------------------------
RUN pip3 install --no-cache-dir \
    cmd2 \
    pytest

# ---------------------------------------------------------------------------
# Set JAVA_HOME
# ---------------------------------------------------------------------------
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# ---------------------------------------------------------------------------
# Create working directories
# ---------------------------------------------------------------------------
WORKDIR /workspace

# /output is the shared volume — build artifacts go here and persist on host
VOLUME ["/output"]

# /workspace is the project source (can be mounted from host or copied)
VOLUME ["/workspace"]

# ---------------------------------------------------------------------------
# Copy project files
# ---------------------------------------------------------------------------
COPY . /workspace/

# ---------------------------------------------------------------------------
# Build the Java components with Maven
# ---------------------------------------------------------------------------
RUN cd /workspace/my-app && mvn compile -q -DskipTests || true

# ---------------------------------------------------------------------------
# Entry point — the build server listens for commands via the cmd2 tool
# or runs in interactive mode
# ---------------------------------------------------------------------------

# Run the Spidey's Toolkit daemon — keeps container alive as a build server.
# The host-side client dispatches commands via docker exec.
# Interactive: docker compose exec builder python3 -m spideys_toolkit
ENTRYPOINT ["python3", "-m", "spideys_toolkit.daemon"]
