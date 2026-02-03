package com.company.app.service.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * INCREMENTAL BENCHMARK TUTORIAL ==============================
 *
 * <p>This class demonstrates performance benchmarking techniques like a principal engineer teaching
 * a junior engineer. We progress through increasingly sophisticated approaches.
 *
 * <p><strong>Learning Objectives:</strong>
 *
 * <ol>
 *   <li>Lesson 1: Basic Timing - Why naive timing is misleading
 *   <li>Lesson 2: JVM Warm-up - How JIT compilation affects measurements
 *   <li>Lesson 3: Statistical Measures - Mean, median, percentiles, and standard deviation
 *   <li>Lesson 4: Before/After Comparison - Measuring the impact of optimizations
 *   <li>Lesson 5: Real-world Optimization - Connection pooling, prepared statements, batching
 * </ol>
 *
 * <p><strong>Principal Engineer Tip:</strong> "Premature optimization is the root of all evil, but
 * so is ignoring performance when it matters. The key is measuring before optimizing."
 *
 * @see DuckDbExperiment for DuckDB-specific benchmarks
 */
public final class IncrementalBenchmarkTutorial {

  // ========================================================================
  // LESSON 1: BASIC TIMING - THE NAIVE APPROACH
  // ========================================================================

  /**
   * LESSON 1: Basic Timing
   *
   * <p>Junior Engineer: "I'll just wrap my code with System.currentTimeMillis()!"
   *
   * <p>Principal Engineer: "That's a start, but there are several problems:
   *
   * <ul>
   *   <li>JVM warm-up effects aren't accounted for
   *   <li>Single measurement has high variance
   *   <li>System.currentTimeMillis() has poor resolution on some systems
   * </ul>
   *
   * <p>Let's start here and improve incrementally."
   */
  public static class Lesson1BasicTiming {

    /** Basic timing - what most developers do first (and shouldn't rely on) */
    public static long measureNaively(Runnable operation) {
      // Principal Engineer Note: System.nanoTime() is more precise than currentTimeMillis()
      // for measuring elapsed time (not wall clock time)
      long start = System.nanoTime();
      operation.run();
      long end = System.nanoTime();
      return TimeUnit.NANOSECONDS.toMillis(end - start);
    }

    /** Demonstrates why naive timing is problematic */
    public static void demonstrateVariance() {
      System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
      System.out.println("║  LESSON 1: Why Naive Timing is Misleading                    ║");
      System.out.println("╚══════════════════════════════════════════════════════════════╝");
      System.out.println();
      System.out.println("Principal Engineer: \"Watch what happens with a simple operation.\"");
      System.out.println();

      // More intensive computation to show visible timing differences
      Runnable simpleTask =
          () -> {
            // Simple computation that takes measurable time
            double sum = 0;
            for (int i = 0; i < 1_000_000; i++) {
              sum += Math.sqrt(i) * Math.sin(i) * Math.cos(i);
            }
            // Prevent dead code elimination
            if (sum == Double.MAX_VALUE) System.out.println("Never printed");
          };

      System.out.println("Running 10 measurements of the SAME operation:");
      System.out.println("────────────────────────────────────────────────");

      List<Long> times = new ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        long time = measureNaively(simpleTask);
        times.add(time);
        System.out.printf("  Run %2d: %4d ms%n", i, time);
      }

