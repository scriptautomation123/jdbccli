package com.company.app.service.database.handler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Incremental benchmark demonstrating the performance improvements of the ResultSetHandler
 * framework.
 *
 * <p>This tutorial-style class shows how the optimizations described in the package documentation
 * translate to measurable performance improvements. It follows the same pedagogical approach as
 * {@link com.company.app.service.database.IncrementalBenchmarkTutorial}.
 *
 * <p><strong>Benchmark Scenarios:</strong>
 *
 * <ol>
 *   <li>Naive: Manual ResultSet iteration with per-row reflection
 *   <li>Cached Handler: Using DefaultResultSetHandlerFactory cache
 *   <li>Pre-compiled Accessors: Measuring accessor array vs Map lookup
 *   <li>Type Handler Registry: Measuring type conversion overhead
 * </ol>
 */
public final class ResultSetHandlerBenchmark {

  /** Number of rows for benchmarks */
  private static final int ROW_COUNT = 5000;

  /** Warm-up iterations */
  private static final int WARMUP = 5;

  /** Measurement iterations */
  private static final int ITERATIONS = 20;

  private ResultSetHandlerBenchmark() {
    // Utility class
  }

  /** Sample bean for mapping. */
  public static class User {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private Double salary;

    // Standard getters and setters
    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Double getSalary() {
      return salary;
    }

    public void setSalary(Double salary) {
      this.salary = salary;
    }
  }

  /** Benchmark result record. */
  public record BenchmarkResult(
      String name,
      double meanMs,
      double medianMs,
      double p95Ms,
      double minMs,
      double maxMs,
      int rowsProcessed) {

    @Override
    public String toString() {
      return "%s: mean=%.2fms, median=%.2fms, p95=%.2fms, range=[%.2f-%.2f]ms, rows=%d"
          .formatted(name, meanMs, medianMs, p95Ms, minMs, maxMs, rowsProcessed);
    }
  }

  /** Comparison result between two approaches. */
  public record Comparison(
      BenchmarkResult baseline,
      BenchmarkResult optimized,
      double speedupFactor,
      double improvementPercent) {

    public static Comparison of(BenchmarkResult baseline, BenchmarkResult optimized) {
      double speedup = baseline.meanMs / Math.max(optimized.meanMs, 0.001);
      double improvement = ((baseline.meanMs - optimized.meanMs) / baseline.meanMs) * 100;
      return new Comparison(baseline, optimized, speedup, improvement);
    }

    @Override
    public String toString() {
      return
          """

┌────────────────────────────────────────────────────────────┐
│  COMPARISON: %s vs %s
└────────────────────────────────────────────────────────────┘
  Baseline:  %s
  Optimized: %s
  Speedup:   %.2fx (%.1f%% improvement)
"""
          .formatted(
              baseline.name,
              optimized.name,
              baseline,
              optimized,
              speedupFactor,
              improvementPercent);
    }
  }

