# JDBC CLI

A command-line tool and library for executing SQL queries and stored procedures against various databases with HashiCorp Vault integration for secure password management.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          JdbcCliLibrary (Public API)                     │
├──────────────────────────────┬──────────────────────────────────────────┤
│  Typed Query API (NEW)       │  String-Based API (CLI)                  │
│  • queryForList()            │  • executeSql()                           │
│  • queryForObject()          │  • executeScript()                        │
│  Returns: List<T>            │  • executeProcedure()                     │
│  Performance: 18.5x faster   │  Returns: ExecutionResult (String)        │
└──────────────┬───────────────┴──────────────┬───────────────────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────┐   ┌──────────────────────────────────────┐
│   QueryExecutor          │   │   SqlExecutorService                 │
│   • executeTyped()       │   │   • execute()                        │
│                          │   │   • executePreparedStatement()       │
└──────────┬───────────────┘   └──────────┬───────────────────────────┘
           │                              │
           ▼                              ▼
┌──────────────────────────┐   ┌──────────────────────────────────────┐
│ ResultSetHandler         │   │   SqlJdbcHelper                      │
│ Framework (Optimized)    │   │   • formatResultSet()                │
│ • LRU cache (1000)       │   │   Returns: Formatted string table    │
│ • Accessor arrays O(1)   │   └──────────────────────────────────────┘
│ • Type handler registry  │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  Typed Domain Objects    │
│  List<Employee>          │
│  List<Order>             │
└──────────────────────────┘
```

### Key Components

- **QueryExecutor** - Unified query execution with dual modes (typed/formatted)
- **ResultSetHandler** - High-performance object mapping (18.5x faster than naive reflection)
- **SqlJdbcHelper** - Direct ResultSet → String formatting for CLI display
- **DatabaseExecutionContext** - Connection lifecycle, password resolution, error handling
- **ProcedureExecutor** - Stored procedure execution with IN/OUT parameters

## Features

- Execute SQL statements and scripts
- Execute stored procedures with input/output parameters
- Oracle PL/SQL block support (BEGIN...END with `/` delimiter)
- HashiCorp Vault integration for password resolution
- Support for Oracle, PostgreSQL, MySQL, H2

## Quick Start

### Run all tests

```bash
cd docker && docker compose down -v && docker compose up -d && cd ..
./manage.sh
```

### Testcontainers prerequisites (integration tests)

Integration tests use Testcontainers (PostgreSQL) and require a reachable Docker daemon.

```bash
# If Docker API version negotiation fails in this environment
mvn -Dapi.version=1.52 test

# If config templates are required (YamlConfig reads filesystem only)
mvn -Dvault.config=/path/to/application.yaml test

# Avoid blocking password prompts in non-interactive runs
mvn -Djdbccli.password=your_password test
# or
JDBCCLI_PASSWORD=your_password mvn test
```

### Interactive Mode

```bash
./manage.sh -i
```

### Available Commands

```bash
./manage.sh --help           # Show all options
./manage.sh --spotless       # Format code & commit
./manage.sh --sbom           # Generate SBOM dependency report
./manage.sh --build          # Build project only
./manage.sh --refresh        # Refresh Oracle DB before tests
./manage.sh --migrate-pkg OLD NEW  # Migrate package paths
```

### Generate SBOM

```bash
# Aggregate SBOM for all modules
mvn cyclonedx:makeAggregateBom

# Or generate SBOM for individual modules
mvn cyclonedx:makeBom
```

### SBOM Dependency Report

Generate a visual report of dependencies, transitive dependencies, and version conflicts:

```bash
# First generate the SBOM
mvn cyclonedx:makeAggregateBom

# Then run the report generator
mvn compile -pl util -q
java -cp util/target/classes \
  com.company.app.service.util.SbomReportGenerator target/sbom.xml
```

**Report includes:**

- 📊 Summary of internal modules vs external libraries
- ⚠️ Version conflict detection with recommendations
- 🌳 Dependency tree with transitive dependencies
- 📜 License summary for compliance review
- 📚 External dependencies table

Example output:

```text
╔══════════════════════════════════════════════════════════════════╗
║                    SBOM DEPENDENCY REPORT                        ║
╚══════════════════════════════════════════════════════════════════╝

📄 Source: target/sbom.xml
📊 Total Components: 17

│ 🏠 Internal Modules:      7                                     │
│ 📦 External Libraries:   10                                     │

🌳 DEPENDENCY TREE
📦 🏠 cli-parent:1.0.0
  ├── 🏠 cli-domain:1.0.0
  ├── 🏠 cli-util:1.0.0
    ├── 📚 log4j-api:2.25.3
    ├── 📚 jackson-databind:2.19.2
    ...
