package org.flossware.jremote;

/**
 * Supported serialization formats for remote invocations.
 */
public enum SerializationFormat {
    /**
     * JSON format - human-readable, default format for backward compatibility.
     */
    JSON((byte) 'J'),

    /**
     * XML format - enterprise integration standard.
     */
    XML((byte) 'X'),

    /**
     * YAML format - human-readable, configuration-friendly.
     */
    YAML((byte) 'Y'),

    /**
     * MessagePack format - compact binary format for performance.
     */
    MESSAGEPACK((byte) 'M');

    private final byte marker;

    SerializationFormat(byte marker) {
        this.marker = marker;
    }

    /**
     * Get the format marker byte used to identify this format in the protocol.
     *
     * @return the format marker byte
     */
    public byte getMarker() {
        return marker;
    }

    /**
     * Lookup serialization format by marker byte.
     *
     * @param marker the format marker byte
     * @return the corresponding SerializationFormat
     * @throws IllegalArgumentException if marker is not recognized
     */
    public static SerializationFormat fromMarker(byte marker) {
        for (SerializationFormat format : values()) {
            if (format.marker == marker) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown format marker: " + (char) marker);
    }
}
