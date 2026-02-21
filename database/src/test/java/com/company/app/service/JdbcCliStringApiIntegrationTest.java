package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.testcontainers.ContainerFactory;
import com.company.app.service.testcontainers.DatabaseType;

/**
 * Integration tests for {@link JdbcCliStringApi} using Testcontainers.
 *
 * <p>Verifies end-to-end string-formatted output from SQL statements and scripts via {@code
 * library.string()}. Supports parameterized database testing via {@code -Ddatabase} system property
 * (postgres, mysql, sqlserver, oracle). Default: postgres.
 */
@SuppressWarnings({"resource", "java:S1118", "PMD.UseUtilityClass"})
@Testcontainers
class JdbcCliStringApiIntegrationTest {

  private static final DatabaseType DATABASE_TYPE = DatabaseType.fromSystemProperty();

  @Container
  private static final GenericContainer<?> container =
      ContainerFactory.createContainer(DATABASE_TYPE);

  private static JdbcCliLibrary library;
  private static Connection directConnection;
  private static String jdbcUrl;
  private static String username;

  @BeforeAll
  static void setupLibrary() throws Exception {
    jdbcUrl = ContainerFactory.getJdbcUrl(container, DATABASE_TYPE);
    username = ContainerFactory.getUsername(container, DATABASE_TYPE);
    String password = ContainerFactory.getPassword(container, DATABASE_TYPE);

    library = JdbcCliLibrary.withPassword(password);

    directConnection = DriverManager.getConnection(jdbcUrl, username, password);

    String schemaResource = DATABASE_TYPE.getSchemaResource();
    try (InputStream is =
        JdbcCliStringApiIntegrationTest.class
            .getClassLoader()
            .getResourceAsStream(schemaResource)) {
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

  private static String captureOutput(final ExecutionResult result) {
    var baos = new ByteArrayOutputStream();
    result.formatOutput(new PrintStream(baos, true, StandardCharsets.UTF_8));
    return baos.toString(StandardCharsets.UTF_8);
  }

  // =========================================================================
  // runSqlStringApi()
  // =========================================================================

  @Nested
  @DisplayName("runSqlStringApi()")
  class RunSqlStringApiTests {

    @Test
    @DisplayName("should return exit code 0 for successful SELECT")
    void shouldReturnSuccessForSelect() {
      String sql = "SELECT * FROM employees ORDER BY id";

      ExecutionResult result =
          library.string().runSqlStringApi(getDatabaseName(), jdbcUrl, username, sql, null);

      assertThat(result.getExitCode()).isZero();
      assertThat(result.getMessage()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("should include column headers in output")
    void shouldIncludeColumnHeaders() {
      String sql = "SELECT first_name, last_name FROM employees WHERE id = 1";

      ExecutionResult result =
          library.string().runSqlStringApi(getDatabaseName(), jdbcUrl, username, sql, null);

      String output = captureOutput(result);
      assertThat(output).containsIgnoringCase("first_name").containsIgnoringCase("last_name");
    }

    @Test
    @DisplayName("should include row data in output")
    void shouldIncludeRowData() {
      String sql = "SELECT first_name, last_name FROM employees WHERE id = 1";

      ExecutionResult result =
          library.string().runSqlStringApi(getDatabaseName(), jdbcUrl, username, sql, null);

      String output = captureOutput(result);
      assertThat(output).contains("Alice").contains("Smith");
    }

    @Test
    @DisplayName("should handle parameterized query")
    void shouldHandleParameterizedQuery() {
      String sql = "SELECT first_name, last_name FROM employees WHERE department = ?";

      ExecutionResult result =
          library
              .string()
              .runSqlStringApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of("Engineering"), null);

      assertThat(result.getExitCode()).isZero();
      String output = captureOutput(result);
      assertThat(output).contains("Alice").contains("Charlie").contains("Eve");
    }

    @Test
    @DisplayName("should handle empty result set")
    void shouldHandleEmptyResult() {
      String sql = "SELECT * FROM employees WHERE email = ?";

      ExecutionResult result =
          library
              .string()
              .runSqlStringApi(
                  getDatabaseName(), jdbcUrl, username, sql, List.of("nobody@example.com"), null);

      assertThat(result.getExitCode()).isZero();
      String output = captureOutput(result);
      assertThat(output).containsIgnoringCase("No rows");
    }

    @Test
    @DisplayName("should return non-zero exit code for invalid SQL")
    void shouldReturnFailureForInvalidSql() {
      String sql = "SELECT * FROM nonexistent_table_xyz";

      ExecutionResult result =
          library.string().runSqlStringApi(getDatabaseName(), jdbcUrl, username, sql, null);

      assertThat(result.getExitCode()).isNotZero();
    }

    @Test
    @DisplayName("should handle DML (INSERT/UPDATE)")
    void shouldHandleDml() {
      String insertSql =
          "INSERT INTO employees (first_name, last_name, email, department, salary,"
              + " hire_date, is_active) VALUES ('Test', 'User', 'test.string@example.com',"
              + " 'Test', 50000, '2024-01-01', true)";

      ExecutionResult insertResult =
          library.string().runSqlStringApi(getDatabaseName(), jdbcUrl, username, insertSql, null);

      assertThat(insertResult.getExitCode()).isZero();
      String insertOutput = captureOutput(insertResult);
      assertThat(insertOutput).containsIgnoringCase("Row");

      // Cleanup
      library
          .string()
          .runSqlStringApi(
              getDatabaseName(),
              jdbcUrl,
              username,
              "DELETE FROM employees WHERE email = 'test.string@example.com'",
              null);
    }
  }

  // =========================================================================
  // runScriptStringApi()
  // =========================================================================

  @Nested
  @DisplayName("runScriptStringApi()")
  class RunScriptStringApiTests {

    @Test
    @DisplayName("should execute a script file and return formatted output")
    void shouldExecuteScriptFile() throws IOException {
      String scriptContent = "SELECT first_name FROM employees WHERE id = 1;\n";
      Path tempScript = Files.createTempFile("test-", ".sql");
      try {
        Files.writeString(tempScript, scriptContent, StandardCharsets.UTF_8);

        ExecutionResult result =
            library
                .string()
                .runScriptStringApi(
                    getDatabaseName(), jdbcUrl, username, tempScript.toString(), null);

        assertThat(result.getExitCode()).isZero();
        String output = captureOutput(result);
        assertThat(output).contains("Alice");
      } finally {
        Files.deleteIfExists(tempScript);
      }
    }

    @Test
    @DisplayName("should execute multi-statement script")
    void shouldExecuteMultiStatementScript() throws IOException {
      String scriptContent =
          """
          SELECT first_name FROM employees WHERE id = 1;
          SELECT last_name FROM employees WHERE id = 1;
          """;
      Path tempScript = Files.createTempFile("test-multi-", ".sql");
      try {
        Files.writeString(tempScript, scriptContent, StandardCharsets.UTF_8);

        ExecutionResult result =
            library
                .string()
                .runScriptStringApi(
                    getDatabaseName(), jdbcUrl, username, tempScript.toString(), null);

        assertThat(result.getExitCode()).isZero();
        String output = captureOutput(result);
        assertThat(output).contains("Alice").contains("Smith");
      } finally {
        Files.deleteIfExists(tempScript);
      }
    }

    @Test
    @DisplayName("should return non-zero exit code for missing script file")
    void shouldReturnFailureForMissingScript() {
      ExecutionResult result =
          library
              .string()
              .runScriptStringApi(
                  getDatabaseName(), jdbcUrl, username, "/nonexistent/path/file.sql", null);

      assertThat(result.getExitCode()).isNotZero();
    }
  }

  // =========================================================================
  // SqlRequestConfig fluent record
  // =========================================================================

  @Nested
  @DisplayName("SqlRequestConfig record (fluent builder)")
  class SqlRequestConfigTests {

    @Test
    @DisplayName("should execute via .execute() fluent API")
    void shouldExecuteViaFluentApi() {
      ExecutionResult result =
          JdbcCliLibrary.request(getDatabaseName(), jdbcUrl, username)
              .withSql("SELECT first_name FROM employees WHERE id = 1")
              .execute(library);

      assertThat(result.getExitCode()).isZero();
      String output = captureOutput(result);
      assertThat(output).contains("Alice");
    }

    @Test
    @DisplayName("should execute with parameters via fluent API")
    void shouldExecuteWithParamsViaFluentApi() {
      ExecutionResult result =
          JdbcCliLibrary.request(getDatabaseName(), jdbcUrl, username)
              .withSql("SELECT first_name FROM employees WHERE department = ?")
              .withParams("Sales")
              .execute(library);

      assertThat(result.getExitCode()).isZero();
      String output = captureOutput(result);
      assertThat(output).contains("Bob");
    }

    @Test
    @DisplayName("hasSql/hasScript/isReady checks")
    void shouldReflectConfigState() {
      var base = JdbcCliLibrary.request("postgresql", "jdbc:postgresql://host/db", "user");
      assertThat(base.isReady()).isFalse();
      assertThat(base.hasSql()).isFalse();
      assertThat(base.hasScript()).isFalse();

      var withSql = base.withSql("SELECT 1");
      assertThat(withSql.hasSql()).isTrue();
      assertThat(withSql.hasScript()).isFalse();
      assertThat(withSql.isReady()).isTrue();

      var withScript = base.withScript("/some/path.sql");
      assertThat(withScript.hasScript()).isTrue();
      assertThat(withScript.hasSql()).isFalse();
      assertThat(withScript.isReady()).isTrue();
    }
  }
}
