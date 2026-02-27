package com.company.app.stringapi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.company.app.service.database.QueryResult;

/**
 * String API query executor — formats SQL results as human-readable string
 * tables for CLI display.
 *
 * <p>
 * <strong>Architecture:</strong>
 *
 * <pre>
 * executeFormatted() → SqlJdbcHelper → String      (for CLI display)
 * </pre>
 *
 * <p>
 * For typed object mapping use {@link
 * com.company.app.typedapi.QueryExecutorTyped}.
 */
public final class QueryExecutor {

  private QueryExecutor() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Executes a query and returns a formatted string table for CLI display. Uses
   * direct string
   * building — no intermediate object allocation overhead.
   *
   * @param conn   database connection
   * @param sql    SQL SELECT statement
   * @param params query parameters (can be empty or {@code null})
   * @return formatted result with string table
   * @throws SQLException if query execution fails
   */
  public static QueryResult.FormattedResult executeFormatted(
      final Connection conn, final String sql, final List<Object> params) throws SQLException {

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      final List<Object> paramList = (params != null) ? params : List.of();
      for (int i = 0; i < paramList.size(); i++) {
        pstmt.setObject(i + 1, paramList.get(i));
      }

      try (ResultSet rs = pstmt.executeQuery()) {
        final var execResult = SqlJdbcHelper.formatResultSet(rs);
        final int rowCount = extractRowCount(execResult.getMessage());
        return new QueryResult.FormattedResult(execResult.getMessage(), rowCount);
      }
    }
  }

  /**
   * Extracts an approximate row count from the formatted output by counting
   * content lines. This is
   * a heuristic — production callers that need an exact count should track it
   * during formatting.
   */
  private static int extractRowCount(final String formattedOutput) {
    if (formattedOutput == null || formattedOutput.contains("No rows returned")) {
      return 0;
    }
    // Subtract header row and separator line
    final long lines = formattedOutput.lines().count();
    return (int) Math.max(0, lines - 2);
  }
}
