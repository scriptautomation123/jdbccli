package com.company.app.service.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.company.app.service.domain.model.ExecutionResult;

/**
 * Experimental comparison between traditional JDBC and DuckDB for analytics workloads.
 *
 * <p><strong>DuckDB Advantages:</strong>
 *
 * <ul>
 *   <li>Columnar storage - faster aggregations
 *   <li>Embedded - no server needed
 *   <li>Parallel query execution
 *   <li>Can query Parquet/CSV directly
 *   <li>OLAP optimized
 * </ul>
 *
 * <p><strong>When to use DuckDB vs Oracle JDBC:</strong>
 *
 * <ul>
 *   <li>DuckDB: Local analytics, data exploration, file queries
 *   <li>Oracle: Transactional workloads, stored procedures, enterprise data
 * </ul>
 *
 * <p><strong>Usage:</strong>
 *
 * <pre>{@code
 * // Run comparison
 * DuckDbExperiment.runComparison();
 *
 * // Or use DuckDB directly
 * try (var conn = DuckDbExperiment.createConnection()) {
 *     var result = DuckDbExperiment.execute(conn, "SELECT * FROM 'data.parquet'");
 * }
 * }</pre>
 *
 * @see <a href="https://duckdb.org/docs/">DuckDB Documentation</a>
 */
public final class DuckDbExperiment {

  /** DuckDB in-memory connection string */
  private static final String DUCKDB_MEMORY = "jdbc:duckdb:";

  /** Sample data size for benchmarks */
  private static final int SAMPLE_ROWS = 10_000;

  private DuckDbExperiment() {
    // Utility class
  }

  /**
   * Benchmark result record.
   *
   * @param operation name of the operation
   * @param duckDbMs DuckDB execution time in milliseconds
   * @param h2Ms H2 (traditional JDBC) execution time in milliseconds
   * @param speedup DuckDB speedup factor (h2Ms / duckDbMs)
   */
  public record BenchmarkResult(String operation, long duckDbMs, long h2Ms, double speedup) {
    @Override
    public String toString() {
      return "%s: DuckDB=%dms, H2=%dms, Speedup=%.1fx"
          .formatted(operation, duckDbMs, h2Ms, speedup);
    }
  }

  /**
   * Creates an in-memory DuckDB connection. DuckDB connections are lightweight and can be created
   * frequently.
   *
   * @return new DuckDB connection
   * @throws SQLException if connection fails
   */
  public static Connection createConnection() throws SQLException {
    return DriverManager.getConnection(DUCKDB_MEMORY);
  }

  /**
   * Creates a file-based DuckDB connection (persistent).
   *
   * @param dbPath path to database file (created if not exists)
   * @return new DuckDB connection
   * @throws SQLException if connection fails
   */
  public static Connection createConnection(final String dbPath) throws SQLException {
    return DriverManager.getConnection("jdbc:duckdb:" + dbPath);
  }

