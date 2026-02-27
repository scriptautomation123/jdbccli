package com.company.app.service.domain.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Stored procedure execution request backed by connection metadata and optional
 * input/output
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
    procedure = procedure.flatMap(DbRequest::normalize);
    input = input.flatMap(DbRequest::normalize);
    output = output.flatMap(DbRequest::normalize);
  }

  @Override
  public DatabaseRequest connection() {
    return connection;
  }
}
