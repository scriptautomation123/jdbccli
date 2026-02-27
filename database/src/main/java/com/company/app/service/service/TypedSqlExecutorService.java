package com.company.app.service.service;

import java.util.List;
import java.util.Objects;

import com.company.app.service.QueryExecutionException;
import com.company.app.service.auth.PasswordResolver;
import com.company.app.typedapi.QueryExecutorTyped;
import com.company.app.service.domain.model.DbRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.ProcedureRequest;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.util.LoggingUtils;

/**
 * Service for executing typed SQL SELECT queries using the optimized {@link
 * com.company.app.typedapi.ResultSetHandler} framework.
 *
 * <p>
 * <strong>Scope:</strong> SQL SELECT only — {@link ProcedureRequest} is
 * explicitly rejected.
 * Procedure execution belongs to {@link ProcedureExecutorService}. Formatted
 * string output belongs
 * to {@link SqlExecutorService}. All three services share
 * {@link DatabaseExecutionContext} for
 * connection and password lifecycle but never call each other.
 *
 * <p>
 * <strong>Performance:</strong> 18.5x faster than naive reflection per query
 * via LRU-cached
 * handler instances and pre-compiled {@code JdbcPropertyAccessor} arrays.
 *
 * <p>
 * <strong>Virtual threads:</strong> Execution is dispatched onto a virtual
 * thread by the
 * underlying {@link DatabaseExecutionContext}, so JDBC blocking I/O does not
 * pin a carrier thread.
 */
public final class TypedSqlExecutorService {

  private static final String TYPED_EXECUTION = "typed_execution";

  private final DatabaseExecutionContext executionContext;

  /**
   * Constructs with a password resolver. {@link DatabaseExecutionContext} is
   * created internally.
   *
   * @param passwordResolver password resolver for authentication
   */
  public TypedSqlExecutorService(final PasswordResolver passwordResolver) {
    this(
        new DatabaseExecutionContext(
            Objects.requireNonNull(passwordResolver, "PasswordResolver cannot be null")));
  }

  /**
   * Package-private constructor for testing with a custom execution context.
   *
   * @param executionContext custom execution context
   */
  TypedSqlExecutorService(final DatabaseExecutionContext executionContext) {
    this.executionContext = Objects.requireNonNull(executionContext, "DatabaseExecutionContext cannot be null");
  }

  /**
   * Executes a SELECT query and maps each row to an instance of
   * {@code resultClass}.
   *
   * <p>
   * The request must be a {@link SqlRequest} in SQL mode (not script mode).
   * Passing a {@link
   * ProcedureRequest} throws {@link QueryExecutionException}.
   *
   * @param <T>         the target bean type
   * @param request     the SQL request; must be a {@link SqlRequest}
   * @param resultClass class to map rows to (must follow JavaBean conventions)
   * @return non-null list of mapped objects; empty list when no rows matched
   * @throws QueryExecutionException if the request type is unsupported or query
   *                                 execution fails
   */
  public <T> List<T> execute(final DbRequest request, final Class<T> resultClass) {
    Objects.requireNonNull(request, "Request cannot be null");
    Objects.requireNonNull(resultClass, "Result class cannot be null");

    // Exhaustive sealed switch — compiler enforces all DbRequest subtypes are
    // handled.
    // No default: adding a new DbRequest subtype will produce a compile error here.
    return switch (request) {
      case SqlRequest sqlRequest -> executeTypedQuery(sqlRequest, resultClass);
      case ProcedureRequest ignored ->
        throw new QueryExecutionException(
            "TypedSqlExecutorService does not support ProcedureRequest; "
                + "use ProcedureExecutorService instead");
    };
  }

  private <T> List<T> executeTypedQuery(final SqlRequest request, final Class<T> resultClass) {

    final ExecutionResult result = executionContext.executeWithPasswordResolution(
        request,
        conn -> {
          try {
            final var typedResult = QueryExecutorTyped.executeTyped(
                conn,
                request
                    .sql()
                    .orElseThrow(
                        () -> new QueryExecutionException("SQL statement is required")),
                request.params(),
                resultClass);
            return ExecutionResult.success(
                java.util.Map.of(
                    "data", typedResult.data(), "rowCount", typedResult.rowCount()));
          } catch (QueryExecutionException e) {
            throw e;
          } catch (Exception e) {
            LoggingUtils.logStructuredError(
                TYPED_EXECUTION,
                "execute",
                "FAILED",
                "Typed query failed: " + e.getMessage(),
                e);
            return ExecutionResult.failure(1, "Query execution failed: " + e.getMessage());
          }
        });

    if (result.getExitCode() != 0) {
      throw new QueryExecutionException(result.getMessage());
    }

    @SuppressWarnings("unchecked") // Safe: data is always List<T> as set above
    final List<T> data = (List<T>) result.getData().get("data");
    return data != null ? data : List.of();
  }
}
