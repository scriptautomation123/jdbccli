package com.company.app.service;

import java.util.Optional;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.domain.model.DatabaseRequest;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.domain.model.ProcedureRequest;
import com.company.app.service.service.ProcedureExecutorService;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command for executing stored procedures and functions with vault authentication.
 *
 * <p><strong>Examples:</strong>
 *
 * <pre>
 * # Simple procedure call
 * jdbccli exec-proc -t oracle -d "jdbc:oracle:thin:@localhost:1521:xe" -u hr get_employee_count
 *
 * # Procedure with IN parameters
 * jdbccli exec-proc -t oracle -d "jdbc:oracle:thin:@localhost:1521:xe" -u hr \
 *     update_salary --in "101,5000"
 *
 * # Procedure with OUT parameters
 * jdbccli exec-proc -t oracle -d "jdbc:oracle:thin:@localhost:1521:xe" -u hr \
 *     get_employee_info --in "101" --out "name:VARCHAR,salary:NUMBER"
 * </pre>
 */
@Command(
    name = "exec-proc",
    aliases = {"proc", "call"},
    mixinStandardHelpOptions = true,
    description = "Execute stored procedures or functions",
    version = "1.0.0",
    sortOptions = false)
public class ExecProcedureCmd extends BaseDatabaseCliCommand {

  @Parameters(
      index = "0",
      description = "Procedure or function name (e.g., MAV_OWNER.TemplateTable.Onehadoop_proc)",
      arity = "0..1")
  private String procedureName;

  @Option(
      names = {"--in-params", "--in", "--input"},
      description = "Comma-separated IN parameters (name:type:value,name:type:value)")
  private String inParams;

  @Option(
      names = {"--out-params", "--out", "--output"},
      description = "OUT parameter definitions (name:TYPE,name:TYPE)")
  private String outParams;

  private final ProcedureExecutorService service;

  /** Default constructor for picocli */
  public ExecProcedureCmd() {
    super();
    this.service = null; // Lazy init via getService()
  }

  /** Package-private constructor for testing with injectable service */
  ExecProcedureCmd(ProcedureExecutorService service) {
    super();
    this.service = service;
  }

  @Override
  public Integer call() {
    try {
      final ProcedureRequest request = buildRequest();
      final ExecutionResult result = getService().execute(request);
      result.formatOutput(System.out);
      return result.getExitCode();
    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute procedure", System.err);
    }
  }

  private ProcedureRequest buildRequest() {
    return new ProcedureRequest(
        new DatabaseRequest(getTypeString(), database, user, createVaultConfig()),
        Optional.ofNullable(procedureName),
        Optional.ofNullable(inParams),
        Optional.ofNullable(outParams));
  }

  private ProcedureExecutorService getService() {
    if (service != null) return service;
    return new ProcedureExecutorService(new PasswordResolver(this::promptForPassword));
  }
}
