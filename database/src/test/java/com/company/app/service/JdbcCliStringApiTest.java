package com.company.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.VaultConfig;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.service.SqlExecutorService;

/**
 * Unit tests for {@link JdbcCliStringApi}. Mocks the underlying services to verify delegation,
 * null-argument guard, and vault-config defaulting.
 */
@DisplayName("JdbcCliStringApi")
class JdbcCliStringApiTest {

  private SqlExecutorService sqlService;
  private ProcedureExecutorService procedureService;
  private JdbcCliStringApi api;

  private static final ExecutionResult OK = ExecutionResult.success("OK");

  @BeforeEach
  void setUp() {
    sqlService = mock(SqlExecutorService.class);
    procedureService = mock(ProcedureExecutorService.class);
    api = new JdbcCliStringApi(sqlService, procedureService);
  }

  // =========================================================================
  // Constructor
  // =========================================================================

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should reject null SqlExecutorService")
    void shouldRejectNullSqlService() {
      assertThatNullPointerException()
          .isThrownBy(() -> new JdbcCliStringApi(null, procedureService))
          .withMessageContaining("SqlExecutorService");
    }

    @Test
    @DisplayName("should reject null ProcedureExecutorService")
    void shouldRejectNullProcedureService() {
      assertThatNullPointerException()
          .isThrownBy(() -> new JdbcCliStringApi(sqlService, null))
          .withMessageContaining("ProcedureExecutorService");
    }
  }

  // =========================================================================
  // runSqlStringApi — 6-arg (with params)
  // =========================================================================

  @Nested
  @DisplayName("runSqlStringApi(dbType, db, user, sql, params, vault)")
  class RunSqlStringApiWithParamsTests {

    @Test
    @DisplayName("should delegate to SqlExecutorService and return its result")
    void shouldDelegate() {
      when(sqlService.execute(any())).thenReturn(OK);

      ExecutionResult result =
          api.runSqlStringApi("postgresql", "jdbc:url", "user", "SELECT 1", List.of(), null);

      assertThat(result).isSameAs(OK);
      verify(sqlService, times(1)).execute(any());
    }

    @Test
    @DisplayName("should treat null params as empty list")
    void shouldTreatNullParamsAsEmpty() {
      when(sqlService.execute(any())).thenReturn(OK);

      api.runSqlStringApi("postgresql", "jdbc:url", "user", "SELECT 1", null, null);

      verify(sqlService, times(1)).execute(any());
    }

    @Test
    @DisplayName("should reject null dbType")
    void shouldRejectNullDbType() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlStringApi(null, "jdbc:url", "user", "SELECT 1", List.of(), null));
    }

    @Test
    @DisplayName("should reject null database")
    void shouldRejectNullDatabase() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlStringApi("postgresql", null, "user", "SELECT 1", List.of(), null));
    }

    @Test
    @DisplayName("should reject null user")
    void shouldRejectNullUser() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  api.runSqlStringApi("postgresql", "jdbc:url", null, "SELECT 1", List.of(), null));
    }

    @Test
    @DisplayName("should reject null sql")
    void shouldRejectNullSql() {
      assertThatNullPointerException()
          .isThrownBy(
              () -> api.runSqlStringApi("postgresql", "jdbc:url", "user", null, List.of(), null));
    }
  }

  // =========================================================================
  // runSqlStringApi — 5-arg (no params)
  // =========================================================================

  @Nested
  @DisplayName("runSqlStringApi(dbType, db, user, sql, vault)")
  class RunSqlStringApiNoParamsTests {

    @Test
    @DisplayName("should delegate with empty params")
    void shouldDelegateWithEmptyParams() {
      when(sqlService.execute(any())).thenReturn(OK);

      ExecutionResult result =
          api.runSqlStringApi("postgresql", "jdbc:url", "user", "SELECT 1", null);

      assertThat(result).isSameAs(OK);
      verify(sqlService, times(1)).execute(any());
    }
  }

  // =========================================================================
  // runScriptStringApi
  // =========================================================================

  @Nested
  @DisplayName("runScriptStringApi()")
  class RunScriptStringApiTests {

    @Test
    @DisplayName("should delegate to SqlExecutorService")
    void shouldDelegate() {
      when(sqlService.execute(any())).thenReturn(OK);

      ExecutionResult result =
          api.runScriptStringApi("postgresql", "jdbc:url", "user", "/path/script.sql", null);

      assertThat(result).isSameAs(OK);
      verify(sqlService, times(1)).execute(any());
    }

    @Test
    @DisplayName("should reject null scriptPath")
    void shouldRejectNullScriptPath() {
      assertThatNullPointerException()
          .isThrownBy(() -> api.runScriptStringApi("postgresql", "jdbc:url", "user", null, null));
    }
  }

  // =========================================================================
  // runProcedureStringApi
  // =========================================================================

  @Nested
  @DisplayName("runProcedureStringApi()")
  class RunProcedureStringApiTests {

    @Test
    @DisplayName("should delegate to ProcedureExecutorService")
    void shouldDelegate() {
      when(procedureService.execute(any())).thenReturn(OK);

      ExecutionResult result =
          api.runProcedureStringApi("postgresql", "jdbc:url", "user", "my_proc", null, null, null);

      assertThat(result).isSameAs(OK);
      verify(procedureService, times(1)).execute(any());
    }

    @Test
    @DisplayName("should reject null procedureName")
    void shouldRejectNullProcedureName() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  api.runProcedureStringApi(
                      "postgresql", "jdbc:url", "user", null, null, null, null));
    }

    @Test
    @DisplayName("should accept null inParams and outParams")
    void shouldAcceptNullInAndOutParams() {
      when(procedureService.execute(any())).thenReturn(OK);

      // Should not throw
      api.runProcedureStringApi("postgresql", "jdbc:url", "user", "proc_name", null, null, null);

      verify(procedureService, times(1)).execute(any());
    }

    @Test
    @DisplayName("should use explicit VaultConfig when provided")
    void shouldUseExplicitVaultConfig() {
      when(procedureService.execute(any())).thenReturn(OK);
      VaultConfig vaultConfig = new VaultConfig("http://vault", "role", "secret", "ait");

      api.runProcedureStringApi(
          "postgresql", "jdbc:url", "user", "proc_name", null, null, vaultConfig);

      verify(procedureService, times(1)).execute(any());
    }
  }
}
