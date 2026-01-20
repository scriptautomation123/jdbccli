package com.company.app.service.service.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Stored procedure execution request backed by connection metadata and optional input/output
 * parameter strings.
 */
public record ProcedureRequest(
    DatabaseRequest connection,
    Optional<String> procedure,
    Optional<String> input,
    Optional<String> output)
    implements DbRequest {

  public ProcedureRequest {
    Objects.requireNonNull(connection, "Database connection details are required");
    procedure = procedure.flatMap(ProcedureRequest::normalize);
    input = input.flatMap(ProcedureRequest::normalize);
    output = output.flatMap(ProcedureRequest::normalize);
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

  private static Optional<String> normalize(final String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
