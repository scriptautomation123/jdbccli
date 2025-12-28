package com.company.app.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

import com.company.app.service.auth.PasswordRequest;
import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.DatabaseConnectionManager;
import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

/**
 * Abstract base class for database execution services providing common
 * functionality
 * for password resolution, connection management, and execution orchestration.
 * Implements template method pattern for database operations.
 * Now uses DatabaseConnectionManager for centralized connection handling.
 */
public abstract class AbstractDatabaseExecutionService {

  /** Context for validation operations */
  private static final String VALIDATION = "validation";

  /** Context for database connection operations */
  private static final String DATABASE_CONNECTION = "database_connection";

  /** Context for password resolution operations */
  private static final String PASSWORD_RESOLUTION = "password_resolution";

  /** Context for database execution operations */
  private static final String DATABASE_EXECUTION = "database_execution";

  /** Status for failed operations */
  private static final String FAILED = "FAILED";

  /** Password resolver for authentication */
  private final PasswordResolver passwordResolver;

  /**
   * Constructs a new AbstractDatabaseExecutionService.
   * 
   * @param passwordResolver resolver for database passwords
   */
  protected AbstractDatabaseExecutionService(final PasswordResolver passwordResolver) {
    this.passwordResolver = passwordResolver;
  }

  /**
   * Resolves database password using the configured password resolver.
   * 
   * @param request database request containing authentication parameters
   * @return optional password if resolution succeeds
   */
  protected Optional<String> resolvePassword(final DatabaseRequest request) {
    try {
      final PasswordRequest passwordRequest = new PasswordRequest(
          request.getUser(),
          request.getDatabase(),
          request.getVaultConfig().vaultUrl(),
          request.getVaultConfig().roleId(),
          request.getVaultConfig().secretId(),
          request.getVaultConfig().ait());

      return passwordResolver.resolvePassword(passwordRequest);

    } catch (IllegalArgumentException e) {
      LoggingUtils.logStructuredError(
          PASSWORD_RESOLUTION,
          VALIDATION,
          "INVALID_PARAMETERS",
          "Invalid password request parameters",
          e);
      throw e;
    }
  }

  /**
   * Creates a database connection using request parameters.
   * Now uses DatabaseConnectionManager for centralized connection handling.
   * 
   * @param request  database request containing connection parameters
   * @param password resolved password for authentication
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  protected Connection createConnection(final DatabaseRequest request, final String password)
      throws SQLException {
    return DatabaseConnectionManager.createConnection(
        request.getType(), request.getDatabase(), request.getUser(), password);
  }

  /**
   * Creates a database connection with explicit parameters.
   * Now uses DatabaseConnectionManager for centralized connection handling.
   * 
   * @param type     database type (e.g., "oracle", "postgresql")
   * @param database database name
   * @param user     database username
   * @param password database password
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  protected Connection createConnection(final String type, final String database, final String user,
      final String password)
      throws SQLException {
    return DatabaseConnectionManager.createConnection(type, database, user, password);
  }

  /**
   * Abstract method for specific execution logic to be implemented by subclasses.
   * 
   * @param request database request
   * @param conn    database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  protected abstract ExecutionResult executeWithConnection(DatabaseRequest request, Connection conn)
      throws SQLException;

  /**
   * Template method for database execution with password resolution and
   * connection management.
   * 
   * @param request database request to execute
   * @return execution result
   */
  public ExecutionResult execute(final DatabaseRequest request) {
    try {
      final Optional<String> password = resolvePassword(request);
      if (!password.isPresent()) {
        LoggingUtils.logStructuredError(
            PASSWORD_RESOLUTION,
            "resolve",
            FAILED,
            "Failed to resolve password for user: " + request.getUser(),
            null);
        return ExecutionResult.failure(
            1, "[ERROR] Failed to resolve password for user: " + request.getUser());
      }

      try (Connection conn = createConnection(request, password.get())) {
        return executeWithConnection(request, conn);
      }

    } catch (SQLException | IllegalArgumentException e) {
      LoggingUtils.logStructuredError(
          DATABASE_EXECUTION, "execute", FAILED, "Failed to execute database operation", e);
      throw ExceptionUtils.wrap(e, "Failed to execute database operation");
    }
  }
}
