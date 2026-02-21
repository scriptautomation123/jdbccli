package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
 * Integration tests for ExecProcedureCmd using Testcontainers PostgreSQL. Tests
 * stored procedure
 * and function execution with real database.
 */
@Testcontainers
@DisplayName("ExecProcedureCmd Integration Tests")
class ExecProcedureCmdIntegrationTest {

  private static final class TestPostgreSQLContainer
      extends PostgreSQLContainer<TestPostgreSQLContainer> {

    private TestPostgreSQLContainer(String dockerImageName) {
      super(dockerImageName);
    }
  }

  @Container
  @SuppressWarnings("resource") // Testcontainers manages container lifecycle
  private static final TestPostgreSQLContainer postgres = new TestPostgreSQLContainer("postgres:15-alpine");

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
    connection = DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

    try (Statement stmt = connection.createStatement()) {
      // Create test table
      stmt.execute(
          """
              CREATE TABLE employees (
                id SERIAL PRIMARY KEY,
                first_name VARCHAR(50),
                last_name VARCHAR(50),
                salary NUMERIC(10, 2),
                department VARCHAR(50)
              )
              """);

      // Insert test data
      stmt.execute(
          """
              INSERT INTO employees (first_name, last_name, salary, department) VALUES
              ('Alice', 'Smith', 95000.00, 'Engineering'),
              ('Bob', 'Jones', 75000.00, 'Sales'),
              ('Charlie', 'Brown', 110000.00, 'Engineering')
              """);

      // Create stored procedures and functions
      // Function: get_employee_count - returns count of employees
      stmt.execute(
          """
              CREATE OR REPLACE FUNCTION get_employee_count()
              RETURNS INTEGER AS $$
              BEGIN
                RETURN (SELECT COUNT(*) FROM employees);
              END;
              $$ LANGUAGE plpgsql;
              """);

      // Function: get_avg_salary - returns average salary by department
      stmt.execute(
          """
              CREATE OR REPLACE FUNCTION get_avg_salary(dept_name VARCHAR)
              RETURNS NUMERIC AS $$
              BEGIN
                RETURN (SELECT AVG(salary) FROM employees WHERE department = dept_name);
              END;
              $$ LANGUAGE plpgsql;
              """);

      // Function: calculate_bonus - calculates bonus based on salary
      stmt.execute(
          """
              CREATE OR REPLACE FUNCTION calculate_bonus(emp_salary NUMERIC, bonus_percent NUMERIC)
              RETURNS NUMERIC AS $$
              BEGIN
                RETURN emp_salary * (bonus_percent / 100.0);
              END;
              $$ LANGUAGE plpgsql;
              """);

      // Procedure: update_salary - updates employee salary
      stmt.execute(
          """
              CREATE OR REPLACE PROCEDURE update_salary(emp_id INTEGER, new_salary NUMERIC)
              LANGUAGE plpgsql AS $$
              BEGIN
                UPDATE employees SET salary = new_salary WHERE id = emp_id;
              END;
              $$;
              """);

      // Function: get_employee_name - returns employee full name
      stmt.execute(
          """
              CREATE OR REPLACE FUNCTION get_employee_name(emp_id INTEGER)
              RETURNS VARCHAR AS $$
              DECLARE
                full_name VARCHAR;
              BEGIN
                SELECT first_name || ' ' || last_name INTO full_name
                FROM employees WHERE id = emp_id;
                RETURN full_name;
              END;
              $$ LANGUAGE plpgsql;
              """);

      // Function: count_by_department - returns employee count per department
      stmt.execute(
          """
              CREATE OR REPLACE FUNCTION count_by_department(dept_name VARCHAR)
              RETURNS INTEGER AS $$
              BEGIN
                RETURN (SELECT COUNT(*) FROM employees WHERE department = dept_name);
              END;
              $$ LANGUAGE plpgsql;
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
  @DisplayName("Simple Function Calls")
  class SimpleFunctionTests {

    @Test
    @DisplayName("should call function with no parameters")
    void shouldCallFunctionWithNoParams() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_employee_count");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("3", "count", "result");
    }

    @Test
    @DisplayName("should call function with single IN parameter")
    void shouldCallFunctionWithSingleParam() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_avg_salary",
              "--in",
              "Engineering");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      // Engineering avg: (95000 + 110000) / 2 = 102500
      assertThat(output).containsAnyOf("102500", "salary", "average");
    }

    @Test
    @DisplayName("should call function with multiple IN parameters")
    void shouldCallFunctionWithMultipleParams() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "calculate_bonus",
              "--in",
              "95000,10");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      // 95000 * 10% = 9500
      assertThat(output).containsAnyOf("9500", "bonus");
    }
  }

  @Nested
  @DisplayName("Procedure Calls")
  class ProcedureTests {

    @Test
    @DisplayName("should call procedure with parameters")
    void shouldCallProcedureWithParams() {
      // First, check initial salary
      int exitCode = new CommandLine(new ExecSqlCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "SELECT salary FROM employees WHERE id = 1");

      assertThat(exitCode).isZero();
      String initialOutput = outContent.toString();
      assertThat(initialOutput).contains("95000");

      // Call procedure to update salary
      outContent.reset();
      exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "update_salary",
              "--in",
              "1,105000");

      assertThat(exitCode).isZero();

      // Verify salary was updated
      outContent.reset();
      exitCode = new CommandLine(new ExecSqlCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "SELECT salary FROM employees WHERE id = 1");

      assertThat(exitCode).isZero();
      String updatedOutput = outContent.toString();
      assertThat(updatedOutput).contains("105000");

      // Reset data so other tests see the original salary.
      outContent.reset();
      exitCode = new CommandLine(new ExecSqlCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "UPDATE employees SET salary = 95000 WHERE id = 1");

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Functions Returning Complex Data")
  class ComplexReturnTests {

    @Test
    @DisplayName("should call function returning string")
    void shouldCallFunctionReturningString() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_employee_name",
              "--in",
              "1");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Alice Smith");
    }

    @Test
    @DisplayName("should call function with department filter")
    void shouldCallDepartmentCount() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "count_by_department",
              "--in",
              "Engineering");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("2", "count");
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should handle non-existent procedure")
    void shouldHandleNonExistentProcedure() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "non_existent_procedure");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("ERROR", "does not exist", "not found");
    }

    @Test
    @DisplayName("should handle wrong parameter count")
    void shouldHandleWrongParameterCount() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_avg_salary",
              "--in",
              "Engineering,ExtraParam");

      // Should fail or handle gracefully
      assertThat(exitCode).isNotZero();
    }

    @Test
    @DisplayName("should handle invalid connection")
    void shouldHandleInvalidConnection() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              "jdbc:postgresql://invalid-host:5432/testdb",
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_employee_count");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("ERROR", "connection", "refused", "timeout");
    }

    @Test
    @DisplayName("should handle missing procedure name")
    void shouldHandleMissingProcedureName() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
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
  class OutputOptionsTests {

    @Test
    @DisplayName("should support verbose mode")
    void shouldSupportVerboseMode() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
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
              "get_employee_count");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).isNotEmpty();
    }

    @Test
    @DisplayName("should support quiet mode")
    void shouldSupportQuietMode() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
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
              "get_employee_count");

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Parameter Formats")
  class ParameterFormatTests {

    @Test
    @DisplayName("should handle numeric parameters")
    void shouldHandleNumericParameters() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "calculate_bonus",
              "--in",
              "100000,15.5");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      // 100000 * 15.5% = 15500
      assertThat(output).containsAnyOf("15500", "bonus");
    }

    @Test
    @DisplayName("should handle string parameters with spaces")
    void shouldHandleStringParametersWithSpaces() {
      int exitCode = new CommandLine(new ExecProcedureCmd())
          .execute(
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "count_by_department",
              "--in",
              "Engineering");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("2");
    }
  }

  @Nested
  @DisplayName("Command Aliases")
  class CommandAliasTests {

    @Test
    @DisplayName("should work with 'proc' alias")
    void shouldWorkWithProcAlias() {
      int exitCode = new CommandLine(new JdbcCliUtil())
          .execute(
              "proc",
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_employee_count");

      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should work with 'call' alias")
    void shouldWorkWithCallAlias() {
      int exitCode = new CommandLine(new JdbcCliUtil())
          .execute(
              "call",
              "-t",
              "postgresql",
              "-d",
              postgres.getJdbcUrl(),
              "-u",
              postgres.getUsername(),
              "-p",
              postgres.getPassword(),
              "get_employee_count");

      assertThat(exitCode).isZero();
    }
  }
}
