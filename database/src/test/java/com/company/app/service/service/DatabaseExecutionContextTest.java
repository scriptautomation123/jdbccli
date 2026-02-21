package com.company.app.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.domain.model.VaultConfig;

/**
 * Unit tests for {@link DatabaseExecutionContext} using H2 in-memory database. Verifies that the
 * virtual-thread dispatch, password resolution, and connection lifecycle work correctly.
 */
@DisplayName("DatabaseExecutionContext")
@SuppressWarnings("PMD.UseUtilityClass")
class DatabaseExecutionContextTest {

  private static String jdbcUrl;
  private static Connection connection;

  @BeforeAll
  static void setup() throws SQLException {
    jdbcUrl = "jdbc:h2:mem:ctx_test;DB_CLOSE_DELAY=-1";
    // Use explicit sa/"" so DatabaseConnectionManager can reconnect as the same
    // user
    connection = DriverManager.getConnection(jdbcUrl, "sa", "");
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("CREATE TABLE ping (val INTEGER)");
      stmt.execute("INSERT INTO ping VALUES (42)");
    }
  }

  @AfterAll
  static void teardown() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  private static DatabaseExecutionContext ctxH2() {
    // H2 in-memory with default sa user requires empty password
    return new DatabaseExecutionContext(new PasswordResolver(() -> "", true));
  }

  private static SqlRequest pingRequest() {
    return new SqlRequest(
        new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
        Optional.of("SELECT val FROM ping"),
        Optional.empty(),
        List.of());
  }

  // =========================================================================
  // Constructor
  // =========================================================================

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("should reject null PasswordResolver")
    void shouldRejectNullResolver() {
      assertThatNullPointerException()
          .isThrownBy(() -> new DatabaseExecutionContext(null))
          .withMessageContaining("PasswordResolver");
    }
  }

  // =========================================================================
  // executeWithPasswordResolution
  // =========================================================================

  @Nested
  @DisplayName("executeWithPasswordResolution()")
  class ExecuteTests {

    @Test
    @DisplayName("should call the connection executor and return its result")
    void shouldCallExecutorAndReturnResult() {
      DatabaseExecutionContext ctx = ctxH2();

      ExecutionResult result =
          ctx.executeWithPasswordResolution(pingRequest(), conn -> ExecutionResult.success("hit"));

      assertThat(result.getExitCode()).isZero();
      assertThat(result.getMessage()).isEqualTo("hit");
    }

    @Test
    @DisplayName("should provide a real live connection to the executor")
    void shouldProvideRealConnection() {
      DatabaseExecutionContext ctx = ctxH2();
      AtomicBoolean gotLiveConnection = new AtomicBoolean(false);

      ctx.executeWithPasswordResolution(
          pingRequest(),
          conn -> {
            try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT val FROM ping")) {
              if (rs.next()) {
                gotLiveConnection.set(rs.getInt(1) == 42);
              }
            } catch (SQLException ignored) {
              // handled in assertion
            }
            return ExecutionResult.success("done");
          });

      assertThat(gotLiveConnection).isTrue();
    }

    @Test
    @DisplayName("should execute callback on a virtual thread")
    void shouldRunOnVirtualThread() {
      DatabaseExecutionContext ctx = ctxH2();
      AtomicReference<Thread> callbackThread = new AtomicReference<>();

      ctx.executeWithPasswordResolution(
          pingRequest(),
          conn -> {
            callbackThread.set(Thread.currentThread());
            return ExecutionResult.success("ok");
          });

      assertThat(callbackThread.get()).isNotNull();
      assertThat(callbackThread.get().isVirtual()).isTrue();
    }

    @Test
    @DisplayName("should return failure result when executor throws SQLException")
    void shouldReturnFailureOnSqlException() {
      DatabaseExecutionContext ctx = ctxH2();

      ExecutionResult result =
          ctx.executeWithPasswordResolution(
              pingRequest(),
              conn -> {
                throw new SQLException("simulated SQL error");
              });

      assertThat(result.getExitCode()).isNotZero();
    }
  }
}
