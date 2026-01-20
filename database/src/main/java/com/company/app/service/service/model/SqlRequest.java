package com.company.app.service.service.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQL execution request backed by connection metadata and optional SQL payloads. */
public record SqlRequest(
    DatabaseRequest connection, Optional<String> sql, Optional<String> script, List<Object> params)
    implements DbRequest {

  public SqlRequest {
    Objects.requireNonNull(connection, "Database connection details are required");
    sql = sql.flatMap(SqlRequest::normalize);
    script = script.flatMap(SqlRequest::normalize);
    params = params == null ? List.of() : List.copyOf(params);

    if (sql.isPresent() && script.isPresent()) {
      throw new IllegalArgumentException("Provide either SQL or --script, not both");
    }
  }

  @Override
  public String type() {
    return connection.type();
  }

  @Override
  public String database() {
    return connection.database();
  }

  @Override
  public String user() {
    return connection.user();
  }

  @Override
  public VaultConfig vaultConfig() {
    return connection.vaultConfig();
  }

  public boolean isScriptMode() {
    return script.isPresent();
  }

  public boolean isSqlMode() {
    return sql.isPresent();
  }

  private static Optional<String> normalize(final String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
