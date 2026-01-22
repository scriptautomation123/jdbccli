package com.company.app.service;

import java.util.List;

import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing SQL statements with vault authentication. Supports both
 * direct SQL execution and SQL script files with secure password resolution. Uses JdbcCliLibrary
 * for centralized service management.
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

  /** Default constructor for picocli */
  public ExecSqlCmd() {
    super();
  }

  /**
   * Executes the SQL command with vault authentication.
   *
   * @return exit code (0 for success, non-zero for failure)
   */
  @Override
  public Integer call() {
    try {
      final ExecutionResult result;
      if (script != null && !script.isBlank()) {
        result =
            getLibrary()
                .executeScript(getTypeString(), database, user, script, createVaultConfig());
      } else {
        result =
            getLibrary()
                .executeSql(
                    getTypeString(), database, user, sql, parseParams(params), createVaultConfig());
      }
      result.formatOutput(System.out); // NOSONAR
      return result.getExitCode();

    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute SQL", System.err); // NOSONAR
    }
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
