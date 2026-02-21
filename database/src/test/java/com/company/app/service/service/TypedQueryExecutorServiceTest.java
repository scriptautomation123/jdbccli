package com.company.app.service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.service.QueryExecutionException;
import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.ProcedureRequest;
import com.company.app.service.domain.model.SqlRequest;
import com.company.app.service.domain.model.VaultConfig;

/**
 * Unit tests for {@link TypedQueryExecutorService} using an H2 in-memory database. Tests sealed
 * switch dispatch, null guards, and error propagation without requiring Testcontainers.
 */
@DisplayName("TypedQueryExecutorService")
@SuppressWarnings("PMD.UseUtilityClass")
class TypedQueryExecutorServiceTest {

  private static Connection connection;
  private static TypedQueryExecutorService service;
  private static String jdbcUrl;

  @BeforeAll
  static void setup() throws SQLException {
    jdbcUrl = "jdbc:h2:mem:typed_exec_test;DB_CLOSE_DELAY=-1";
    // Use explicit sa/"" so DatabaseConnectionManager can reconnect as the same
    // user
    connection = DriverManager.getConnection(jdbcUrl, "sa", "");

    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE items (
            id INTEGER PRIMARY KEY,
            name VARCHAR(100),
            quantity INTEGER
          )
          """);
      stmt.execute("INSERT INTO items VALUES (1, 'Widget', 10), (2, 'Gadget', 5)");
    }

    // H2 in-memory with default sa user requires empty password
    service = new TypedQueryExecutorService(new PasswordResolver(() -> "", true));
  }

  @AfterAll
  static void teardown() throws SQLException {
    if (connection != null && !connection.isClosed()) {
      connection.close();
    }
  }

  // =========================================================================
  // Constructor / null guards
  // =========================================================================

  @Nested
  @DisplayName("Constructor")
  class ConstructorTests {

    @Test
    @DisplayName("public constructor should reject null PasswordResolver")
    void shouldRejectNullPasswordResolver() {
      assertThatNullPointerException()
          .isThrownBy(() -> new TypedQueryExecutorService((PasswordResolver) null))
          .withMessageContaining("PasswordResolver");
    }

    @Test
    @DisplayName("package-private constructor should reject null DatabaseExecutionContext")
    void shouldRejectNullContext() {
      assertThatNullPointerException()
          .isThrownBy(() -> new TypedQueryExecutorService((DatabaseExecutionContext) null))
          .withMessageContaining("DatabaseExecutionContext");
    }
  }

  // =========================================================================
  // execute() null guards
  // =========================================================================

  @Nested
  @DisplayName("execute() null guards")
  class ExecuteNullGuardsTests {

    @Test
    @DisplayName("should reject null request")
    void shouldRejectNullRequest() {
      assertThatNullPointerException()
          .isThrownBy(() -> service.execute(null, Item.class))
          .withMessageContaining("Request");
    }

    @Test
    @DisplayName("should reject null resultClass")
    void shouldRejectNullResultClass() {
      SqlRequest request =
          new SqlRequest(
              new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
              Optional.of("SELECT * FROM items"),
              Optional.empty(),
              List.of());

      assertThatNullPointerException()
          .isThrownBy(() -> service.execute(request, null))
          .withMessageContaining("Result class");
    }
  }

  // =========================================================================
  // SqlRequest dispatch via H2
  // =========================================================================

  @Nested
  @DisplayName("SqlRequest dispatch")
  class SqlRequestDispatchTests {

    @Test
    @DisplayName("should return list of mapped objects for SELECT *")
    void shouldExecuteSelectAll() {
      SqlRequest request =
          new SqlRequest(
              new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
              Optional.of("SELECT * FROM items ORDER BY id"),
              Optional.empty(),
              List.of());

      List<Item> items = service.execute(request, Item.class);

      assertThat(items).hasSize(2).extracting(Item::getName).containsExactly("Widget", "Gadget");
    }

    @Test
    @DisplayName("should bind parameters in SELECT with WHERE")
    void shouldBindParameters() {
      SqlRequest request =
          new SqlRequest(
              new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
              Optional.of("SELECT * FROM items WHERE id = ?"),
              Optional.empty(),
              List.of(2));

      List<Item> items = service.execute(request, Item.class);

      assertThat(items).hasSize(1);
      assertThat(items.getFirst().getName()).isEqualTo("Gadget");
    }

    @Test
    @DisplayName("should return empty list when no rows match")
    void shouldReturnEmptyList() {
      SqlRequest request =
          new SqlRequest(
              new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
              Optional.of("SELECT * FROM items WHERE id = ?"),
              Optional.empty(),
              List.of(999));

      List<Item> items = service.execute(request, Item.class);

      assertThat(items).isEmpty();
    }
  }

  // =========================================================================
  // ProcedureRequest dispatch — must throw
  // =========================================================================

  @Nested
  @DisplayName("ProcedureRequest dispatch — unsupported")
  class ProcedureRequestDispatchTests {

    @Test
    @DisplayName("should throw QueryExecutionException for ProcedureRequest")
    void shouldThrowForProcedureRequest() {
      ProcedureRequest request =
          new ProcedureRequest(
              new DatabaseRequest("h2", jdbcUrl, "sa", VaultConfig.empty()),
              Optional.of("MY_PROC"),
              Optional.empty(),
              Optional.empty());

      assertThatThrownBy(() -> service.execute(request, Item.class))
          .isInstanceOf(QueryExecutionException.class)
          .hasMessageContaining("ProcedureRequest");
    }
  }

  // =========================================================================
  // Test bean
  // =========================================================================

  public static class Item {
    private Integer id;
    private String name;
    private Integer quantity;

    public Integer getId() {
      return id;
    }

    public void setId(final Integer id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public Integer getQuantity() {
      return quantity;
    }

    public void setQuantity(final Integer quantity) {
      this.quantity = quantity;
    }
  }
}
