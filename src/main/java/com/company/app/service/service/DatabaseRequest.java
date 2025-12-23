package com.company.app.service.service;

import java.util.Objects;

/**
 * Immutable request object for database operations containing connection
 * parameters
 * and vault configuration. Uses builder pattern for construction.
 */
public class DatabaseRequest {

  /** Database type (e.g., "oracle", "postgresql") */
  private final String type;

  /** Database name for connection */
  private final String database;

  /** Database username for authentication */
  private final String user;

  /** Vault configuration for password resolution */
  private final VaultConfig vaultConfig;

  /**
   * Constructs a new DatabaseRequest from builder parameters.
   * 
   * @param builder builder containing request parameters
   */
  protected DatabaseRequest(final Builder<?> builder) {
    this.type = Objects.requireNonNull(builder.type, "Database type cannot be null");
    this.database = Objects.requireNonNull(builder.database, "Database name cannot be null");
    this.user = Objects.requireNonNull(builder.user, "Database user cannot be null");
    this.vaultConfig = builder.vaultConfig != null ? builder.vaultConfig : VaultConfig.empty();
  }

  /**
   * Gets the database type.
   * 
   * @return database type
   */
  public String getType() {
    return type;
  }

  /**
   * Gets the database name.
   * 
   * @return database name
   */
  public String getDatabase() {
    return database;
  }

  /**
   * Gets the database username.
   * 
   * @return database username
   */
  public String getUser() {
    return user;
  }

  /**
   * Gets the vault configuration.
   * 
   * @return vault configuration
   */
  public VaultConfig getVaultConfig() {
    return vaultConfig;
  }

  /**
   * Generic builder for DatabaseRequest objects supporting fluent API.
   * 
   * @param <T> builder type for method chaining
   */
  public static class Builder<T extends Builder<T>> {

    /** Database type field */
    protected String type;

    /** Database name field */
    protected String database;

    /** Database username field */
    protected String user;

    /** Vault configuration field */
    protected VaultConfig vaultConfig;

    /**
     * Sets the database type.
     * 
     * @param type database type to set
     * @return this builder for method chaining
     */
    public T type(final String type) {
      this.type = type;
      return self();
    }

    /**
     * Sets the database name.
     * 
     * @param database database name to set
     * @return this builder for method chaining
     */
    public T database(final String database) {
      this.database = database;
      return self();
    }

    /**
     * Sets the database username.
     * 
     * @param user database username to set
     * @return this builder for method chaining
     */
    public T user(final String user) {
      this.user = user;
      return self();
    }

    /**
     * Sets the vault configuration.
     * 
     * @param vaultConfig vault configuration to set
     * @return this builder for method chaining
     */
    public T vaultConfig(final VaultConfig vaultConfig) {
      this.vaultConfig = vaultConfig;
      return self();
    }

    /**
     * Returns the typed builder instance for method chaining.
     * 
     * @return typed builder instance
     */
    @SuppressWarnings("unchecked")
    protected T self() {
      return (T) this;
    }

    /**
     * Builds a new DatabaseRequest instance.
     * 
     * @return new DatabaseRequest instance
     */
    public DatabaseRequest build() {
      return new DatabaseRequest(this);
    }
  }
}
