package org.flossware.jremote.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.flossware.jremote.SerializationFormat;
import org.flossware.jremote.SerializationStrategy;

import java.io.IOException;

/**
 * XML serialization strategy using Jackson XmlMapper.
 */
public class XmlSerializationStrategy implements SerializationStrategy {
    private final XmlMapper xmlMapper;

    public XmlSerializationStrategy() {
        this.xmlMapper = new XmlMapper();
        this.xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String serialize(Object obj) throws IOException {
        return xmlMapper.writeValueAsString(obj);
    }

    @Override
    public <T> T deserialize(String data, Class<T> type) throws IOException {
        return xmlMapper.readValue(data, type);
    }

    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.XML;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public byte[] serializeToBytes(Object obj) throws IOException {
        throw new UnsupportedOperationException("XML is a text format");
    }

    @Override
    public <T> T deserializeFromBytes(byte[] data, Class<T> type) throws IOException {
        throw new UnsupportedOperationException("XML is a text format");
    }
}
