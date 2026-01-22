package com.company.app.service.cli;

import java.io.Console;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.company.app.service.JdbcCliLibrary;
import com.company.app.service.domain.model.VaultConfig;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Abstract base class for database CLI commands providing common options and utilities.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Database connection options (-t, -d, -u, -p)
 *   <li>Vault authentication options (--vault-*)
 *   <li>Output control (-v, -q)
 *   <li>Password prompting utilities
 *   <li>JdbcCliLibrary instance management
 * </ul>
 */
public abstract class BaseDatabaseCliCommand implements Callable<Integer> {

  // =====================================================
  // Database Connection Options
  // =====================================================

  @Option(
      names = {"-t", "--type"},
      description = "Database type: ${COMPLETION-CANDIDATES}",
      defaultValue = "ORACLE")
  protected DatabaseType type;

  @Option(
      names = {"-d", "--database"},
      description = "Database connection string (JDBC URL)",
      required = true)
  protected String database;

  @Option(
      names = {"-u", "--user"},
      description = "Database username",
      required = true)
  protected String user;

  @Option(
      names = {"-p", "--password"},
      description = "Database password (prompted if not provided)",
      interactive = true,
      arity = "0..1")
  protected String password;

  // =====================================================
  // Vault Authentication Options
  // =====================================================

  @Option(names = "--vault-url", description = "Vault server URL")
  protected String vaultUrl;

  @Option(names = "--vault-role-id", description = "Vault role ID for authentication")
  protected String roleId;

  @Option(names = "--vault-secret-id", description = "Vault secret ID for authentication")
  protected String secretId;

  @Option(names = "--vault-ait", description = "Vault application identifier token")
  protected String ait;

  // =====================================================
  // Output Options
  // =====================================================

  @Option(
      names = {"-v", "--verbose"},
      description = "Enable verbose output")
  protected boolean verbose;

  @Option(
      names = {"-q", "--quiet"},
      description = "Suppress non-essential output")
  protected boolean quiet;

  @Spec protected CommandSpec spec;

  /** Cached library instance */
  private JdbcCliLibrary library;

  protected BaseDatabaseCliCommand() {}

  // =====================================================
  // Library Management
  // =====================================================

  protected JdbcCliLibrary getLibrary() {
    if (library == null) {
      library = JdbcCliLibrary.create(createPasswordSupplier());
    }
    return library;
  }

  protected Supplier<String> createPasswordSupplier() {
    return () -> {
      if (password != null && !password.isBlank()) return password;
      return promptForPassword();
    };
  }

  // =====================================================
  // Vault Configuration
  // =====================================================

  protected VaultConfig createVaultConfig() {
    if (vaultUrl == null && roleId == null && secretId == null && ait == null) {
      return VaultConfig.empty();
    }
    return new VaultConfig(vaultUrl, roleId, secretId, ait);
  }

  // =====================================================
  // Password Handling
  // =====================================================

  protected String promptForPassword() {
    final Console console = System.console();
    if (console != null) {
      final char[] pwd = console.readPassword("Enter password for %s@%s: ", user, database);
      return pwd != null ? new String(pwd) : "";
    }
    if (!quiet) {
      spec.commandLine().getOut().print("Enter password: ");
    }
    try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
      return scanner.hasNextLine() ? scanner.nextLine() : "";
    }
  }

  // =====================================================
  // Output Helpers
  // =====================================================

  protected void info(final String message) {
    if (!quiet) {
      spec.commandLine().getOut().println(message);
    }
  }

  protected void debug(final String message) {
    if (verbose && !quiet) {
      spec.commandLine().getOut().println("[DEBUG] " + message);
    }
  }

  protected void error(final String message) {
    spec.commandLine().getErr().println("[ERROR] " + message);
  }

  protected String getTypeString() {
    return type.name().toLowerCase();
  }

  // =====================================================
  // Database Type Enum
  // =====================================================

  public enum DatabaseType {
    ORACLE("jdbc:oracle:thin:@"),
    MYSQL("jdbc:mysql://"),
    POSTGRESQL("jdbc:postgresql://"),
    H2("jdbc:h2:"),
    SQLSERVER("jdbc:sqlserver://");

    private final String urlPrefix;

    DatabaseType(final String urlPrefix) {
      this.urlPrefix = urlPrefix;
    }

    public String getUrlPrefix() {
      return urlPrefix;
    }
  }
}
