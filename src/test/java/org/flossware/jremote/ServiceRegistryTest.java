package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistryTest {
    private ServiceRegistry registry;
    private TestService testImpl;

    @BeforeEach
    void setUp() {
        registry = new ServiceRegistry();
        testImpl = new TestServiceImpl();
    }

    @Test
    void testRegisterService() {
        ServiceRegistry result = registry.register("test", TestService.class, testImpl);

        assertSame(registry, result);
        assertTrue(registry.contains("test"));
        assertEquals(1, registry.size());
    }

    @Test
    void testGetRegisteredService() {
        registry.register("test", TestService.class, testImpl);

        ServiceDescriptor descriptor = registry.get("test");

        assertNotNull(descriptor);
        assertEquals("test", descriptor.id());
        assertEquals(TestService.class, descriptor.serviceInterface());
        assertSame(testImpl, descriptor.implementation());
        assertNotNull(descriptor.allowedMethods());
        assertFalse(descriptor.allowedMethods().isEmpty());
    }

    @Test
    void testGetNonExistentService() {
        ServiceDescriptor descriptor = registry.get("nonexistent");
        assertNull(descriptor);
    }

    @Test
    void testRegisterDuplicateId() {
        registry.register("test", TestService.class, testImpl);

        assertThrows(IllegalArgumentException.class, () ->
            registry.register("test", TestService.class, new TestServiceImpl())
        );
    }

    @Test
    void testRegisterWithGeneratedId() {
        String id1 = registry.registerWithGeneratedId(TestService.class, testImpl);
        String id2 = registry.registerWithGeneratedId(TestService.class, new TestServiceImpl());

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(registry.contains(id1));
        assertTrue(registry.contains(id2));
        assertEquals(2, registry.size());
    }

    @Test
    void testGenerateId() {
        String id1 = registry.generateId();
        String id2 = registry.generateId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    void testContains() {
        assertFalse(registry.contains("test"));

        registry.register("test", TestService.class, testImpl);

        assertTrue(registry.contains("test"));
        assertFalse(registry.contains("other"));
    }

    @Test
    void testMultipleServices() {
        registry.register("service1", TestService.class, testImpl);
        registry.register("service2", TestService.class, new TestServiceImpl());

        assertEquals(2, registry.size());
        assertTrue(registry.contains("service1"));
        assertTrue(registry.contains("service2"));

        ServiceDescriptor desc1 = registry.get("service1");
        ServiceDescriptor desc2 = registry.get("service2");

        assertNotNull(desc1);
        assertNotNull(desc2);
        assertNotSame(desc1.implementation(), desc2.implementation());
    }
}
