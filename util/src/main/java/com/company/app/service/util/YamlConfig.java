package com.company.app.service.util;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlConfig {
  private final Map<String, Object> config;

  public YamlConfig(String path) {
    this.config = loadConfig(path);
  }

  private Map<String, Object> loadConfig(String path) {
    try {
      ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

      // Only load from file system, never from classpath
      File configFile = new File(path);
      LoggingUtils.logConfigLoading(configFile.getAbsolutePath());

      return yaml.readValue(configFile, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      LoggingUtils.logStructuredError(
          "config_loading", "load", "FAILED", "Could not find configuration file: " + path, e);
      throw ExceptionUtils.wrap(
          e,
          "Could not find configuration file: "
              + path
              + "\n"
              + "• Ensure "
              + path
              + " exists in the file system.\n"
              + "• Specify the correct path with -Dvault.config=/path/to/vaults.yaml\n"
              + "Original error: "
              + e.getMessage());
    }
  }

  public String getRawValue(String key) {
    if (key == null || key.isBlank()) {
      LoggingUtils.logStructuredError(
          "config_access",
          "get_raw_value",
          "INVALID_KEY",
          "Configuration key cannot be null or empty",
          null);
      throw new IllegalArgumentException("Configuration key cannot be null or empty");
    }

    String[] parts = key.split("\\.");
    Object current = config;
    for (String part : parts) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(part);
      } else {
        return null;
      }
    }
    return current != null ? current.toString() : null;
  }

  public String getRawValue(String key, String defaultValue) {
    String value = getRawValue(key);
    return value != null ? value : defaultValue;
  }

  public Map<String, Object> getAll() {
    return config;
  }

  /**
   * Retrieves a vault configuration entry by ID using modern stream API.
   *
   * @param id the vault ID to search for
   * @return vault configuration map or empty map if not found
   */
  public Map<String, Object> getVaultEntryById(String id) {
    return switch (config.get("vaults")) {
      case List<?> vaultsList ->
          vaultsList.stream()
              .filter(
                  entry ->
                      entry instanceof Map<?, ?> map && id.equals(String.valueOf(map.get("id"))))
              .findFirst()
              .map(this::castToStringObjectMap)
              .orElse(Collections.emptyMap());
      default -> Collections.emptyMap();
    };
  }

  /**
   * Safely casts a vault entry to Map&lt;String, Object&gt;. The cast is safe because YAML
   * deserialization guarantees this structure.
   *
   * @param entry the entry object from the vaults list
   * @return properly typed map
   */
  @SuppressWarnings("unchecked") // Safe cast - YAML structure guarantees Map<String, Object>
  private Map<String, Object> castToStringObjectMap(Object entry) {
    return (Map<String, Object>) entry;
  }
}
