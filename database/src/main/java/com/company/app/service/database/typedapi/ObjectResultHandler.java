package com.company.app.service.database.typedapi;

import static java.util.Locale.ROOT;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ResultSetHandler implementation that maps ResultSet rows to Java bean objects.
 *
 * <p><strong>Optimization Pattern:</strong> This handler uses pre-compiled {@link
 * JdbcPropertyAccessor} arrays indexed by column position for O(1) property access. On
 * construction, it:
 *
 * <ol>
 *   <li>Inspects the ResultSet metadata for column names
 *   <li>Matches columns to bean properties (with underscore-to-camelCase conversion)
 *   <li>Builds an accessor array indexed by column position
 *   <li>Each accessor holds a pre-resolved setter method and type handler
 * </ol>
 *
 * <p>Subsequent row handling is a simple loop over the accessor array with direct method
 * invocation—no reflection lookup, no map access per column.
 *
 * @param <T> the target bean type
 * @see DefaultResultSetHandlerFactory
 * @see JdbcPropertyAccessor
 */
public final class ObjectResultHandler<T> implements ResultSetHandler<T> {

  private static final Logger LOG = LogManager.getLogger(ObjectResultHandler.class);

  private final Class<T> targetType;
  private final JdbcPropertyAccessor[] accessors;
  private final int columnCount;

  /**
   * Creates a new handler for the given target type and ResultSet metadata.
   *
   * <p><strong>Note:</strong> This constructor performs reflection to build the accessor array.
   * Instances should be cached by {@link DefaultResultSetHandlerFactory}.
   *
   * @param targetType the target bean class
   * @param metaData the ResultSet metadata
   * @throws SQLException if metadata cannot be read
   */
  public ObjectResultHandler(Class<T> targetType, ResultSetMetaData metaData) throws SQLException {
    this.targetType = targetType;
    this.columnCount = metaData.getColumnCount();
    this.accessors = buildAccessors(targetType, metaData);
  }

  /**
   * Creates a handler using existing accessor array (for factory caching).
   *
   * @param targetType the target bean class
   * @param accessors pre-built accessor array
   */
  ObjectResultHandler(Class<T> targetType, JdbcPropertyAccessor[] accessors) {
    this.targetType = targetType;
    this.accessors = accessors;
    this.columnCount = accessors.length;
  }

  @Override
  public T handle(ResultSet rs) throws SQLException {
    try {
      T instance = targetType.getDeclaredConstructor().newInstance();

      // O(1) access per column via array indexing
      for (int i = 0; i < columnCount; i++) {
        accessors[i].setProperty(instance, rs, i + 1); // JDBC is 1-indexed
      }

      return instance;
    } catch (ReflectiveOperationException e) {
      throw new SQLException("Failed to instantiate or populate " + targetType.getName(), e);
    }
  }

  @Override
  public Class<T> getTargetType() {
    return targetType;
  }

  /**
   * Returns the accessor array for caching purposes.
   *
   * @return the accessor array (do not modify)
   */
  JdbcPropertyAccessor[] getAccessors() {
    return accessors;
  }

  /**
   * Builds the accessor array by matching ResultSet columns to bean properties.
   *
   * @param type the target bean class
   * @param metaData the ResultSet metadata
   * @return array of accessors indexed by column position (0-based)
   * @throws SQLException if metadata cannot be read
   */
  private static <T> JdbcPropertyAccessor[] buildAccessors(
      Class<T> type, ResultSetMetaData metaData) throws SQLException {

    int columnCount = metaData.getColumnCount();
    JdbcPropertyAccessor[] accessors = new JdbcPropertyAccessor[columnCount];

    Map<String, Method> setters = buildSetterMap(type);
    TypeHandlerRegistry registry = TypeHandlerRegistry.getInstance();

    for (int i = 0; i < columnCount; i++) {
      String columnName = getColumnName(metaData, i + 1);
      accessors[i] = createAccessor(type, columnName, setters, registry);
    }

    return accessors;
  }

