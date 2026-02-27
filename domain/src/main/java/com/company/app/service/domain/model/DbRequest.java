package com.company.app.service.domain.model;

import java.util.Optional;

/**
 * Sealed marker interface for all database requests handled by the CLI.
 *
 * <p>
 * Subtypes compose a {@link DatabaseRequest} for connection details. The four
 * connection
 * accessors are provided as {@code default} methods delegating to
 * {@link #connection()}, so
 * subtypes only need to expose the connection record — no delegation
 * boilerplate required.
 */
public sealed interface DbRequest permits SqlRequest, ProcedureRequest {

  /** Core connection details (type, database, user, vault config). */
  DatabaseRequest connection();

  /** Database type (e.g., oracle, postgresql). */
  default String type() {
    return connection().type();
  }

  /** Target database name/schema. */
  default String database() {
    return connection().database();
  }

  /** Database username. */
  default String user() {
    return connection().user();
  }

  /** Vault configuration (never null). */
  default VaultConfig vaultConfig() {
    return connection().vaultConfig();
  }

  /**
   * Normalizes an Optional string field: empty/blank values become
   * {@code Optional.empty()}.
   * Shared by all {@code DbRequest} subtypes to avoid duplication in compact
   * constructors.
   *
   * @param value raw string value
   * @return normalized Optional
   */
  static Optional<String> normalize(final String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
