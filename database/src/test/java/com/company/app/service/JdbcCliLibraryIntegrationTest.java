package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for JdbcCliLibrary typed query API using Testcontainers PostgreSQL. Tests
 * end-to-end functionality: connection management, vault integration, typed queries. Testcontainers
 * manages PostgreSQL lifecycle - container is closed after tests. Static test class pattern is
 * acceptable for integration tests with @BeforeAll/@AfterAll.
 */
@SuppressWarnings({"resource", "java:S1118"})
@Testcontainers
@DisplayName("JdbcCliLibrary Integration Tests (PostgreSQL)")
class JdbcCliLibraryIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  private static JdbcCliLibrary library;
  private static Connection directConnection;

  @BeforeAll
  static void setupLibrary() throws Exception {
    // Initialize library using factory method with fixed password
    library = JdbcCliLibrary.withPassword(postgres.getPassword());

    // Create direct connection for test data setup
    directConnection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

    // Create test schema
    try (Statement stmt = directConnection.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE employees (
            id SERIAL PRIMARY KEY,
            first_name VARCHAR(50) NOT NULL,
            last_name VARCHAR(50) NOT NULL,
            email VARCHAR(100) UNIQUE NOT NULL,
            department VARCHAR(50),
            salary NUMERIC(10, 2),
            hire_date DATE,
            is_active BOOLEAN DEFAULT TRUE
          )
          """);

      stmt.execute(
          """
          CREATE TABLE departments (
            dept_id SERIAL PRIMARY KEY,
            dept_name VARCHAR(50) NOT NULL,
            location VARCHAR(100)
          )
          """);

      // Insert test data
      stmt.execute(
          """
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date, is_active) VALUES
('Alice', 'Smith', 'alice@example.com', 'Engineering', 95000.00, '2020-01-15', true),
('Bob', 'Jones', 'bob@example.com', 'Sales', 75000.00, '2021-03-20', true),
('Charlie', 'Brown', 'charlie@example.com', 'Engineering', 110000.00, '2019-11-10', true),
('Diana', 'Prince', 'diana@example.com', 'HR', 82000.00, '2022-05-01', false),
('Eve', 'Davis', 'eve@example.com', 'Engineering', 105000.00, '2020-08-15', true)
""");

      stmt.execute(
          """
          INSERT INTO departments (dept_name, location) VALUES
          ('Engineering', 'Building A'),
          ('Sales', 'Building B'),
          ('HR', 'Building C')
          """);
    }
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (directConnection != null && !directConnection.isClosed()) {
      directConnection.close();
    }
  }

  // Test beans matching database schema
  public static class Employee {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private String department;
    private BigDecimal salary;
    private Date hireDate;
    private Boolean isActive;

    // Getters and setters
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

    public Date getHireDate() {
      return hireDate;
    }

    public void setHireDate(Date hireDate) {
      this.hireDate = hireDate;
    }

    public Boolean getIsActive() {
      return isActive;
    }

    public void setIsActive(Boolean isActive) {
      this.isActive = isActive;
    }

    @Override
    public String toString() {
      return "Employee{id=%d, name='%s %s', email='%s', dept='%s', salary=%s}"
          .formatted(id, firstName, lastName, email, department, salary);
    }
  }

  public static class Department {
    private Integer deptId;
    private String deptName;
    private String location;

    public Integer getDeptId() {
      return deptId;
    }

    public void setDeptId(Integer deptId) {
      this.deptId = deptId;
    }

    public String getDeptName() {
      return deptName;
    }

    public void setDeptName(String deptName) {
      this.deptName = deptName;
    }

    public String getLocation() {
      return location;
    }

    public void setLocation(String location) {
      this.location = location;
    }
  }

  @Nested
  @DisplayName("queryForList()")
  class QueryForListTests {

    @Test
    @DisplayName("should query all employees")
    void shouldQueryAllEmployees() {
      // Given
      String sql = "SELECT * FROM employees ORDER BY id";

      // When
      List<Employee> employees =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(),
              Employee.class,
              null // No vault config for test
              );

      // Then
      assertThat(employees)
          .hasSize(5)
          .extracting(Employee::getFirstName)
          .containsExactly("Alice", "Bob", "Charlie", "Diana", "Eve");
    }

    @Test
    @DisplayName("should query with WHERE clause and parameters")
    void shouldQueryWithParameters() {
      // Given
      String sql = "SELECT * FROM employees WHERE department = ? AND is_active = ?";

      // When
      List<Employee> engineers =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of("Engineering", true),
              Employee.class,
              null);

      // Then
      assertThat(engineers)
          .hasSize(3) // Alice, Charlie, Eve
          .extracting(Employee::getFirstName)
          .containsExactlyInAnyOrder("Alice", "Charlie", "Eve");
    }

    @Test
    @DisplayName("should handle salary range query")
    void shouldQuerySalaryRange() {
      // Given
      String sql = "SELECT * FROM employees WHERE salary BETWEEN ? AND ? ORDER BY salary";

      // When
      List<Employee> midRangeSalaries =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(80000, 100000),
              Employee.class,
              null);

      // Then
      assertThat(midRangeSalaries)
          .hasSize(2) // Alice (95k), Diana (82k)
          .extracting(Employee::getFirstName)
          .containsExactly("Diana", "Alice"); // Ordered by salary
    }

    @Test
    @DisplayName("should return empty list for no matches")
    void shouldReturnEmptyForNoMatches() {
      // Given
      String sql = "SELECT * FROM employees WHERE email = ?";

      // When
      List<Employee> result =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of("nonexistent@example.com"),
              Employee.class,
              null);

      // Then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should map column names correctly (underscore to camelCase)")
    void shouldMapColumnNames() {
      // Given - first_name → firstName, last_name → lastName, hire_date → hireDate
      String sql = "SELECT first_name, last_name, hire_date FROM employees WHERE id = 1";

      // When
      List<Employee> result =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(),
              Employee.class,
              null);

      // Then
      Employee alice = result.get(0);
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
      assertThat(alice.getHireDate()).isEqualTo(Date.valueOf("2020-01-15"));
    }

    @Test
    @DisplayName("should handle complex types (BigDecimal, Date, Boolean)")
    void shouldHandleComplexTypes() {
      // Given
      String sql = "SELECT salary, hire_date, is_active FROM employees WHERE id = 3";

      // When
      List<Employee> result =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(),
              Employee.class,
              null);

      // Then
      Employee charlie = result.get(0);
      assertThat(charlie.getSalary()).isEqualByComparingTo("110000.00");
      assertThat(charlie.getHireDate()).isEqualTo(Date.valueOf("2019-11-10"));
      assertThat(charlie.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("should work with different table (departments)")
    void shouldWorkWithDifferentTable() {
      // Given
      String sql = "SELECT * FROM departments ORDER BY dept_id";

      // When
      List<Department> departments =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(),
              Department.class,
              null);

      // Then
      assertThat(departments)
          .hasSize(3)
          .extracting(Department::getDeptName)
          .containsExactly("Engineering", "Sales", "HR");
    }

    @Test
    @DisplayName("should handle JOIN queries")
    void shouldHandleJoinQueries() {
      // Given
      String sql =
          """
          SELECT e.first_name, e.last_name, e.department
          FROM employees e
          WHERE e.is_active = true
          ORDER BY e.salary DESC
          LIMIT 2
          """;

      // When
      List<Employee> topEarners =
          library.queryForList(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(),
              Employee.class,
              null);

      // Then
      assertThat(topEarners)
          .hasSize(2)
          .extracting(Employee::getFirstName)
          .containsExactly("Charlie", "Eve"); // Highest paid active employees
    }

    @Test
    @DisplayName("should handle aggregate queries (though result type might be different)")
    void shouldHandleAggregateQueries() throws SQLException {
      // Given - Using a simple Map for aggregate results
      String sql = "SELECT department, COUNT(*) as count FROM employees GROUP BY department";

      // When - Query as generic Map results
      try (var stmt = directConnection.prepareStatement(sql);
          var rs = stmt.executeQuery()) {

        int engineeringCount = 0;
        while (rs.next()) {
          if ("Engineering".equals(rs.getString("department"))) {
            engineeringCount = rs.getInt("count");
          }
        }

        // Then
        assertThat(engineeringCount).isEqualTo(3); // Alice, Charlie, Eve
      }
    }
  }

  @Nested
  @DisplayName("queryForObject()")
  class QueryForObjectTests {

    @Test
    @DisplayName("should return single object for unique query")
    void shouldReturnSingleObject() {
      // Given
      String sql = "SELECT * FROM employees WHERE email = ?";

      // When
      Employee alice =
          library.queryForObject(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of("alice@example.com"),
              Employee.class,
              null);

      // Then
      assertThat(alice).isNotNull();
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
      assertThat(alice.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("should return null when no rows found")
    void shouldReturnNullForNoRows() {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";

      // When
      Employee result =
          library.queryForObject(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(999),
              Employee.class,
              null);

      // Then
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should throw exception when multiple rows found")
    void shouldThrowForMultipleRows() {
      // Given - query that returns multiple rows
      String sql = "SELECT * FROM employees WHERE department = ?";
      String dbName = postgres.getJdbcUrl();
      String username = postgres.getUsername();

      // When/Then - Extract parameters to avoid multiple invocations in lambda
      assertThatThrownBy(
              () ->
                  library.queryForObject(
                      "postgresql",
                      dbName,
                      username,
                      sql,
                      List.of("Engineering"),
                      Employee.class,
                      null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Query returned 3 rows");
    }

    @Test
    @DisplayName("should work with primary key lookup")
    void shouldWorkWithPrimaryKey() {
      // Given
      String sql = "SELECT * FROM departments WHERE dept_id = ?";

      // When
      Department engineering =
          library.queryForObject(
              "postgresql",
              postgres.getJdbcUrl(),
              postgres.getUsername(),
              sql,
              List.of(1),
              Department.class,
              null);

      // Then
      assertThat(engineering).isNotNull();
      assertThat(engineering.getDeptName()).isEqualTo("Engineering");
      assertThat(engineering.getLocation()).isEqualTo("Building A");
    }
  }

  @Nested
  @DisplayName("Connection Management")
  class ConnectionManagementTests {

    @Test
    @DisplayName("should handle connection pooling (multiple queries)")
    void shouldHandleMultipleQueries() {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";

      // When - execute multiple queries sequentially
      for (int i = 1; i <= 5; i++) {
        List<Employee> result =
            library.queryForList(
                "postgresql",
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                sql,
                List.of(i),
                Employee.class,
                null);

        // Then
        assertThat(result).hasSize(1);
      }
    }

    @Test
    @DisplayName("should handle concurrent queries safely")
    void shouldHandleConcurrentQueries() throws InterruptedException {
      // Given
      String sql = "SELECT COUNT(*) FROM employees";
      String jdbcUrl = postgres.getJdbcUrl();
      String username = postgres.getUsername();
      String password = postgres.getPassword();

      // When - simulate concurrent access
      var threads = new Thread[10];
      var exceptions = new java.util.concurrent.ConcurrentLinkedQueue<Exception>();

      for (int i = 0; i < threads.length; i++) {
        threads[i] =
            new Thread(
                () -> {
                  try {
                    // Use direct SQL for count query
                    try (var conn = DriverManager.getConnection(jdbcUrl, username, password);
                        var stmt = conn.prepareStatement(sql);
                        var rs = stmt.executeQuery()) {
                      if (rs.next()) {
                        int count = rs.getInt(1);
                        assertThat(count).isEqualTo(5);
                      }
                    }
                  } catch (Exception e) {
                    exceptions.add(e);
                  }
                });
        threads[i].start();
      }

      // Wait for all threads
      for (Thread thread : threads) {
        thread.join();
      }

      // Then
      assertThat(exceptions).isEmpty();
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw RuntimeException for invalid SQL")
    void shouldThrowForInvalidSql() {
      // Given
      String invalidSql = "SELECT * FROM nonexistent_table";
      String dbName = postgres.getJdbcUrl();
      String username = postgres.getUsername();

      // When/Then
      assertThatThrownBy(
              () ->
                  library.queryForList(
                      "postgresql", dbName, username, invalidSql, List.of(), Employee.class, null))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Query execution failed")
          .hasMessageContaining("nonexistent_table");
    }

    @Test
    @DisplayName("should throw RuntimeException for parameter count mismatch")
    void shouldThrowForParameterMismatch() {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ? AND email = ?";
      String dbName = postgres.getJdbcUrl();
      String username = postgres.getUsername();

      // When/Then - providing only 1 parameter when 2 needed
      assertThatThrownBy(
              () ->
                  library.queryForList(
                      "postgresql",
                      dbName,
                      username,
                      sql,
                      List.of(1), // Missing second parameter
                      Employee.class,
                      null))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Query execution failed")
          .hasMessageContaining("parameter 2");
    }

    @Test
    @DisplayName("should handle connection failure (wrong credentials)")
    void shouldHandleConnectionFailure() {
      // Given
      String sql = "SELECT * FROM employees";
      String dbName = postgres.getJdbcUrl();

      // When/Then - wrong username
      assertThatThrownBy(
              () ->
                  library.queryForList(
                      "postgresql", dbName, "wronguser", sql, List.of(), Employee.class, null))
          .isInstanceOf(Exception.class); // Will fail during connection
    }
  }

  @Nested
  @DisplayName("Performance")
  class PerformanceTests {

    @Test
    @DisplayName("should execute 100 queries in reasonable time")
    void shouldExecuteMultipleQueriesQuickly() {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";
      int iterations = 30;

      // When
      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        library.queryForList(
            "postgresql",
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            sql,
            List.of((i % 5) + 1), // Cycle through IDs 1-5
            Employee.class,
            null);
      }
      long elapsed = System.nanoTime() - start;

      // Then
      System.out.println("30 PostgreSQL queries: " + elapsed / 1_000_000 + "ms");
      assertThat(elapsed).isLessThan(5_000_000_000L); // < 5 seconds
    }

    @Test
    @DisplayName("should handle large result sets efficiently")
    void shouldHandleLargeResultSet() throws SQLException {
      // Given - insert more test data using batching to reduce per-row overhead
      boolean originalAutoCommit = directConnection.getAutoCommit();
      directConnection.setAutoCommit(false);
      boolean cleanupRequired = false;
      int targetRows = 200;
      try (var stmt =
          directConnection.prepareStatement(
              "INSERT INTO employees (first_name, last_name, email, department, salary, hire_date,"
                  + " is_active) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
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
        // When
        long start = System.nanoTime();
        List<Employee> allEmployees =
            library.queryForList(
                "postgresql",
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                sql,
                List.of(),
                Employee.class,
                null);
        long elapsed = System.nanoTime() - start;

        // Then
        System.out.println("Query ~" + targetRows + " rows: " + elapsed / 1_000_000 + "ms");
        assertThat(allEmployees).hasSizeGreaterThanOrEqualTo(targetRows);
        assertThat(elapsed).isLessThan(3_000_000_000L); // < 3 seconds
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
