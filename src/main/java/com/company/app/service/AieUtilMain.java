package com.company.app.service;

import com.company.app.service.util.ExceptionUtils;
import com.company.app.service.util.LoggingUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI application that registers all available commands.
 * Provides a unified entry point for all aieutil operations.
 */
@Command(name = "aieutil", mixinStandardHelpOptions = true, description = "AIE Utility - Database and Vault CLI Tool", version = "1.0.0", subcommands = {
        ExecProcedureCmd.class,
        ExecSqlCmd.class
})
public class AieUtilMain {

    /**
     * Main entry point for the aieutil CLI application.
     * 
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        try {
            LoggingUtils.logCliStartup(System.getProperty("java.home"));

            final CommandLine cmd = new CommandLine(new AieUtilMain());
            cmd.setExecutionExceptionHandler(new ExceptionUtils.ExecutionExceptionHandler());
            cmd.setParameterExceptionHandler(new ExceptionUtils.ParameterExceptionHandler());

            System.exit(cmd.execute(args)); // NOSONAR
        } catch (Exception e) {
            LoggingUtils.logMinimalError(e);
            System.exit(CommandLine.ExitCode.SOFTWARE); // NOSONAR
        }
    }
}