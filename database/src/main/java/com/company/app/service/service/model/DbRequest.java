package com.company.app.service.service.model;

/**
 * Sealed marker interface for all database requests handled by the CLI. Provides a minimal surface
 * for connection creation and password resolution while allowing request-specific payloads to be
 * expressed by subtypes.
 */
public sealed interface DbRequest permits SqlRequest, ProcedureRequest {

  /** Database type (e.g., oracle, postgresql). */
  String type();

  /** Target database name/schema. */
  String database();

  /** Database username. */
  String user();

  /** Vault configuration (never null). */
  VaultConfig vaultConfig();
}
