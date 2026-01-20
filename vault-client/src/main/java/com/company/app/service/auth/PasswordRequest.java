package com.company.app.service.auth;

import java.util.Objects;

import com.company.app.service.util.LoggingUtils;

/**
 * Immutable request object for password resolution operations.
 * Supports both vault lookup mode (user/database only) and direct vault
 * parameter mode.
 * Validates that vault parameters are either all provided or none provided.
 *
 * @param user     database username for authentication
 * @param database database name for connection
 * @param vaultUrl vault server URL for authentication
 * @param roleId   vault role ID for authentication
 * @param secretId vault secret ID for authentication
 * @param ait      application identifier token for vault
 */
public record PasswordRequest(
    String user,
    String database,
    String vaultUrl,
    String roleId,
    String secretId,
    String ait) {

  private static final String ERROR_CONTEXT = "password_request";
  private static final String NO_VAULT_PARAMS_ERROR = "NO_VAULT_PARAMS";
  private static final String NO_VAULT_PARAMS_MESSAGE = "Vault parameters not available - direct vault params not provided";

  /**
   * Compact constructor with validation.
   */
  public PasswordRequest {
    user = Objects.requireNonNull(user, "User cannot be null");
    database = Objects.requireNonNull(database, "Database cannot be null");

    validateVaultParameters(vaultUrl, roleId, secretId, ait);
  }

  private static void validateVaultParameters(
      final String vaultUrl,
      final String roleId,
      final String secretId,
      final String ait) {
    final boolean hasSomeVaultParams = hasAnyVaultParams(vaultUrl, roleId, secretId, ait);
    final boolean hasAllVaultParams = hasAllDirectVaultParams(vaultUrl, roleId, secretId, ait);

    if (hasSomeVaultParams && !hasAllVaultParams) {
      LoggingUtils.logStructuredError(
          ERROR_CONTEXT,
          "validation",
          "INVALID_VAULT_PARAMS",
          "Invalid vault parameter combination. Either provide all vault parameters or none.",
          null);
      throw new IllegalArgumentException(
          "Invalid vault parameter combination. Either provide all vault parameters or none.");
    }
  }

  private static boolean hasAnyVaultParams(
      final String vaultUrl,
      final String roleId,
      final String secretId,
      final String ait) {
    return vaultUrl != null || roleId != null || secretId != null || ait != null;
  }

  /**
   * Checks if all direct vault parameters are provided and non-empty.
   *
   * @return true if all vault parameters are present and non-empty
   */
  public boolean hasDirectVaultParams() {
    return hasAllDirectVaultParams(vaultUrl, roleId, secretId, ait);
  }

  private static boolean hasAllDirectVaultParams(
      final String vaultUrl,
      final String roleId,
      final String secretId,
      final String ait) {
    return isNonEmpty(vaultUrl) && isNonEmpty(roleId) && isNonEmpty(secretId) && isNonEmpty(ait);
  }

  private static boolean isNonEmpty(final String value) {
    return value != null && !value.isBlank();
  }

  /**
   * Gets the database username.
   *
   * @return the username
   */
  public String getUser() {
    return user;
  }

  /**
   * Gets the database name.
   *
   * @return the database name
   */
  public String getDatabase() {
    return database;
  }

  /**
   * Gets the vault URL if direct vault parameters are provided.
   *
   * @return the vault URL
   * @throws IllegalStateException if direct vault parameters are not provided
   */
  public String getVaultUrl() {
    validateDirectVaultParams("get_vault_url");
    return vaultUrl;
  }

  /**
   * Gets the vault role ID if direct vault parameters are provided.
   *
   * @return the role ID
   * @throws IllegalStateException if direct vault parameters are not provided
   */
  public String getRoleId() {
    validateDirectVaultParams("get_role_id");
    return roleId;
  }

  /**
   * Gets the vault secret ID if direct vault parameters are provided.
   *
   * @return the secret ID
   * @throws IllegalStateException if direct vault parameters are not provided
   */
  public String getSecretId() {
    validateDirectVaultParams("get_secret_id");
    return secretId;
  }

  /**
   * Gets the application identifier token if direct vault parameters are
   * provided.
   *
   * @return the AIT
   * @throws IllegalStateException if direct vault parameters are not provided
   */
  public String getAit() {
    validateDirectVaultParams("get_ait");
    return ait;
  }

  private void validateDirectVaultParams(final String operation) {
    if (!hasDirectVaultParams()) {
      LoggingUtils.logStructuredError(
          ERROR_CONTEXT,
          operation,
          NO_VAULT_PARAMS_ERROR,
          NO_VAULT_PARAMS_MESSAGE,
          null);
      throw new IllegalStateException(NO_VAULT_PARAMS_MESSAGE);
    }
  }
}
