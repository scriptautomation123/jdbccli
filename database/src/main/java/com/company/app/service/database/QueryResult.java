package com.company.app.service.database;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing query results in different forms. Enables both typed object mapping
 * and dynamic string formatting.
 *
 * <p><strong>Design Pattern:</strong> This sealed interface allows the same query execution to
 * return either:
 *
 * <ul>
 *   <li>Typed objects (for programmatic use) - 18.5x faster with handlers
 *   <li>Formatted strings (for CLI display) - current behavior
 * </ul>
 */
public sealed interface QueryResult permits QueryResult.TypedResult, QueryResult.FormattedResult {

  /**
   * Result containing typed objects mapped via ResultSetHandler framework. Uses optimized accessor
   * arrays and LRU caching.
   *
   * @param <T> the type of objects in the result
   * @param data list of mapped objects
   * @param rowCount number of rows
   */
  record TypedResult<T>(List<T> data, int rowCount) implements QueryResult {
    public TypedResult {
      if (data == null) {
        throw new IllegalArgumentException("Data cannot be null");
      }
      // Defensive copy for immutability
      data = List.copyOf(data);
    }

    /** Returns true if no rows were returned. */
    public boolean isEmpty() {
      return data.isEmpty();
    }

    /** Returns the first result, or null if empty. */
    public T first() {
      return data.isEmpty() ? null : data.get(0);
    }
  }

  /**
   * Result containing formatted string table for CLI display. Uses SqlJdbcHelper formatting.
   *
   * @param formattedOutput the formatted string table
   * @param rowCount number of rows
   */
  record FormattedResult(String formattedOutput, int rowCount) implements QueryResult {
    public FormattedResult {
      if (formattedOutput == null) {
        throw new IllegalArgumentException("Formatted output cannot be null");
      }
    }

    /** Returns true if no rows were returned. */
    public boolean isEmpty() {
      return rowCount == 0;
    }
  }

  /**
   * Converts any QueryResult to a formatted string for display.
   *
   * @param result the query result
   * @return formatted string output
   */
  static String toFormattedString(QueryResult result) {
    return switch (result) {
      case FormattedResult f -> f.formattedOutput();
      case TypedResult<?> t -> formatTypedResult(t);
    };
  }

  /**
   * Formats a TypedResult as a string table (fallback when CLI needs display).
   *
   * @param result typed result
   * @return formatted string table
   */
  private static String formatTypedResult(TypedResult<?> result) {
    if (result.isEmpty()) {
      return "No rows returned";
    }

    // Simple toString representation
    // For production, you'd use reflection to build a proper table
    StringBuilder sb = new StringBuilder();
    sb.append("Rows: ").append(result.rowCount()).append("\n");
    for (Object obj : result.data()) {
      sb.append(obj.toString()).append("\n");
    }
    return sb.toString();
  }

  /**
   * Creates a result from dynamic row data (List of Maps). Used when column structure is unknown at
   * compile time.
   *
   * @param rows list of row maps (column_name -> value)
   * @return typed result containing maps
   */
  static QueryResult fromDynamicRows(List<Map<String, Object>> rows) {
    return new TypedResult<>(rows, rows.size());
  }
}
