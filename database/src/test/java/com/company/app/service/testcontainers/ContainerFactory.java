package com.company.app.service.testcontainers;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Lightweight factory for creating database containers based on the selected database type.
 * Provides a single entry point for container instantiation with database-specific configuration.
 * Supports PostgreSQL, MySQL, MSSQL, and Oracle.
 */
public final class ContainerFactory {

  private ContainerFactory() {
    // Utility class
  }

  /**
   * Create and configure a GenericContainer for the specified database type.
   *
   * @param dbType the database type to create a container for
   * @return a configured GenericContainer ready to start
   */
  @SuppressWarnings("resource")
  public static GenericContainer<?> createContainer(DatabaseType dbType) {
    return switch (dbType) {
      case POSTGRES ->
          new PostgreSQLContainer<>("postgres:15-alpine")
              .withDatabaseName("testdb")
              .withUsername("testuser")
              .withPassword("testpass");

      case MYSQL ->
          new MySQLContainer<>("mysql:8.0")
              .withDatabaseName("testdb")
              .withUsername("testuser")
              .withPassword("testpass")
              .withStartupTimeout(Duration.ofSeconds(60));

      case SQLSERVER ->
          new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
              .acceptLicense()
              .withPassword("TestPass@123")
              .withStartupTimeout(Duration.ofSeconds(120));

      case ORACLE ->
          new OracleContainer("gvenzl/oracle-xe:21-slim-faststart")
              .withDatabaseName("testdb")
              .withUsername("testuser")
              .withPassword("testpass");
    };
  }

  /**
   * Get JDBC URL for the given container.
   *
   * @param container the started container
   * @param dbType the database type
   * @return the JDBC connection URL
   */
  public static String getJdbcUrl(GenericContainer<?> container, DatabaseType dbType) {
    return switch (dbType) {
      case POSTGRES -> ((PostgreSQLContainer<?>) container).getJdbcUrl();
      case MYSQL -> {
        MySQLContainer<?> mysql = (MySQLContainer<?>) container;
        yield mysql.getJdbcUrl();
      }
      case SQLSERVER -> {
        MSSQLServerContainer<?> mssql = (MSSQLServerContainer<?>) container;
        yield mssql.getJdbcUrl();
      }
      case ORACLE -> ((OracleContainer) container).getJdbcUrl();
    };
  }

  /**
   * Get username for the given container.
   *
   * @param container the started container
   * @param dbType the database type
   * @return the database username
   */
  public static String getUsername(GenericContainer<?> container, DatabaseType dbType) {
    return switch (dbType) {
      case POSTGRES -> ((PostgreSQLContainer<?>) container).getUsername();
      case MYSQL -> ((MySQLContainer<?>) container).getUsername();
      case SQLSERVER -> ((MSSQLServerContainer<?>) container).getUsername();
      case ORACLE -> ((OracleContainer) container).getUsername();
    };
  }

  /**
   * Get password for the given container.
   *
   * @param container the started container
   * @param dbType the database type
   * @return the database password
   */
  public static String getPassword(GenericContainer<?> container, DatabaseType dbType) {
    return switch (dbType) {
      case POSTGRES -> ((PostgreSQLContainer<?>) container).getPassword();
      case MYSQL -> ((MySQLContainer<?>) container).getPassword();
      case SQLSERVER -> ((MSSQLServerContainer<?>) container).getPassword();
      case ORACLE -> ((OracleContainer) container).getPassword();
    };
  }
}
