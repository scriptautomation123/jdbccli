package com.company.app.service.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for QueryResult sealed interface and its implementations. Tests both TypedResult and
 * FormattedResult without database dependencies.
 */
@DisplayName("QueryResult")
class QueryResultTest {

  @Nested
  @DisplayName("TypedResult")
  class TypedResultTests {

    @Test
    @DisplayName("should create typed result with data")
    void shouldCreateTypedResult() {
      // Given
      List<String> data = List.of("row1", "row2", "row3");

      // When
      var result = new QueryResult.TypedResult<>(data, 3);

      // Then
      assertThat(result.data()).containsExactly("row1", "row2", "row3");
      assertThat(result.rowCount()).isEqualTo(3);
      assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("should handle empty result set")
    void shouldHandleEmptyResult() {
      // Given
      List<String> data = List.of();

      // When
      var result = new QueryResult.TypedResult<>(data, 0);

      // Then
      assertThat(result.data()).isEmpty();
      assertThat(result.rowCount()).isZero();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should return first element")
    void shouldReturnFirstElement() {
      // Given
      List<String> data = List.of("first", "second");
      var result = new QueryResult.TypedResult<>(data, 2);

      // When
      String first = result.first();

      // Then
      assertThat(first).isEqualTo("first");
    }

    @Test
    @DisplayName("should return null for empty list")
    void shouldReturnNullForEmptyList() {
      // Given
      List<String> data = List.of();
      var result = new QueryResult.TypedResult<>(data, 0);

      // When
      String first = result.first();

      // Then
      assertThat(first).isNull();
    }

    @Test
    @DisplayName("should reject null data")
    void shouldRejectNullData() {
      // When/Then
      assertThatThrownBy(() -> new QueryResult.TypedResult<String>(null, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Data cannot be null");
    }

    @Test
    @DisplayName("should be immutable (defensive copy)")
    void shouldBeImmutable() {
      // Given
      List<String> mutableData = new java.util.ArrayList<>(List.of("original"));
      var result = new QueryResult.TypedResult<>(mutableData, 1);

      // When - try to modify original list
      mutableData.add("should not appear");

      // Then - result data should not change
      assertThat(result.data()).containsExactly("original");
    }

    @Test
    @DisplayName("should return unmodifiable data list")
    void shouldReturnUnmodifiableDataList() {
      // Given
      var result = new QueryResult.TypedResult<>(List.of("a"), 1);

      // When/Then
      assertThatThrownBy(() -> result.data().add("b"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should use toString for formatted output")
    void shouldUseToStringForFormattedOutput() {
      // Given
      record Item(String value) {
        @Override
        public String toString() {
          return "Item(" + value + ")";
        }
      }
      var result = new QueryResult.TypedResult<>(List.of(new Item("x")), 1);

      // When
      String output = QueryResult.toFormattedString(result);

      // Then
      assertThat(output).contains("Rows: 1").contains("Item(x)");
    }

    @Test
    @DisplayName("should work with complex types")
    void shouldWorkWithComplexTypes() {
      // Given
      record Employee(int id, String name) {}
      List<Employee> data = List.of(new Employee(1, "Alice"), new Employee(2, "Bob"));

      // When
      var result = new QueryResult.TypedResult<>(data, 2);

      // Then
      assertThat(result.data())
          .hasSize(2)
          .extracting(Employee::name)
          .containsExactly("Alice", "Bob");
    }
  }

  @Nested
  @DisplayName("FormattedResult")
  class FormattedResultTests {

    @Test
    @DisplayName("should create formatted result")
    void shouldCreateFormattedResult() {
      // Given
      String formatted = "id | name\n---\n1  | Alice\n2  | Bob";

      // When
      var result = new QueryResult.FormattedResult(formatted, 2);

      // Then
      assertThat(result.formattedOutput()).isEqualTo(formatted);
      assertThat(result.rowCount()).isEqualTo(2);
      assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("should handle empty result")
    void shouldHandleEmptyResult() {
      // Given
      String formatted = "No rows returned";

      // When
      var result = new QueryResult.FormattedResult(formatted, 0);

      // Then
      assertThat(result.formattedOutput()).isEqualTo("No rows returned");
      assertThat(result.rowCount()).isZero();
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should consider rowCount for emptiness")
    void shouldConsiderRowCountForEmptiness() {
      // Given
      String formatted = "Some output";

      // When
      var result = new QueryResult.FormattedResult(formatted, 0);

      // Then
      assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should reject null formatted output")
    void shouldRejectNullOutput() {
      // When/Then
      assertThatThrownBy(() -> new QueryResult.FormattedResult(null, 0))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Formatted output cannot be null");
    }
  }

  @Nested
  @DisplayName("toFormattedString")
  class ToFormattedStringTests {

    @Test
    @DisplayName("should convert FormattedResult to string")
    void shouldConvertFormattedResult() {
      // Given
      String expected = "id | name\n1  | Alice";
      var result = new QueryResult.FormattedResult(expected, 1);

      // When
      String output = QueryResult.toFormattedString(result);

      // Then
      assertThat(output).isEqualTo(expected);
    }

    @Test
    @DisplayName("should convert TypedResult to string")
    void shouldConvertTypedResult() {
      // Given
      List<String> data = List.of("row1", "row2");
      var result = new QueryResult.TypedResult<>(data, 2);

      // When
      String output = QueryResult.toFormattedString(result);

      // Then
      assertThat(output).contains("Rows: 2").contains("row1").contains("row2");
    }

    @Test
    @DisplayName("should handle empty TypedResult")
    void shouldHandleEmptyTypedResult() {
      // Given
      var result = new QueryResult.TypedResult<>(List.of(), 0);

      // When
      String output = QueryResult.toFormattedString(result);

      // Then
      assertThat(output).isEqualTo("No rows returned");
    }
  }

  @Nested
  @DisplayName("fromDynamicRows")
  class FromDynamicRowsTests {

    @Test
    @DisplayName("should create result from map list")
    void shouldCreateFromMapList() {
      // Given
      List<Map<String, Object>> rows =
          List.of(Map.of("id", 1, "name", "Alice"), Map.of("id", 2, "name", "Bob"));

      // When
      QueryResult result = QueryResult.fromDynamicRows(rows);

      // Then
      assertThat(result).isInstanceOf(QueryResult.TypedResult.class);
      var typed = (QueryResult.TypedResult<?>) result;
      assertThat(typed.rowCount()).isEqualTo(2);
      assertThat(typed.data()).hasSize(2);
    }

    @Test
    @DisplayName("should handle empty map list")
    void shouldHandleEmptyMapList() {
      // Given
      List<Map<String, Object>> rows = List.of();

      // When
      QueryResult result = QueryResult.fromDynamicRows(rows);

      // Then
      var typed = (QueryResult.TypedResult<?>) result;
      assertThat(typed.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should copy dynamic rows list defensively")
    void shouldCopyDynamicRowsListDefensively() {
      // Given
      List<Map<String, Object>> rows = new ArrayList<>();
      rows.add(Map.of("id", 1));

      // When
      QueryResult result = QueryResult.fromDynamicRows(rows);
      rows.add(Map.of("id", 2));

      // Then
      var typed = (QueryResult.TypedResult<?>) result;
      assertThat(typed.data()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Pattern Matching (Java 21)")
  class PatternMatchingTests {

    @Test
    @DisplayName("should support pattern matching on TypedResult")
    void shouldPatternMatchTypedResult() {
      // Given
      QueryResult result = new QueryResult.TypedResult<>(List.of("data"), 1);

      // When
      String message =
          switch (result) {
            case QueryResult.TypedResult<?> t -> "Typed: " + t.rowCount() + " rows";
            case QueryResult.FormattedResult f -> "Formatted: " + f.rowCount() + " rows";
          };

      // Then
      assertThat(message).isEqualTo("Typed: 1 rows");
    }

    @Test
    @DisplayName("should support pattern matching on FormattedResult")
    void shouldPatternMatchFormattedResult() {
      // Given
      QueryResult result = new QueryResult.FormattedResult("output", 5);

      // When
      String message =
          switch (result) {
            case QueryResult.TypedResult<?> t -> "Typed: " + t.rowCount() + " rows";
            case QueryResult.FormattedResult f -> "Formatted: " + f.rowCount() + " rows";
          };

      // Then
      assertThat(message).isEqualTo("Formatted: 5 rows");
    }
  }
}
