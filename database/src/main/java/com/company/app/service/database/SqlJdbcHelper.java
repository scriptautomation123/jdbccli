package com.company.app.service.database;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.util.LoggingUtils;

/**
 * JDBC helper utilities for SQL result formatting. Script parsing has been moved to {@link
 * ScriptParser}.
 */
public final class SqlJdbcHelper {

  /** Maximum rows to format to prevent OutOfMemoryErrors */
  private static final int MAX_ROWS = 1000;

  private SqlJdbcHelper() {
    // utility class
  }

  /**
   * Formats a ResultSet into a user-friendly string table with column alignment. Limits output to
   * MAX_ROWS to prevent memory exhaustion.
   *
   * @param resultSet JDBC ResultSet
   * @return execution result wrapping the formatted output
   * @throws SQLException on JDBC errors
   */
  public static ExecutionResult formatResultSet(final ResultSet resultSet) throws SQLException {
    final ResultSetMetaData metaData = resultSet.getMetaData();
    final int columnCount = metaData.getColumnCount();
    final List<String> columnNames = new ArrayList<>(columnCount);
    final List<List<String>> rows = new ArrayList<>();

    for (int i = 1; i <= columnCount; i++) {
      columnNames.add(metaData.getColumnName(i));
    }

    int rowCount = 0;
    while (resultSet.next() && rowCount < MAX_ROWS) {
      final List<String> row = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        final Object value = resultSet.getObject(i);
        row.add(value != null ? value.toString() : "null");
      }
      rows.add(row);
      rowCount++;
    }

    if (rows.isEmpty()) {
      LoggingUtils.logStructuredError(
          "sql_execution", "format_result", "SUCCESS", "No rows returned", null);
      return ExecutionResult.success("No rows returned");
    }

    // Calculate column widths for alignment
    final int[] maxWidths = new int[columnCount];
    for (int i = 0; i < columnCount; i++) {
      maxWidths[i] = columnNames.get(i).length();
    }
    for (List<String> row : rows) {
      for (int i = 0; i < columnCount; i++) {
        maxWidths[i] = Math.max(maxWidths[i], row.get(i).length());
      }
    }

    final StringBuilder output = new StringBuilder();

    // Header
    formatRow(output, columnNames, maxWidths);

    // Separator based on actual content width
    int totalWidth = Arrays.stream(maxWidths).sum() + (3 * (columnCount - 1));
    output.append("-".repeat(totalWidth)).append("\n");

    // Data
    for (final List<String> row : rows) {
      formatRow(output, row, maxWidths);
    }

    if (resultSet.next()) {
      output.append("... (results truncated at ").append(MAX_ROWS).append(" rows)");
    }

    LoggingUtils.logStructuredError(
        "sql_execution", "format_result", "SUCCESS", "Formatted " + rows.size() + " rows", null);
    return ExecutionResult.success(output.toString());
  }

  private static void formatRow(StringBuilder sb, List<String> row, int[] widths) {
    for (int i = 0; i < row.size(); i++) {
      String val = row.get(i);
      sb.append(val).append(" ".repeat(widths[i] - val.length()));
      if (i < row.size() - 1) {
        sb.append(" | ");
      }
    }
    sb.append("\n");
  }
}
