package com.company.app.service.service;

/**
 * Immutable request object for vault client operations.
 * Extends DatabaseRequest to include vault-specific parameters like
 * operation mode and vault ID.
 */
public final class VaultClientRequest extends DatabaseRequest {

    /** Vault operation mode */
    private final String mode;

    /** Vault ID for lookup operations */
    private final String vaultId;

    /**
     * Constructs a new VaultClientRequest from builder parameters.
     * 
     * @param builder builder containing vault client request parameters
     */
    private VaultClientRequest(final Builder builder) {
        super(builder);
        this.mode = builder.modeField;
        this.vaultId = builder.vaultIdField;
    }

    /**
     * Gets the vault operation mode.
     * 
     * @return operation mode
     */
    public String getMode() {
        return mode;
    }

    /**
     * Gets the vault ID.
     * 
     * @return vault ID
     */
    public String getVaultId() {
        return vaultId;
    }

    /**
     * Checks if this request is in lookup mode.
     * 
     * @return true if lookup mode
     */
    public boolean isLookupMode() {
        return "lookup".equals(mode);
    }

    /**
     * Checks if this request is in direct mode (using vault config).
     * 
     * @return true if direct mode
     */
    public boolean isDirectMode() {
        return !isLookupMode();
    }

    /**
     * Checks if this request is in password-only mode (no mode specified).
     * 
     * @return true if password-only mode
     */
    public boolean isPasswordOnlyMode() {
        return isNullOrBlank(mode);
    }

    /**
     * Creates a new builder instance.
     * 
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for VaultClientRequest objects supporting fluent API.
     */
    public static class Builder extends DatabaseRequest.Builder<Builder> {

        /** Vault operation mode field */
        private String modeField;

        /** Vault ID field */
        private String vaultIdField;

        /**
         * Sets the vault operation mode.
         * 
         * @param mode operation mode to set
         * @return this builder for method chaining
         */
        public Builder mode(final String mode) {
            this.modeField = mode;
            return this;
        }

        /**
         * Sets the vault ID.
         * 
         * @param vaultId vault ID to set
         * @return this builder for method chaining
         */
        public Builder vaultId(final String vaultId) {
            this.vaultIdField = vaultId;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public VaultClientRequest build() {
            return new VaultClientRequest(this);
        }
    }

    /**
     * Checks if a string is null or contains only whitespace.
     * 
     * @param value string to check
     * @return true if null or blank
     */
    private static boolean isNullOrBlank(final String value) {
        return value == null || value.trim().isEmpty();
    }
}