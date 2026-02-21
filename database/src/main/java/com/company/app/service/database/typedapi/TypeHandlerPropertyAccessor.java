package com.company.app.service.database.typedapi;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Property accessor implementation that uses a {@link TypeHandler} for type conversion.
 *
 * <p><strong>Issue Fixed:</strong> This implementation explicitly logs and propagates type
 * conversion errors instead of silently swallowing exceptions. Previously, type conversion errors
 * would fall back silently to getObject(), masking bugs.
 *
 * <p><strong>Performance Pattern:</strong> The accessor pre-compiles the setter method reference
 * and type handler, enabling direct invocation without reflection lookup on each access.
 *
 * @see JdbcPropertyAccessor
 * @see TypeHandler
 */
final class TypeHandlerPropertyAccessor implements JdbcPropertyAccessor {

  private static final Logger LOG = LogManager.getLogger(TypeHandlerPropertyAccessor.class);

  private final String propertyName;
  private final Method setter;
  private final TypeHandler<?> handler;
  private final Class<?> propertyType;

  /**
   * Creates a new property accessor.
   *
   * @param propertyName the property name
   * @param setter the setter method
   * @param handler the type handler for conversion
   */
  TypeHandlerPropertyAccessor(String propertyName, Method setter, TypeHandler<?> handler) {
    this.propertyName = propertyName;
    this.setter = setter;
    this.handler = handler;
    this.propertyType = setter.getParameterTypes()[0];
  }

  /**
   * Sets the property value on the target object.
   *
   * <p><strong>Issue Fixed:</strong> Type conversion errors are now logged and propagated, not
   * silently swallowed. This ensures that data type mismatches are caught during development rather
   * than causing subtle bugs in production.
   *
   * @param target the object to set the property on
   * @param rs the ResultSet to read from
   * @param columnIndex the 1-based column index
   * @throws SQLException if a database access error occurs
   * @throws ReflectiveOperationException if the setter invocation fails
   */
  @Override
  public void setProperty(Object target, ResultSet rs, int columnIndex)
      throws SQLException, ReflectiveOperationException {
    Object value;
    try {
      value = handler.getResult(rs, columnIndex);
    } catch (SQLException e) {
      // Log the conversion error with full context
      if (LOG.isErrorEnabled()) {
        LOG.error(
            "Type conversion error for property '{}' at column {}: {}. "
                + "Expected type: {}, Handler: {}. NOT falling back to getObject() - fix the data!",
            propertyName,
            columnIndex,
            e.getMessage(),
            propertyType.getName(),
            handler.getClass().getSimpleName());
      }
      // Propagate the error - don't silently swallow it!
      throw new SQLException(
          String.format(
              "Type conversion failed for property '%s' (column %d, type %s): %s",
              propertyName, columnIndex, propertyType.getName(), e.getMessage()),
          e);
    }

    try {
      setter.invoke(target, value);
    } catch (IllegalArgumentException e) {
      if (LOG.isErrorEnabled()) {
        LOG.error(
            "Cannot set property '{}' with value '{}' (type {}): {}",
            propertyName,
            value,
            value != null ? value.getClass().getName() : "null",
            e.getMessage());
      }
      throw e;
    }
  }

  @Override
  public String getPropertyName() {
    return propertyName;
  }

  @Override
  public Class<?> getPropertyType() {
    return propertyType;
  }

  @Override
  public String toString() {
    return "TypeHandlerPropertyAccessor{property='"
        + propertyName
        + "', type="
        + propertyType
        + "}";
  }
}
