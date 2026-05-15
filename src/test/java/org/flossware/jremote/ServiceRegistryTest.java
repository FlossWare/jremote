package org.flossware.jremote;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ServiceRegistryTest {
    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ServiceRegistry();
    }

    @Test
    void testRegisterFactory() {
        ServiceRegistry result = registry.registerFactory(TestService.class, TestServiceImpl.class);

        assertSame(registry, result);
        assertEquals(1, registry.factoryCount());
    }

    @Test
    void testRegisterCustomFactory() {
        ServiceRegistry result = registry.registerFactory(
            TestService.class,
            () -> new TestServiceImpl()
        );

        assertSame(registry, result);
        assertEquals(1, registry.factoryCount());
    }

    @Test
    void testRegisterDuplicateFactory() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        assertThrows(IllegalArgumentException.class, () ->
            registry.registerFactory(TestService.class, TestServiceImpl.class)
        );
    }

    @Test
    void testRegisterMultipleFactories() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);
        registry.registerFactory(OrderService.class, OrderServiceImpl.class);

        assertEquals(2, registry.factoryCount());
    }

    @Test
    void testCreateInstance() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        String objectId = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        assertNotNull(objectId);
        assertTrue(registry.contains(objectId));
        assertEquals(1, registry.size());
    }

    @Test
    void testCreateInstanceWithConstructorArgs() {
        registry.registerFactory(OrderService.class, OrderServiceImpl.class);

        String objectId = registry.createInstance(
            OrderService.class.getName(),
            "client1",
            new Class<?>[0],
            new Object[0]
        );

        assertNotNull(objectId);
        assertTrue(registry.contains(objectId));
    }

    @Test
    void testCreateMultipleInstances() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        String objectId1 = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        String objectId2 = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        assertNotEquals(objectId1, objectId2);
        assertEquals(2, registry.size());
    }

    @Test
    void testDestroyInstance() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        String objectId = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        assertTrue(registry.contains(objectId));

        registry.destroyInstance(objectId, "client1");

        assertFalse(registry.contains(objectId));
        assertEquals(0, registry.size());
    }

    @Test
    void testDestroyInstanceOwnershipValidation() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        String objectId = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        // Different client cannot destroy instance
        assertThrows(SecurityException.class, () ->
            registry.destroyInstance(objectId, "client2")
        );
    }

    @Test
    void testCleanupClient() {
        registry.registerFactory(TestService.class, TestServiceImpl.class);

        String objectId1 = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        String objectId2 = registry.createInstance(
            TestService.class.getName(),
            "client1",
            null,
            null
        );

        assertEquals(2, registry.size());

        registry.cleanupClient("client1");

        assertEquals(0, registry.size());
        assertFalse(registry.contains(objectId1));
        assertFalse(registry.contains(objectId2));
    }

    @Test
    void testCreateInstanceNoFactory() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.createInstance(
                TestService.class.getName(),
                "client1",
                null,
                null
            )
        );
    }

    @Test
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () ->
            registry.registerFactory(null, TestServiceImpl.class)
        );

        assertThrows(IllegalArgumentException.class, () ->
            registry.registerFactory(TestService.class, (Class<TestServiceImpl>) null)
        );
    }
}
