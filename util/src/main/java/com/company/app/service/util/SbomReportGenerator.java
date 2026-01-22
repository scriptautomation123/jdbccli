package com.company.app.service.util;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates visual reports from CycloneDX SBOM (Software Bill of Materials) files.
 * Supports both XML and JSON formats.
 *
 * <p><strong>Features:</strong>
 * <ul>
 *   <li>Dependency tree visualization</li>
 *   <li>Transitive dependency analysis</li>
 *   <li>Version conflict detection</li>
 *   <li>License summary</li>
 *   <li>Security-relevant metadata</li>
 * </ul>
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * // From command line
 * java SbomReportGenerator target/sbom.xml
 *
 * // Programmatic
 * SbomReportGenerator.generateReport(Path.of("target/sbom.xml"), System.out);
 * }</pre>
 */
public final class SbomReportGenerator {

  /** Pattern to extract group:name@version from purl */
  private static final Pattern PURL_PATTERN = 
      Pattern.compile("pkg:maven/([^/]+)/([^@]+)@([^?]+)");

  /** Pattern to extract components from XML */
  private static final Pattern COMPONENT_PATTERN = Pattern.compile(
      "<component[^>]*>.*?<group>([^<]+)</group>.*?<name>([^<]+)</name>.*?<version>([^<]+)</version>.*?</component>",
      Pattern.DOTALL);

  /** Pattern to extract licenses */
  private static final Pattern LICENSE_PATTERN = 
      Pattern.compile("<license>.*?<id>([^<]+)</id>.*?</license>", Pattern.DOTALL);

  /** Pattern for dependencies section */
  private static final Pattern DEPENDENCY_PATTERN = 
      Pattern.compile("<dependency ref=\"([^\"]+)\">(.*?)</dependency>", Pattern.DOTALL);

  /** Pattern for nested dependency refs */
  private static final Pattern DEP_REF_PATTERN = 
      Pattern.compile("<dependency ref=\"([^\"]+)\"/>");

  private SbomReportGenerator() {
    // Utility class
  }

  /**
   * Component record for parsed SBOM entries.
   */
  public record Component(
      String group, 
      String name, 
      String version, 
      String license,
      boolean isInternal) {

    public String gav() {
      return group + ":" + name + ":" + version;
    }

    public String shortName() {
      return name + ":" + version;
    }

    /** Check if this is a project module vs external dependency */
    public static boolean isInternalGroup(String group) {
      return group != null && group.startsWith("com.company.app");
    }
  }

  /**
   * Version conflict record.
   */
  public record VersionConflict(
      String group,
      String name,
      Set<String> versions,
      String recommendation) {

    @Override
    public String toString() {
      return "%s:%s has %d versions: %s → %s".formatted(
          group, name, versions.size(), versions, recommendation);
    }
  }

  /**
   * Generates a comprehensive SBOM report.
   *
   * @param sbomPath path to sbom.xml or sbom.json
   * @param out output stream for the report
   * @throws IOException if file cannot be read
   */
  public static void generateReport(final Path sbomPath, final PrintStream out) 
      throws IOException {
    Objects.requireNonNull(sbomPath, "SBOM path cannot be null");
    Objects.requireNonNull(out, "Output stream cannot be null");

    final String content = Files.readString(sbomPath);
    final boolean isJson = sbomPath.toString().endsWith(".json");

    if (isJson) {
      out.println("⚠ JSON format detected. For best results, use sbom.xml");
      out.println("  Run: mvn cyclonedx:makeAggregateBom\n");
    }

    // Parse components
    final List<Component> components = parseComponents(content);
    final Map<String, List<String>> dependencies = parseDependencies(content);

    // Generate report sections
    printHeader(out, sbomPath, components);
    printSummary(out, components);
    printVersionConflicts(out, components);
    printDependencyTree(out, components, dependencies);
    printLicenseSummary(out, components);
    printExternalDependencies(out, components);
  }

  /**
   * Parses components from SBOM content.
   */
  private static List<Component> parseComponents(final String content) {
    final List<Component> components = new ArrayList<>();
    final Matcher matcher = COMPONENT_PATTERN.matcher(content);

    while (matcher.find()) {
      final String group = matcher.group(1);
      final String name = matcher.group(2);
      final String version = matcher.group(3);

      // Extract license if present in this component block
      final String componentBlock = matcher.group(0);
      final Matcher licenseMatcher = LICENSE_PATTERN.matcher(componentBlock);
      final String license = licenseMatcher.find() ? licenseMatcher.group(1) : "Unknown";

      components.add(new Component(
          group, name, version, license, Component.isInternalGroup(group)));
    }

    return components;
  }

