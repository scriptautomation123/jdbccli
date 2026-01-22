package com.company.app.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.domain.model.VaultConfig;
import com.company.app.service.service.SqlExecutorService;

/**
 * Main entry point for external consumers of the JDBC CLI library. Provides a fluent API for
 * executing SQL statements and scripts with vault authentication.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // Create library instance with password supplier
 * JdbcCliLibrary lib = JdbcCliLibrary.create(() -> System.console().readPassword("Password: "));
 *
 * // Execute a SQL statement
 * ExecutionResult result = lib.executeSql(
 *     "postgresql",
 *     "jdbc:postgresql://localhost/mydb",
 *     "admin",
 *     "SELECT * FROM users WHERE id = ?",
 *     List.of(123),
 *     VaultConfig.empty()
 * );
 *
 * // Check result
 * if (result.getExitCode() == 0) {
 *     result.formatOutput(System.out);
 * }
 * }</pre>
 *
 * <p><strong>Using the modern fluent API with records (Java 21+):</strong>
 *
 * <pre>{@code
 * ExecutionResult result = library.request("postgresql", "jdbc:postgresql://localhost/mydb", "admin")
 *     .withSql("SELECT * FROM users WHERE status = ?")
 *     .withParams("active")
 *     .withVault(myVaultConfig)
 *     .execute(library);
 * }</pre>
 *
 * @see SqlExecutorService
 * @see ExecutionResult
 */
public final class JdbcCliLibrary {

  /** The underlying SQL executor service */
  private final SqlExecutorService sqlService;

  /** The password resolver used for authentication */
  private final PasswordResolver passwordResolver;

  /**
   * Private constructor - use factory methods to create instances.
   *
   * @param passwordResolver password resolver for authentication
   */
  private JdbcCliLibrary(final PasswordResolver passwordResolver) {
    this.passwordResolver =
        Objects.requireNonNull(passwordResolver, "PasswordResolver cannot be null");
    this.sqlService = new SqlExecutorService(passwordResolver);
  }

  /**
   * Creates a new library instance with a custom password supplier. The supplier will be called
   * when vault authentication fails or is not configured.
   *
   * @param passwordSupplier function to supply password when prompted
   * @return new JdbcCliLibrary instance
   * @throws NullPointerException if passwordSupplier is null
   */
  public static JdbcCliLibrary create(final Supplier<String> passwordSupplier) {
    Objects.requireNonNull(passwordSupplier, "Password supplier cannot be null");
    return new JdbcCliLibrary(new PasswordResolver(passwordSupplier));
  }

  /**
   * Creates a library instance with a static password. Useful for testing, batch operations, or
   * when password is already known.
   *
   * <p><strong>Warning:</strong> Avoid hardcoding passwords in production code. Prefer using {@link
   * #create(Supplier)} with vault configuration.
   *
   * @param password the password to use for authentication
   * @return new JdbcCliLibrary instance
   */
  public static JdbcCliLibrary withPassword(final String password) {
    return create(() -> password);
  }

  /**
   * Creates a library instance with no fallback password supplier. Useful when vault authentication
   * is always expected to succeed.
   *
   * @return new JdbcCliLibrary instance
   */
  public static JdbcCliLibrary withVaultOnly() {
    return create(
        () -> {
          throw new IllegalStateException(
              "Password prompt requested but vault-only mode is enabled");
        });
  }

  /**
   * Executes a SQL statement with parameters.
   *
   * @param dbType database type (oracle, mysql, postgresql, h2)
   * @param database database connection string (JDBC URL)
   * @param user database username
   * @param sql SQL statement to execute (can include ? placeholders)
   * @param params parameters to bind to SQL placeholders
   * @param vaultConfig vault configuration for password resolution
   * @return execution result with exit code and output
   */
  public ExecutionResult executeSql(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final List<Object> params,
      final VaultConfig vaultConfig) {

    final SqlRequest request =
        new SqlRequest(
            new DatabaseRequest(dbType, database, user, vaultConfig),
            Optional.ofNullable(sql),
            Optional.empty(),
            params != null ? params : List.of());

    return sqlService.execute(request);
  }

