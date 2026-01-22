package com.company.app.service.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Centralized logging utility providing structured logging capabilities for database operations,
 * vault authentication, and error handling. Implements consistent logging patterns across the
 * application.
 */
public final class LoggingUtils {

  private static final Logger LOGGER = LogManager.getLogger(LoggingUtils.class);

  private LoggingUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Logs database connection details.
   *
   * @param type database type
   * @param database database name
   * @param user database user
   */
  public static void logDatabaseConnection(
      final String type, final String database, final String user) {
    LOGGER.info("event=database_connection type={} database={} user={}", type, database, user);
  }

  /**
   * Logs procedure execution details.
   *
   * @param procedure procedure name
   * @param input input parameters
   * @param output output parameters
   */
  public static void logProcedureExecution(
      final String procedure, final String input, final String output) {
    LOGGER.info(
        "event=procedure_execution procedure={} input={} output={}", procedure, input, output);
  }

  /**
   * Logs successful procedure execution.
   *
   * @param procedure procedure name
   */
  public static void logProcedureExecutionSuccess(final String procedure) {
    LOGGER.info("event=procedure_execution procedure={} status=SUCCESS", procedure);
  }

  /**
   * Logs password resolution attempt.
   *
   * @param user username
   * @param method resolution method
   */
  public static void logPasswordResolution(final String user, final String method) {
    LOGGER.info("event=password_resolution user={} method={}", user, method);
  }

  /**
   * Logs successful password resolution.
   *
   * @param user username
   * @param method resolution method
   */
  public static void logPasswordResolutionSuccess(final String user, final String method) {
    LOGGER.info("event=password_resolution user={} method={} status=SUCCESS", user, method);
  }

  /**
   * Logs vault operation details.
   *
   * @param operation operation name
   * @param user username
   * @param status operation status
   */
  public static void logVaultOperation(
      final String operation, final String user, final String status) {
    LOGGER.info("event=vault_operation operation={} user={} status={}", operation, user, status);
  }

  /**
   * Logs vault authentication details.
   *
   * @param vaultUrl vault URL
   * @param status authentication status
   */
  public static void logVaultAuthentication(final String vaultUrl, final String status) {
    LOGGER.info("event=vault_authentication vaultUrl={} status={}", vaultUrl, status);
  }

  /**
   * Logs CLI startup information.
   *
   * @param javaHome Java home directory
   */
  public static void logCliStartup(final String javaHome) {
    LOGGER.debug("event=cli_startup javaHome={}", javaHome);
  }

  /**
   * Logs SQL execution details.
   *
   * @param sql SQL statement
   * @param paramCount parameter count
   */
  public static void logSqlExecution(final String sql, final int paramCount) {
    LOGGER.info("event=sql_execution sql={} paramCount={}", sql, paramCount);
  }

  /**
   * Logs SQL script execution.
   *
   * @param scriptName script name
   */
  public static void logSqlScriptExecution(final String scriptName) {
    LOGGER.info("event=sql_script_execution script={}", scriptName);
  }

  /**
   * Logs configuration loading.
   *
   * @param configPath configuration file path
   */
  public static void logConfigLoading(final String configPath) {
    LOGGER.info("event=config_loading path={}", configPath);
  }

  /**
   * Logs minimal error information to both logger and console.
   *
   * @param exception exception to log
   */
  public static void logMinimalError(final Throwable exception) {
    final Throwable cause = getRootCause(exception);
    final StackTraceElement[] stack = cause.getStackTrace();
    final String location = extractLocation(stack);
    final String message = formatErrorMessage(cause, location);

    LOGGER.error(message);
    System.err.println(message); // NOSONAR Direct to console, regardless of logger config
  }

  /**
   * Logs structured error information.
   *
   * @param event event type
   * @param operation operation name
   * @param errorType error type
   * @param message error message
   * @param throwable exception
   */
  public static void logStructuredError(
      final String event,
      final String operation,
      final String errorType,
      final String message,
      final Throwable throwable) {
    LOGGER.error(
        "event={} operation={} errorType={} message={} exception={}",
        event,
        operation,
        errorType,
        message,
        throwable);
  }

  /**
   * Gets the root cause of a throwable.
   *
   * @param throwable throwable to analyze
   * @return root cause
   */
  private static Throwable getRootCause(final Throwable throwable) {
    Throwable cause = throwable;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause;
  }

  /**
   * Extracts location information from stack trace.
   *
   * @param stack stack trace elements
   * @return formatted location string
   */
  private static String extractLocation(final StackTraceElement[] stack) {
    if (stack != null && stack.length > 0) {
      final StackTraceElement firstElement = stack[0];
      return String.format(
          " (at %s:%d)", firstElement.getClassName(), firstElement.getLineNumber());
    }
    return "";
  }

  /**
   * Formats error message with cause and location.
   *
   * @param cause root cause exception
   * @param location location information
   * @return formatted error message
   */
  private static String formatErrorMessage(final Throwable cause, final String location) {
    return String.format(
        "[ERROR] Caused by: %s: %s%s",
        cause.getClass().getSimpleName(), cause.getMessage(), location);
  }
}
