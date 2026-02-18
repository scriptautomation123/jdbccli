package com.company.app.service.testcontainers;

import java.util.Locale;

/**
 * Enumeration of supported database types for integration testing. Allows tests to be parameterized
 * across multiple database engines. The database type is selected via the system property {@code
 * -Ddatabase=postgres|mysql|sqlserver|oracle}, defaulting to postgres when unspecified for backward
 * compatibility.
 */
public enum DatabaseType {
  POSTGRES("postgres:15-alpine", "org.postgresql.Driver", 5432, "schema-postgres.sql"),
  MYSQL("mysql:8.0", "com.mysql.cj.jdbc.Driver", 3306, "schema-mysql.sql"),
  SQLSERVER(
      "mcr.microsoft.com/mssql/server:2022-latest",
      "com.microsoft.sqlserver.jdbc.SQLServerDriver",
      1433,
      "schema-sqlserver.sql"),
  ORACLE(
      "gvenzl/oracle-xe:21-slim-faststart", "oracle.jdbc.OracleDriver", 1521, "schema-oracle.sql");

  private final String testImage;
  private final String driverClass;
  private final int port;
  private final String schemaResource;

  DatabaseType(String testImage, String driverClass, int port, String schemaResource) {
    this.testImage = testImage;
    this.driverClass = driverClass;
    this.port = port;
    this.schemaResource = schemaResource;
  }

  public String getTestImage() {
    return testImage;
  }

  public String getDriverClass() {
    return driverClass;
  }

  public int getPort() {
    return port;
  }

  public String getSchemaResource() {
    return schemaResource;
  }

  /**
   * Get the database type from system property or default to POSTGRES.
   *
   * @return the resolved DatabaseType
   */
  public static DatabaseType fromSystemProperty() {
    String dbType = System.getProperty("database", "postgres").toLowerCase(Locale.ROOT);
    try {
      return DatabaseType.valueOf(dbType.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      System.out.println(
          "Unknown database type: "
              + dbType
              + ". Defaulting to POSTGRES. Valid types: postgres, mysql, sqlserver, oracle");
      return POSTGRES;
    }
  }
}
