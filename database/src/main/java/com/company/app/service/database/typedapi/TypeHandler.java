package com.company.app.service.database.typedapi;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface for handling type conversions from JDBC ResultSet to Java objects.
 *
 * <p>
 * TypeHandlers are responsible for extracting values from a ResultSet column
 * and converting them
 * to the appropriate Java type. The registry singleton pattern avoids repeated
 * handler creation for
 * common types.
 *
 * <p>
 * <strong>Performance Pattern:</strong> TypeHandlers are cached in
 * {@link TypeHandlerRegistry}
 * and shared across all queries, paying the reflection cost once per type.
 *
 * @param <T> the Java type this handler converts to
 * @see TypeHandlerRegistry
 */
@FunctionalInterface
public interface TypeHandler<T> {

  /**
   * Extracts a value from the ResultSet at the given column index and converts it
   * to the target
   * type.
   *
   * @param rs          the ResultSet to read from
   * @param columnIndex the 1-based column index
   * @return the converted value, may be null
   * @throws SQLException if a database access error occurs
   */
  T getResult(ResultSet rs, int columnIndex) throws SQLException;
}