  /**
   * Builds a map of setter methods keyed by property name (lowercase).
   *
   * @param type the target bean class
   * @return map of property name to setter method
   */
  private static <T> Map<String, Method> buildSetterMap(Class<T> type) {
    Map<String, Method> setters = new HashMap<>();
    for (Method method : type.getMethods()) {
      if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
        String propertyName = method.getName().substring(3).toLowerCase(ROOT);
        setters.put(propertyName, method);
      }
    }
    return setters;
  }

  /**
   * Gets the column name from metadata, preferring label over name.
   *
   * @param metaData the ResultSet metadata
   * @param columnIndex the column index (1-based)
   * @return the column name
   * @throws SQLException if metadata cannot be read
   */
  private static String getColumnName(ResultSetMetaData metaData, int columnIndex)
      throws SQLException {
    String columnName = metaData.getColumnLabel(columnIndex);
    return columnName != null ? columnName : metaData.getColumnName(columnIndex);
  }

  /**
   * Creates a property accessor for a column.
   *
   * @param type the target bean class
   * @param columnName the column name
   * @param setters the setter lookup map
   * @param registry the type handler registry
   * @return the property accessor
   */
  private static <T> JdbcPropertyAccessor createAccessor(
      Class<T> type, String columnName, Map<String, Method> setters, TypeHandlerRegistry registry) {

    PropertyMatch match = findSetter(columnName, setters);

    if (match.setter == null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("No setter found for column '{}' on type {}", columnName, type.getName());
      }
      return JdbcPropertyAccessor.noOp(columnName);
    }

    return createAccessorWithHandler(columnName, match, registry);
  }

  /**
   * Finds the matching setter for a column name.
   *
   * @param columnName the column name
   * @param setters the setter lookup map
   * @return the property match result
   */
  private static PropertyMatch findSetter(String columnName, Map<String, Method> setters) {
    String propertyName = columnName.toLowerCase(ROOT);
    Method setter = setters.get(propertyName);

    if (setter == null && columnName.contains("_")) {
      String camelName = UnderscoreToCamelCase.convert(columnName).toLowerCase(ROOT);
      setter = setters.get(camelName);
      if (setter != null) {
        propertyName = camelName;
      }
    }

    return new PropertyMatch(setter, propertyName);
  }

  /**
   * Creates an accessor with a type handler.
   *
   * @param columnName the column name
   * @param match the property match
   * @param registry the type handler registry
   * @return the property accessor
   */
  private static JdbcPropertyAccessor createAccessorWithHandler(
      String columnName, PropertyMatch match, TypeHandlerRegistry registry) {

    Class<?> paramType = match.setter.getParameterTypes()[0];
    TypeHandler<?> handler = registry.getHandler(paramType);

    if (handler == null) {
      if (LOG.isWarnEnabled()) {
        LOG.warn(
            "No TypeHandler for type {} (property '{}'), using Object fallback",
            paramType.getName(),
            match.propertyName);
      }
      handler = registry.getHandler(Object.class);
    }

    JdbcPropertyAccessor accessor =
        JdbcPropertyAccessor.create(match.propertyName, match.setter, handler);
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "Mapped column '{}' -> property '{}' ({})",
          columnName,
          match.propertyName,
          paramType.getName());
    }

    return accessor;
  }

  /** Helper class to hold setter matching result. */
  private static final class PropertyMatch {
    final Method setter;
    final String propertyName;

    PropertyMatch(Method setter, String propertyName) {
      this.setter = setter;
      this.propertyName = propertyName;
    }
  }

  @Override
  public String toString() {
    return "ObjectResultHandler{type=" + targetType.getName() + ", columns=" + columnCount + "}";
  }

  /**
   * Returns a cache key for this handler configuration.
   *
   * @return string representation suitable for caching
   */
  String getCacheKey() {
    StringBuilder sb = new StringBuilder(targetType.getName()).append(":");
    for (JdbcPropertyAccessor accessor : accessors) {
      sb.append(accessor.getPropertyName()).append(",");
    }
    return sb.toString();
  }

  /**
   * Creates a cache key from metadata without building the full handler.
   *
   * @param type the target type
   * @param metaData the ResultSet metadata
   * @return cache key string
   * @throws SQLException if metadata cannot be read
   */
  static String createCacheKey(Class<?> type, ResultSetMetaData metaData) throws SQLException {
    StringBuilder sb = new StringBuilder(type.getName()).append(":");
    for (int i = 1; i <= metaData.getColumnCount(); i++) {
      String label = metaData.getColumnLabel(i);
      sb.append(label != null ? label : metaData.getColumnName(i)).append(",");
    }
    return sb.toString();
  }
}
