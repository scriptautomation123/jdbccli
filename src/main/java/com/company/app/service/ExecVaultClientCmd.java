package com.company.app.service;

import java.util.concurrent.Callable;

import com.company.app.service.service.ExecutionResult;
import com.company.app.service.service.VaultClientRequest;
import com.company.app.service.service.VaultClientService;
import com.company.app.service.service.VaultConfig;
import com.company.app.service.util.ExceptionUtils;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command-line interface for retrieving vault passwords with vault
 * authentication.
 * Supports both vault parameter lookup and direct vault parameter usage
 * for secure password resolution.
 */
@Command(name = "exec-vault", mixinStandardHelpOptions = true, description = "Vault password retrieval", version = "1.0.0", exitCodeOnInvalidInput = CommandLine.ExitCode.USAGE, exitCodeOnExecutionException = CommandLine.ExitCode.SOFTWARE)
public class ExecVaultClientCmd implements Callable<Integer> {

    /** Vault client service for password retrieval */
    private final VaultClientService service;

    /** Database type */
    @Option(names = "--type", description = "Database type (oracle, postgresql, etc.)")
    private String type;

    /** Database name */
    @Option(names = "--database", description = "Database name", required = true)
    private String database;

    /** Database user */
    @Option(names = "--user", description = "Database user", required = true)
    private String user;

    /** Vault lookup mode identifier */
    @Parameters(index = "0", description = "Vault lookup mode (lookup)", arity = "0..1")
    private String mode;

    /** Vault ID for lookup mode */
    @Option(names = "--vault-id", description = "Vault ID for parameter lookup")
    private String vaultId;

    /** Vault URL for direct mode */
    @Option(names = "--vault-url", description = "Vault URL for direct authentication")
    private String vaultUrl;

    /** Role ID for direct mode */
    @Option(names = "--role-id", description = "Role ID for direct authentication")
    private String roleId;

    /** Secret ID for direct mode */
    @Option(names = "--secret-id", description = "Secret ID for direct authentication")
    private String secretId;

    /** AIT for direct mode */
    @Option(names = "--ait", description = "AIT for direct authentication")
    private String ait;

    /**
     * Constructs a new ExecVaultClientCmd with vault client service initialization.
     */
    public ExecVaultClientCmd() {
        this.service = new VaultClientService();
    }

    @Override
    public Integer call() {
        try {
            final VaultClientRequest request = buildVaultClientRequest();
            final ExecutionResult result = service.execute(request);
            result.formatOutput(System.out); // NOSONAR
            return result.getExitCode();

        } catch (Exception e) {
            return ExceptionUtils.handleCliException(e, "retrieve vault password", System.err); // NOSONAR
        }
    }

    private VaultClientRequest buildVaultClientRequest() {
        return VaultClientRequest.builder()
                .type(type)
                .database(database)
                .user(user)
                .mode(mode)
                .vaultId(vaultId)
                .vaultConfig(createVaultConfig())
                .build();
    }

    private VaultConfig createVaultConfig() {
        return new VaultConfig(vaultUrl, roleId, secretId, ait);
    }
}