```

---

## DuckDB Experimentation (Analytics)

The project includes DuckDB support for experimenting with columnar analytics as an alternative to traditional JDBC for certain workloads.

### When to Use DuckDB vs Oracle JDBC

| Use Case                         | Recommendation |
| :------------------------------- | :------------- |
| Oracle production data           | JDBC           |
| Stored procedures                | JDBC only      |
| Local analytics on files         | DuckDB         |
| Large aggregations (>100K rows)  | DuckDB         |
| Query CSV/Parquet directly       | DuckDB         |

### Run DuckDB Benchmark

Compare DuckDB vs traditional row-store (H2) performance:

```bash
cd database
mvn test-compile exec:java \
  -Dexec.mainClass="com.company.app.service.database.DuckDbExperiment" \
  -Dexec.classpathScope=test
```

### Query Files Directly with DuckDB

```bash
# Query a CSV file
mvn test-compile exec:java \
  -Dexec.mainClass="com.company.app.service.database.DuckDbExperiment" \
  -Dexec.classpathScope=test \
  -Dexec.args="csv:/path/to/data.csv"

# Query a Parquet file
mvn test-compile exec:java \
  -Dexec.mainClass="com.company.app.service.database.DuckDbExperiment" \
  -Dexec.classpathScope=test \
  -Dexec.args="parquet:/path/to/data.parquet"
```

### DuckDB in Code

```java
// In-memory DuckDB
try (Connection conn = DuckDbExperiment.createConnection()) {
    // Query Parquet directly - no ETL needed!
    ExecutionResult result = DuckDbExperiment.execute(conn,
        "SELECT * FROM read_parquet('data.parquet') WHERE amount > 100");
}

// Or use standard JDBC
try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
    // Works with existing SqlJdbcHelper
    ResultSet rs = stmt.executeQuery("SELECT * FROM read_csv_auto('data.csv')");
    ExecutionResult result = SqlJdbcHelper.formatResultSet(rs);
}
```

---

## CLI Usage Examples

### 0. Execute basic SQL query

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-sql "SELECT * FROM hr.employees WHERE rownum <= 5" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 1. Get employee salary (use SQL, not procedure)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.hr_pkg.get_employee_salary(100) as salary FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 2. Get department budget (use SQL)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.hr_pkg.get_department_budget(80) as budget FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 3. Calculate bonus (use SQL)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-sql "SELECT hr.calculate_bonus(10000, 15) as bonus FROM dual" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 4. Get employee details (procedure with input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.get_employee_details \
--input "p_employee_id:NUMBER:100" \
--output "o_first_name:VARCHAR2,o_last_name:VARCHAR2,o_email:VARCHAR2,o_salary:NUMBER,o_job_id:VARCHAR2" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 5. Get department info (procedure with input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.get_department_info \
--input "p_department_id:NUMBER:80" \
--output "o_department_name:VARCHAR2,o_manager_id:NUMBER,o_employee_count:NUMBER,o_total_salary:NUMBER" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 6. Raise employee salary (package procedure with multiple inputs)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.raise_employee_salary \
--input "p_employee_id:NUMBER:100,p_raise_percent:NUMBER:10" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 7. Hire new employee (package procedure with 6 input parameters)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.hire_employee \
--input "p_first_name:VARCHAR2:John,p_last_name:VARCHAR2:Doe,p_email:VARCHAR2:jdoe@example.com,p_job_id:VARCHAR2:IT_PROG,p_salary:NUMBER:8000,p_department_id:NUMBER:60" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 8. Update job history (package procedure with 3 input parameters)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.update_job_history \
--input "p_employee_id:NUMBER:100,p_new_job_id:VARCHAR2:AD_VP,p_new_department_id:NUMBER:90" \
--type oracle \
--database localhost:1521:xe \
--user hr
```

### 9. Terminate employee (package procedure with 1 input parameter)

```bash
cd ~/code/scriptautomation123/jdbccli/package-helper/target/dist/cli-1.0.0 &&\
./jre/bin/java \
-Dlog4j.configurationFile=file:./log4j2.xml \
-Dvault.config=./vaults.yaml \
-Djdbccli.password=your_password \
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.terminate_employee \
--input "p_employee_id:NUMBER:100" \
--type oracle \
--database localhost:1521:xe \
--user hr

### Non-interactive password override

Use these when running tests or automation to avoid blocking prompts:

```bash
# System property
mvn -Djdbccli.password=your_password test

# Or environment variable
JDBCCLI_PASSWORD=your_password mvn test
```
```

---

## Project Structure

```text
jdbccli/
├── cli/           # PicoCLI command implementations
├── database/      # JDBC services, DuckDB experiment
├── domain/        # Request/response records (sealed types)
├── util/          # Logging, exception handling, YAML config
├── vault-client/  # HashiCorp Vault HTTP client
└── package-helper/# Fat JAR packaging with JRE
```

