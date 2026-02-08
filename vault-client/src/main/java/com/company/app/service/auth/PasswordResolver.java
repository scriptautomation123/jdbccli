package com.company.app.service.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;
import com.company.app.service.util.VaultClient;

/**
 * Resolves database passwords using multiple strategies: direct vault parameters, vault lookup, or
 * console prompting. Provides a unified interface for password resolution across different
 * authentication methods.
 */
public class PasswordResolver {

  /** Context for direct vault parameter resolution */
  private static final String DIRECT_VAULT = "direct_vault";

  /** Context for password resolution operations */
  private static final String PWD_RESOLUTION = "password_resolution";

  /** Error status for failed operations */
  private static final String FAILED = "FAILED";

  /** Supplier for console password prompting */
  private final Supplier<String> passwordPrompter;

  /** When true, bypass vault lookup and use the supplied password directly. */
  private final boolean preferDirectPassword;

  /**
   * Constructs a PasswordResolver with a password prompter function.
   *
   * @param passwordPrompter function to prompt for password when other methods fail
   */
  public PasswordResolver(final Supplier<String> passwordPrompter) {
    this(passwordPrompter, false);
  }

  /**
   * Constructs a PasswordResolver with an option to skip vault lookups.
   *
   * @param passwordPrompter function to supply a password
   * @param preferDirectPassword when true, return the supplied password without vault lookups
   */
  public PasswordResolver(
      final Supplier<String> passwordPrompter, final boolean preferDirectPassword) {
    this.passwordPrompter =
        Objects.requireNonNull(passwordPrompter, "Password prompter cannot be null");
    this.preferDirectPassword = preferDirectPassword;
  }

  /**
   * Resolves a password using the most appropriate method based on the request. Tries direct vault
   * parameters first, then vault lookup, then console prompt.
   *
   * @param request the password request containing user and database information
   * @return Optional containing the resolved password, or empty if resolution failed
   * @throws RuntimeException if password resolution fails and cannot fall back to console prompt
   */
  public Optional<String> resolvePassword(final PasswordRequest request) {
    Objects.requireNonNull(request, "PasswordRequest cannot be null");

    try {
      return attemptPasswordResolution(request);
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          PWD_RESOLUTION,
          "resolve",
          FAILED,
          "Failed to resolve password for user: " + request.getUser(),
          e);
      throw ExceptionUtils.wrap(e, "Failed to resolve password for user: " + request.getUser());
    }
  }

  private Optional<String> attemptPasswordResolution(final PasswordRequest request) {
    if (preferDirectPassword) {
      return Optional.ofNullable(passwordPrompter.get());
    }
    // Try direct vault parameters first
    if (request.hasDirectVaultParams()) {
      LoggingUtils.logPasswordResolution(request.getUser(), DIRECT_VAULT);
      try {
        return resolveWithDirectVaultParams(request);
      } catch (RuntimeException ex) {
        final Optional<String> fallback = Optional.ofNullable(passwordPrompter.get());
        if (fallback.isPresent()) {
          return fallback;
        }
        throw ex;
      }
    }

    // Try vault lookup
    final Optional<String> vaultPassword = resolveWithVaultLookup(request);
    if (vaultPassword.isPresent()) {
      LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
      return vaultPassword;
    }

    // Fall back to console prompt
    LoggingUtils.logPasswordResolution(request.getUser(), DIRECT_VAULT);
    return Optional.ofNullable(passwordPrompter.get());
  }

  private Optional<String> resolveWithDirectVaultParams(final PasswordRequest request) {
    try (VaultClient client = new VaultClient()) {
      final String password = fetchPasswordFromVault(client, request);
      final Optional<String> result = validatePassword(password);

      if (result.isPresent()) {
        LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
      } else {
        LoggingUtils.logStructuredError(
            PWD_RESOLUTION,
            DIRECT_VAULT,
            FAILED,
            "No password returned from direct vault parameters",
            null);
      }

      return result;
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          PWD_RESOLUTION,
          DIRECT_VAULT,
          FAILED,
          "Failed to fetch password from vault with direct params for user: " + request.getUser(),
          e);
      throw ExceptionUtils.wrap(e, "Failed to fetch password from vault");
    }
  }

  private String fetchPasswordFromVault(final VaultClient client, final PasswordRequest request) {
    return client.fetchOraclePassword(
        request.getVaultUrl(),
        request.getRoleId(),
        request.getSecretId(),
        request.getDatabase(),
        request.getAit(),
        request.getUser());
  }

  private Optional<String> resolveWithVaultLookup(final PasswordRequest request) {
    try (VaultClient client = new VaultClient()) {
      final String password = client.fetchOraclePassword(request.getUser(), request.getDatabase());
      final Optional<String> result = validatePassword(password);

      if (result.isPresent()) {
        LoggingUtils.logPasswordResolutionSuccess(request.getUser(), DIRECT_VAULT);
      } else {
        LoggingUtils.logStructuredError(
            PWD_RESOLUTION,
            DIRECT_VAULT,
            "NO_CONFIG",
            "No vault config found for user: " + request.getUser(),
            null);
      }

      return result;
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          PWD_RESOLUTION,
          DIRECT_VAULT,
          FAILED,
          "Failed to fetch password from vault lookup for user: " + request.getUser(),
          e);
      // Don't throw here - fall back to console prompt
      return Optional.empty();
    }
  }

  private static Optional<String> validatePassword(final String password) {
    return Optional.ofNullable(password).filter(pwd -> !pwd.isBlank());
  }
}
