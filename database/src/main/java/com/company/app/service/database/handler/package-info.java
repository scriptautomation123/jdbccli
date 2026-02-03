/**
 * High-performance ResultSet-to-object mapping framework.
 *
 * <p><strong>Key Optimization Patterns:</strong>
 *
 * <ul>
 *   <li><strong>ResultSetHandler caching:</strong> {@link
 *       com.company.app.service.database.handler.DefaultResultSetHandlerFactory} caches handlers by
 *       query structure + bean type, paying reflection cost once per unique query shape.
 *   <li><strong>Pre-compiled accessor arrays:</strong> {@link
 *       com.company.app.service.database.handler.ObjectResultHandler} uses {@link
 *       com.company.app.service.database.handler.JdbcPropertyAccessor}[] indexed by column position
 *       for O(1) property access vs Map lookups.
 *   <li><strong>Single-pass string conversion:</strong> {@link
 *       com.company.app.service.database.handler.UnderscoreToCamelCase} uses in-place char array
 *       manipulation—no regex, no StringBuilder.
 *   <li><strong>TypeHandler registry singleton:</strong> {@link
 *       com.company.app.service.database.handler.TypeHandlerRegistry} shared instance avoids
 *       repeated handler creation for common types.
 * </ul>
 *
 * <p><strong>Issues Fixed:</strong>
 *
 * <ul>
 *   <li>Unbounded static cache in DefaultResultSetHandlerFactory—now uses bounded LRU cache to
 *       prevent memory leaks in long-running apps with dynamic queries.
 *   <li>Silent exception swallowing in TypeHandlerPropertyAccessor.getResult()—type conversion
 *       errors are now logged and propagated instead of falling back silently to getObject().
 *   <li>Null key registration allowed in TypeHandlerRegistry.register()—now explicitly validated
 *       and rejected.
 * </ul>
 *
 * <p><strong>Architectural Overview:</strong>
 *
 * <pre>
 * Query → ResultSetHandlerFactory (cached) → ObjectResultHandler (accessor array) → TypeHandler (registry)
 * </pre>
 *
 * First execution builds and caches the handler chain; subsequent executions hit cache at factory
 * level for near-hand-coded performance.
 *
 * <p><strong>Usage Example:</strong>
 *
 * <pre>{@code
 * try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM users");
 *      ResultSet rs = pstmt.executeQuery()) {
 *
 *     ResultSetHandler<User> handler =
 *         DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
 *
 *     List<User> users = handler.handleAll(rs);
 * }
 * }</pre>
 *
 * @see com.company.app.service.database.handler.DefaultResultSetHandlerFactory
 * @see com.company.app.service.database.handler.ResultSetHandler
 * @see com.company.app.service.database.handler.TypeHandlerRegistry
 */
package com.company.app.service.database.handler;