  /**
   * Parses dependency relationships from SBOM content.
   */
  private static Map<String, List<String>> parseDependencies(final String content) {
    final Map<String, List<String>> deps = new HashMap<>();
    final Matcher depMatcher = DEPENDENCY_PATTERN.matcher(content);

    while (depMatcher.find()) {
      final String parent = extractArtifactId(depMatcher.group(1));
      final String children = depMatcher.group(2);

      final List<String> childList = new ArrayList<>();
      final Matcher refMatcher = DEP_REF_PATTERN.matcher(children);
      while (refMatcher.find()) {
        childList.add(extractArtifactId(refMatcher.group(1)));
      }

      if (!childList.isEmpty()) {
        deps.put(parent, childList);
      }
    }

    return deps;
  }

  /**
   * Extracts artifact identifier from purl.
   */
  private static String extractArtifactId(final String purl) {
    final Matcher m = PURL_PATTERN.matcher(purl);
    if (m.find()) {
      return m.group(1) + ":" + m.group(2) + ":" + m.group(3);
    }
    return purl;
  }

  private static void printHeader(
      final PrintStream out, final Path sbomPath, final List<Component> components) {
    out.println("╔══════════════════════════════════════════════════════════════════╗");
    out.println("║                    SBOM DEPENDENCY REPORT                        ║");
    out.println("╚══════════════════════════════════════════════════════════════════╝");
    out.println();
    out.println("📄 Source: " + sbomPath.toAbsolutePath());
    out.println("📊 Total Components: " + components.size());
    out.println();
  }

  private static void printSummary(final PrintStream out, final List<Component> components) {
    final long internal = components.stream().filter(Component::isInternal).count();
    final long external = components.size() - internal;

    out.println("┌─────────────────────────────────────────────────────────────────┐");
    out.println("│ SUMMARY                                                         │");
    out.println("├─────────────────────────────────────────────────────────────────┤");
    out.printf("│ 🏠 Internal Modules:    %3d                                     │%n", internal);
    out.printf("│ 📦 External Libraries:  %3d                                     │%n", external);
    out.println("└─────────────────────────────────────────────────────────────────┘");
    out.println();
  }

  private static void printVersionConflicts(
      final PrintStream out, final List<Component> components) {
    // Group by group:name to find multiple versions
    final Map<String, Set<String>> versionsByArtifact = new HashMap<>();

    for (final Component c : components) {
      final String key = c.group() + ":" + c.name();
      versionsByArtifact.computeIfAbsent(key, k -> new HashSet<>()).add(c.version());
    }

    final List<VersionConflict> conflicts = versionsByArtifact.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .map(e -> {
          final String[] parts = e.getKey().split(":");
          final List<String> sorted = e.getValue().stream()
              .sorted(Comparator.reverseOrder())
              .toList();
          return new VersionConflict(
              parts[0], parts[1], e.getValue(),
              "Use latest: " + sorted.get(0));
        })
        .toList();

    out.println("┌─────────────────────────────────────────────────────────────────┐");
    out.println("│ ⚠️  VERSION CONFLICTS                                            │");
    out.println("├─────────────────────────────────────────────────────────────────┤");

    if (conflicts.isEmpty()) {
      out.println("│ ✅ No version conflicts detected                                │");
    } else {
      for (final VersionConflict conflict : conflicts) {
        out.println("│ ❌ " + truncate(conflict.toString(), 62) + " │");
      }
    }
    out.println("└─────────────────────────────────────────────────────────────────┘");
    out.println();
  }