  /**
   * Executes SQL and formats result using existing infrastructure. Shows DuckDB works with existing
   * SqlJdbcHelper.
   *
   * @param conn DuckDB connection
   * @param sql SQL to execute
   * @return formatted execution result
   * @throws SQLException if execution fails
   */
  public static ExecutionResult execute(final Connection conn, final String sql)
      throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      if (stmt.execute(sql)) {
        return SqlJdbcHelper.formatResultSet(stmt.getResultSet());
      }
      return ExecutionResult.success("Rows affected: " + stmt.getUpdateCount());
    }
  }

  /**
   * Demonstrates DuckDB's ability to query files directly. This is a key differentiator from
   * traditional JDBC.
   *
   * @param conn DuckDB connection
   * @param filePath path to CSV or Parquet file
   * @return execution result with data
   * @throws SQLException if query fails
   */
  public static ExecutionResult queryFile(final Connection conn, final String filePath)
      throws SQLException {
    // DuckDB auto-detects format from extension
    final String sql = "SELECT * FROM '%s' LIMIT 100".formatted(filePath);
    return execute(conn, sql);
  }

  /**
   * Runs a comparison benchmark between DuckDB and H2 (traditional row-store). Creates sample data
   * and runs analytical queries on both.
   *
   * @return list of benchmark results
   */
  public static List<BenchmarkResult> runComparison() {
    try {
      final var results = new java.util.ArrayList<BenchmarkResult>();

      // Setup both databases with same data
      try (Connection duckDb = createConnection();
          Connection h2 = DriverManager.getConnection("jdbc:h2:mem:benchmark")) {

        setupTestData(duckDb);
        setupTestData(h2);

        // Benchmark 1: Simple COUNT
        results.add(
            benchmark(
                "COUNT(*)",
                () -> runQuery(duckDb, "SELECT COUNT(*) FROM test_data"),
                () -> runQuery(h2, "SELECT COUNT(*) FROM test_data")));

        // Benchmark 2: GROUP BY aggregation (DuckDB shines here)
        results.add(
            benchmark(
                "GROUP BY + SUM",
                () ->
                    runQuery(
                        duckDb,
                        "SELECT category, SUM(amount), AVG(amount) FROM test_data GROUP BY"
                            + " category"),
                () ->
                    runQuery(
                        h2,
                        "SELECT category, SUM(amount), AVG(amount) FROM test_data GROUP BY"
                            + " category")));

        // Benchmark 3: Complex aggregation with HAVING
        results.add(
            benchmark(
                "GROUP BY + HAVING + ORDER",
                () ->
                    runQuery(
                        duckDb,
                        """
                        SELECT category, COUNT(*) as cnt, SUM(amount) as total
                        FROM test_data
                        GROUP BY category
                        HAVING SUM(amount) > 1000
                        ORDER BY total DESC
                        """),
                () ->
                    runQuery(
                        h2,
                        """
                        SELECT category, COUNT(*) as cnt, SUM(amount) as total
                        FROM test_data
                        GROUP BY category
                        HAVING SUM(amount) > 1000
                        ORDER BY total DESC
                        """)));

        // Benchmark 4: Window function (analytical query)
        results.add(
            benchmark(
                "Window Function (ROW_NUMBER)",
                () ->
                    runQuery(
                        duckDb,
                        """
                        SELECT category, amount,
                               ROW_NUMBER() OVER (PARTITION BY category ORDER BY amount DESC) as rn
                        FROM test_data
                        LIMIT 1000
                        """),
                () ->
                    runQuery(
                        h2,
                        """
                        SELECT category, amount,
                               ROW_NUMBER() OVER (PARTITION BY category ORDER BY amount DESC) as rn
                        FROM test_data
                        LIMIT 1000
                        """)));

        // Benchmark 5: String operations
        results.add(
            benchmark(
                "String LIKE + Filter",
                () ->
                    runQuery(
                        duckDb,
                        "SELECT * FROM test_data WHERE description LIKE '%test%' AND amount > 50"),
                () ->
                    runQuery(
                        h2,
                        "SELECT * FROM test_data WHERE description LIKE '%test%' AND amount >"
                            + " 50")));
      }

      return results;

    } catch (SQLException e) {
      throw new RuntimeException("Benchmark failed: " + e.getMessage(), e);
    }
  }

  /** Prints comparison results to stdout. */
  public static void printComparison() {
    System.out.println("\n=== DuckDB vs Traditional JDBC Benchmark ===");
    System.out.println("Data size: " + SAMPLE_ROWS + " rows\n");

    for (BenchmarkResult result : runComparison()) {
      System.out.println(result);
    }

    System.out.println("\n=== Key Takeaways ===");
    System.out.println("• DuckDB excels at: GROUP BY, aggregations, window functions");
    System.out.println("• Traditional JDBC better for: OLTP, stored procedures, Oracle");
    System.out.println("• DuckDB unique: Can query Parquet/CSV files directly");
  }

  /** Creates test data in the given connection. */
  private static void setupTestData(final Connection conn) throws SQLException {

    try (Statement stmt = conn.createStatement()) {
      // Create table
      stmt.execute(
          """
          CREATE TABLE test_data (
              id INTEGER,
              category VARCHAR(50),
              amount DECIMAL(10,2),
              description VARCHAR(200),
              created_date DATE
          )
          """);
    }

    // Insert sample data
    final String insertSql =
        "INSERT INTO test_data (id, category, amount, description, created_date) VALUES (?, ?, ?,"
            + " ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
      conn.setAutoCommit(false);

      final String[] categories = {"A", "B", "C", "D", "E", "F", "G", "H"};
      final java.time.LocalDate baseDate = java.time.LocalDate.of(2025, 1, 1);

      for (int i = 0; i < SAMPLE_ROWS; i++) {
        pstmt.setInt(1, i);
        pstmt.setString(2, categories[i % categories.length]);
        pstmt.setDouble(3, Math.random() * 1000);
        pstmt.setString(4, "Description test item " + i);
        pstmt.setDate(5, java.sql.Date.valueOf(baseDate.plusDays(i % 365)));

        pstmt.addBatch();

        if (i % 10000 == 0) {
          pstmt.executeBatch();
        }
      }
      pstmt.executeBatch();
      conn.commit();
      conn.setAutoCommit(true);
    }
  }

  /** Runs a query and returns execution time. */
  private static long runQuery(final Connection conn, final String sql) {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql)) {
      // Consume all results to measure full execution time
      int rowCount = 0;
      while (rs.next()) {
        rowCount++;
      }
      // Row count used to prevent optimization
      return rowCount > 0 ? 0 : -1;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  /** Benchmarks two operations and returns comparison. */
  private static BenchmarkResult benchmark(
      final String name, final Runnable duckDbOp, final Runnable h2Op) {

    // Warm up
    duckDbOp.run();
    h2Op.run();

    // Measure DuckDB (3 runs, take median-ish)
    final long duck1 = timeOp(duckDbOp);
    final long duck2 = timeOp(duckDbOp);
    final long duck3 = timeOp(duckDbOp);
    final long duckMs = (duck1 + duck2 + duck3) / 3;

    // Measure H2
    final long h2Run1 = timeOp(h2Op);
    final long h2Run2 = timeOp(h2Op);
    final long h2Run3 = timeOp(h2Op);
    final long h2Ms = (h2Run1 + h2Run2 + h2Run3) / 3;

    final double speedup = h2Ms > 0 ? (double) h2Ms / Math.max(1, duckMs) : 1.0;

    return new BenchmarkResult(name, duckMs, h2Ms, speedup);
  }

  private static long timeOp(final Runnable op) {
    final Instant start = Instant.now();
    op.run();
    return Duration.between(start, Instant.now()).toMillis();
  }

  /**
   * Example: Query a Parquet file directly with DuckDB. This is not possible with traditional JDBC!
   *
   * @param parquetPath path to parquet file
   */
  public static void demoParquetQuery(final String parquetPath) {
    System.out.println("\n=== DuckDB Parquet Query Demo ===");
    System.out.println("Querying: " + parquetPath);

    try (Connection conn = createConnection()) {
      // DuckDB can query Parquet directly - no ETL needed!
      final String sql =
              """
              SELECT *
              FROM read_parquet('%s')
              LIMIT 10
              """
              .formatted(parquetPath);

      ExecutionResult result = execute(conn, sql);
      System.out.println(result.getMessage());

    } catch (SQLException e) {
      System.err.println("Demo failed: " + e.getMessage());
    }
  }

  /**
   * Example: Query a CSV file directly with DuckDB.
   *
   * @param csvPath path to CSV file
   */
  public static void demoCsvQuery(final String csvPath) {
    System.out.println("\n=== DuckDB CSV Query Demo ===");
    System.out.println("Querying: " + csvPath);

    try (Connection conn = createConnection()) {
      // DuckDB auto-detects CSV structure
      final String sql =
              """
              SELECT *
              FROM read_csv_auto('%s')
              LIMIT 10
              """
              .formatted(csvPath);

      ExecutionResult result = execute(conn, sql);
      System.out.println(result.getMessage());

    } catch (SQLException e) {
      System.err.println("Demo failed: " + e.getMessage());
    }
  }

  /**
   * Main entry point for running experiments from command line.
   *
   * @param args command line arguments (optional: "parquet:path" or "csv:path")
   */
  public static void main(final String[] args) {
    if (args.length > 0 && args[0].startsWith("parquet:")) {
      demoParquetQuery(args[0].substring(8));
    } else if (args.length > 0 && args[0].startsWith("csv:")) {
      demoCsvQuery(args[0].substring(4));
    } else {
      printComparison();
    }
  }
}
