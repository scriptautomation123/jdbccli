package com.company.app.service;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.service.model.ExecutionResult;
import com.company.app.service.service.model.ProcedureRequest;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing stored procedures with vault
 * authentication.
 * Uses composition-based service architecture for better testability.
 */
@Command(
  name = "exec-proc",
  mixinStandardHelpOptions = true,
  description = "Vault-authenticated procedure execution",
  version = "1.0.0",
  exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE
)
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

  /** Stored procedure name to execute */
  @Parameters(
    index = "0",
    description = "Stored procedure name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)",
    arity = "0..1"
  )
  private String procedure;

  /** Input parameters in format name:type:value,name:type:value */
  @Option(
    names = "--input",
    description = "Input parameters (name:type:value,name:type:value)"
  )
  private String input;

  /** Output parameters in format name:type,name:type */
  @Option(
    names = "--output",
    description = "Output parameters (name:type,name:type)"
  )
  private String output;

  /** Service for executing stored procedures - uses composition */
  private final ProcedureExecutorService service;

  /**
   * Constructs a new ExecProcedureCmd with procedure execution service.
   * Initializes service with password resolver for vault authentication.
   */
  public ExecProcedureCmd() {
    super();
    this.service = new ProcedureExecutorService(
      new PasswordResolver(this::promptForPassword)
    );
  }

  /**
   * Package-private constructor for testing with custom service.
   *
   * @param service custom procedure executor service
   */
  ExecProcedureCmd(final ProcedureExecutorService service) {
    super();
    this.service = service;
  }

  /**
   * Executes the stored procedure command with vault authentication.
   *
   * @return exit code (0 for success, non-zero for failure)
   */
  @Override
  public Integer call() {
    try {
      final ProcedureRequest request = buildProcedureRequest();
      final ExecutionResult result = service.execute(request);
      result.formatOutput(System.out); // NOSONAR
      return result.getExitCode();

    } catch (IllegalArgumentException e) {
      return ExceptionUtils.handleCliException(
        e, "execute procedure", System.err); // NOSONAR
    } catch (Exception e) {
      return ExceptionUtils.handleCliException(
        e, "execute procedure", System.err); // NOSONAR
    }
  }

  /**
   * Builds a procedure request from command-line arguments.
   *
   * @return configured procedure request
   */
  private ProcedureRequest buildProcedureRequest() {
    return new ProcedureRequest(
      new com.company.app.service.service.model.DatabaseRequest(type, database, user, createVaultConfig()),
      java.util.Optional.ofNullable(procedure),
      java.util.Optional.ofNullable(input),
      java.util.Optional.ofNullable(output));
  }
}
