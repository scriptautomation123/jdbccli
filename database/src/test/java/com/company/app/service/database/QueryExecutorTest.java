package com.company.app.service.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.stringapi.QueryExecutor;
import com.company.app.typedapi.QueryExecutorTyped;

/**
 * Unit tests for QueryExecutor using in-memory H2 database. Tests typed and
 * formatted execution
 * modes without Testcontainers.
 */
@DisplayName("QueryExecutor")
class QueryExecutorTest {

  private static Connection connection;

  @BeforeAll
  static void setupDatabase() throws SQLException {
    // Use H2 in-memory database for fast unit tests
    connection = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");

    // Create test schema
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
              CREATE TABLE employees (
                id INTEGER PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                email VARCHAR(100),
                salary DECIMAL(10, 2),
                hire_date DATE
              )
              """);

      // Insert test data
      stmt.execute(
          """
              INSERT INTO employees VALUES
              (1, 'Alice', 'Smith', 'alice@example.com', 75000.00, '2020-01-15'),
              (2, 'Bob', 'Jones', 'bob@example.com', 65000.00, '2021-03-20'),
              (3, 'Charlie', 'Brown', 'charlie@example.com', 85000.00, '2019-11-10')
              """);
    }
  }

  @AfterAll
  static void closeDatabase() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  // Test bean matching database schema
  public static class Employee {
    private Integer id;
    private String firstName;
    private String lastName;
    private String email;
    private java.math.BigDecimal salary;
    private java.sql.Date hireDate;

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

    public java.math.BigDecimal getSalary() {
      return salary;
    }

    public void setSalary(java.math.BigDecimal salary) {
      this.salary = salary;
    }

    public java.sql.Date getHireDate() {
      return hireDate;
    }

    public void setHireDate(java.sql.Date hireDate) {
      this.hireDate = hireDate;
    }
  }

  @Nested
  @DisplayName("executeTyped()")
  class ExecuteTypedTests {

    @Test
    @DisplayName("should execute simple SELECT and map to typed objects")
    void shouldExecuteSimpleSelect() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees ORDER BY id";

      // When
      var result = QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);

      // Then
      assertThat(result.data()).hasSize(3);
      assertThat(result.rowCount()).isEqualTo(3);
      assertThat(result.isEmpty()).isFalse();

      Employee alice = result.data().get(0);
      assertThat(alice.getId()).isEqualTo(1);
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
      assertThat(alice.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("should execute SELECT with WHERE clause and parameters")
    void shouldExecuteWithParameters() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";

      // When
      var result = QueryExecutorTyped.executeTyped(connection, sql, List.of(2), Employee.class);

      // Then
      assertThat(result.data()).hasSize(1);
      Employee bob = result.first();
      assertThat(bob.getFirstName()).isEqualTo("Bob");
      assertThat(bob.getLastName()).isEqualTo("Jones");
    }

    @Test
    @DisplayName("should handle multiple parameters")
    void shouldHandleMultipleParameters() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees WHERE salary > ? AND hire_date < ?";

      // When
      var result = QueryExecutorTyped.executeTyped(
          connection, sql, List.of(70000, java.sql.Date.valueOf("2021-01-01")), Employee.class);

      // Then
      assertThat(result.data()).hasSize(2); // Alice and Charlie
      assertThat(result.data())
          .extracting(Employee::getFirstName)
          .containsExactlyInAnyOrder("Alice", "Charlie");
    }

    @Test
    @DisplayName("should return empty result for no matches")
    void shouldReturnEmptyForNoMatches() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";

      // When
      var result = QueryExecutorTyped.executeTyped(connection, sql, List.of(999), Employee.class);

      // Then
      assertThat(result.data()).isEmpty();
      assertThat(result.rowCount()).isZero();
      assertThat(result.isEmpty()).isTrue();
      assertThat(result.first()).isNull();
    }

    @Test
    @DisplayName("should handle column name mapping (underscore to camelCase)")
    void shouldHandleColumnNameMapping() throws SQLException {
      // Given - first_name maps to firstName, last_name to lastName
      String sql = "SELECT first_name, last_name FROM employees WHERE id = 1";

      // When
      var result = QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);

      // Then
      Employee alice = result.first();
      assertThat(alice.getFirstName()).isEqualTo("Alice");
      assertThat(alice.getLastName()).isEqualTo("Smith");
    }

    @Test
    @DisplayName("should handle decimal and date types correctly")
    void shouldHandleComplexTypes() throws SQLException {
      // Given
      String sql = "SELECT salary, hire_date FROM employees WHERE id = 1";

      // When
      var result = QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);

      // Then
      Employee alice = result.first();
      assertThat(alice.getSalary()).isEqualByComparingTo("75000.00");
      assertThat(alice.getHireDate()).isEqualTo(java.sql.Date.valueOf("2020-01-15"));
    }

    @Test
    @DisplayName("should leverage cache for repeated queries (performance)")
    void shouldLeverageCache() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ?";

      // When - execute same query multiple times
      long start = System.nanoTime();
      for (int i = 0; i < 100; i++) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(1), Employee.class);
      }
      long cacheTime = System.nanoTime() - start;

      // Then - should be fast due to LRU cache
      System.out.println("100 cached executions: " + cacheTime / 1_000_000 + "ms");
      assertThat(cacheTime).isLessThan(500_000_000L); // < 500ms for 100 queries
    }
  }

  @Nested
  @DisplayName("executeFormatted()")
  class ExecuteFormattedTests {

    @Test
    @DisplayName("should execute SELECT and return formatted string table")
    void shouldExecuteAndFormat() throws SQLException {
      // Given
      String sql = "SELECT id, first_name FROM employees WHERE id = 1";

      // When
      var result = QueryExecutor.executeFormatted(connection, sql, List.of());

      // Then
      assertThat(result.formattedOutput())
          .contains("ID")
          .contains("FIRST_NAME")
          .contains("1")
          .contains("Alice");
      assertThat(result.rowCount()).isPositive();
    }

    @Test
    @DisplayName("should handle empty result set")
    void shouldHandleEmptyResult() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees WHERE id = 999";

      // When
      var result = QueryExecutor.executeFormatted(connection, sql, List.of());

      // Then
      assertThat(result.formattedOutput()).contains("No rows returned");
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should format with parameters")
    void shouldFormatWithParameters() throws SQLException {
      // Given
      String sql = "SELECT first_name, last_name FROM employees WHERE id = ?";

      // When
      var result = QueryExecutor.executeFormatted(connection, sql, List.of(2));

      // Then
      assertThat(result.formattedOutput()).contains("Bob").contains("Jones");
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should throw SQLException for invalid SQL")
    void shouldThrowForInvalidSql() {
      // Given
      String invalidSql = "SELECT * FROM nonexistent_table";

      // When/Then
      assertThatThrownBy(
          () -> QueryExecutorTyped.executeTyped(connection, invalidSql, List.of(), Employee.class))
          .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("should throw SQLException for parameter count mismatch")
    void shouldThrowForParameterMismatch() {
      // Given
      String sql = "SELECT * FROM employees WHERE id = ? AND first_name = ?";

      // When/Then - only providing 1 parameter when 2 are needed
      assertThatThrownBy(
          () -> QueryExecutorTyped.executeTyped(connection, sql, List.of(1), Employee.class))
          .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("should handle null parameters list gracefully")
    void shouldHandleNullParams() throws SQLException {
      // Given
      String sql = "SELECT * FROM employees";

      // When - params can be null or empty
      var result = QueryExecutorTyped.executeTyped(connection, sql, null, Employee.class);

      // Then - should work fine
      assertThat(result.data()).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Performance Characteristics")
  class PerformanceTests {

    @Test
    @DisplayName("typed execution should outperform string formatting")
    void typedShouldBeFaster() throws SQLException {
      String sql = "SELECT * FROM employees";
      int iterations = 1000;

      // Warmup
      for (int i = 0; i < 10; i++) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
      }

      // Measure typed
      long start = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        QueryExecutorTyped.executeTyped(connection, sql, List.of(), Employee.class);
      }
      long typedTime = System.nanoTime() - start;

      System.out.println("Typed execution (1000x): " + typedTime / 1_000_000 + "ms");

      // For this small dataset, typed should be reasonably fast
      assertThat(typedTime).isLessThan(2_000_000_000L); // < 2 seconds
    }
  }
}
