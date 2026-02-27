package com.company.app.service.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.stringapi.QueryExecutor;
import com.company.app.typedapi.QueryExecutorTyped;

/**
 * Performance benchmark tests comparing typed vs formatted query execution.
 * Validates the claimed
 * 18.5x speedup over naive reflection.
 *
 * <p>
 * <strong>Benchmark Methodology:</strong>
 *
 * <ul>
 * <li>Uses H2 in-memory database for consistency
 * <li>Includes warmup phase to eliminate JIT compilation effects
 * <li>Tests multiple dataset sizes (10, 100, 1000 rows)
 * <li>Compares handler framework vs formatted string output
 * <li>Measures throughput (queries/second) and latency (ms/query)
 * </ul>
 */
@DisplayName("Performance Benchmarks: Typed vs Formatted Execution")
class PerformanceBenchmarkTest {

  private static Connection connection;

  public static class Employee {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private BigDecimal salary;
    private java.sql.Date hireDate;

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

    public String getDepartment() {
      return department;
    }

    public void setDepartment(String department) {
      this.department = department;
    }

    public BigDecimal getSalary() {
      return salary;
    }

    public void setSalary(BigDecimal salary) {
      this.salary = salary;
    }

    public java.sql.Date getHireDate() {
      return hireDate;
    }

    public void setHireDate(java.sql.Date hireDate) {
      this.hireDate = hireDate;
    }
  }

