package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Scanner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.company.app.service.database.ScriptParser;
import com.company.app.service.testcontainers.ContainerFactory;
import com.company.app.service.testcontainers.DatabaseType;

/**
 * Integration tests for {@link JdbcCliTypedApi} using Testcontainers.
 *
 * <p>Supports parameterized database testing via {@code -Ddatabase} system property (postgres,
 * mysql, sqlserver, oracle). Default: postgres.
 *
 * <p>Tests end-to-end functionality via {@code library.typed()}: typed query execution, column
 * mapping, parameter binding, error propagation.
 */
@SuppressWarnings({"resource", "java:S1118", "PMD.UseUtilityClass"})
@Testcontainers
class JdbcCliTypedApiIntegrationTest {

  private static final DatabaseType DATABASE_TYPE = DatabaseType.fromSystemProperty();

  @Container
  private static final GenericContainer<?> container =
      ContainerFactory.createContainer(DATABASE_TYPE);

  private static JdbcCliLibrary library;
  private static Connection directConnection;
  private static String jdbcUrl;
  private static String username;
  private static String password;

  @BeforeAll
  static void setupLibrary() throws Exception {
    jdbcUrl = ContainerFactory.getJdbcUrl(container, DATABASE_TYPE);
    username = ContainerFactory.getUsername(container, DATABASE_TYPE);
    password = ContainerFactory.getPassword(container, DATABASE_TYPE);

    library = JdbcCliLibrary.withPassword(password);

    directConnection = DriverManager.getConnection(jdbcUrl, username, password);

    String schemaResource = DATABASE_TYPE.getSchemaResource();
    try (InputStream is =
        JdbcCliTypedApiIntegrationTest.class.getClassLoader().getResourceAsStream(schemaResource)) {
      if (is == null) {
        throw new IllegalStateException("Schema resource not found: " + schemaResource);
      }
      String schema = new Scanner(is, StandardCharsets.UTF_8).useDelimiter("\\A").next();
      executeStatements(directConnection, schema);
    }
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (directConnection != null && !directConnection.isClosed()) {
      directConnection.close();
    }
  }

  private static void executeStatements(final Connection connection, final String sql)
      throws SQLException {
    ScriptParser.ParsedScript parsed = ScriptParser.parseContent(sql, "inline-schema");
    try (Statement stmt = connection.createStatement()) {
      for (String statement : parsed.statements()) {
        stmt.execute(statement);
      }
    }
  }

  private static String getDatabaseName() {
    return DATABASE_TYPE.name().toLowerCase();
  }

  private static Object convertBoolean(final boolean bool) {
    return switch (DATABASE_TYPE) {
      case POSTGRES -> bool;
      case MYSQL, SQLSERVER -> bool ? 1 : 0;
      case ORACLE -> bool ? "Y" : "N";
    };
  }

  // =========================================================================
  // Test beans
  // =========================================================================

  public static class Employee {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private BigDecimal salary;
    private Date hireDate;
    private Boolean isActive;

    public Integer getId() {
      return id;
    }

