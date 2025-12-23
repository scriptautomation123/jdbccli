package com.company.app.service.database;

import java.util.Objects;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.YamlConfig;

public class ConnectionStringGenerator {
  private static YamlConfig appConfig = null;

  public static void setConfigPath(String path) {
    appConfig = new YamlConfig(path);
  }

  private static YamlConfig getConfig() {
    if (appConfig == null) {
      appConfig = new YamlConfig("application.yaml");
    }
    return appConfig;
  }

  public static class ConnInfo {
    private final String url;
    private final String user;
    private final String password;

    public ConnInfo(String url, String user, String password) {
      this.url = Objects.requireNonNull(url, "URL cannot be null");
      this.user = Objects.requireNonNull(user, "User cannot be null");
      this.password = Objects.requireNonNull(password, "Password cannot be null");
    }

    public String getUrl() {
      return url;
    }

    public String getUser() {
      return user;
    }

    public String getPassword() {
      return password;
    }
  }

  private interface ConnectionStrategy {
    String buildUrl();
  }

  private static class H2Jdbc implements ConnectionStrategy {
    private final String database;

    public H2Jdbc(String database) {
      this.database = database;
    }

    @Override
    public String buildUrl() {
      String template = getConfig().getRawValue("databases.h2.connection-string.jdbc-thin.template");
      return String.format(template, database);
    }
  }

  private static class H2Memory implements ConnectionStrategy {
    private final String database;

    public H2Memory(String database) {
      this.database = database;
    }

    @Override
    public String buildUrl() {
      String template = getConfig().getRawValue("databases.h2.connection-string.jdbc-thin.template");
      return String.format(template, "mem:" + database);
    }
  }

  private static class OracleJdbc implements ConnectionStrategy {
    private final String host;
    private final String database;

    public OracleJdbc(String host, String database) {
      this.host = host;
      this.database = database;
    }

    @Override
    public String buildUrl() {
      try {
        String template = getConfig().getRawValue("databases.oracle.connection-string.jdbc-thin.template");
        int port = Integer.parseInt(
            getConfig().getRawValue("databases.oracle.connection-string.jdbc-thin.port"));
        return String.format(template, host, port, database);
      } catch (Exception e) {
        throw ExceptionUtils.wrap(
            e, "Failed to build Oracle JDBC URL for host=" + host + ", database=" + database);
      }
    }
  }

  private static class OracleLdap implements ConnectionStrategy {
    private final String database;

    public OracleLdap(String database) {
      this.database = database;
    }

    @Override
    public String buildUrl() {
      try {
        int port = Integer.parseInt(getConfig().getRawValue("databases.oracle.connection-string.ldap.port"));
        String context = getConfig().getRawValue("databases.oracle.connection-string.ldap.context");
        String[] servers = getConfig().getRawValue("databases.oracle.connection-string.ldap.servers").split(",");

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
    ConnectionStrategy strategy;

    if ("h2".equals(type)) {
      if (host != null && !host.trim().isEmpty()) {
        strategy = new H2Jdbc(database);
      } else {
        strategy = new H2Memory(database);
      }
    } else {
      if (host != null && !host.trim().isEmpty()) {
        strategy = new OracleJdbc(host, database);
      } else {
        strategy = new OracleLdap(database);
      }
    }

    return new ConnInfo(strategy.buildUrl(), user, password);
  }

  public static ConnInfo createSampleConnection() {
    return new ConnInfo(
        "jdbc:oracle:thin:@ldap://ldap.example.com:389/ORCL,cn=OracleContext,dc=example,dc=com",
        "testuser",
        "testpass");
  }
}
