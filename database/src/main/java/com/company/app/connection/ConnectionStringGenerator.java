package com.company.app.connection;

import java.util.Locale;
import java.util.Objects;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.YamlConfig;

public final class ConnectionStringGenerator {

  private static final String DEFAULT_CONFIG_PATH = "application.yaml";
  private static final String DATABASE_PARAM = ", database=";

  /** Private constructor to prevent instantiation of utility class. */
  private ConnectionStringGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /** Lazy, thread-safe config holder using initialization-on-demand idiom. */
  private static final class ConfigHolder {
    static final YamlConfig INSTANCE = new YamlConfig(resolveConfigPath());

    private static String resolveConfigPath() {
      String override = System.getProperty("vault.config");
      if (override == null || override.isBlank()) {
        return DEFAULT_CONFIG_PATH;
      }
      return override;
    }
  }

  private static YamlConfig getConfig() {
    return ConfigHolder.INSTANCE;
  }

  /**
   * Connection information record containing URL, user, and password. Uses Java 21 record for
   * immutable data.
   *
   * @param url JDBC connection URL
   * @param user database username
   * @param password database password
   */
  public record ConnInfo(String url, String user, String password) {

    /** Compact constructor with validation. */
    public ConnInfo {
      Objects.requireNonNull(url, "URL cannot be null");
      Objects.requireNonNull(user, "User cannot be null");
      Objects.requireNonNull(password, "Password cannot be null");
    }
  }

  private interface ConnectionStrategy {
    String buildUrl();
  }

  private record H2Jdbc(String database) implements ConnectionStrategy {
    @Override
    public String buildUrl() {
      String template =
          getConfig().getRawValue("databases.h2.connection-string.jdbc-thin.template");
      return String.format(template, database);
    }
  }

  private record H2Memory(String database) implements ConnectionStrategy {
    @Override
    public String buildUrl() {
      String template =
          getConfig().getRawValue("databases.h2.connection-string.jdbc-thin.template");
      return String.format(template, "mem:" + database);
    }
  }

  private record OracleJdbc(String host, String database, Integer port)
      implements ConnectionStrategy {
    public OracleJdbc(String host, String database) {
      this(host, database, null);
    }

    @Override
    public String buildUrl() {
      try {
        String template =
            getConfig().getRawValue("databases.oracle.connection-string.jdbc-thin.template");
        int portToUse =
            Objects.requireNonNullElseGet(
                this.port,
                () ->
                    Integer.parseInt(
                        getConfig()
                            .getRawValue("databases.oracle.connection-string.jdbc-thin.port")));
        return String.format(template, host, portToUse, database);
      } catch (Exception e) {
        throw ExceptionUtils.wrap(
            e, "Failed to build Oracle JDBC URL for host=" + host + DATABASE_PARAM + database);
      }
    }
  }

  private record OracleLdap(String database) implements ConnectionStrategy {
    @Override
    public String buildUrl() {
      try {
        int port =
            Integer.parseInt(
                getConfig().getRawValue("databases.oracle.connection-string.ldap.port"));
        String context = getConfig().getRawValue("databases.oracle.connection-string.ldap.context");
        String[] servers =
            getConfig().getRawValue("databases.oracle.connection-string.ldap.servers").split(",");

        StringBuilder urlBuilder = new StringBuilder("jdbc:oracle:thin:@");

        for (int i = 0; i < servers.length; i++) {
          if (i > 0) {
            urlBuilder.append(" ");
          }
          urlBuilder.append(
              String.format("ldap://%s:%d/%s,%s", servers[i].trim(), port, database, context));
        }

        return urlBuilder.toString();
      } catch (Exception e) {
        throw ExceptionUtils.wrap(e, "Failed to build Oracle LDAP URL for database=" + database);
      }
    }
  }

