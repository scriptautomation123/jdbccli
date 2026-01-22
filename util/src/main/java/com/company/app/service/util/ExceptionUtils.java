package com.company.app.service.util;

import java.io.PrintStream;
import java.io.Serial;
import java.sql.SQLException;

import com.company.app.service.domain.model.ExecutionResult;

import picocli.CommandLine;

/**
 * Utility class for centralized exception handling and error reporting. Provides methods for
 * logging, wrapping, and translating exceptions for both application and CLI contexts.
 */
public final class ExceptionUtils {

  /** Prefix for error messages in CLI output */
  private static final String ERROR_PREFIX = "[ERROR] ";

  /** SQL state prefix for connection errors */
  private static final String CONNECTION_ERROR_PREFIX = "08";

  /** SQL state prefix for syntax errors */
  private static final String SYNTAX_ERROR_PREFIX = "42";

  /** SQL state prefix for constraint violations */
  private static final String CONSTRAINT_VIOLATION_PREFIX = "23";

  /** SQL state prefix for data errors */
  private static final String DATA_ERROR_PREFIX = "22";

  /** SQL state prefix for authentication errors */
  private static final String AUTHENTICATION_ERROR_PREFIX = "28";

  private ExceptionUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Wraps an exception in a ConfigurationException.
   *
   * @param exception exception to wrap
   * @param message error message
   * @return wrapped exception
   */
  public static RuntimeException wrap(final Exception exception, final String message) {
    return new ConfigurationException(message, exception);
  }

  /**
   * Handles execution exceptions with structured logging and appropriate return or rethrow.
   *
   * @param exception exception to handle
   * @param context context identifier for logging
   * @param operation operation identifier for logging
   * @return execution result if handleable, otherwise rethrows
   */
  public static ExecutionResult handleExecutionException(
      final Exception exception, final String context, final String operation) {

    return switch (exception) {
      case IllegalArgumentException ex -> {
        LoggingUtils.logStructuredError(
            context, operation, "FAILED", "Invalid parameters: " + ex.getMessage(), ex);
        yield ExecutionResult.failure(1, "[ERROR] Invalid parameters: " + ex.getMessage());
      }
      default -> {
        LoggingUtils.logStructuredError(
            context, operation, "FAILED", "Unexpected error: " + exception.getMessage(), exception);
        throw wrap(exception, "Failed to execute database operation");
      }
    };
  }

  /**
   * Handles CLI exceptions with appropriate logging and user-friendly output. Uses Java 21 pattern
   * matching for cleaner exception type handling.
   *
   * @param exception exception to handle
   * @param operation operation that failed
   * @param errorStream stream to write error output
   * @return exit code
   */
  public static int handleCliException(
      final Exception exception, final String operation, final PrintStream errorStream) {
    return switch (exception) {
      case SQLException sqlEx -> handleCliSQLException(sqlEx, operation, errorStream);
      case ConfigurationException configEx ->
          handleCliConfigurationException(configEx, operation, errorStream);
      case CliUsageException usageEx -> handleCliUsageException(usageEx, errorStream);
      default -> {
        // Generic exception handling
        LoggingUtils.logStructuredError(
            "cli_exception", operation, "UNEXPECTED_ERROR", exception.getMessage(), exception);
        final String userMessage = translateExceptionForCli(exception);
        errorStream.println(ERROR_PREFIX + userMessage);
        yield 1;
      }
    };
  }

  /**
   * Handles SQL exceptions specifically for CLI with structured logging.
   *
   * @param exception SQL exception to handle
   * @param operation operation that failed
   * @param errorStream stream to write error output
   * @return exit code
   */
  public static int handleCliSQLException(
      final SQLException exception, final String operation, final PrintStream errorStream) {
    final String errorType = getErrorTypeForSQLException(exception);
    final String userMessage = translateSQLExceptionForCli(exception);

    // Log once with structured data
    LoggingUtils.logStructuredError(
        "sql_exception", operation, errorType, exception.getMessage(), exception);

    // Print user-friendly message
    errorStream.println(ERROR_PREFIX + userMessage);

    return 1;
  }

  /**
   * Handles configuration exceptions for CLI.
   *
   * @param exception configuration exception to handle
   * @param operation operation that failed
   * @param errorStream stream to write error output
   * @return exit code
   */
  public static int handleCliConfigurationException(
      final ConfigurationException exception,
      final String operation,
      final PrintStream errorStream) {
    // Log once
    LoggingUtils.logStructuredError(
        "configuration_error", operation, "CONFIG_ERROR", exception.getMessage(), exception);

    // Print user-friendly message
    errorStream.println(ERROR_PREFIX + "Configuration error: " + exception.getMessage());

    return 1;
  }

