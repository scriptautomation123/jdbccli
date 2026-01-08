package com.company.app.service.service;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.database.SqlJdbcHelper;
import com.company.app.service.service.model.DbRequest;
import com.company.app.service.service.model.ExecutionResult;
import com.company.app.service.service.model.SqlRequest;
import com.company.app.service.util.LoggingUtils;

/**
 * Service for executing SQL statements and scripts with database connections.
 * Uses composition with DatabaseExecutionContext instead of inheritance
 * for better flexibility and testability.
 */
public final class SqlExecutorService {

    /** SQL execution operation identifier */
    private static final String SQL_EXECUTION = "sql_execution";

    /** Script execution operation identifier */
    private static final String SCRIPT_EXECUTION = "script_execution";

    /** Success status for operations */
    private static final String SUCCESS = "SUCCESS";

    /** Failed status for operations */
    private static final String FAILED = "FAILED";

    /** Started status for operations */
    private static final String STARTED = "STARTED";

    /** Rows affected message template */
    private static final String ROWS_AFFECTED_MSG = "Rows affected: ";

    /** Execution context handles password resolution and connection management */
    private final DatabaseExecutionContext executionContext;

    /**
     * Constructs a new SqlExecutorService with password resolver.
     * 
     * @param passwordResolver password resolver for authentication
     */
    public SqlExecutorService(final PasswordResolver passwordResolver) {
        if (passwordResolver == null) {
            throw new IllegalArgumentException("PasswordResolver cannot be null");
        }
        this.executionContext = new DatabaseExecutionContext(passwordResolver);
    }

    /**
     * Package-private constructor for testing with custom execution context.
     * 
     * @param executionContext custom execution context
     */
    SqlExecutorService(final DatabaseExecutionContext executionContext) {
        if (executionContext == null) {
            throw new IllegalArgumentException("DatabaseExecutionContext cannot be null");
        }
        this.executionContext = executionContext;
    }

    /**
     * Executes a SQL request with automatic password resolution and 
     * connection management.
     * 
     * @param request SQL request to execute
     * @return execution result
     */
    public ExecutionResult execute(final DbRequest request) {
        if (request == null) {
            return ExecutionResult.failure(1, "[ERROR] Request cannot be null");
        }

        if (!(request instanceof SqlRequest sqlRequest)) {
            return ExecutionResult.failure(
                1, "[ERROR] Unsupported request type: " + request.getClass().getName());
        }

        // Delegate to execution context with lambda for SQL-specific logic
        return executionContext.executeWithPasswordResolution(
            request, 
            conn -> executeWithConnection(sqlRequest, conn));
    }

    /**
     * Executes SQL-specific logic with an established connection. 
     * This is the service-specific implementation that gets called by the context. 
     * 
     * @param request    SQL request containing statement or script
     * @param connection database connection (managed by execution context)
     * @return execution result
     * @throws SQLException if execution fails
     */
    ExecutionResult executeWithConnection(
            final SqlRequest request, 
            final Connection connection) throws SQLException {

        if (request. isScriptMode()) {
            return executeScript(request, connection);
        } else if (request.isSqlMode()) {
            return executeSingleSql(request, connection);
        } else {
            return ExecutionResult.failure(
                1, "[ERROR] Either SQL statement or --script must be specified");
        }
    }

    /**
     * Executes a single SQL statement with optional parameters.
     * 
     * @param request    SQL request containing statement and parameters
     * @param connection database connection
     * @return execution result
     * @throws SQLException if execution fails
     */
    private ExecutionResult executeSingleSql(
            final SqlRequest request, 
            final Connection connection) throws SQLException {
        
        LoggingUtils.logStructuredError(
            SQL_EXECUTION,
            "execute_single",
            STARTED,
            "Executing SQL:  " + request.sql().orElse(""),
            null);

        try {
            if (request.params().isEmpty()) {
                return executeStatement(request.sql().orElse(""), connection);
            } else {
                return executePreparedStatement(
                    request.sql().orElse(""), 
                    request.params(), 
                    connection);
            }
        } catch (SQLException exception) {
            LoggingUtils.logStructuredError(
                SQL_EXECUTION,
                "execute_single",
                FAILED,
                "SQL execution failed: " + exception.getMessage(),
                exception);
            throw exception;
        }
    }

    /**
     * Executes a SQL statement without parameters.
     * 
     * @param sql        SQL statement to execute
     * @param connection database connection
     * @return execution result
     * @throws SQLException if execution fails
     */
    private ExecutionResult executeStatement(
            final String sql, 
            final Connection connection) throws SQLException {
        
        try (Statement statement = connection.createStatement()) {
            final boolean hasResults = statement.execute(sql);

            if (hasResults) {
                return SqlJdbcHelper.formatResultSet(statement.getResultSet());
            } else {
                final int updateCount = statement.getUpdateCount();
                LoggingUtils.logStructuredError(
                    SQL_EXECUTION,
                    "execute_statement",
                    SUCCESS,
                    ROWS_AFFECTED_MSG + updateCount,
                    null);
                return ExecutionResult.success(ROWS_AFFECTED_MSG + updateCount);
            }
        }
    }

    /**
     * Executes a prepared statement with parameters.
     * 
     * @param sql        SQL statement with placeholders
     * @param params     parameters to bind
     * @param connection database connection
     * @return execution result
     * @throws SQLException if execution fails
     */
    private ExecutionResult executePreparedStatement(
            final String sql, 
            final List<Object> params,
            final Connection connection) throws SQLException {
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }

            final boolean hasResults = statement. execute();

            if (hasResults) {
                return SqlJdbcHelper.formatResultSet(statement.getResultSet());
            } else {
                final int updateCount = statement.getUpdateCount();
                LoggingUtils. logStructuredError(
                    SQL_EXECUTION,
                    "execute_prepared",
                    SUCCESS,
                    ROWS_AFFECTED_MSG + updateCount,
                    null);
                return ExecutionResult.success(ROWS_AFFECTED_MSG + updateCount);
            }
        }
    }

    /**
     * Executes a SQL script file. 
     * 
     * @param request    SQL request containing script file path
     * @param connection database connection
     * @return execution result
     * @throws SQLException if execution fails
     */
    private ExecutionResult executeScript(
            final SqlRequest request, 
            final Connection connection) throws SQLException {
        
        LoggingUtils.logStructuredError(
            SCRIPT_EXECUTION,
            "execute_script",
            STARTED,
            "Executing script: " + request.script().orElse(""),
            null);

        try {
            final String scriptContent = SqlJdbcHelper.readScriptFile(request.script().orElse(""));
            final List<String> results = new ArrayList<>();

            for (final String statement : SqlJdbcHelper.splitStatements(scriptContent)) {
                final ExecutionResult result = executeStatement(statement, connection);
                results.add(result.getMessage());
            }

            final String combinedResults = String.join("\n", results);
            LoggingUtils.logStructuredError(
                SCRIPT_EXECUTION,
                "execute_script",
                SUCCESS,
                "Script execution completed",
                null);
            return ExecutionResult. success(combinedResults);

        } catch (IOException exception) {
            LoggingUtils.logStructuredError(
                SCRIPT_EXECUTION,
                "read_script",
                FAILED,
                "Failed to read script file: " + exception.getMessage(),
                exception);
            throw new SQLException(
                "Failed to read script file: " + request.script().orElse(""), 
                exception);
        }
    }
}