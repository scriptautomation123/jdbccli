package com.company.app.service.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.ProcedureExecutor;
import com.company.app.service.domain.model.DbRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.ProcedureRequest;
import com.company.app.service.util.LoggingUtils;
import com.company.app.service.util.StringUtils;

/**
 * Database service implementation for executing stored procedures. Uses composition with
 * DatabaseExecutionContext instead of inheritance for better flexibility and testability.
 */
public final class ProcedureExecutorService {

  /** Static procedure executor instance for database operations */
  private static final ProcedureExecutor PROCEDURE_EXECUTOR = new ProcedureExecutor();

  private static final String PROCEDURE_EXECUTION_EVENT = "procedure_execution";
  private static final String VALIDATION_EVENT = "validation";
  private static final String FAILED_STATUS = "FAILED";
  private static final String ERR_DB_CONN_NULL = "Database connection cannot be null";
  private static final String ERR_UNSUPPORTED_REQUEST = "Unsupported request type";

  /** Execution context handles password resolution and connection management */
  private final DatabaseExecutionContext executionContext;

  /**
   * Constructs a new ProcedureExecutorService with password resolver.
   *
   * @param passwordResolver resolver for database passwords
   */
  public ProcedureExecutorService(final PasswordResolver passwordResolver) {
    this(
        new DatabaseExecutionContext(
            java.util.Objects.requireNonNull(passwordResolver, "PasswordResolver cannot be null")));
  }

  /**
   * Package-private constructor for testing with custom execution context.
   *
   * @param executionContext custom execution context
   */
  ProcedureExecutorService(final DatabaseExecutionContext executionContext) {
    this.executionContext =
        java.util.Objects.requireNonNull(
            executionContext, "DatabaseExecutionContext cannot be null");
  }

  /**
   * Executes a procedure request with automatic password resolution and connection management.
   *
   * @param request procedure request to execute
   * @return execution result
   */
  public ExecutionResult execute(final DbRequest request) {
    if (request == null) {
      return ExecutionResult.failure(1, "[ERROR] Request cannot be null");
    }

    if (!(request instanceof ProcedureRequest procedureRequest)) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT,
          VALIDATION_EVENT,
          FAILED_STATUS,
          ERR_UNSUPPORTED_REQUEST + ": " + request.getClass().getName(),
          null);
      return ExecutionResult.failure(
          1, "[ERROR] " + ERR_UNSUPPORTED_REQUEST + ": " + request.getClass().getName());
    }

    // Delegate to execution context with lambda for procedure-specific logic
    return executionContext.executeWithPasswordResolution(
        request, conn -> executeWithConnection(procedureRequest, conn));
  }

  /**
   * Executes procedure-specific logic with an established connection. This is the service-specific
   * implementation that gets called by the context.
   *
   * @param request procedure request containing execution parameters
   * @param conn database connection (managed by execution context)
   * @return execution result
   * @throws SQLException if execution fails
   */
  ExecutionResult executeWithConnection(final ProcedureRequest request, final Connection conn)
      throws SQLException {

    if (conn == null) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT, VALIDATION_EVENT, FAILED_STATUS, ERR_DB_CONN_NULL, null);
      return ExecutionResult.failure(1, "[ERROR] " + ERR_DB_CONN_NULL);
    }

    return executeProcedure(request, conn);
  }

  /**
   * Executes a stored procedure with the given request and connection.
   *
   * @param request procedure request containing execution parameters
   * @param conn database connection
   * @return execution result
   * @throws SQLException if execution fails
   */
  private ExecutionResult executeProcedure(final ProcedureRequest request, final Connection conn)
      throws SQLException {

    final Map<String, Object> validationResult =
        validateProcedureParameters(conn, request.procedure().orElse(null));

    if (!validationResult.isEmpty()) {
      return ExecutionResult.failure(1, validationResult.toString());
    }

    final Map<String, Object> result =
        executeProcedureInternal(
            conn,
            request.procedure().orElse(null),
            request.input().orElse(null),
            request.output().orElse(null));

    return ExecutionResult.success(result);
  }

  /**
   * Internal method to execute a stored procedure with validation.
   *
   * @param conn database connection
   * @param procedure procedure name to execute
   * @param input input parameters string
   * @param output output parameters string
   * @return execution result map
   * @throws SQLException if execution fails
   */
  private Map<String, Object> executeProcedureInternal(
      final Connection conn, final String procedure, final String input, final String output)
      throws SQLException {

    LoggingUtils.logProcedureExecution(procedure, input, output);

    final Map<String, Object> result =
        PROCEDURE_EXECUTOR.executeProcedureWithStrings(conn, procedure, input, output);

    LoggingUtils.logProcedureExecutionSuccess(procedure);
    return result;
  }

  /**
   * Validates procedure execution parameters.
   *
   * @param conn database connection to validate
   * @param procedure procedure name to validate
   * @return error result map if validation fails, empty map if validation passes
   */
  private Map<String, Object> validateProcedureParameters(
      final Connection conn, final String procedure) {

    if (conn == null) {
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT, VALIDATION_EVENT, FAILED_STATUS, ERR_DB_CONN_NULL, null);
      return java.util.Collections.singletonMap("error", ERR_DB_CONN_NULL);
    }

    if (StringUtils.isNullOrBlank(procedure)) {
      final String errorMsg = "Procedure name cannot be null or empty";
      LoggingUtils.logStructuredError(
          PROCEDURE_EXECUTION_EVENT, VALIDATION_EVENT, FAILED_STATUS, errorMsg, null);
      return java.util.Collections.singletonMap("error", errorMsg);
    }

    return java.util.Collections.emptyMap();
  }
}
