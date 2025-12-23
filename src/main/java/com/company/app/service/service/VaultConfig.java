package com.company.app.service.service;

/**
 * Immutable configuration object for Vault authentication parameters.
 * Contains URL, role ID, secret ID, and AIT for secure password resolution.
 */
public final class VaultConfig {

  /** Vault server base URL */
  private final String vaultUrl;

  /** Vault role ID for authentication */
  private final String roleId;

  /** Vault secret ID for authentication */
  private final String secretId;

  /** Application identifier token */
  private final String ait;

  /**
   * Constructs a new VaultConfig with the specified parameters.
   * 
   * @param vaultUrl vault server base URL
   * @param roleId   vault role ID for authentication
   * @param secretId vault secret ID for authentication
   * @param ait      application identifier token
   */
  public VaultConfig(final String vaultUrl, final String roleId, final String secretId, final String ait) {
    this.vaultUrl = vaultUrl;
    this.roleId = roleId;
    this.secretId = secretId;
    this.ait = ait;
  }

  /**
   * Gets the vault server base URL.
   * 
   * @return vault URL
   */
  public String getVaultUrl() {
    return vaultUrl;
  }

  /**
   * Gets the vault role ID.
   * 
   * @return role ID
   */
  public String getRoleId() {
    return roleId;
  }

  /**
   * Gets the vault secret ID.
   * 
   * @return secret ID
   */
  public String getSecretId() {
    return secretId;
  }

  /**
   * Gets the application identifier token.
   * 
   * @return AIT
   */
  public String getAit() {
    return ait;
  }

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
    return value != null && !value.trim().isEmpty();
  }
}
