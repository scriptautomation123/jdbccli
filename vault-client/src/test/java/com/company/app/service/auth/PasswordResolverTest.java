package com.company.app.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import com.company.app.service.util.VaultClient;

/**
 * Unit tests for PasswordResolver. Uses Mockito to mock VaultClient and test password resolution
 * strategies.
 */
@DisplayName("PasswordResolver Tests")
@SuppressWarnings(
    "PMD.UnusedLocalVariable") // False positive: try-with-resources variables are used
class PasswordResolverTest {

  private static final String TEST_USER = "testuser";
  private static final String TEST_DATABASE = "testdb";
  private static final String TEST_PASSWORD = "test_password_123";
  private static final String PROMPTED_PASSWORD = "prompted_password";
  private static final String TEST_VAULT_URL = "https://vault.example.com";
  private static final String TEST_ROLE_ID = "role123";
  private static final String TEST_SECRET_ID = "secret456";
  private static final String TEST_AIT = "ait789";

  private Supplier<String> passwordPrompter;
  private PasswordResolver resolver;

  @BeforeEach
  void setUp() {
    @SuppressWarnings("unchecked")
    Supplier<String> mockedPrompter = mock(Supplier.class);
    passwordPrompter = mockedPrompter;
    resolver = new PasswordResolver(passwordPrompter);
  }

  @Nested
  @DisplayName("Constructor Tests")
  class ConstructorTests {

    @Test
    @DisplayName("should create resolver with password prompter")
    void shouldCreateResolverWithPrompter() {
      PasswordResolver testResolver = new PasswordResolver(() -> "password");

      assertThat(testResolver).isNotNull();
    }

