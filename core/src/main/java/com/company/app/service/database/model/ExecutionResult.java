package com.company.app.service.service.model;

import java.io.PrintStream;
import java.util.Map;

/**
 * Immutable result object for database execution operations.
 * Contains exit code, result data, and optional message for CLI output
 * formatting.
 */
public final class ExecutionResult {

  /** Exit code for the operation (0 for success, non-zero for failure) */
  private final int exitCode;

  /** Result data map containing execution results */
  private final Map<String, Object> data;

  /** Optional message for user display */
  private final String message;

  /** Success exit code constant */
  private static final int SUCCESS_EXIT_CODE = 0;

  /** Single item threshold for simplified output */
  private static final int SINGLE_ITEM_THRESHOLD = 1;

  /**
   * Constructs a new ExecutionResult with the specified parameters.
   * 
   * @param exitCode operation exit code
   * @param data     result data map
   * @param message  optional message
   */
  private ExecutionResult(final int exitCode, final Map<String, Object> data, final String message) {
    this.exitCode = exitCode;
    this.data = data;
    this.message = message;
  }

  /**
   * Gets the exit code.
   * 
   * @return exit code
   */
  public int getExitCode() {
    return exitCode;
  }

  /**
   * Gets the result data.
   * 
   * @return result data map
   */
  public Map<String, Object> getData() {
    return data;
  }

  /**
   * Gets the message.
   * 
   * @return optional message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Formats and outputs the result to the specified stream.
   * 
   * @param outputStream stream to write output to
   */
  public void formatOutput(final PrintStream outputStream) {
    if (message != null) {
      outputStream.println(message);
    }
    if (data != null && !data.isEmpty()) {
      if (data.size() == SINGLE_ITEM_THRESHOLD) {
        final Object value = data.values().iterator().next();
        outputStream.println(value != null ? value.toString() : "null");
      } else {
        data.forEach((key, value) -> outputStream.println(key + ": " + value));
      }
    }
  }

  /**
   * Creates a successful execution result with data.
   * 
   * @param data result data map
   * @return successful execution result
   */
  public static ExecutionResult success(final Map<String, Object> data) {
    return new ExecutionResult(SUCCESS_EXIT_CODE, data, null);
  }

  /**
   * Creates a successful execution result with message.
   * 
   * @param message success message
   * @return successful execution result
   */
  public static ExecutionResult success(final String message) {
    return new ExecutionResult(SUCCESS_EXIT_CODE, null, message);
  }

  /**
   * Creates a failed execution result.
   * 
   * @param exitCode failure exit code
   * @param message  failure message
   * @return failed execution result
   */
  public static ExecutionResult failure(final int exitCode, final String message) {
    return new ExecutionResult(exitCode, null, message);
  }

  /**
   * Creates a password-only mode result for vault operations.
   * 
   * @return password-only mode result
   */
  public static ExecutionResult passwordOnlyMode() {
    return new ExecutionResult(SUCCESS_EXIT_CODE, null, "=== VAULT PASSWORD DECRYPTION ===\nSuccess: true");
  }
}
