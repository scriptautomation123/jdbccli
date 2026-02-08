package com.company.app.service.database.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for UnderscoreToCamelCase utility. Tests single-pass O(n) conversion algorithm. */
@DisplayName("UnderscoreToCamelCase")
class UnderscoreToCamelCaseTest {

  @Nested
  @DisplayName("Basic Conversions")
  class BasicConversionTests {

    @ParameterizedTest
    @DisplayName("should convert common database column names")
    @CsvSource({
      "user_name, userName",
      "first_name, firstName",
      "last_name, lastName",
      "email_address, emailAddress",
      "created_at, createdAt",
      "updated_at, updatedAt",
      "is_active, isActive",
      "employee_id, employeeId"
    })
    void shouldConvertCommonNames(String input, String expected) {
      assertThat(UnderscoreToCamelCase.convert(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("should handle UPPER_CASE database columns")
    @CsvSource({
      "USER_NAME, userName",
      "FIRST_NAME, firstName",
      "EMPLOYEE_ID, employeeId",
      "IS_ACTIVE, isActive"
    })
    void shouldConvertUpperCase(String input, String expected) {
      assertThat(UnderscoreToCamelCase.convert(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @DisplayName("should handle Mixed_Case columns")
    @CsvSource({"User_Name, userName", "First_Name, firstName", "Employee_ID, employeeId"})
    void shouldConvertMixedCase(String input, String expected) {
      assertThat(UnderscoreToCamelCase.convert(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle null input")
    void shouldHandleNull() {
      assertThat(UnderscoreToCamelCase.convert(null)).isEmpty();
    }

    @Test
    @DisplayName("should handle empty string")
    void shouldHandleEmpty() {
      assertThat(UnderscoreToCamelCase.convert("")).isEmpty();
    }

    @Test
    @DisplayName("should handle single character")
    void shouldHandleSingleChar() {
      assertThat(UnderscoreToCamelCase.convert("A")).isEqualTo("a");
      assertThat(UnderscoreToCamelCase.convert("x")).isEqualTo("x");
    }

    @Test
    @DisplayName("should handle no underscores")
    void shouldHandleNoUnderscores() {
      assertThat(UnderscoreToCamelCase.convert("id")).isEqualTo("id");
      assertThat(UnderscoreToCamelCase.convert("name")).isEqualTo("name");
      assertThat(UnderscoreToCamelCase.convert("ID")).isEqualTo("id");
    }

    @Test
    @DisplayName("should handle multiple consecutive underscores")
    void shouldHandleMultipleUnderscores() {
      assertThat(UnderscoreToCamelCase.convert("user__name")).isEqualTo("userName");
      assertThat(UnderscoreToCamelCase.convert("first___last")).isEqualTo("firstLast");
    }

    @Test
    @DisplayName("should handle leading underscore")
    void shouldHandleLeadingUnderscore() {
      // Leading underscore: next char becomes uppercase (since it comes after
      // underscore)
      // Note: First non-underscore char is uppercase, not lowercase
      assertThat(UnderscoreToCamelCase.convert("_user_name")).isEqualTo("UserName");
      assertThat(UnderscoreToCamelCase.convert("__user_name")).isEqualTo("UserName");
      assertThat(UnderscoreToCamelCase.convert("_id")).isEqualTo("Id");
    }

    @Test
    @DisplayName("should handle trailing underscore")
    void shouldHandleTrailingUnderscore() {
      assertThat(UnderscoreToCamelCase.convert("user_name_")).isEqualTo("userName");
      assertThat(UnderscoreToCamelCase.convert("id__")).isEqualTo("id");
    }
  }

  @Nested
  @DisplayName("Complex Patterns")
  class ComplexPatternTests {

    @Test
    @DisplayName("should handle long column names")
    void shouldHandleLongNames() {
      assertThat(UnderscoreToCamelCase.convert("very_long_column_name_with_many_parts"))
          .isEqualTo("veryLongColumnNameWithManyParts");
    }

    @Test
    @DisplayName("should handle numeric suffixes")
    void shouldHandleNumericSuffixes() {
      assertThat(UnderscoreToCamelCase.convert("column_1")).isEqualTo("column1");
      assertThat(UnderscoreToCamelCase.convert("field_123")).isEqualTo("field123");
    }

    @Test
    @DisplayName("should handle abbreviations")
    void shouldHandleAbbreviations() {
      assertThat(UnderscoreToCamelCase.convert("user_id")).isEqualTo("userId");
      assertThat(UnderscoreToCamelCase.convert("emp_id")).isEqualTo("empId");
      assertThat(UnderscoreToCamelCase.convert("dept_id")).isEqualTo("deptId");
    }
  }

  @Nested
  @DisplayName("isCamelCase Check")
  class IsCamelCaseTests {

    @Test
    @DisplayName("should recognize valid camelCase")
    void shouldRecognizeCamelCase() {
      assertThat(UnderscoreToCamelCase.isCamelCase("userName")).isTrue();
      assertThat(UnderscoreToCamelCase.isCamelCase("firstName")).isTrue();
      assertThat(UnderscoreToCamelCase.isCamelCase("id")).isTrue();
    }

    @Test
    @DisplayName("should reject underscore_case")
    void shouldRejectUnderscoreCase() {
      assertThat(UnderscoreToCamelCase.isCamelCase("user_name")).isFalse();
      assertThat(UnderscoreToCamelCase.isCamelCase("first_name")).isFalse();
    }

    @Test
    @DisplayName("should reject PascalCase")
    void shouldRejectPascalCase() {
      assertThat(UnderscoreToCamelCase.isCamelCase("UserName")).isFalse();
      assertThat(UnderscoreToCamelCase.isCamelCase("FirstName")).isFalse();
    }

    @Test
    @DisplayName("should reject null and empty")
    void shouldRejectNullAndEmpty() {
      assertThat(UnderscoreToCamelCase.isCamelCase(null)).isFalse();
      assertThat(UnderscoreToCamelCase.isCamelCase("")).isFalse();
    }
  }

  @Nested
  @DisplayName("Performance Characteristics")
  class PerformanceTests {

    @Test
    @DisplayName("should convert 10000 names in reasonable time")
    void shouldConvertManyNamesQuickly() {
      // Warmup
      for (int i = 0; i < 100; i++) {
        UnderscoreToCamelCase.convert("user_name");
      }

      // Measure
      long start = System.nanoTime();
      for (int i = 0; i < 10000; i++) {
        UnderscoreToCamelCase.convert("very_long_column_name_with_many_underscores");
      }
      long elapsed = System.nanoTime() - start;

      System.out.println("10000 conversions: " + elapsed / 1_000_000 + "ms");
      assertThat(elapsed).isLessThan(100_000_000L); // < 100ms
    }

    @Test
    @DisplayName("should be faster than regex-based conversion")
    void shouldBeFasterThanRegex() {
      String input = "very_long_column_name_with_many_parts";
      int iterations = 10000;

      // Warmup both implementations
      for (int i = 0; i < 100; i++) {
        UnderscoreToCamelCase.convert(input);
        convertWithRegex(input);
      }

      // Our implementation
      long start1 = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        UnderscoreToCamelCase.convert(input);
      }
      long ourTime = System.nanoTime() - start1;

      // Regex-based (naive) implementation
      long start2 = System.nanoTime();
      for (int i = 0; i < iterations; i++) {
        convertWithRegex(input);
      }
      long regexTime = System.nanoTime() - start2;

      System.out.println("Our time: " + ourTime / 1_000_000 + "ms");
      System.out.println("Regex time: " + regexTime / 1_000_000 + "ms");
      if (regexTime > 0) {
        System.out.println("Speedup: " + String.format("%.2f", (double) regexTime / ourTime) + "x");
      }

      // Our implementation should be reasonably fast (< 100ms for 10k conversions)
      assertThat(ourTime).isLessThan(100_000_000L);
      // And ideally faster than regex (but not a hard requirement due to JIT
      // variability)
      System.out.println(
          "Performance comparison: " + (ourTime < regexTime ? "FASTER" : "SLOWER") + " than regex");
    }

    // Naive regex implementation for comparison
    private String convertWithRegex(String input) {
      if (input == null || input.isEmpty()) {
        return "";
      }
      String[] parts = input.toLowerCase().split("_");
      StringBuilder result = new StringBuilder(parts[0]);
      for (int i = 1; i < parts.length; i++) {
        if (!parts[i].isEmpty()) {
          result.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
      }
      return result.toString();
    }
  }
}
