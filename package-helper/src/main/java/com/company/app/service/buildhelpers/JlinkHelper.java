package com.company.app.service.buildhelpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

public final class JlinkHelper {
  // Exit codes
  private static final int EXIT_INVALID_ARGS = 1;
  private static final int EXIT_JAR_NOT_FOUND = 2;
  private static final int EXIT_JMODS_NOT_FOUND = 3;
  private static final int EXIT_JDEPS_FAILED = 4;
  private static final int EXIT_JLINK_FAILED = 5;
  private static final int EXIT_CMD_EXECUTION_FAILED = 10;

  // Crypto modules required for Oracle connectivity
  private static final String[] REQUIRED_CRYPTO_MODULES = {"jdk.crypto.ec", "jdk.crypto.cryptoki"};

  /** Private constructor to prevent instantiation. */
  private JlinkHelper() {
    // Utility class
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    validateArguments(args);
    try {
      runJlinkBuild(args[0], args[1], args[2]);
    } catch (JlinkException e) {
      System.err.println("[ERROR] " + e.getMessage());
      System.exit(e.getExitCode());
    }
  }

  private static void validateArguments(String[] args) {
    if (args.length != 3) {
      System.err.println(
          "Usage: java ...JlinkHelper <path-to-jar> <path-to-jmods> <output-jre-dir>");
      System.exit(EXIT_INVALID_ARGS);
    }
  }

  private static void runJlinkBuild(String jarArg, String jmodsArg, String outputArg)
      throws IOException, InterruptedException, JlinkException {
    Path jarPath = Paths.get(jarArg);
    Path jmodsPath = Paths.get(jmodsArg);
    Path outputJreDir = Paths.get(outputArg);

    System.out.println("[INFO] === AIE Util Bundle Information ===");
    System.out.println("[INFO] JAR file: " + jarPath.toAbsolutePath());
    System.out.println("[INFO] JRE output: " + outputJreDir.toAbsolutePath());

    validatePaths(jarPath, jmodsPath);
    long jarSize = Files.size(jarPath);
    System.out.println("[INFO] JAR size: " + (jarSize / 1024 / 1024) + " MB");

    String modules = discoverModules(jarPath);
    modules = appendCryptoModules(modules);
    System.out.println("[INFO] jdeps modules: " + modules);
    System.out.println("[INFO] Total modules required: " + modules.split(",").length);

    buildJre(jmodsPath, modules, outputJreDir);
    copyJavaSecurityFiles(outputJreDir);
    reportResults(jarPath, outputJreDir);
  }

  private static void validatePaths(Path jarPath, Path jmodsPath) throws JlinkException {
    if (!Files.exists(jarPath)) {
      throw new JlinkException("JAR file does not exist: " + jarPath, EXIT_JAR_NOT_FOUND);
    }
    if (!Files.exists(jmodsPath) || !Files.isDirectory(jmodsPath)) {
      throw new JlinkException(
          "jmods directory does not exist: " + jmodsPath, EXIT_JMODS_NOT_FOUND);
    }
  }

  private static String discoverModules(Path jarPath)
      throws IOException, InterruptedException, JlinkException {
    String[] jdepsCmd =
        new String[] {
          "jdeps",
          "--print-module-deps",
          "--ignore-missing-deps",
          "--multi-release",
          "21",
          "--recursive",
          jarPath.toString()
        };
    System.out.println("[INFO] Running jdeps...");
    String modules = runAndCapture(jdepsCmd);

    if (modules.isBlank()) {
      throw new JlinkException("jdeps did not return any modules", EXIT_JDEPS_FAILED);
    }
    return modules.trim();
  }

  private static String appendCryptoModules(String modules) {
    StringBuilder result = new StringBuilder(modules);
    for (String cryptoModule : REQUIRED_CRYPTO_MODULES) {
      if (!modules.contains(cryptoModule)) {
        result.append(",").append(cryptoModule);
        System.out.println("[INFO] Added required crypto module: " + cryptoModule);
      }
    }
    return result.toString();
  }

