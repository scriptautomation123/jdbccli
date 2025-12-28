package com.company.app.service;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.service.ExecutionResult;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.service.ProcedureRequest;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing stored procedures with vault
 * authentication.
 * Supports both LDAP and JDBC connections with secure password resolution.
 */
@Command(name = "exec-proc", mixinStandardHelpOptions = true, description = "Vault-authenticated procedure execution", version = "1.0.0", exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

  /** Stored procedure name to execute */
  @Parameters(index = "0", description = "Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)", arity = "0..1")
  private String procedure;

  /** Input parameters in format name:type:value,name:type:value */
  @Option(names = "--input", description = "Input parameters (name:type:value,name:type:value)")
  private String input;

  /** Output parameters in format name:type,name:type */
  @Option(names = "--output", description = "Output parameters (name:type,name:type)")
  private String output;

  /**
   * Constructs a new ExecProcedureCmd with database service initialization.
   */
  public ExecProcedureCmd() {
    super(createService());
  }

  private static ProcedureExecutorService createService() {
    return new ProcedureExecutorService(
        new PasswordResolver(() -> new String(System.console().readPassword("Enter password: "))));
  }

  @Override
  public Integer call() {
    try {
      final ProcedureRequest request = buildProcedureRequest();
      final ExecutionResult result = service.execute(request);
      result.formatOutput(System.out); // NOSONAR
      return result.getExitCode();

    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute procedure", System.err); // NOSONAR
    }
  }

  private ProcedureRequest buildProcedureRequest() {
    return ProcedureRequest.builder()
        .type(type)
        .database(database)
        .user(user)
        .procedure(procedure)
        .input(input)
        .output(output)
        .vaultConfig(createVaultConfig())
        .build();
  }

}
