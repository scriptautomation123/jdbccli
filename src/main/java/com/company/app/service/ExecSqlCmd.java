package com.company.app.service;

import com.company.app.service.auth.PasswordResolver;
import com.company.app.service.cli.BaseDatabaseCliCommand;
import com.company.app.service.service.ExecutionResult;
import com.company.app.service.service.SqlExecutorService;
import com.company.app.service.service.SqlRequest;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for executing SQL statements with vault
 * authentication.
 * Supports both direct SQL execution and SQL script files with secure
 * password resolution.
 */
@Command(name = "exec-sql", mixinStandardHelpOptions = true, description = "Vault-authenticated SQL execution", version = "1.0.0", exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecSqlCmd extends BaseDatabaseCliCommand {

    /** SQL statement to execute */
    @Parameters(index = "0", description = "SQL statement to execute", arity = "0..1")
    private String sql;

    /** SQL script file path */
    @Option(names = "--script", description = "SQL script file path")
    private String script;

    /** SQL parameters in format value1,value2,value3 */
    @Option(names = "--params", description = "SQL parameters (value1,value2,value3)")
    private String params;

    /**
     * Constructs a new ExecSqlCmd with SQL execution service initialization.
     */
    public ExecSqlCmd() {
        super(createService());
    }

    private static SqlExecutorService createService() {
        return new SqlExecutorService(
                new PasswordResolver(() -> new String(System.console().readPassword("Enter password: "))));
    }

    @Override
    public Integer call() {
        try {
            final SqlRequest request = buildSqlRequest();
            final ExecutionResult result = service.execute(request);
            result.formatOutput(System.out); // NOSONAR
            return result.getExitCode();

        } catch (Exception e) {
            return ExceptionUtils.handleCliException(e, "execute SQL", System.err); // NOSONAR
        }
    }

    private SqlRequest buildSqlRequest() {
        return SqlRequest.builder()
                .type(type)
                .database(database)
                .user(user)
                .sql(sql)
                .script(script)
                .params(parseParams(params))
                .vaultConfig(createVaultConfig())
                .build();
    }

    /**
     * Parses comma-separated parameter string into list of objects.
     * 
     * @param params comma-separated parameter string
     * @return list of parameter values
     */
    private java.util.List<Object> parseParams(final String params) {
        if (params == null || params.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        final String[] paramArray = params.split(",");
        final java.util.List<Object> paramList = new java.util.ArrayList<>(paramArray.length);
        for (final String param : paramArray) {
            paramList.add(param.trim());
        }
        return paramList;
    }

}