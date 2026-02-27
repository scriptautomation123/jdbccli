package com.company.app.typedapi;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry for {@link TypeHandler} instances.
 *
 * <p><strong>Optimization Pattern:</strong> This registry is a shared singleton that caches
 * TypeHandlers for common types, avoiding repeated handler creation. The registry is initialized
 * with handlers for all common JDBC types.
 *
 * <p><strong>Singleton Justification:</strong> The singleton pattern is appropriate here because:
 *
 * <ul>
 *   <li>Type handlers are stateless and immutable
 *   <li>Shared registry reduces memory footprint across all JDBC operations
 *   <li>Thread-safe ConcurrentHashMap allows concurrent access
 *   <li>Pre-initialized handlers avoid setup cost on first use
 *   <li>No alternative injection mechanism needed for this utility
 * </ul>
 *
 * <p><strong>Issue Fixed:</strong> Null key registration is now validated and rejected with a clear
 * error message.
 *
 * <p><strong>Thread Safety:</strong> The registry uses a ConcurrentHashMap and is safe for
 * concurrent access.
 *
 * @see TypeHandler
 */
public final class TypeHandlerRegistry {

  /** Singleton instance. */
  private static final TypeHandlerRegistry INSTANCE = new TypeHandlerRegistry();

  /** Map of type handlers by target class. */
  private final Map<Class<?>, TypeHandler<?>> handlers = new ConcurrentHashMap<>();

  /** Private constructor - use {@link #getInstance()}. */
  private TypeHandlerRegistry() {
    registerDefaultHandlers();
  }

  /**
   * Returns the singleton instance of the registry.
   *
   * @return the registry instance
   */
  public static TypeHandlerRegistry getInstance() {
    return INSTANCE;
  }

  /**
   * Registers a type handler for the given class.
   *
   * <p><strong>Issue Fixed:</strong> Null keys are explicitly rejected to prevent subtle bugs.
   *
   * @param type the target type class (must not be null)
   * @param handler the handler to register (must not be null)
   * @param <T> the target type
   * @throws NullPointerException if type or handler is null
   */
  public <T> void register(Class<T> type, TypeHandler<T> handler) {
    Objects.requireNonNull(type, "Type must not be null - null key registration is not allowed");
    Objects.requireNonNull(handler, "Handler must not be null");
    handlers.put(type, handler);
  }

  /**
   * Gets a type handler for the given class.
   *
   * @param type the target type class
   * @param <T> the target type
   * @return the registered handler, or null if not found
   */
  @SuppressWarnings("unchecked")
  public <T> TypeHandler<T> getHandler(Class<T> type) {
    if (type == null) {
      return null;
    }
    return (TypeHandler<T>) handlers.get(type);
  }

  /**
   * Checks if a handler is registered for the given type.
   *
   * @param type the type to check
   * @return true if a handler is registered
   */
  public boolean hasHandler(Class<?> type) {
    return type != null && handlers.containsKey(type);
  }

  /** Registers handlers for common JDBC types. */
  private void registerDefaultHandlers() {
    // Primitive wrappers
    register(String.class, ResultSet::getString);
    register(Integer.class, TypeHandlerRegistry::getIntegerOrNull);
    register(int.class, ResultSet::getInt);
    register(Long.class, TypeHandlerRegistry::getLongOrNull);
    register(long.class, ResultSet::getLong);
    register(Double.class, TypeHandlerRegistry::getDoubleOrNull);
    register(double.class, ResultSet::getDouble);
    register(Float.class, TypeHandlerRegistry::getFloatOrNull);
    register(float.class, ResultSet::getFloat);
    register(Boolean.class, TypeHandlerRegistry::getBooleanOrNull);
    register(boolean.class, ResultSet::getBoolean);
    register(Short.class, TypeHandlerRegistry::getShortOrNull);
    register(short.class, ResultSet::getShort);
    register(Byte.class, TypeHandlerRegistry::getByteOrNull);
    register(byte.class, ResultSet::getByte);

    // Date/Time types
    register(Date.class, ResultSet::getTimestamp);
    register(Timestamp.class, ResultSet::getTimestamp);
    register(java.sql.Date.class, ResultSet::getDate);
    register(java.sql.Time.class, ResultSet::getTime);
    register(
        LocalDate.class,
        (rs, i) -> {
          java.sql.Date date = rs.getDate(i);
          return date != null ? date.toLocalDate() : null;
        });
    register(
        LocalDateTime.class,
        (rs, i) -> {
          Timestamp ts = rs.getTimestamp(i);
          return ts != null ? ts.toLocalDateTime() : null;
        });

    // Numeric types
    register(BigDecimal.class, ResultSet::getBigDecimal);

    // Binary
    register(byte[].class, ResultSet::getBytes);

    // Object fallback
    register(Object.class, ResultSet::getObject);
  }

  // Helper methods to handle null values properly for boxed types
  private static Integer getIntegerOrNull(ResultSet rs, int columnIndex) throws SQLException {
    int value = rs.getInt(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Long getLongOrNull(ResultSet rs, int columnIndex) throws SQLException {
    long value = rs.getLong(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Double getDoubleOrNull(ResultSet rs, int columnIndex) throws SQLException {
    double value = rs.getDouble(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Float getFloatOrNull(ResultSet rs, int columnIndex) throws SQLException {
    float value = rs.getFloat(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Boolean getBooleanOrNull(ResultSet rs, int columnIndex) throws SQLException {
    boolean value = rs.getBoolean(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Short getShortOrNull(ResultSet rs, int columnIndex) throws SQLException {
    short value = rs.getShort(columnIndex);
    return rs.wasNull() ? null : value;
  }

  private static Byte getByteOrNull(ResultSet rs, int columnIndex) throws SQLException {
    byte value = rs.getByte(columnIndex);
    return rs.wasNull() ? null : value;
  }
}
