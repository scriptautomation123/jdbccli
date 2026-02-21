package com.company.app.service.database.typedapi;

/**
 * Utility class for converting underscore_case strings to camelCase.
 *
 * <p>
 * <strong>Optimization Pattern:</strong> This implementation uses single-pass
 * char array
 * manipulation for O(n) performance—no regex, no StringBuilder allocation per
 * character. The
 * conversion is done in-place using a fixed-size char array.
 *
 * <p>
 * <strong>Example conversions:</strong>
 *
 * <ul>
 * <li>{@code user_name} → {@code userName}
 * <li>{@code first_name_last} → {@code firstNameLast}
 * <li>{@code ID} → {@code id}
 * <li>{@code USER_ID} → {@code userId}
 * </ul>
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is stateless and thread-safe.
 */
public final class UnderscoreToCamelCase {

  private UnderscoreToCamelCase() {
    // Utility class
  }

  /**
   * Converts an underscore_case string to camelCase using single-pass char array
   * manipulation.
   *
   * <p>
   * <strong>Performance:</strong> O(n) time, O(n) space where n is the input
   * length. No regex
   * compilation, no StringBuilder reallocations.
   *
   * <p>
   * <strong>Algorithm:</strong>
   *
   * <ol>
   * <li>First character is always lowercase
   * <li>Character after underscore is uppercase (underscore is removed)
   * <li>All other characters are lowercase
   * </ol>
   *
   * <p>
   * This simple approach handles common database column naming conventions like
   * UPPER_CASE,
   * lower_case, and Mixed_Case uniformly.
   *
   * @param input the underscore_case string (e.g., "user_name", "USER_NAME",
   *              "FIRST_NAME")
   * @return the camelCase string (e.g., "userName", "userName", "firstName"), or
   *         empty string if
   *         input is null/empty
   */
  public static String convert(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }

    final int length = input.length();
    final char[] result = new char[length];
    int resultIndex = 0;
    boolean capitalizeNext = false;

    for (int i = 0; i < length; i++) {
      char c = input.charAt(i);

      if (c == '_') {
        // Skip underscore but capitalize next character
        capitalizeNext = true;
      } else if (capitalizeNext) {
        // After an underscore: uppercase this character
        result[resultIndex++] = Character.toUpperCase(c);
        capitalizeNext = false;
      } else if (resultIndex == 0) {
        // First character: always lowercase
        result[resultIndex++] = Character.toLowerCase(c);
      } else {
        // All other characters: lowercase
        result[resultIndex++] = Character.toLowerCase(c);
      }
    }

    return new String(result, 0, resultIndex);
  }

  /**
   * Checks if a string is already in camelCase format.
   *
   * @param input the string to check
   * @return true if the string appears to be camelCase (no underscores, starts
   *         lowercase)
   */
  public static boolean isCamelCase(String input) {
    if (input == null || input.isEmpty()) {
      return false;
    }
    return !input.contains("_") && Character.isLowerCase(input.charAt(0));
  }
}