    public void setId(final Integer id) {
      this.id = id;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(final String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(final String lastName) {
      this.lastName = lastName;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(final String email) {
      this.email = email;
    }

    public String getDepartment() {
      return department;
    }

    public void setDepartment(final String department) {
      this.department = department;
    }

    public BigDecimal getSalary() {
      return salary;
    }

    public void setSalary(final BigDecimal salary) {
      this.salary = salary;
    }

    public Date getHireDate() {
      return hireDate;
    }

    public void setHireDate(final Date hireDate) {
      this.hireDate = hireDate;
    }

    public Boolean getIsActive() {
      return isActive;
    }

    public void setIsActive(final Boolean isActive) {
      this.isActive = isActive;
    }
  }

  public static class Department {
    private Integer deptId;
    private String deptName;
    private String location;

    public Integer getDeptId() {
      return deptId;
    }

    public void setDeptId(final Integer deptId) {
      this.deptId = deptId;
    }

    public String getDeptName() {
      return deptName;
    }

    public void setDeptName(final String deptName) {
      this.deptName = deptName;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(final String location) {
      this.location = location;
    }
  }

  // =========================================================================
  // runSqlTypedApi() — list overload
  // =========================================================================

  @Nested
  @DisplayName("runSqlTypedApi() — list")
  class RunSqlTypedApiListTests {

    @Test
    @DisplayName("should query all employees")
    void shouldQueryAllEmployees() {
      String sql = "SELECT * FROM employees ORDER BY id";

      List<Employee> employees =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(), Employee.class, null);

      assertThat(employees)
          .hasSize(5)
          .extracting(Employee::getFirstName)
          .containsExactly("Alice", "Bob", "Charlie", "Diana", "Eve");
    }

    @Test
    @DisplayName("should query with WHERE clause and parameters")
    void shouldQueryWithParameters() {
      String sql = "SELECT * FROM employees WHERE department = ? AND is_active = ?";

      List<Employee> engineers =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(),
                  jdbcUrl,
                  username,
                  sql,
                  List.of("Engineering", convertBoolean(true)),
                  Employee.class,
                  null);

      assertThat(engineers)
          .hasSize(3)
          .extracting(Employee::getFirstName)
          .containsExactlyInAnyOrder("Alice", "Charlie", "Eve");
    }

    @Test
    @DisplayName("should handle salary range query")
    void shouldQuerySalaryRange() {
      String sql = "SELECT * FROM employees WHERE salary BETWEEN ? AND ? ORDER BY salary";

      List<Employee> midRangeSalaries =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(),
                  jdbcUrl,
                  username,
                  sql,
                  List.of(80000, 100000),
                  Employee.class,
                  null);

      assertThat(midRangeSalaries)
          .hasSize(2)
          .extracting(Employee::getFirstName)
          .containsExactly("Diana", "Alice");
    }

    @Test
    @DisplayName("should return empty list for no matches")
    void shouldReturnEmptyForNoMatches() {
      String sql = "SELECT * FROM employees WHERE email = ?";

      List<Employee> result =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(),
                  jdbcUrl,
                  username,
                  sql,
                  List.of("nonexistent@example.com"),
                  Employee.class,
                  null);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should map underscore column names to camelCase setters")
    void shouldMapColumnNames() {
      String sql = "SELECT first_name, last_name, hire_date FROM employees WHERE id = 1";

      List<Employee> result =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(), Employee.class, null);

      Employee alice = result.getFirst();
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
      assertThat(alice.getHireDate()).isEqualTo(Date.valueOf("2020-01-15"));
    }

    @Test
    @DisplayName("should handle complex types (BigDecimal, Date, Boolean)")
    void shouldHandleComplexTypes() {
      String sql = "SELECT salary, hire_date, is_active FROM employees WHERE id = 3";

      List<Employee> result =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(), Employee.class, null);

      Employee charlie = result.getFirst();
      assertThat(charlie.getSalary()).isEqualByComparingTo("110000.00");
      assertThat(charlie.getHireDate()).isEqualTo(Date.valueOf("2019-11-10"));
      assertThat(charlie.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("should work with departments table")
    void shouldWorkWithDifferentTable() {
      String sql = "SELECT * FROM departments ORDER BY dept_id";

      List<Department> departments =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(), Department.class, null);

      assertThat(departments)
          .hasSize(3)
          .extracting(Department::getDeptName)
          .containsExactly("Engineering", "Sales", "HR");
    }

    @Test
    @DisplayName("should handle database-specific LIMIT/FETCH FIRST syntax")
    void shouldHandleTopNQueries() {
      String sql =
          switch (DATABASE_TYPE) {
            case ORACLE ->
                """
                SELECT e.first_name, e.last_name, e.department
                FROM employees e
                WHERE e.is_active = ?
                ORDER BY e.salary DESC
                FETCH FIRST 2 ROWS ONLY
                """;
            case SQLSERVER ->
                """
                SELECT TOP 2 e.first_name, e.last_name, e.department
                FROM employees e
                WHERE e.is_active = ?
                ORDER BY e.salary DESC
                """;
            case POSTGRES, MYSQL ->
                """
                SELECT e.first_name, e.last_name, e.department
                FROM employees e
                WHERE e.is_active = ?
                ORDER BY e.salary DESC
                LIMIT 2
                """;
          };

      List<Employee> topEarners =
          library
              .typed()
              .runSqlTypedApi(
                  getDatabaseName(),
                  jdbcUrl,
                  username,
                  sql,
                  List.of(convertBoolean(true)),
                  Employee.class,
                  null);

      assertThat(topEarners)
          .hasSize(2)
          .extracting(Employee::getFirstName)
          .containsExactly("Charlie", "Eve");
    }