  private record PostgreSqlJdbc(String host, String database, Integer port)
      implements ConnectionStrategy {
    public PostgreSqlJdbc(String host, String database) {
      this(host, database, null);
    }

    @Override
    public String buildUrl() {
      try {
        String template =
            getConfig().getRawValue("databases.postgresql.connection-string.jdbc.template");
        int portToUse =
            Objects.requireNonNullElseGet(
                this.port,
                () ->
                    Integer.parseInt(
                        getConfig()
                            .getRawValue("databases.postgresql.connection-string.jdbc.port")));
        return String.format(template, host, portToUse, database);
      } catch (Exception e) {
        throw ExceptionUtils.wrap(
            e, "Failed to build PostgreSQL JDBC URL for host=" + host + DATABASE_PARAM + database);
      }
    }
  }

  private record MySqlJdbc(String host, String database, Integer port)
      implements ConnectionStrategy {
    public MySqlJdbc(String host, String database) {
      this(host, database, null);
    }

    @Override
    public String buildUrl() {
      try {
        String template =
            getConfig().getRawValue("databases.mysql.connection-string.jdbc.template");
        int portToUse =
            Objects.requireNonNullElseGet(
                this.port,
                () ->
                    Integer.parseInt(
                        getConfig().getRawValue("databases.mysql.connection-string.jdbc.port")));
        return String.format(template, host, portToUse, database);
      } catch (Exception e) {
        throw ExceptionUtils.wrap(
            e, "Failed to build MySQL JDBC URL for host=" + host + DATABASE_PARAM + database);
      }
    }
  }

  private record SqlServerJdbc(String host, String database, Integer port)
      implements ConnectionStrategy {
    public SqlServerJdbc(String host, String database) {
      this(host, database, null);
    }

    @Override
    public String buildUrl() {
      try {
        String template =
            getConfig().getRawValue("databases.sqlserver.connection-string.jdbc.template");
        int portToUse =
            Objects.requireNonNullElseGet(
                this.port,
                () ->
                    Integer.parseInt(
                        getConfig()
                            .getRawValue("databases.sqlserver.connection-string.jdbc.port")));
        return String.format(template, host, portToUse, database);
      } catch (Exception e) {
        throw ExceptionUtils.wrap(
            e, "Failed to build SQL Server JDBC URL for host=" + host + DATABASE_PARAM + database);
      }
    }
  }

  public static ConnInfo createConnectionString(
      String type, String database, String user, String password, String host) {
    ConnectionStrategy strategy = buildConnectionStrategy(type, database, host);
    return new ConnInfo(strategy.buildUrl(), user, password);
  }

  private static ConnectionStrategy buildConnectionStrategy(
      String type, String database, String host) {
    if (isH2(type)) {
      return hasHost(host) ? new H2Jdbc(database) : new H2Memory(database);
    }

    if (!hasHost(host)) {
      // Only Oracle supports LDAP fallback without host
      if ("oracle".equalsIgnoreCase(type)) {
        return buildOracleFromDatabaseString(database);
      }
      throw new IllegalArgumentException("Database type '" + type + "' requires host parameter");
    }

    // Generic JDBC connection with host
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "oracle" -> new OracleJdbc(host, database);
      case "postgresql" -> new PostgreSqlJdbc(host, database);
      case "mysql" -> new MySqlJdbc(host, database);
      case "sqlserver" -> new SqlServerJdbc(host, database);
      default ->
          throw new IllegalArgumentException(
              "Unsupported database type: '"
                  + type
                  + "'. Supported types: oracle, postgresql, mysql, sqlserver, h2");
    };
  }

  private static boolean isH2(String type) {
    return "h2".equals(type);
  }

  private static boolean hasHost(String host) {
    return host != null && !host.trim().isEmpty();
  }

  private static ConnectionStrategy buildOracleFromDatabaseString(String database) {
    String[] parts = database.split(":");
    if (parts.length == 3) {
      try {
        int port = Integer.parseInt(parts[1]);
        // parts[0] is host, parts[2] is sid/service
        return new OracleJdbc(parts[0], parts[2], port);
      } catch (NumberFormatException e) {
        return new OracleLdap(database);
      }
    }
    return new OracleLdap(database);
  }
}
