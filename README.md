# JDBC CLI

A command-line tool and library for executing SQL queries and stored procedures against Oracle, PostgreSQL, MySQL, SQL Server, and H2 — with HashiCorp Vault integration for secure credential management.

---

## Documentation

| Doc | Purpose |
|:----|:--------|
| [docs/TUTORIAL.md](docs/TUTORIAL.md) | Typed API design rationale and performance walkthrough |
| [docs/COMPREHENSIVE_TEST_REPORT.md](docs/COMPREHENSIVE_TEST_REPORT.md) | Integration test setup and benchmark results across all databases |
| [docs/GOOGLE_JAVA_FORMAT_GUIDE.md](docs/GOOGLE_JAVA_FORMAT_GUIDE.md) | Spotless / Google Java Format setup for IDE, CI, and pre-commit |
| [docs/typed-api-flow.svg](docs/typed-api-flow.svg) | Full call-flow sequence diagram (principal engineer reference) |
| [docs/typed-api-flow.puml](docs/typed-api-flow.puml) | PlantUML source for the diagram |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       JdbcCliLibrary  (Public API)                       │
├────────────────────────────────┬────────────────────────────────────────┤
│  Typed Query API               │  String-Based API (CLI)                │
│  queryForList() / queryForObject()  │  executeSql() / executeScript()   │
│  Returns: List<T>              │  executeProcedure()                    │
│  Performance: LRU-cached       │  Returns: ExecutionResult (String)     │
└───────────────┬────────────────┴──────────────┬────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────┐   ┌──────────────────────────────────────┐
│  QueryExecutorTyped        │   │  SqlExecutorService                  │
│  executeTyped()            │   │  execute() / executePreparedStatement│
└───────────────────────────┘   └──────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────┐
│  ResultSetHandler Framework                            │
│  • DefaultResultSetHandlerFactory  (LRU cache ≤1000)  │
│  • ObjectResultHandler             (accessor array)   │
│  • TypeHandlerRegistry             (singleton)        │
│  • TypeHandlerPropertyAccessor     (pre-resolved)     │
└───────────────────────────────────────────────────────┘
```

See [docs/typed-api-flow.svg](docs/typed-api-flow.svg) for the annotated sequence diagram with code snippets at every layer.

---

## Key Source Entry Points

| Class | Role |
|:------|:-----|
| [`JdbcCliLibrary`](database/src/main/java/com/company/app/service/JdbcCliLibrary.java) | Public API surface — entry point for all queries |
| [`BaseDatabaseCliCommand`](cli/src/main/java/com/company/app/service/cli/BaseDatabaseCliCommand.java) | CLI command wiring, password prompts |
| [`DatabaseExecutionContext`](database/src/main/java/com/company/app/service/service/DatabaseExecutionContext.java) | Connection lifecycle, password resolution, error handling |
| [`PasswordResolver`](vault-client/src/main/java/com/company/app/service/auth/PasswordResolver.java) | Vault-based password lookup with interactive fallback |
| [`VaultClient`](vault-client/src/main/java/com/company/app/service/util/VaultClient.java) | HashiCorp Vault HTTP client |
| [`ConnectionStringGenerator`](database/src/main/java/com/company/app/service/database/ConnectionStringGenerator.java) | Connection string templates per database type (Oracle JDBC/LDAP, PostgreSQL, MySQL, SQL Server, H2) |
| [`YamlConfig`](util/src/main/java/com/company/app/service/util/YamlConfig.java) | YAML config loader — filesystem paths only |

---

## Project Structure

```
jdbccli/
├── cli/            # PicoCLI command implementations
├── database/       # JDBC services, typed API, DuckDB experiment
├── domain/         # Request/response records (sealed types)
├── util/           # Logging, exception handling, YAML config
├── vault-client/   # HashiCorp Vault HTTP client
└── package-helper/ # Fat JAR packaging with bundled JRE
```

---

## Quick Start

### Run all tests (Docker-based)

```bash
cd docker && docker compose down -v && docker compose up -d && cd ..
./manage.sh
```

### Integration tests by database

```bash
# PostgreSQL (default, fastest)
mvn test -pl database -Dtest=JdbcCliLibraryIntegrationTest \
  -Ddatabase=postgres -Dapi.version=1.52 \
  -Dvault.config=/workspaces/jdbccli/cli/src/main/resources/application.yaml \
  -Djdbccli.password=test

# MySQL / SQL Server / Oracle — same flags, change -Ddatabase=mysql|sqlserver|oracle
```

See [docs/COMPREHENSIVE_TEST_REPORT.md](docs/COMPREHENSIVE_TEST_REPORT.md) for full matrix, benchmark commands, and Testcontainers prerequisites.

### Interactive mode

```bash
./manage.sh -i
```

### manage.sh reference

```bash
./manage.sh             # Run tests
./manage.sh --build     # Build only
./manage.sh --spotless  # Format code and commit
./manage.sh --sbom      # Generate SBOM
./manage.sh --refresh   # Refresh Oracle DB before tests
./manage.sh --migrate-pkg OLD NEW  # Migrate package paths
```

---

## Configuration

### Vault config

Pass as a system property pointing to a filesystem YAML file:

```bash
-Dvault.config=/path/to/application.yaml
```

### Avoid blocking password prompts in automation

```bash
# System property
-Djdbccli.password=your_password

# Environment variable
JDBCCLI_PASSWORD=your_password mvn test
```

---

## CLI Usage

### Basic SQL

```bash
cd package-helper/target/dist/cli-1.0.0
./jre/bin/java \
  -Dlog4j.configurationFile=file:./log4j2.xml \
  -Dvault.config=./vaults.yaml \
  -Djdbccli.password=your_password \
  -jar ./cli-1.0.0.jar exec-sql "SELECT * FROM hr.employees WHERE rownum <= 5" \
  --type oracle --database localhost:1521:xe --user hr
```

Supported `--type` values: `oracle` `postgresql` `mysql` `sqlserver` `h2`

### Stored procedure

```bash
./jre/bin/java ... -jar ./cli-1.0.0.jar exec-proc hr.get_employee_details \
  --input  "p_employee_id:NUMBER:100" \
  --output "o_first_name:VARCHAR2,o_last_name:VARCHAR2,o_salary:NUMBER" \
  --type oracle --database localhost:1521:xe --user hr
```

---

## Code Formatting

Uses **Google Java Format v1.21.0** via **Spotless v2.44.0**.

```bash
mvn spotless:apply   # fix all files
mvn spotless:check   # CI check (fails on violations)
```

See [docs/GOOGLE_JAVA_FORMAT_GUIDE.md](docs/GOOGLE_JAVA_FORMAT_GUIDE.md) for IDE setup (VS Code / IntelliJ / Eclipse), pre-commit hook, and CI pipeline integration.

---

## Java 21 Features

| Feature | Where used |
|:--------|:-----------|
| Sealed interfaces | `DbRequest` permits `SqlRequest`, `ProcedureRequest` |
| Records | Immutable request/response types with fluent withers |
| Pattern-matching switch | `TypedSqlExecutorService` dispatch |
| Virtual threads | `VaultClient` I/O, `DatabaseExecutionContext` |
| Text blocks | Multi-line SQL in tests |

---

## SBOM

```bash
mvn cyclonedx:makeAggregateBom
java -cp util/target/classes \
  com.company.app.service.util.SbomReportGenerator target/sbom.xml
```

Output includes dependency tree, version conflict detection, and license summary.
