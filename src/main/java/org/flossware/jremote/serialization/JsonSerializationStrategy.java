package org.flossware.jremote.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flossware.jremote.SerializationFormat;
import org.flossware.jremote.SerializationStrategy;

import java.io.IOException;

/**
 * JSON serialization strategy using Jackson ObjectMapper.
 */
public class JsonSerializationStrategy implements SerializationStrategy {
    private final ObjectMapper objectMapper;

    public JsonSerializationStrategy() {
        this.objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String serialize(Object obj) throws IOException {
        return objectMapper.writeValueAsString(obj);
    }

    @Override
    public <T> T deserialize(String data, Class<T> type) throws IOException {
        return objectMapper.readValue(data, type);
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.JSON;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public byte[] serializeToBytes(Object obj) throws IOException {
        throw new UnsupportedOperationException("JSON is a text format");
    }

    @Override
    public <T> T deserializeFromBytes(byte[] data, Class<T> type) throws IOException {
        throw new UnsupportedOperationException("JSON is a text format");
    }
}
