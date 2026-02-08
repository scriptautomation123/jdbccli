# ResultSet-to-Bean Mapping: A Performance Optimization Journey

## From Reflection Hell to Near-Native Speed

This tutorial explains the optimization path and why each step matters. Performance numbers are illustrative; run the JMH benchmarks in this repo for current measurements.

---

## Table of Contents

1. [The Problem: Why ResultSet Mapping is Slow](#the-problem)
2. [Lesson 0: The Naive Approach (Baseline)](#lesson-0-naive-approach)
3. [Lesson 1: Eliminating Repeated Reflection](#lesson-1-cache-reflection)
4. [Lesson 2: From Maps to Arrays (O(n) → O(1))](#lesson-2-arrays-over-maps)
5. [Lesson 3: Type Handler Registry](#lesson-3-type-handler-registry)
6. [Lesson 4: String Optimization](#lesson-4-string-optimization)
7. [Lesson 5: Bounded Cache (Production-Ready)](#lesson-5-bounded-cache)
8. [Final Architecture & Results](#final-architecture)
9. [JMH Benchmark Setup](#jmh-setup)

---

## The Problem: Why ResultSet Mapping is Slow {#the-problem}

**Student Question:** "Why not just use `rs.getString("column_name")` everywhere?"

**Principal Engineer:** "Let's measure the cost of a typical ORM operation:"

```java
// Typical hand-written mapping (what you write)
while (rs.next()) {
    User user = new User();
    user.setId(rs.getInt("id"));
    user.setUserName(rs.getString("user_name"));
    user.setEmail(rs.getString("email"));
    users.add(user);
}
```

**Hidden Costs:**

1. **Column name lookup**: `getString("user_name")` searches metadata on EVERY row
2. **Manual mapping**: You write this for EVERY entity
3. **No reuse**: Same reflection cost repeated across queries
4. **Error-prone**: Typos, null handling, type conversion

**What we want:**

```java
// Automated, cached, optimized
ResultSetHandler<User> handler = factory.getHandler(User.class, rs.getMetaData());
List<User> users = handler.handleAll(rs);
```

---

## Lesson 0: The Naive Approach (Baseline) {#lesson-0-naive-approach}

**Principal Engineer:** "Let's start with the most obvious implementation. This is what a junior engineer might write."

### Implementation: Naive Reflection-Based Mapper

```java
public class NaiveResultSetMapper<T> {

    public T map(ResultSet rs, Class<T> targetClass) throws Exception {
        T instance = targetClass.getDeclaredConstructor().newInstance();
        ResultSetMetaData metaData = rs.getMetaData();

        // FOR EVERY ROW, we do this reflection:
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnLabel(i);

            // Convert column_name → columnName (every time!)
            String propertyName = columnName.toLowerCase().replaceAll("_", "");

            // Find setter by reflection (every time!)
            for (Method method : targetClass.getMethods()) {
                if (method.getName().toLowerCase().equals("set" + propertyName)) {
                    Object value = rs.getObject(i);
                    method.invoke(instance, value);
                    break;
                }
            }
        }
        return instance;
    }
}
```

### Performance Characteristics

| Operation         | Cost per Row     | Example (1000 rows) |
| ----------------- | ---------------- | ------------------- |
| `newInstance()`   | ~100ns           | 100µs               |
| `getMetaData()`   | ~50ns            | 50µs                |
| String operations | ~200ns × columns | 600µs (3 cols)      |
| Reflection search | ~5µs × columns   | 15ms (3 cols)       |
| `method.invoke()` | ~100ns × columns | 300µs (3 cols)      |
| **TOTAL**         | **~15.2ms**      | **15.2ms**          |

**Key Insight:** The reflection search (`getMethods()` + iteration) dominates at **98% of total time**.

---

## Lesson 1: Eliminating Repeated Reflection {#lesson-1-cache-reflection}

**Student:** "Can't we just cache the reflection results?"

**Principal Engineer:** "Exactly! Let's cache the Method objects."

### Pattern: Pre-computed Accessor

```java
public class CachedReflectionMapper<T> {

    private static class ColumnAccessor {
        final Method setter;
        final int columnIndex;

        ColumnAccessor(Method setter, int columnIndex) {
            this.setter = setter;
            this.columnIndex = columnIndex;
        }
    }

    private final Class<T> targetClass;
    private final ColumnAccessor[] accessors; // ← Cached!

    public CachedReflectionMapper(Class<T> targetClass, ResultSetMetaData metaData)
            throws SQLException {
        this.targetClass = targetClass;
        this.accessors = buildAccessors(targetClass, metaData); // ONCE!
    }

    private ColumnAccessor[] buildAccessors(Class<T> targetClass, ResultSetMetaData metaData)
            throws SQLException {
        int columnCount = metaData.getColumnCount();
        ColumnAccessor[] result = new ColumnAccessor[columnCount];

        // Build setter map once
        Map<String, Method> setters = new HashMap<>();
        for (Method method : targetClass.getMethods()) {
            if (method.getName().startsWith("set") && method.getParameterCount() == 1) {
                String propName = method.getName().substring(3).toLowerCase();
                setters.put(propName, method);
            }
        }

        // Match columns to setters
        for (int i = 0; i < columnCount; i++) {
            String columnName = metaData.getColumnLabel(i + 1);
            String propName = columnName.toLowerCase().replace("_", "");
            Method setter = setters.get(propName);
            result[i] = new ColumnAccessor(setter, i + 1);
        }

        return result;
    }

    public T map(ResultSet rs) throws Exception {
        T instance = targetClass.getDeclaredConstructor().newInstance();

        // Fast path: direct array iteration
        for (ColumnAccessor accessor : accessors) {
            if (accessor.setter != null) {
                Object value = rs.getObject(accessor.columnIndex);
                accessor.setter.invoke(instance, value);
            }
        }

        return instance;
    }
}
```

### Performance Impact

```java
// JMH Benchmark
@Benchmark
public void naiveMapping(Blackhole bh) throws Exception {
    try (ResultSet rs = statement.executeQuery("SELECT * FROM users LIMIT 1000")) {
        NaiveResultSetMapper<User> mapper = new NaiveResultSetMapper<>();
        while (rs.next()) {
            bh.consume(mapper.map(rs, User.class));
        }
    }
}

@Benchmark
public void cachedReflectionMapping(Blackhole bh) throws Exception {
    try (ResultSet rs = statement.executeQuery("SELECT * FROM users LIMIT 1000")) {
        ResultSetMetaData metaData = rs.getMetaData();
        CachedReflectionMapper<User> mapper = new CachedReflectionMapper<>(User.class, metaData);
        while (rs.next()) {
            bh.consume(mapper.map(rs));
        }
    }
}
```

**Results:**

```
Benchmark                        Mode  Cnt   Score   Error  Units
naiveMapping                     avgt    5  15.234 ± 0.432  ms/op
cachedReflectionMapping          avgt    5   2.456 ± 0.089  ms/op

Improvement: 6.2x faster (84% reduction)
```

**Key Insight:** Moving reflection from per-row to per-query eliminates 84% of overhead.

---

## Lesson 2: From Maps to Arrays (O(n) → O(1)) {#lesson-2-arrays-over-maps}

**Student:** "We're still doing `rs.getObject()` and type casting. Can we optimize that?"

**Principal Engineer:** "Yes! Two optimizations: 1) Array indexing over maps, 2) Type-specific getters"

### Pattern: Pre-compiled Accessor Array

This is the core of `ObjectResultHandler.java`:

```java
public final class ObjectResultHandler<T> implements ResultSetHandler<T> {

    private final Class<T> targetType;
    private final JdbcPropertyAccessor[] accessors; // ← Array indexed by column!
    private final int columnCount;

    public ObjectResultHandler(Class<T> targetType, ResultSetMetaData metaData)
            throws SQLException {
        this.targetType = targetType;
        this.columnCount = metaData.getColumnCount();
        this.accessors = buildAccessors(targetType, metaData);
    }

    @Override
    public T handle(ResultSet rs) throws SQLException {
        try {
            T instance = targetType.getDeclaredConstructor().newInstance();

            // O(1) access per column via direct array indexing
            for (int i = 0; i < columnCount; i++) {
                accessors[i].setProperty(instance, rs, i + 1); // JDBC is 1-indexed
            }

            return instance;
        } catch (ReflectiveOperationException e) {
            throw new SQLException("Failed to instantiate " + targetType.getName(), e);
        }
    }
}
```

### The Secret: JdbcPropertyAccessor

```java
public final class JdbcPropertyAccessor {
    private final String propertyName;
    private final Method setter;
    private final TypeHandler<?> typeHandler; // ← Type-specific getter!

    public void setProperty(Object target, ResultSet rs, int columnIndex)
            throws SQLException {
        try {
            // Type-specific extraction (rs.getInt() vs rs.getString())
            Object value = typeHandler.getValue(rs, columnIndex);
            if (value != null || !setter.getParameterTypes()[0].isPrimitive()) {
                setter.invoke(target, value);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SQLException("Failed to set property " + propertyName, e);
        }
    }

    public static JdbcPropertyAccessor create(
            String propertyName, Method setter, TypeHandler<?> handler) {
        return new JdbcPropertyAccessor(propertyName, setter, handler);
    }
}
```

### Why This is Fast

**Before (Map-based):**

```java
Map<String, Method> setters = ... // Hash lookup
Map<Class<?>, TypeHandler<?>> handlers = ... // Hash lookup
for (column : columns) {
    Method setter = setters.get(propertyName);     // O(log n)
    TypeHandler handler = handlers.get(type);      // O(log n)
    Object value = handler.getValue(rs, i);
    setter.invoke(instance, value);
}
```

**After (Array-based):**

```java
JdbcPropertyAccessor[] accessors = ... // Pre-computed
for (int i = 0; i < columnCount; i++) {
    accessors[i].setProperty(instance, rs, i + 1); // O(1)
}
```

### Benchmark

```java
@Benchmark
public void mapBased(Blackhole bh) throws Exception {
    // Uses Map<String, Method> + Map<Class<?>, TypeHandler<?>>
    ...
}

@Benchmark
public void arrayBased(Blackhole bh) throws Exception {
    // Uses JdbcPropertyAccessor[]
    ...
}
```

**Results:**

```
Benchmark                        Mode  Cnt   Score   Error  Units
mapBased                         avgt    5   2.456 ± 0.089  ms/op
arrayBased                       avgt    5   1.123 ± 0.042  ms/op

Improvement: 2.2x faster (54% reduction from cached reflection)
```

---

## Lesson 3: Type Handler Registry {#lesson-3-type-handler-registry}

**Student:** "Why do we need a TypeHandler? Can't we just use `rs.getObject()`?"

**Principal Engineer:** "Let me show you the problem with `getObject()`:"

### The Problem with getObject()

```java
// Using getObject() everywhere
Object value = rs.getObject(columnIndex);
setter.invoke(instance, value); // ClassCastException risk!

// Example failure:
// Database: INTEGER → Java: Integer (boxed)
// Bean property: int (primitive)
// Result: NullPointerException on null value
```

### Pattern: Type-Specific Handlers

```java
@FunctionalInterface
public interface TypeHandler<T> {
    T getValue(ResultSet rs, int columnIndex) throws SQLException;
}

public final class TypeHandlerRegistry {

    private static final TypeHandlerRegistry INSTANCE = new TypeHandlerRegistry();
    private final Map<Class<?>, TypeHandler<?>> handlers = new ConcurrentHashMap<>();

    private TypeHandlerRegistry() {
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        // Primitive types
        register(int.class, ResultSet::getInt);
        register(long.class, ResultSet::getLong);
        register(String.class, ResultSet::getString);

        // Boxed types with null handling
        register(Integer.class, (rs, i) -> {
            int value = rs.getInt(i);
            return rs.wasNull() ? null : value;
        });

        register(Long.class, (rs, i) -> {
            long value = rs.getLong(i);
            return rs.wasNull() ? null : value;
        });

        // Date/Time types
        register(LocalDateTime.class, (rs, i) -> {
            Timestamp ts = rs.getTimestamp(i);
            return ts != null ? ts.toLocalDateTime() : null;
        });
    }

    public <T> void register(Class<T> type, TypeHandler<T> handler) {
        handlers.put(type, handler);
    }

    public <T> TypeHandler<T> getHandler(Class<T> type) {
        return (TypeHandler<T>) handlers.get(type);
    }
}
```

### Benefits

1. **Type Safety**: Correct JDBC method for each type (`getInt()` vs `getString()`)
2. **Null Handling**: Proper null handling for primitives vs boxed types
3. **Performance**: Direct JDBC call vs reflection overhead
4. **Extensibility**: Easy to add custom type converters

### Benchmark Impact

```java
@Benchmark
public void withGetObject(Blackhole bh) throws Exception {
    // Uses rs.getObject() + casting
    ...
}

@Benchmark
public void withTypeHandler(Blackhole bh) throws Exception {
    // Uses type-specific TypeHandler
    ...
}
```

**Results:**

```
Benchmark                        Mode  Cnt   Score   Error  Units
withGetObject                    avgt    5   1.123 ± 0.042  ms/op
withTypeHandler                  avgt    5   0.892 ± 0.031  ms/op

Improvement: 1.26x faster (26% reduction)
```

---

## Lesson 4: String Optimization {#lesson-4-string-optimization}

**Student:** "What about `user_name` → `userName` conversion? That's just a string operation, right?"

**Principal Engineer:** "String operations are deceptively expensive. Let's measure:"

### The Naive Approach

```java
// What most developers write
public static String convert(String input) {
    return input.toLowerCase()
                .replaceAll("_", " ")
                .replace(" ", "")
                .replaceAll("\\b\\w", m -> m.toUpperCase()); // Regex!
}

// Or with StringBuilder
public static String convert(String input) {
    String[] parts = input.split("_");
    StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
    for (int i = 1; i < parts.length; i++) {
        sb.append(Character.toUpperCase(parts[i].charAt(0)));
        sb.append(parts[i].substring(1).toLowerCase());
    }
    return sb.toString();
}
```

### The Optimized Approach: Single-Pass Char Array

```java
public final class UnderscoreToCamelCase {

    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        char[] chars = input.toCharArray();
        int writePos = 0;
        boolean capitalizeNext = false;

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '_') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    chars[writePos++] = Character.toUpperCase(c);
                    capitalizeNext = false;
                } else {
                    chars[writePos++] = Character.toLowerCase(c);
                }
            }
        }

        return new String(chars, 0, writePos);
    }
}
```

### Why This is Fast

1. **Single pass**: One iteration, no multiple passes
2. **In-place**: Reuses same char array
3. **No allocations**: No StringBuilder, no String.split()
4. **No regex**: Direct character operations

### Benchmark

```java
@State(Scope.Benchmark)
public class StringConversionBenchmark {

    private static final String[] TEST_STRINGS = {
        "user_name", "first_name", "last_name",
        "created_at", "updated_at", "is_active"
    };

    @Benchmark
    public void naiveConversion(Blackhole bh) {
        for (String s : TEST_STRINGS) {
            bh.consume(naiveConvert(s));
        }
    }

    @Benchmark
    public void optimizedConversion(Blackhole bh) {
        for (String s : TEST_STRINGS) {
            bh.consume(UnderscoreToCamelCase.convert(s));
        }
    }
}
```

**Results:**

```
Benchmark                        Mode  Cnt   Score   Error  Units
naiveConversion                  avgt    5   342.5 ± 12.3   ns/op
optimizedConversion              avgt    5    87.2 ±  3.1   ns/op

Improvement: 3.9x faster (75% reduction)
```

---

## Lesson 5: Bounded Cache (Production-Ready) {#lesson-5-bounded-cache}

**Student:** "If caching is so great, why not cache everything?"

**Principal Engineer:** "That's the #1 mistake in production. Let me show you the memory leak:"

### The Memory Leak Problem

```java
// DANGEROUS: Unbounded cache
public class NaiveFactory {
    private static final Map<String, ResultSetHandler<?>> CACHE = new HashMap<>();

    public static <T> ResultSetHandler<T> getHandler(
            Class<T> type, ResultSetMetaData metaData) throws SQLException {
        String cacheKey = createCacheKey(type, metaData);
        return (ResultSetHandler<T>) CACHE.computeIfAbsent(cacheKey, k -> {
            return new ObjectResultHandler<>(type, metaData);
        });
    }
}

// Problem: Dynamic queries create unique keys
String sql = "SELECT * FROM users WHERE created_at > '" + timestamp + "'";
// Each unique timestamp creates a new cache entry!
// After 1 million queries: OutOfMemoryError
```

### Pattern: LRU Bounded Cache

```java
public final class DefaultResultSetHandlerFactory {

    private static final int MAX_CACHE_SIZE = 1000;

    // LinkedHashMap with access-order for LRU
    private static final Map<String, ResultSetHandler<?>> CACHE =
        Collections.synchronizedMap(new LinkedHashMap<String, ResultSetHandler<?>>(
            MAX_CACHE_SIZE + 1, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ResultSetHandler<?>> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });

    public static <T> ResultSetHandler<T> getHandler(
            Class<T> targetType, ResultSetMetaData metaData) throws SQLException {

        String cacheKey = ObjectResultHandler.createCacheKey(targetType, metaData);

        @SuppressWarnings("unchecked")
        ResultSetHandler<T> handler = (ResultSetHandler<T>) CACHE.get(cacheKey);

        if (handler == null) {
            handler = new ObjectResultHandler<>(targetType, metaData);
            CACHE.put(cacheKey, handler);
        }

        return handler;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static int getCacheSize() {
        return CACHE.size();
    }

    public static int getMaxCacheSize() {
        return MAX_CACHE_SIZE;
    }
}
```

### Cache Key Design

```java
// Good cache key: query structure, not query values
static String createCacheKey(Class<?> type, ResultSetMetaData metaData) throws SQLException {
    StringBuilder sb = new StringBuilder(type.getName()).append(":");
    for (int i = 1; i <= metaData.getColumnCount(); i++) {
        String label = metaData.getColumnLabel(i);
        sb.append(label != null ? label : metaData.getColumnName(i)).append(",");
    }
    return sb.toString();
}

// Examples:
// "User:id,user_name,email,"       ← Same for all User queries with these columns
// "Order:id,amount,created_at,"    ← Different entity type
```

### Long-Running Test

```java
@Test
public void testCacheBoundedness() throws Exception {
    DefaultResultSetHandlerFactory.clearCache();

    // Simulate 10,000 unique queries (pathological case)
    for (int i = 0; i < 10_000; i++) {
        String sql = "SELECT id, name" + i + " FROM users"; // Unique column each time
        try (ResultSet rs = conn.prepareStatement(sql).executeQuery()) {
            DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());
        }
    }

    int finalSize = DefaultResultSetHandlerFactory.getCacheSize();
    assertTrue("Cache should be bounded", finalSize <= 1000);
    assertEquals(1000, finalSize); // Should hit max and stabilize
}
```

---

## Final Architecture {#final-architecture}

### Component Diagram

```
Query Execution Flow
────────────────────

┌─────────────────────────────────────────────────────────────┐
│ 1. Application Code                                         │
│    try (ResultSet rs = pstmt.executeQuery()) {             │
│        ResultSetHandler<User> handler =                     │
│            DefaultResultSetHandlerFactory.getHandler(...)   │
│        List<User> users = handler.handleAll(rs);           │
│    }                                                         │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. DefaultResultSetHandlerFactory (LRU Cache)               │
│    ┌─────────────────────────────────────────┐             │
│    │ Cache Key: "User:id,userName,email,"    │             │
│    │ • Hit:  Return cached handler (fast)    │             │
│    │ • Miss: Build new handler (slow, once)  │             │
│    └─────────────────────────────────────────┘             │
└────────────┬────────────────────────────────────────────────┘
             │ (on cache miss)
             ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. ObjectResultHandler (Pre-compiled Accessor Array)        │
│    Construction (once per query shape):                     │
│    ┌─────────────────────────────────────────┐             │
│    │ Build JdbcPropertyAccessor[]            │             │
│    │   [0] → id       : TypeHandler<Integer> │             │
│    │   [1] → userName : TypeHandler<String>  │             │
│    │   [2] → email    : TypeHandler<String>  │             │
│    └─────────────────────────────────────────┘             │
│                                                              │
│    Execution (per row):                                     │
│    for (int i = 0; i < columns; i++) {                     │
│        accessors[i].setProperty(instance, rs, i+1); // O(1) │
│    }                                                         │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. JdbcPropertyAccessor (Type-Safe Property Setter)         │
│    void setProperty(target, rs, columnIndex) {             │
│        Object value = typeHandler.getValue(rs, i);         │
│        setter.invoke(target, value);                        │
│    }                                                         │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. TypeHandlerRegistry (Singleton, Type-Specific Getters)  │
│    getHandler(Integer.class) → (rs, i) -> rs.getInt(i)    │
│    getHandler(String.class)  → ResultSet::getString        │
│    getHandler(LocalDateTime) → custom converter            │
└─────────────────────────────────────────────────────────────┘
```

### Performance Summary

| Optimization | Technique                     | Speedup | Cumulative |
| ------------ | ----------------------------- | ------- | ---------- |
| Baseline     | Naive reflection per row      | 1.0x    | 1.0x       |
| Lesson 1     | Cache reflection results      | 6.2x    | 6.2x       |
| Lesson 2     | Array indexing (O(1))         | 2.2x    | 13.6x      |
| Lesson 3     | Type-specific handlers        | 1.26x   | 17.1x      |
| Lesson 4     | String optimization           | 1.1x    | 18.8x      |
| Lesson 5     | Bounded cache (no regression) | 1.0x    | 18.8x      |

**Final Result: 18.8x faster than naive approach, production-safe**

---

## JMH Benchmark Setup {#jmh-setup}

### Maven Dependencies

```xml
<dependencies>
    <!-- JMH Core -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>

    <!-- JMH Annotation Processor -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-generator-annprocess</artifactId>
        <version>1.37</version>
        <scope>test</scope>
    </dependency>

    <!-- H2 Database for testing -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>2.2.224</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Complete JMH Benchmark Class

```java
package com.company.app.service.database.handler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class ResultSetHandlerBenchmark {

    private Connection conn;
    private PreparedStatement queryStatement;
    private static final int ROW_COUNT = 10_000;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Create in-memory H2 database
        conn = DriverManager.getConnection("jdbc:h2:mem:benchmark");

        // Create test table
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE users (" +
                "  id INTEGER PRIMARY KEY, " +
                "  first_name VARCHAR(50), " +
                "  last_name VARCHAR(50), " +
                "  email VARCHAR(100), " +
                "  salary DECIMAL(10,2)" +
                ")"
            );
        }

        // Insert test data
        conn.setAutoCommit(false);
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO users VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < ROW_COUNT; i++) {
                pstmt.setInt(1, i);
                pstmt.setString(2, "First" + i);
                pstmt.setString(3, "Last" + i);
                pstmt.setString(4, "user" + i + "@example.com");
                pstmt.setBigDecimal(5, new java.math.BigDecimal("50000.00"));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        }
        conn.setAutoCommit(true);

        queryStatement = conn.prepareStatement("SELECT * FROM users");
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (queryStatement != null) queryStatement.close();
        if (conn != null) conn.close();
    }

    /**
     * Baseline: Create new handler for each query (no caching).
     * This simulates the cost of reflection per query.
     */
    @Benchmark
    public void baseline_noCaching(Blackhole bh) throws Exception {
        try (ResultSet rs = queryStatement.executeQuery()) {
            // Force handler creation on each query
            ResultSetHandler<User> handler =
                DefaultResultSetHandlerFactory.createHandler(User.class, rs.getMetaData());

            while (rs.next()) {
                bh.consume(handler.handle(rs));
            }
        }
    }

    /**
     * Optimized: Use cached handler from factory.
     * This is the real-world usage pattern.
     */
    @Benchmark
    public void optimized_withCaching(Blackhole bh) throws Exception {
        try (ResultSet rs = queryStatement.executeQuery()) {
            // Get cached handler (fast path after first call)
            ResultSetHandler<User> handler =
                DefaultResultSetHandlerFactory.getHandler(User.class, rs.getMetaData());

            while (rs.next()) {
                bh.consume(handler.handle(rs));
            }
        }
    }

    /**
     * Naive approach: Manual reflection per row (worst case).
     */
    @Benchmark
    public void naive_reflectionPerRow(Blackhole bh) throws Exception {
        try (ResultSet rs = queryStatement.executeQuery()) {
            NaiveResultSetMapper<User> mapper = new NaiveResultSetMapper<>();
            while (rs.next()) {
                bh.consume(mapper.map(rs, User.class));
            }
        }
    }

    // Sample User class for testing
    public static class User {
        private Integer id;
        private String firstName;
        private String lastName;
        private String email;
        private java.math.BigDecimal salary;

        // Getters and setters omitted for brevity
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public java.math.BigDecimal getSalary() { return salary; }
        public void setSalary(java.math.BigDecimal salary) { this.salary = salary; }
    }
}
```

### Running the Benchmark

```bash
# Build the project
mvn clean package -DskipTests

# Run JMH benchmarks
mvn test-compile exec:java \
    -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.classpathScope=test \
    -Dexec.args="ResultSetHandlerBenchmark"

# Run with specific parameters
mvn test-compile exec:java \
    -Dexec.mainClass=org.openjdk.jmh.Main \
    -Dexec.classpathScope=test \
    -Dexec.args="-f 1 -wi 3 -i 5 ResultSetHandlerBenchmark"
```

### Expected Output

```
Benchmark                                           Mode  Cnt    Score    Error  Units
ResultSetHandlerBenchmark.baseline_noCaching        avgt    5   12.456 ± 0.432  ms/op
ResultSetHandlerBenchmark.optimized_withCaching     avgt    5    3.876 ± 0.134  ms/op
ResultSetHandlerBenchmark.naive_reflectionPerRow    avgt    5   72.891 ± 2.156  ms/op

Performance Summary:
- Cached vs No Cache: 3.21x faster (68.9% improvement)
- Cached vs Naive:    18.8x faster (94.7% improvement)
```

---

## Key Takeaways for Engineering Teams

### What to Remember

1. **Measure First**: Always establish a baseline before optimizing
2. **Cache Strategically**: Cache expensive computations, but bound the cache
3. **Data Structures Matter**: O(1) array access vs O(log n) map lookup compounds over millions of rows
4. **Type Safety**: Generic `getObject()` hides performance and correctness issues
5. **String Operations**: They're more expensive than you think

### When to Apply These Patterns

✅ **Use this approach when:**

- Mapping 1000+ rows per query
- Running queries in tight loops
- Building ORM layers or data access frameworks
- Long-running applications with dynamic queries

❌ **Don't overcomplicate when:**

- Querying < 100 rows total
- One-off scripts or migrations
- Prototyping (optimize later if needed)

### Production Checklist

- [ ] Bounded cache (prevent memory leaks)
- [ ] Null validation on public APIs
- [ ] Logging for type conversion errors
- [ ] Thread-safe shared state
- [ ] Graceful degradation (no-op accessors for missing columns)
- [ ] JMH benchmarks in CI pipeline

---

## Further Reading

1. **Effective Java (Joshua Bloch)** - Item 55: Return optionals judiciously
2. **Java Performance (Scott Oaks)** - Chapter 4: Working with Collections
3. **JMH Documentation** - https://github.com/openjdk/jmh
4. **JDBC Specification** - Understanding ResultSet performance characteristics

---

**End of Tutorial**

_Questions? Open an issue or contact the performance engineering team._
