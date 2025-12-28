package com.company.app.service.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.company.app.service.util.LoggingUtils;

/**
 * Centralized manager for database connections using Java 21 features.
 * Encapsulates connection string generation, validation, and error handling.
 * Uses pattern matching for cleaner connection type selection.
 */
public final class DatabaseConnectionManager {

  /** Context for database connection operations */
  private static final String DATABASE_CONNECTION = "database_connection";

  /** Context for validation operations */
  private static final String VALIDATION = "validation";

  /** Status for failed operations */
  private static final String FAILED = "FAILED";

  /** Status for successful operations */
  private static final String SUCCESS = "SUCCESS";

  /**
   * Configuration record for database connection parameters.
   * Uses Java 21 record for immutable data.
   * 
   * @param type     database type (e.g., "oracle", "h2")
   * @param database database name
   * @param user     database username
   * @param password database password
   * @param host     optional host for JDBC connections
   */
  public record ConnectionConfig(String type, String database, String user, String password, String host) {

    /**
     * Creates a connection config without host (for LDAP/memory connections).
     */
    public ConnectionConfig(String type, String database, String user, String password) {
      this(type, database, user, password, null);
    }

    /**
     * Validates the connection configuration.
     * 
     * @throws IllegalArgumentException if any required parameter is invalid
     */
    public void validate() {
      if (isNullOrBlank(type)) {
        throw new IllegalArgumentException("Database type cannot be null or empty");
      }
      if (isNullOrBlank(database)) {
        throw new IllegalArgumentException("Database name cannot be null or empty");
      }
      if (isNullOrBlank(user)) {
        throw new IllegalArgumentException("Database user cannot be null or empty");
      }
      if (password == null) {
        throw new IllegalArgumentException("Database password cannot be null");
      }
    }

    private static boolean isNullOrBlank(final String value) {
      return value == null || value.trim().isEmpty();
    }
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private DatabaseConnectionManager() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Creates a database connection using the provided configuration.
   * Uses pattern matching for cleaner type handling.
   * 
   * @param config connection configuration
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  public static Connection createConnection(final ConnectionConfig config) throws SQLException {
    // Validate configuration
    try {
      config.validate();
    } catch (IllegalArgumentException e) {
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          VALIDATION,
          FAILED,
          e.getMessage(),
          e);
      throw e;
    }

    // Generate connection string
    final String connectionUrl = ConnectionStringGenerator
        .createConnectionString(config.type(), config.database(), config.user(), config.password(), config.host())
        .url();

    LoggingUtils.logDatabaseConnection(config.type(), config.database(), config.user());

    // Create connection with error handling
    try {
      final Connection conn = DriverManager.getConnection(connectionUrl, config.user(), config.password());
      LoggingUtils.logStructuredError(
          DATABASE_CONNECTION,
          "create",
          SUCCESS,
          "Successfully connected to database",
          null);
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
   * Creates a database connection with explicit parameters.
   * Convenience method for backward compatibility.
   * 
   * @param type     database type
   * @param database database name
   * @param user     database username
   * @param password database password
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  public static Connection createConnection(final String type, final String database,
      final String user, final String password) throws SQLException {
    return createConnection(new ConnectionConfig(type, database, user, password));
  }

  /**
   * Creates a database connection with host specification.
   * Convenience method for JDBC connections.
   * 
   * @param type     database type
   * @param database database name
   * @param user     database username
   * @param password database password
   * @param host     database host
   * @return database connection
   * @throws SQLException if connection creation fails
   */
  public static Connection createConnection(final String type, final String database,
      final String user, final String password, final String host) throws SQLException {
    return createConnection(new ConnectionConfig(type, database, user, password, host));
  }
}