      System.out.println("────────────────────────────────────────────────");
      System.out.printf(
          "  Min: %d ms, Max: %d ms%n", Collections.min(times), Collections.max(times));
      System.out.println();
      System.out.println("Notice: The first few runs are often SLOWER! This is JVM warm-up.");
      System.out.println("Junior Engineer: \"Why does performance improve over time?\"");
      System.out.println("Principal Engineer: \"The JIT compiler optimizes hot code paths.\"");
      System.out.println();
    }
  }

  // ========================================================================
  // LESSON 2: JVM WARM-UP - WHY THE FIRST RUNS DON'T COUNT
  // ========================================================================

  /**
   * LESSON 2: JVM Warm-up
   *
   * <p>Principal Engineer: "The JVM's Just-In-Time (JIT) compiler watches your code run. After
   * enough executions, it compiles frequently-run methods to native code.
   *
   * <p>This means:
   *
   * <ul>
   *   <li>First few runs: Interpreted bytecode (slow)
   *   <li>After warm-up: Native machine code (fast)
   * </ul>
   *
   * <p>Always warm up before measuring!"
   */
  public static class Lesson2JvmWarmup {

    /** Number of warm-up iterations before real measurement */
    private static final int WARMUP_ITERATIONS = 5;

    /** Number of actual measurement iterations */
    private static final int MEASUREMENT_ITERATIONS = 10;

    /**
     * Measures with proper warm-up phase.
     *
     * @param operation the operation to measure
     * @return average execution time in milliseconds after warm-up
     */
    public static double measureWithWarmup(Runnable operation) {
      // Warm-up phase: JIT compiler optimizes the code
      for (int i = 0; i < WARMUP_ITERATIONS; i++) {
        operation.run();
      }

      // Measurement phase: Now we get realistic numbers
      long totalTime = 0;
      for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
        long start = System.nanoTime();
        operation.run();
        totalTime += System.nanoTime() - start;
      }

      return TimeUnit.NANOSECONDS.toMillis(totalTime) / (double) MEASUREMENT_ITERATIONS;
    }

    /** Demonstrates the impact of warm-up */
    public static void demonstrateWarmupEffect() {
      System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
      System.out.println("║  LESSON 2: The Impact of JVM Warm-up                         ║");
      System.out.println("╚══════════════════════════════════════════════════════════════╝");
      System.out.println();

      // More intensive task to show visible timing differences
      Runnable task =
          () -> {
            double sum = 0;
            for (int i = 0; i < 500_000; i++) {
              sum += Math.sqrt(i) * Math.sin(i);
            }
            if (sum == Double.MAX_VALUE) System.out.println("Never printed");
          };

      System.out.println("Comparing: Cold Start vs Warmed Up");
      System.out.println("────────────────────────────────────────────────");

      // Cold measurement (first run)
      long coldStart = System.nanoTime();
      task.run();
      long coldTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - coldStart);

      // Warm up
      for (int i = 0; i < 100; i++) {
        task.run();
      }

      // Warm measurement
      long warmStart = System.nanoTime();
      task.run();
      long warmTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - warmStart);

      System.out.printf("  Cold start (1st run):    %4d ms%n", coldTime);
      System.out.printf("  After warm-up (101st):   %4d ms%n", warmTime);

      if (coldTime > 0 && warmTime > 0) {
        double improvement = ((double) (coldTime - warmTime) / coldTime) * 100;
        System.out.printf("  Improvement:             %.1f%%%n", improvement);
      }

      System.out.println();
      System.out.println("Principal Engineer: \"Always discard warm-up measurements!\"");
      System.out.println("Junior Engineer: \"How many warm-up iterations do I need?\"");
      System.out.println("Principal Engineer: \"It depends, but 5-10 is often enough for");
      System.out.println("                    simple operations. For JMH benchmarks, use");
      System.out.println("                    @Warmup annotation with appropriate settings.\"");
      System.out.println();
    }
  }

  // ========================================================================
  // LESSON 3: STATISTICAL MEASURES - BEYOND SIMPLE AVERAGES
  // ========================================================================

  /**
   * LESSON 3: Statistical Measures
   *
   * <p>Principal Engineer: "Averages lie. A single outlier can skew your results.
   *
   * <p>Better metrics:
   *
   * <ul>
   *   <li>Median (p50) - The middle value, ignores outliers
   *   <li>p95 - 95% of requests are faster than this
   *   <li>p99 - Important for SLAs
   *   <li>Standard deviation - How much variation exists
   * </ul>
   *
   * "
   */
  public static class Lesson3Statistics {

    /**
     * Comprehensive benchmark result with statistical measures.
     *
     * @param operationName name of the operation
     * @param measurements all timing measurements in milliseconds
     * @param mean arithmetic mean
     * @param median 50th percentile
     * @param p95 95th percentile
     * @param p99 99th percentile
     * @param stdDev standard deviation
     * @param min minimum value
     * @param max maximum value
     */
    public record BenchmarkStats(
        String operationName,
        List<Long> measurements,
        double mean,
        double median,
        double p95,
        double p99,
        double stdDev,
        long min,
        long max) {

      @Override
      public String toString() {
        return
            """
            %s:
              Mean:   %.2f ms
              Median: %.2f ms (p50)
              p95:    %.2f ms
              p99:    %.2f ms
              StdDev: %.2f ms
              Range:  %d - %d ms
            """
            .formatted(operationName, mean, median, p95, p99, stdDev, min, max);
      }

      /** Returns a compact single-line summary */
      public String toCompactString() {
        return "%s: mean=%.2fms, median=%.2fms, p95=%.2fms, p99=%.2fms"
            .formatted(operationName, mean, median, p95, p99);
      }
    }

    /**
     * Runs a comprehensive benchmark with statistical analysis.
     *
     * @param name operation name
     * @param operation the operation to benchmark
     * @param warmupIterations number of warm-up runs
     * @param measurementIterations number of measurement runs
     * @return comprehensive benchmark statistics
     */
    public static BenchmarkStats runBenchmark(
        String name, Runnable operation, int warmupIterations, int measurementIterations) {

      // Warm-up phase
      for (int i = 0; i < warmupIterations; i++) {
        operation.run();
      }

      // Measurement phase
      List<Long> measurements = new ArrayList<>(measurementIterations);
      for (int i = 0; i < measurementIterations; i++) {
        long start = System.nanoTime();
        operation.run();
        measurements.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      }

      return calculateStats(name, measurements);
    }

    /** Calculates statistical measures from raw measurements */
    public static BenchmarkStats calculateStats(String name, List<Long> measurements) {
      List<Long> sorted = new ArrayList<>(measurements);
      Collections.sort(sorted);

      double mean = measurements.stream().mapToLong(Long::longValue).average().orElse(0);
      double median = percentile(sorted, 50);
      double p95 = percentile(sorted, 95);
      double p99 = percentile(sorted, 99);
      double stdDev = calculateStdDev(measurements, mean);
      long min = sorted.get(0);
      long max = sorted.get(sorted.size() - 1);

      return new BenchmarkStats(name, measurements, mean, median, p95, p99, stdDev, min, max);
    }

    private static double percentile(List<Long> sorted, int percentile) {
      if (sorted.isEmpty()) return 0;
      int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
      return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double calculateStdDev(List<Long> values, double mean) {
      if (values.size() < 2) return 0;
      double sumSquaredDiff = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum();
      return Math.sqrt(sumSquaredDiff / (values.size() - 1));
    }

    /** Demonstrates statistical analysis */
    public static void demonstrateStatistics() {
      System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
      System.out.println("║  LESSON 3: Statistical Analysis for Benchmarks               ║");
      System.out.println("╚══════════════════════════════════════════════════════════════╝");
      System.out.println();

      System.out.println("Principal Engineer: \"Let me show you why percentiles matter.\"");
      System.out.println();

      // Create an operation with occasional slowdowns (simulating real-world variance)
      Runnable variableTask =
          () -> {
            double sum = 0;
            for (int i = 0; i < 200_000; i++) {
              sum += Math.sqrt(i) * Math.sin(i);
            }
            // Simulate occasional GC pause or contention
            if (Math.random() < 0.08) { // 8% of requests are slow
              try {
                Thread.sleep(15); // Simulated slowdown
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            if (sum == Double.MAX_VALUE) System.out.println("Never printed");
          };

      BenchmarkStats stats = runBenchmark("Variable Workload", variableTask, 5, 50);

      System.out.println("Results with simulated 5% slow requests:");
      System.out.println("────────────────────────────────────────────────");
      System.out.println(stats);

      System.out.println("Key Insight:");
      System.out.println("────────────────────────────────────────────────");
      System.out.println("Notice how p95/p99 capture the slow requests that mean hides!");
      System.out.println("For SLAs, p99 is often more important than average latency.");
      System.out.println();
    }
  }

  // ========================================================================
  // LESSON 4: BEFORE/AFTER COMPARISON - MEASURING OPTIMIZATION IMPACT
  // ========================================================================

  /**
   * LESSON 4: Before/After Comparison
   *
   * <p>Principal Engineer: "The most important benchmark compares two implementations. This tells
   * you if your optimization actually helped, and by how much."
   */
  public static class Lesson4Comparison {

    /**
     * Result of comparing two implementations.
     *
     * @param beforeStats statistics for the baseline implementation
     * @param afterStats statistics for the optimized implementation
     * @param speedupFactor how much faster (e.g., 2.0 means twice as fast)
     * @param improvement percentage improvement in mean time
     * @param isSignificant whether the improvement is statistically significant
     */
    public record ComparisonResult(
        Lesson3Statistics.BenchmarkStats beforeStats,
        Lesson3Statistics.BenchmarkStats afterStats,
        double speedupFactor,
        double improvement,
        boolean isSignificant) {

      @Override
      public String toString() {
        String significanceNote =
            isSignificant
                ? "✓ SIGNIFICANT improvement"
                : "⚠ NOT statistically significant (within noise)";

        return
            """

╔══════════════════════════════════════════════════════════════╗
║  COMPARISON RESULT                                           ║
╚══════════════════════════════════════════════════════════════╝

BEFORE (Baseline):
%s
AFTER (Optimized):
%s
SUMMARY:
────────────────────────────────────────────────────────────────
  Speedup Factor:  %.2fx
  Time Saved:      %.1f%% improvement
  Verdict:         %s
────────────────────────────────────────────────────────────────
"""
            .formatted(beforeStats, afterStats, speedupFactor, improvement, significanceNote);
      }
    }

    /**
     * Compares two implementations and determines if the difference is significant.
     *
     * @param baselineName name for the baseline implementation
     * @param baseline the baseline operation
     * @param optimizedName name for the optimized implementation
     * @param optimized the optimized operation
     * @param iterations number of measurement iterations
     * @return comparison result with statistical analysis
     */
    public static ComparisonResult compare(
        String baselineName,
        Runnable baseline,
        String optimizedName,
        Runnable optimized,
        int iterations) {

      var beforeStats = Lesson3Statistics.runBenchmark(baselineName, baseline, 10, iterations);
      var afterStats = Lesson3Statistics.runBenchmark(optimizedName, optimized, 10, iterations);

      double speedup = beforeStats.mean() / Math.max(afterStats.mean(), 0.001);
      double improvement = ((beforeStats.mean() - afterStats.mean()) / beforeStats.mean()) * 100;

      // Simple significance test: improvement should be > 2 standard deviations
      double combinedStdDev =
          Math.sqrt(Math.pow(beforeStats.stdDev(), 2) + Math.pow(afterStats.stdDev(), 2));
      double diff = beforeStats.mean() - afterStats.mean();
      boolean significant = Math.abs(diff) > 2 * combinedStdDev;

      return new ComparisonResult(beforeStats, afterStats, speedup, improvement, significant);
    }

    /** Demonstrates before/after comparison */
    public static void demonstrateComparison() {
      System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
      System.out.println("║  LESSON 4: Before/After Optimization Comparison              ║");
      System.out.println("╚══════════════════════════════════════════════════════════════╝");
      System.out.println();

      System.out.println("Principal Engineer: \"Let's compare two ways to sum numbers.\"");
      System.out.println();

      // Baseline: Simple loop with more computation
      Runnable baseline =
          () -> {
            long sum = 0;
            for (int i = 0; i < 5_000_000; i++) {
              sum += i;
            }
            // Prevent JIT from eliminating the loop entirely
            if (sum == -1) System.out.println("Never printed");
          };

      // Optimized: Using formula n*(n-1)/2
      Runnable optimized =
          () -> {
            int n = 5_000_000;
            long sum = (long) n * (n - 1) / 2;
            // Same safeguard
            if (sum == -1) System.out.println("Never printed");
          };

      System.out.println("Comparing: Loop Sum vs Mathematical Formula");
      System.out.println("────────────────────────────────────────────────");

      ComparisonResult result =
          compare("Loop Sum (baseline)", baseline, "Formula (optimized)", optimized, 30);

      System.out.println(result);

      System.out.println("Principal Engineer: \"The formula is O(1) vs O(n) for the loop.");
      System.out.println("                    This is algorithmic optimization - the best kind!\"");
      System.out.println();
    }
  }

  // ========================================================================
  // LESSON 5: REAL-WORLD DATABASE OPTIMIZATIONS
  // ========================================================================

  /**
   * LESSON 5: Real-World Database Optimizations
   *
   * <p>Principal Engineer: "Let's apply what we've learned to actual database operations. These are
   * optimizations you'll use in production code."
   */
  public static class Lesson5DatabaseOptimizations {

    private static final int BATCH_SIZE = 1000;
    private static final int TOTAL_ROWS = 5000;

    /**
     * Demonstrates real database optimizations with H2 (in-memory). We'll show: 1. Statement vs
     * PreparedStatement 2. Individual inserts vs Batch inserts 3. AutoCommit vs Transaction
     * batching
     */
    public static void demonstrateDatabaseOptimizations() {
      System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
      System.out.println("║  LESSON 5: Real-World Database Optimizations                 ║");
      System.out.println("╚══════════════════════════════════════════════════════════════╝");
      System.out.println();

      System.out.println("Principal Engineer: \"Now for the practical stuff that will");
      System.out.println("                    make your production code faster.\"");
      System.out.println();

      try (Connection conn =
          DriverManager.getConnection("jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1")) {
        setupBenchmarkTable(conn);

        // Benchmark 1: Statement vs PreparedStatement
        benchmarkStatementVsPrepared(conn);

        // Benchmark 2: Individual vs Batch inserts
        benchmarkIndividualVsBatch(conn);

        // Benchmark 3: Query optimization
        benchmarkQueryOptimization(conn);

      } catch (SQLException e) {
        System.err.println("Database error: " + e.getMessage());
      }
    }

    private static void setupBenchmarkTable(Connection conn) throws SQLException {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS benchmark_data");
        // Note: 'value' is a reserved word in H2, so we use 'amount' instead
        stmt.execute(
            """
            CREATE TABLE benchmark_data (
                id INTEGER PRIMARY KEY,
                name VARCHAR(100),
                amount DECIMAL(10,2),
                category VARCHAR(50),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        stmt.execute("CREATE INDEX idx_category ON benchmark_data(category)");
      }
    }

    private static void benchmarkStatementVsPrepared(Connection conn) throws SQLException {
      System.out.println("\n┌────────────────────────────────────────────────────────────┐");
      System.out.println("│  Optimization 1: Statement vs PreparedStatement            │");
      System.out.println("└────────────────────────────────────────────────────────────┘");
      System.out.println();

      // Using Statement (bad practice for repeated queries) - clears table each run
      Runnable usingStatement =
          () -> {
            try {
              clearTable(conn);
              for (int i = 0; i < 500; i++) {
                try (Statement stmt = conn.createStatement()) {
                  stmt.executeUpdate(
                      "INSERT INTO benchmark_data (id, name, amount, category) VALUES ("
                          + i
                          + ", 'Item "
                          + i
                          + "', "
                          + (i * 1.5)
                          + ", 'CategoryA')");
                }
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      // Using PreparedStatement (best practice) - clears table each run
      Runnable usingPrepared =
          () -> {
            try {
              clearTable(conn);
              String sql =
                  "INSERT INTO benchmark_data (id, name, amount, category) VALUES (?, ?, ?, ?)";
              try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < 500; i++) {
                  pstmt.setInt(1, i);
                  pstmt.setString(2, "Item " + i);
                  pstmt.setDouble(3, i * 1.5);
                  pstmt.setString(4, "CategoryA");
                  pstmt.executeUpdate();
                }
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      // Compare
      var comparison =
          Lesson4Comparison.compare(
              "Statement (SQL concatenation)",
              usingStatement,
              "PreparedStatement (parameterized)",
              usingPrepared,
              5);

      System.out.println(comparison);
      System.out.println("Why PreparedStatement is better:");
      System.out.println("  1. SQL injection protection (security!)");
      System.out.println("  2. Query plan caching (performance!)");
      System.out.println("  3. No string concatenation overhead");
      System.out.println();
    }

    private static void benchmarkIndividualVsBatch(Connection conn) throws SQLException {
      System.out.println("\n┌────────────────────────────────────────────────────────────┐");
      System.out.println("│  Optimization 2: Individual Inserts vs Batch Inserts       │");
      System.out.println("└────────────────────────────────────────────────────────────┘");
      System.out.println();

      // Individual inserts (common anti-pattern) - clears table each run
      Runnable individualInserts =
          () -> {
            try {
              clearTable(conn);
              String sql =
                  "INSERT INTO benchmark_data (id, name, amount, category) VALUES (?, ?, ?, ?)";
              try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < BATCH_SIZE; i++) {
                  pstmt.setInt(1, i);
                  pstmt.setString(2, "Item " + i);
                  pstmt.setDouble(3, i * 1.5);
                  pstmt.setString(4, "Category" + (i % 5));
                  pstmt.executeUpdate(); // Each insert is a separate round-trip
                }
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      // Batch inserts (optimized) - clears table each run
      Runnable batchInserts =
          () -> {
            try {
              clearTable(conn);
              String sql =
                  "INSERT INTO benchmark_data (id, name, amount, category) VALUES (?, ?, ?, ?)";
              conn.setAutoCommit(false);
              try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < BATCH_SIZE; i++) {
                  pstmt.setInt(1, i);
                  pstmt.setString(2, "Item " + i);
                  pstmt.setDouble(3, i * 1.5);
                  pstmt.setString(4, "Category" + (i % 5));
                  pstmt.addBatch();

                  if (i % 100 == 0) {
                    pstmt.executeBatch();
                  }
                }
                pstmt.executeBatch();
                conn.commit();
              } finally {
                conn.setAutoCommit(true);
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      var comparison =
          Lesson4Comparison.compare(
              "Individual Inserts", individualInserts, "Batch Inserts", batchInserts, 5);

      System.out.println(comparison);
      System.out.println("Why batching is faster:");
      System.out.println("  1. Fewer network round-trips");
      System.out.println("  2. Database can optimize bulk operations");
      System.out.println("  3. Transaction overhead is amortized");
      System.out.println();
    }

    private static void benchmarkQueryOptimization(Connection conn) throws SQLException {
      System.out.println("\n┌────────────────────────────────────────────────────────────┐");
      System.out.println("│  Optimization 3: Query Optimization with Indexes           │");
      System.out.println("└────────────────────────────────────────────────────────────┘");
      System.out.println();

      // Setup: Insert data for query benchmark
      setupQueryData(conn);

      // Query without using index (full table scan)
      Runnable fullTableScan =
          () -> {
            try {
              String sql = "SELECT * FROM benchmark_data WHERE amount > 2500";
              try (PreparedStatement pstmt = conn.prepareStatement(sql);
                  ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) count++;
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      // Query using indexed column
      Runnable indexedQuery =
          () -> {
            try {
              String sql = "SELECT * FROM benchmark_data WHERE category = 'Category2'";
              try (PreparedStatement pstmt = conn.prepareStatement(sql);
                  ResultSet rs = pstmt.executeQuery()) {
                int count = 0;
                while (rs.next()) count++;
              }
            } catch (SQLException e) {
              throw new RuntimeException(e);
            }
          };

      var comparison =
          Lesson4Comparison.compare(
              "Non-indexed column scan", fullTableScan, "Indexed column query", indexedQuery, 20);

      System.out.println(comparison);
      System.out.println("Index optimization tips:");
      System.out.println("  1. Index columns used in WHERE clauses");
      System.out.println("  2. Index columns used in JOINs");
      System.out.println("  3. Consider composite indexes for multi-column queries");
      System.out.println("  4. But don't over-index - indexes slow down writes!");
      System.out.println();
    }

    private static void clearTable(Connection conn) throws SQLException {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("DELETE FROM benchmark_data");
      }
    }

    private static void setupQueryData(Connection conn) throws SQLException {
      clearTable(conn);
      String sql = "INSERT INTO benchmark_data (id, name, amount, category) VALUES (?, ?, ?, ?)";
      conn.setAutoCommit(false);
      try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
        for (int i = 0; i < TOTAL_ROWS; i++) {
          pstmt.setInt(1, i);
          pstmt.setString(2, "Item " + i);
          pstmt.setDouble(3, i * 1.0);
          pstmt.setString(4, "Category" + (i % 5));
          pstmt.addBatch();
          if (i % 1000 == 0) pstmt.executeBatch();
        }
        pstmt.executeBatch();
        conn.commit();
      }
      conn.setAutoCommit(true);
    }
  }

  // ========================================================================
  // MAIN ENTRY POINT - RUN ALL LESSONS
  // ========================================================================

  /**
   * Runs all lessons in sequence.
   *
   * @param args command line arguments (unused)
   */
  public static void main(String[] args) {
    printHeader();

    // Run all lessons
    Lesson1BasicTiming.demonstrateVariance();
    Lesson2JvmWarmup.demonstrateWarmupEffect();
    Lesson3Statistics.demonstrateStatistics();
    Lesson4Comparison.demonstrateComparison();
    Lesson5DatabaseOptimizations.demonstrateDatabaseOptimizations();

    printSummary();
  }

  private static void printHeader() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║     INCREMENTAL BENCHMARK TUTORIAL                           ║");
    System.out.println("║     Principal Engineer Teaching Performance Testing          ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();
    System.out.println("This tutorial demonstrates benchmarking techniques progressively,");
    System.out.println("from naive timing to sophisticated statistical analysis.");
    System.out.println();
    System.out.println("Each lesson builds on the previous one, showing you how to:");
    System.out.println("  • Measure performance accurately");
    System.out.println("  • Account for JVM warm-up effects");
    System.out.println("  • Use statistical measures for reliable results");
    System.out.println("  • Compare optimizations and verify significance");
    System.out.println("  • Apply real-world database optimizations");
  }

  private static void printSummary() {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║     TUTORIAL COMPLETE - KEY TAKEAWAYS                        ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();
    System.out.println("  1. NEVER trust a single measurement");
    System.out.println("  2. ALWAYS warm up the JVM before measuring");
    System.out.println("  3. USE percentiles (p95, p99) not just averages");
    System.out.println("  4. COMPARE before and after to validate optimizations");
    System.out.println("  5. BATCH operations reduce network overhead");
    System.out.println("  6. USE PreparedStatement for repeated queries");
    System.out.println("  7. INDEX columns used in WHERE and JOIN clauses");
    System.out.println();
    System.out.println("For production benchmarks, consider JMH (Java Microbenchmark Harness)");
    System.out.println("which handles many of these concerns automatically.");
    System.out.println();
    System.out.println("Principal Engineer: \"Now go forth and measure before optimizing!\"");
    System.out.println();
  }
}
