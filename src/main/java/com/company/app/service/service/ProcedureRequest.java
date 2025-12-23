package com.company.app.service.service;

/**
 * Immutable request object for stored procedure execution operations.
 * Extends DatabaseRequest to include procedure-specific parameters like
 * procedure name, input parameters, and output parameters.
 */
public final class ProcedureRequest extends DatabaseRequest {

  /** Stored procedure name to execute */
  private final String procedure;

  /** Input parameters string in format name:type:value,name:type:value */
  private final String input;

  /** Output parameters string in format name:type,name:type */
  private final String output;

  /**
   * Constructs a new ProcedureRequest from builder parameters.
   * 
   * @param builder builder containing procedure request parameters
   */
  private ProcedureRequest(final Builder builder) {
    super(builder);
    this.procedure = builder.procedureField;
    this.input = builder.inputField;
    this.output = builder.outputField;
  }

  /**
   * Gets the stored procedure name.
   * 
   * @return procedure name
   */
  public String getProcedure() {
    return procedure;
  }

  /**
   * Gets the input parameters string.
   * 
   * @return input parameters
   */
  public String getInput() {
    return input;
  }

  /**
   * Gets the output parameters string.
   * 
   * @return output parameters
   */
  public String getOutput() {
    return output;
  }

  /**
   * Checks if this request is in password-only mode (no procedure specified).
   * 
   * @return true if password-only mode
   */
  public boolean isPasswordOnlyMode() {
    return isNullOrBlank(procedure);
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
   * Builder for ProcedureRequest objects supporting fluent API.
   */
  public static class Builder extends DatabaseRequest.Builder<Builder> {

    /** Stored procedure name field */
    private String procedureField;

    /** Input parameters field */
    private String inputField;

    /** Output parameters field */
    private String outputField;

    /**
     * Sets the stored procedure name.
     * 
     * @param procedure procedure name to set
     * @return this builder for method chaining
     */
    public Builder procedure(final String procedure) {
      this.procedureField = procedure;
      return this;
    }

    /**
     * Sets the input parameters string.
     * 
     * @param input input parameters to set
     * @return this builder for method chaining
     */
    public Builder input(final String input) {
      this.inputField = input;
      return this;
    }

    /**
     * Sets the output parameters string.
     * 
     * @param output output parameters to set
     * @return this builder for method chaining
     */
    public Builder output(final String output) {
      this.outputField = output;
      return this;
    }

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public ProcedureRequest build() {
      return new ProcedureRequest(this);
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
