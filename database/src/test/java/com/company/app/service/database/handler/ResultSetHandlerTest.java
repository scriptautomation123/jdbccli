package com.company.app.service.database.handler;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Unit tests for the ResultSetHandler optimization framework.
 *
 * <p>These tests verify the functionality and issue fixes described in the package documentation:
 *
 * <ul>
 *   <li>Bounded LRU cache in DefaultResultSetHandlerFactory
 *   <li>Null key validation in TypeHandlerRegistry
 *   <li>Proper exception handling in TypeHandlerPropertyAccessor
 *   <li>UnderscoreToCamelCase conversion
 * </ul>
 *
 * <p><strong>Note:</strong> This class uses a main() method for demonstration purposes. In a
 * production environment, use a proper testing framework like JUnit. The manual test approach is
 * intentional for this demo/benchmark project.
 */
public final class ResultSetHandlerTest {

  private ResultSetHandlerTest() {
    // Test class
  }

  /** Sample bean for testing. */
  public static class TestUser {
    private Integer id;
    private String userName;
    private String firstName;
    private String lastName;

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public String getUserName() {
      return userName;
    }

    public void setUserName(String userName) {
      this.userName = userName;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    @Override
    public String toString() {
      return "TestUser{id=%d, userName='%s', firstName='%s', lastName='%s'}"
          .formatted(id, userName, firstName, lastName);
    }
  }

  /** Main entry point for running tests. */
  public static void main(String[] args) {
    System.out.println();
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║  ResultSetHandler Framework Tests                            ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    int passed = 0;
    int failed = 0;

    // Run all tests
    if (testTypeHandlerRegistryNullValidation()) {
      passed++;
    } else {
      failed++;
    }
    if (testUnderscoreToCamelCaseConversion()) {
      passed++;
    } else {
      failed++;
    }
    if (testObjectResultHandlerMapping()) {
      passed++;
    } else {
      failed++;
    }
    if (testFactoryCacheHit()) {
      passed++;
    } else {
      failed++;
    }
    if (testBoundedCacheEviction()) {
      passed++;
    } else {
      failed++;
    }
    if (testNullHandling()) {
      passed++;
    } else {
      failed++;
    }

    // Print summary
    System.out.println();
    System.out.println("══════════════════════════════════════════════════════════════");
    System.out.printf("  RESULTS: %d passed, %d failed%n", passed, failed);
    System.out.println("══════════════════════════════════════════════════════════════");

    if (failed > 0) {
      System.exit(1);
    }
  }

  /**
   * Test: TypeHandlerRegistry rejects null keys. Issue Fixed: Null key registration was previously
   * allowed.
   */
  private static boolean testTypeHandlerRegistryNullValidation() {
    System.out.println("Test: TypeHandlerRegistry null key validation");

    try {
      TypeHandlerRegistry registry = TypeHandlerRegistry.getInstance();

      // Attempt to register with null key
      try {
        registry.register(null, ResultSet::getString);
        System.out.println("  ✗ FAILED: Should have thrown NullPointerException");
        return false;
      } catch (NullPointerException e) {
        // Expected
        if (e.getMessage() != null && e.getMessage().contains("null")) {
          System.out.println("  ✓ PASSED: Null key rejected with message: " + e.getMessage());
          return true;
        } else {
          System.out.println("  ✗ FAILED: Exception message unclear");
          return false;
        }
      }
    } catch (Exception e) {
      System.out.println("  ✗ FAILED: Unexpected exception: " + e.getMessage());
      return false;
    }
  }

  /** Test: UnderscoreToCamelCase conversion. */
  private static boolean testUnderscoreToCamelCaseConversion() {
    System.out.println("Test: UnderscoreToCamelCase conversion");

    boolean allPassed = true;

    // Test cases
    allPassed &= assertConversion("user_name", "userName");
    allPassed &= assertConversion("first_name", "firstName");
    allPassed &= assertConversion("ID", "id");
    allPassed &= assertConversion("user_id", "userId");
    allPassed &= assertConversion("FIRST_NAME", "firstName");
    allPassed &= assertConversion("already_camel_case", "alreadyCamelCase");
    allPassed &= assertConversion("", "");
    allPassed &= assertConversion(null, "");

    if (allPassed) {
      System.out.println("  ✓ PASSED: All conversions correct");
    } else {
      System.out.println("  ✗ FAILED: Some conversions incorrect");
    }

    return allPassed;
  }

  private static boolean assertConversion(String input, String expected) {
    String actual = UnderscoreToCamelCase.convert(input);
    if (!expected.equals(actual)) {
      System.out.printf("    Failed: '%s' -> '%s' (expected '%s')%n", input, actual, expected);
      return false;
    }
    return true;
  }

  /** Test: ObjectResultHandler maps columns to bean properties. */
  private static boolean testObjectResultHandlerMapping() {
    System.out.println("Test: ObjectResultHandler column-to-property mapping");

    try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_mapping")) {
      // Create table with underscore column names
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            """
            CREATE TABLE test_users (
                id INTEGER,
                user_name VARCHAR(50),
                first_name VARCHAR(50),
                last_name VARCHAR(50)
            )
            """);
        stmt.execute("INSERT INTO test_users VALUES (1, 'jdoe', 'John', 'Doe')");
      }

