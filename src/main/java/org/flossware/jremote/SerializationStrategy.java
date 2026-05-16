package org.flossware.jremote;

import java.io.IOException;

/**
 * Strategy interface for pluggable serialization formats.
 */
public interface SerializationStrategy {
    /**
     * Serialize an object to a string representation.
     *
     * @param obj the object to serialize
     * @return the serialized string
     * @throws IOException if serialization fails
     */
    String serialize(Object obj) throws IOException;

    /**
     * Deserialize a string to an object of the specified type.
     *
     * @param data the serialized data
     * @param type the target class type
     * @param <T> the type parameter
     * @return the deserialized object
     * @throws IOException if deserialization fails
     */
    <T> T deserialize(String data, Class<T> type) throws IOException;

    /**
     * Get the serialization format this strategy implements.
     *
     * @return the serialization format
     */
    SerializationFormat getFormat();

    /**
     * Check if this strategy uses binary serialization.
     *
     * @return true if binary format, false if text format
     */
    boolean isBinary();

    /**
     * Serialize an object to a byte array (for binary formats).
     *
     * @param obj the object to serialize
     * @return the serialized bytes
     * @throws IOException if serialization fails
     * @throws UnsupportedOperationException if format is not binary
     */
    byte[] serializeToBytes(Object obj) throws IOException;

    /**
     * Deserialize a byte array to an object of the specified type (for binary formats).
     *
     * @param data the serialized bytes
     * @param type the target class type
     * @param <T> the type parameter
     * @return the deserialized object
     * @throws IOException if deserialization fails
     * @throws UnsupportedOperationException if format is not binary
     */
    <T> T deserializeFromBytes(byte[] data, Class<T> type) throws IOException;
}