  /**
   * Handles CLI usage exceptions (user errors).
   *
   * @param exception usage exception to handle
   * @param errorStream stream to write error output
   * @return exit code
   */
  public static int handleCliUsageException(
      final CliUsageException exception, final PrintStream errorStream) {
    // For usage errors, don't log as errors - just print to user
    errorStream.println(ERROR_PREFIX + exception.getMessage());
    return 1;
  }

  /**
   * Translates SQL exceptions to user-friendly messages for CLI output.
   *
   * @param exception SQL exception to translate
   * @return user-friendly message
   */
  private static String translateSQLExceptionForCli(final SQLException exception) {
    final String sqlState = exception.getSQLState();
    if (sqlState == null) {
      return "Database operation failed: " + exception.getMessage();
    }

    final String sqlStatePrefix = sqlState.substring(0, 2);
    return switch (sqlStatePrefix) {
      case CONNECTION_ERROR_PREFIX -> "Cannot connect to database server";
      case AUTHENTICATION_ERROR_PREFIX -> "Invalid username or password";
      case SYNTAX_ERROR_PREFIX -> "Invalid SQL syntax in procedure";
      case CONSTRAINT_VIOLATION_PREFIX -> "Database constraint violation";
      case DATA_ERROR_PREFIX -> "Invalid data provided";
      default -> "Database operation failed: " + exception.getMessage();
    };
  }

  /**
   * Translates generic exceptions to user-friendly messages for CLI output. Uses Java 21 pattern
   * matching for cleaner type checking.
   *
   * @param exception exception to translate
   * @return user-friendly message
   */
  private static String translateExceptionForCli(final Exception exception) {
    return switch (exception) {
      case IllegalArgumentException ex -> "Invalid parameter: " + ex.getMessage();
      case SecurityException ex -> "Access denied: " + ex.getMessage();
      default -> "Operation failed: " + exception.getMessage();
    };
  }

  /**
   * Determines the error type for SQL exceptions based on SQL state.
   *
   * @param exception SQL exception to analyze
   * @return error type string
   */
  private static String getErrorTypeForSQLException(final SQLException exception) {
    final String sqlState = exception.getSQLState();
    if (sqlState == null) {
      return "OP_QUERY";
    }

    final String sqlStatePrefix = sqlState.substring(0, 2);
    return switch (sqlStatePrefix) {
      case CONNECTION_ERROR_PREFIX -> "CONN_FAILED";
      case SYNTAX_ERROR_PREFIX -> "SYNTAX_ERROR";
      case CONSTRAINT_VIOLATION_PREFIX -> "CONSTRAINT_VIOLATION";
      case DATA_ERROR_PREFIX -> "DATA_ERROR";
      case AUTHENTICATION_ERROR_PREFIX -> "AUTH_FAILED";
      default -> "OP_QUERY";
    };
  }

  /** Exception for CLI usage errors (user input problems). */
  public static class CliUsageException extends RuntimeException {

    /** Serial version UID for serialization */
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Constructs a new CliUsageException with the specified message.
     *
     * @param message error message
     */
    public CliUsageException(final String message) {
      super(message);
    }
  }

  /** Exception for configuration-related errors. */
  public static class ConfigurationException extends RuntimeException {

    /** Serial version UID for serialization */
    @Serial private static final long serialVersionUID = 1L;

    /**
     * Constructs a new ConfigurationException with the specified message.
     *
     * @param message error message
     */
    public ConfigurationException(final String message) {
      super(message);
    }

    /**
     * Constructs a new ConfigurationException with the specified message and cause.
     *
     * @param message error message
     * @param cause underlying cause
     */
    public ConfigurationException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  /** Handler for execution exceptions in PicoCLI commands. */
  public static class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(
        final Exception exception,
        final CommandLine cmd,
        final CommandLine.ParseResult parseResult) {
      LoggingUtils.logMinimalError(exception);
      return CommandLine.ExitCode.SOFTWARE;
    }
  }

  /** Handler for parameter exceptions in PicoCLI commands. */
  public static class ParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {

    @Override
    public int handleParseException(
        final CommandLine.ParameterException exception, final String[] args) {
      final CommandLine cmd = exception.getCommandLine();
      cmd.getErr().println(exception.getMessage());
      if (!CommandLine.UnmatchedArgumentException.printSuggestions(exception, cmd.getErr())) {
        exception.getCommandLine().usage(cmd.getErr());
      }
      return CommandLine.ExitCode.USAGE;
    }
  }
}
