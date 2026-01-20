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
   * Checks if all direct vault parameters are provided (not null or blank).
   * 
   * @return true if all parameters are provided
   */
  public boolean hasDirectVaultParams() {
    return isNonBlank(vaultUrl) && isNonBlank(roleId) && isNonBlank(secretId) && isNonBlank(ait);
  }

  /**
   * Creates an empty VaultConfig with all null values.
   * 
   * @return empty vault configuration
   */
  public static VaultConfig empty() {
    return new VaultConfig(null, null, null, null);
  }

  /**
   * Checks if a string is not null and contains non-whitespace characters.
   * 
   * @param value string to check
   * @return true if non-null and non-blank
   */
  private static boolean isNonBlank(final String value) {
    return value != null && !value.isBlank();
  }
}
