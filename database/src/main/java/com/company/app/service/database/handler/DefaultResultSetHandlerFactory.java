package com.company.app.service.database.handler;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory for creating and caching {@link ResultSetHandler} instances.
 *
 * <p><strong>Optimization Pattern:</strong> This factory caches handlers by query structure + bean
 * type, paying reflection cost once per unique query shape. Subsequent executions of the same query
 * pattern hit the cache for near-hand-coded performance.
 *
 * <p><strong>Issue Fixed:</strong> The cache is now bounded with LRU eviction to prevent memory
 * leaks in long-running applications with dynamic queries. Previously, the static cache was
 * unbounded, potentially causing OOM errors.
 *
 * <p><strong>Cache Key Format:</strong> {@code ClassName:column1,column2,column3,...}
 *
 * <p><strong>Performance Chain:</strong>
 *
 * <pre>
 * Query → ResultSetHandlerFactory (cached) → ObjectResultHandler (accessor array) → TypeHandler (registry)
 * </pre>
 *
 * @see ObjectResultHandler
 * @see TypeHandlerRegistry
 */
public final class DefaultResultSetHandlerFactory {

  private static final Logger LOG = LogManager.getLogger(DefaultResultSetHandlerFactory.class);

  /**
   * Default maximum cache size. This limits memory usage while providing good cache hit rates for
   * typical applications.
   */
  public static final int DEFAULT_MAX_CACHE_SIZE = 1000;

  /**
   * Bounded LRU cache for handler instances. Uses a LinkedHashMap with access-order and
   * removeEldestEntry for automatic eviction.
   *
   * <p><strong>Issue Fixed:</strong> Previously this was an unbounded ConcurrentHashMap which could
   * grow indefinitely. Now it's a bounded LRU cache.
   *
   * <p><strong>Thread Safety:</strong> Access is synchronized via Collections.synchronizedMap or
   * explicit synchronization.
   */
  private static final Map<String, ResultSetHandler<?>> CACHE =
      new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResultSetHandler<?>> eldest) {
          boolean shouldRemove = size() > DEFAULT_MAX_CACHE_SIZE;
          if (shouldRemove && LOG.isDebugEnabled()) {
            LOG.debug("LRU evicting cached handler: {} (cache size: {})", eldest.getKey(), size());
          }
          return shouldRemove;
        }
      };

  /** Lock object for thread-safe cache access. */
  private static final Object CACHE_LOCK = new Object();

  private DefaultResultSetHandlerFactory() {
    // Static factory methods only
  }

  /**
   * Creates or retrieves a cached handler for the given type and ResultSet metadata.
   *
   * <p><strong>Cache Behavior:</strong>
   *
   * <ul>
   *   <li>Cache hit: Returns existing handler (O(1) lookup)
   *   <li>Cache miss: Creates handler, caches it, returns it
   *   <li>Cache full: LRU entry is evicted before adding new entry
   * </ul>
   *
   * @param type the target bean type
   * @param metaData the ResultSet metadata
   * @param <T> the target type
   * @return the handler (cached if possible)
   * @throws SQLException if metadata cannot be read or handler creation fails
   */
  @SuppressWarnings("unchecked")
  public static <T> ResultSetHandler<T> getHandler(Class<T> type, ResultSetMetaData metaData)
      throws SQLException {

    String cacheKey = ObjectResultHandler.createCacheKey(type, metaData);

    synchronized (CACHE_LOCK) {
      ResultSetHandler<?> cached = CACHE.get(cacheKey);
      if (cached != null) {
        LOG.trace("Cache hit for handler: {}", cacheKey);
        return (ResultSetHandler<T>) cached;
      }
    }

    // Create outside synchronized block to minimize lock contention
    LOG.debug("Cache miss - creating handler for: {}", cacheKey);
    ResultSetHandler<T> handler = new ObjectResultHandler<>(type, metaData);

    synchronized (CACHE_LOCK) {
      // Double-check in case another thread created it
      ResultSetHandler<?> existing = CACHE.get(cacheKey);
      if (existing != null) {
        return (ResultSetHandler<T>) existing;
      }
      CACHE.put(cacheKey, handler);
    }

    return handler;
  }

  /**
   * Creates a handler without caching. Use this for one-off queries or when you want to control
   * caching yourself.
   *
   * @param type the target bean type
   * @param metaData the ResultSet metadata
   * @param <T> the target type
   * @return a new handler instance
   * @throws SQLException if metadata cannot be read or handler creation fails
   */
  public static <T> ResultSetHandler<T> createHandler(Class<T> type, ResultSetMetaData metaData)
      throws SQLException {
    return new ObjectResultHandler<>(type, metaData);
  }

  /**
   * Returns the current cache size.
   *
   * @return number of cached handlers
   */
  public static int getCacheSize() {
    synchronized (CACHE_LOCK) {
      return CACHE.size();
    }
  }

  /**
   * Returns the maximum cache size.
   *
   * @return maximum number of handlers that will be cached
   */
  public static int getMaxCacheSize() {
    return DEFAULT_MAX_CACHE_SIZE;
  }

  /**
   * Clears the handler cache. Useful for testing or reclaiming memory.
   *
   * <p><strong>Note:</strong> This should rarely be needed in production as the LRU eviction
   * handles memory management automatically.
   */
  public static void clearCache() {
    synchronized (CACHE_LOCK) {
      int size = CACHE.size();
      CACHE.clear();
      LOG.info("Cleared handler cache ({} entries)", size);
    }
  }

  /**
   * Returns cache statistics for monitoring.
   *
   * @return cache statistics record
   */
  public static CacheStats getCacheStats() {
    synchronized (CACHE_LOCK) {
      return new CacheStats(CACHE.size(), DEFAULT_MAX_CACHE_SIZE);
    }
  }

  /**
   * Cache statistics for monitoring and debugging.
   *
   * @param currentSize current number of cached handlers
   * @param maxSize maximum cache capacity
   */
  public record CacheStats(int currentSize, int maxSize) {
    /** Returns the cache utilization as a percentage. */
    public double utilizationPercent() {
      return maxSize > 0 ? (currentSize * 100.0) / maxSize : 0;
    }

    @Override
    public String toString() {
      return String.format(
          "CacheStats{size=%d/%d (%.1f%%)}", currentSize, maxSize, utilizationPercent());
    }
  }
}
