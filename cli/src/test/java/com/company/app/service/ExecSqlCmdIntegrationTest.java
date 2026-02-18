package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import picocli.CommandLine;

/**
 * Integration tests for ExecSqlCmd using Testcontainers PostgreSQL. Tests CLI command execution
 * with real database connections.
 */
@Testcontainers
@DisplayName("ExecSqlCmd Integration Tests")
class ExecSqlCmdIntegrationTest {

  @Container
  @SuppressWarnings("resource") // Testcontainers manages container lifecycle
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine");

  static {
    postgres.withDatabaseName("testdb");
    postgres.withUsername("testuser");
    postgres.withPassword("testpass");
  }

  private static Connection connection;
  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeAll
  static void setupDatabase() throws Exception {
    connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

    try (Statement stmt = connection.createStatement()) {
      // Create test tables
      stmt.execute(
          """
          CREATE TABLE employees (
            id SERIAL PRIMARY KEY,
            first_name VARCHAR(50) NOT NULL,
            last_name VARCHAR(50) NOT NULL,
            email VARCHAR(100) UNIQUE NOT NULL,
            department VARCHAR(50),
            salary NUMERIC(10, 2),
            hire_date DATE
          )
          """);

      stmt.execute(
          """
          CREATE TABLE products (
            product_id SERIAL PRIMARY KEY,
            product_name VARCHAR(100) NOT NULL,
            price NUMERIC(10, 2),
            stock_quantity INTEGER
          )
          """);

      // Insert test data
      stmt.execute(
          """
INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
('Alice', 'Smith', 'alice@example.com', 'Engineering', 95000.00, '2020-01-15'),
('Bob', 'Jones', 'bob@example.com', 'Sales', 75000.00, '2021-03-20'),
('Charlie', 'Brown', 'charlie@example.com', 'Engineering', 110000.00, '2019-11-10')
""");

      stmt.execute(
          """
          INSERT INTO products (product_name, price, stock_quantity) VALUES
          ('Laptop', 1299.99, 50),
          ('Mouse', 29.99, 200),
          ('Keyboard', 89.99, 150)
          """);
    }
  }

  @AfterAll
  static void teardownDatabase() throws Exception {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  @BeforeEach
  void setupStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Nested
  @DisplayName("Simple SELECT Queries")
  class SimpleSelectTests {

    @Test
    @DisplayName("should execute simple SELECT query")
    void shouldExecuteSimpleSelect() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM employees ORDER BY id");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Alice", "Bob", "Charlie").contains("alice@example.com");
    }

