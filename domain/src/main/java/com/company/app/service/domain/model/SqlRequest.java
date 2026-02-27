package com.company.app.service.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SQL execution request backed by connection metadata and optional SQL
 * payloads.
 */
public record SqlRequest(
    DatabaseRequest connection, Optional<String> sql, Optional<String> script, List<Object> params)
    implements DbRequest {

  public SqlRequest {
    Objects.requireNonNull(connection, "Database connection details are required");
    sql = sql.flatMap(DbRequest::normalize);
    script = script.flatMap(DbRequest::normalize);
    params = params == null ? List.of() : List.copyOf(params);

    if (sql.isPresent() && script.isPresent()) {
      throw new IllegalArgumentException("Provide either SQL or --script, not both");
    }
  }

  @Override
  public DatabaseRequest connection() {
    return connection;
  }

  public boolean isScriptMode() {
    return script.isPresent();
  }

  public boolean isSqlMode() {
    return sql.isPresent();
  }
}
