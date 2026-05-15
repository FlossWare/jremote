package org.flossware.jremote;

/**
 * Test service implementation with parameterized constructor.
 */
public class ConfigurableServiceImpl implements ConfigurableService {
    private final String config;
    private final int port;

    public ConfigurableServiceImpl(String config, Integer port) {
        this.config = config;
        this.port = port;
    }

    @Override
    public String getConfig() {
        return config;
    }

    @Override
    public int getPort() {
        return port;
    }
}
