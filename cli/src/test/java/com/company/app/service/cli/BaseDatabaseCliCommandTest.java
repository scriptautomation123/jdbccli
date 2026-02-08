package com.company.app.service.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.company.app.service.ExecSqlCmd;
import com.company.app.service.domain.model.VaultConfig;

import picocli.CommandLine;

/**
 * Unit tests for BaseDatabaseCliCommand utilities and options. Tests password prompting, vault
 * configuration, output helpers without requiring database.
 */
@DisplayName("BaseDatabaseCliCommand Unit Tests")
class BaseDatabaseCliCommandTest {

  private ByteArrayOutputStream outContent;
  private ByteArrayOutputStream errContent;
  private PrintStream originalOut;
  private PrintStream originalErr;
  private InputStream originalIn;

  @BeforeEach
  void setupStreams() {
    outContent = new ByteArrayOutputStream();
    errContent = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    originalIn = System.in;
    System.setOut(new PrintStream(outContent));
    System.setErr(new PrintStream(errContent));
  }

  @AfterEach
  void restoreStreams() {
    System.setOut(originalOut);
    System.setErr(originalErr);
    System.setIn(originalIn);
  }

  @Nested
  @DisplayName("Database Type Enum Tests")
  class DatabaseTypeTests {

    @Test
    @DisplayName("should have correct JDBC URL prefix for Oracle")
    void shouldHaveOraclePrefix() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.ORACLE.getUrlPrefix())
          .isEqualTo("jdbc:oracle:thin:@");
    }

    @Test
    @DisplayName("should have correct JDBC URL prefix for PostgreSQL")
    void shouldHavePostgresPrefix() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.POSTGRESQL.getUrlPrefix())
          .isEqualTo("jdbc:postgresql://");
    }

    @Test
    @DisplayName("should have correct JDBC URL prefix for MySQL")
    void shouldHaveMySqlPrefix() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.MYSQL.getUrlPrefix())
          .isEqualTo("jdbc:mysql://");
    }

    @Test
    @DisplayName("should have correct JDBC URL prefix for H2")
    void shouldHaveH2Prefix() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.H2.getUrlPrefix()).isEqualTo("jdbc:h2:");
    }

    @Test
    @DisplayName("should have correct JDBC URL prefix for SQL Server")
    void shouldHaveSqlServerPrefix() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.SQLSERVER.getUrlPrefix())
          .isEqualTo("jdbc:sqlserver://");
    }

    @Test
    @DisplayName("should support all common database types")
    void shouldSupportAllCommonTypes() {
      assertThat(BaseDatabaseCliCommand.DatabaseType.values())
          .hasSize(5)
          .containsExactlyInAnyOrder(
              BaseDatabaseCliCommand.DatabaseType.ORACLE,
              BaseDatabaseCliCommand.DatabaseType.MYSQL,
              BaseDatabaseCliCommand.DatabaseType.POSTGRESQL,
              BaseDatabaseCliCommand.DatabaseType.H2,
              BaseDatabaseCliCommand.DatabaseType.SQLSERVER);
    }
  }

  @Nested
  @DisplayName("Vault Configuration Tests")
  class VaultConfigTests {

    @Test
    @DisplayName("should create empty vault config when no vault options provided")
    void shouldCreateEmptyVaultConfig() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      VaultConfig config = cmd.createVaultConfig();

      assertThat(config).isNotNull();
      assertThat(config.vaultUrl()).isNull();
      assertThat(config.roleId()).isNull();
      assertThat(config.secretId()).isNull();
      assertThat(config.ait()).isNull();
    }

    @Test
    @DisplayName("should create vault config with all options")
    void shouldCreateVaultConfigWithAllOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "--vault-url",
          "https://vault.example.com",
          "--vault-role-id",
          "role123",
          "--vault-secret-id",
          "secret456",
          "--vault-ait",
          "ait789",
          "SELECT 1");

      VaultConfig config = cmd.createVaultConfig();

      assertThat(config).isNotNull();
      assertThat(config.vaultUrl()).isEqualTo("https://vault.example.com");
      assertThat(config.roleId()).isEqualTo("role123");
      assertThat(config.secretId()).isEqualTo("secret456");
      assertThat(config.ait()).isEqualTo("ait789");
    }

    @Test
    @DisplayName("should create vault config with partial options")
    void shouldCreateVaultConfigWithPartialOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "--vault-url",
          "https://vault.example.com",
          "--vault-role-id",
          "role123",
          "SELECT 1");

      VaultConfig config = cmd.createVaultConfig();

      assertThat(config).isNotNull();
      assertThat(config.vaultUrl()).isEqualTo("https://vault.example.com");
      assertThat(config.roleId()).isEqualTo("role123");
    }
  }

  @Nested
  @DisplayName("Output Helper Tests")
  class OutputHelperTests {

    @Test
    @DisplayName("info() should output message when not quiet")
    void infoShouldOutputWhenNotQuiet() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      cmd.info("Test info message");

      assertThat(outContent.toString()).contains("Test info message");
    }

    @Test
    @DisplayName("info() should not output when quiet mode enabled")
    void infoShouldNotOutputWhenQuiet() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-q",
          "SELECT 1");

      cmd.info("Test info message");

      assertThat(outContent.toString()).isEmpty();
    }

    @Test
    @DisplayName("debug() should output when verbose mode enabled")
    void debugShouldOutputWhenVerbose() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-v",
          "SELECT 1");

      cmd.debug("Test debug message");

      assertThat(outContent.toString()).contains("[DEBUG]", "Test debug message");
    }

    @Test
    @DisplayName("debug() should not output when verbose mode disabled")
    void debugShouldNotOutputWhenNotVerbose() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      cmd.debug("Test debug message");

      assertThat(outContent.toString()).isEmpty();
    }

    @Test
    @DisplayName("error() should always output to stderr")
    void errorShouldAlwaysOutput() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      cmd.error("Test error message");

      assertThat(errContent.toString()).contains("[ERROR]", "Test error message");
    }

    @Test
    @DisplayName("error() should output even in quiet mode")
    void errorShouldOutputEvenWhenQuiet() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-q",
          "SELECT 1");

      cmd.error("Test error message");

      assertThat(errContent.toString()).contains("[ERROR]", "Test error message");
    }
  }

  @Nested
  @DisplayName("Password Handling Tests")
  class PasswordHandlingTests {
    private String originalPasswordProperty;

    @BeforeEach
    void clearPasswordOverride() {
      // Maven test runs set -Djdbccli.password to avoid blocking prompts.
      originalPasswordProperty = System.getProperty("jdbccli.password");
      System.clearProperty("jdbccli.password");
    }

    @AfterEach
    void restorePasswordOverride() {
      if (originalPasswordProperty == null) {
        System.clearProperty("jdbccli.password");
      } else {
        System.setProperty("jdbccli.password", originalPasswordProperty);
      }
    }

    @Test
    @DisplayName("should use provided password when given")
    void shouldUseProvidedPassword() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-p",
          "mypassword",
          "SELECT 1");

      String password = cmd.createPasswordSupplier().get();

      assertThat(password).isEqualTo("mypassword");
    }

    @Test
    @DisplayName("should prompt for password when not provided")
    void shouldPromptForPasswordWhenNotProvided() {
      // Simulate user input
      String simulatedInput = "prompted_password\n";
      System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));

      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      String password = cmd.createPasswordSupplier().get();

      assertThat(password).isEqualTo("prompted_password");
    }

    @Test
    @DisplayName("should handle empty password gracefully")
    void shouldHandleEmptyPassword() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-p",
          "",
          "SELECT 1");

      // Should prompt when password is empty/blank
      System.setIn(new ByteArrayInputStream("fallback_password\n".getBytes()));
      String password = cmd.createPasswordSupplier().get();

      assertThat(password).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Database Type String Tests")
  class TypeStringTests {

    @Test
    @DisplayName("should return lowercase type string for Oracle")
    void shouldReturnLowercaseOracle() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "ORACLE", "-d", "jdbc:oracle:thin:@localhost:1521:xe", "-u", "user", "SELECT 1");

      assertThat(cmd.getTypeString()).isEqualTo("oracle");
    }

    @Test
    @DisplayName("should return lowercase type string for PostgreSQL")
    void shouldReturnLowercasePostgresql() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t", "POSTGRESQL", "-d", "jdbc:POSTGRESQL://localhost/test", "-u", "user", "SELECT 1");

      assertThat(cmd.getTypeString()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("should handle mixed case input")
    void shouldHandleMixedCaseInput() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd).setCaseInsensitiveEnumValuesAllowed(true);
      commandLine.parseArgs(
          "-t", "PostgreSQL", "-d", "jdbc:postgresql://localhost/test", "-u", "user", "SELECT 1");

      assertThat(cmd.getTypeString()).isEqualToIgnoringCase("POSTGRESQL");
    }
  }

  @Nested
  @DisplayName("Command Option Tests")
  class CommandOptionTests {

    @Test
    @DisplayName("should parse short option flags")
    void shouldParseShortOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "testuser",
          "-v",
          "-q",
          "SELECT 1");

      assertThat(cmd.verbose).isTrue();
      assertThat(cmd.quiet).isTrue();
      assertThat(cmd.user).isEqualTo("testuser");
    }

    @Test
    @DisplayName("should parse long option flags")
    void shouldParseLongOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "--type",
          "POSTGRESQL",
          "--database",
          "jdbc:postgresql://localhost/test",
          "--user",
          "testuser",
          "--verbose",
          "--quiet",
          "SELECT 1");

      assertThat(cmd.verbose).isTrue();
      assertThat(cmd.quiet).isTrue();
      assertThat(cmd.user).isEqualTo("testuser");
    }

    @Test
    @DisplayName("should use default database type when not specified")
    void shouldUseDefaultType() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-d", "jdbc:oracle:thin:@localhost:1521:xe", "-u", "testuser", "SELECT 1");

      assertThat(cmd.type).isEqualTo(BaseDatabaseCliCommand.DatabaseType.ORACLE);
    }

    @Test
    @DisplayName("should handle all vault options")
    void shouldHandleAllVaultOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);
      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "--vault-url",
          "https://vault.example.com",
          "--vault-role-id",
          "my-role",
          "--vault-secret-id",
          "my-secret",
          "--vault-ait",
          "my-ait",
          "SELECT 1");

      assertThat(cmd.vaultUrl).isEqualTo("https://vault.example.com");
      assertThat(cmd.roleId).isEqualTo("my-role");
      assertThat(cmd.secretId).isEqualTo("my-secret");
      assertThat(cmd.ait).isEqualTo("my-ait");
    }
  }

  @Nested
  @DisplayName("Required Options Tests")
  class RequiredOptionsTests {

    @Test
    @DisplayName("should fail when database option is missing")
    void shouldFailWhenDatabaseMissing() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);

      int exitCode = commandLine.execute("-t", "POSTGRESQL", "-u", "user", "SELECT 1");

      assertThat(exitCode).isEqualTo(2); // Missing required option
    }

    @Test
    @DisplayName("should fail when user option is missing")
    void shouldFailWhenUserMissing() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);

      int exitCode =
          commandLine.execute(
              "-t", "POSTGRESQL", "-d", "jdbc:postgresql://localhost/test", "SELECT 1");

      assertThat(exitCode).isEqualTo(2); // Missing required option
    }

    @Test
    @DisplayName("should accept all required options")
    void shouldAcceptAllRequiredOptions() {
      ExecSqlCmd cmd = new ExecSqlCmd();
      CommandLine commandLine = new CommandLine(cmd);

      commandLine.parseArgs(
          "-t",
          "POSTGRESQL",
          "-d",
          "jdbc:postgresql://localhost/test",
          "-u",
          "user",
          "-p",
          "pass",
          "SELECT 1");

      assertThat(cmd.database).isEqualTo("jdbc:postgresql://localhost/test");
      assertThat(cmd.user).isEqualTo("user");
      assertThat(cmd.password).isEqualTo("pass");
    }
  }
}
