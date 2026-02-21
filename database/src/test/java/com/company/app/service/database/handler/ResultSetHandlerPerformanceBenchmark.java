package com.company.app.service.database.handler;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.company.app.service.database.typedapi.DefaultResultSetHandlerFactory;
import com.company.app.service.database.typedapi.ResultSetHandler;

/**
 * JMH Benchmark comparing the optimized ResultSetHandler implementation against naive approaches.
 *
 * <p>This benchmark demonstrates the cumulative performance improvements from:
 *
 * <ol>
 *   <li>Caching reflection results (vs per-row reflection)
 *   <li>Array indexing (vs Map lookups)
 *   <li>Type-specific handlers (vs generic getObject())
 *   <li>Optimized string conversion (vs regex/StringBuilder)
 *   <li>Bounded LRU cache (vs unbounded cache)
 * </ol>
 *
 * <p><strong>Run with:</strong>
 *
 * <pre>
 * mvn clean test-compile exec:java \
 *   -Dexec.mainClass=org.openjdk.jmh.Main \
 *   -Dexec.classpathScope=test \
 *   -Dexec.args="ResultSetHandlerPerformanceBenchmark -f 1"
 * </pre>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(
    value = 1,
    jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class ResultSetHandlerPerformanceBenchmark {

  private Connection conn;
  private PreparedStatement queryStatement;
  private static final int ROW_COUNT = 10_000;

  @Setup(Level.Trial)
  public void setup() throws Exception {
    // Create in-memory H2 database
    conn = DriverManager.getConnection("jdbc:h2:mem:benchmark;DB_CLOSE_DELAY=-1");

    // Create test table with typical columns
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE users ("
              + "  id INTEGER PRIMARY KEY, "
              + "  first_name VARCHAR(50), "
              + "  last_name VARCHAR(50), "
              + "  email VARCHAR(100), "
              + "  salary DECIMAL(10,2), "
              + "  is_active BOOLEAN, "
              + "  created_at TIMESTAMP"
              + ")");
    }

    // Insert test data
    conn.setAutoCommit(false);
    try (PreparedStatement pstmt =
        conn.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)")) {
      for (int i = 0; i < ROW_COUNT; i++) {
        pstmt.setInt(1, i);
        pstmt.setString(2, "First" + i);
        pstmt.setString(3, "Last" + i);
        pstmt.setString(4, "user" + i + "@example.com");
        pstmt.setBigDecimal(5, new BigDecimal("50000.00"));
        pstmt.setBoolean(6, i % 2 == 0);
        pstmt.setTimestamp(7, new java.sql.Timestamp(System.currentTimeMillis()));
        pstmt.addBatch();
      }
      pstmt.executeBatch();
      conn.commit();
    }
    conn.setAutoCommit(true);

    queryStatement = conn.prepareStatement("SELECT * FROM users");
  }

  @TearDown(Level.Trial)
  public void tearDown() throws Exception {
    if (queryStatement != null) {
      queryStatement.close();
    }
    if (conn != null) {
      conn.close();
    }
  }

  /**
   * Baseline: Naive reflection per row (worst case). Demonstrates the cost of doing reflection on
   * every single row.
   */
  @Benchmark
  public void baselineNaiveReflectionPerRow(Blackhole bh) throws Exception {
    try (ResultSet rs = queryStatement.executeQuery()) {
      NaiveReflectionMapper<User> mapper = new NaiveReflectionMapper<>();
      while (rs.next()) {
        bh.consume(mapper.map(rs, User.class));
      }
    }
  }

  /**
   * Improvement 1: Cache reflection results per query. Shows the benefit of moving reflection from
   * per-row to per-query.
   */
  @Benchmark
  public void improvement1CachedReflection(Blackhole bh) throws Exception {
    try (ResultSet rs = queryStatement.executeQuery()) {
      ResultSetMetaData metaData = rs.getMetaData();
      CachedReflectionMapper<User> mapper = new CachedReflectionMapper<>(User.class, metaData);
      while (rs.next()) {
        bh.consume(mapper.map(rs));
      }
    }
  }

  /**
   * Current implementation: Fully optimized with factory caching. This is your production code with
   * all optimizations enabled.
   */
  @Benchmark
  public void optimizedWithFactoryCaching(Blackhole bh) throws Exception {
    try (ResultSet rs = queryStatement.executeQuery()) {
      ResultSetHandler<User> handler =
          DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
      while (rs.next()) {
        bh.consume(handler.handle(rs));
      }
    }
  }

  /**
   * Optimized without caching: Shows benefit of factory cache. Creates a new handler on each query
   * to measure caching impact.
   */
  @Benchmark
  public void optimizedNoCaching(Blackhole bh) throws Exception {
    try (ResultSet rs = queryStatement.executeQuery()) {
      ResultSetHandler<User> handler =
          DefaultResultSetHandlerFactory.createHandler(User.class, rs.getMetaData());
      while (rs.next()) {
        bh.consume(handler.handle(rs));
      }
    }
  }

  // ==================== Test Entity ====================

  public static class User {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private BigDecimal salary;
    private Boolean isActive;
    private java.sql.Timestamp createdAt;

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

    public BigDecimal getSalary() {
      return salary;
    }

    public void setSalary(BigDecimal salary) {
      this.salary = salary;
    }

    public Boolean getIsActive() {
      return isActive;
    }

    public void setIsActive(Boolean isActive) {
      this.isActive = isActive;
    }

    public java.sql.Timestamp getCreatedAt() {
      return createdAt;
    }

    public void setCreatedAt(java.sql.Timestamp createdAt) {
      this.createdAt = createdAt;
    }
  }

  // ==================== Naive Implementation (for comparison)
  // ====================

  /**
   * Naive mapper that does reflection on every row. This represents the "simplest possible"
   * implementation.
   */
  static class NaiveReflectionMapper<T> {
    public T map(ResultSet rs, Class<T> targetClass) throws Exception {
      T instance = targetClass.getDeclaredConstructor().newInstance();
      ResultSetMetaData metaData = rs.getMetaData();

      // FOR EVERY ROW: Do full reflection lookup
      for (int i = 1; i <= metaData.getColumnCount(); i++) {
        String columnName = metaData.getColumnLabel(i);

        // Naive string conversion (regex on every column!)
        String propertyName =
            columnName
                .toLowerCase(java.util.Locale.ENGLISH)
                .replaceAll("_", "")
                .replaceAll("\\s+", "");

        // Linear search through all methods (O(n) per column!)
        for (Method method : targetClass.getMethods()) {
          String methodName = method.getName().toLowerCase(java.util.Locale.ENGLISH);
          if (methodName.equals("set" + propertyName) && method.getParameterCount() == 1) {
            Object value = rs.getObject(i); // Generic getObject (no type optimization)
            try {
              method.invoke(instance, value);
            } catch (Exception e) {
              // Intentionally ignore - demonstrating naive approach with silent failures
            }
            break;
          }
        }
      }
      return instance;
    }
  }

  /**
   * Cached reflection mapper that pre-compiles accessor information. This is the first
   * optimization: cache reflection results per query.
   */
  static class CachedReflectionMapper<T> {
    private final Class<T> targetClass;
    private final ColumnAccessor[] accessors;

    static class ColumnAccessor {
      final Method setter;
      final int columnIndex;

      ColumnAccessor(Method setter, int columnIndex) {
        this.setter = setter;
        this.columnIndex = columnIndex;
      }
    }

    CachedReflectionMapper(Class<T> targetClass, ResultSetMetaData metaData) throws Exception {
      this.targetClass = targetClass;
      this.accessors = buildAccessors(targetClass, metaData);
    }

    private ColumnAccessor[] buildAccessors(Class<T> targetClass, ResultSetMetaData metaData)
        throws Exception {
      int columnCount = metaData.getColumnCount();
      ColumnAccessor[] result = new ColumnAccessor[columnCount];

      // Build setter map once
      Map<String, Method> setters = new HashMap<>();
      for (Method method : targetClass.getMethods()) {
        if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
          String propName = method.getName().substring(3).toLowerCase(java.util.Locale.ENGLISH);
          setters.put(propName, method);
        }
      }

      // Match columns to setters once
      for (int i = 0; i < columnCount; i++) {
        String columnName = metaData.getColumnLabel(i + 1);
        String propName = columnName.toLowerCase(java.util.Locale.ENGLISH).replace("_", "");
        Method setter = setters.get(propName);
        result[i] = new ColumnAccessor(setter, i + 1);
      }

      return result;
    }

    public T map(ResultSet rs) throws Exception {
      T instance = targetClass.getDeclaredConstructor().newInstance();

      // Fast path: iterate pre-compiled accessors
      for (ColumnAccessor accessor : accessors) {
        if (accessor.setter != null) {
          Object value = rs.getObject(accessor.columnIndex);
          try {
            accessor.setter.invoke(instance, value);
          } catch (Exception e) {
            // Intentionally ignore - demonstrating cached reflection with silent failures
          }
        }
      }

      return instance;
    }
  }
}
