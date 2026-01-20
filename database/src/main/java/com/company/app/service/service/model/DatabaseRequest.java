package com.company.app.service.service.model;

/**
 * Core connection details shared by all database requests. Ensures non-blank connection metadata
 * and never exposes a null VaultConfig.
 */
public record DatabaseRequest(String type, String database, String user, VaultConfig vaultConfig) {

  public DatabaseRequest {
    type = requireNonBlank(type, "Database type cannot be blank");
    database = requireNonBlank(database, "Database name cannot be blank");
    user = requireNonBlank(user, "Database user cannot be blank");
    vaultConfig = vaultConfig != null ? vaultConfig : VaultConfig.empty();
  }

  private static String requireNonBlank(final String value, final String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }
}
