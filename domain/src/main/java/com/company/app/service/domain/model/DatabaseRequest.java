package com.company.app.service.domain.model;

/**
 * Core connection details shared by all database requests. Ensures non-blank connection metadata
 * and never exposes a null VaultConfig.
 */
public record DatabaseRequest(String type, String database, String user, VaultConfig vaultConfig) {

  public DatabaseRequest {
    validateNonBlank(type, "Database type cannot be blank");
    validateNonBlank(database, "Database name cannot be blank");
    validateNonBlank(user, "Database user cannot be blank");
    vaultConfig = vaultConfig != null ? vaultConfig : VaultConfig.empty();
  }

  private static void validateNonBlank(final String value, final String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
