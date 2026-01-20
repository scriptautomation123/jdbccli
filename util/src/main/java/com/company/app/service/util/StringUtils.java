package com.company.app.service.util;

/**
 * Utility class for common string operations.
 * Provides null-safe validation and manipulation methods.
 */
public final class StringUtils {

  /**
   * Private constructor to prevent instantiation.
   */
  private StringUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Checks if a string is null or contains only whitespace characters.
   *
   * @param value string to check
   * @return true if null or blank, false otherwise
   */
  public static boolean isNullOrBlank(final String value) {
    return value == null || value.isBlank();
  }

  /**
   * Checks if a string is not null and contains non-whitespace characters.
   *
   * @param value string to check
   * @return true if non-null and non-blank, false otherwise
   */
  public static boolean isNotBlank(final String value) {
    return !isNullOrBlank(value);
  }
}
