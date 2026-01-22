package com.company.app.service;

import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.domain.model.ExecutionResult;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command for executing stored procedures and functions with vault authentication. Uses
 * JdbcCliLibrary for centralized service management.
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

  /** Default constructor for picocli */
  public ExecProcedureCmd() {
    super();
  }

  @Override
  public Integer call() {
    try {
      final ExecutionResult result =
          getLibrary()
              .executeProcedure(
                  getTypeString(),
                  database,
                  user,
                  procedureName,
                  inParams,
                  outParams,
                  createVaultConfig());
      result.formatOutput(System.out);
      return result.getExitCode();
    } catch (Exception e) {
      return ExceptionUtils.handleCliException(e, "execute procedure", System.err);
    }
  }
}
