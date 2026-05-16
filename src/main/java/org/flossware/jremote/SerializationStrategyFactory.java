package org.flossware.jremote;

import org.flossware.jremote.serialization.JsonSerializationStrategy;
import org.flossware.jremote.serialization.XmlSerializationStrategy;
import org.flossware.jremote.serialization.YamlSerializationStrategy;
import org.flossware.jremote.serialization.MessagePackSerializationStrategy;

import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for creating serialization strategy instances.
 */
public class SerializationStrategyFactory {
    private static final Map<SerializationFormat, SerializationStrategy> strategies;

    static {
        strategies = new EnumMap<>(SerializationFormat.class);
        strategies.put(SerializationFormat.JSON, new JsonSerializationStrategy());
        strategies.put(SerializationFormat.XML, new XmlSerializationStrategy());
        strategies.put(SerializationFormat.YAML, new YamlSerializationStrategy());
        strategies.put(SerializationFormat.MESSAGEPACK, new MessagePackSerializationStrategy());
    }

    /**
     * Get the serialization strategy for the specified format.
     *
     * @param format the serialization format
     * @return the serialization strategy
     * @throws IllegalArgumentException if format is null
     */
    public static SerializationStrategy getStrategy(SerializationFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Format cannot be null");
        }
        return strategies.get(format);
    }
}
