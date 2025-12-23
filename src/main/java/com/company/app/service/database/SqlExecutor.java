package com.company.app.service.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

/**
 * Executes SQL statements and scripts with parameter binding and result
 * handling.
 * Supports both query and update operations with comprehensive error handling.
 */
public class SqlExecutor {

  /** Error status for failed operations */
  private static final String FAILED = "FAILED";

  /** Placeholder character for SQL parameters */
  private static final char PLACEHOLDER_CHAR = '?';

  /** Statement separator for SQL scripts */
  private static final String STATEMENT_SEPARATOR = ";";

  /** Context for SQL execution operations */
  private static final String SQL_EXECUTION = "sql_execution";

  /** Supplier for database connections */
  private final ConnectionSupplier connSupplier;

  /**
   * Immutable result object containing SQL execution results.
   * Supports both result sets and update counts with metadata.
   */
  public static class SqlResult {

    /** Result rows for query operations */
    private final List<Map<String, Object>> resultRows;

    /** Metadata for result set operations */
    private final ResultSetMetaData resultMetaData;

    /** Update count for DML operations */
    private final int resultUpdateCount;

    /** Flag indicating if this is a result set */
    private final boolean hasResultSet;

    /**
     * Constructs a new SqlResult.
     * 
     * @param rows        result rows (null for update operations)
     * @param metaData    result metadata (null for update operations)
     * @param updateCount number of affected rows
     * @param isResultSet true if this represents a result set
     */
    public SqlResult(
        final List<Map<String, Object>> rows,
        final ResultSetMetaData metaData,
        final int updateCount,
        final boolean isResultSet) {
      if (isResultSet && rows == null) {
        LoggingUtils.logStructuredError(
            "sql_result", "validation", "NULL_ROWS", "Rows cannot be null for result sets", null);
        throw new IllegalArgumentException("Rows cannot be null for result sets");
      }
      this.resultRows = rows;
      this.resultMetaData = metaData;
      this.resultUpdateCount = updateCount;
      this.hasResultSet = isResultSet;
    }

    /**
     * Creates a SqlResult for result set operations.
     * 
     * @param rows     result rows
     * @param metaData result metadata
     * @return new SqlResult instance
     */
    public static SqlResult ofResultSet(
        final List<Map<String, Object>> rows, final ResultSetMetaData metaData) {
      return new SqlResult(rows, metaData, -1, true);
    }

    /**
     * Creates a SqlResult for update operations.
     * 
     * @param updateCount number of affected rows
     * @return new SqlResult instance
     */
    public static SqlResult ofUpdateCount(final int updateCount) {
      return new SqlResult(null, null, updateCount, false);
    }

    /**
     * Gets the result rows.
     * 
     * @return list of result rows
     */
    public List<Map<String, Object>> getRows() {
      return resultRows;
    }

    /**
     * Gets the result metadata.
     * 
     * @return result metadata
     */
    public ResultSetMetaData getMetaData() {
      return resultMetaData;
    }

    /**
     * Gets the update count.
     * 
     * @return number of affected rows
     */
    public int getUpdateCount() {
      return resultUpdateCount;
    }

    /**
     * Checks if this result represents a result set.
     * 
     * @return true if this is a result set
     */
    public boolean isResultSet() {
      return hasResultSet;
    }

    /**
     * Checks if the result is empty.
     * 
     * @return true if no rows returned
     */
    public boolean isEmpty() {
      return resultRows == null || resultRows.isEmpty();
    }

    /**
     * Gets the number of rows in the result.
     * 
     * @return row count
     */
    public int getRowCount() {
      return resultRows != null ? resultRows.size() : 0;
    }

    /**
     * Gets the column names from the result metadata.
     * 
     * @return list of column names
     */
    public List<String> getColumnNames() {
      List<String> columnNames = Collections.emptyList();

      if (resultMetaData != null) {
        try {
          final List<String> names = new ArrayList<>();
          for (int i = 1; i <= resultMetaData.getColumnCount(); i++) {
            names.add(resultMetaData.getColumnName(i));
          }
          columnNames = names;
        } catch (Exception e) {
          LoggingUtils.logStructuredError(
              "sql_result", "get_column_names", FAILED, "Failed to get column names", e);
          columnNames = Collections.emptyList();
        }
      }

      return columnNames;
    }
  }