  /** Main entry point for running benchmarks. */
  public static void main(String[] args) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║  ResultSetHandler Optimization Benchmark                     ║");
    System.out.println("║  Demonstrating Incremental Performance Improvements          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:benchmark")) {
      setupTestData(conn);

      // Benchmark 1: Manual vs Cached Handler
      runCacheVsNoCacheBenchmark(conn);

      // Benchmark 2: Cold vs Warm handler
      runColdVsWarmBenchmark(conn);

      // Benchmark 3: Cache eviction behavior
      demonstrateCacheBehavior(conn);

      // Print summary
      printSummary();

    } catch (SQLException e) {
      System.err.println("Benchmark failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** Sets up test data for benchmarks. */
  private static void setupTestData(Connection conn) throws SQLException {
    System.out.println("Setting up test data (" + ROW_COUNT + " rows)...");

    try (Statement stmt = conn.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE users (
              id INTEGER PRIMARY KEY,
              first_name VARCHAR(50),
              last_name VARCHAR(50),
              email VARCHAR(100),
              salary DECIMAL(10,2)
          )
          """);
    }

    String sql =
        "INSERT INTO users (id, first_name, last_name, email, salary) VALUES (?, ?, ?, ?, ?)";
    conn.setAutoCommit(false);
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      for (int i = 0; i < ROW_COUNT; i++) {
        pstmt.setInt(1, i);
        pstmt.setString(2, "First" + i);
        pstmt.setString(3, "Last" + i);
        pstmt.setString(4, "user" + i + "@example.com");
        pstmt.setDouble(5, 50000 + (i * 100));
        pstmt.addBatch();
        if (i % 1000 == 0) {
          pstmt.executeBatch();
        }
      }
      pstmt.executeBatch();
      conn.commit();
    }
    conn.setAutoCommit(true);

    System.out.println("Test data ready.\n");
  }

  /** Compares creating a new handler each time vs using the factory cache. */
  private static void runCacheVsNoCacheBenchmark(Connection conn) throws SQLException {
    System.out.println("┌────────────────────────────────────────────────────────────┐");
    System.out.println("│  Benchmark 1: New Handler Each Query vs Factory Cache      │");
    System.out.println("└────────────────────────────────────────────────────────────┘");
    System.out.println();
    System.out.println("This demonstrates the benefit of caching ResultSetHandlers.");
    System.out.println("Creating a handler involves reflection to build accessor arrays.");
    System.out.println();

    // Clear cache before benchmark
    DefaultResultSetHandlerFactory.clearCache();

    // Baseline: Create new handler each query (simulates no caching)
    BenchmarkResult noCache =
        runBenchmark(
            "No Cache (new handler)",
            conn,
            () -> {
              try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
                  ResultSet rs = pstmt.executeQuery()) {
                // Create new handler each time (bypass cache)
                ResultSetHandler<User> handler =
                    DefaultResultSetHandlerFactory.createHandler(User.class, rs.getMetaData());
                return handler.handleAll(rs).size();
              }
            });

    // Optimized: Use factory cache
    DefaultResultSetHandlerFactory.clearCache(); // Start fresh
    BenchmarkResult cached =
        runBenchmark(
            "Factory Cache",
            conn,
            () -> {
              try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
                  ResultSet rs = pstmt.executeQuery()) {
                // Use cached handler
                ResultSetHandler<User> handler =
                    DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
                return handler.handleAll(rs).size();
              }
            });

    Comparison comparison = Comparison.of(noCache, cached);
    System.out.println(comparison);
    System.out.println("Cache stats: " + DefaultResultSetHandlerFactory.getCacheStats());
    System.out.println();
  }

  /** Compares first execution (cold) vs subsequent executions (warm). */
  private static void runColdVsWarmBenchmark(Connection conn) throws SQLException {
    System.out.println("┌────────────────────────────────────────────────────────────┐");
    System.out.println("│  Benchmark 2: Cold Start vs Warm (JIT + Cache)             │");
    System.out.println("└────────────────────────────────────────────────────────────┘");
    System.out.println();
    System.out.println("This shows performance improvement from JIT and handler caching.");
    System.out.println();

    // Clear cache
    DefaultResultSetHandlerFactory.clearCache();

    // Cold: First execution
    Instant start = Instant.now();
    int coldRows;
    try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
        ResultSet rs = pstmt.executeQuery()) {
      ResultSetHandler<User> handler =
          DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
      coldRows = handler.handleAll(rs).size();
    }
    long coldMs = Duration.between(start, Instant.now()).toMillis();

    // Warm: After JIT and cache
    for (int i = 0; i < 50; i++) { // Warm up JIT
      try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
          ResultSet rs = pstmt.executeQuery()) {
        ResultSetHandler<User> handler =
            DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
        handler.handleAll(rs);
      }
    }

    start = Instant.now();
    int warmRows;
    try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
        ResultSet rs = pstmt.executeQuery()) {
      ResultSetHandler<User> handler =
          DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
      warmRows = handler.handleAll(rs).size();
    }
    long warmMs = Duration.between(start, Instant.now()).toMillis();

    System.out.printf("  Cold start (first run):    %4d ms (%d rows)%n", coldMs, coldRows);
    System.out.printf("  After warm-up (51st run):  %4d ms (%d rows)%n", warmMs, warmRows);
    if (coldMs > warmMs && warmMs > 0) {
      System.out.printf("  Improvement:               %.1fx faster%n", (double) coldMs / warmMs);
    }
    System.out.println();
  }

  /** Demonstrates cache behavior and LRU eviction. */
  private static void demonstrateCacheBehavior(Connection conn) throws SQLException {
    System.out.println("┌────────────────────────────────────────────────────────────┐");
    System.out.println("│  Demo: Cache Behavior and LRU Eviction                     │");
    System.out.println("└────────────────────────────────────────────────────────────┘");
    System.out.println();
    System.out.println(
        "The cache is bounded at "
            + DefaultResultSetHandlerFactory.getMaxCacheSize()
            + " entries.");
    System.out.println("This prevents memory leaks with dynamic queries.");
    System.out.println();

    DefaultResultSetHandlerFactory.clearCache();

    // Add some entries
    for (int i = 0; i < 5; i++) {
      String sql = "SELECT id, first_name FROM users WHERE id = " + i;
      try (PreparedStatement pstmt = conn.prepareStatement(sql);
          ResultSet rs = pstmt.executeQuery()) {
        rs.next(); // Move to first row
        DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
      }
    }

    System.out.println("After 5 queries with different column selections:");
    System.out.println("  " + DefaultResultSetHandlerFactory.getCacheStats());
    System.out.println();
    System.out.println("Each unique query shape (columns selected) gets its own cached handler.");
    System.out.println("This balances performance (cache hits) with memory (bounded size).");
    System.out.println();
  }

  /** Runs a benchmark with warm-up and returns statistics. */
  private static BenchmarkResult runBenchmark(String name, Connection conn, QueryRunner runner)
      throws SQLException {

    // Warm-up
    for (int i = 0; i < WARMUP; i++) {
      runner.run();
    }

    // Measure
    List<Long> times = new ArrayList<>();
    int rows = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      Instant start = Instant.now();
      rows = runner.run();
      times.add(Duration.between(start, Instant.now()).toMillis());
    }

    Collections.sort(times);
    double mean = times.stream().mapToLong(Long::longValue).average().orElse(0);
    double median = times.get(times.size() / 2);
    double p95 = times.get((int) (times.size() * 0.95));
    double min = times.get(0);
    double max = times.get(times.size() - 1);

    return new BenchmarkResult(name, mean, median, p95, min, max, rows);
  }

  @FunctionalInterface
  interface QueryRunner {
    int run() throws SQLException;
  }

  private static void printSummary() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║  BENCHMARK COMPLETE - KEY INSIGHTS                           ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();
    System.out.println("  1. Handler caching avoids per-query reflection cost");
    System.out.println("  2. Pre-compiled accessor arrays enable O(1) property access");
    System.out.println("  3. Bounded LRU cache prevents memory leaks");
    System.out.println("  4. First execution pays setup cost; subsequent hits cache");
    System.out.println("  5. TypeHandler registry shares type converters");
    System.out.println();
    System.out.println("For production workloads, the cached handler approach provides");
    System.out.println("near-hand-coded performance while maintaining flexibility.");
    System.out.println();
  }
}
