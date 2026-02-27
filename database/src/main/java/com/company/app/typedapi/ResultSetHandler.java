package com.company.app.typedapi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface for handling JDBC ResultSet conversion to domain objects.
 *
 * <p><strong>Architectural Pattern:</strong> ResultSetHandlers are cached by the {@link
 * DefaultResultSetHandlerFactory} based on query structure + bean type. Once created, they can
 * efficiently convert multiple rows without repeated reflection or type handler lookup.
 *
 * <p><strong>Performance Chain:</strong>
 *
 * <pre>
 * Query → ResultSetHandlerFactory (cached) → ObjectResultHandler (accessor array) → TypeHandler (registry)
 * </pre>
 *
 * First execution builds and caches the handler chain; subsequent executions hit cache at factory
 * level for near-hand-coded performance.
 *
 * @param <T> the target type to map rows to
 * @see DefaultResultSetHandlerFactory
 * @see ObjectResultHandler
 */
public interface ResultSetHandler<T> {

  /**
   * Handles a single row from the ResultSet and returns the mapped object.
   *
   * @param rs the ResultSet positioned at the current row
   * @return the mapped object, may be null
   * @throws SQLException if a database access error occurs
   */
  T handle(ResultSet rs) throws SQLException;

  /**
   * Handles multiple rows from the ResultSet and returns a list of mapped objects. The ResultSet
   * cursor should be positioned before the first row.
   *
   * @param rs the ResultSet to process
   * @return list of mapped objects
   * @throws SQLException if a database access error occurs
   */
  default List<T> handleAll(ResultSet rs) throws SQLException {
    List<T> results = new java.util.ArrayList<>();
    while (rs.next()) {
      T item = handle(rs);
      if (item != null) {
        results.add(item);
      }
    }
    return results;
  }

  /**
   * Returns the target type this handler maps to.
   *
   * @return the target class
   */
  Class<T> getTargetType();
}
