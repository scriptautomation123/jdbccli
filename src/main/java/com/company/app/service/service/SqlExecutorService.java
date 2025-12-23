package com.company.app.service.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.util.LoggingUtils;

/**
 * Service for executing SQL statements and scripts with database connections.
 * Extends AbstractDatabaseExecutionService to provide SQL-specific execution
 * logic.
 */
public final class SqlExecutorService extends AbstractDatabaseExecutionService {

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

    /**
     * Constructs a new SqlExecutionService with password resolver.
     * 
     * @param passwordResolver password resolver for authentication
     */
    public SqlExecutorService(final PasswordResolver passwordResolver) {
        super(passwordResolver);
    }

    @Override
    protected ExecutionResult executeWithConnection(final DatabaseRequest request, final Connection connection)
            throws SQLException {
        final SqlRequest sqlRequest = (SqlRequest) request;

        if (sqlRequest.isScriptMode()) {
            return executeScript(sqlRequest, connection);
        } else if (sqlRequest.isSqlMode()) {
            return executeSingleSql(sqlRequest, connection);
        } else {
            return ExecutionResult.failure(1, "[ERROR] Either SQL statement or --script must be specified");
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
    private ExecutionResult executeSingleSql(final SqlRequest request, final Connection connection)
            throws SQLException {
        LoggingUtils.logStructuredError(
                SQL_EXECUTION,
                "execute_single",
                STARTED,
                "Executing SQL: " + request.getSql(),
                null);

        try {
            if (request.getParams().isEmpty()) {
                return executeStatement(request.getSql(), connection);
            } else {
                return executePreparedStatement(request.getSql(), request.getParams(), connection);
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
    private ExecutionResult executeStatement(final String sql, final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            final boolean hasResults = statement.execute(sql);

            if (hasResults) {
                return formatResultSet(statement.getResultSet());
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
    private ExecutionResult executePreparedStatement(final String sql, final List<Object> params,
            final Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }

            final boolean hasResults = statement.execute();

            if (hasResults) {
                return formatResultSet(statement.getResultSet());
            } else {
                final int updateCount = statement.getUpdateCount();
                LoggingUtils.logStructuredError(
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
    private ExecutionResult executeScript(final SqlRequest request, final Connection connection) throws SQLException {
        LoggingUtils.logStructuredError(
                SCRIPT_EXECUTION,
                "execute_script",
                STARTED,
                "Executing script: " + request.getScript(),
                null);

        try {
            final String scriptContent = readScriptFile(request.getScript());
            final List<String> results = new ArrayList<>();

            final String[] statements = scriptContent.split(";");
            for (final String statement : statements) {
                final String trimmedStatement = statement.trim();
                if (!trimmedStatement.isEmpty()) {
                    final ExecutionResult result = executeStatement(trimmedStatement, connection);
                    results.add(result.getMessage());
                }
            }

            final String combinedResults = String.join("\n", results);
            LoggingUtils.logStructuredError(
                    SCRIPT_EXECUTION,
                    "execute_script",
                    SUCCESS,
                    "Script execution completed",
                    null);
            return ExecutionResult.success(combinedResults);

        } catch (IOException exception) {
            LoggingUtils.logStructuredError(
                    SCRIPT_EXECUTION,
                    "read_script",
                    FAILED,
                    "Failed to read script file: " + exception.getMessage(),
                    exception);
            throw new SQLException("Failed to read script file: " + request.getScript(), exception);
        }
    }

    /**
     * Reads the content of a script file.
     * 
     * @param scriptPath path to the script file
     * @return script content
     * @throws IOException if file cannot be read
     */
    private String readScriptFile(final String scriptPath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(scriptPath)));
    }

    /**
     * Formats a result set into a readable string output.
     * 
     * @param resultSet result set to format
     * @return formatted result string
     * @throws SQLException if formatting fails
     */
    private ExecutionResult formatResultSet(final ResultSet resultSet) throws SQLException {
        final ResultSetMetaData metaData = resultSet.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final List<String> columnNames = new ArrayList<>(columnCount);
        final List<List<String>> rows = new ArrayList<>();

        // Get column names
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnName(i));
        }

        // Get data rows
        while (resultSet.next()) {
            final List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                final Object value = resultSet.getObject(i);
                row.add(value != null ? value.toString() : "null");
            }
            rows.add(row);
        }

        if (rows.isEmpty()) {
            LoggingUtils.logStructuredError(
                    SQL_EXECUTION,
                    "format_result",
                    SUCCESS,
                    "No rows returned",
                    null);
            return ExecutionResult.success("No rows returned");
        }

        // Build formatted output
        final StringBuilder output = new StringBuilder();
        output.append(String.join(" | ", columnNames)).append("\n");
        final String separator = String.join("", java.util.Collections.nCopies(output.length(), "-"));
        output.append(separator).append("\n");

        for (final List<String> row : rows) {
            output.append(String.join(" | ", row)).append("\n");
        }

        LoggingUtils.logStructuredError(
                SQL_EXECUTION,
                "format_result",
                SUCCESS,
                "Returned " + rows.size() + " rows",
                null);
        return ExecutionResult.success(output.toString());
    }
}