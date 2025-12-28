package com.company.app.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.ProcedureExecutor;
import com.company.app.service.util.LoggingUtils;

/**
 * Database service implementation for executing stored procedures.
 * Extends AbstractDatabaseExecutionService to provide procedure-specific
 * execution logic.
 */
public class ProcedureExecutorService extends AbstractDatabaseExecutionService {

  /** Static procedure executor instance for database operations */
  private static final ProcedureExecutor PROCEDURE_EXECUTOR = new ProcedureExecutor();
  private static final String PROCEDURE_EXECUTION_EVENT = "procedure_execution";
  private static final String VALIDATION_EVENT = "validation";
  private static final String FAILED_STATUS = "FAILED";
  private static final String ERR_DB_CONN_NULL = "Database connection cannot be null";

  /**
   * Constructs a new DatabaseService with password resolver.
   * 
   * @param passwordResolver resolver for database passwords
   */
  public ProcedureExecutorService(final PasswordResolver passwordResolver) {
    super(passwordResolver);
  }

  @Override
  protected ExecutionResult executeWithConnection(final DatabaseRequest request, final Connection conn)
      throws SQLException {
    if (conn == null) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT,
          VALIDATION_EVENT,
          FAILED_STATUS,
          ERR_DB_CONN_NULL,
          null);
      return ExecutionResult.failure(1, ERR_DB_CONN_NULL);
    }
    if (request instanceof ProcedureRequest) {
      final ProcedureRequest procedureRequest = (ProcedureRequest) request;
      return executeProcedure(procedureRequest, conn);
    } else {
      LoggingUtils.logStructuredError(
          "database_execution",
          "execute",
          "UNSUPPORTED_REQUEST_TYPE",
          "Unsupported request type: " + request.getClass().getName(),
          null);
      throw new IllegalArgumentException(
          "Unsupported request type: " + request.getClass().getName());
    }
  }

  /**
   * Executes a stored procedure with the given request and connection.
   * 
   * @param request procedure request containing execution parameters
   * @param conn    database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executeProcedure(final ProcedureRequest request, final Connection conn)
      throws SQLException {
    final Map<String, Object> result = executeProcedureInternal(
        conn, request.getProcedure(), request.getInput(), request.getOutput());

    return ExecutionResult.success(result);
  }

  /**
   * Internal method to execute a stored procedure with validation.
   * 
   * @param conn      database connection
   * @param procedure procedure name to execute
   * @param input     input parameters string
   * @param output    output parameters string
   * @return execution result map
   * @throws SQLException if execution fails
   */
  private Map<String, Object> executeProcedureInternal(
      final Connection conn, final String procedure, final String input, final String output) throws SQLException {

    final Map<String, Object> validationResult = validateProcedureParameters(conn, procedure);
    if (!validationResult.isEmpty()) {
      return validationResult;
    }

    LoggingUtils.logProcedureExecution(procedure, input, output);

    final Map<String, Object> result = PROCEDURE_EXECUTOR.executeProcedureWithStrings(conn, procedure, input, output);

    LoggingUtils.logProcedureExecutionSuccess(procedure);
    return result;
  }

  /**
   * Validates procedure execution parameters.
   * 
   * @param conn      database connection to validate
   * @param procedure procedure name to validate
   * @return error result map if validation fails, null if validation passes
   */
  private Map<String, Object> validateProcedureParameters(final Connection conn, final String procedure) {
    if (conn == null) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT,
          VALIDATION_EVENT,
          FAILED_STATUS,
          ERR_DB_CONN_NULL,
          null);
      return java.util.Collections.singletonMap("error", ERR_DB_CONN_NULL);
    }
    if (isNullOrBlank(procedure)) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT,
          VALIDATION_EVENT,
          FAILED_STATUS,
          "Procedure name cannot be null or empty",
          null);
      return java.util.Collections.singletonMap("error", "Procedure name cannot be null or empty");
    }
    return java.util.Collections.emptyMap();
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
}