    @Test
    @DisplayName("should throw exception when prompter is null")
    void shouldThrowWhenPrompterIsNull() {
      assertThatThrownBy(() -> new PasswordResolver(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Password prompter cannot be null");
    }
  }

  @Nested
  @DisplayName("Direct Vault Parameters Resolution Tests")
  class DirectVaultParamsTests {

    @Test
    @DisplayName("should resolve password using direct vault parameters")
    void shouldResolveWithDirectVaultParams() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        TEST_VAULT_URL,
                        TEST_ROLE_ID,
                        TEST_SECRET_ID,
                        TEST_DATABASE,
                        TEST_AIT,
                        TEST_USER))
                    .thenReturn(TEST_PASSWORD);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(TEST_PASSWORD);
        verify(passwordPrompter, never()).get();
      }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should handle null, empty, or blank password from direct vault params")
    void shouldHandleInvalidPasswordFromDirectVault(String invalidPassword) {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                    .thenReturn(invalidPassword);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isEmpty();
      }
    }

    @Test
    @DisplayName("should wrap exception from direct vault params")
    void shouldWrapExceptionFromDirectVault() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                    .thenThrow(new RuntimeException("Vault error"));
              })) {

        assertThatThrownBy(() -> resolver.resolvePassword(request))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to resolve password");
      }
    }
  }

  @Nested
  @DisplayName("Vault Lookup Resolution Tests")
  class VaultLookupTests {

    @Test
    @DisplayName("should resolve password using vault lookup when no direct params")
    void shouldResolveWithVaultLookup() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(TEST_PASSWORD);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(TEST_PASSWORD);
        verify(passwordPrompter, never()).get();
      }
    }

    @Test
    @DisplayName("should fall back to prompter when vault lookup returns null")
    void shouldFallBackToPrompterWhenLookupReturnsNull() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(PROMPTED_PASSWORD);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(PROMPTED_PASSWORD);
        verify(passwordPrompter, times(1)).get();
      }
    }

    @Test
    @DisplayName("should fall back to prompter when vault lookup returns empty")
    void shouldFallBackToPrompterWhenLookupReturnsEmpty() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(PROMPTED_PASSWORD);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn("");
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(PROMPTED_PASSWORD);
        verify(passwordPrompter, times(1)).get();
      }
    }

    @Test
    @DisplayName("should fall back to prompter when vault lookup throws exception")
    void shouldFallBackToPrompterWhenLookupThrows() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(PROMPTED_PASSWORD);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE))
                    .thenThrow(new RuntimeException("Connection error"));
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(PROMPTED_PASSWORD);
        verify(passwordPrompter, times(1)).get();
      }
    }
  }

  @Nested
  @DisplayName("Console Prompt Resolution Tests")
  class ConsolePromptTests {

    @Test
    @DisplayName("should resolve password using console prompt as fallback")
    void shouldResolveWithConsolePrompt() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(PROMPTED_PASSWORD);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(PROMPTED_PASSWORD);
        verify(passwordPrompter, times(1)).get();
      }
    }

    @Test
    @DisplayName("should return empty when prompter returns null")
    void shouldReturnEmptyWhenPrompterReturnsNull() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(null);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isEmpty();
      }
    }

    @Test
    @DisplayName("should return empty string when prompter returns empty (not filtered)")
    void shouldReturnEmptyWhenPrompterReturnsEmpty() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn("");

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        // Prompter result is wrapped in Optional.ofNullable, not validated
        assertThat(result).isPresent().contains("");
      }
    }

    @Test
    @DisplayName("should return blank string when prompter returns blank (not filtered)")
    void shouldReturnEmptyWhenPrompterReturnsBlank() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn("   ");

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        // Prompter result is wrapped in Optional.ofNullable, not validated
        assertThat(result).isPresent().contains("   ");
      }
    }
  }

  @Nested
  @DisplayName("Resolution Strategy Priority Tests")
  class ResolutionStrategyTests {

    @Test
    @DisplayName("should prefer direct vault params over lookup")
    void shouldPreferDirectParamsOverLookup() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        TEST_VAULT_URL,
                        TEST_ROLE_ID,
                        TEST_SECRET_ID,
                        TEST_DATABASE,
                        TEST_AIT,
                        TEST_USER))
                    .thenReturn(TEST_PASSWORD);
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE))
                    .thenReturn("should_not_be_used");
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(TEST_PASSWORD);
        // Verify lookup was never called
        VaultClient vaultClient = mocked.constructed().get(0);
        verify(vaultClient, never()).fetchOraclePassword(TEST_USER, TEST_DATABASE);
      }
    }

    @Test
    @DisplayName("should use vault lookup when direct params not available")
    void shouldUseLookupWhenNoDirectParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(TEST_PASSWORD);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(TEST_PASSWORD);
        verify(passwordPrompter, never()).get();
      }
    }

    @Test
    @DisplayName("should use prompter only as last resort")
    void shouldUsePrompterAsLastResort() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);
      when(passwordPrompter.get()).thenReturn(PROMPTED_PASSWORD);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(TEST_USER, TEST_DATABASE)).thenReturn(null);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(PROMPTED_PASSWORD);
        verify(passwordPrompter, times(1)).get();
      }
    }
  }

  @Nested
  @DisplayName("Null Request Handling Tests")
  class NullRequestTests {

    @Test
    @DisplayName("should throw exception when request is null")
    void shouldThrowWhenRequestIsNull() {
      assertThatThrownBy(() -> resolver.resolvePassword(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("PasswordRequest cannot be null");
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle password with special characters")
    void shouldHandlePasswordWithSpecialChars() {
      String specialPassword = "p@ssw0rd!#$%^&*()";
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                    .thenReturn(specialPassword);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(specialPassword);
      }
    }

    @Test
    @DisplayName("should handle very long password")
    void shouldHandleVeryLongPassword() {
      String longPassword = "p".repeat(1000);
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                    .thenReturn(longPassword);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        assertThat(result).isPresent().contains(longPassword);
        assertThat(result.get()).hasSize(1000);
      }
    }

    @Test
    @DisplayName("should handle password with only whitespace at edges")
    void shouldHandlePasswordWithWhitespace() {
      String passwordWithWhitespace = "  password  ";
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      try (MockedConstruction<VaultClient> mocked =
          Mockito.mockConstruction(
              VaultClient.class,
              (mock, context) -> {
                when(mock.fetchOraclePassword(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString()))
                    .thenReturn(passwordWithWhitespace);
              })) {

        Optional<String> result = resolver.resolvePassword(request);

        // Should preserve the password exactly as returned
        assertThat(result).isPresent().contains(passwordWithWhitespace);
      }
    }
  }
}
