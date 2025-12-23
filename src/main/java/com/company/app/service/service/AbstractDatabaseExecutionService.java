package com.company.app.service.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import com.company.app.service.auth.PasswordRequest;
import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.ConnectionStringGenerator;
import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

/**
 * Abstract base class for database execution services providing common
 * functionality
 * for password resolution, connection management, and execution orchestration.
 * Implements template method pattern for database operations.
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
          request.getVaultConfig().getVaultUrl(),
          request.getVaultConfig().getRoleId(),
          request.getVaultConfig().getSecretId(),
          request.getVaultConfig().getAit());

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
   * 
   * @param request  database request containing connection parameters
   * @param password resolved password for authentication
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  protected Connection createConnection(final DatabaseRequest request, final String password)
      throws SQLException {
    return createConnection(request.getType(), request.getDatabase(), request.getUser(), password);
  }

  /**
   * Creates a database connection with explicit parameters.
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
    validateConnectionParameters(type, database, user, password);

    final String connectionUrl = ConnectionStringGenerator
        .createConnectionString(type, database, user, password, null)
        .getUrl();

    LoggingUtils.logDatabaseConnection(type, database, user);

    try {
      final Connection conn = DriverManager.getConnection(connectionUrl, user, password);
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION, "create", "SUCCESS", "Successfully connected to database", null);
      return conn;
    } catch (SQLException e) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          "create",
          FAILED,
          "Failed to connect to database: " + e.getMessage(),
          e);
      throw e;
    }
  }

  /**
   * Validates connection parameters for null or empty values.
   * 
   * @param type     database type to validate
   * @param database database name to validate
   * @param user     username to validate
   * @param password password to validate
   * @throws IllegalArgumentException if any parameter is invalid
   */
  private void validateConnectionParameters(final String type, final String database, final String user,
      final String password) {
    if (isNullOrBlank(type)) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          VALIDATION,
          "INVALID_TYPE",
          "Database type cannot be null or empty",
          null);
      throw new IllegalArgumentException("Database type cannot be null or empty");
    }
    if (isNullOrBlank(database)) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          VALIDATION,
          "INVALID_DATABASE",
          "Database name cannot be null or empty",
          null);
      throw new IllegalArgumentException("Database name cannot be null or empty");
    }
    if (isNullOrBlank(user)) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          VALIDATION,
          "INVALID_USER",
          "Database user cannot be null or empty",
          null);
      throw new IllegalArgumentException("Database user cannot be null or empty");
    }
    if (password == null) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          VALIDATION,
          "INVALID_PASSWORD",
          "Database password cannot be null",
          null);
      throw new IllegalArgumentException("Database password cannot be null");
    }
  }

  /**
   * Checks if a string is null or contains only whitespace.
   * 
   * @param value string to check
   * @return true if null or blank
   */
  private static boolean isNullOrBlank(final String value) {
    return value == null || value.trim().isEmpty();
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
