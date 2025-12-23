package com.company.app.service.service;

import java.io.IOException;

import com.company.app.service.util.LoggingUtils;
import com.company.app.service.util.VaultClient;

/**
 * Service for vault password retrieval operations.
 * Provides vault-specific execution logic without database connections.
 */
public final class VaultClientService {

        /** Vault password retrieval operation identifier */
        private static final String VAULT_PASSWORD_RETRIEVAL = "vault_password_retrieval";

        /** Vault lookup operation identifier */
        private static final String VAULT_LOOKUP = "vault_lookup";

        /** Success status for operations */
        private static final String SUCCESS = "SUCCESS";

        /** Failed status for operations */
        private static final String FAILED = "FAILED";

        /** Started status for operations */
        private static final String STARTED = "STARTED";

        /** No configuration found status */
        private static final String NO_CONFIG = "NO_CONFIG";

        /** Execute lookup operation identifier */
        private static final String EXECUTE_LOOKUP = "execute_lookup";

        /** Execute direct operation identifier */
        private static final String EXECUTE_DIRECT = "execute_direct";

        /** Database label for messages */
        private static final String DATABASE_LABEL = ", database: ";

        /**
         * Constructs a new VaultClientService.
         * 
         */
        public VaultClientService() {
                // No parameters needed for vault operations
        }

        /**
         * Executes the vault client request.
         * 
         * @param request vault client request
         * @return execution result
         */
        public ExecutionResult execute(final VaultClientRequest request) {
                if (request.isLookupMode()) {
                        return executeLookupMode(request);
                } else {
                        return executeDirectMode(request);
                }
        }

        /**
         * Executes vault password retrieval in lookup mode.
         * 
         * @param request vault client request
         * @return execution result
         */
        private ExecutionResult executeLookupMode(final VaultClientRequest request) {
                LoggingUtils.logStructuredError(
                                VAULT_LOOKUP,
                                EXECUTE_LOOKUP,
                                STARTED,
                                "Retrieving password for user: " + request.getUser() + DATABASE_LABEL
                                                + request.getDatabase(),
                                null);

                try (VaultClient vaultClient = new VaultClient()) {
                        final String password = vaultClient.fetchOraclePassword(request.getUser(),
                                        request.getDatabase());

                        if (password != null && !password.trim().isEmpty()) {
                                LoggingUtils.logStructuredError(
                                                VAULT_LOOKUP,
                                                EXECUTE_LOOKUP,
                                                SUCCESS,
                                                "Password retrieved successfully for user: " + request.getUser(),
                                                null);
                                return ExecutionResult.success(password);
                        } else {
                                LoggingUtils.logStructuredError(
                                                VAULT_LOOKUP,
                                                EXECUTE_LOOKUP,
                                                NO_CONFIG,
                                                "No vault configuration found for user: " + request.getUser()
                                                                + DATABASE_LABEL
                                                                + request.getDatabase(),
                                                null);
                                return ExecutionResult.failure(1,
                                                "[ERROR] No vault configuration found for user: " + request.getUser()
                                                                + DATABASE_LABEL + request.getDatabase());
                        }

                } catch (IOException exception) {
                        LoggingUtils.logStructuredError(
                                        VAULT_LOOKUP,
                                        EXECUTE_LOOKUP,
                                        FAILED,
                                        "Failed to retrieve password: " + exception.getMessage(),
                                        exception);
                        return ExecutionResult.failure(1,
                                        "[ERROR] Failed to retrieve password: " + exception.getMessage());
                } catch (Exception exception) {
                        LoggingUtils.logStructuredError(
                                        VAULT_LOOKUP,
                                        EXECUTE_LOOKUP,
                                        FAILED,
                                        "Unexpected error during password retrieval: " + exception.getMessage(),
                                        exception);
                        return ExecutionResult.failure(1,
                                        "[ERROR] Unexpected error during password retrieval: "
                                                        + exception.getMessage());
                }
        }

        /**
         * Executes vault password retrieval in direct mode using vault configuration.
         * 
         * @param request vault client request
         * @return execution result
         */
        private ExecutionResult executeDirectMode(final VaultClientRequest request) {
                LoggingUtils.logStructuredError(
                                VAULT_PASSWORD_RETRIEVAL,
                                EXECUTE_DIRECT,
                                STARTED,
                                "Retrieving password using direct vault configuration for user: " + request.getUser(),
                                null);

                final VaultConfig vaultConfig = request.getVaultConfig();
                if (!vaultConfig.hasDirectVaultParams()) {
                        LoggingUtils.logStructuredError(
                                        VAULT_PASSWORD_RETRIEVAL,
                                        EXECUTE_DIRECT,
                                        FAILED,
                                        "Direct vault parameters not provided",
                                        null);
                        return ExecutionResult.failure(1,
                                        "[ERROR] Direct vault parameters not provided. Use --vault-url, --role-id, --secret-id, and --ait options.");
                }

                try (VaultClient vaultClient = new VaultClient()) {
                        final String password = vaultClient.fetchOraclePassword(
                                        vaultConfig.getVaultUrl(),
                                        vaultConfig.getRoleId(),
                                        vaultConfig.getSecretId(),
                                        request.getDatabase(),
                                        vaultConfig.getAit(),
                                        request.getUser());

                        if (password != null && !password.trim().isEmpty()) {
                                LoggingUtils.logStructuredError(
                                                VAULT_PASSWORD_RETRIEVAL,
                                                EXECUTE_DIRECT,
                                                SUCCESS,
                                                "Password retrieved successfully using direct vault configuration for user: "
                                                                + request.getUser(),
                                                null);
                                return ExecutionResult.success(password);
                        } else {
                                LoggingUtils.logStructuredError(
                                                VAULT_PASSWORD_RETRIEVAL,
                                                EXECUTE_DIRECT,
                                                FAILED,
                                                "Failed to retrieve password using direct vault configuration for user: "
                                                                + request.getUser(),
                                                null);
                                return ExecutionResult.failure(1,
                                                "[ERROR] Failed to retrieve password using direct vault configuration for user: "
                                                                + request.getUser());
                        }

                } catch (IOException exception) {
                        LoggingUtils.logStructuredError(
                                        VAULT_PASSWORD_RETRIEVAL,
                                        EXECUTE_DIRECT,
                                        FAILED,
                                        "Failed to retrieve password: " + exception.getMessage(),
                                        exception);
                        return ExecutionResult.failure(1,
                                        "[ERROR] Failed to retrieve password: " + exception.getMessage());
                } catch (Exception exception) {
                        LoggingUtils.logStructuredError(
                                        VAULT_PASSWORD_RETRIEVAL,
                                        EXECUTE_DIRECT,
                                        FAILED,
                                        "Unexpected error during password retrieval: " + exception.getMessage(),
                                        exception);
                        return ExecutionResult.failure(1,
                                        "[ERROR] Unexpected error during password retrieval: "
                                                        + exception.getMessage());
                }
        }
}