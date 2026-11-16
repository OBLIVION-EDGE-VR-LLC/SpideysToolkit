package com.utece.student.llpdetection.instrumentation.implant;

import java.io.*;
import java.nio.file.*;

/**
 * Architecture-aware native library loader for the implant channel.
 * Extracts the correct shared object from the JAR based on os.arch
 * and loads it into the JVM process.
 *
 * Factory Method pattern: the static loadForArch() method selects
 * and loads the appropriate native library without exposing the
 * architecture detection and extraction logic to callers.
 */
public class NativeImplantLoader {

    private static boolean loaded = false;
    private static String loadedArch = null;

    /**
     * Load the native implant library for the current architecture.
     * Extracts from JAR resource path lib/{arch}/libimplant.so to
     * a temporary file and loads via System.load().
     */
    public static synchronized void loadForArch() {
        if (loaded) {
            return;
        }

        String arch = System.getProperty("os.arch", "").toLowerCase();
        String archDir;

        if (arch.contains("amd64") || arch.contains("x86_64")) {
            archDir = "x86_64";
        } else if (arch.contains("aarch64") || arch.contains("arm")) {
            archDir = "arm";
        } else if (arch.contains("mips")) {
            archDir = "mips";
        } else {
            System.out.println("[NativeImplantLoader] Unsupported architecture: " + arch);
            return;
        }

        String resourcePath = "/lib/" + archDir + "/libimplant.so";
        try {
            String tempPath = extractFromJar(resourcePath);
            if (tempPath != null) {
                System.load(tempPath);
                loaded = true;
                loadedArch = archDir;
                System.out.println("[NativeImplantLoader] Loaded implant for " + archDir);
            }
        } catch (Throwable t) {
            System.out.println("[NativeImplantLoader] Failed to load: " + t.getMessage());
        }
    }

    /**
     * Check whether the native implant library has been loaded.
     */
    public static boolean isAvailable() {
        return loaded;
    }

    /**
     * Get the architecture string of the loaded library.
     */
    public static String getLoadedArch() {
        return loadedArch;
    }

    /**
     * Extract a resource from the JAR to a temporary file.
     */
    private static String extractFromJar(String resourcePath) throws IOException {
        InputStream in = NativeImplantLoader.class.getResourceAsStream(resourcePath);
        if (in == null) {
            System.out.println("[NativeImplantLoader] Resource not found: " + resourcePath);
            return null;
        }

        Path tempFile = Files.createTempFile("llp-implant-", ".so");
        tempFile.toFile().deleteOnExit();

        try (OutputStream out = Files.newOutputStream(tempFile)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } finally {
            in.close();
        }

        // Set executable permission
        tempFile.toFile().setExecutable(true);

        return tempFile.toAbsolutePath().toString();
    }
}