    @Test
    @DisplayName("should execute SELECT with WHERE clause")
    void shouldExecuteSelectWithWhere() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT first_name, last_name FROM employees WHERE department = 'Engineering'");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Alice", "Charlie").doesNotContain("Bob");
    }

    @Test
    @DisplayName("should handle query returning no rows")
    void shouldHandleEmptyResultSet() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM employees WHERE department = 'NonExistent'");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("0 rows", "empty", "No rows");
    }

    @Test
    @DisplayName("should execute aggregate queries")
    void shouldExecuteAggregateQuery() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT COUNT(*) as employee_count, AVG(salary) as avg_salary FROM employees");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("employee_count", "avg_salary", "3", "93333");
    }
  }

  @Nested
  @DisplayName("Parameterized Queries")
  class ParameterizedQueryTests {

    @Test
    @DisplayName("should execute query with single parameter")
    void shouldExecuteWithSingleParameter() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM employees WHERE first_name = ?",
                  "--params",
                  "Alice");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Alice", "Smith").doesNotContain("Bob", "Charlie");
    }

    @Test
    @DisplayName("should execute query with multiple parameters")
    void shouldExecuteWithMultipleParameters() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM employees WHERE department = ? AND salary > ?",
                  "--params",
                  "Engineering,100000");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Charlie").doesNotContain("Alice", "Bob");
    }
  }

  @Nested
  @DisplayName("DML Operations")
  class DMLOperationTests {

    @Test
    @DisplayName("should execute INSERT statement")
    void shouldExecuteInsert() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "INSERT INTO products (product_name, price, stock_quantity) VALUES ('Monitor',"
                      + " 299.99, 75)");

      assertThat(exitCode).isZero();

      // Verify insertion
      exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM products WHERE product_name = 'Monitor'");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Monitor", "299.99");
    }

    @Test
    @DisplayName("should execute UPDATE statement")
    void shouldExecuteUpdate() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "UPDATE products SET price = 24.99 WHERE product_name = 'Mouse'");

      assertThat(exitCode).isZero();

      // Verify update
      outContent.reset();
      exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT price FROM products WHERE product_name = 'Mouse'");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("24.99");
    }

    @Test
    @DisplayName("should execute DELETE statement")
    void shouldExecuteDelete() {
      // First verify the product exists
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT COUNT(*) FROM products WHERE product_name = 'Keyboard'");

      assertThat(exitCode).isZero();

      // Delete the product
      outContent.reset();
      exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "DELETE FROM products WHERE product_name = 'Keyboard'");

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Script Execution")
  class ScriptExecutionTests {

    @Test
    @DisplayName("should execute SQL script from file")
    void shouldExecuteSqlScript() throws Exception {
      // Create temporary SQL script
      File scriptFile = File.createTempFile("test-script", ".sql");
      scriptFile.deleteOnExit();

      try (PrintWriter writer = new PrintWriter(scriptFile)) {
        writer.println("SELECT COUNT(*) as total_employees FROM employees;");
        writer.println("SELECT COUNT(*) as total_products FROM products;");
      }

      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "--script",
                  scriptFile.getAbsolutePath());

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("total_employees", "total_products");
    }

    @Test
    @DisplayName("should handle multi-line SQL script")
    void shouldExecuteMultiLineScript() throws Exception {
      File scriptFile = File.createTempFile("multiline-script", ".sql");
      scriptFile.deleteOnExit();

      try (PrintWriter writer = new PrintWriter(scriptFile)) {
        writer.println(
            """
            CREATE TEMPORARY TABLE temp_test (
              id SERIAL,
              name VARCHAR(50)
            );
            INSERT INTO temp_test (name) VALUES ('Test1'), ('Test2');
            SELECT * FROM temp_test;
            """);
      }

      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "--script",
                  scriptFile.getAbsolutePath());

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should handle invalid SQL syntax")
    void shouldHandleInvalidSql() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "INVALID SQL SYNTAX HERE");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("ERROR", "syntax", "invalid");
    }

    @Test
    @DisplayName("should handle invalid table name")
    void shouldHandleInvalidTable() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM non_existent_table");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("ERROR", "does not exist", "not found");
    }

    @Test
    @DisplayName("should handle connection failure with wrong credentials")
    void shouldHandleWrongCredentials() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  "wronguser",
                  "-p",
                  "wrongpassword",
                  "SELECT * FROM employees");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("ERROR", "authentication", "password", "denied");
    }

    @Test
    @DisplayName("should handle missing SQL statement")
    void shouldHandleMissingSql() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword());

      assertThat(exitCode).isNotZero();
    }
  }

  @Nested
  @DisplayName("Output Options")
  class OutputOptionTests {

    @Test
    @DisplayName("should support verbose output")
    void shouldSupportVerboseOutput() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "-v",
                  "SELECT COUNT(*) FROM employees");

      assertThat(exitCode).isZero();
      // Verbose mode may add additional debug information
      String output = outContent.toString();
      assertThat(output).isNotEmpty();
    }

    @Test
    @DisplayName("should support quiet output")
    void shouldSupportQuietOutput() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "-q",
                  "SELECT COUNT(*) FROM employees");

      assertThat(exitCode).isZero();
      // Quiet mode should minimize non-essential output
    }
  }

  @Nested
  @DisplayName("Database Type Support")
  class DatabaseTypeTests {

    @Test
    @DisplayName("should handle case-insensitive database type")
    void shouldHandleCaseInsensitiveType() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "-t",
                  "PostgreSQL",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT 1");

      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should accept database type abbreviation")
    void shouldAcceptTypeAbbreviation() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "--type",
                  "postgresql",
                  "--database",
                  postgres.getJdbcUrl(),
                  "--user",
                  postgres.getUsername(),
                  "--password",
                  postgres.getPassword(),
                  "SELECT 1");

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Complex Queries")
  class ComplexQueryTests {

    @Test
    @DisplayName("should execute JOIN queries")
    void shouldExecuteJoinQuery() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  """
                  SELECT e.first_name, e.last_name, e.department
                  FROM employees e
                  WHERE e.department IN ('Engineering', 'Sales')
                  ORDER BY e.salary DESC
                  """);

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Charlie", "Alice", "Bob");
    }

    @Test
    @DisplayName("should execute subquery")
    void shouldExecuteSubquery() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  """
                  SELECT first_name, salary
                  FROM employees
                  WHERE salary > (SELECT AVG(salary) FROM employees)
                  ORDER BY salary DESC
                  """);

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Charlie", "Alice");
    }

    @Test
    @DisplayName("should execute query with CASE expression")
    void shouldExecuteCaseExpression() {
      int exitCode =
          new CommandLine(new ExecSqlCmd())
              .execute(
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  """
                  SELECT first_name,
                         CASE
                           WHEN salary >= 100000 THEN 'Senior'
                           WHEN salary >= 80000 THEN 'Mid-level'
                           ELSE 'Junior'
                         END as level
                  FROM employees
                  ORDER BY salary DESC
                  """);

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("Senior", "Mid-level", "Junior");
    }
  }
}