## Java 21 Features Used

- **Sealed interfaces** - `DbRequest` permits only `SqlRequest`, `ProcedureRequest`
- **Records with withers** - Immutable fluent API for request building
- **Pattern matching** - Switch expressions with type patterns
- **Virtual threads** - Used in VaultClient for I/O operations
- **Text blocks** - Multi-line SQL in code

## Architecture Highlights

- **ScriptParser** - Handles Oracle PL/SQL blocks (BEGIN...END with `/`)
- **ResultFormatter** - Abstraction point for future Arrow Flight SQL
- **DatabaseExecutionContext** - Composition over inheritance for DB operations

---

## Code Formatting (Google Java Format)

The project uses **Google Java Format v1.21.0** via **Spotless Maven Plugin v2.44.0** for consistent code style across all 24 Java source files (3,717 lines).

### Quick Commands

```bash
# Apply formatting to all files
mvn spotless:apply

# Check formatting compliance (CI/CD)
mvn spotless:check

# Format only modified files (faster)
mvn spotless:apply -DspotlessFollow=true

# Format specific module only
mvn -pl database spotless:apply
mvn -pl cli spotless:apply
```

### Formatting Rules

**Configuration:** `pom.xml` (parent module)

```xml
<java>
    <googleJavaFormat>
        <version>1.21.0</version>
        <style>GOOGLE</style>  <!-- or AOSP for 4-space indent -->
        <reflowLongStrings>true</reflowLongStrings>
    </googleJavaFormat>
    <trimTrailingWhitespace/>
    <endWithNewline/>
    <importOrder>
        <order>java,javax,org,com</order>
        <wildcardsLast>true</wildcardsLast>
    </importOrder>
    <removeUnusedImports/>
</java>
```

| Rule                | Setting                            |
| :------------------ | :--------------------------------- |
| Indentation         | 2 spaces (GOOGLE) / 4 spaces (AOSP) |
| Line length         | 100 characters                     |
| Import order        | `java` → `javax` → `org` → `com`   |
| Wildcard imports    | Last                               |
| Trailing whitespace | Removed                            |
| Unused imports      | Removed                            |
| File endings        | Newline added                      |

### IDE Integration

#### VS Code

1. Install [Google Java Format](https://marketplace.visualstudio.com/items?itemName=joseandrade.google-java-format-for-vs-code) extension
2. Add to `settings.json`:
```json
{
  "[java]": {
    "editor.defaultFormatter": "joseandrade.google-java-format-for-vs-code",
    "editor.formatOnSave": true
  }
}
```

#### IntelliJ IDEA

1. Install "Google Java Format" plugin (Settings → Plugins)
2. Enable: Settings → Editor → Code Style → Scheme → "Google Style"
3. Optional: Settings → Tools → Actions on Save → "Reformat code"

#### Eclipse

Install **google-java-format** from Eclipse Marketplace

### Before Committing

```bash
# Format and verify
mvn spotless:apply && mvn spotless:check

# Then commit
git add -A && git commit -m "Your message"
```

### Pre-commit Hook (Optional)

Create `.git/hooks/pre-commit`:
```bash
#!/bin/bash
mvn spotless:check
if [ $? -ne 0 ]; then
    echo "❌ Code formatting issues detected."
    echo "Run: mvn spotless:apply"
    exit 1
fi
```

Make executable: `chmod +x .git/hooks/pre-commit`

### CI/CD Integration

```bash
# In your build pipeline
mvn spotless:check  # Fail build on violations

# Or auto-fix (optional)
mvn spotless:apply && git diff --exit-code
```

### Troubleshooting

| Issue | Solution |
|-------|----------|
| Plugin fails | `mvn clean install && mvn spotless:apply` |
| Too many files | `mvn spotless:apply -DspotlessFollow=true` |
| IDE differs | Ensure Google Java Format extension installed |
| Import order changes | Check `<order>java,javax,org,com</order>` in pom.xml |

### Example Transformation

**Before:**
```java
import java.util.*;import com.company.app.service.util.*;
public class MyClass {
public void method1(String a,String b){
LOGGER.info("event="+a);}
}
```

**After (Google Java Format):**
```java
import java.util.List;

import com.company.app.service.util.ExceptionUtils;

public class MyClass {

  public void method1(String a, String b) {
    LOGGER.info("event={}", a);
  }
}
```

### Resources

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spotless Maven Plugin](https://github.com/diffplug/spotless/tree/main/plugin-maven)
- See [GOOGLE_JAVA_FORMAT_GUIDE.md](GOOGLE_JAVA_FORMAT_GUIDE.md) for advanced configuration
