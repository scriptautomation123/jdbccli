package com.company.app.service.database.typedapi;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface for accessing a property of a Java bean from a JDBC ResultSet.
 *
 * <p>
 * <strong>Optimization Pattern:</strong> Property accessors are pre-compiled
 * and stored in
 * arrays indexed by column position, enabling O(1) property access vs Map
 * lookups. The accessor
 * encapsulates the setter method and type handler needed to populate a bean
 * property.
 *
 * @see ObjectResultHandler
 */
public interface JdbcPropertyAccessor {

  /**
   * Sets the property value on the target object by reading from the ResultSet.
   *
   * @param target      the object to set the property on
   * @param rs          the ResultSet to read the value from
   * @param columnIndex the 1-based column index
   * @throws SQLException                 if a database access error occurs
   * @throws ReflectiveOperationException if the property cannot be set
   */
  void setProperty(Object target, ResultSet rs, int columnIndex)
      throws SQLException, ReflectiveOperationException;

  /**
   * Returns the property name this accessor handles.
   *
   * @return the property name
   */
  String getPropertyName();

  /**
   * Returns the target type this accessor converts to.
   *
   * @return the property type
   */
  Class<?> getPropertyType();

  /**
   * Creates a no-op accessor for columns that don't map to any bean property.
   *
   * @param columnName the column name (for debugging)
   * @return a no-op accessor
   */
  static JdbcPropertyAccessor noOp(String columnName) {
    return new NoOpAccessor(columnName);
  }

  /**
   * Creates a property accessor for the given setter method and type handler.
   *
   * @param propertyName the property name
   * @param setter       the setter method
   * @param handler      the type handler for conversion
   * @return the property accessor
   */
  static JdbcPropertyAccessor create(String propertyName, Method setter, TypeHandler<?> handler) {
    return new TypeHandlerPropertyAccessor(propertyName, setter, handler);
  }

  /** No-op accessor implementation for unmapped columns. */
  final class NoOpAccessor implements JdbcPropertyAccessor {
    private final String columnName;

    NoOpAccessor(String columnName) {
      this.columnName = columnName;
    }

    @Override
    public void setProperty(Object target, ResultSet rs, int columnIndex) {
      // No-op: column doesn't map to any property
    }

    @Override
    public String getPropertyName() {
      return columnName;
    }

    @Override
    public Class<?> getPropertyType() {
      return Void.class;
    }
  }
}