  private static void buildJre(Path jmodsPath, String modules, Path outputJreDir)
      throws IOException, InterruptedException, JlinkException {
    String[] jlinkCmd =
        new String[] {
          "jlink",
          "--module-path",
          jmodsPath.toString(),
          "--add-modules",
          modules,
          "--output",
          outputJreDir.toString(),
          "--strip-debug",
          "--no-man-pages",
          "--no-header-files",
          "--compress",
          "zip-2"
        };
    System.out.println("[INFO] Running jlink...");
    int jlinkExit = runAndStream(jlinkCmd);
    if (jlinkExit != 0) {
      throw new JlinkException("jlink failed with exit code: " + jlinkExit, EXIT_JLINK_FAILED);
    }
  }

  private static void reportResults(Path jarPath, Path outputJreDir) throws IOException {
    if (Files.exists(outputJreDir)) {
      long jarSize = Files.size(jarPath);
      long jreSize = calculateDirectorySize(outputJreDir);
      System.out.println("[INFO] Custom JRE created at: " + outputJreDir);
      System.out.println("[INFO] JRE size: " + (jreSize / 1024 / 1024) + " MB");
      System.out.println(
          "[INFO] Size reduction: " + ((jarSize - jreSize) / 1024 / 1024) + " MB saved");
    }
    System.out.println("[INFO] === Bundle Information Complete ===");
  }

  private static void copyJavaSecurityFiles(Path jreDir) throws IOException {
    Path jreLibSecurity = jreDir.resolve("lib").resolve("security");
    if (!Files.exists(jreLibSecurity)) {
      Files.createDirectories(jreLibSecurity);
    }

    String javaHome =
        Objects.requireNonNullElseGet(
            System.getenv("JAVA_HOME"), () -> System.getProperty("java.home"));
    Path sourceSecurityDir = Paths.get(javaHome, "lib", "security");

    System.out.println("[INFO] Copying Java security files to JRE...");
    if (!Files.exists(sourceSecurityDir) || !Files.isDirectory(sourceSecurityDir)) {
      System.err.println("Warning: JAVA_HOME/lib/security does not exist: " + sourceSecurityDir);
      System.err.println("JAVA_HOME: " + javaHome);
      return;
    }

    System.out.println("[INFO] Copying Java security files from: " + sourceSecurityDir);
    try (Stream<Path> stream = Files.walk(sourceSecurityDir)) {
      stream
          .filter(Files::isRegularFile)
          .forEach(
              sourceFile -> {
                try {
                  Path relativePath = sourceSecurityDir.relativize(sourceFile);
                  Path targetFile = jreLibSecurity.resolve(relativePath);
                  Files.createDirectories(targetFile.getParent());
                  Files.copy(
                      sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                  System.out.println("[INFO] Copied: " + relativePath);
                } catch (IOException e) {
                  System.err.println(
                      "Warning: Could not copy " + sourceFile + ": " + e.getMessage());
                }
              });
    }
    System.out.println("[INFO] Java security files copied to: " + jreLibSecurity);
  }

  private static long calculateDirectorySize(Path dir) throws IOException {
    try (Stream<Path> stream = Files.walk(dir)) {
      return stream
          .filter(Files::isRegularFile)
          .mapToLong(
              path -> {
                try {
                  return Files.size(path);
                } catch (IOException e) {
                  return 0L;
                }
              })
          .sum();
    }
  }

  private static String runAndCapture(String[] cmd)
      throws IOException, InterruptedException, JlinkException {
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
      throw new JlinkException(
          "Command failed: " + String.join(" ", cmd), EXIT_CMD_EXECUTION_FAILED);
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
        System.out.println(line);
      }
    }
    return proc.waitFor();
  }

  // Simple exception class for jlink operations
  private static class JlinkException extends Exception {
    private final int exitCode;

    JlinkException(String message, int exitCode) {
      super(message);
      this.exitCode = exitCode;
    }

    int getExitCode() {
      return exitCode;
    }
  }
}
