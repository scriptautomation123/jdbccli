package com.company.app.service.service.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Stored procedure execution request backed by connection metadata and
 * optional input/output parameter strings.
 */
public record ProcedureRequest(
    DatabaseRequest connection,
    Optional<String> procedure,
    Optional<String> input,
    Optional<String> output) implements DbRequest {

  public ProcedureRequest {
    connection = Objects.requireNonNull(connection, "Database connection details are required");
    procedure = normalize(procedure);
    input = normalize(input);
    output = normalize(output);
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

  public boolean isPasswordOnlyMode() {
    return procedure.isEmpty();
  }

  private static Optional<String> normalize(final Optional<String> value) {
    return value == null ? Optional.empty() : value.filter(v -> !v.isBlank());
  }
}
