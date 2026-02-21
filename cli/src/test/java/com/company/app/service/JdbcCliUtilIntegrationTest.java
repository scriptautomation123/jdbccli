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
 * Integration tests for JdbcCliUtil CLI application with Testcontainers. Tests complete CLI
 * workflow: command routing, help, subcommands, error handling.
 */
@Testcontainers
@DisplayName("JdbcCliUtil Integration Tests")
class JdbcCliUtilIntegrationTest {

  private static final class TestPostgreSQLContainer
      extends PostgreSQLContainer<TestPostgreSQLContainer> {

    private TestPostgreSQLContainer(String dockerImageName) {
      super(dockerImageName);
    }
  }

  @Container
  @SuppressWarnings("resource") // Testcontainers manages container lifecycle
  private static final TestPostgreSQLContainer postgres =
      new TestPostgreSQLContainer("postgres:15-alpine");

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
      stmt.execute(
          """
          CREATE TABLE test_table (
            id SERIAL PRIMARY KEY,
            name VARCHAR(50),
            value INTEGER
          )
          """);

      stmt.execute("INSERT INTO test_table (name, value) VALUES ('Item1', 100), ('Item2', 200)");
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
  @DisplayName("Main Command Tests")
  class MainCommandTests {

    @Test
    @DisplayName("should show help when no subcommand provided")
    void shouldShowHelpWhenNoSubcommand() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute();

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output)
          .contains("Usage:", "jdbccli", "Commands:")
          .containsAnyOf("exec-sql", "exec-proc");
    }

    @Test
    @DisplayName("should show version with --version flag")
    void shouldShowVersion() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("--version");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("1.0.0");
    }

    @Test
    @DisplayName("should show help with --help flag")
    void shouldShowHelpWithFlag() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("--help");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Usage:", "jdbccli");
    }

    @Test
    @DisplayName("should show help with help command")
    void shouldShowHelpWithCommand() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("help");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Usage:", "jdbccli");
    }
  }

  @Nested
  @DisplayName("exec-sql Subcommand Tests")
  class ExecSqlSubcommandTests {

    @Test
    @DisplayName("should execute exec-sql subcommand")
    void shouldExecuteExecSqlSubcommand() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM test_table");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Item1", "Item2");
    }

    @Test
    @DisplayName("should show exec-sql help")
    void shouldShowExecSqlHelp() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("exec-sql", "--help");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("exec-sql", "SQL statement", "-t", "-d", "-u", "-p");
    }

    @Test
    @DisplayName("should handle exec-sql with script option")
    void shouldHandleExecSqlWithScript() throws Exception {
      java.io.File scriptFile = java.io.File.createTempFile("test", ".sql");
      scriptFile.deleteOnExit();

      try (java.io.PrintWriter writer = new java.io.PrintWriter(scriptFile)) {
        writer.println("SELECT COUNT(*) FROM test_table;");
      }

      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
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

    @Test
    @DisplayName("should handle exec-sql with parameters")
    void shouldHandleExecSqlWithParams() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT * FROM test_table WHERE name = ?",
                  "--params",
                  "Item1");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("Item1").doesNotContain("Item2");
    }
  }

  @Nested
  @DisplayName("exec-proc Subcommand Tests")
  class ExecProcSubcommandTests {

    @BeforeAll
    static void setupProcedures() throws Exception {
      try (Statement stmt = connection.createStatement()) {
        stmt.execute(
            """
            CREATE OR REPLACE FUNCTION get_item_count()
            RETURNS INTEGER AS $$
            BEGIN
              RETURN (SELECT COUNT(*) FROM test_table);
            END;
            $$ LANGUAGE plpgsql;
            """);

        stmt.execute(
            """
            CREATE OR REPLACE FUNCTION get_item_value(item_name VARCHAR)
            RETURNS INTEGER AS $$
            DECLARE
              item_val INTEGER;
            BEGIN
              SELECT value INTO item_val FROM test_table WHERE name = item_name;
              RETURN item_val;
            END;
            $$ LANGUAGE plpgsql;
            """);
      }
    }

    @Test
    @DisplayName("should execute exec-proc subcommand")
    void shouldExecuteExecProcSubcommand() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-proc",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "get_item_count");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).containsAnyOf("2", "count");
    }

    @Test
    @DisplayName("should show exec-proc help")
    void shouldShowExecProcHelp() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("exec-proc", "--help");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("exec-proc", "procedure", "function");
    }

    @Test
    @DisplayName("should handle exec-proc with aliases")
    void shouldHandleExecProcAliases() {
      // Test 'proc' alias
      int exitCode =
          new CommandLine(new JdbcCliUtil())
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
                  "get_item_count");

      assertThat(exitCode).isZero();

      outContent.reset();

      // Test 'call' alias
      exitCode =
          new CommandLine(new JdbcCliUtil())
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
                  "get_item_count");

      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should handle exec-proc with parameters")
    void shouldHandleExecProcWithParams() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-proc",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "get_item_value",
                  "--in",
                  "Item1");

      assertThat(exitCode).isZero();
      String output = outContent.toString();
      assertThat(output).contains("100");
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    @Test
    @DisplayName("should handle invalid subcommand")
    void shouldHandleInvalidSubcommand() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("invalid-command");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("Unmatched", "invalid", "Unknown");
    }

    @Test
    @DisplayName("should handle exec-sql with missing required options")
    void shouldHandleMissingRequiredOptions() {
      int exitCode = new CommandLine(new JdbcCliUtil()).execute("exec-sql", "SELECT 1");

      assertThat(exitCode).isNotZero();
      String errorOutput = errContent.toString();
      assertThat(errorOutput).containsAnyOf("Missing", "required");
    }

    @Test
    @DisplayName("should handle database connection errors")
    void shouldHandleDatabaseConnectionErrors() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  "jdbc:postgresql://invalid-host:5432/testdb",
                  "-u",
                  "user",
                  "-p",
                  "pass",
                  "SELECT 1");

      assertThat(exitCode).isNotZero();
    }

    @Test
    @DisplayName("should handle SQL syntax errors")
    void shouldHandleSqlSyntaxErrors() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "INVALID SQL");

      assertThat(exitCode).isNotZero();
    }
  }

  @Nested
  @DisplayName("Case Insensitivity Tests")
  class CaseInsensitivityTests {

    @Test
    @DisplayName("should handle case-insensitive database types")
    void shouldHandleCaseInsensitiveTypes() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "exec-sql",
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
    @DisplayName("should handle lowercase database types")
    void shouldHandleLowercaseTypes() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .setCaseInsensitiveEnumValuesAllowed(true)
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "SELECT 1");

      assertThat(exitCode).isZero();
    }
  }

  @Nested
  @DisplayName("Output Mode Tests")
  class OutputModeTests {

    @Test
    @DisplayName("should support verbose mode")
    void shouldSupportVerboseMode() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "-v",
                  "SELECT COUNT(*) FROM test_table");

      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should support quiet mode")
    void shouldSupportQuietMode() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "-q",
                  "SELECT COUNT(*) FROM test_table");

      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should support both verbose and quiet simultaneously")
    void shouldHandleVerboseAndQuietTogether() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "-v",
                  "-q",
                  "SELECT COUNT(*) FROM test_table");

      assertThat(exitCode).isZero();
      // Quiet should take precedence for info messages
    }
  }

  @Nested
  @DisplayName("Integration with Vault Options")
  class VaultIntegrationTests {

    @Test
    @DisplayName("should accept vault configuration options")
    void shouldAcceptVaultOptions() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "--vault-url",
                  "https://vault.example.com",
                  "--vault-role-id",
                  "test-role",
                  "SELECT 1");

      // Should parse successfully even if vault is not actually used
      assertThat(exitCode).isZero();
    }

    @Test
    @DisplayName("should accept all vault authentication options")
    void shouldAcceptAllVaultAuthOptions() {
      int exitCode =
          new CommandLine(new JdbcCliUtil())
              .execute(
                  "exec-sql",
                  "-t",
                  "postgresql",
                  "-d",
                  postgres.getJdbcUrl(),
                  "-u",
                  postgres.getUsername(),
                  "-p",
                  postgres.getPassword(),
                  "--vault-url",
                  "https://vault.example.com",
                  "--vault-role-id",
                  "test-role",
                  "--vault-secret-id",
                  "test-secret",
                  "--vault-ait",
                  "test-ait",
                  "SELECT 1");

      assertThat(exitCode).isZero();
    }
  }
}
