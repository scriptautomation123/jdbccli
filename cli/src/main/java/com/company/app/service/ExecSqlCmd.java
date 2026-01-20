package com.company.app.service;

import java.util.List;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.service.SqlExecutorService;
import com.company.app.service.service.model.DatabaseRequest;
import com.company.app.service.service.model.SqlRequest;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing SQL statements with vault authentication. Supports both
 * direct SQL execution and SQL script files with secure password resolution. Uses composition-based
 * service architecture for better testability.
 */
@Command(
    name = "exec-sql",
    mixinStandardHelpOptions = true,
    description = "Vault-authenticated SQL execution",
    version = "1.0.0")
public class ExecSqlCmd extends BaseDatabaseCliCommand {

  /** SQL statement to execute */
  @Parameters(index = "0", description = "SQL statement to execute", arity = "0..1")
  private String sql;

  /** SQL script file path */
  @Option(names = "--script", description = "SQL script file path")
  private String script;

  /** SQL parameters in format value1,value2,value3 */
  @Option(names = "--params", description = "SQL parameters (value1,value2,value3)")
  private String params;

  /** Service for executing SQL - uses composition */
  private final SqlExecutorService service;

  /**
   * Constructs a new ExecSqlCmd with SQL execution service. Initializes service with password
   * resolver for vault authentication.
   */
  public ExecSqlCmd() {
    super();
    this.service = new SqlExecutorService(new PasswordResolver(this::promptForPassword));
  }

  /**
   * Package-private constructor for testing with custom service.
   *
   * @param service custom SQL executor service
   */
  ExecSqlCmd(final SqlExecutorService service) {
    super();
    this.service = service;
  }

  /**
   * Executes the SQL command with vault authentication.
   *
   * @return exit code (0 for success, non-zero for failure)
   */
  @Override
  public Integer call() {
    try {
      final SqlRequest request = buildSqlRequest();
      final ExecutionResult result = service.execute(request);
      result.formatOutput(System.out); // NOSONAR
      return result.getExitCode();

    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute SQL", System.err); // NOSONAR
    }
  }

  /**
   * Builds a SQL request from command-line arguments. Builds a SQL request from command-line
   * arguments.
   *
   * @return configured SQL request
   */
  private SqlRequest buildSqlRequest() {
    return new SqlRequest(
        new DatabaseRequest(type, database, user, createVaultConfig()),
        java.util.Optional.ofNullable(sql),
        java.util.Optional.ofNullable(script),
        parseParams(params));
  }

  /**
   * Parses comma-separated parameter string into list of objects.
   *
   * @param params comma-separated parameter string
   * @return list of parameter values, empty list if params is null/empty
   */
  private List<Object> parseParams(final String params) {
    if (params == null || params.isBlank()) {
      return java.util.Collections.emptyList();
    }

    final String[] paramArray = params.split(",");
    final List<Object> paramList = new java.util.ArrayList<>(paramArray.length);

    for (final String param : paramArray) {
      final String trimmed = param.trim();
      if (!trimmed.isEmpty()) {
        paramList.add(trimmed);
      }
    }

    return paramList;
  }
}
