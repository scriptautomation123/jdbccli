package com.company.app.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.ProcedureRequest;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.domain.model.VaultConfig;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.service.SqlExecutorService;

/**
 * Facade for the string-formatted output API. All methods return an {@link ExecutionResult} whose
 * message is a human-readable, column-aligned text table suitable for CLI display.
 *
 * <p><strong>Separation of concerns:</strong> This facade exclusively delegates to the
 * string-output service stack ({@link SqlExecutorService} and {@link ProcedureExecutorService}).
 * For typed Java-object mapping use {@link JdbcCliTypedApi}.
 *
 * <p>Obtain an instance via {@link JdbcCliLibrary#string()}.
 */
public final class JdbcCliStringApi {

  private static final String DBTYPE_NULL = "dbType cannot be null";
  private static final String DATABASE_NULL = "database cannot be null";
  private static final String USER_NULL = "user cannot be null";

  private final SqlExecutorService sqlExecutorService;
  private final ProcedureExecutorService procedureExecutorService;

  /**
   * Package-private — instances are created by {@link JdbcCliLibrary}.
   *
   * @param sqlExecutorService service for SQL and script execution
   * @param procedureExecutorService service for stored procedure execution
   */
  JdbcCliStringApi(
      final SqlExecutorService sqlExecutorService,
      final ProcedureExecutorService procedureExecutorService) {
    this.sqlExecutorService =
        Objects.requireNonNull(sqlExecutorService, "SqlExecutorService cannot be null");
    this.procedureExecutorService =
        Objects.requireNonNull(procedureExecutorService, "ProcedureExecutorService cannot be null");
  }

  /**
   * Executes a SQL statement and returns formatted string output.
   *
   * @param dbType database type (oracle, mysql, postgresql, sqlserver, h2)
   * @param database JDBC connection URL
   * @param user database username
   * @param sql SQL statement; may contain {@code ?} placeholders
   * @param params positional parameters bound to {@code ?} placeholders
   * @param vaultConfig vault configuration for password resolution; {@code null} for none
   * @return execution result with column-aligned text output
   */
  public ExecutionResult runSqlStringApi(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final List<Object> params,
      final VaultConfig vaultConfig) {
    Objects.requireNonNull(dbType, DBTYPE_NULL);
    Objects.requireNonNull(database, DATABASE_NULL);
    Objects.requireNonNull(user, USER_NULL);
    Objects.requireNonNull(sql, "sql cannot be null");

    final SqlRequest request =
        new SqlRequest(
            new DatabaseRequest(dbType, database, user, resolvedVault(vaultConfig)),
            Optional.of(sql),
            Optional.empty(),
            params != null ? params : List.of());

    return sqlExecutorService.execute(request);
  }

  /**
   * Executes a parameter-less SQL statement and returns formatted string output.
   *
   * @param dbType database type
   * @param database JDBC connection URL
   * @param user database username
   * @param sql SQL statement
   * @param vaultConfig vault configuration; {@code null} for none
   * @return execution result with column-aligned text output
   */
  public ExecutionResult runSqlStringApi(
      final String dbType,
      final String database,
      final String user,
      final String sql,
      final VaultConfig vaultConfig) {
    return runSqlStringApi(dbType, database, user, sql, List.of(), vaultConfig);
  }

  /**
   * Executes a SQL script file and returns formatted string output for each statement.
   *
   * @param dbType database type
   * @param database JDBC connection URL
   * @param user database username
   * @param scriptPath absolute or relative path to the {@code .sql} script file
   * @param vaultConfig vault configuration; {@code null} for none
   * @return execution result with concatenated output of all statements
   */
  public ExecutionResult runScriptStringApi(
      final String dbType,
      final String database,
      final String user,
      final String scriptPath,
      final VaultConfig vaultConfig) {
    Objects.requireNonNull(dbType, DBTYPE_NULL);
    Objects.requireNonNull(database, DATABASE_NULL);
    Objects.requireNonNull(user, USER_NULL);
    Objects.requireNonNull(scriptPath, "scriptPath cannot be null");

    final SqlRequest request =
        new SqlRequest(
            new DatabaseRequest(dbType, database, user, resolvedVault(vaultConfig)),
            Optional.empty(),
            Optional.of(scriptPath),
            List.of());

    return sqlExecutorService.execute(request);
  }

  /**
   * Executes a stored procedure and returns string output of output parameters.
   *
   * @param dbType database type
   * @param database JDBC connection URL
   * @param user database username
   * @param procedureName stored procedure name (schema.procedure notation supported)
   * @param inParams comma-separated input parameters in {@code name:type:value} format; may be
   *     {@code null}
   * @param outParams comma-separated output parameters in {@code name:TYPE} format; may be {@code
   *     null}
   * @param vaultConfig vault configuration; {@code null} for none
   * @return execution result containing output parameter values
   */
  public ExecutionResult runProcedureStringApi(
      final String dbType,
      final String database,
      final String user,
      final String procedureName,
      final String inParams,
      final String outParams,
      final VaultConfig vaultConfig) {
    Objects.requireNonNull(dbType, DBTYPE_NULL);
    Objects.requireNonNull(database, DATABASE_NULL);
    Objects.requireNonNull(user, USER_NULL);
    Objects.requireNonNull(procedureName, "procedureName cannot be null");

    final ProcedureRequest request =
        new ProcedureRequest(
            new DatabaseRequest(dbType, database, user, resolvedVault(vaultConfig)),
            Optional.ofNullable(procedureName),
            Optional.ofNullable(inParams),
            Optional.ofNullable(outParams));

    return procedureExecutorService.execute(request);
  }

  private static VaultConfig resolvedVault(final VaultConfig vaultConfig) {
    return vaultConfig != null ? vaultConfig : VaultConfig.empty();
  }
}
