package com.utece.student.llpdetection;

import com.utece.student.llpdetection.predicate.ExecutionTrace;
import com.utece.student.llpdetection.predicate.Predicate;
import com.utece.student.llpdetection.predicate.PredicateViolation;
import com.utece.student.llpdetection.predicate.builtin.*;
import com.utece.student.llpdetection.predicate.event.EventCollector;
import com.utece.student.llpdetection.predicate.event.EventLog;
import com.utece.student.llpdetection.visualization.ConsoleVisualizer;
import com.utece.student.llpdetection.visualization.HtmlReportGenerator;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class LLPMain {

    private enum Mode { CLI, GUI }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String target = null;
        Mode mode = Mode.CLI;
        Set<String> predicateNames = new LinkedHashSet<>(Arrays.asList("race"));
        int maxCuts = 100_000;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--cli":
                    mode = Mode.CLI;
                    break;
                case "--gui":
                    mode = Mode.GUI;
                    break;
                case "--predicates":
                    if (i + 1 < args.length) {
                        predicateNames.clear();
                        for (String p : args[++i].split(",")) predicateNames.add(p.trim());
                    }
                    break;
                case "--max-cuts":
                    if (i + 1 < args.length) maxCuts = Integer.parseInt(args[++i]);
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return;
                default:
                    if (target == null) target = args[i];
                    break;
            }
        }

        if (target == null) {
            System.err.println("Error: No target specified.");
            printUsage();
            System.exit(1);
        }

        List<Predicate> predicates = buildPredicates(predicateNames);
        run(target, mode, predicates, maxCuts);
    }

    private static void run(String target, Mode mode, List<Predicate> predicates, int maxCuts) {
        System.out.println();
        System.out.println("  LLP Predicate Detector");
        System.out.println("  Target: " + target);
        System.out.println("  Mode:   " + mode);
        System.out.println();

        // Determine if target is a .java source file, .jar, or class name
        Runnable program;
        String displayName;

        if (target.endsWith(".java")) {
            displayName = target;
            program = compileAndLoadSource(target);
        } else if (target.endsWith(".jar")) {
            displayName = target;
            program = loadFromJar(target);
        } else {
            displayName = target;
            program = loadClass(target);
        }

        if (program == null) {
            System.err.println("Failed to load target: " + target);
            System.exit(1);
            return;
        }

        // Run with detection
        EventCollector.reset();
        EventCollector.enable();

        System.out.println("[LLP] Executing target program...");
        System.out.println("---------- Program Output ----------");
        long start = System.currentTimeMillis();

        program.run();

        long elapsed = System.currentTimeMillis() - start;
        EventCollector.disable();
        System.out.println("-------------------------------------");
        System.out.println("[LLP] Execution completed in " + elapsed + "ms");
        System.out.println("[LLP] Captured " + EventCollector.getTotalEvents()
                + " events across " + EventCollector.getThreadCount() + " threads");

        Map<Long, EventLog> logs = EventCollector.getAllLogs();

        // Fast-path detection: scan events directly (no lattice -- fast)
        System.out.println("[LLP] Running fast-path detection...");
        long detectStart = System.currentTimeMillis();

        DataRacePredicate racePredicate = new DataRacePredicate();
        List<DataRacePredicate.RaceInfo> races = new ArrayList<>();
        List<PredicateViolation> violations = new ArrayList<>();

        for (Predicate p : predicates) {
            if (p instanceof DataRacePredicate) {
                races.addAll(racePredicate.findAllRaces(logs));
            } else if (p instanceof BufferOverflowPredicate) {
                BufferOverflowPredicate bop = (BufferOverflowPredicate) p;
                List<BufferOverflowPredicate.OverflowInfo> overflows = bop.findAllOverflows(logs);
                for (BufferOverflowPredicate.OverflowInfo oi : overflows) {
                    List<com.utece.student.llpdetection.predicate.event.Event> witnesses = new ArrayList<>();
                    if (oi.arrayEvent != null) witnesses.add(oi.arrayEvent);
                    if (oi.boundsEvent != null) witnesses.add(oi.boundsEvent);
                    violations.add(new PredicateViolation(bop, null, witnesses, oi.description));
                }
            } else if (p instanceof HeapOverflowPredicate) {
                HeapOverflowPredicate hop = (HeapOverflowPredicate) p;
                List<HeapOverflowPredicate.HeapIssue> issues = hop.findAllHeapIssues(logs);
                for (HeapOverflowPredicate.HeapIssue hi : issues) {
                    violations.add(new PredicateViolation(hop, null, hi.events, hi.description));
                }
            } else if (p instanceof StackOverflowPredicate) {
                StackOverflowPredicate sop = (StackOverflowPredicate) p;
                List<StackOverflowPredicate.RecursionInfo> recursions = sop.findAllRecursionIssues(logs);
                for (StackOverflowPredicate.RecursionInfo ri : recursions) {
                    List<com.utece.student.llpdetection.predicate.event.Event> witnesses =
                            ri.events.size() > 10 ? ri.events.subList(0, 10) : ri.events;
                    violations.add(new PredicateViolation(sop, null, witnesses, ri.description));
                }
            }
        }

        System.out.println("[LLP] Detection completed in " + (System.currentTimeMillis() - detectStart) + "ms");
        System.out.println("[LLP] Found " + races.size() + " race(s), " + violations.size() + " violation(s)");

        // Save execution trace (seeds, paths, inputs)
        long seed = System.nanoTime();
        ExecutionTrace trace = ExecutionTrace.capture(
                displayName, logs, races, violations, new String[]{}, seed);

        if (!races.isEmpty() || !violations.isEmpty()) {
            try {
                String traceFileName = "llp-trace-" + System.currentTimeMillis() + ".ser";
                File traceFile = new File(traceFileName);
                trace.saveToFile(traceFile);
                System.out.println("[LLP] Execution trace saved: " + traceFile.getAbsolutePath());

                String textFileName = traceFileName.replace(".ser", ".txt");
                File textFile = new File(textFileName);
                try (java.io.PrintWriter pw = new java.io.PrintWriter(textFile)) {
                    pw.print(trace.toTextReport());
                }
                System.out.println("[LLP] Text trace saved: " + textFile.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("[LLP] Failed to save trace: " + e.getMessage());
            }
        }

        // Visualize
        switch (mode) {
            case CLI:
                ConsoleVisualizer.visualize(logs, races, violations);
                break;
            case GUI:
                try {
                    File report = HtmlReportGenerator.writeReport(logs, races, violations, displayName, seed);
                    System.out.println("[LLP] Report written to: " + report.getAbsolutePath());
                    openInBrowser(report);
                } catch (IOException e) {
                    System.err.println("Failed to write report: " + e.getMessage());
                    ConsoleVisualizer.visualize(logs, races, violations);
                }
                break;
        }
    }

    private static Runnable compileAndLoadSource(String sourcePath) {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            System.err.println("Source file not found: " + sourcePath);
            return null;
        }

        System.out.println("[LLP] Compiling " + sourcePath + "...");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.err.println("Java compiler not available. Use a JDK (not JRE).");
            return null;
        }

        // Compile to the same directory as the source
        String outputDir = sourceFile.getParent() != null ? sourceFile.getParent() : ".";
        int result = compiler.run(null, null, null,
                "-cp", System.getProperty("java.class.path"),
                "-d", outputDir,
                sourcePath);
        if (result != 0) {
            System.err.println("Compilation failed.");
            return null;
        }

        // Determine class name from file
        String className = sourceFile.getName().replace(".java", "");

        // Check if file has a package declaration
        try (Scanner scanner = new Scanner(sourceFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("package ")) {
                    String pkg = line.substring(8).replace(";", "").trim();
                    className = pkg + "." + className;
                    break;
                }
                if (line.startsWith("import") || line.startsWith("public") || line.startsWith("class")) {
                    break;
                }
            }
        } catch (Exception e) {
            // proceed with simple class name
        }

        try {
            File outDir = new File(outputDir);
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{outDir.toURI().toURL()},
                    LLPMain.class.getClassLoader());
            Class<?> clazz = loader.loadClass(className);
            Method mainMethod = clazz.getMethod("main", String[].class);
            System.out.println("[LLP] Compiled and loaded: " + className);
            return () -> {
                try {
                    mainMethod.invoke(null, (Object) new String[]{});
                } catch (Exception e) {
                    throw new RuntimeException("Target program failed: " + e.getCause(), e);
                }
            };
        } catch (Exception e) {
            System.err.println("Failed to load compiled class: " + e.getMessage());
            return null;
        }
    }

    private static Runnable loadFromJar(String jarPath) {
        File jarFile = new File(jarPath);
        if (!jarFile.exists()) {
            System.err.println("JAR file not found: " + jarPath);
            return null;
        }

        try {
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    LLPMain.class.getClassLoader());

            // Read Main-Class from manifest
            java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile);
            String mainClass = jar.getManifest().getMainAttributes().getValue("Main-Class");
            jar.close();

            if (mainClass == null) {
                System.err.println("No Main-Class found in JAR manifest.");
                return null;
            }

            Class<?> clazz = loader.loadClass(mainClass);
            Method mainMethod = clazz.getMethod("main", String[].class);
            System.out.println("[LLP] Loaded from JAR: " + mainClass);
            return () -> {
                try {
                    mainMethod.invoke(null, (Object) new String[]{});
                } catch (Exception e) {
                    throw new RuntimeException("Target program failed: " + e.getCause(), e);
                }
            };
        } catch (Exception e) {
            System.err.println("Failed to load JAR: " + e.getMessage());
            return null;
        }
    }

    private static Runnable loadClass(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Method mainMethod = clazz.getMethod("main", String[].class);
            System.out.println("[LLP] Loaded class: " + className);
            return () -> {
                try {
                    mainMethod.invoke(null, (Object) new String[]{});
                } catch (Exception e) {
                    throw new RuntimeException("Target program failed: " + e.getCause(), e);
                }
            };
        } catch (ClassNotFoundException e) {
            System.err.println("Class not found: " + className);
            System.err.println("Make sure it's on the classpath.");
            return null;
        } catch (NoSuchMethodException e) {
            System.err.println("No main(String[]) method found in: " + className);
            return null;
        }
    }

    private static List<Predicate> buildPredicates(Set<String> names) {
        List<Predicate> predicates = new ArrayList<>();
        for (String name : names) {
            switch (name.toLowerCase()) {
                case "race":
                    predicates.add(new DataRacePredicate());
                    break;
                case "atomicity":
                    predicates.add(new AtomicityViolationPredicate());
                    break;
                case "buffer":
                    predicates.add(new BufferOverflowPredicate());
                    break;
                case "heap":
                    predicates.add(new HeapOverflowPredicate());
                    break;
                case "stack":
                    predicates.add(new StackOverflowPredicate());
                    break;
                case "all":
                    predicates.add(new DataRacePredicate());
                    predicates.add(new AtomicityViolationPredicate());
                    predicates.add(new BufferOverflowPredicate());
                    predicates.add(new HeapOverflowPredicate());
                    predicates.add(new StackOverflowPredicate());
                    break;
                default:
                    System.err.println("Unknown predicate: " + name);
                    break;
            }
        }
        if (predicates.isEmpty()) {
            predicates.add(new DataRacePredicate());
        }
        return predicates;
    }

    private static void openInBrowser(File file) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("linux")) {
                pb = new ProcessBuilder("xdg-open", file.getAbsolutePath());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", file.getAbsolutePath());
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", file.getAbsolutePath());
            } else {
                System.out.println("Open the report manually: " + file.getAbsolutePath());
                return;
            }
            pb.start();
        } catch (Exception e) {
            System.out.println("Could not open browser. Open manually: " + file.getAbsolutePath());
        }
    }

    private static void printUsage() {
        System.out.println("Usage: LLPMain <target> [options]");
        System.out.println();
        System.out.println("Target can be:");
        System.out.println("  ClassName              Fully qualified class name on classpath");
        System.out.println("  path/to/File.java      Java source file (will be compiled)");
        System.out.println("  path/to/app.jar        JAR file with Main-Class manifest");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --cli                  Terminal output with ASCII timeline (default)");
        System.out.println("  --gui                  Generate HTML report with heat map");
        System.out.println("  --predicates LIST      Comma-separated: race,atomicity,buffer,heap,stack,all");
        System.out.println("  --max-cuts N           Max lattice cuts to explore (default: 100000)");
        System.out.println("  --help, -h             Show this help");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  LLPMain com.utece.student.llpdetection.examples.RacyCounter --cli");
        System.out.println("  LLPMain MyProgram.java --gui");
        System.out.println("  LLPMain app.jar --cli --predicates race,atomicity");
    }
}
