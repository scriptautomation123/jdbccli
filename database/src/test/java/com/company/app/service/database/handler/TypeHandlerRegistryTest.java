package com.company.app.service.database.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.company.app.service.database.typedapi.TypeHandler;
import com.company.app.service.database.typedapi.TypeHandlerRegistry;

/**
 * Unit tests for TypeHandlerRegistry singleton. Tests handler registration,
 * retrieval, and all
 * default type handlers. Mock ResultSet objects don't require closing as
 * they're not real database
 * resources.
 */
@SuppressWarnings({ "resource", "PMD.CloseResource" })
@DisplayName("TypeHandlerRegistry")
class TypeHandlerRegistryTest {

  private final TypeHandlerRegistry registry = TypeHandlerRegistry.getInstance();

  @Nested
  @DisplayName("Singleton Pattern")
  class SingletonTests {

    @Test
    @DisplayName("should return same instance on multiple calls")
    void shouldReturnSameInstance() {
      TypeHandlerRegistry instance1 = TypeHandlerRegistry.getInstance();
      TypeHandlerRegistry instance2 = TypeHandlerRegistry.getInstance();

      assertThat(instance1).isSameAs(instance2);
    }
  }

  @Nested
  @DisplayName("Handler Registration")
  class RegistrationTests {

    @Test
    @DisplayName("should register and retrieve custom handler")
    void shouldRegisterCustomHandler() {
      // Test with a custom class to avoid polluting the singleton registry
      class TestCustomType {
      }

      // Given
      TypeHandler<TestCustomType> customHandler = (rs, col) -> new TestCustomType();

      // When
      registry.register(TestCustomType.class, customHandler);
      TypeHandler<TestCustomType> retrieved = registry.getHandler(TestCustomType.class);

      // Then
      assertThat(retrieved).isSameAs(customHandler);
    }

    @Test
    @DisplayName("should reject null type registration")
    void shouldRejectNullType() {
      TypeHandler<String> handler = (rs, col) -> "test";

      assertThatThrownBy(() -> registry.register(null, handler))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Type must not be null");
    }

    @Test
    @DisplayName("should reject null handler registration")
    void shouldRejectNullHandler() {
      assertThatThrownBy(() -> registry.register(String.class, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Handler must not be null");
    }

    @Test
    @DisplayName("should check if handler exists")
    void shouldCheckHandlerExists() {
      assertThat(registry.hasHandler(String.class)).isTrue();
      assertThat(registry.hasHandler(Integer.class)).isTrue();
      assertThat(registry.hasHandler(TypeHandlerRegistryTest.class)).isFalse();
    }
  }

  @Nested
  @DisplayName("Default String Handler")
  class StringHandlerTests {

    @Test
    @DisplayName("should handle non-null strings")
    void shouldHandleNonNullString() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getString(1)).thenReturn("test value");

      TypeHandler<String> handler = registry.getHandler(String.class);
      String result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo("test value");
    }

    @Test
    @DisplayName("should handle null strings")
    void shouldHandleNullString() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getString(1)).thenReturn(null);

      TypeHandler<String> handler = registry.getHandler(String.class);
      String result = handler.getResult(rs, 1);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Numeric Type Handlers")
  class NumericHandlerTests {

