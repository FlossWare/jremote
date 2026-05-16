package org.flossware.jremote.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import org.flossware.jremote.SerializationFormat;
import org.flossware.jremote.SerializationStrategy;

import java.io.IOException;

/**
 * YAML serialization strategy using Jackson YAMLFactory.
 */
public class YamlSerializationStrategy implements SerializationStrategy {
    private final ObjectMapper yamlMapper;

    public YamlSerializationStrategy() {
        // Configure YAML factory for single-line output
        YAMLFactory yamlFactory = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build();

        this.yamlMapper = new ObjectMapper(yamlFactory);
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String serialize(Object obj) throws IOException {
        return yamlMapper.writeValueAsString(obj).trim();
    }

    @Override
    public <T> T deserialize(String data, Class<T> type) throws IOException {
        return yamlMapper.readValue(data, type);
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.YAML;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public byte[] serializeToBytes(Object obj) throws IOException {
        throw new UnsupportedOperationException("YAML is a text format");
    }

    @Override
    public <T> T deserializeFromBytes(byte[] data, Class<T> type) throws IOException {
        throw new UnsupportedOperationException("YAML is a text format");
    }
}
