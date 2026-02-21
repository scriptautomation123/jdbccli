# Test Suite Report (Current)

## Scope

This report summarizes the test coverage and how to run the suite. It avoids hard-coded counts and performance claims because those change as the code evolves.

## Test Suites

- Unit tests for core typed query and handler components
- Integration tests using Testcontainers with **multi-database support** (PostgreSQL, MySQL, SQL Server, Oracle)
- Performance benchmarks (JMH-based) for handler and string conversion paths

## Multi-Database Integration Testing

The integration test suite is parameterized to run against multiple database engines using the `-Ddatabase` system property:

```bash
# PostgreSQL (default, fastest startup)
mvn test -pl database -Dtest=JdbcCliLibraryIntegrationTest \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test

# MySQL
mvn test -pl database -Dtest=JdbcCliLibraryIntegrationTest -Ddatabase=mysql \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test

# SQL Server
mvn test -pl database -Dtest=JdbcCliLibraryIntegrationTest -Ddatabase=sqlserver \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test

# Oracle (longer startup time ~2-3 minutes on first run)
mvn test -pl database -Dtest=JdbcCliLibraryIntegrationTest -Ddatabase=oracle \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test
```

**Default:** If `-Ddatabase` is omitted, PostgreSQL is used (backward compatible).

### Database-Specific Implementation

Each database has database-specific SQL schema and type handling:

| Database   | Schema File          | Boolean Handling | Container          |
| ---------- | -------------------- | ---------------- | ------------------ |
| PostgreSQL | schema-postgres.sql  | true/false       | postgres:15-alpine |
| MySQL      | schema-mysql.sql     | 1/0              | mysql:8.0          |
| SQL Server | schema-sqlserver.sql | 1/0 (BIT)        | mssql/server:2022  |
| Oracle     | schema-oracle.sql    | 'Y'/'N'          | oracle-xe:21-slim  |

## How to Run

### Full test run (all modules, PostgreSQL)

```bash
mvn test \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test
```

## Performance Benchmarks (JMH)

These benchmarks validate the ResultSetHandler optimizations and string
conversion paths.

### Quick Start

```bash
# Build benchmark classes
cd /workspaces/jdbccli
mvn clean test-compile -pl database

# Run all benchmarks (no fork for dev environments)
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-f 0"

# Run a specific benchmark with quick settings
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="ResultSetHandlerPerformanceBenchmark -f 0 -wi 2 -i 3"

# Alternative: run the tests JAR
mvn package -pl database -DskipTests
java -jar database/target/database-1.0.0-tests.jar ResultSetHandlerPerformanceBenchmark
```

### Available Benchmarks

- `ResultSetHandlerPerformanceBenchmark` - End-to-end handler optimizations
- `UnderscoreToCamelCaseBenchmark` - String conversion optimization

### JMH Command Reference

```bash
# List all benchmarks
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="-l"

# Run with profilers
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="ResultSetHandlerPerformanceBenchmark -prof gc"

# Generate JSON results
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="ResultSetHandlerPerformanceBenchmark -rf json -rff results.json"

# Quick run (fewer iterations)
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="ResultSetHandlerPerformanceBenchmark -f 1 -wi 2 -i 3"
```

### JMH Parameters (Common)

- `-f 1` - Fork once (separate JVM)
- `-wi 3` - Warmup iterations
- `-i 5` - Measurement iterations
- `-prof gc` - Enable GC profiler
- `-rf json` - Result format (json, csv, text)
- `-rff file.json` - Result file path

### Troubleshooting

- `ClassNotFoundException: org.openjdk.jmh.Main` -> run `mvn clean test-compile -pl database`
- Benchmarks run too long -> use `-f 1 -wi 2 -i 3`
- Results inconsistent -> increase iterations: `-wi 5 -i 10`
- Out of memory -> increase heap in @Fork (e.g., `-Xms4G -Xmx4G`)

## Notes

- **Docker requirement:** Integration tests use Testcontainers and require a reachable Docker daemon with API version ≥1.44. Use `-Dapi.version=1.52` if negotiation fails.
- **Password override:** For non-interactive runs, provide a password via `-Djdbccli.password=YOUR_PASSWORD` or environment variable `JDBCCLI_PASSWORD=YOUR_PASSWORD`.
- **Performance measurement:** Use the JMH section above for current numbers.
- **Database containers:** First run of each database may take longer due to image download and container initialization.
- **Test isolation:** Each test class uses a separate container instance; containers are cleaned up after tests complete.