    @Test
    @DisplayName("should handle Integer with null check")
    void shouldHandleInteger() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getInt(1)).thenReturn(42);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Integer> handler = registry.getHandler(Integer.class);
      Integer result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(42);
    }

    @Test
    @DisplayName("should handle null Integer")
    void shouldHandleNullInteger() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getInt(1)).thenReturn(0);
      Mockito.when(rs.wasNull()).thenReturn(true);

      TypeHandler<Integer> handler = registry.getHandler(Integer.class);
      Integer result = handler.getResult(rs, 1);

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("should handle Long with null check")
    void shouldHandleLong() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getLong(1)).thenReturn(123456789L);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Long> handler = registry.getHandler(Long.class);
      Long result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(123456789L);
    }

    @Test
    @DisplayName("should handle Double with null check")
    void shouldHandleDouble() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getDouble(1)).thenReturn(3.14159);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Double> handler = registry.getHandler(Double.class);
      Double result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(3.14159);
    }

    @Test
    @DisplayName("should handle BigDecimal")
    void shouldHandleBigDecimal() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      BigDecimal expected = new BigDecimal("12345.67");
      Mockito.when(rs.getBigDecimal(1)).thenReturn(expected);

      TypeHandler<BigDecimal> handler = registry.getHandler(BigDecimal.class);
      BigDecimal result = handler.getResult(rs, 1);

      assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("should handle Float with null check")
    void shouldHandleFloat() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getFloat(1)).thenReturn(2.71828f);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Float> handler = registry.getHandler(Float.class);
      Float result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(2.71828f);
    }

    @Test
    @DisplayName("should handle Short with null check")
    void shouldHandleShort() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getShort(1)).thenReturn((short) 100);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Short> handler = registry.getHandler(Short.class);
      Short result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo((short) 100);
    }

    @Test
    @DisplayName("should handle Byte with null check")
    void shouldHandleByte() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getByte(1)).thenReturn((byte) 127);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Byte> handler = registry.getHandler(Byte.class);
      Byte result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo((byte) 127);
    }
  }

  @Nested
  @DisplayName("Boolean Handler")
  class BooleanHandlerTests {

    @Test
    @DisplayName("should handle true boolean")
    void shouldHandleTrue() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getBoolean(1)).thenReturn(true);
      Mockito.when(rs.wasNull()).thenReturn(false);

      TypeHandler<Boolean> handler = registry.getHandler(Boolean.class);
      Boolean result = handler.getResult(rs, 1);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should handle null boolean")
    void shouldHandleNullBoolean() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Mockito.when(rs.getBoolean(1)).thenReturn(false);
      Mockito.when(rs.wasNull()).thenReturn(true);

      TypeHandler<Boolean> handler = registry.getHandler(Boolean.class);
      Boolean result = handler.getResult(rs, 1);

      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("Date and Time Handlers")
  class DateTimeHandlerTests {

    @Test
    @DisplayName("should handle java.sql.Date")
    void shouldHandleSqlDate() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Date expected = Date.valueOf("2024-03-15");
      Mockito.when(rs.getDate(1)).thenReturn(expected);

      TypeHandler<Date> handler = registry.getHandler(Date.class);
      Date result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should handle java.sql.Timestamp")
    void shouldHandleTimestamp() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Timestamp expected = Timestamp.valueOf("2024-03-15 10:30:45.123");
      Mockito.when(rs.getTimestamp(1)).thenReturn(expected);

      TypeHandler<Timestamp> handler = registry.getHandler(Timestamp.class);
      Timestamp result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("should handle java.util.Date")
    void shouldHandleUtilDate() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Timestamp ts = Timestamp.valueOf("2024-03-15 10:30:45");
      Mockito.when(rs.getTimestamp(1)).thenReturn(ts);

      TypeHandler<java.util.Date> handler = registry.getHandler(java.util.Date.class);
      java.util.Date result = handler.getResult(rs, 1);

      // Timestamp extends Date, so this is valid
      assertThat(result).isInstanceOf(java.util.Date.class);
      assertThat(result.getTime()).isEqualTo(ts.getTime());
    }

    @Test
    @DisplayName("should handle LocalDate")
    void shouldHandleLocalDate() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Date sqlDate = Date.valueOf("2024-03-15");
      Mockito.when(rs.getDate(1)).thenReturn(sqlDate);

      TypeHandler<LocalDate> handler = registry.getHandler(LocalDate.class);
      LocalDate result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(LocalDate.of(2024, 3, 15));
    }

    @Test
    @DisplayName("should handle LocalDateTime")
    void shouldHandleLocalDateTime() throws SQLException {
      ResultSet rs = Mockito.mock(ResultSet.class);
      Timestamp ts = Timestamp.valueOf("2024-03-15 10:30:45");
      Mockito.when(rs.getTimestamp(1)).thenReturn(ts);

      TypeHandler<LocalDateTime> handler = registry.getHandler(LocalDateTime.class);
      LocalDateTime result = handler.getResult(rs, 1);

      assertThat(result).isEqualTo(ts.toLocalDateTime());
    }
  }

  @Nested
  @DisplayName("Thread Safety")
  class ThreadSafetyTests {

    @Test
    @DisplayName("should handle concurrent handler registration")
    void shouldHandleConcurrentRegistration() throws InterruptedException {
      // Test with a custom class to avoid polluting the singleton registry
      class CustomType {
      }

      int threadCount = 10;
      Thread[] threads = new Thread[threadCount];

      for (int i = 0; i < threadCount; i++) {
        threads[i] = new Thread(
            () -> {
              TypeHandler<CustomType> handler = (rs, col) -> new CustomType();
              registry.register(CustomType.class, handler);
            });
        threads[i].start();
      }

      for (Thread thread : threads) {
        thread.join();
      }

      // Should not crash - one of the handlers will win
      assertThat(registry.hasHandler(CustomType.class)).isTrue();
    }
  }
}
