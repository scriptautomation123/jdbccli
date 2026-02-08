# Copilot Instructions (jdbccli)

## Architecture and Key Flows
- Public API entry point is JdbcCliLibrary in [database/src/main/java/com/company/app/service/JdbcCliLibrary.java](database/src/main/java/com/company/app/service/JdbcCliLibrary.java), which routes typed queries through QueryExecutor and formatted queries through SqlExecutorService.
- Connection lifecycle and password resolution are centralized in DatabaseExecutionContext in [database/src/main/java/com/company/app/service/service/DatabaseExecutionContext.java](database/src/main/java/com/company/app/service/service/DatabaseExecutionContext.java).
- Vault-based password lookup uses PasswordResolver and VaultClient in [vault-client/src/main/java/com/company/app/service/auth/PasswordResolver.java](vault-client/src/main/java/com/company/app/service/auth/PasswordResolver.java) and [vault-client/src/main/java/com/company/app/service/util/VaultClient.java](vault-client/src/main/java/com/company/app/service/util/VaultClient.java).
- Connection string templates come from application.yaml via ConnectionStringGenerator in [database/src/main/java/com/company/app/service/database/ConnectionStringGenerator.java](database/src/main/java/com/company/app/service/database/ConnectionStringGenerator.java). YamlConfig only reads filesystem paths in [util/src/main/java/com/company/app/service/util/YamlConfig.java](util/src/main/java/com/company/app/service/util/YamlConfig.java).
- CLI commands and password prompting live in BaseDatabaseCliCommand in [cli/src/main/java/com/company/app/service/cli/BaseDatabaseCliCommand.java](cli/src/main/java/com/company/app/service/cli/BaseDatabaseCliCommand.java).

## Build and Test Workflows
- Full test workflow uses Docker: run docker compose then manage.sh, per [README.md](README.md).
- Typical commands:
  - ./manage.sh (runs tests)
  - ./manage.sh -i (interactive mode)
  - mvn test -pl database (database tests only)
  - mvn spotless:apply (formatting)
- Integration tests use Testcontainers (PostgreSQL). Docker must be available.
- Testcontainers prerequisites in this repo:
  - Docker daemon reachable from the dev container (socket or DOCKER_HOST set).
  - Docker API version must be >= 1.44; in this environment we often pass -Dapi.version=1.52.
  - If tests need config templates, pass -Dvault.config=/path/to/application.yaml (YamlConfig reads filesystem only).
  - Avoid blocking password prompts in non-interactive runs with -Djdbccli.password=... or JDBCCLI_PASSWORD=....

## Configuration and Overrides
- Vault config path is provided with system property vault.config and must point to a real file path.
- To avoid blocking password prompts in automation, provide either:
  - System property jdbccli.password
  - Environment variable JDBCCLI_PASSWORD
- CLI examples in [README.md](README.md) show how vault.config is passed to the CLI.

## Project Conventions
- Typed query path uses the ResultSetHandler framework and LRU cache for performance; see overview in [README.md](README.md) and details in [doccs/README.md](doccs/README.md).
- Prefer module-specific Maven commands when iterating on a single area (database, cli, vault-client).
