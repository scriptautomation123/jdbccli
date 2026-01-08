package com.company.app.service.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.company.app.service.service.model.ExecutionResult;
import com.company.app.service.util.LoggingUtils;

/**
 * Shared JDBC helper utilities for SQL execution to mirror procedure path and
 * simplify testing. Extracted from SqlExecutorService.
 */
public final class SqlJdbcHelper {

  private SqlJdbcHelper() {
    // utility class
  }

  /**
   * Reads the full contents of a script file.
   *
   * @param scriptPath path to the script
   * @return content of the script file
   * @throws IOException if reading fails
   */
  public static String readScriptFile(final String scriptPath) throws IOException {
    return Files.readString(Path.of(scriptPath));
  }

  /**
   * Splits script content into executable statements using ';' delimiter and
   * trims blanks.
   *
   * @param scriptContent script content
   * @return list of statements without trailing/leading whitespace
   */
  public static List<String> splitStatements(final String scriptContent) {
    if (scriptContent == null || scriptContent.isBlank()) {
      return List.of();
    }
    return Arrays.stream(scriptContent.split(";"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableList());
  }

  /**
   * Formats a ResultSet into a user-friendly string table.
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

    while (resultSet.next()) {
      final List<String> row = new ArrayList<>(columnCount);
      for (int i = 1; i <= columnCount; i++) {
        final Object value = resultSet.getObject(i);
        row.add(value != null ? value.toString() : "null");
      }
      rows.add(row);
    }

    if (rows.isEmpty()) {
      LoggingUtils.logStructuredError("sql_execution", "format_result", "SUCCESS", "No rows returned", null);
      return ExecutionResult.success("No rows returned");
    }

    final StringBuilder output = new StringBuilder();
    output.append(String.join(" | ", columnNames)).append("\n");
    final String separator = "-".repeat(Math.max(0, output.length()));
    output.append(separator).append("\n");

    for (final List<String> row : rows) {
      output.append(String.join(" | ", row)).append("\n");
    }

    LoggingUtils.logStructuredError(
        "sql_execution",
        "format_result",
        "SUCCESS",
        "Returned " + rows.size() + " rows",
        null);
    return ExecutionResult.success(output.toString());
  }
}
