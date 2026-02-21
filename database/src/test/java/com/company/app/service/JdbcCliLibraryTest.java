package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JdbcCliLibrary}: factory methods, accessor wiring, and {@link
 * JdbcCliLibrary.SqlRequestConfig} record behaviour. No database required.
 */
@DisplayName("JdbcCliLibrary")
class JdbcCliLibraryTest {

  // =========================================================================
  // Factory methods
  // =========================================================================

  @Nested
  @DisplayName("create(Supplier)")
  class CreateTests {

    @Test
    @DisplayName("should return a non-null library with string and typed accessors")
    void shouldCreateLibrary() {
      JdbcCliLibrary library = JdbcCliLibrary.create(() -> "password");

      assertThat(library).isNotNull();
      assertThat(library.string()).isNotNull().isInstanceOf(JdbcCliStringApi.class);
      assertThat(library.typed()).isNotNull().isInstanceOf(JdbcCliTypedApi.class);
    }

    @Test
    @DisplayName("should throw NullPointerException for null supplier")
    void shouldRejectNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> JdbcCliLibrary.create(null))
          .withMessageContaining("supplier");
    }
  }

  @Nested
  @DisplayName("withPassword(String)")
  class WithPasswordTests {

    @Test
    @DisplayName("should return a non-null library")
    void shouldCreateLibraryWithPassword() {
      JdbcCliLibrary library = JdbcCliLibrary.withPassword("secret");

      assertThat(library).isNotNull();
      assertThat(library.string()).isNotNull();
      assertThat(library.typed()).isNotNull();
    }

    @Test
    @DisplayName("should throw NullPointerException for null password")
    void shouldRejectNullPassword() {
      assertThatNullPointerException()
          .isThrownBy(() -> JdbcCliLibrary.withPassword(null))
          .withMessageContaining("Password");
    }
  }

  @Nested
  @DisplayName("withVaultOnly()")
  class WithVaultOnlyTests {

    @Test
    @DisplayName("should return a non-null library")
    void shouldCreateLibrary() {
      JdbcCliLibrary library = JdbcCliLibrary.withVaultOnly();

      assertThat(library).isNotNull();
      assertThat(library.string()).isNotNull();
      assertThat(library.typed()).isNotNull();
    }
  }

  // =========================================================================
  // Accessor stability (same instance returned every call)
  // =========================================================================

  @Nested
  @DisplayName("API instance stability")
  class ApiStabilityTests {

    @Test
    @DisplayName("string() should return the same instance on every call")
    void stringShouldBeSameInstance() {
      JdbcCliLibrary library = JdbcCliLibrary.withPassword("pw");

      assertThat(library.string()).isSameAs(library.string());
    }

    @Test
    @DisplayName("typed() should return the same instance on every call")
    void typedShouldBeSameInstance() {
      JdbcCliLibrary library = JdbcCliLibrary.withPassword("pw");

      assertThat(library.typed()).isSameAs(library.typed());
    }

    @Test
    @DisplayName("string() and typed() should be different instances")
    void stringAndTypedAreDifferentInstances() {
      JdbcCliLibrary library = JdbcCliLibrary.withPassword("pw");

      assertThat(library.string()).isInstanceOf(JdbcCliStringApi.class);
      assertThat(library.typed()).isInstanceOf(JdbcCliTypedApi.class);
    }
  }

  // =========================================================================
  // SqlRequestConfig record
  // =========================================================================

  @Nested
  @DisplayName("SqlRequestConfig")
  class SqlRequestConfigTests {

    @Test
    @DisplayName("of() should create a config with empty params and empty vault")
    void ofShouldSetDefaults() {
      var config = JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user");

      assertThat(config.dbType()).isEqualTo("postgresql");
      assertThat(config.database()).isEqualTo("jdbc:url");
      assertThat(config.user()).isEqualTo("user");
      assertThat(config.sql()).isNull();
      assertThat(config.scriptPath()).isNull();
      assertThat(config.params()).isEmpty();
      assertThat(config.vaultConfig()).isNotNull();
    }

    @Test
    @DisplayName("withSql() should set SQL and clear script")
    void withSqlShouldSetSqlAndClearScript() {
      var config =
          JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user")
              .withScript("/some/script.sql")
              .withSql("SELECT 1");

      assertThat(config.sql()).isEqualTo("SELECT 1");
      assertThat(config.scriptPath()).isNull();
    }

    @Test
    @DisplayName("withScript() should set script and clear SQL")
    void withScriptShouldSetScriptAndClearSql() {
      var config =
          JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user")
              .withSql("SELECT 1")
              .withScript("/some/script.sql");

      assertThat(config.scriptPath()).isEqualTo("/some/script.sql");
      assertThat(config.sql()).isNull();
    }

    @Test
    @DisplayName("withParams(varargs) should replace params")
    void withParamsShouldReplaceVarargs() {
      var config =
          JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user")
              .withSql("SELECT ?")
              .withParams("value1", 42);

      assertThat(config.params()).containsExactly("value1", 42);
    }

    @Test
    @DisplayName("params should be immutable")
    void paramsShouldBeImmutable() {
      var config =
          JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user")
              .withSql("SELECT ?")
              .withParams("v1");

      var params = config.params();
      assertThatThrownBy(() -> params.add("v2")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("isReady() should reflect configured state")
    void isReadyShouldReflectState() {
      var base = JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user");
      assertThat(base.isReady()).isFalse();

      assertThat(base.withSql("SELECT 1").isReady()).isTrue();
      assertThat(base.withScript("/path.sql").isReady()).isTrue();
    }

    @Test
    @DisplayName("execute() should throw when neither SQL nor script is set")
    void executeShouldThrowWhenNeitherSqlNorScript() {
      var config = JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user");
      JdbcCliLibrary library = JdbcCliLibrary.withPassword("pw");

      assertThatThrownBy(() -> config.execute(library))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SQL statement or script path");
    }

    @Test
    @DisplayName("compact constructor should reject null dbType")
    void shouldRejectNullDbType() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new JdbcCliLibrary.SqlRequestConfig(null, "url", "user", null, null, null, null))
          .withMessageContaining("Database type");
    }

    @Test
    @DisplayName("compact constructor should reject null database")
    void shouldRejectNullDatabase() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new JdbcCliLibrary.SqlRequestConfig(
                      "postgresql", null, "user", null, null, null, null))
          .withMessageContaining("Database connection");
    }

    @Test
    @DisplayName("compact constructor should reject null user")
    void shouldRejectNullUser() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new JdbcCliLibrary.SqlRequestConfig(
                      "postgresql", "url", null, null, null, null, null))
          .withMessageContaining("User");
    }

    @Test
    @DisplayName("request() static factory matches SqlRequestConfig.of()")
    void requestFactoryShouldMatchOf() {
      var via = JdbcCliLibrary.request("postgresql", "jdbc:url", "user");
      var direct = JdbcCliLibrary.SqlRequestConfig.of("postgresql", "jdbc:url", "user");

      assertThat(via).isEqualTo(direct);
    }
  }
}
