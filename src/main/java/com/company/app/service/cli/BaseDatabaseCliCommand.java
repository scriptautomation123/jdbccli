package com.company.app.service.cli;

import java.util.concurrent.Callable;

import com.company.app.service.service.AbstractDatabaseExecutionService;
import com.company.app.service.service.VaultConfig;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class BaseDatabaseCliCommand implements Callable<Integer> {

  // Common PicoCLI options
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

  protected final AbstractDatabaseExecutionService service;

  protected BaseDatabaseCliCommand(AbstractDatabaseExecutionService service) {
    this.service = service;
  }

  protected VaultConfig createVaultConfig() {
    return new VaultConfig(vaultUrl, roleId, secretId, ait);
  }

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
