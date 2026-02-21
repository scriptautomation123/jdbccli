package com.company.app.service;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.VaultConfig;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.service.SqlExecutorService;
import com.company.app.service.service.TypedQueryExecutorService;

/**
 * Thin aggregator and entry point for the JDBC CLI library.
 *
 * <p>Access the two distinct API surfaces via:
 *
 * <ul>
 *   <li>{@link #string()} — formatted string output for CLI display
 *   <li>{@link #typed()} — typed Java-object mapping for programmatic use
 * </ul>
 *
 * <p><strong>Usage example (string API):</strong>
 *
 * <pre>{@code
 * JdbcCliLibrary lib = JdbcCliLibrary.withPassword("secret");
 * ExecutionResult result = lib.string().runSqlStringApi(
 *     "postgresql", "jdbc:postgresql://localhost/mydb", "admin",
 *     "SELECT * FROM employees WHERE dept = ?",
 *     List.of("Engineering"),
 *     VaultConfig.empty());
 * result.formatOutput(System.out);
 * }</pre>
 *
 * <p><strong>Usage example (typed API):</strong>
 *
 * <pre>{@code
 * JdbcCliLibrary lib = JdbcCliLibrary.withPassword("secret");
 * List<Employee> employees = lib.typed().runSqlTypedApi(
 *     "postgresql", "jdbc:postgresql://localhost/mydb", "admin",
 *     "SELECT * FROM employees WHERE dept = ?",
 *     List.of("Engineering"),
 *     Employee.class,
 *     VaultConfig.empty());
 * }</pre>
 *
 * @see JdbcCliStringApi
 * @see JdbcCliTypedApi
 */
public final class JdbcCliLibrary {

  private final JdbcCliStringApi stringApi;
  private final JdbcCliTypedApi typedApi;

  private JdbcCliLibrary(final PasswordResolver passwordResolver) {
    Objects.requireNonNull(passwordResolver, "PasswordResolver cannot be null");
    this.stringApi =
        new JdbcCliStringApi(
            new SqlExecutorService(passwordResolver),
            new ProcedureExecutorService(passwordResolver));
    this.typedApi = new JdbcCliTypedApi(new TypedQueryExecutorService(passwordResolver));
  }

  /**
   * Creates a library instance with a custom password supplier called when vault auth is
   * unavailable or disabled.
   *
   * @param passwordSupplier supplier invoked when a password prompt is needed
   * @return new {@code JdbcCliLibrary} instance
   * @throws NullPointerException if {@code passwordSupplier} is null
   */
  public static JdbcCliLibrary create(final Supplier<String> passwordSupplier) {
    Objects.requireNonNull(passwordSupplier, "Password supplier cannot be null");
    return new JdbcCliLibrary(new PasswordResolver(passwordSupplier));
  }

  /**
   * Creates a library instance with a static password. Prefer vault configuration for production.
   *
   * @param password static password
   * @return new {@code JdbcCliLibrary} instance
   * @throws NullPointerException if {@code password} is null
   */
  public static JdbcCliLibrary withPassword(final String password) {
    Objects.requireNonNull(password, "Password cannot be null");
    return new JdbcCliLibrary(new PasswordResolver(() -> password, true));
  }

  /**
   * Creates a library instance that relies solely on vault authentication. Throws at runtime if a
   * password prompt would be required.
   *
   * @return new {@code JdbcCliLibrary} instance
   */
  public static JdbcCliLibrary withVaultOnly() {
    return create(
        () -> {
          throw new IllegalStateException(
              "Password prompt requested but vault-only mode is enabled");
        });
  }

  /**
   * Returns the string-output API surface.
   *
   * <p>Use this for CLI display, scripts, and stored procedure execution where human-readable
   * formatted output is required.
   *
   * @return {@link JdbcCliStringApi} instance
   */
  public JdbcCliStringApi string() {
    return stringApi;
  }

