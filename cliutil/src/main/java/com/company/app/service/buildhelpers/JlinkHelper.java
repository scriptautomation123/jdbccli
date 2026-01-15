package com.company.app.service.buildhelpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class JlinkHelper {
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 3) {
            System.err.println("Usage: java ...JlinkHelper <path-to-jar> <path-to-jmods> <output-jre-dir>"); // NOSONAR
            System.exit(1);
        }
        Path jarPath = Paths.get(args[0]);
        Path jmodsPath = Paths.get(args[1]);
        Path outputJreDir = Paths.get(args[2]);

        System.out.println("[INFO] === AIE Util Bundle Information ==="); // NOSONAR
        System.out.println("[INFO] JAR file: " + jarPath.toAbsolutePath()); // NOSONAR
        System.out.println("[INFO] JRE output: " + outputJreDir.toAbsolutePath()); // NOSONAR

        if (!Files.exists(jarPath)) {
            System.err.println("JAR file does not exist: " + jarPath); // NOSONAR
            System.exit(2);
        }
        if (!Files.exists(jmodsPath) || !Files.isDirectory(jmodsPath)) {
            System.err.println("jmods directory does not exist: " + jmodsPath); // NOSONAR
            System.exit(3);
        }

        long jarSize = Files.size(jarPath);
        System.out.println("[INFO] JAR size: " + (jarSize / 1024 / 1024) + " MB"); // NOSONAR

        String[] jdepsCmd = new String[] {
                "jdeps",
                "--print-module-deps",
                "--ignore-missing-deps",
                "--multi-release", "21",
                "--recursive",
                jarPath.toString()
        };
        System.out.println("[INFO] Running jdeps..."); // NOSONAR
        String modules = runAndCapture(jdepsCmd);
        if (modules == null || modules.isBlank()) {
            System.err.println("jdeps did not return any modules. Aborting."); // NOSONAR
            System.exit(4);
        }
        modules = modules.trim();

        // Add required crypto modules if not already present
        String[] requiredCryptoModules = { "jdk.crypto.ec", "jdk.crypto.cryptoki" };
        StringBuilder modulesBuilder = new StringBuilder(modules);
        for (String cryptoModule : requiredCryptoModules) {
            if (!modules.contains(cryptoModule)) {
                modulesBuilder.append(",").append(cryptoModule);
                System.out.println("[INFO] Added required crypto module: " + cryptoModule); // NOSONAR
            }
        }
        modules = modulesBuilder.toString();
        System.out.println("[INFO] jdeps modules: " + modules); // NOSONAR

        int moduleCount = modules.split(",").length;
        System.out.println("[INFO] Total modules required: " + moduleCount); // NOSONAR

        String[] jlinkCmd = new String[] {
                "jlink",
                "--module-path", jmodsPath.toString(),
                "--add-modules", modules,
                "--output", outputJreDir.toString(),
                "--strip-debug",
                "--no-man-pages",
                "--no-header-files",
                "--compress", "zip-2"
        };
        System.out.println("[INFO] Running jlink..."); // NOSONAR
        int jlinkExit = runAndStream(jlinkCmd);
        if (jlinkExit != 0) {
            System.err.println("jlink failed with exit code: " + jlinkExit); // NOSONAR
            System.exit(5);
        }

        // Copy Java security files to JRE
        System.out.println("[INFO] Copying Java security files to JRE..."); // NOSONAR
        copyJavaSecurityFiles(outputJreDir);

        if (Files.exists(outputJreDir)) {
            long jreSize = calculateDirectorySize(outputJreDir);
            System.out.println("[INFO] Custom JRE created at: " + outputJreDir); // NOSONAR
            System.out.println("[INFO] JRE size: " + (jreSize / 1024 / 1024) + " MB"); // NOSONAR
            System.out.println("[INFO] Size reduction: " + ((jarSize - jreSize) / 1024 / 1024) + " MB saved"); // NOSONAR
        }

        System.out.println("[INFO] === Bundle Information Complete ==="); // NOSONAR
    }

    private static void copyJavaSecurityFiles(Path jreDir) throws IOException {
        Path jreLibSecurity = jreDir.resolve("lib").resolve("security");

        // Ensure the security directory exists
        if (!Files.exists(jreLibSecurity)) {
            Files.createDirectories(jreLibSecurity);
        }

        // Get JAVA_HOME from environment or system property
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            javaHome = System.getProperty("java.home");
        }

        Path sourceSecurityDir = Paths.get(javaHome, "lib", "security");

        if (Files.exists(sourceSecurityDir) && Files.isDirectory(sourceSecurityDir)) {
            System.out.println("[INFO] Copying Java security files from: " + sourceSecurityDir); // NOSONAR

            // Copy all files from source security directory
            try (Stream<Path> stream = Files.walk(sourceSecurityDir)) {
                stream.filter(Files::isRegularFile)
                        .forEach(sourceFile -> {
                            try {
                                Path relativePath = sourceSecurityDir.relativize(sourceFile);
                                Path targetFile = jreLibSecurity.resolve(relativePath);

                                // Ensure target directory exists
                                Files.createDirectories(targetFile.getParent());

                                // Copy the file
                                Files.copy(sourceFile, targetFile,
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                                System.out.println("[INFO] Copied: " + relativePath); // NOSONAR
                            } catch (IOException e) {
                                System.err.println("Warning: Could not copy " + sourceFile + ": " + e.getMessage()); // NOSONAR
                            }
                        });
            }

            System.out.println("[INFO] Java security files copied to: " + jreLibSecurity); // NOSONAR
        } else {
            System.err.println("Warning: JAVA_HOME/lib/security does not exist: " + sourceSecurityDir); // NOSONAR
            System.err.println("JAVA_HOME: " + javaHome); // NOSONAR
        }
    }

    private static long calculateDirectorySize(Path dir) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private static String runAndCapture(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        int exit = proc.waitFor();
        if (exit != 0) {
            System.err.println("Command failed: " + String.join(" ", cmd)); // NOSONAR
            System.exit(10);
        }
        return sb.toString();
    }

    private static int runAndStream(String[] cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // NOSONAR
            }
        }
        return proc.waitFor();
    }
}