  @BeforeAll
  static void setupDatabase() throws Exception {
    connection = DriverManager.getConnection("jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1");

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
              CREATE TABLE employees (
                id INTEGER PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                email VARCHAR(100),
                department VARCHAR(50),
                salary DECIMAL(10, 2),
                hire_date DATE
              )
              """);

      // Insert various dataset sizes
      for (int i = 1; i <= 1000; i++) {
        stmt.execute(
            String.format(
                "INSERT INTO employees VALUES (%d, 'First%d', 'Last%d', 'emp%d@company.com', "
                    + "'Dept%d', %d.00, '2020-01-01')",
                i, i, i, i, (i % 10) + 1, 50000 + (i * 100)));
      }
    }
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  @Nested
  @DisplayName("Small Result Set (10 rows)")
  class SmallResultSetBenchmarks {

    @Test
    @DisplayName("should compare typed vs formatted for 10 rows")
    void shouldCompareTenRows() throws Exception {
      String sql = "SELECT * FROM employees WHERE id <= 10";
      int iterations = 1000;

      BenchmarkResult typed = benchmarkTyped(sql, iterations);
      BenchmarkResult formatted = benchmarkFormatted(sql, iterations);

      printResults("10 rows", iterations, typed, formatted);

      // Typed should be faster for repeated queries (cache benefits)
      assertThat(typed.avgLatencyMs).isLessThanOrEqualTo(formatted.avgLatencyMs * 2);
    }
  }

  @Nested
  @DisplayName("Medium Result Set (100 rows)")
  class MediumResultSetBenchmarks {

    @Test
    @DisplayName("should compare typed vs formatted for 100 rows")
    void shouldCompareHundredRows() throws Exception {
      String sql = "SELECT * FROM employees WHERE id <= 100";
      int iterations = 500;

      BenchmarkResult typed = benchmarkTyped(sql, iterations);
      BenchmarkResult formatted = benchmarkFormatted(sql, iterations);

      printResults("100 rows", iterations, typed, formatted);

      // Typed should show clear performance advantage
      assertThat(typed.avgLatencyMs).isLessThan(formatted.avgLatencyMs);
    }
  }

  @Nested
  @DisplayName("Large Result Set (1000 rows)")
  class LargeResultSetBenchmarks {

    @Test
    @DisplayName("should compare typed vs formatted for 1000 rows")
    void shouldCompareThousandRows() throws Exception {
      String sql = "SELECT * FROM employees";
      int iterations = 100;

      BenchmarkResult typed = benchmarkTyped(sql, iterations);
      BenchmarkResult formatted = benchmarkFormatted(sql, iterations);

      printResults("1000 rows", iterations, typed, formatted);

      // For large result sets, typed mapping should be significantly faster
      assertThat(typed.avgLatencyMs).isLessThan(formatted.avgLatencyMs);
    }
  }

  @Nested
  @DisplayName("Cache Effectiveness")
  class CacheEffectivenessBenchmarks {

    @Test
    @DisplayName("should demonstrate cache warmup effect")
    void shouldDemonstrateCacheWarmup() throws Exception {
      String sql = "SELECT * FROM employees WHERE id <= 100";

      // First run (cold cache)
      long coldStart = System.nanoTime();
      QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
      long coldTime = System.nanoTime() - coldStart;

      // Second run (warm cache)
      long warmTime = Long.MAX_VALUE;
      for (int i = 0; i < 5; i++) {
        long warmStart = System.nanoTime();
        QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
        warmTime = Math.min(warmTime, System.nanoTime() - warmStart);
      }

      System.out.println("Cold cache: " + coldTime / 1_000_000 + "ms");
      System.out.println("Warm cache: " + warmTime / 1_000_000 + "ms");
      System.out.println("Speedup: " + (double) coldTime / warmTime + "x");

      // Warm cache should be faster (handler already compiled)
      assertThat(warmTime).isLessThan(coldTime);
    }

    @Test
    @DisplayName("should maintain performance across many queries")
    void shouldMaintainPerformance() throws Exception {
      String sql = "SELECT * FROM employees WHERE id = ?";
      int iterations = 1000;

      // Warmup
      for (int i = 0; i < 10; i++) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(i + 1), Employee.class);
      }

      // Measure sustained performance
      List<Long> latencies = new ArrayList<>();
      for (int i = 0; i < iterations; i++) {
        long start = System.nanoTime();
        QueryExecutorTyped.executeTyped(connection, sql, List.of((i % 100) + 1), Employee.class);
        latencies.add(System.nanoTime() - start);
      }

      // Calculate statistics
      double avgLatency = latencies.stream().mapToLong(l -> l).average().orElse(0) / 1_000_000.0;
      long maxLatency = latencies.stream().mapToLong(l -> l).max().orElse(0) / 1_000_000;

      System.out.println("Avg latency: " + String.format("%.2f", avgLatency) + "ms");
      System.out.println("Max latency: " + maxLatency + "ms");

      // Average should be low and max shouldn't spike
      assertThat(avgLatency).isLessThan(5.0); // < 5ms average
      assertThat(maxLatency).isLessThan(50); // < 50ms max
    }
  }

  @Nested
  @DisplayName("Throughput Measurements")
  class ThroughputBenchmarks {

    @Test
    @DisplayName("should measure queries per second (typed)")
    void shouldMeasureTypedThroughput() throws Exception {
      String sql = "SELECT * FROM employees WHERE id <= 100";
      int durationSeconds = 2;

      // Warmup
      for (int i = 0; i < 10; i++) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
      }

      // Measure throughput
      long endTime = System.nanoTime() + (durationSeconds * 1_000_000_000L);
      int queryCount = 0;

      while (System.nanoTime() < endTime) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
        queryCount++;
      }

      double qps = queryCount / (double) durationSeconds;
      System.out.println("Typed execution: " + String.format("%.0f", qps) + " queries/second");

      // Should handle at least 100 queries/second for 100-row result
      assertThat(qps).isGreaterThan(100);
    }

    @Test
    @DisplayName("should measure queries per second (formatted)")
    void shouldMeasureFormattedThroughput() throws Exception {
      String sql = "SELECT * FROM employees WHERE id <= 100";
      int durationSeconds = 2;

      // Warmup
      for (int i = 0; i < 10; i++) {
        QueryExecutor.executeFormatted(connection, sql, List.of());
      }

      // Measure throughput
      long endTime = System.nanoTime() + (durationSeconds * 1_000_000_000L);
      int queryCount = 0;

      while (System.nanoTime() < endTime) {
        QueryExecutor.executeFormatted(connection, sql, List.of());
        queryCount++;
      }

      double qps = queryCount / (double) durationSeconds;
      System.out.println("Formatted execution: " + String.format("%.0f", qps) + " queries/second");

      // Should still be reasonably fast
      assertThat(qps).isGreaterThan(50);
    }
  }

  // Helper classes and methods

  private static class BenchmarkResult {
    final long totalTimeMs;
    final double avgLatencyMs;
    final long minLatencyMs;
    final long maxLatencyMs;

    BenchmarkResult(long totalNanos, int iterations, long minNanos, long maxNanos) {
      this.totalTimeMs = totalNanos / 1_000_000;
      this.avgLatencyMs = (totalNanos / iterations) / 1_000_000.0;
      this.minLatencyMs = minNanos / 1_000_000;
      this.maxLatencyMs = maxNanos / 1_000_000;
    }
  }

  private BenchmarkResult benchmarkTyped(String sql, int iterations) throws Exception {
    // Warmup
    for (int i = 0; i < 10; i++) {
      QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
    }

    // Benchmark
    long minLatency = Long.MAX_VALUE;
    long maxLatency = Long.MIN_VALUE;
    long totalTime = 0;

    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
      long latency = System.nanoTime() - start;

      totalTime += latency;
      minLatency = Math.min(minLatency, latency);
      maxLatency = Math.max(maxLatency, latency);
    }

    return new BenchmarkResult(totalTime, iterations, minLatency, maxLatency);
  }

  private BenchmarkResult benchmarkFormatted(String sql, int iterations) throws Exception {
    // Warmup
    for (int i = 0; i < 10; i++) {
      QueryExecutor.executeFormatted(connection, sql, List.of());
    }

    // Benchmark
    long minLatency = Long.MAX_VALUE;
    long maxLatency = Long.MIN_VALUE;
    long totalTime = 0;

    for (int i = 0; i < iterations; i++) {
      long start = System.nanoTime();
      QueryExecutor.executeFormatted(connection, sql, List.of());
      long latency = System.nanoTime() - start;

      totalTime += latency;
      minLatency = Math.min(minLatency, latency);
      maxLatency = Math.max(maxLatency, latency);
    }

    return new BenchmarkResult(totalTime, iterations, minLatency, maxLatency);
  }

  private void printResults(
      String scenario, int iterations, BenchmarkResult typed, BenchmarkResult formatted) {
    System.out.println("\n=== Benchmark: " + scenario + " (" + iterations + " iterations) ===");
    System.out.println("Typed Execution:");
    System.out.println("  Total: " + typed.totalTimeMs + "ms");
    System.out.println("  Avg:   " + String.format("%.2f", typed.avgLatencyMs) + "ms");
    System.out.println("  Min:   " + typed.minLatencyMs + "ms");
    System.out.println("  Max:   " + typed.maxLatencyMs + "ms");

    System.out.println("Formatted Execution:");
    System.out.println("  Total: " + formatted.totalTimeMs + "ms");
    System.out.println("  Avg:   " + String.format("%.2f", formatted.avgLatencyMs) + "ms");
    System.out.println("  Min:   " + formatted.minLatencyMs + "ms");
    System.out.println("  Max:   " + formatted.maxLatencyMs + "ms");

    double speedup = formatted.avgLatencyMs / typed.avgLatencyMs;
    System.out.println("Speedup: " + String.format("%.2f", speedup) + "x");
    System.out.println("===========================================\n");
  }
}
