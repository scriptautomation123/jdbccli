package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.service.service.TypedSqlExecutorService;

/**
 * Unit tests for {@link JdbcCliTypedApi}. Mocks {@link TypedSqlExecutorService}
 * to verify
 * delegation and the single-result vs list overload semantics.
 */
@DisplayName("JdbcCliTypedApi")
class JdbcCliTypedApiTest {

  private TypedSqlExecutorService typedService;
  private JdbcCliTypedApi api;

  @BeforeEach
  void setUp() {
    typedService = mock(TypedSqlExecutorService.class);
    api = new JdbcCliTypedApi(typedService);
  }

  // =========================================================================
  // Constructor
  // =========================================================================

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should reject null TypedSqlExecutorService")
    void shouldRejectNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new JdbcCliTypedApi(null))
          .withMessageContaining("TypedSqlExecutorService");
    }
  }

  // =========================================================================
  // runSqlTypedApi — list overload
  // =========================================================================

  @Nested
  @DisplayName("runSqlTypedApi() — list")
  class ListOverloadTests {

    @Test
    @DisplayName("should delegate to TypedSqlExecutorService and return its result")
    void shouldDelegate() {
      List<String> expected = List.of("a", "b");
      when(typedService.execute(any(), eq(String.class))).thenReturn(expected);

      List<String> result = api.runSqlTypedApi(
          "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), String.class, null);

      assertThat(result).isSameAs(expected);
      verify(typedService, times(1)).execute(any(), eq(String.class));
    }

    @Test
    @DisplayName("should return empty list when service returns empty")
    void shouldReturnEmptyList() {
      when(typedService.execute(any(), eq(String.class))).thenReturn(List.of());

      List<String> result = api.runSqlTypedApi(
          "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), String.class, null);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should treat null params as empty list")
    void shouldTreatNullParamsAsEmpty() {
      when(typedService.execute(any(), eq(String.class))).thenReturn(List.of());

      api.runSqlTypedApi("postgresql", "jdbc:url", "user", "SELECT 1", null, String.class, null);

      verify(typedService, times(1)).execute(any(), eq(String.class));
    }

    @Test
    @DisplayName("should reject null dbType")
    void shouldRejectNullDbType() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlTypedApi(
                  null, "jdbc:url", "user", "SELECT 1", List.of(), String.class, null));
    }

    @Test
    @DisplayName("should reject null database")
    void shouldRejectNullDatabase() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlTypedApi(
                  "postgresql", null, "user", "SELECT 1", List.of(), String.class, null));
    }

    @Test
    @DisplayName("should reject null user")
    void shouldRejectNullUser() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlTypedApi(
                  "postgresql", "jdbc:url", null, "SELECT 1", List.of(), String.class, null));
    }

    @Test
    @DisplayName("should reject null sql")
    void shouldRejectNullSql() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlTypedApi(
                  "postgresql", "jdbc:url", "user", null, List.of(), String.class, null));
    }

    @Test
    @DisplayName("should reject null resultClass")
    void shouldRejectNullResultClass() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlTypedApi(
                  "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), null, null));
    }
  }

  // =========================================================================
  // runSqlTypedApi — single-result overload
  // =========================================================================

  @Nested
  @DisplayName("runSqlTypedApi() — single result")
  class SingleResultOverloadTests {

    @Test
    @DisplayName("should return the single element when exactly one row")
    void shouldReturnSingleElement() {
      when(typedService.execute(any(), eq(String.class))).thenReturn(List.of("only"));

      String result = api.runSqlSingleTypedApi(
          "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), String.class, null);

      assertThat(result).isEqualTo("only");
    }

    @Test
    @DisplayName("should return null when no rows found")
    void shouldReturnNullForEmptyResult() {
      when(typedService.execute(any(), eq(String.class))).thenReturn(List.of());

      String result = api.runSqlSingleTypedApi(
          "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), String.class, null);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should throw IllegalStateException when multiple rows returned")
    @SuppressWarnings("java:S5778")
    void shouldThrowForMultipleRows() {
      when(typedService.execute(any(), eq(String.class))).thenReturn(List.of("a", "b", "c"));

      assertThatThrownBy(
          () -> api.runSqlSingleTypedApi(
              "postgresql", "jdbc:url", "user", "SELECT 1", List.of(), String.class, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("3 rows");
    }
  }

  // =========================================================================
  // SingleResult marker enum
  // =========================================================================
}
