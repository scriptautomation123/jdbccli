package com.company.app.service. cli;

import java.util. concurrent.Callable;

import com.company.app.service.service.model.VaultConfig;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Abstract base class for database CLI commands providing common PicoCLI options
 * and vault configuration support.
 * Uses composition internally - subclasses create their own service instances.
 */
public abstract class BaseDatabaseCliCommand implements Callable<Integer> {

  // Common PicoCLI options shared across all database commands
  @Option(names = { "-t", "--type" }, description = "Database type", defaultValue = "oracle")
  protected String type;

  @Option(names = { "-d", "--database" }, description = "Database name", required = true)
  protected String database;

  @Option(names = { "-u", "--user" }, description = "Database username", required = true)
  protected String user;

  @Option(names = "--vault-url", description = "Vault base URL")
  protected String vaultUrl;

  @Option(names = "--role-id", description = "Vault role ID")
  protected String roleId;

  @Option(names = "--secret-id", description = "Vault secret ID")
  protected String secretId;

  @Option(names = "--ait", description = "AIT")
  protected String ait;

  @Spec
  protected CommandSpec spec;

  /**
   * Default constructor for PicoCLI initialization.
   * Subclasses should create their own service instances.
   */
  protected BaseDatabaseCliCommand() {
    // PicoCLI requires no-arg constructor
  }

  /**
   * Creates vault configuration from command-line options.
   * 
   * @return vault configuration object
   */
  protected VaultConfig createVaultConfig() {
    return new VaultConfig(vaultUrl, roleId, secretId, ait);
  }

  /**
   * Prompts user for password via console or standard input.
   * This is a fallback - prefer using PasswordResolver for vault-based auth.
   * 
   * @return password entered by user
   */
  protected String promptForPassword() {
    spec.commandLine().getOut().print("Enter password: ");
    if (System.console() != null) {
      return new String(System.console().readPassword());
    } else {
      try (java.util.Scanner scanner = new java.util.Scanner(System.in)) {
        return scanner.nextLine();
      }
    }
  }
}