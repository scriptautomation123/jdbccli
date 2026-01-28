package com.company.app.service;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI application for AIE Utility - Database and Vault operations.
 *
 * <p><strong>Usage:</strong>
 *
 * <pre>
 * # Execute SQL statement
 * aieutil exec-sql -t oracle -d "jdbc:oracle:thin:@localhost:1521:xe" -u hr "SELECT * FROM employees"
 *
 * # Execute stored procedure
 * aieutil exec-proc -t oracle -d "jdbc:oracle:thin:@localhost:1521:xe" -u hr my_proc --in "101"
 *
 * # Test connection
 * aieutil test-conn -t postgresql -d "jdbc:postgresql://localhost/db" -u admin
 *
 * # Show help
 * aieutil --help
 * aieutil exec-sql --help
 * </pre>
 */
@Command(
    name = "aieutil",
    mixinStandardHelpOptions = true,
    description = "AIE Utility - Database and Vault CLI Tool",
    version = "1.0.0",
    subcommands = {ExecSqlCmd.class, ExecProcedureCmd.class, CommandLine.HelpCommand.class})
public class AieUtilMain implements Runnable {

  @Override
  public void run() {
    // Show help when no subcommand is provided
    CommandLine.usage(this, System.out);
  }

  public static void main(final String[] args) {
    try {
      LoggingUtils.logCliStartup(System.getProperty("java.home"));

      final CommandLine cmd =
          new CommandLine(new AieUtilMain()).setCaseInsensitiveEnumValuesAllowed(true);
      cmd.setExecutionExceptionHandler(new ExceptionUtils.ExecutionExceptionHandler());
      cmd.setParameterExceptionHandler(new ExceptionUtils.ParameterExceptionHandler());

      System.exit(cmd.execute(args));
    } catch (Exception e) {
      LoggingUtils.logMinimalError(e);
      System.exit(CommandLine.ExitCode.SOFTWARE);
    }
  }
}