  /**
   * Returns the typed-object mapping API surface.
   *
   * <p>Use this for programmatic access where results are consumed as Java beans.
   *
   * @return {@link JdbcCliTypedApi} instance
   */
  public JdbcCliTypedApi typed() {
    return typedApi;
  }

  /**
   * Immutable fluent configuration record for string-API SQL requests.
   *
   * <p><strong>Example:</strong>
   *
   * <pre>{@code
   * ExecutionResult result = JdbcCliLibrary.request("postgresql", jdbcUrl, "admin")
   *     .withSql("SELECT * FROM employees WHERE status = ?")
   *     .withParams("active")
   *     .execute(library);
   * }</pre>
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
     * Creates a minimal configuration with required connection details.
     *
     * @param dbType database type
     * @param database JDBC URL
     * @param user database username
     * @return new {@code SqlRequestConfig}
     */
    public static SqlRequestConfig of(
        final String dbType, final String database, final String user) {
      return new SqlRequestConfig(
          dbType, database, user, null, null, List.of(), VaultConfig.empty());
    }

    /** Returns a copy with the specified SQL statement; clears any script path. */
    public SqlRequestConfig withSql(final String sql) {
      return new SqlRequestConfig(dbType, database, user, sql, null, params, vaultConfig);
    }

    /** Returns a copy with the specified script path; clears any SQL statement. */
    public SqlRequestConfig withScript(final String scriptPath) {
      return new SqlRequestConfig(dbType, database, user, null, scriptPath, params, vaultConfig);
    }

    /** Returns a copy with the specified varargs parameters. */
    public SqlRequestConfig withParams(final Object... params) {
      return new SqlRequestConfig(
          dbType, database, user, sql, scriptPath, List.of(params), vaultConfig);
    }

    /** Returns a copy with the specified parameters list. */
    public SqlRequestConfig withParams(final List<Object> params) {
      return new SqlRequestConfig(dbType, database, user, sql, scriptPath, params, vaultConfig);
    }

    /** Returns a copy with the specified vault configuration. */
    public SqlRequestConfig withVault(final VaultConfig vaultConfig) {
      return new SqlRequestConfig(dbType, database, user, sql, scriptPath, params, vaultConfig);
    }

    /** Returns a copy with vault configuration from individual parameters. */
    public SqlRequestConfig withVault(
        final String vaultUrl, final String roleId, final String secretId, final String ait) {
      return withVault(new VaultConfig(vaultUrl, roleId, secretId, ait));
    }

    /**
     * Executes via the string API of the provided library instance.
     *
     * @param library library instance to use
     * @return execution result
     * @throws IllegalStateException if neither SQL nor script path is configured
     */
    public ExecutionResult execute(final JdbcCliLibrary library) {
      if (sql == null && scriptPath == null) {
        throw new IllegalStateException("Either SQL statement or script path is required");
      }
      if (scriptPath != null) {
        return library.string().runScriptStringApi(dbType, database, user, scriptPath, vaultConfig);
      }
      return library.string().runSqlStringApi(dbType, database, user, sql, params, vaultConfig);
    }

    /** Returns {@code true} if a SQL statement is configured. */
    public boolean hasSql() {
      return sql != null && !sql.isBlank();
    }

    /** Returns {@code true} if a script path is configured. */
    public boolean hasScript() {
      return scriptPath != null && !scriptPath.isBlank();
    }

    /** Returns {@code true} if either SQL or script path is configured. */
    public boolean isReady() {
      return hasSql() || hasScript();
    }
  }

  /**
   * Creates a new fluent {@link SqlRequestConfig} for the string API.
   *
   * @param dbType database type
   * @param database JDBC URL
   * @param user database username
   * @return new {@code SqlRequestConfig}
   */
  public static SqlRequestConfig request(
      final String dbType, final String database, final String user) {
    return SqlRequestConfig.of(dbType, database, user);
  }
}
