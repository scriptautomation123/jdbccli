package com.company.app.service.database;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.company.app.service.domain.model.ExecutionResult;

/**
 * Strategy interface for formatting SQL query results.
 *
 * <p>Current implementation uses JDBC ResultSet. This abstraction allows future support for
 * alternative data sources (Arrow Flight SQL, etc.) without changing the service layer.
 *
 * <p><strong>Future Extension Point:</strong> To add Arrow Flight SQL support:
 *
 * <ol>
 *   <li>Create {@code ArrowResultFormatter} implementing a parallel interface
 *   <li>Add Flight SQL client in a separate module (heavy dependencies)
 *   <li>Use for analytics-focused databases (DuckDB, Dremio, Snowflake)
 * </ol>
 *
 * <p><strong>When to consider Arrow Flight SQL:</strong>
 *
 * <ul>
 *   <li>Result sets > 100K rows regularly
 *   <li>Columnar analytics databases
 *   <li>Need parallel data transfer
 *   <li>Database has Flight SQL driver (not Oracle)
 * </ul>
 */
@FunctionalInterface
public interface ResultFormatter {

  /**
   * Formats a JDBC ResultSet into an ExecutionResult.
   *
   * @param resultSet the result set to format
   * @return formatted execution result
   * @throws SQLException if reading the result set fails
   */
  ExecutionResult format(ResultSet resultSet) throws SQLException;

  /**
   * Default JDBC implementation using SqlJdbcHelper. Suitable for CLI usage with reasonable result
   * sizes.
   */
  ResultFormatter JDBC_DEFAULT = SqlJdbcHelper::formatResultSet;
}
