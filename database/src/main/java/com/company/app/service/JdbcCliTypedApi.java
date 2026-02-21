package com.company.app.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.domain.model.VaultConfig;
import com.company.app.service.service.TypedQueryExecutorService;

/**
 * Facade for the typed-object mapping API. All methods return Java bean instances populated from
 * JDBC {@code ResultSet} data via the optimized {@link
 * com.company.app.service.database.typedapi.ResultSetHandler} framework.
 *
 * <p><strong>Separation of concerns:</strong> This facade exclusively delegates to {@link
 * TypedQueryExecutorService}. For human-readable CLI output use {@link JdbcCliStringApi}.
 *
 * <p>Obtain an instance via {@link JdbcCliLibrary#typed()}.
 */
public final class JdbcCliTypedApi {

  private final TypedQueryExecutorService typedQueryExecutorService;

  /**
   * Package-private — instances are created by {@link JdbcCliLibrary}.
   *
   * @param typedQueryExecutorService service for typed query execution
   */
  JdbcCliTypedApi(final TypedQueryExecutorService typedQueryExecutorService) {
    this.typedQueryExecutorService =
        Objects.requireNonNull(
            typedQueryExecutorService, "TypedQueryExecutorService cannot be null");
  }

  /**
   * Executes a SELECT query and maps each row to an instance of {@code resultClass}.
   *
   * <p>Column names are mapped to bean setter names using underscore-to-camelCase conversion (e.g.,
   * {@code first_name} → {@code setFirstName}).
   *
   * @param <T> the target bean type
   * @param dbType database type (oracle, mysql, postgresql, sqlserver, h2)
   * @param database JDBC connection URL
   * @param user database username
   * @param sql SELECT statement; may contain {@code ?} placeholders
   * @param params positional parameters bound to {@code ?} placeholders
   * @param resultClass class to map rows to; must follow JavaBean conventions
   * @param vaultConfig vault configuration for password resolution; {@code null} for none
   * @return non-null list of mapped objects; empty list when no rows matched
   * @throws QueryExecutionException if query execution or mapping fails
   */
  public <T> List<T> runSqlTypedApi(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final List<Object> params,
      final Class<T> resultClass,
      final VaultConfig vaultConfig) {
    Objects.requireNonNull(dbType, "dbType cannot be null");
    Objects.requireNonNull(database, "database cannot be null");
    Objects.requireNonNull(user, "user cannot be null");
    Objects.requireNonNull(sql, "sql cannot be null");
    Objects.requireNonNull(resultClass, "resultClass cannot be null");

    final SqlRequest request =
        new SqlRequest(
            new DatabaseRequest(dbType, database, user, resolvedVault(vaultConfig)),
            Optional.of(sql),
            Optional.empty(),
            params != null ? params : List.of());

    return typedQueryExecutorService.execute(request, resultClass);
  }

  /**
   * Executes a SELECT query and returns a single mapped object.
   *
   * <p>Returns {@code null} when no rows match. Throws {@link IllegalStateException} when more than
   * one row is returned — use {@link #runSqlTypedApi(String, String, String, String, List, Class,
   * VaultConfig)} for multiple rows.
   *
   * @param <T> the target bean type
   * @param dbType database type
   * @param database JDBC connection URL
   * @param user database username
   * @param sql SELECT statement expected to return zero or one row
   * @param params positional parameters
   * @param resultClass class to map the result to
   * @param vaultConfig vault configuration; {@code null} for none
   * @return mapped object, or {@code null} if no rows found
   * @throws IllegalStateException if more than one row is returned
   * @throws QueryExecutionException if query execution or mapping fails
   */
  public <T> T runSqlSingleTypedApi(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final List<Object> params,
      final Class<T> resultClass,
      final VaultConfig vaultConfig) {
    final List<T> results =
        runSqlTypedApi(dbType, database, user, sql, params, resultClass, vaultConfig);

    if (results.isEmpty()) {
      return null;
    }
    if (results.size() > 1) {
      throw new IllegalStateException(
          "Query returned " + results.size() + " rows, expected 1 or 0");
    }
    return results.getFirst();
  }

  private static VaultConfig resolvedVault(final VaultConfig vaultConfig) {
    return vaultConfig != null ? vaultConfig : VaultConfig.empty();
  }
}