  /**
   * Executes a SQL statement without parameters.
   *
   * @param dbType database type (oracle, mysql, postgresql, h2)
   * @param database database connection string (JDBC URL)
   * @param user database username
   * @param sql SQL statement to execute
   * @param vaultConfig vault configuration for password resolution
   * @return execution result with exit code and output
   */
  public ExecutionResult executeSql(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final VaultConfig vaultConfig) {

    return executeSql(dbType, database, user, sql, List.of(), vaultConfig);
  }

  /**
   * Executes a SQL script file.
   *
   * @param dbType database type (oracle, mysql, postgresql, h2)
   * @param database database connection string (JDBC URL)
   * @param user database username
   * @param scriptPath path to the SQL script file
   * @param vaultConfig vault configuration for password resolution
   * @return execution result with exit code and output
   */
  public ExecutionResult executeScript(
      final String dbType,
      final String database,
      final String user,
      final String scriptPath,
      final VaultConfig vaultConfig) {

    final SqlRequest request =
        new SqlRequest(
            new DatabaseRequest(dbType, database, user, vaultConfig),
            Optional.empty(),
            Optional.ofNullable(scriptPath),
            List.of());

    return sqlService.execute(request);
  }

  /**
   * Creates a new SQL request configuration using the modern record-based API.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * ExecutionResult result = library.request("postgresql", "jdbc:postgresql://localhost/mydb", "admin")
   *     .withSql("SELECT * FROM users WHERE status = ?")
   *     .withParams("active")
   *     .execute(library);
   * }</pre>
   *
   * @param dbType database type (oracle, mysql, postgresql, h2)
   * @param database database connection string (JDBC URL)
   * @param user database username
   * @return new SqlRequestConfig instance
   */
  public static SqlRequestConfig request(
      final String dbType, final String database, final String user) {
    return SqlRequestConfig.of(dbType, database, user);
  }

  /**
   * Gets the underlying SQL executor service for advanced usage.
   *
   * @return the SqlExecutorService instance
   */
  public SqlExecutorService getSqlService() {
    return sqlService;
  }

  /**
   * Gets the password resolver for advanced usage.
   *
   * @return the PasswordResolver instance
   */
  public PasswordResolver getPasswordResolver() {
    return passwordResolver;
  }

