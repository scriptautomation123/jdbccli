package com.company.app.service.database;

import java.util.Objects;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.YamlConfig;

public class ConnectionStringGenerator {
  private static YamlConfig appConfig = null;

  private static YamlConfig getConfig() {
    appConfig = Objects.requireNonNullElseGet(appConfig, () -> new YamlConfig("application.yaml"));
    return appConfig;
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
            e, "Failed to build Oracle JDBC URL for host=" + host + ", database=" + database);
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
    return hasHost(host) ? new OracleJdbc(host, database) : buildOracleFromDatabaseString(database);
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
