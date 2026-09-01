package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.utils.Serialization;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Serializes Fabric8 {@link HasMetadata} models and Kuadrant manifest records to YAML.
 */
@ApplicationScoped
public class ManifestSerializer {

  private final com.fasterxml.jackson.databind.ObjectMapper yamlMapper;

  public ManifestSerializer() {
    this.yamlMapper = YamlSerializationConfig.createYamlMapper();
  }

  public String toYaml(Object manifest) {
    if (manifest == null) {
      throw new IllegalArgumentException("manifest must not be null");
    }
    if (manifest instanceof HasMetadata hasMetadata) {
      return Serialization.asYaml(hasMetadata);
    }
    try {
      return yamlMapper.writeValueAsString(manifest);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize manifest to YAML", e);
    }
  }
}