  private static void printDependencyTree(
      final PrintStream out,
      final List<Component> components,
      final Map<String, List<String>> dependencies) {

    out.println("┌─────────────────────────────────────────────────────────────────┐");
    out.println("│ 🌳 DEPENDENCY TREE                                              │");
    out.println("└─────────────────────────────────────────────────────────────────┘");
    out.println();

    // Find root modules (internal components that are not dependencies of others)
    final Set<String> allDeps = dependencies.values().stream()
        .flatMap(List::stream)
        .collect(Collectors.toSet());

    final List<Component> roots = components.stream()
        .filter(Component::isInternal)
        .filter(c -> !allDeps.contains(c.gav()))
        .toList();

    // If no clear roots, use internal modules
    final List<Component> treeRoots = roots.isEmpty() 
        ? components.stream().filter(Component::isInternal).toList()
        : roots;

    final Set<String> printed = new HashSet<>();
    for (final Component root : treeRoots) {
      printTree(out, root.gav(), dependencies, 0, printed, components);
    }
    out.println();
  }

  private static void printTree(
      final PrintStream out,
      final String artifact,
      final Map<String, List<String>> dependencies,
      final int depth,
      final Set<String> printed,
      final List<Component> components) {

    if (depth > 5) return; // Limit depth to avoid stack overflow

    final String indent = "  ".repeat(depth);
    final String prefix = depth == 0 ? "📦 " : (printed.contains(artifact) ? "├── ↩ " : "├── ");
    
    // Find component details
    final String shortName = components.stream()
        .filter(c -> c.gav().equals(artifact))
        .findFirst()
        .map(Component::shortName)
        .orElse(artifact.substring(artifact.lastIndexOf(':') + 1));

    final boolean isInternal = artifact.startsWith("com.company.app");
    final String icon = isInternal ? "🏠" : "📚";

    out.println(indent + prefix + icon + " " + shortName);

    if (printed.contains(artifact)) {
      return; // Already printed children
    }
    printed.add(artifact);

    final List<String> children = dependencies.getOrDefault(artifact, List.of());
    for (final String child : children) {
      printTree(out, child, dependencies, depth + 1, printed, components);
    }
  }

  private static void printLicenseSummary(
      final PrintStream out, final List<Component> components) {

    final Map<String, Long> licenseCounts = components.stream()
        .filter(c -> !c.isInternal())
        .collect(Collectors.groupingBy(Component::license, TreeMap::new, Collectors.counting()));

    out.println("┌─────────────────────────────────────────────────────────────────┐");
    out.println("│ 📜 LICENSE SUMMARY                                              │");
    out.println("├─────────────────────────────────────────────────────────────────┤");

    if (licenseCounts.isEmpty()) {
      out.println("│ No external licenses to report                                  │");
    } else {
      for (final var entry : licenseCounts.entrySet()) {
        out.printf("│   %-45s %5d component(s) │%n", 
            truncate(entry.getKey(), 45), entry.getValue());
      }
    }
    out.println("└─────────────────────────────────────────────────────────────────┘");
    out.println();
  }

  private static void printExternalDependencies(
      final PrintStream out, final List<Component> components) {

    final List<Component> external = components.stream()
        .filter(c -> !c.isInternal())
        .sorted(Comparator.comparing(Component::group).thenComparing(Component::name))
        .toList();

    out.println("┌─────────────────────────────────────────────────────────────────┐");
    out.println("│ 📚 EXTERNAL DEPENDENCIES                                        │");
    out.println("├────────────────────────────────────────────┬────────────────────┤");
    out.println("│ Artifact                                   │ Version            │");
    out.println("├────────────────────────────────────────────┼────────────────────┤");

    for (final Component c : external) {
      out.printf("│ %-42s │ %-18s │%n",
          truncate(c.group() + ":" + c.name(), 42),
          truncate(c.version(), 18));
    }
    out.println("└────────────────────────────────────────────┴────────────────────┘");
  }

  private static String truncate(final String s, final int maxLen) {
    if (s == null) return "";
    return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
  }

  /**
   * Main entry point for command-line usage.
   *
   * @param args command line arguments (first arg is path to sbom.xml)
   */
  public static void main(final String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java SbomReportGenerator <path-to-sbom.xml>");
      System.err.println("Example: java SbomReportGenerator target/sbom.xml");
      System.exit(1);
    }

    final Path sbomPath = Path.of(args[0]);
    if (!Files.exists(sbomPath)) {
      System.err.println("Error: File not found: " + sbomPath);
      System.exit(1);
    }

    try {
      generateReport(sbomPath, System.out);
    } catch (IOException e) {
      System.err.println("Error reading SBOM: " + e.getMessage());
      System.exit(1);
    }
  }
}
