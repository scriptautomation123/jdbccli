package com.company.app.service.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.SqlJdbcHelper;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.service.model.DbRequest;
import com.company.app.service.service.model.SqlRequest;
import com.company.app.service.util.LoggingUtils;

/**
 * Service for executing SQL statements and scripts with database connections. Uses composition with
 * DatabaseExecutionContext instead of inheritance for better flexibility and testability.
 *
 * <p><strong>SECURITY MODEL:</strong> This is a CLI tool that allows users to execute arbitrary SQL
 * commands, similar to mysql/psql clients. Security is enforced through:
 *
 * <ul>
 *   <li>Database authentication (username/password via vault)
 *   <li>Database-level permissions (GRANT/REVOKE)
 *   <li>Assumption: Users with CLI access are authorized database users
 * </ul>
 *
 * <p><strong>DO NOT</strong> expose this service via web API or accept SQL from untrusted sources.
 */
public final class SqlExecutorService {

  /** SQL execution operation identifier */
  private static final String SQL_EXECUTION = "sql_execution";

  /** Script execution operation identifier */
  private static final String SCRIPT_EXECUTION = "script_execution";

  /** Success status for operations */
  private static final String SUCCESS = "SUCCESS";

  /** Failed status for operations */
  private static final String FAILED = "FAILED";

  /** Rows affected message template */
  private static final String ROWS_AFFECTED_MSG = "Rows affected: ";

  /** Execution context handles password resolution and connection management */
  private final DatabaseExecutionContext executionContext;

  /**
   * Constructs a new SqlExecutorService with password resolver.
   *
   * @param passwordResolver password resolver for authentication
   */
  public SqlExecutorService(final PasswordResolver passwordResolver) {
    this(
        new DatabaseExecutionContext(
            java.util.Objects.requireNonNull(passwordResolver, "PasswordResolver cannot be null")));
  }

  /**
   * Package-private constructor for testing with custom execution context.
   *
   * @param executionContext custom execution context
   */
  SqlExecutorService(final DatabaseExecutionContext executionContext) {
    this.executionContext =
        java.util.Objects.requireNonNull(
            executionContext, "DatabaseExecutionContext cannot be null");
  }

  /**
   * Executes a SQL request with automatic password resolution and connection management.
   *
   * @param request SQL request to execute
   * @return execution result
   */
  public ExecutionResult execute(final DbRequest request) {
    if (request == null) {
      return ExecutionResult.failure(1, "[ERROR] Request cannot be null");
    }

    if (!(request instanceof SqlRequest sqlRequest)) {
      return ExecutionResult.failure(
          1, "[ERROR] Unsupported request type: " + request.getClass().getName());
    }

    // Delegate to execution context with lambda for SQL-specific logic
    return executionContext.executeWithPasswordResolution(
        request, conn -> executeWithConnection(sqlRequest, conn));
  }

  /**
   * Executes SQL-specific logic with an established connection. This is the service-specific
   * implementation that gets called by the context.
   *
   * @param request SQL request containing statement or script
   * @param connection database connection (managed by execution context)
   * @return execution result
   * @throws SQLException if execution fails
   */
  ExecutionResult executeWithConnection(final SqlRequest request, final Connection connection)
      throws SQLException {

    return switch (request) {
      case SqlRequest r when r.isScriptMode() -> executeScript(r, connection);
      case SqlRequest r when r.isSqlMode() -> executeSingleSql(r, connection);
      default ->
          ExecutionResult.failure(1, "[ERROR] Either SQL statement or --script must be specified");
    };
  }

  /**
   * Executes a single SQL statement with optional parameters.
   *
   * @param request SQL request containing statement and parameters
   * @param connection database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executeSingleSql(final SqlRequest request, final Connection connection)
      throws SQLException {

    final String sql = request.sql().orElse("");
    final int paramCount = request.params().size();
    LoggingUtils.logSqlExecution(sql, paramCount);

    try {
      if (request.params().isEmpty()) {
        return executeStatement(sql, connection);
      } else {
        return executePreparedStatement(sql, request.params(), connection);
      }
    } catch (SQLException exception) {
      LoggingUtils.logStructuredError(
          SQL_EXECUTION,
          "execute_single",
          FAILED,
          "SQL execution failed: " + exception.getMessage(),
          exception);
      throw exception;
    }
  }

  /**
   * Executes a SQL statement without parameters.
   *
   * <p><strong>SECURITY WARNING:</strong> This method executes SQL statements directly without
   * validation. It is intended for CLI use where users provide SQL commands. This is similar to
   * mysql/psql CLI clients. The security model assumes:
   *
   * <ul>
   *   <li>Users are authenticated via database credentials
   *   <li>Database permissions control what operations are allowed
   *   <li>This is NOT exposed via web API or untrusted inputs
   * </ul>
   *
   * <p>For use cases with untrusted input, use {@link #executePreparedStatement} instead.
   *
   * @param sql SQL statement to execute (user-provided, no validation)
   * @param connection database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executeStatement(final String sql, final Connection connection)
      throws SQLException {

    //noinspection SqlSourceToSinkFlow - Intentional: CLI tool for executing user SQL
    try (Statement statement = connection.createStatement()) {
      final boolean hasResults = statement.execute(sql);

      if (hasResults) {
        return SqlJdbcHelper.formatResultSet(statement.getResultSet());
      } else {
        final int updateCount = statement.getUpdateCount();
        LoggingUtils.logStructuredError(
            SQL_EXECUTION, "execute_statement", SUCCESS, ROWS_AFFECTED_MSG + updateCount, null);
        return ExecutionResult.success(ROWS_AFFECTED_MSG + updateCount);
      }
    }
  }

  /**
   * Executes a prepared statement with parameters.
   *
   * @param sql SQL statement with placeholders
   * @param params parameters to bind
   * @param connection database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executePreparedStatement(
      final String sql, final List<Object> params, final Connection connection)
      throws SQLException {

    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < params.size(); i++) {
        statement.setObject(i + 1, params.get(i));
      }

      final boolean hasResults = statement.execute();

      if (hasResults) {
        return SqlJdbcHelper.formatResultSet(statement.getResultSet());
      } else {
        final int updateCount = statement.getUpdateCount();
        LoggingUtils.logStructuredError(
            SQL_EXECUTION, "execute_prepared", SUCCESS, ROWS_AFFECTED_MSG + updateCount, null);
        return ExecutionResult.success(ROWS_AFFECTED_MSG + updateCount);
      }
    }
  }

  /**
   * Executes a SQL script file.
   *
   * @param request SQL request containing script file path
   * @param connection database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executeScript(final SqlRequest request, final Connection connection)
      throws SQLException {

    final String scriptPath = request.script().orElse("");
    LoggingUtils.logSqlScriptExecution(scriptPath);

    try {
      final String scriptContent = SqlJdbcHelper.readScriptFile(scriptPath);
      final List<String> results = new ArrayList<>();

      for (final String statement : SqlJdbcHelper.splitStatements(scriptContent)) {
        final ExecutionResult result = executeStatement(statement, connection);
        results.add(result.getMessage());
      }

      final String combinedResults = String.join("\n", results);
      LoggingUtils.logStructuredError(
          SCRIPT_EXECUTION, "execute_script", SUCCESS, "Script execution completed", null);
      return ExecutionResult.success(combinedResults);

    } catch (IOException exception) {
      LoggingUtils.logStructuredError(
          SCRIPT_EXECUTION,
          "read_script",
          FAILED,
          "Failed to read script file: " + exception.getMessage(),
          exception);
      throw new SQLException("Failed to read script file: " + scriptPath, exception);
    }
  }
}
