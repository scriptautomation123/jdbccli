package com.company.app.service.service.model;

/**
 * Immutable configuration record for Vault authentication parameters.
 * Contains URL, role ID, secret ID, and AIT for secure password resolution.
 * 
 * @param vaultUrl vault server base URL
 * @param roleId   vault role ID for authentication
 * @param secretId vault secret ID for authentication
 * @param ait      application identifier token
 */
public record VaultConfig(String vaultUrl, String roleId, String secretId, String ait) {

  /**
   * Creates an empty VaultConfig with all null values.
   * 
   * @return empty vault configuration
   */
  public static VaultConfig empty() {
    return new VaultConfig(null, null, null, null);
  }
}
