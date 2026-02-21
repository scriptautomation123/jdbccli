# JDBC CLI Documentation

This directory contains the curated documentation for the JDBC CLI project and its typed query API.

## Documentation Index

- [TUTORIAL.md](TUTORIAL.md) - Performance optimization walkthrough and design rationale
- [COMPREHENSIVE_TEST_REPORT.md](COMPREHENSIVE_TEST_REPORT.md) - How to run tests and benchmarks
- [GOOGLE_JAVA_FORMAT_GUIDE.md](GOOGLE_JAVA_FORMAT_GUIDE.md) - Spotless and Google Java Format setup

## Key Entry Points

- CLI command wiring and password prompts: cli/src/main/java/com/company/app/service/cli/BaseDatabaseCliCommand.java
- Library API surface and execution flow: database/src/main/java/com/company/app/service/JdbcCliLibrary.java
- Password resolution and Vault fallback: vault-client/src/main/java/com/company/app/service/auth/PasswordResolver.java
- Vault HTTP client: vault-client/src/main/java/com/company/app/service/util/VaultClient.java
- Connection string configuration loader: database/src/main/java/com/company/app/service/database/ConnectionStringGenerator.java
- YAML config loader (filesystem only): util/src/main/java/com/company/app/service/util/YamlConfig.java

## Quick Commands

See [COMPREHENSIVE_TEST_REPORT.md](COMPREHENSIVE_TEST_REPORT.md) for the latest
test and benchmark commands.