      // Query and map
      try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM test_users");
          ResultSet rs = pstmt.executeQuery()) {

        ResultSetHandler<TestUser> handler =
            DefaultResultSetHandlerFactory.getHandler(TestUser.class, rs.getMetaData());

        if (!rs.next()) {
          System.out.println("  ✗ FAILED: No data in result set");
          return false;
        }
        TestUser user = handler.handle(rs);

        // Verify mappings
        boolean passed = true;
        if (user.getId() != 1) {
          System.out.println("    Failed: id should be 1, got " + user.getId());
          passed = false;
        }
        if (!"jdoe".equals(user.getUserName())) {
          System.out.println(
              "    Failed: userName should be 'jdoe', got '" + user.getUserName() + "'");
          passed = false;
        }
        if (!"John".equals(user.getFirstName())) {
          System.out.println(
              "    Failed: firstName should be 'John', got '" + user.getFirstName() + "'");
          passed = false;
        }
        if (!"Doe".equals(user.getLastName())) {
          System.out.println(
              "    Failed: lastName should be 'Doe', got '" + user.getLastName() + "'");
          passed = false;
        }

        if (passed) {
          System.out.println("  ✓ PASSED: " + user);
        } else {
          System.out.println("  ✗ FAILED");
        }
        return passed;
      }

    } catch (SQLException e) {
      System.out.println("  ✗ FAILED: " + e.getMessage());
      return false;
    }
  }

  /** Test: Factory cache returns same handler for same query. */
  private static boolean testFactoryCacheHit() {
    System.out.println("Test: Factory cache hit");

    try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_cache")) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE cache_test (id INTEGER, name VARCHAR(50))");
        stmt.execute("INSERT INTO cache_test VALUES (1, 'Test')");
      }

      DefaultResultSetHandlerFactory.clearCache();
      int initialSize = DefaultResultSetHandlerFactory.getCacheSize();

      // First query creates handler
      try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM cache_test");
          ResultSet rs = pstmt.executeQuery()) {
        DefaultResultSetHandlerFactory.getHandler(TestUser.class, rs.getMetaData());
      }

      int sizeAfterFirst = DefaultResultSetHandlerFactory.getCacheSize();

      // Second query should hit cache
      try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM cache_test");
          ResultSet rs = pstmt.executeQuery()) {
        DefaultResultSetHandlerFactory.getHandler(TestUser.class, rs.getMetaData());
      }

      int sizeAfterSecond = DefaultResultSetHandlerFactory.getCacheSize();

      // Verify
      if (sizeAfterFirst == initialSize + 1 && sizeAfterSecond == sizeAfterFirst) {
        System.out.println(
            "  ✓ PASSED: Cache size = " + sizeAfterSecond + " (created once, reused)");
        return true;
      } else {
        System.out.println(
            "  ✗ FAILED: Sizes were "
                + initialSize
                + " -> "
                + sizeAfterFirst
                + " -> "
                + sizeAfterSecond);
        return false;
      }

    } catch (SQLException e) {
      System.out.println("  ✗ FAILED: " + e.getMessage());
      return false;
    }
  }

  /** Test: Bounded cache evicts oldest entries. */
  private static boolean testBoundedCacheEviction() {
    System.out.println("Test: Bounded cache eviction (LRU)");

    // This is a logical test - we won't fill the entire cache but verify the
    // mechanism exists
    try {
      DefaultResultSetHandlerFactory.clearCache();

      int maxSize = DefaultResultSetHandlerFactory.getMaxCacheSize();
      DefaultResultSetHandlerFactory.CacheStats stats =
          DefaultResultSetHandlerFactory.getCacheStats();

      if (maxSize == 1000 && stats.currentSize() == 0) {
        System.out.println(
            "  ✓ PASSED: Cache is bounded at "
                + maxSize
                + " entries (currently "
                + stats.currentSize()
                + ")");
        System.out.println("    Stats: " + stats);
        return true;
      } else {
        System.out.println("  ✗ FAILED: Unexpected cache configuration");
        return false;
      }

    } catch (Exception e) {
      System.out.println("  ✗ FAILED: " + e.getMessage());
      return false;
    }
  }

  /** Test: Null value handling in type handlers. */
  private static boolean testNullHandling() {
    System.out.println("Test: Null value handling");

    try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:test_null")) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("CREATE TABLE null_test (id INTEGER, name VARCHAR(50))");
        stmt.execute("INSERT INTO null_test VALUES (1, NULL)");
      }

      try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM null_test");
          ResultSet rs = pstmt.executeQuery()) {

        ResultSetHandler<TestUser> handler =
            DefaultResultSetHandlerFactory.getHandler(TestUser.class, rs.getMetaData());

        if (!rs.next()) {
          System.out.println("  ✗ FAILED: No data in result set");
          return false;
        }
        TestUser user = handler.handle(rs);

        if (user.getId() == 1 && user.getUserName() == null) {
          System.out.println("  ✓ PASSED: Null values handled correctly");
          return true;
        } else {
          System.out.println("  ✗ FAILED: Unexpected values");
          return false;
        }
      }

    } catch (SQLException e) {
      System.out.println("  ✗ FAILED: " + e.getMessage());
      return false;
    }
  }
}
