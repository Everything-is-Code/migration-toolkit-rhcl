package com.redhat.migrationtoolkit.rhcl.service.conversion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Shared Jackson YAML configuration for {@link ManifestSerializer} and test helpers.
 */
public final class YamlSerializationConfig {

  private YamlSerializationConfig() {
  }

  public static ObjectMapper createYamlMapper() {
    YAMLFactory factory = YAMLFactory.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
        .build();
    ObjectMapper mapper = new ObjectMapper(factory);
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, false);
    return mapper;
  }
}
