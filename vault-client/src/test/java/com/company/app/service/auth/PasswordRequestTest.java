package com.company.app.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PasswordRequest record. Tests validation, direct vault parameters, and accessor
 * methods.
 */
@DisplayName("PasswordRequest Tests")
class PasswordRequestTest {

  private static final String TEST_USER = "testuser";
  private static final String TEST_DATABASE = "testdb";
  private static final String TEST_VAULT_URL = "https://vault.example.com";
  private static final String TEST_ROLE_ID = "role123";
  private static final String TEST_SECRET_ID = "secret456";
  private static final String TEST_AIT = "ait789";

  @Nested
  @DisplayName("Constructor Validation Tests")
  class ConstructorValidationTests {

    @Test
    @DisplayName("should create PasswordRequest with all vault parameters")
    void shouldCreateWithAllVaultParams() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.user()).isEqualTo(TEST_USER);
      assertThat(request.database()).isEqualTo(TEST_DATABASE);
      assertThat(request.vaultUrl()).isEqualTo(TEST_VAULT_URL);
      assertThat(request.roleId()).isEqualTo(TEST_ROLE_ID);
      assertThat(request.secretId()).isEqualTo(TEST_SECRET_ID);
      assertThat(request.ait()).isEqualTo(TEST_AIT);
    }

    @Test
    @DisplayName("should create PasswordRequest with no vault parameters")
    void shouldCreateWithNoVaultParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThat(request.user()).isEqualTo(TEST_USER);
      assertThat(request.database()).isEqualTo(TEST_DATABASE);
      assertThat(request.vaultUrl()).isNull();
      assertThat(request.roleId()).isNull();
      assertThat(request.secretId()).isNull();
      assertThat(request.ait()).isNull();
    }

    @Test
    @DisplayName("should throw exception when user is null")
    void shouldThrowWhenUserIsNull() {
      assertThatThrownBy(
              () ->
                  new PasswordRequest(
                      null, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("User cannot be null");
    }

    @Test
    @DisplayName("should throw exception when database is null")
    void shouldThrowWhenDatabaseIsNull() {
      assertThatThrownBy(
              () ->
                  new PasswordRequest(
                      TEST_USER, null, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Database cannot be null");
    }

    @Test
    @DisplayName("should throw exception when only vault URL is provided")
    void shouldThrowWhenOnlyVaultUrlProvided() {
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, TEST_VAULT_URL, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should throw exception when only role ID is provided")
    void shouldThrowWhenOnlyRoleIdProvided() {
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, null, TEST_ROLE_ID, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should throw exception when only secret ID is provided")
    void shouldThrowWhenOnlySecretIdProvided() {
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, TEST_SECRET_ID, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should throw exception when only AIT is provided")
    void shouldThrowWhenOnlyAitProvided() {
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, TEST_AIT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should throw exception when three out of four vault params provided")
    void shouldThrowWhenThreeOutOfFourProvided() {
      assertThatThrownBy(
              () ->
                  new PasswordRequest(
                      TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should reject empty strings as invalid vault params")
    void shouldRejectEmptyStringsInVaultParams() {
      // Empty strings are non-null, so validation detects "some" params but not "all"
      // params
      assertThatThrownBy(() -> new PasswordRequest(TEST_USER, TEST_DATABASE, "", "", "", ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }
  }

  @Nested
  @DisplayName("Direct Vault Parameters Tests")
  class DirectVaultParametersTests {

    @Test
    @DisplayName("hasDirectVaultParams should return true when all params provided")
    void shouldReturnTrueWhenAllParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.hasDirectVaultParams()).isTrue();
    }

    @Test
    @DisplayName("hasDirectVaultParams should return false when no params provided")
    void shouldReturnFalseWhenNoParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThat(request.hasDirectVaultParams()).isFalse();
    }

    @Test
    @DisplayName("should reject empty strings as invalid vault params")
    void shouldRejectEmptyStringsInDirectParams() {
      // Empty strings are still non-null, so hasAnyVaultParams returns true
      // but hasAllDirectVaultParams returns false (isNotBlank check fails)
      assertThatThrownBy(() -> new PasswordRequest(TEST_USER, TEST_DATABASE, "", "", "", ""))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }

    @Test
    @DisplayName("should reject blank strings as invalid vault params")
    void shouldRejectBlankStringsInDirectParams() {
      // Blank strings are non-null, so hasAnyVaultParams returns true
      // but isNotBlank fails, so hasAllDirectVaultParams returns false
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, "   ", "   ", "   ", "   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }
  }

  @Nested
  @DisplayName("Accessor Method Tests")
  class AccessorMethodTests {

    @Test
    @DisplayName("getUser should return user")
    void shouldGetUser() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getUser()).isEqualTo(TEST_USER);
    }

    @Test
    @DisplayName("getDatabase should return database")
    void shouldGetDatabase() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getDatabase()).isEqualTo(TEST_DATABASE);
    }

    @Test
    @DisplayName("getVaultUrl should return vault URL when direct params provided")
    void shouldGetVaultUrlWhenDirectParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getVaultUrl()).isEqualTo(TEST_VAULT_URL);
    }

    @Test
    @DisplayName("getVaultUrl should throw exception when direct params not provided")
    void shouldThrowWhenGetVaultUrlWithoutDirectParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThatThrownBy(request::getVaultUrl)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Vault parameters not available");
    }

    @Test
    @DisplayName("getRoleId should return role ID when direct params provided")
    void shouldGetRoleIdWhenDirectParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getRoleId()).isEqualTo(TEST_ROLE_ID);
    }

    @Test
    @DisplayName("getRoleId should throw exception when direct params not provided")
    void shouldThrowWhenGetRoleIdWithoutDirectParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThatThrownBy(request::getRoleId)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Vault parameters not available");
    }

    @Test
    @DisplayName("getSecretId should return secret ID when direct params provided")
    void shouldGetSecretIdWhenDirectParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getSecretId()).isEqualTo(TEST_SECRET_ID);
    }

    @Test
    @DisplayName("getSecretId should throw exception when direct params not provided")
    void shouldThrowWhenGetSecretIdWithoutDirectParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThatThrownBy(request::getSecretId)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Vault parameters not available");
    }

    @Test
    @DisplayName("getAit should return AIT when direct params provided")
    void shouldGetAitWhenDirectParamsProvided() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getAit()).isEqualTo(TEST_AIT);
    }

    @Test
    @DisplayName("getAit should throw exception when direct params not provided")
    void shouldThrowWhenGetAitWithoutDirectParams() {
      PasswordRequest request =
          new PasswordRequest(TEST_USER, TEST_DATABASE, null, null, null, null);

      assertThatThrownBy(request::getAit)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Vault parameters not available");
    }
  }

  @Nested
  @DisplayName("Record Equality Tests")
  class RecordEqualityTests {

    @Test
    @DisplayName("should be equal when all fields are the same")
    void shouldBeEqualWhenAllFieldsSame() {
      PasswordRequest request1 =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);
      PasswordRequest request2 =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request1).isEqualTo(request2).hasSameHashCodeAs(request2);
    }

    @Test
    @DisplayName("should not be equal when user is different")
    void shouldNotBeEqualWhenUserDifferent() {
      PasswordRequest request1 =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);
      PasswordRequest request2 =
          new PasswordRequest(
              "different", TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request1).isNotEqualTo(request2);
    }

    @Test
    @DisplayName("should not be equal when database is different")
    void shouldNotBeEqualWhenDatabaseDifferent() {
      PasswordRequest request1 =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);
      PasswordRequest request2 =
          new PasswordRequest(
              TEST_USER, "differentdb", TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request1).isNotEqualTo(request2);
    }

    @Test
    @DisplayName("should have consistent toString representation")
    void shouldHaveConsistentToString() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      String toString = request.toString();

      assertThat(toString).contains("PasswordRequest").contains(TEST_USER).contains(TEST_DATABASE);
    }
  }

  @Nested
  @DisplayName("Edge Case Tests")
  class EdgeCaseTests {

    @Test
    @DisplayName("should handle special characters in user")
    void shouldHandleSpecialCharsInUser() {
      PasswordRequest request =
          new PasswordRequest(
              "user@domain.com",
              TEST_DATABASE,
              TEST_VAULT_URL,
              TEST_ROLE_ID,
              TEST_SECRET_ID,
              TEST_AIT);

      assertThat(request.getUser()).isEqualTo("user@domain.com");
    }

    @Test
    @DisplayName("should handle special characters in database")
    void shouldHandleSpecialCharsInDatabase() {
      PasswordRequest request =
          new PasswordRequest(
              TEST_USER, "db-name_123", TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getDatabase()).isEqualTo("db-name_123");
    }

    @Test
    @DisplayName("should handle very long strings")
    void shouldHandleVeryLongStrings() {
      String longString = "a".repeat(1000);
      PasswordRequest request =
          new PasswordRequest(
              longString, TEST_DATABASE, TEST_VAULT_URL, TEST_ROLE_ID, TEST_SECRET_ID, TEST_AIT);

      assertThat(request.getUser()).hasSize(1000);
    }

    @Test
    @DisplayName("should handle URLs with different protocols")
    void shouldHandleUrlsWithDifferentProtocols() {
      PasswordRequest request1 =
          new PasswordRequest(
              TEST_USER,
              TEST_DATABASE,
              "https://vault.example.com",
              TEST_ROLE_ID,
              TEST_SECRET_ID,
              TEST_AIT);
      PasswordRequest request2 =
          new PasswordRequest(
              TEST_USER,
              TEST_DATABASE,
              "http://vault.example.com",
              TEST_ROLE_ID,
              TEST_SECRET_ID,
              TEST_AIT);

      assertThat(request1.getVaultUrl()).startsWith("https://");
      assertThat(request2.getVaultUrl()).startsWith("http://");
    }

    @Test
    @DisplayName("should reject whitespace-only vault parameters as invalid")
    void shouldRejectWhitespaceOnlyAsInvalid() {
      // Whitespace-only strings are non-null, triggering validation failure
      assertThatThrownBy(
              () -> new PasswordRequest(TEST_USER, TEST_DATABASE, "   ", "   ", "   ", "   "))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Invalid vault parameter combination");
    }
  }
}
