# Test Suite Report (Current)

## Scope

This report summarizes the test coverage and how to run the suite. It avoids hard-coded counts and performance claims because those change as the code evolves.

## Test Suites

- Unit tests for core typed query and handler components
- Integration tests using Testcontainers (PostgreSQL)
- Performance benchmarks (JMH-based) for handler and string conversion paths

## How to Run

### Full test run (with Docker)

```bash
mvn test \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test
```

### Database module only

```bash
mvn test -pl database \
  -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test
```

### JMH benchmarks (module scoped)

```bash
mvn exec:java -pl database \
  -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.classpathScope=test \
  -Dexec.args="ResultSetHandlerPerformanceBenchmark"
```

## Notes

- Integration tests require a reachable Docker daemon.
- For non-interactive runs, provide a password via `-Djdbccli.password` or `JDBCCLI_PASSWORD`.
- For the latest performance numbers, run the JMH benchmarks in your environment.