  /**
   * Functional interface for supplying database connections.
   */
  @FunctionalInterface
  public interface ConnectionSupplier {
    /**
     * Gets a database connection.
     * 
     * @return database connection
     * @throws SQLException if connection fails
     */
    Connection get() throws SQLException;
  }

  /**
   * Constructs a new SqlExecutor with a connection supplier.
   * 
   * @param connectionSupplier supplier for database connections
   */
  public SqlExecutor(final ConnectionSupplier connectionSupplier) {
    this.connSupplier = connectionSupplier;
  }

  /**
   * Executes a SQL statement with parameters.
   * 
   * @param sql    SQL statement to execute
   * @param params parameters to bind
   * @return execution result
   * @throws SQLException if execution fails
   */
  public SqlResult executeSql(final String sql, final List<Object> params) throws SQLException {
    LoggingUtils.logSqlExecution(sql, params.size());
    final int placeholderCount = countPlaceholders(sql);
    if (placeholderCount != params.size()) {
      LoggingUtils.logStructuredError(
          SQL_EXECUTION,
          "validation",
          "PLACEHOLDER_MISMATCH",
          "SQL placeholder count ("
              + placeholderCount
              + ") does not match parameter count ("
              + params.size()
              + ")",
          null);
      throw new IllegalArgumentException(
          "SQL placeholder count ("
              + placeholderCount
              + ") does not match parameter count ("
              + params.size()
              + ")");
    }
    try (Connection conn = connSupplier.get();
        java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
      for (int i = 0; i < params.size(); i++) {
        pstmt.setObject(i + 1, params.get(i));
      }
      final boolean isResultSet = pstmt.execute();
      SqlResult result = null;

      if (isResultSet) {
        result = handleResultSet(pstmt.getResultSet());
      } else {
        result = handleUpdateCount(pstmt.getUpdateCount());
      }

      return result;
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          SQL_EXECUTION, "execute", FAILED, "Failed to execute SQL with parameters: " + sql, e);
      throw ExceptionUtils.wrap(e, "Failed to execute SQL with parameters: " + sql);
    }
  }

  /**
   * Executes a SQL script with multiple statements.
   * 
   * @param script        SQL script to execute
   * @param resultHandler handler for each statement result
   * @throws SQLException if execution fails
   */
  public void executeSqlScript(final String script, final Consumer<SqlResult> resultHandler)
      throws SQLException {
    LoggingUtils.logSqlScriptExecution("script");
    final String[] statements = script.split(STATEMENT_SEPARATOR);

    for (final String sql : statements) {
      if (!isNullOrEmpty(sql)) {
        try {
          final SqlResult result = executeSql(sql, new ArrayList<>());
          resultHandler.accept(result);
        } catch (Exception e) {
          LoggingUtils.logStructuredError(
              "sql_script_execution",
              "execute_statement",
              FAILED,
              "Failed to execute SQL statement in script: " + sql,
              e);
          throw ExceptionUtils.wrap(e, "Failed to execute SQL statement in script: " + sql);
        }
      }
    }
  }

  private SqlResult handleResultSet(final ResultSet resultSet) throws SQLException {
    try {
      final ResultSetMetaData meta = resultSet.getMetaData();
      final int columnCount = meta.getColumnCount();
      final List<Map<String, Object>> rows = new ArrayList<>();

      while (resultSet.next()) {
        final Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= columnCount; i++) {
          row.put(meta.getColumnLabel(i), resultSet.getObject(i));
        }
        rows.add(row);
      }

      return SqlResult.ofResultSet(rows, meta);
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          SQL_EXECUTION, "handle_result_set", FAILED, "Failed to handle SQL result set", e);
      throw ExceptionUtils.wrap(e, "Failed to handle SQL result set");
    }
  }

  private SqlResult handleUpdateCount(final int updateCount) {
    return SqlResult.ofUpdateCount(updateCount);
  }

  /**
   * Counts the number of placeholder characters in the SQL statement.
   * 
   * @param sql SQL statement to analyze
   * @return number of placeholder characters
   */
  private int countPlaceholders(final String sql) {
    int placeholderCount = 0;
    for (int i = 0; i < sql.length(); i++) {
      if (sql.charAt(i) == PLACEHOLDER_CHAR) {
        placeholderCount++;
      }
    }
    return placeholderCount;
  }

  private static boolean isNullOrEmpty(final String value) {
    return value == null || value.trim().isEmpty();
  }
}