  /**
   * Immutable configuration record for SQL requests using Java 21+ features. Uses wither methods to
   * create modified copies, maintaining immutability.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * // Create and configure request
   * SqlRequestConfig config = SqlRequestConfig.of("postgresql", "jdbc:postgresql://localhost/mydb", "admin")
   *     .withSql("SELECT * FROM users WHERE id = ?")
   *     .withParams(123)
   *     .withVault(myVaultConfig);
   *
   * // Execute
   * ExecutionResult result = config.execute(library);
   * }</pre>
   *
   * @param dbType database type (oracle, mysql, postgresql, h2)
   * @param database database connection string (JDBC URL)
   * @param user database username
   * @param sql SQL statement to execute (nullable)
   * @param scriptPath path to SQL script file (nullable)
   * @param params parameters for prepared statement
   * @param vaultConfig vault configuration for password resolution
   */
  public record SqlRequestConfig(
      String dbType,
      String database,
      String user,
      String sql,
      String scriptPath,
      List<Object> params,
      VaultConfig vaultConfig) {

    /** Compact constructor with validation and defaults. */
    public SqlRequestConfig {
      Objects.requireNonNull(dbType, "Database type cannot be null");
      Objects.requireNonNull(database, "Database connection string cannot be null");
      Objects.requireNonNull(user, "User cannot be null");
      params = params != null ? List.copyOf(params) : List.of();
      vaultConfig = vaultConfig != null ? vaultConfig : VaultConfig.empty();
    }

    /**
     * Creates a minimal configuration with required database connection details.
     *
     * @param dbType database type
     * @param database JDBC connection string
     * @param user database username
     * @return new SqlRequestConfig instance
     */
    public static SqlRequestConfig of(
        final String dbType, final String database, final String user) {
      return new SqlRequestConfig(
          dbType, database, user, null, null, List.of(), VaultConfig.empty());
    }

    /**
     * Returns a new config with the specified SQL statement. Clears any previously set script path.
     *
     * @param sql SQL statement to execute
     * @return new SqlRequestConfig with sql set
     */
    public SqlRequestConfig withSql(final String sql) {
      return new SqlRequestConfig(dbType, database, user, sql, null, params, vaultConfig);
    }

    /**
     * Returns a new config with the specified script path. Clears any previously set SQL statement.
     *
     * @param scriptPath path to SQL script file
     * @return new SqlRequestConfig with script path set
     */
    public SqlRequestConfig withScript(final String scriptPath) {
      return new SqlRequestConfig(dbType, database, user, null, scriptPath, params, vaultConfig);
    }

    /**
     * Returns a new config with the specified parameters.
     *
     * @param params parameter values for prepared statement
     * @return new SqlRequestConfig with params set
     */
    public SqlRequestConfig withParams(final Object... params) {
      return new SqlRequestConfig(
          dbType, database, user, sql, scriptPath, List.of(params), vaultConfig);
    }

    /**
     * Returns a new config with the specified parameters list.
     *
     * @param params parameter values for prepared statement
     * @return new SqlRequestConfig with params set
     */
    public SqlRequestConfig withParams(final List<Object> params) {
      return new SqlRequestConfig(dbType, database, user, sql, scriptPath, params, vaultConfig);
    }

    /**
     * Returns a new config with the specified vault configuration.
     *
     * @param vaultConfig vault configuration
     * @return new SqlRequestConfig with vault config set
     */
    public SqlRequestConfig withVault(final VaultConfig vaultConfig) {
      return new SqlRequestConfig(dbType, database, user, sql, scriptPath, params, vaultConfig);
    }

    /**
     * Returns a new config with vault configuration from individual parameters.
     *
     * @param vaultUrl vault server URL
     * @param roleId vault role ID
     * @param secretId vault secret ID
     * @param ait application identifier token
     * @return new SqlRequestConfig with vault config set
     */
    public SqlRequestConfig withVault(
        final String vaultUrl, final String roleId, final String secretId, final String ait) {
      return withVault(new VaultConfig(vaultUrl, roleId, secretId, ait));
    }

    /**
     * Executes this SQL request configuration using the provided library.
     *
     * @param library the JdbcCliLibrary instance to execute with
     * @return execution result with exit code and output
     * @throws IllegalStateException if neither sql nor scriptPath is set
     */
    public ExecutionResult execute(final JdbcCliLibrary library) {
      if (sql == null && scriptPath == null) {
        throw new IllegalStateException("Either SQL statement or script path is required");
      }

      if (scriptPath != null) {
        return library.executeScript(dbType, database, user, scriptPath, vaultConfig);
      }
      return library.executeSql(dbType, database, user, sql, params, vaultConfig);
    }

    /**
     * Checks if this config has a SQL statement set.
     *
     * @return true if sql is set
     */
    public boolean hasSql() {
      return sql != null && !sql.isBlank();
    }

    /**
     * Checks if this config has a script path set.
     *
     * @return true if scriptPath is set
     */
    public boolean hasScript() {
      return scriptPath != null && !scriptPath.isBlank();
    }

    /**
     * Checks if this config is ready for execution.
     *
     * @return true if either sql or scriptPath is set
     */
    public boolean isReady() {
      return hasSql() || hasScript();
    }
  }
}
