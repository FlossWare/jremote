package org.flossware.jremote.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.flossware.jremote.SerializationFormat;
import org.flossware.jremote.SerializationStrategy;

import java.io.IOException;
import java.util.Base64;

/**
 * MessagePack serialization strategy using Jackson MessagePack dataformat.
 * Uses Base64 encoding for text-based wire protocol.
 */
public class MessagePackSerializationStrategy implements SerializationStrategy {
    private final ObjectMapper msgpackMapper;

    public MessagePackSerializationStrategy() {
        this.msgpackMapper = new ObjectMapper(new MessagePackFactory());
        this.msgpackMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String serialize(Object obj) throws IOException {
        // Serialize to bytes then Base64 encode for text-based protocol
        byte[] bytes = msgpackMapper.writeValueAsBytes(obj);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public <T> T deserialize(String data, Class<T> type) throws IOException {
        // Base64 decode then deserialize
        byte[] bytes = Base64.getDecoder().decode(data);
        return msgpackMapper.readValue(bytes, type);
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.MESSAGEPACK;
    }

    @Override
    public boolean isBinary() {
        return true;
    }

    @Override
    public byte[] serializeToBytes(Object obj) throws IOException {
        return msgpackMapper.writeValueAsBytes(obj);
    }

    @Override
    public <T> T deserializeFromBytes(byte[] data, Class<T> type) throws IOException {
        return msgpackMapper.readValue(data, type);
    }
}
