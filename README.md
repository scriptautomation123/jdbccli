# JDBC CLI
Essentially, CLI classes use the jdbchelper builder class to do db oerpations, everything else is supporting


A command-line tool for executing SQL queries and stored procedures against various databases with HashiCorp Vault integration for secure password management.

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
./run_all_tests.sh
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
-jar ./cli-1.0.0.jar exec-proc hr.hr_pkg.terminate_employee \
--input "p_employee_id:NUMBER:100" \
--type oracle \
--database localhost:1521:xe \
--user hr
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

The project uses **Google Java Format** via the **Spotless Maven Plugin** for consistent code style.

### Quick Commands

```bash
# Apply formatting to all files
mvn spotless:apply

# Check formatting compliance (CI/CD)
mvn spotless:check

# Format specific module only
mvn -pl util spotless:apply
mvn -pl database spotless:apply
```

### Formatting Rules

| Rule                | Setting                       |
| :------------------ | :---------------------------- |
| Indentation         | 2 spaces (Google standard)    |
| Line length         | 100 characters                |
| Import order        | `java`, `javax`, `org`, `com` |
| Trailing whitespace | Removed                       |
| Unused imports      | Removed                       |

### IDE Integration

**VS Code:** Install [Google Java Format](https://marketplace.visualstudio.com/items?itemName=joseandrade.google-java-format-for-vs-code) extension, enable format on save.

**IntelliJ IDEA:** Install "Google Java Format" plugin from Settings → Plugins.

### Before Committing

```bash
mvn spotless:apply && mvn spotless:check && git add -A
```

See [GOOGLE_JAVA_FORMAT_GUIDE.md](GOOGLE_JAVA_FORMAT_GUIDE.md) for detailed configuration and troubleshooting.
