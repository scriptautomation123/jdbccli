package com.company.app.service.util;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class YamlConfig {
  private final Map<String, Object> config;

  public YamlConfig(String path) {
    this.config = loadConfig(path);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> loadConfig(String path) {
    try {
      ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

      // Only load from file system, never from classpath
      File configFile = new File(path);
      LoggingUtils.logConfigLoading(configFile.getAbsolutePath());

      return yaml.readValue(configFile, Map.class);
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
    if (key == null || key.trim().isEmpty()) {
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
      if (current instanceof Map<?, ?>) {
        Map<?, ?> map = (Map<?, ?>) current;
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

  @SuppressWarnings("unchecked")
  public Map<String, Object> getVaultEntryById(String id) {
    Object vaultsObj = config.get("vaults");
    if (vaultsObj instanceof List<?>) {
      List<?> vaultsList = (List<?>) vaultsObj;
      for (Object entry : vaultsList) {
        if (entry instanceof Map<?, ?>) {
          Map<?, ?> map = (Map<?, ?>) entry;
          Object entryId = map.get("id");
          if (entryId != null && entryId.toString().equals(id)) {
            return (Map<String, Object>) map;
          }
        }
      }
    }
    return Collections.emptyMap();
  }
}