    @Test
    @DisplayName("should handle aggregate COUNT via direct JDBC (typed API requires JavaBean)")
    void shouldHandleAggregateQueriesViaDirectJdbc() throws SQLException {
      String sql = "SELECT department, COUNT(*) as count FROM employees GROUP BY department";

      try (var stmt = directConnection.prepareStatement(sql);
          var rs = stmt.executeQuery()) {

        int engineeringCount = 0;
        while (rs.next()) {
          if ("Engineering".equals(rs.getString("department"))) {
            engineeringCount = rs.getInt("count");
          }
        }

        assertThat(engineeringCount).isEqualTo(3);
      }
    }
  }

  // =========================================================================
  // runSqlTypedApi() — single-result overload
  // =========================================================================

  @Nested
  @DisplayName("runSqlTypedApi() — single result")
  class RunSqlTypedApiSingleResultTests {

    @Test
    @DisplayName("should return single object for unique query")
    void shouldReturnSingleObject() {
      String sql = "SELECT * FROM employees WHERE email = ?";

      Employee alice =
          library
              .typed()
              .runSqlSingleTypedApi(
                  getDatabaseName(),
                  jdbcUrl,
                  username,
                  sql,
                  List.of("alice@example.com"),
                  Employee.class,
                  null);

      assertThat(alice).isNotNull();
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
      assertThat(alice.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("should return null when no rows found")
    void shouldReturnNullForNoRows() {
      String sql = "SELECT * FROM employees WHERE id = ?";

      Employee result =
          library
              .typed()
              .runSqlSingleTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(999), Employee.class, null);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should throw IllegalStateException when multiple rows found")
    @SuppressWarnings("java:S5778")
    void shouldThrowForMultipleRows() {
      String sql = "SELECT * FROM employees WHERE department = ?";
      String dbName = getDatabaseName();
      String dbUrl = jdbcUrl;
      String dbUser = username;

      assertThatThrownBy(
              () ->
                  library
                      .typed()
                      .runSqlSingleTypedApi(
                          dbName, dbUrl, dbUser, sql, List.of("Engineering"), Employee.class, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Query returned 3 rows");
    }

    @Test
    @DisplayName("should work with primary key lookup")
    void shouldWorkWithPrimaryKey() {
      String sql = "SELECT * FROM departments WHERE dept_id = ?";

      Department engineering =
          library
              .typed()
              .runSqlSingleTypedApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of(1), Department.class, null);

      assertThat(engineering).isNotNull();
      assertThat(engineering.getDeptName()).isEqualTo("Engineering");
      assertThat(engineering.getLocation()).isEqualTo("Building A");
    }
  }

  // =========================================================================
  // Connection management
  // =========================================================================

  @Nested
  @DisplayName("Connection Management")
  class ConnectionManagementTests {

    @Test
    @DisplayName("should handle multiple sequential queries")
    void shouldHandleMultipleQueries() {
      String sql = "SELECT * FROM employees WHERE id = ?";

      for (int i = 1; i <= 5; i++) {
        List<Employee> result =
            library
                .typed()
                .runSqlTypedApi(
                    getDatabaseName(), jdbcUrl, username, sql, List.of(i), Employee.class, null);

        assertThat(result).hasSize(1);
      }
    }

    @Test
    @DisplayName("should handle concurrent queries safely")
    void shouldHandleConcurrentQueries() throws InterruptedException {
      String sql = "SELECT COUNT(*) FROM employees";
      final String connectionUrl = jdbcUrl;
      final String connectionUser = username;
      final String connectionPassword = password;

      var threads = new Thread[10];
      var exceptions = new java.util.concurrent.ConcurrentLinkedQueue<Exception>();

      for (int i = 0; i < threads.length; i++) {
        threads[i] =
            new Thread(
                () -> {
                  try (var conn =
                          DriverManager.getConnection(
                              connectionUrl, connectionUser, connectionPassword);
                      var stmt = conn.prepareStatement(sql);
                      var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                      int count = rs.getInt(1);
                      assertThat(count).isEqualTo(5);
                    }
                  } catch (Exception e) {
                    exceptions.add(e);
                  }
                });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      assertThat(exceptions).isEmpty();
    }
  }

  // =========================================================================
  // Error handling
  // =========================================================================

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw RuntimeException for invalid SQL")
    @SuppressWarnings("java:S5778")
    void shouldThrowForInvalidSql() {
      String invalidSql = "SELECT * FROM nonexistent_table";
      String dbName = getDatabaseName();
      String dbUrl = jdbcUrl;
      String dbUser = username;

      var assertion =
          assertThatThrownBy(
                  () ->
                      library
                          .typed()
                          .runSqlTypedApi(
                              dbName, dbUrl, dbUser, invalidSql, List.of(), Employee.class, null))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Query execution failed");
      if (DATABASE_TYPE == DatabaseType.ORACLE) {
        assertion.hasMessageContaining("ORA-00942");
      } else {
        assertion.hasMessageContaining("nonexistent_table");
      }
    }

    @Test
    @DisplayName("should throw RuntimeException for parameter count mismatch")
    @SuppressWarnings("java:S5778")
    void shouldThrowForParameterMismatch() {
      String sql = "SELECT * FROM employees WHERE id = ? AND email = ?";
      String dbName = getDatabaseName();
      String dbUrl = jdbcUrl;
      String dbUser = username;

      var assertion =
          assertThatThrownBy(
                  () ->
                      library
                          .typed()
                          .runSqlTypedApi(
                              dbName, dbUrl, dbUser, sql, List.of(1), Employee.class, null))
              .isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Query execution failed");
      if (DATABASE_TYPE == DatabaseType.ORACLE) {
        assertion.hasMessageContaining("ORA-17041");
      } else if (DATABASE_TYPE == DatabaseType.SQLSERVER) {
        assertion.hasMessageContaining("parameter number 2");
      } else {
        assertion.hasMessageContaining("parameter 2");
      }
    }

    @Test
    @DisplayName("should handle connection failure on wrong credentials")
    void shouldHandleConnectionFailure() {
      String sql = "SELECT * FROM employees";
      String dbName = getDatabaseName();
      String dbUrl = jdbcUrl;

      assertThatThrownBy(
              () ->
                  library
                      .typed()
                      .runSqlTypedApi(
                          dbName, dbUrl, "wronguser", sql, List.of(), Employee.class, null))
          .isInstanceOf(Exception.class);
    }
  }

  // =========================================================================
  // Performance
  // =========================================================================

  @Nested
  @DisplayName("Performance")
  class PerformanceTests {

    @Test
    @DisplayName("should execute 30 queries in under 5 seconds")
    void shouldExecuteMultipleQueriesQuickly() {
      String sql = "SELECT * FROM employees WHERE id = ?";
      int iterations = 30;

      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        library
            .typed()
            .runSqlTypedApi(
                getDatabaseName(),
                jdbcUrl,
                username,
                sql,
                List.of((i % 5) + 1),
                Employee.class,
                null);
      }
      long elapsed = System.nanoTime() - start;

      System.out.println("30 queries via typed API: " + elapsed / 1_000_000 + "ms"); // NOSONAR
      assertThat(elapsed).isLessThan(5_000_000_000L);
    }

    @Test
    @DisplayName("should handle large result sets efficiently")
    void shouldHandleLargeResultSet() throws SQLException {
      boolean originalAutoCommit = directConnection.getAutoCommit();
      directConnection.setAutoCommit(false);
      boolean cleanupRequired = false;
      int targetRows = 200;
      try (var stmt =
          directConnection.prepareStatement(
              "INSERT INTO employees (first_name, last_name, email, department, salary,"
                  + " hire_date, is_active) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
        for (int i = 6; i <= targetRows; i++) {
          stmt.setString(1, "User" + i);
          stmt.setString(2, "Test");
          stmt.setString(3, "user" + i + "@example.com");
          stmt.setString(4, "Engineering");
          stmt.setBigDecimal(5, new BigDecimal("75000"));
          stmt.setDate(6, Date.valueOf("2023-01-01"));
          stmt.setBoolean(7, true);
          stmt.addBatch();
          if (i % 100 == 0) {
            stmt.executeBatch();
          }
        }
        stmt.executeBatch();
        directConnection.commit();
        cleanupRequired = true;
      } finally {
        directConnection.setAutoCommit(originalAutoCommit);
      }

      String sql = "SELECT * FROM employees";

      try {
        long start = System.nanoTime();
        List<Employee> allEmployees =
            library
                .typed()
                .runSqlTypedApi(
                    getDatabaseName(), jdbcUrl, username, sql, List.of(), Employee.class, null);
        long elapsed = System.nanoTime() - start;

        System.out.println( // NOSONAR
            "Query ~" + targetRows + " rows via typed API: " + elapsed / 1_000_000 + "ms");
        assertThat(allEmployees).hasSizeGreaterThanOrEqualTo(targetRows);
        assertThat(elapsed).isLessThan(3_000_000_000L);
      } finally {
        if (cleanupRequired) {
          try (var cleanup =
              directConnection.prepareStatement("DELETE FROM employees WHERE id > 5")) {
            cleanup.executeUpdate();
          }
        }
      }
    }
  }
}
